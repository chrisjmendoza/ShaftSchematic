// app/src/main/java/com/android/shaftschematic/io/InternalStorage.kt
package com.android.shaftschematic.io

import android.content.Context
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.SHAFT_EXT
import com.android.shaftschematic.doc.isLegacyExtension
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.util.VerboseLog
import java.io.File

/**
 * InternalStorage
 *
 * Purpose
 * Provides private JSON document storage for ShaftSchematic inside the app sandbox.
 *
 * Location: <filesDir>/shafts/
 *
 * Contract
 * - Stores shaft documents as JSON text.
 * - Default/current extension is `.shaft`.
 * - Legacy `.json` files remain readable/listable.
 * - Creates its directory lazily on first access.
 * - Does not perform threading; callers must dispatch to Dispatchers.IO.
 * - No external permissions required.
 * - Not responsible for JSON schema; ViewModel handles serialization.
 *
 * Non-Goals
 * - No SAF, sharing, or export features (those live in SafRoutes.kt).
 * - No exception UI; errors propagate to caller.
 */

object InternalStorage {
    private fun dir(ctx: Context): File = dir(ctx.filesDir)

    internal fun dir(filesDir: File): File = File(filesDir, "shafts").apply { mkdirs() }

    internal fun list(dir: File): List<String> =
        dir.listFiles()
            ?.filter { it.isFile }
            ?.filter { f ->
                val ext = f.extension.lowercase()
                ext == SHAFT_EXT || isLegacyExtension(ext)
            }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun list(ctx: Context): List<String> = list(dir(ctx))

    fun exists(ctx: Context, name: String): Boolean = File(dir(ctx), name).exists()

    /**
     * Normalizes a user-provided name into a saved-shaft document filename.
     * - Trims whitespace
     * - If user includes `.shaft` or a legacy extension like `.json`, it is replaced with `.shaft`
     * - Appends `.shaft` if no extension is present
     *
     * Returns null if the input is blank.
     */
    fun normalizeShaftDocName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val base = stripShaftDocExtension(trimmed)
        return base + SHAFT_DOT_EXT
    }

    /** Back-compat alias for older call sites/tests; now produces `.shaft`. */
    @Deprecated("Use normalizeShaftDocName", ReplaceWith("normalizeShaftDocName(raw)"))
    internal fun normalizeJsonName(raw: String): String? = normalizeShaftDocName(raw)

    fun save(ctx: Context, name: String, content: String) {
        require(name.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) { "Name must end with $SHAFT_DOT_EXT" }
        VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") { "save name=$name chars=${content.length}" }
        File(dir(ctx), name).writeText(content)
    }

    fun load(ctx: Context, name: String): String =
        File(dir(ctx), name).readText().also { content ->
            VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") { "load name=$name chars=${content.length}" }
        }

    /** Returns true when the file was actually deleted. */
    fun delete(ctx: Context, name: String): Boolean = delete(dir(ctx), name)

    internal fun delete(dir: File, name: String): Boolean = File(dir, name).delete()

    /**
     * Renames an existing saved shaft.
     *
     * Contract:
     * - [fromName] must be an existing filename returned by [list] (may be `.shaft` or legacy).
     * - [toName] must end with `.shaft`.
     * - Returns false if the source is missing, the target already exists, or the rename fails.
     */
    fun rename(ctx: Context, fromName: String, toName: String): Boolean = rename(dir(ctx), fromName, toName)

    internal fun rename(dir: File, fromName: String, toName: String): Boolean {
        require(toName.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) { "Target name must end with $SHAFT_DOT_EXT" }

        val src = File(dir, fromName)
        if (!src.exists()) return false

        val dst = File(dir, toName)
        if (dst.exists()) return false

        return src.renameTo(dst) || runCatching {
            src.copyTo(dst, overwrite = false)
            src.delete()
        }.getOrDefault(false)
    }

    data class MigrationReport(
        val migratedCount: Int,
        val skippedCount: Int,
    )

    /**
     * Optional migration:
     * Renames legacy `*.json` saved shafts under internal storage to `*.shaft`.
     *
     * If a target name already exists, appends " (Migrated)" / " (Migrated N)" to avoid overwrites.
     */
    suspend fun migrateLegacyJsonToShaft(ctx: Context): MigrationReport = migrateLegacyJsonToShaft(dir(ctx))

    internal fun migrateLegacyJsonToShaft(dir: File): MigrationReport {
        val legacy = dir.listFiles()?.filter { f ->
            f.isFile && isLegacyExtension(f.extension)
        }
            ?: emptyList()

        var migrated = 0
        var skipped = 0

        legacy.forEach { src ->
            val base = src.nameWithoutExtension
            val desired = File(dir, base + SHAFT_DOT_EXT)

            val target = if (!desired.exists()) {
                desired
            } else {
                // Collision: keep both.
                var candidate = File(dir, "$base (Migrated)" + SHAFT_DOT_EXT)
                var n = 2
                while (candidate.exists()) {
                    candidate = File(dir, "$base (Migrated $n)" + SHAFT_DOT_EXT)
                    n++
                }
                candidate
            }

            val ok = src.renameTo(target) || runCatching {
                src.copyTo(target, overwrite = false)
                src.delete()
            }.getOrDefault(false)

            if (ok) migrated++ else skipped++
        }

        return MigrationReport(migratedCount = migrated, skippedCount = skipped)
    }
}
