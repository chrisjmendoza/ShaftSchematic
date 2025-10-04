// app/src/main/java/com/android/shaftschematic/io/InternalStorage.kt
package com.android.shaftschematic.io

import android.content.Context
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

    fun save(ctx: Context, name: String, content: String) {
        require(name.endsWith(".json")) { "Name must end with .json" }
        File(dir(ctx), name).writeText(content)
    }

    fun load(ctx: Context, name: String): String =
        File(dir(ctx), name).readText()

    fun delete(ctx: Context, name: String) {
        File(dir(ctx), name).delete()
    }
}
