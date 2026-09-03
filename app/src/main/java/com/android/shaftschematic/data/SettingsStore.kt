
package com.android.shaftschematic.data
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.MutablePreferences
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.pdf.PdfExportMode
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.settings.AppThemeMode
import com.android.shaftschematic.settings.DRAWING_LINE_THICKNESS_DEFAULT
import com.android.shaftschematic.settings.DRAWING_LINE_THICKNESS_MAX
import com.android.shaftschematic.settings.DRAWING_LINE_THICKNESS_MIN
import com.android.shaftschematic.settings.DRAWING_PROFILE_MAX_COUNT
import com.android.shaftschematic.settings.DrawingProfile
import com.android.shaftschematic.settings.decodeDrawingProfiles
import com.android.shaftschematic.settings.encodeDrawingProfiles
import com.android.shaftschematic.settings.normalizeDrawingProfileName
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_SMALL_PT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MAX
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MIN
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_MAX_MM
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_MIN_MM
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_DROP_SCALE_MAX
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_DROP_SCALE_MIN
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_SCALE_MAX
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_SCALE_MIN
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MAX_IN
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MIN_IN
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UndercutShadeColor
import com.android.shaftschematic.util.UndercutShadeIntensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Every preference in the app lives in this one file — units, theme, the whole drawing look, the
 * backup-mirror folder, and the migration/seeding flags read during startup.
 *
 * The corruption handler is load-bearing, not boilerplate. A truncated `settings.preferences_pb`
 * is what a tablet yanked off power mid-write leaves behind, and DataStore's unhandled answer to
 * one is to throw `CorruptionException` from **every** read — so the failure would not be
 * "settings went back to default" but the app crashing on launch, permanently, recoverable only
 * by clearing app data (which takes the drawings with it). Replacing the file costs the user
 * their preferences and nothing else; the saved shafts live in `filesDir/shafts`.
 *
 * Same posture `decodeDrawingProfiles` already takes one level up: a corrupt value may cost the
 * presets, never the screen. `SettingsStoreCorruptionTest` pins both halves.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The read seam. Every preference read in the app comes through this flow.
 *
 * The `catch` is the running-cost counterpart to the delegate's corruption handler: that repairs
 * a broken file once, this keeps a read that fails for any other reason (an I/O error, a device
 * that has run out of disk) from propagating into a Compose collector, where it would take down
 * whatever screen happened to be reading a preference. A preference that answers with its default
 * is a cosmetic problem; a crash is not.
 */
private val Context.settingsPrefs: Flow<Preferences>
    get() = settingsDataStore.data.catch { t ->
        AppLog.e("Settings", "preference read failed", t)
        emit(emptyPreferences())
    }

/**
 * The write seam, with the same reasoning: preference writes are fired from `scope.launch` all
 * over the UI with nothing catching above them, so a full disk would turn a toggle into a crash.
 * A write that could not land leaves the toggle where it was — visible, and recoverable.
 *
 * `CancellationException` is rethrown rather than swallowed, so structured concurrency still
 * works for callers that race writes.
 */
private suspend fun Context.editSettings(block: suspend (MutablePreferences) -> Unit) {
    try {
        settingsDataStore.edit(block)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        AppLog.e("Settings", "preference write failed", t)
    }
}

object SettingsStore {
    private val KEY_DEFAULT_UNIT = intPreferencesKey("default_unit") // 0=MM, 1=IN
    private val KEY_SHOW_GRID    = booleanPreferencesKey("show_grid")
    private val KEY_SHOW_COMPONENT_ARROWS = booleanPreferencesKey("show_component_arrows")
    private val KEY_COMPONENT_ARROW_WIDTH_DP = intPreferencesKey("component_arrow_width_dp")
    private val KEY_SHOW_HIGHLIGHT_SELECTION = booleanPreferencesKey("show_highlight_selection")

    // Developer options (hidden behind About taps)
    private val KEY_DEV_OPTIONS_ENABLED = booleanPreferencesKey("dev_options_enabled")
    private val KEY_SHOW_OAL_DEBUG_LABEL = booleanPreferencesKey("show_oal_debug_label")
    private val KEY_SHOW_OAL_IN_PREVIEW_BOX = booleanPreferencesKey("show_oal_in_preview_box")

    // Developer options (debug tooling)
    private val KEY_SHOW_COMPONENT_DEBUG_LABELS = booleanPreferencesKey("show_component_debug_labels")
    private val KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY = booleanPreferencesKey("show_render_layout_debug_overlay")
    private val KEY_SHOW_RENDER_OAL_MARKERS = booleanPreferencesKey("show_render_oal_markers")
    private val KEY_SHOW_DIM_DEBUG_OVERLAY = booleanPreferencesKey("show_dim_debug_overlay")
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

    // Appearance (app-wide theme)
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")

