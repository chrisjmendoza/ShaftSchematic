package com.android.shaftschematic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "autosave_datastore")

object AutosaveManager {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val AUTOSAVE_KEY = stringPreferencesKey("autosave_last_session")

    @Serializable
    data class SessionSnapshot(
        val shaftSpec: ShaftSpec,
        val unitSystem: UnitSystem,
        val shaftPosition: ShaftPosition,
        val customer: String,
        val vessel: String,
        val jobNumber: String,
        val notes: String
    )

    suspend fun autosave(context: Context, snapshot: SessionSnapshot) {
        val jsonString = json.encodeToString(snapshot)
        context.dataStore.edit { prefs ->
            prefs[AUTOSAVE_KEY] = jsonString
        }
    }

    suspend fun restore(context: Context): SessionSnapshot? {
        val jsonString = context.dataStore.data.map { prefs -> prefs[AUTOSAVE_KEY] ?: "" }.first()
        return if (jsonString.isNotBlank()) {
            try {
                json.decodeFromString<SessionSnapshot>(jsonString)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(AUTOSAVE_KEY)
        }
    }
}
