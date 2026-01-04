// app/src/main/java/com/android/shaftschematic/io/InternalStorage.kt
package com.android.shaftschematic.io

import android.content.Context
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
 * - Handles only .json text documents.
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
    private fun dir(ctx: Context): File = File(ctx.filesDir, "shafts").apply { mkdirs() }

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles()?.filter { it.isFile && it.extension == "json" }?.map { it.name }?.sorted()
            ?: emptyList()

    fun exists(ctx: Context, name: String): Boolean = File(dir(ctx), name).exists()

    /**
     * Normalizes a user-provided name into a saved-shaft JSON filename.
     * - Trims whitespace
     * - Appends `.json` if missing
     * - Forces lowercase `.json` extension
     *
     * Returns null if the input is blank.
     */
    internal fun normalizeJsonName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        return if (lower.endsWith(".json")) {
            trimmed.dropLast(5) + ".json"
        } else {
            "$trimmed.json"
        }
    }

    fun save(ctx: Context, name: String, content: String) {
        require(name.endsWith(".json", ignoreCase = true)) { "Name must end with .json" }
        VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") { "save name=$name chars=${content.length}" }
        File(dir(ctx), name).writeText(content)
    }

    fun load(ctx: Context, name: String): String =
        File(dir(ctx), name).readText().also { content ->
            VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") { "load name=$name chars=${content.length}" }
        }

    fun delete(ctx: Context, name: String) {
        File(dir(ctx), name).delete()
    }
}
