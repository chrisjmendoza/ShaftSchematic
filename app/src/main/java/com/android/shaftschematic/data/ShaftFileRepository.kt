// file: com/android/shaftschematic/data/ShaftFileRepository.kt
package com.android.shaftschematic.data

import android.content.Context
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ShaftSpecMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ShaftFileRepository(
    private val context: Context,
    private val fileName: String = "shaftspec.json",
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) : ShaftRepository {

    private fun file(): File = File(context.filesDir, fileName)

    override suspend fun loadSpec(): ShaftSpec = withContext(Dispatchers.IO) {
        val f = file()
        if (!f.exists()) return@withContext ShaftSpec()
        val text = f.readText()
        val decoded = json.decodeFromString<ShaftSpec>(text)
        ShaftSpecMigrations.ensureIds(decoded)
    }

    override suspend fun saveSpec(spec: ShaftSpec) = withContext(Dispatchers.IO) {
        val f = file()
        val text = json.encodeToString(spec)
        f.writeText(text)
    }
}
