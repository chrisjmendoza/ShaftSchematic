// app/src/main/java/com/android/shaftschematic/io/InternalStorage.kt
package com.android.shaftschematic.io

import android.content.Context
import android.content.res.AssetManager
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.SHAFT_EXT
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.isSeededSampleNotes
import com.android.shaftschematic.doc.isLegacyExtension
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        save(dir(ctx), name, content)
    }

    internal fun save(dir: File, name: String, content: String) {
        require(name.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) { "Name must end with $SHAFT_DOT_EXT" }
        File(dir, name).writeText(content)
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

    // ────────────────────────────────────────────────────────────────────────────
    // Bundled sample seeding (first-run)
    // ────────────────────────────────────────────────────────────────────────────

    internal interface SampleAssetSource {
        suspend fun listSampleShaftFiles(): List<String>
        suspend fun readSampleShaftText(filename: String): String
    }

    internal class AndroidSampleAssetSource(
        private val assets: AssetManager,
        private val dir: String = "sample_shafts",
    ) : SampleAssetSource {
        override suspend fun listSampleShaftFiles(): List<String> =
            assets.list(dir)?.toList().orEmpty()

        override suspend fun readSampleShaftText(filename: String): String =
            assets.open("$dir/$filename").bufferedReader().use { it.readText() }
    }

    internal interface SampleSeedSettings {
        val currentSeedVersion: Int
        suspend fun getSeedVersion(): Int
        suspend fun setSeedVersion(v: Int)
    }

    internal class SettingsStoreSampleSeedSettings(
        private val ctx: Context,
        private val settingsStore: SettingsStore,
    ) : SampleSeedSettings {
        override val currentSeedVersion: Int
            get() = settingsStore.currentSampleSeedVersion()

        override suspend fun getSeedVersion(): Int = settingsStore.getSampleSeedVersion(ctx)

        override suspend fun setSeedVersion(v: Int) {
            settingsStore.setSampleSeedVersion(ctx, v)
        }
    }

    data class SeedReport(
        val attemptedCount: Int,
        val savedCount: Int,
        val failedCount: Int,
    )

    /**
     * Seeds bundled sample shafts into internal storage on first run (or when seed version bumps).
     *
     * Contract:
     * - Never overwrites existing user documents.
     * - Uses the same JSON decode path as import (via [ShaftDocCodec]).
     * - Runs on [Dispatchers.IO].
     */
    suspend fun seedBundledSamplesIfNeeded(
        context: Context,
        settingsStore: SettingsStore,
    ): SeedReport = seedBundledSamples(context, settingsStore, force = false)

    /**
     * Seeds bundled sample shafts into internal storage.
     *
     * When [force] is true, bypasses the version gate (for Settings → "Restore sample shafts").
     * Still never overwrites user docs; collisions get suffixed with "(Sample)".
     */
    suspend fun seedBundledSamples(
        context: Context,
        settingsStore: SettingsStore,
        force: Boolean = false,
    ): SeedReport = withContext(Dispatchers.IO) {
        val dir = dir(context)
        val assets = AndroidSampleAssetSource(context.assets)
        val settings = SettingsStoreSampleSeedSettings(context, settingsStore)
        seedBundledSamples(dir, assets, settings, force = force)
    }

    internal suspend fun seedBundledSamplesIfNeeded(
        dir: File,
        assets: SampleAssetSource,
        settings: SampleSeedSettings,
    ): SeedReport {
        return seedBundledSamples(dir, assets, settings, force = false)
    }

    internal suspend fun seedBundledSamples(
        dir: File,
        assets: SampleAssetSource,
        settings: SampleSeedSettings,
        force: Boolean,
    ): SeedReport {
        if (!force) {
            val seedVersion = settings.getSeedVersion()
            if (seedVersion >= settings.currentSeedVersion) {
                return SeedReport(attemptedCount = 0, savedCount = 0, failedCount = 0)
            }

            // Version-bump cleanup: remove previously seeded *legacy* sample docs so the
            // Saved list doesn't accumulate old+new bundled examples.
            //
            // Safety constraints:
            // - Only runs when upgrading from a non-zero seed version.
            // - Matches by stable notes marker + base-name match against current bundled samples.
            if (seedVersion > 0) {
                prunePreviouslySeededBundledSamples(dir, assets)
            }
        }

        val filenames = assets.listSampleShaftFiles()
            .filter { it.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
            .sorted()

        if (filenames.isEmpty()) {
            return SeedReport(attemptedCount = 0, savedCount = 0, failedCount = 0)
        }

        val existingBaseNames = list(dir).map(::stripShaftDocExtension).toMutableSet()
        val existingSeededSampleRoots = if (force) {
            existingSeededSampleRootBases(dir)
        } else {
            emptySet()
        }

        var saved = 0
        var failed = 0

        for (filename in filenames) {
            val raw = runCatching { assets.readSampleShaftText(filename) }.getOrElse {
                failed++
                continue
            }

            val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrElse {
                failed++
                continue
            }

            val suffix = decoded.shaftPosition.printableLabelOrNull()
            val preferredBase =
                DocumentNaming.suggestedBaseName(
                    jobNumber = decoded.jobNumber,
                    customer = decoded.customer,
                    vessel = decoded.vessel,
                    suffix = suffix,
                ) ?: deriveBaseNameFromFilename(filename)

            val preferredBaseClean = sanitizeFilenameBase(preferredBase)

            // Settings → "Restore sample shafts" should only re-add missing samples.
            // In particular: don't create duplicate "(Sample)" suffixed copies when the
            // sample is already present.
            if (force && existingSeededSampleRoots.contains(preferredBaseClean)) {
                continue
            }

            val uniqueBase = if (force) {
                // Missing-only restore: prefer the canonical base name, but still avoid overwrites.
                ensureUniqueBaseName(existingBaseNames, preferredBaseClean)
            } else {
                ensureUniqueBaseName(existingBaseNames, preferredBase)
            }
            val targetName = uniqueBase + SHAFT_DOT_EXT

            val ok = runCatching {
                save(dir, targetName, raw)
                true
            }.getOrDefault(false)

            if (ok) {
                existingBaseNames.add(uniqueBase)
                saved++
            } else {
                failed++
            }
        }

        if (!force && saved > 0) {
            settings.setSeedVersion(settings.currentSeedVersion)
        }

        return SeedReport(
            attemptedCount = filenames.size,
            savedCount = saved,
            failedCount = failed,
        )
    }

    private fun deriveBaseNameFromFilename(filename: String): String {
        val base = stripShaftDocExtension(filename)
        val stripped = base
            .replace(Regex("^\\d+[_\\-\\s]*"), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return sanitizeFilenameBase(stripped.ifBlank { base })
    }

    private fun sanitizeFilenameBase(raw: String): String {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return "Sample"
        return collapsed
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
            .ifBlank { "Sample" }
    }

    private fun ensureUniqueBaseName(existing: Set<String>, desired: String): String {
        val desiredClean = sanitizeFilenameBase(desired)
        if (!existing.contains(desiredClean)) return desiredClean

        val first = "$desiredClean (Sample)"
        if (!existing.contains(first)) return first

        var n = 2
        while (true) {
            val candidate = "$desiredClean (Sample $n)"
            if (!existing.contains(candidate)) return candidate
            n++
        }
    }

    private fun existingSeededSampleRootBases(dir: File): Set<String> {
        val roots = mutableSetOf<String>()
        val names = list(dir).filter { it.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
        for (name in names) {
            val raw = runCatching { File(dir, name).readText() }.getOrNull() ?: continue
            val doc = runCatching { ShaftDocCodec.decode(raw) }.getOrNull() ?: continue
            if (!isSeededSampleNotes(doc.notes)) continue

            val base = stripShaftDocExtension(name)
            roots.add(sampleRootBaseName(base))
        }
        return roots
    }

    private fun sampleRootBaseName(base: String): String {
        val stripped = base.replace(Regex(" \\((?:Sample)(?: \\d+)?\\)$"), "")
        return sanitizeFilenameBase(stripped)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Bundled samples: versioned cleanup
    // ────────────────────────────────────────────────────────────────────────────

    private suspend fun prunePreviouslySeededBundledSamples(dir: File, assets: SampleAssetSource): Int {
        val bundledSampleBases = bundledSampleBaseNames(assets)
        if (bundledSampleBases.isEmpty()) return 0

        var deleted = 0

        // Only consider `.shaft` docs. Legacy `.json` docs are user data and shouldn't be touched.
        val names = list(dir).filter { it.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
        for (name in names) {
            val base = stripShaftDocExtension(name)
            if (!bundledSampleBases.contains(base)) continue

            val raw = runCatching { File(dir, name).readText() }.getOrNull() ?: continue
            val doc = runCatching { ShaftDocCodec.decode(raw) }.getOrNull() ?: continue
            if (!isSeededSampleNotes(doc.notes)) continue

            if (delete(dir, name)) deleted++
        }

        return deleted
    }

    private suspend fun bundledSampleBaseNames(assets: SampleAssetSource): Set<String> {
        val filenames = runCatching { assets.listSampleShaftFiles() }.getOrDefault(emptyList())
            .filter { it.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }

        val bases = mutableSetOf<String>()
        for (filename in filenames) {
            val raw = runCatching { assets.readSampleShaftText(filename) }.getOrNull() ?: continue
            val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrNull() ?: continue

            val suffix = decoded.shaftPosition.printableLabelOrNull()
            val preferredBase =
                DocumentNaming.suggestedBaseName(
                    jobNumber = decoded.jobNumber,
                    customer = decoded.customer,
                    vessel = decoded.vessel,
                    suffix = suffix,
                ) ?: deriveBaseNameFromFilename(filename)

            bases.add(sanitizeFilenameBase(preferredBase))
        }

        return bases
    }
}
