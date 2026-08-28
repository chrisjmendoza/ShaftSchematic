// app/src/main/java/com/android/shaftschematic/io/TemplateStorage.kt
package com.android.shaftschematic.io

import android.content.Context
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.SHAFT_EXT
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.template.TemplateLinerCount
import com.android.shaftschematic.template.TemplateSizeBucket
import com.android.shaftschematic.template.templateLinerCount
import com.android.shaftschematic.template.templateSizeBucket
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TemplateStorage
 *
 * Purpose
 * Private storage for reusable shaft **templates** — starting points a new drawing is built
 * from, kept apart from saved job documents.
 *
 * Location: `<filesDir>/templates/`
 *
 * Contract
 * - A template is an ordinary `.shaft` document. No new file format, no codec change; any
 *   path that can read a shaft document can read a template.
 * - A template carries **geometry only**. Job identity (customer, vessel, job number, notes,
 *   shaft position) and every measurement record (wear, runout, undercut) are stripped at
 *   WRITE time — see `ShaftViewModel.exportTemplateJson`. A template that shipped a customer
 *   name would leak it into every drawing made from it.
 * - Browser grouping is DERIVED from the stored spec on scan (`template/TemplateBuckets.kt`);
 *   nothing about a template's bucket is stored, so an edited template re-files itself.
 * - No threading here — callers dispatch to `Dispatchers.IO`.
 *
 * Non-Goals
 * - No SAF, sharing, or export (templates are on-device for now).
 * - No UI; errors propagate to the caller.
 */
object TemplateStorage {

    private fun dir(ctx: Context): File = dir(ctx.filesDir)

    internal fun dir(filesDir: File): File = File(filesDir, "templates").apply { mkdirs() }

    /** One template as the browser needs it: identity, derived buckets, and the spec to draw. */
    data class TemplateSummary(
        val filename: String,
        val displayName: String,
        val sizeBucket: TemplateSizeBucket,
        val linerCount: TemplateLinerCount,
        val spec: ShaftSpec,
        val unit: UnitSystem,
        val updatedAtEpochMs: Long,
    )

    internal fun list(dir: File): List<String> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.equals(SHAFT_EXT, ignoreCase = true) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun list(ctx: Context): List<String> = list(dir(ctx))

    /** Same normalization as saved documents — templates share the `.shaft` extension. */
    fun normalizeTemplateName(raw: String): String? =
        InternalStorage.normalizeShaftDocName(raw)

    /**
     * Writes [content] (a scrubbed shaft document) as a template.
     *
     * Delegates to [InternalStorage]'s atomic save so templates get the same
     * write-temp-then-swap guarantee as documents — one implementation, not two.
     */
    fun save(ctx: Context, name: String, content: String) = save(dir(ctx), name, content)

    internal fun save(dir: File, name: String, content: String) {
        require(name.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) { "Name must end with $SHAFT_DOT_EXT" }
        VerboseLog.d(VerboseLog.Category.IO, "TemplateStorage") { "save name=$name chars=${content.length}" }
        InternalStorage.save(dir, name, content)
    }

    fun load(ctx: Context, name: String): String = File(dir(ctx), name).readText()

    fun delete(ctx: Context, name: String): Boolean = delete(dir(ctx), name)

    internal fun delete(dir: File, name: String): Boolean = File(dir, name).delete()

    /** Why a [rename] did not complete — each maps to its own user message in the browser. */
    enum class RenameResult { OK, SOURCE_MISSING, TARGET_EXISTS, IO_ERROR }

    /** Renames a template. Never overwrites — a collision reports [RenameResult.TARGET_EXISTS]. */
    fun rename(ctx: Context, fromName: String, toName: String): RenameResult = rename(dir(ctx), fromName, toName)

