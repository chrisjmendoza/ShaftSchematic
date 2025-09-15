package com.android.shaftschematic.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.ui.viewmodel.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object UnitsStore {
    private val KEY_UNITS = intPreferencesKey("units") // 0 = MM, 1 = IN

    /** Emits the last saved Units (MM by default). */
    fun flow(context: Context): Flow<Units> =
        context.dataStore.data.map { pref ->
            when (pref[KEY_UNITS] ?: 0) {
                1 -> Units.IN
                else -> Units.MM
            }
        }

    /** Persist the given units. */
    suspend fun save(context: Context, units: Units) {
        context.dataStore.edit { it[KEY_UNITS] = if (units == Units.IN) 1 else 0 }
    }
}
