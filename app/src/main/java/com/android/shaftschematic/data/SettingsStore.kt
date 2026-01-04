package com.android.shaftschematic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.settings.PdfPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsStore {
    private val KEY_DEFAULT_UNIT = intPreferencesKey("default_unit") // 0=MM, 1=IN
    private val KEY_SHOW_GRID    = booleanPreferencesKey("show_grid")
    private val KEY_SHOW_COMPONENT_ARROWS = booleanPreferencesKey("show_component_arrows")
    private val KEY_COMPONENT_ARROW_WIDTH_DP = intPreferencesKey("component_arrow_width_dp")

    enum class UnitPref { MILLIMETERS, INCHES }

    fun defaultUnitFlow(ctx: Context): Flow<UnitPref> =
        ctx.settingsDataStore.data.map { p ->
            when (p[KEY_DEFAULT_UNIT] ?: 0) { 1 -> UnitPref.INCHES; else -> UnitPref.MILLIMETERS }
        }

    fun showGridFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_GRID] ?: false }

    fun showComponentArrowsFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_COMPONENT_ARROWS] ?: false }

    fun componentArrowWidthDpFlow(ctx: Context): Flow<Int> =
        ctx.settingsDataStore.data.map { p -> p[KEY_COMPONENT_ARROW_WIDTH_DP] ?: 40 }

    suspend fun setDefaultUnit(ctx: Context, unit: UnitPref) {
        ctx.settingsDataStore.edit { it[KEY_DEFAULT_UNIT] = if (unit == UnitPref.INCHES) 1 else 0 }
    }

    suspend fun setShowGrid(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_GRID] = show }
    }

    suspend fun setShowComponentArrows(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_COMPONENT_ARROWS] = show }
    }

    suspend fun setComponentArrowWidthDp(ctx: Context, widthDp: Int) {
        ctx.settingsDataStore.edit { it[KEY_COMPONENT_ARROW_WIDTH_DP] = widthDp }
    }

    // --- PDF section (new) ---
    @Volatile
    private var _pdfPrefs: PdfPrefs = PdfPrefs() // default; load actual on init if you persist

    val pdfPrefs: PdfPrefs
        get() = _pdfPrefs

    fun updatePdfPrefs(transform: (PdfPrefs) -> PdfPrefs) {
        _pdfPrefs = transform(_pdfPrefs).clamped()
        // TODO: persist _pdfPrefs via your existing persistence layer
    }

    fun setPdfOalSpacingFactor(f: Float) {
        updatePdfPrefs { it.copy(oalSpacingFactor = f) }
    }
}