    internal fun rename(dir: File, fromName: String, toName: String): RenameResult {
        require(toName.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) { "Target name must end with $SHAFT_DOT_EXT" }
        val src = File(dir, fromName)
        if (!src.exists()) return RenameResult.SOURCE_MISSING
        // Case-insensitive collision check, so one browser row can never fork into two
        // case-variant files on a case-sensitive filesystem. Renaming a template to its own
        // name in a different case is not a collision.
        val collides = list(dir).any {
            it.equals(toName, ignoreCase = true) && !it.equals(fromName, ignoreCase = true)
        }
        if (collides) return RenameResult.TARGET_EXISTS
        val dst = File(dir, toName)
        val moved = src.renameTo(dst) || runCatching {
            src.copyTo(dst, overwrite = false)
            src.delete()
        }.getOrDefault(false)
        return if (moved) RenameResult.OK else RenameResult.IO_ERROR
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Bundled starter templates (first run)
    // ────────────────────────────────────────────────────────────────────────────

    data class SeedReport(val savedCount: Int, val failedCount: Int)

    /**
     * Copies the bundled starter templates in on first run, so the browser is not empty the
     * first time it opens.
     *
     * One-shot: a user who deletes every starter does not get them back on the next launch
     * (the flag is already set). Never overwrites — a name collision is skipped, not resolved,
     * because the user's file is the one that matters. Content is validated by decoding it
     * before it is written, so a malformed asset cannot plant a file the browser will choke on.
     */
    suspend fun seedStarterTemplatesIfNeeded(ctx: Context, settingsStore: SettingsStore): SeedReport =
        withContext(Dispatchers.IO) {
            if (settingsStore.starterTemplatesSeeded(ctx)) return@withContext SeedReport(0, 0)

            val dir = dir(ctx)
            val assetNames = runCatching { ctx.assets.list(STARTER_ASSET_DIR)?.toList() }
                .getOrNull()
                .orEmpty()
                .filter { it.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
                .sorted()

            var saved = 0
            var failed = 0
            val existing = list(dir).map { stripShaftDocExtension(it).lowercase() }.toMutableSet()

            for (assetName in assetNames) {
                val raw = runCatching {
                    ctx.assets.open("$STARTER_ASSET_DIR/$assetName").bufferedReader().use { it.readText() }
                }.getOrElse { failed++; continue }

                if (runCatching { ShaftDocCodec.decode(raw) }.isFailure) { failed++; continue }

                val base = starterDisplayBase(assetName)
                // Just-seeded bases join the collision set, so two assets that normalize to
                // one display name cannot silently overwrite each other.
                if (!existing.add(base.lowercase())) continue

                val ok = runCatching { save(dir, base + SHAFT_DOT_EXT, raw); true }.getOrDefault(false)
                if (ok) saved++ else failed++
            }

            // The one-shot flag sets only when the run had nothing go wrong (collision skips
            // included) or actually seeded something. A run where EVERY asset failed leaves
            // the flag unset so a fixed build can retry — there is no manual "restore
            // starters" path to recover with.
            if (failed == 0 || saved > 0) settingsStore.setStarterTemplatesSeeded(ctx, true)
            SeedReport(savedCount = saved, failedCount = failed)
        }

    private const val STARTER_ASSET_DIR = "templates"

    /** `01_6in_2liners.shaft` → `6in 2liners` — the ordering prefix is for the asset list only. */
    internal fun starterDisplayBase(assetName: String): String =
        stripShaftDocExtension(assetName)
            .replace(Regex("^\\d+[_\\-\\s]*"), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { "Starter template" }

    /**
     * Decodes every template into a browser summary, newest first.
     *
     * A file that fails to decode (hand-edited, or written by a newer app version) is skipped
     * rather than failing the whole scan — one bad template must not empty the browser.
     */
    fun summaries(ctx: Context): List<TemplateSummary> = summaries(dir(ctx))

    internal fun summaries(dir: File): List<TemplateSummary> =
        list(dir).mapNotNull { name ->
            val file = File(dir, name)
            val raw = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrNull() ?: return@mapNotNull null
            TemplateSummary(
                filename = name,
                displayName = stripShaftDocExtension(name),
                sizeBucket = templateSizeBucket(decoded.spec),
                linerCount = templateLinerCount(decoded.spec),
                spec = decoded.spec,
                unit = decoded.preferredUnit ?: UnitSystem.INCHES,
                updatedAtEpochMs = file.lastModified(),
            )
        }.sortedByDescending { it.updatedAtEpochMs }
}