    // Undercut drawing style (on-screen only; the printed PDF keeps standard drawing colors)
    private val KEY_UNDERCUT_LINE_ART = booleanPreferencesKey("undercut_line_art")
    private val KEY_UNDERCUT_SHADE_COLOR = stringPreferencesKey("undercut_shade_color")
    private val KEY_UNDERCUT_SHADE_INTENSITY = stringPreferencesKey("undercut_shade_intensity")

    // PDF export
    private val KEY_OPEN_PDF_AFTER_EXPORT = booleanPreferencesKey("open_pdf_after_export")
    private val KEY_PDF_TIERING_MODE = stringPreferencesKey("pdf_tiering_mode")
    private val KEY_PDF_SHOW_COMPONENT_TITLES = booleanPreferencesKey("pdf_show_component_titles")
    private val KEY_PDF_EXPORT_MODE = stringPreferencesKey("pdf_export_mode")
    // Mixed units + dual display. Capability gate for per-component unit chips, and the
    // global default for new documents' dual-unit display (a document persists its own value).
    private val KEY_PER_COMPONENT_UNITS = booleanPreferencesKey("per_component_units")
    private val KEY_DUAL_UNITS_DEFAULT  = booleanPreferencesKey("dual_units_default")
    // Liner shoulders. Capability gate for the AUTHORING UI only (liner card + Add Liner
    // dialog sections) — most shop drawings never carry shoulders, so the controls stay
    // hidden until wanted. Stored shoulder data always draws and a liner that carries any
    // keeps its controls visible whatever this says: a device pref may hide empty entry
    // fields, never authored work.
    private val KEY_LINER_SHOULDERS_ENABLED = booleanPreferencesKey("liner_shoulders_enabled")
    // Add-dialog unit converter icon. Capability gate for the title-row calculator button on
    // the five Add dialogs only — most shops work in one unit, so the icon is noise on every
    // add (on-device request). The sidebar "Unit converter" Tools entry is a separate, always-
    // available launcher and is not gated by this pref.
    private val KEY_DIALOG_UNIT_CONVERTER_ENABLED = booleanPreferencesKey("dialog_unit_converter_enabled")
    private val KEY_PDF_SHADED_BODIES  = booleanPreferencesKey("pdf_shaded_bodies")
    private val KEY_PDF_SHADED_TAPERS  = booleanPreferencesKey("pdf_shaded_tapers")
    private val KEY_PDF_SHADED_LINERS  = booleanPreferencesKey("pdf_shaded_liners")
    private val KEY_PDF_CURVE_LO_HEIGHT_IN = floatPreferencesKey("pdf_curve_lo_height_in")
    private val KEY_PDF_CURVE_HI_HEIGHT_IN = floatPreferencesKey("pdf_curve_hi_height_in")
    private val KEY_PDF_SBREAK_THRESHOLD_FRAC = floatPreferencesKey("pdf_sbreak_threshold_frac")
    private val KEY_PDF_ARROW_SIZE_PT = floatPreferencesKey("pdf_arrow_size_pt")
    private val KEY_PDF_FRACTION_STYLE = stringPreferencesKey("pdf_fraction_style")
    private val KEY_PDF_DUAL_UNIT_LAYOUT = stringPreferencesKey("pdf_dual_unit_layout")
    private val KEY_PDF_WEAR_TRACE_DEPTH_FRAC = floatPreferencesKey("pdf_wear_trace_depth_frac")
    private val KEY_PDF_WEAR_BAND_SHADE_FRAC = floatPreferencesKey("pdf_wear_band_shade_frac")
    private val KEY_PDF_WEAR_JOIN_GAP_MAX_MM = floatPreferencesKey("pdf_wear_join_gap_max_mm")
    private val KEY_PDF_SHADE_EXPLICIT_BODIES_ONLY =
        booleanPreferencesKey("pdf_shade_explicit_bodies_only")
    private val KEY_PDF_RUNOUT_BUBBLE_SCALE = floatPreferencesKey("pdf_runout_bubble_scale")
    private val KEY_PDF_RUNOUT_BUBBLE_DROP_SCALE =
        floatPreferencesKey("pdf_runout_bubble_drop_scale")

    // Drawing line thickness (applies to both preview and PDF)
    private val KEY_LINE_THICKNESS_SCALE = floatPreferencesKey("line_thickness_scale")
    fun pdfTieringModeFlow(ctx: Context): Flow<PdfTieringMode> =
        ctx.settingsPrefs.map { p -> PdfTieringMode.fromName(p[KEY_PDF_TIERING_MODE]) }

    suspend fun setPdfTieringMode(ctx: Context, mode: PdfTieringMode) {
        ctx.editSettings { it[KEY_PDF_TIERING_MODE] = mode.name }
    }

    fun pdfShowComponentTitlesFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_SHOW_COMPONENT_TITLES] ?: true }

    suspend fun setPdfShowComponentTitles(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_PDF_SHOW_COMPONENT_TITLES] = show }
    }

    /** Capability: show a per-component in/mm chip in the carousel cards and Add dialogs. */
    fun perComponentUnitsFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PER_COMPONENT_UNITS] ?: false }
    suspend fun setPerComponentUnits(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_PER_COMPONENT_UNITS] = v }
    }

    /**
     * Capability: show liner shoulder controls on liner cards and in the Add Liner dialog.
     * A liner already carrying shoulder values shows its controls regardless — see the key's
     * comment.
     */
    fun linerShouldersEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_LINER_SHOULDERS_ENABLED] ?: false }
    suspend fun setLinerShouldersEnabled(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_LINER_SHOULDERS_ENABLED] = v }
    }

    /**
     * Capability: show the unit-converter calculator icon in the title row of the five Add
     * dialogs. The sidebar Tools entry stays available regardless of this flag.
     */
    fun dialogUnitConverterEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_DIALOG_UNIT_CONVERTER_ENABLED] ?: false }
    suspend fun setDialogUnitConverterEnabled(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_DIALOG_UNIT_CONVERTER_ENABLED] = v }
    }

    /** Global default for new documents' inline dual-unit display. */
    fun dualUnitsDefaultFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_DUAL_UNITS_DEFAULT] ?: false }
    suspend fun setDualUnitsDefault(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_DUAL_UNITS_DEFAULT] = v }
    }

    fun pdfShadedBodiesFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_SHADED_BODIES] ?: false }
    suspend fun setPdfShadedBodies(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_PDF_SHADED_BODIES] = v }
    }

    fun pdfShadedTapersFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_SHADED_TAPERS] ?: false }
    suspend fun setPdfShadedTapers(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_PDF_SHADED_TAPERS] = v }
    }

    fun pdfShadedLinersFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_SHADED_LINERS] ?: false }
    suspend fun setPdfShadedLiners(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_PDF_SHADED_LINERS] = v }
    }

    // Sizing-curve anchor heights (paper inches): what a 4" / 8" shaft draws by default.
    fun pdfCurveLoHeightInFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_CURVE_LO_HEIGHT_IN] ?: PdfPrefs().curveLoHeightIn }
    suspend fun setPdfCurveLoHeightIn(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_CURVE_LO_HEIGHT_IN] = v.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN)
        }
    }

    fun pdfCurveHiHeightInFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_CURVE_HI_HEIGHT_IN] ?: PdfPrefs().curveHiHeightIn }
    suspend fun setPdfCurveHiHeightIn(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_CURVE_HI_HEIGHT_IN] = v.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN)
        }
    }

    // Body S-break threshold: fraction of true width below which a compressed body run
    // shows the S-break pair. 0 = never break on compression.
    fun pdfSBreakThresholdFracFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_SBREAK_THRESHOLD_FRAC] ?: PdfPrefs().sBreakThresholdFrac }
    suspend fun setPdfSBreakThresholdFrac(ctx: Context, v: Float) {
        ctx.editSettings { it[KEY_PDF_SBREAK_THRESHOLD_FRAC] = v.coerceIn(0f, 1f) }
    }

    // Default worn-profile trace exaggeration: how deep the deepest liner reading draws, as a
    // fraction of the drawn radius. A job may pin its own value (WearRecord.traceDepthFrac).
    fun pdfWearTraceDepthFracFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            (p[KEY_PDF_WEAR_TRACE_DEPTH_FRAC] ?: PdfPrefs().wearTraceDepthFrac)
                .coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)
        }
    suspend fun setPdfWearTraceDepthFrac(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_WEAR_TRACE_DEPTH_FRAC] = v.coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)
        }
    }

    // Grey of a wear area's fill in the wear document's detail strips, as a fraction of full
    // black. App-wide (no per-job override); the cap keeps pit "X"s legible over the band.
    fun pdfWearBandShadeFracFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            (p[KEY_PDF_WEAR_BAND_SHADE_FRAC] ?: PdfPrefs().wearBandShadeFrac)
                .coerceIn(PDF_WEAR_BAND_SHADE_MIN, PDF_WEAR_BAND_SHADE_MAX)
        }
    suspend fun setPdfWearBandShadeFrac(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_WEAR_BAND_SHADE_FRAC] = v.coerceIn(PDF_WEAR_BAND_SHADE_MIN, PDF_WEAR_BAND_SHADE_MAX)
        }
    }

    // How much bare shaft may sit between two components in one wear detail strip before the run
    // compresses to an S-break. Canonical mm; the UI converts for display only. App-wide.
    fun pdfWearJoinGapMaxMmFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            (p[KEY_PDF_WEAR_JOIN_GAP_MAX_MM] ?: PdfPrefs().wearJoinGapMaxMm)
                .coerceIn(PDF_WEAR_JOIN_GAP_MIN_MM, PDF_WEAR_JOIN_GAP_MAX_MM)
        }
    suspend fun setPdfWearJoinGapMaxMm(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_WEAR_JOIN_GAP_MAX_MM] = v.coerceIn(PDF_WEAR_JOIN_GAP_MIN_MM, PDF_WEAR_JOIN_GAP_MAX_MM)
        }
    }

    // Narrows the body shade to authored sections: with pdf_shaded_bodies on, auto (bare-shaft)
    // runs draw unfilled. Subtractive — meaningless on its own.
    fun pdfShadeExplicitBodiesOnlyFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p ->
            p[KEY_PDF_SHADE_EXPLICIT_BODIES_ONLY] ?: PdfPrefs().shadeExplicitBodiesOnly
        }
    suspend fun setPdfShadeExplicitBodiesOnly(ctx: Context, v: Boolean) {
        ctx.editSettings { it[KEY_PDF_SHADE_EXPLICIT_BODIES_ONLY] = v }
    }

    // Runout bubble radius multiplier — both draw sites (sheet and canvas preview) read it.
    fun pdfRunoutBubbleScaleFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            (p[KEY_PDF_RUNOUT_BUBBLE_SCALE] ?: PdfPrefs().runoutBubbleScale)
                .coerceIn(PDF_RUNOUT_BUBBLE_SCALE_MIN, PDF_RUNOUT_BUBBLE_SCALE_MAX)
        }
    suspend fun setPdfRunoutBubbleScale(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_RUNOUT_BUBBLE_SCALE] =
                v.coerceIn(PDF_RUNOUT_BUBBLE_SCALE_MIN, PDF_RUNOUT_BUBBLE_SCALE_MAX)
        }
    }

    // How far the first bubble row hangs below the shaft, as a multiplier on the shipped drop.
    fun pdfRunoutBubbleDropScaleFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            (p[KEY_PDF_RUNOUT_BUBBLE_DROP_SCALE] ?: PdfPrefs().runoutBubbleDropScale)
                .coerceIn(PDF_RUNOUT_BUBBLE_DROP_SCALE_MIN, PDF_RUNOUT_BUBBLE_DROP_SCALE_MAX)
        }
    suspend fun setPdfRunoutBubbleDropScale(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_RUNOUT_BUBBLE_DROP_SCALE] =
                v.coerceIn(PDF_RUNOUT_BUBBLE_DROP_SCALE_MIN, PDF_RUNOUT_BUBBLE_DROP_SCALE_MAX)
        }
    }

    // Dimension-rail arrowhead size (pt): one of PDF_ARROW_SIZES_PT.
    // How a fraction is SET wherever the app draws one — previews included, since both draw
    // families go through the one renderer.
    fun pdfFractionStyleFlow(ctx: Context): Flow<FractionStyle> =
        ctx.settingsPrefs.map { p -> FractionStyle.fromName(p[KEY_PDF_FRACTION_STYLE]) }
    suspend fun setPdfFractionStyle(ctx: Context, style: FractionStyle) {
        ctx.editSettings { it[KEY_PDF_FRACTION_STYLE] = style.name }
    }

    // How a dual value is SET on the drawing: an inline one-liner or a two-line stack. Only ever
    // visible on a document with dual units switched on.
    fun pdfDualUnitLayoutFlow(ctx: Context): Flow<DualUnitLayout> =
        ctx.settingsPrefs.map { p -> DualUnitLayout.fromName(p[KEY_PDF_DUAL_UNIT_LAYOUT]) }
    suspend fun setPdfDualUnitLayout(ctx: Context, layout: DualUnitLayout) {
        ctx.editSettings { it[KEY_PDF_DUAL_UNIT_LAYOUT] = layout.name }
    }

    fun pdfArrowSizePtFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p -> p[KEY_PDF_ARROW_SIZE_PT] ?: PdfPrefs().arrowSizePt }
    suspend fun setPdfArrowSizePt(ctx: Context, v: Float) {
        ctx.editSettings {
            it[KEY_PDF_ARROW_SIZE_PT] = v.coerceIn(PDF_ARROW_SIZE_SMALL_PT, PDF_ARROW_SIZE_LARGE_PT)
        }
    }

    fun pdfExportModeFlow(ctx: Context): Flow<PdfExportMode> =
        ctx.settingsPrefs.map { p ->
            runCatching { PdfExportMode.valueOf(p[KEY_PDF_EXPORT_MODE] ?: "Standard") }
                .getOrDefault(PdfExportMode.Standard)
        }

    suspend fun setPdfExportMode(ctx: Context, mode: PdfExportMode) {
        ctx.editSettings { it[KEY_PDF_EXPORT_MODE] = mode.name }
    }


    fun lineThicknessScaleFlow(ctx: Context): Flow<Float> =
        ctx.settingsPrefs.map { p ->
            p[KEY_LINE_THICKNESS_SCALE] ?: DRAWING_LINE_THICKNESS_DEFAULT
        }

    suspend fun setLineThicknessScale(ctx: Context, scale: Float) {
        ctx.editSettings {
            it[KEY_LINE_THICKNESS_SCALE] =
                scale.coerceIn(DRAWING_LINE_THICKNESS_MIN, DRAWING_LINE_THICKNESS_MAX)
        }
    }

    // ── Drawing preset profiles (app-wide) ────────────────────────────────────
    // Named sets of drawing-look prefs, stored as one JSON name → profile map — the
    // seeded-sample ledger's posture: an unreadable value degrades to "no saved profiles"
    // rather than failing the settings read. Nothing here is per-document: a profile is a
    // device preference, and applying one is a one-shot copy into the live prefs.

    private val KEY_DRAWING_PROFILES = stringPreferencesKey("drawing_profiles")

    fun drawingProfilesFlow(ctx: Context): Flow<Map<String, DrawingProfile>> =
        ctx.settingsPrefs.map { p -> decodeDrawingProfiles(p[KEY_DRAWING_PROFILES]) }

    suspend fun getDrawingProfiles(ctx: Context): Map<String, DrawingProfile> =
        decodeDrawingProfiles(ctx.settingsPrefs.first()[KEY_DRAWING_PROFILES])

    /**
     * Saves [profile] under [name], replacing any profile already stored under it. Returns false
     * when the name is empty or the store is at [DRAWING_PROFILE_MAX_COUNT] and this would be a
     * new entry — overwriting an existing profile is always allowed.
     */
    suspend fun saveDrawingProfile(ctx: Context, name: String, profile: DrawingProfile): Boolean {
        val key = normalizeDrawingProfileName(name) ?: return false
        var saved = false
        ctx.editSettings { prefs ->
            val current = decodeDrawingProfiles(prefs[KEY_DRAWING_PROFILES])
            if (!current.containsKey(key) && current.size >= DRAWING_PROFILE_MAX_COUNT) return@editSettings
            prefs[KEY_DRAWING_PROFILES] = encodeDrawingProfiles(current + (key to profile))
            saved = true
        }
        return saved
    }

    suspend fun deleteDrawingProfile(ctx: Context, name: String) {
        ctx.editSettings { prefs ->
            val current = decodeDrawingProfiles(prefs[KEY_DRAWING_PROFILES])
            if (!current.containsKey(name)) return@editSettings
            prefs[KEY_DRAWING_PROFILES] = encodeDrawingProfiles(current - name)
        }
    }

    /** Returns false when [from] is gone or [to] is empty or already taken by another profile. */
    suspend fun renameDrawingProfile(ctx: Context, from: String, to: String): Boolean {
        val target = normalizeDrawingProfileName(to) ?: return false
        var renamed = false
        ctx.editSettings { prefs ->
            val current = decodeDrawingProfiles(prefs[KEY_DRAWING_PROFILES])
            val profile = current[from] ?: return@editSettings
            if (target != from && current.containsKey(target)) return@editSettings
            prefs[KEY_DRAWING_PROFILES] =
                encodeDrawingProfiles(current - from + (target to profile))
            renamed = true
        }
        return renamed
    }

    // ── Backup auto-mirror folder ─────────────────────────────────────────────
    // The SAF tree URI every saved shaft document is copied to, so the off-device backup is
    // always current. Stored as the raw URI string; absent/blank means mirroring is off.
    //
    // The stored value is only ever cleared by the user. A mirror write that finds its grant
    // revoked skips and logs — clearing on failure would turn the feature off behind the user's
    // back, and re-granting the same folder must be enough to resume.

    private val KEY_BACKUP_MIRROR_TREE_URI = stringPreferencesKey("backup_mirror_tree_uri")

    fun backupMirrorFolderUriFlow(ctx: Context): Flow<String?> =
        ctx.settingsPrefs.map { p -> p[KEY_BACKUP_MIRROR_TREE_URI]?.takeIf { it.isNotBlank() } }

    suspend fun getBackupMirrorFolderUri(ctx: Context): String? =
        ctx.settingsPrefs.first()[KEY_BACKUP_MIRROR_TREE_URI]?.takeIf { it.isNotBlank() }

    /** Passing null (or a blank string) stops mirroring. */
    suspend fun setBackupMirrorFolderUri(ctx: Context, uri: String?) {
        ctx.editSettings { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(KEY_BACKUP_MIRROR_TREE_URI)
            else prefs[KEY_BACKUP_MIRROR_TREE_URI] = uri
        }
    }

    // One-time migrations
    private val KEY_MIGRATED_INTERNAL_DOCS_TO_SHAFT = booleanPreferencesKey("migrated_internal_docs_to_shaft")

    // Bundled sample documents seeding (internal storage)
    private val KEY_SAMPLE_SEED_VERSION = intPreferencesKey("sample_seed_version")
    private const val CURRENT_SAMPLE_SEED_VERSION = 4

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
        ctx.settingsPrefs.map { p ->
            when (p[KEY_DEFAULT_UNIT] ?: 0) { 1 -> UnitPref.INCHES; else -> UnitPref.MILLIMETERS }
        }

    fun showGridFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_GRID] ?: false }

    fun showComponentArrowsFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_COMPONENT_ARROWS] ?: false }

    fun showHighlightSelectionFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_HIGHLIGHT_SELECTION] ?: true }

    fun componentArrowWidthDpFlow(ctx: Context): Flow<Int> =
        ctx.settingsPrefs.map { p -> p[KEY_COMPONENT_ARROW_WIDTH_DP] ?: 40 }
    fun devOptionsEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_DEV_OPTIONS_ENABLED] ?: false }

    fun showOalDebugLabelFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_OAL_DEBUG_LABEL] ?: false }

    fun showOalInPreviewBoxFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_OAL_IN_PREVIEW_BOX] ?: false }

    fun showComponentDebugLabelsFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_COMPONENT_DEBUG_LABELS] ?: false }

    fun showRenderLayoutDebugOverlayFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY] ?: false }

    fun showRenderOalMarkersFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_RENDER_OAL_MARKERS] ?: false }

    fun showDimDebugOverlayFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_SHOW_DIM_DEBUG_OVERLAY] ?: false }

    fun verboseLoggingEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_VERBOSE_LOGGING_ENABLED] ?: false }

    fun verboseLoggingRenderFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_VERBOSE_LOGGING_RENDER] ?: false }

    fun verboseLoggingOalFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_VERBOSE_LOGGING_OAL] ?: false }

    fun verboseLoggingPdfFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_VERBOSE_LOGGING_PDF] ?: false }

    fun verboseLoggingIoFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_VERBOSE_LOGGING_IO] ?: false }

    fun achievementsEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_ACHIEVEMENTS_ENABLED] ?: false }

    fun unlockedAchievementIdsFlow(ctx: Context): Flow<Set<String>> =
        ctx.settingsPrefs.map { p -> p[KEY_UNLOCKED_ACHIEVEMENT_IDS] ?: emptySet() }

    fun previewBlackWhiteOnlyFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_PREVIEW_BW_ONLY] ?: false }

    fun themeModeFlow(ctx: Context): Flow<AppThemeMode> =
        ctx.settingsPrefs.map { p -> AppThemeMode.fromName(p[KEY_THEME_MODE]) }

    suspend fun setThemeMode(ctx: Context, mode: AppThemeMode) {
        ctx.editSettings { it[KEY_THEME_MODE] = mode.name }
    }

    fun highContrastFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_HIGH_CONTRAST] ?: false }

    suspend fun setHighContrast(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_HIGH_CONTRAST] = enabled }
    }

    fun undercutLineArtFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_UNDERCUT_LINE_ART] ?: false }

    suspend fun setUndercutLineArt(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_UNDERCUT_LINE_ART] = enabled }
    }

    fun undercutShadeColorFlow(ctx: Context): Flow<UndercutShadeColor> =
        ctx.settingsPrefs.map { p -> UndercutShadeColor.fromName(p[KEY_UNDERCUT_SHADE_COLOR]) }

    suspend fun setUndercutShadeColor(ctx: Context, color: UndercutShadeColor) {
        ctx.editSettings { it[KEY_UNDERCUT_SHADE_COLOR] = color.name }
    }

    fun undercutShadeIntensityFlow(ctx: Context): Flow<UndercutShadeIntensity> =
        ctx.settingsPrefs.map { p -> UndercutShadeIntensity.fromName(p[KEY_UNDERCUT_SHADE_INTENSITY]) }

    suspend fun setUndercutShadeIntensity(ctx: Context, intensity: UndercutShadeIntensity) {
        ctx.editSettings { it[KEY_UNDERCUT_SHADE_INTENSITY] = intensity.name }
    }

    fun openPdfAfterExportFlow(ctx: Context): Flow<Boolean> =
        ctx.settingsPrefs.map { p -> p[KEY_OPEN_PDF_AFTER_EXPORT] ?: false }

    suspend fun internalDocsMigratedToShaft(ctx: Context): Boolean =
        ctx.settingsPrefs.first()[KEY_MIGRATED_INTERNAL_DOCS_TO_SHAFT] ?: false

    suspend fun setInternalDocsMigratedToShaft(ctx: Context, migrated: Boolean) {
        ctx.editSettings { it[KEY_MIGRATED_INTERNAL_DOCS_TO_SHAFT] = migrated }
    }

    /**
     * One-shot flag for the bundled starter templates.
     *
     * Deliberately simpler than the sample-shaft seeder's version + hash ledger: templates are
     * user-owned from the moment they appear, so there is no "update the bundled copy" story to
     * support. Seed once, never again — a user who deletes every starter does not get them back
     * on the next launch.
     */
    private val KEY_STARTER_TEMPLATES_SEEDED = booleanPreferencesKey("starter_templates_seeded")

    suspend fun starterTemplatesSeeded(ctx: Context): Boolean =
        ctx.settingsPrefs.first()[KEY_STARTER_TEMPLATES_SEEDED] ?: false

    suspend fun setStarterTemplatesSeeded(ctx: Context, seeded: Boolean) {
        ctx.editSettings { it[KEY_STARTER_TEMPLATES_SEEDED] = seeded }
    }

    fun currentSampleSeedVersion(): Int = CURRENT_SAMPLE_SEED_VERSION

    suspend fun getSampleSeedVersion(ctx: Context): Int =
        ctx.settingsPrefs.first()[KEY_SAMPLE_SEED_VERSION] ?: 0

    suspend fun setSampleSeedVersion(ctx: Context, v: Int) {
        ctx.editSettings { it[KEY_SAMPLE_SEED_VERSION] = v }
    }

    // ── Seeded-sample ledger (filename → SHA-256 of seeded content) ──────────
    // Lets sample pruning prove a file untouched before deleting it. Stored as
    // a JSON map; an unreadable value degrades to "prune nothing" (safe).

    private val KEY_SEEDED_SAMPLE_HASHES = stringPreferencesKey("seeded_sample_hashes")
    private val seededHashesSerializer = MapSerializer(String.serializer(), String.serializer())

    suspend fun getSeededSampleHashes(ctx: Context): Map<String, String> {
        val raw = ctx.settingsPrefs.first()[KEY_SEEDED_SAMPLE_HASHES] ?: return emptyMap()
        return runCatching {
            Json.decodeFromString(seededHashesSerializer, raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun setSeededSampleHashes(ctx: Context, hashes: Map<String, String>) {
        ctx.editSettings {
            it[KEY_SEEDED_SAMPLE_HASHES] = Json.encodeToString(seededHashesSerializer, hashes)
        }
    }

    // ── Pre-update snapshot gate ──────────────────────────────────────────────

    private val KEY_LAST_SNAPSHOT_VERSION_CODE = intPreferencesKey("last_snapshot_version_code")

    suspend fun getLastSnapshotVersionCode(ctx: Context): Int =
        ctx.settingsPrefs.first()[KEY_LAST_SNAPSHOT_VERSION_CODE] ?: 0

    suspend fun setLastSnapshotVersionCode(ctx: Context, v: Int) {
        ctx.editSettings { it[KEY_LAST_SNAPSHOT_VERSION_CODE] = v }
    }

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
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_OUTLINE_PRESET],
                customRoleRaw = p[KEY_PREVIEW_OUTLINE_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_OUTLINE_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    fun previewBodyFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_BODY_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_BODY_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_BODY_FILL_ROLE],
                defaultPreset = PreviewColorPreset.TRANSPARENT,
                defaultCustomRole = PreviewColorRole.PRIMARY
            )
        }

    fun previewTaperFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_TAPER_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_TAPER_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_TAPER_FILL_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    fun previewLinerFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_LINER_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_LINER_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_LINER_FILL_ROLE],
                defaultPreset = PreviewColorPreset.BRONZE,
                defaultCustomRole = PreviewColorRole.TERTIARY
            )
        }

    fun previewThreadFillSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_THREAD_FILL_PRESET],
                customRoleRaw = p[KEY_PREVIEW_THREAD_FILL_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_THREAD_FILL_ROLE],
                defaultPreset = PreviewColorPreset.TRANSPARENT,
                defaultCustomRole = PreviewColorRole.PRIMARY
            )
        }

    fun previewThreadHatchSettingFlow(ctx: Context): Flow<PreviewColorSetting> =
        ctx.settingsPrefs.map { p ->
            parseSetting(
                presetRaw = p[KEY_PREVIEW_THREAD_HATCH_PRESET],
                customRoleRaw = p[KEY_PREVIEW_THREAD_HATCH_CUSTOM_ROLE],
                legacyRoleRaw = p[KEY_PREVIEW_THREAD_HATCH_ROLE],
                defaultPreset = PreviewColorPreset.STEEL,
                defaultCustomRole = PreviewColorRole.MONOCHROME
            )
        }

    suspend fun setDefaultUnit(ctx: Context, unit: UnitPref) {
        ctx.editSettings { it[KEY_DEFAULT_UNIT] = if (unit == UnitPref.INCHES) 1 else 0 }
    }

    suspend fun setShowGrid(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_GRID] = show }
    }

    suspend fun setShowComponentArrows(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_COMPONENT_ARROWS] = show }
    }

    suspend fun setShowHighlightSelection(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_HIGHLIGHT_SELECTION] = show }
    }

    suspend fun setOpenPdfAfterExport(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_OPEN_PDF_AFTER_EXPORT] = enabled }
    }

    suspend fun setComponentArrowWidthDp(ctx: Context, widthDp: Int) {
        ctx.editSettings { it[KEY_COMPONENT_ARROW_WIDTH_DP] = widthDp }
    }
    suspend fun setDevOptionsEnabled(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_DEV_OPTIONS_ENABLED] = enabled }
    }

    suspend fun setShowOalDebugLabel(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_OAL_DEBUG_LABEL] = show }
    }

    suspend fun setShowOalInPreviewBox(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_OAL_IN_PREVIEW_BOX] = show }
    }

    suspend fun setShowComponentDebugLabels(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_COMPONENT_DEBUG_LABELS] = show }
    }

    suspend fun setShowRenderLayoutDebugOverlay(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY] = show }
    }

    suspend fun setShowRenderOalMarkers(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_RENDER_OAL_MARKERS] = show }
    }

    suspend fun setShowDimDebugOverlay(ctx: Context, show: Boolean) {
        ctx.editSettings { it[KEY_SHOW_DIM_DEBUG_OVERLAY] = show }
    }

    suspend fun setVerboseLoggingEnabled(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_VERBOSE_LOGGING_ENABLED] = enabled }
    }

    suspend fun setVerboseLoggingRender(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_VERBOSE_LOGGING_RENDER] = enabled }
    }

    suspend fun setVerboseLoggingOal(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_VERBOSE_LOGGING_OAL] = enabled }
    }

    suspend fun setVerboseLoggingPdf(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_VERBOSE_LOGGING_PDF] = enabled }
    }

    suspend fun setVerboseLoggingIo(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_VERBOSE_LOGGING_IO] = enabled }
    }

    // Clears all dev sub-flags when dev options master toggle is off. Safe to call on every
    // startup — a no-op when devOptionsEnabled is true (developer session).
    suspend fun resetDevSubFlagsIfDisabled(ctx: Context) {
        val devEnabled = devOptionsEnabledFlow(ctx).first()
        if (devEnabled) return
        ctx.editSettings { prefs ->
            prefs[KEY_SHOW_OAL_DEBUG_LABEL] = false
            prefs[KEY_SHOW_OAL_IN_PREVIEW_BOX] = false
            prefs[KEY_SHOW_COMPONENT_DEBUG_LABELS] = false
            prefs[KEY_SHOW_RENDER_LAYOUT_DEBUG_OVERLAY] = false
            prefs[KEY_SHOW_RENDER_OAL_MARKERS] = false
            prefs[KEY_SHOW_DIM_DEBUG_OVERLAY] = false
            prefs[KEY_VERBOSE_LOGGING_ENABLED] = false
            prefs[KEY_VERBOSE_LOGGING_RENDER] = false
            prefs[KEY_VERBOSE_LOGGING_OAL] = false
            prefs[KEY_VERBOSE_LOGGING_PDF] = false
            prefs[KEY_VERBOSE_LOGGING_IO] = false
        }
    }

    suspend fun setAchievementsEnabled(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_ACHIEVEMENTS_ENABLED] = enabled }
    }

    /** Returns true if the achievement was newly unlocked by this call. */
    suspend fun unlockAchievement(ctx: Context, id: String): Boolean {
        var added = false
        ctx.editSettings { prefs ->
            val current = prefs[KEY_UNLOCKED_ACHIEVEMENT_IDS]?.toMutableSet() ?: mutableSetOf()
            added = current.add(id)
            prefs[KEY_UNLOCKED_ACHIEVEMENT_IDS] = current
        }
        return added
    }

    suspend fun setPreviewOutlineSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_OUTLINE_PRESET] = setting.preset.name
            it[KEY_PREVIEW_OUTLINE_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewBodyFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_BODY_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_BODY_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewTaperFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_TAPER_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_TAPER_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewLinerFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_LINER_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_LINER_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewThreadFillSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_THREAD_FILL_PRESET] = setting.preset.name
            it[KEY_PREVIEW_THREAD_FILL_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewThreadHatchSetting(ctx: Context, setting: PreviewColorSetting) {
        ctx.editSettings {
            it[KEY_PREVIEW_THREAD_HATCH_PRESET] = setting.preset.name
            it[KEY_PREVIEW_THREAD_HATCH_CUSTOM_ROLE] = setting.customRole.name
        }
    }

    suspend fun setPreviewBlackWhiteOnly(ctx: Context, enabled: Boolean) {
        ctx.editSettings { it[KEY_PREVIEW_BW_ONLY] = enabled }
    }

    // --- PDF section ---
    // In-memory mirror of persisted PDF prefs. Each field has a corresponding DataStore
    // key; the ViewModel loads them on init via the flow functions above.
    @Volatile
    private var _pdfPrefs: PdfPrefs = PdfPrefs()

    val pdfPrefs: PdfPrefs
        get() = _pdfPrefs

    fun updatePdfPrefs(transform: (PdfPrefs) -> PdfPrefs) {
        val next = transform(_pdfPrefs).clamped()
        _pdfPrefs = next
        // The one writer of the fraction renderer's active style. Every draw site reads
        // `FractionTypography.active` rather than taking the style as a parameter, so this
        // mirror is what makes the Settings choice reach the ink.
        FractionTypography.setStyle(next.fractionStyle)
    }
}
