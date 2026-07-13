// app/src/main/java/com/android/shaftschematic/io/ShaftBackup.kt
package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.stripShaftDocExtension
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ShaftBackup
 *
 * Purpose
 * Zip-based backup/restore of saved shaft documents, plus pre-update snapshots.
 *
 * Contract
 * - Backup zips contain every saved doc under `shafts/<name>` plus a
 *   `manifest.json` (app version, doc format version, count, timestamp).
 * - Restore never overwrites an existing document: byte-identical content is
 *   skipped, a differing collision is renamed `<base> (restored)` / `(restored N)`.
 * - Entry names from foreign zips are reduced to their basename (zip-slip safe)
 *   and re-normalized to `.shaft`.
 * - Stream-based and Android-free so the whole module is JVM unit-testable;
 *   callers own SAF/ContentResolver plumbing and threading (Dispatchers.IO).
 */
object ShaftBackup {

    const val MANIFEST_ENTRY = "manifest.json"
    const val DOCS_PREFIX = "shafts/"

    /** Refuse absurd entries so a malformed zip can't balloon memory. */
    private const val MAX_ENTRY_BYTES = 10L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Manifest(
        val appVersion: String,
        val docFormatVersion: Int,
        val createdEpochMs: Long,
        val documentCount: Int,
    )

    data class Contents(
        val manifest: Manifest?,
        val docs: List<Pair<String, String>>,
    )

    data class RestoreReport(
        val restoredCount: Int,
        val renamedCount: Int,
        val skippedIdenticalCount: Int,
        val failedCount: Int,
    )

    fun defaultBackupFilename(nowMs: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        return "ShaftSchematic-backup-$stamp.zip"
    }

    /** Writes all [docs] (name → JSON text) plus [manifest] into a zip on [out]. */
    fun writeZip(out: OutputStream, docs: List<Pair<String, String>>, manifest: Manifest) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for ((name, content) in docs) {
                zip.putNextEntry(ZipEntry(DOCS_PREFIX + name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a backup zip. Tolerant of foreign zips: any `.shaft` entry is accepted
     * regardless of folder, the manifest is optional, and non-doc entries are ignored.
     */
    fun readZip(input: InputStream): Contents {
        var manifest: Manifest? = null
        val docs = mutableListOf<Pair<String, String>>()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.size > MAX_ENTRY_BYTES) continue

                // Basename only: entry paths from untrusted zips must never
                // influence where a file lands (zip-slip).
                val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')

                when {
                    baseName.equals(MANIFEST_ENTRY, ignoreCase = true) && manifest == null -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        manifest = runCatching {
                            json.decodeFromString(Manifest.serializer(), text)
                        }.getOrNull()
                    }
                    baseName.endsWith(SHAFT_DOT_EXT, ignoreCase = true) -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        docs += baseName to text
                    }
                }
            }
        }
        return Contents(manifest = manifest, docs = docs)
    }

    /**
     * Restores [docs] into [dir] (the internal `shafts/` directory).
     *
     * Policy per document:
     * - [validate] fails → counted failed, nothing written.
     * - Target exists with identical content → skipped.
     * - Target exists with different content → saved under `<base> (restored)` /
     *   `<base> (restored N)`.
     * - Otherwise saved under its own name.
     */
    fun restoreInto(
        dir: File,
        docs: List<Pair<String, String>>,
        validate: (String) -> Boolean,
    ): RestoreReport {
        var restored = 0
        var renamed = 0
        var skippedIdentical = 0
        var failed = 0

        for ((rawName, content) in docs) {
            if (!validate(content)) {
                failed++
                continue
            }

            val base = sanitizeBaseName(stripShaftDocExtension(rawName))
            if (base.isEmpty()) {
                failed++
                continue
            }

            val target = File(dir, base + SHAFT_DOT_EXT)
            val finalName: String
            var wasRenamed = false

            if (!target.exists()) {
                finalName = target.name
            } else if (runCatching { target.readText() }.getOrNull() == content) {
                skippedIdentical++
                continue
            } else {
                var candidate = "$base (restored)"
                var n = 2
                while (File(dir, candidate + SHAFT_DOT_EXT).exists()) {
                    // An existing identical "(restored)" copy also counts as already present.
                    if (runCatching { File(dir, candidate + SHAFT_DOT_EXT).readText() }.getOrNull() == content) break
                    candidate = "$base (restored $n)"
                    n++
                }
                if (File(dir, candidate + SHAFT_DOT_EXT).exists()) {
                    skippedIdentical++
                    continue
                }
                finalName = candidate + SHAFT_DOT_EXT
                wasRenamed = true
            }

            val ok = runCatching {
                InternalStorage.save(dir, finalName, content)
                true
            }.getOrDefault(false)

            when {
                !ok -> failed++
                wasRenamed -> renamed++
                else -> restored++
            }
        }

        return RestoreReport(
            restoredCount = restored,
            renamedCount = renamed,
            skippedIdenticalCount = skippedIdentical,
            failedCount = failed,
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // Pre-update snapshots (internal, automatic)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Zips every saved doc in [shaftsDir] into `[backupsDir]/snapshot-<stamp>.zip`
     * and prunes the oldest snapshots beyond [keep]. Returns the written file, or
     * null when there is nothing to snapshot.
     *
     * Called once per app-version change, *before* migrations/seeding run, so a
     * bad update can always be rolled back from the newest snapshot.
     */
    fun writeSnapshot(
        shaftsDir: File,
        backupsDir: File,
        appVersion: String,
        docFormatVersion: Int,
        nowMs: Long,
        keep: Int = 3,
    ): File? {
        val names = InternalStorage.list(shaftsDir)
        if (names.isEmpty()) return null

        val docs = names.mapNotNull { name ->
            runCatching { name to File(shaftsDir, name).readText() }.getOrNull()
        }
        if (docs.isEmpty()) return null

        backupsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs))
        val file = File(backupsDir, "snapshot-$stamp.zip")

        file.outputStream().use { out ->
            writeZip(
                out = out,
                docs = docs,
                manifest = Manifest(
                    appVersion = appVersion,
                    docFormatVersion = docFormatVersion,
                    createdEpochMs = nowMs,
                    documentCount = docs.size,
                ),
            )
        }

        pruneSnapshots(backupsDir, keep)
        return file
    }

    internal fun pruneSnapshots(backupsDir: File, keep: Int) {
        backupsDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("snapshot-") && it.extension.equals("zip", ignoreCase = true) }
            ?.sortedByDescending { it.name } // stamp-named → lexicographic == chronological
            ?.drop(keep)
            ?.forEach { runCatching { it.delete() } }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ────────────────────────────────────────────────────────────────────────

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sanitizeBaseName(raw: String): String =
        raw.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
}
