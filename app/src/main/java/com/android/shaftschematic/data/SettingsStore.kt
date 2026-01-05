package com.android.shaftschematic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorSetting
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

    // Preview colors (theme roles; preview-only)
    private val KEY_PREVIEW_BW_ONLY = booleanPreferencesKey("preview_bw_only")

    // PDF export
    private val KEY_OPEN_PDF_AFTER_EXPORT = booleanPreferencesKey("open_pdf_after_export")

    // Legacy role-only keys (kept for migration)
    private val KEY_PREVIEW_OUTLINE_ROLE = stringPreferencesKey("preview_outline_role")
    private val KEY_PREVIEW_BODY_FILL_ROLE = stringPreferencesKey("preview_body_fill_role")
    private val KEY_PREVIEW_TAPER_FILL_ROLE = stringPreferencesKey("preview_taper_fill_role")
    private val KEY_PREVIEW_LINER_FILL_ROLE = stringPreferencesKey("preview_liner_fill_role")
    private val KEY_PREVIEW_THREAD_FILL_ROLE = stringPreferencesKey("preview_thread_fill_role")
    private val KEY_PREVIEW_THREAD_HATCH_ROLE = stringPreferencesKey("preview_thread_hatch_role")

    // New preset + custom keys
    private val KEY_PREVIEW_OUTLINE_PRESET = stringPreferencesKey("preview_outline_preset")
    private val KEY_PREVIEW_OUTLINE_CUSTOM_ROLE = stringPreferencesKey("preview_outline_custom_role")

    private val KEY_PREVIEW_BODY_FILL_PRESET = stringPreferencesKey("preview_body_fill_preset")
    private val KEY_PREVIEW_BODY_FILL_CUSTOM_ROLE = stringPreferencesKey("preview_body_fill_custom_role")

    private val KEY_PREVIEW_TAPER_FILL_PRESET = stringPreferencesKey("preview_taper_fill_preset")
    private val KEY_PREVIEW_TAPER_FILL_CUSTOM_ROLE = stringPreferencesKey("preview_taper_fill_custom_role")

    private val KEY_PREVIEW_LINER_FILL_PRESET = stringPreferencesKey("preview_liner_fill_preset")
    private val KEY_PREVIEW_LINER_FILL_CUSTOM_ROLE = stringPreferencesKey("preview_liner_fill_custom_role")

    private val KEY_PREVIEW_THREAD_FILL_PRESET = stringPreferencesKey("preview_thread_fill_preset")
    private val KEY_PREVIEW_THREAD_FILL_CUSTOM_ROLE = stringPreferencesKey("preview_thread_fill_custom_role")

    private val KEY_PREVIEW_THREAD_HATCH_PRESET = stringPreferencesKey("preview_thread_hatch_preset")
    private val KEY_PREVIEW_THREAD_HATCH_CUSTOM_ROLE = stringPreferencesKey("preview_thread_hatch_custom_role")

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

    fun previewBlackWhiteOnlyFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_PREVIEW_BW_ONLY] ?: false }

    fun openPdfAfterExportFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsDataStore.data.map { p -> p[KEY_OPEN_PDF_AFTER_EXPORT] ?: false }

    private fun parseRole(raw: String?, fallback: PreviewColorRole): PreviewColorRole {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { PreviewColorRole.valueOf(raw) }.getOrElse { fallback }
    }

    private fun parsePreset(raw: String?, fallback: PreviewColorPreset): PreviewColorPreset {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { PreviewColorPreset.valueOf(raw) }.getOrElse { fallback }
    }

    private fun legacyRoleToSetting(role: PreviewColorRole, legacyDefaultPreset: PreviewColorPreset): PreviewColorSetting {
        val preset = when (role) {
            PreviewColorRole.TRANSPARENT -> PreviewColorPreset.TRANSPARENT
            PreviewColorRole.SURFACE_VARIANT -> PreviewColorPreset.STAINLESS
            PreviewColorRole.OUTLINE, PreviewColorRole.ON_SURFACE, PreviewColorRole.MONOCHROME -> PreviewColorPreset.STEEL
            PreviewColorRole.TERTIARY -> PreviewColorPreset.BRONZE
            else -> PreviewColorPreset.CUSTOM
        }
        return if (preset == PreviewColorPreset.CUSTOM) {
            PreviewColorSetting(preset = preset, customRole = role)
        } else {
            // If we can map, prefer the mapped preset (over legacy default).
            PreviewColorSetting(preset = preset)
        }
    }

    private fun parseSetting(
        presetRaw: String?,
        customRoleRaw: String?,
        legacyRoleRaw: String?,
        defaultPreset: PreviewColorPreset,
        defaultCustomRole: PreviewColorRole = PreviewColorRole.PRIMARY,
    ): PreviewColorSetting {
        // New format takes precedence when present.
        val preset = parsePreset(presetRaw, fallback = PreviewColorPreset.CUSTOM)
        val hasNew = !presetRaw.isNullOrBlank()
        if (hasNew) {
            val customRole = parseRole(customRoleRaw, fallback = defaultCustomRole)
            return PreviewColorSetting(preset = preset, customRole = customRole)
        }

        // Legacy: role-only.
        val legacyRole = parseRole(legacyRoleRaw, fallback = when (defaultPreset) {
            PreviewColorPreset.TRANSPARENT -> PreviewColorRole.TRANSPARENT
            PreviewColorPreset.STAINLESS -> PreviewColorRole.SURFACE_VARIANT
            PreviewColorPreset.STEEL -> PreviewColorRole.OUTLINE
            PreviewColorPreset.BRONZE -> PreviewColorRole.TERTIARY
            PreviewColorPreset.CUSTOM -> defaultCustomRole
        })
        return legacyRoleToSetting(legacyRole, legacyDefaultPreset = defaultPreset)
    }

    /**
     * Test hook: exercises the same preview-color parsing/migration logic used by the
     * DataStore-backed flows, without requiring an Android [Context].
     */
    internal fun parsePreviewColorSettingForTest(
        presetRaw: String?,
        customRoleRaw: String?,
        legacyRoleRaw: String?,
        defaultPreset: PreviewColorPreset,
        defaultCustomRole: PreviewColorRole = PreviewColorRole.PRIMARY,
    ): PreviewColorSetting =
        parseSetting(
            presetRaw = presetRaw,
            customRoleRaw = customRoleRaw,
            legacyRoleRaw = legacyRoleRaw,
            defaultPreset = defaultPreset,
            defaultCustomRole = defaultCustomRole
        )

    fun previewOutlineSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_OUTLINE_PRESET],
                customRoleRaw = p[KEY_PREVIEW_OUTLINE_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_OUTLINE_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    fun previewBodyFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_BODY_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_BODY_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_BODY_FILL_ROLE],
                defaultPreset = PreviewColorPreset.TRANSPARENT,
                defaultCustomRole = PreviewColorRole.PRIMARY
            )
        }

    fun previewTaperFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_TAPER_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_TAPER_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_TAPER_FILL_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    fun previewLinerFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_LINER_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_LINER_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_LINER_FILL_ROLE],
                defaultPreset = PreviewColorPreset.BRONZE,
                defaultCustomRole = PreviewColorRole.TERTIARY
            )
        }

    fun previewThreadFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_THREAD_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_THREAD_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_THREAD_FILL_ROLE],
                defaultPreset = PreviewColorPreset.TRANSPARENT,
                defaultCustomRole = PreviewColorRole.PRIMARY
            )
        }

    fun previewThreadHatchSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsDataStore.data.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_THREAD_HATCH_PRESET],
                customRoleRaw = p[KEY_PREVIEW_THREAD_HATCH_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_THREAD_HATCH_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    suspend fun setDefaultUnit(ctx: Context, unit: UnitPref) {
        ctx.settingsDataStore.edit { it[KEY_DEFAULT_UNIT] = if (unit == UnitPref.INCHES) 1 else 0 }
    }

    suspend fun setShowGrid(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_GRID] = show }
    }

    suspend fun setShowComponentArrows(ctx: Context, show: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_SHOW_COMPONENT_ARROWS] = show }
    }

    suspend fun setOpenPdfAfterExport(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_OPEN_PDF_AFTER_EXPORT] = enabled }
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

    suspend fun setPreviewOutlineSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_OUTLINE_PRESET] = setting.preset.name
            it[KEY_PREVIEW_OUTLINE_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewBodyFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_BODY_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_BODY_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewTaperFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_TAPER_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_TAPER_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewLinerFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_LINER_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_LINER_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewThreadFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_THREAD_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_THREAD_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewThreadHatchSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.settingsDataStore.edit {
            it[KEY_PREVIEW_THREAD_HATCH_PRESET] = setting.preset.name
            it[KEY_PREVIEW_THREAD_HATCH_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewBlackWhiteOnly(ctx: Context, enabled: Boolean) {
        ctx.settingsDataStore.edit { it[KEY_PREVIEW_BW_ONLY] = enabled }
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
