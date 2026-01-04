package com.android.shaftschematic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    // Developer options (hidden behind About taps)
    private val KEY_DEV_OPTIONS_ENABLED = booleanPreferencesKey("dev_options_enabled")
    private val KEY_SHOW_OAL_DEBUG_LABEL = booleanPreferencesKey("show_oal_debug_label")
    private val KEY_SHOW_OAL_HELPER_LINE = booleanPreferencesKey("show_oal_helper_line")

    // Developer options (debug tooling)
    private val KEY_SHOW_COMPONENT_DEBUG_LABELS = booleanPreferencesKey("show_component_debug_labels")
    private val KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY = booleanPreferencesKey("show_render_layout_debug_overlay")
    private val KEY_SHOW_RENDER_OAL_MARKERS = booleanPreferencesKey("show_render_oal_markers")
    private val KEY_VERBOSE_LOGGING_ENABLED = booleanPreferencesKey("verbose_logging_enabled")
    private val KEY_VERBOSE_LOGGING_RENDER = booleanPreferencesKey("verbose_logging_render")
    private val KEY_VERBOSE_LOGGING_OAL = booleanPreferencesKey("verbose_logging_oal")
    private val KEY_VERBOSE_LOGGING_PDF = booleanPreferencesKey("verbose_logging_pdf")
    private val KEY_VERBOSE_LOGGING_IO = booleanPreferencesKey("verbose_logging_io")

    // Achievements (Steam-style)
    private val KEY_ACHIEVEMENTS_ENABLED = booleanPreferencesKey("achievements_enabled")
    private val KEY_UNLOCKED_ACHIEVEMENT_IDS = stringSetPreferencesKey("unlocked_achievement_ids")

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

    fun devOptionsEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_DEV_OPTIONS_ENABLED] ?: false }

    fun showOalDebugLabelFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_OAL_DEBUG_LABEL] ?: false }

    fun showOalHelperLineFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_OAL_HELPER_LINE] ?: false }

    fun showComponentDebugLabelsFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_COMPONENT_DEBUG_LABELS] ?: false }

    fun showRenderLayoutDebugOverlayFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY] ?: false }

    fun showRenderOalMarkersFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_SHOW_RENDER_OAL_MARKERS] ?: false }

    fun verboseLoggingEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_VERBOSE_LOGGING_ENABLED] ?: false }

    fun verboseLoggingRenderFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_VERBOSE_LOGGING_RENDER] ?: false }

    fun verboseLoggingOalFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_VERBOSE_LOGGING_OAL] ?: false }

    fun verboseLoggingPdfFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_VERBOSE_LOGGING_PDF] ?: false }

    fun verboseLoggingIoFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_VERBOSE_LOGGING_IO] ?: false }

    fun achievementsEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_ACHIEVEMENTS_ENABLED] ?: false }

    fun unlockedAchievementIdsFlow(ctx: Context): Flow<Set<String>> =
        ctx.settingsDataStore.data.map { p -> p[KEY_UNLOCKED_ACHIEVEMENT_IDS] ?: emptySet() }

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

    suspend fun setDevOptionsEnabled(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_DEV_OPTIONS_ENABLED] = enabled }
    }

    suspend fun setShowOalDebugLabel(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_OAL_DEBUG_LABEL] = show }
    }

    suspend fun setShowOalHelperLine(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_OAL_HELPER_LINE] = show }
    }

    suspend fun setShowComponentDebugLabels(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_COMPONENT_DEBUG_LABELS] = show }
    }

    suspend fun setShowRenderLayoutDebugOverlay(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY] = show }
    }

    suspend fun setShowRenderOalMarkers(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_RENDER_OAL_MARKERS] = show }
    }

    suspend fun setVerboseLoggingEnabled(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_VERBOSE_LOGGING_ENABLED] = enabled }
    }

    suspend fun setVerboseLoggingRender(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_VERBOSE_LOGGING_RENDER] = enabled }
    }

    suspend fun setVerboseLoggingOal(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_VERBOSE_LOGGING_OAL] = enabled }
    }

    suspend fun setVerboseLoggingPdf(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_VERBOSE_LOGGING_PDF] = enabled }
    }

    suspend fun setVerboseLoggingIo(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_VERBOSE_LOGGING_IO] = enabled }
    }

    suspend fun setAchievementsEnabled(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_ACHIEVEMENTS_ENABLED] = enabled }
    }

    /** Returns true if the achievement was newly unlocked by this call. */
    suspend fun unlockAchievement(ctx: Context, id: String): Boolean {
        var added = false
        ctx.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_UNLOCKED_ACHIEVEMENT_IDS]?.toMutableSet() ?: mutableSetOf()
            added = current.add(id)
            prefs[KEY_UNLOCKED_ACHIEVEMENT_IDS] = current
        }
        return added
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
