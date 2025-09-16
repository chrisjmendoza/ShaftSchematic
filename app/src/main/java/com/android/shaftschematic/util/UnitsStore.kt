package com.android.shaftschematic.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// IMPORTANT: this must be at top level (file scope), not inside an object/class.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object UnitsStore {
    private val KEY_UNITS = intPreferencesKey("units") // 0 = mm, 1 = in

    /** Stream the saved unit system; defaults to MILLIMETERS. */
    fun flow(context: Context): Flow<UnitSystem> =
        context.dataStore.data.map { pref ->
            when (pref[KEY_UNITS] ?: 0) {
                1 -> UnitSystem.INCHES
                else -> UnitSystem.MILLIMETERS
            }
        }

    /** Persist the given unit system. */
    suspend fun save(context: Context, unit: UnitSystem) {
        context.dataStore.edit { it[KEY_UNITS] = if (unit == UnitSystem.INCHES) 1 else 0 }
    }

    /** Convenience: read once (suspending). */
    suspend fun getOnce(context: Context): UnitSystem = flow(context).first()
}
