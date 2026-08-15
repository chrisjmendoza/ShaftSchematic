package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.pdf.PdfExportMode
import com.android.shaftschematic.settings.AppThemeMode
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_SMALL_PT
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MAX_IN
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MIN_IN
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UndercutShadeColor
import com.android.shaftschematic.util.UndercutShadeIntensity
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.launch

/**
 * ShaftViewModelSettings — extension functions for all persisted preference setters.
 *
 * Extracted from ShaftViewModel to keep preference mutation grouped by concern.
 * All functions are extensions on ShaftViewModel and access internal-visibility
 * backing fields declared in the primary class file.
 */

// ── Verbose logging sync (called by every debug/verbose setter) ──────────────

internal fun ShaftViewModel.syncVerboseLogConfig() {
    VerboseLog.configure(
        devOptionsEnabled = _devOptionsEnabled.value,
        verboseEnabled = _verboseLoggingEnabled.value,
        renderEnabled = _verboseLoggingRender.value,
        oalEnabled = _verboseLoggingOal.value,
        pdfEnabled = _verboseLoggingPdf.value,
        ioEnabled = _verboseLoggingIo.value,
    )
}

// ── PDF export preferences ────────────────────────────────────────────────────

fun ShaftViewModel.setOpenPdfAfterExport(enabled: Boolean, persist: Boolean = true) {
    _openPdfAfterExport.value = enabled
    if (persist) {
        viewModelScope.launch { SettingsStore.setOpenPdfAfterExport(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setPdfTieringMode(mode: PdfTieringMode, persist: Boolean = true) {
    _pdfTieringMode.value = mode
    SettingsStore.updatePdfPrefs { it.copy(tieringMode = mode) }
    if (persist) {
        viewModelScope.launch { SettingsStore.setPdfTieringMode(getApplication(), mode) }
    }
}

fun ShaftViewModel.setPdfShowComponentTitles(show: Boolean, persist: Boolean = true) {
    _pdfShowComponentTitles.value = show
    SettingsStore.updatePdfPrefs { it.copy(showComponentTitles = show) }
    if (persist) {
        viewModelScope.launch { SettingsStore.setPdfShowComponentTitles(getApplication(), show) }
    }
}

fun ShaftViewModel.setPdfShadedBodies(v: Boolean, persist: Boolean = true) {
    _pdfShadedBodies.value = v
    SettingsStore.updatePdfPrefs { it.copy(shadedBodies = v) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfShadedBodies(getApplication(), v) }
}

fun ShaftViewModel.setPdfShadedTapers(v: Boolean, persist: Boolean = true) {
    _pdfShadedTapers.value = v
    SettingsStore.updatePdfPrefs { it.copy(shadedTapers = v) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfShadedTapers(getApplication(), v) }
}

fun ShaftViewModel.setPdfShadedLiners(v: Boolean, persist: Boolean = true) {
    _pdfShadedLiners.value = v
    SettingsStore.updatePdfPrefs { it.copy(shadedLiners = v) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfShadedLiners(getApplication(), v) }
}

fun ShaftViewModel.setPdfCurveLoHeightIn(v: Float, persist: Boolean = true) {
    val clamped = v.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN)
    _pdfCurveLoHeightIn.value = clamped
    SettingsStore.updatePdfPrefs { it.copy(curveLoHeightIn = clamped) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfCurveLoHeightIn(getApplication(), clamped) }
}

fun ShaftViewModel.setPdfCurveHiHeightIn(v: Float, persist: Boolean = true) {
    val clamped = v.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN)
    _pdfCurveHiHeightIn.value = clamped
    SettingsStore.updatePdfPrefs { it.copy(curveHiHeightIn = clamped) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfCurveHiHeightIn(getApplication(), clamped) }
}

/** Wired to Settings → Drawing → "Body S-break"; 0 = never break on compression. */
fun ShaftViewModel.setPdfSBreakThresholdFrac(v: Float, persist: Boolean = true) {
    val clamped = v.coerceIn(0f, 1f)
    _pdfSBreakThresholdFrac.value = clamped
    SettingsStore.updatePdfPrefs { it.copy(sBreakThresholdFrac = clamped) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfSBreakThresholdFrac(getApplication(), clamped) }
}

/**
 * Wired to Settings → Drawing → "Wear depth exaggeration" and to the Wear tab's
 * "Save as default" button — the value a document with no `WearRecord.traceDepthFrac`
 * override draws its worn-profile trace with.
 */
fun ShaftViewModel.setPdfWearTraceDepthFrac(v: Float, persist: Boolean = true) {
    val clamped = v.coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)
    _pdfWearTraceDepthFrac.value = clamped
    SettingsStore.updatePdfPrefs { it.copy(wearTraceDepthFrac = clamped) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfWearTraceDepthFrac(getApplication(), clamped) }
}

/** Wired to the PDF options sheets and Settings → Drawing → "Dimension arrows". */
fun ShaftViewModel.setPdfArrowSizePt(v: Float, persist: Boolean = true) {
    val clamped = v.coerceIn(PDF_ARROW_SIZE_SMALL_PT, PDF_ARROW_SIZE_LARGE_PT)
    _pdfArrowSizePt.value = clamped
    SettingsStore.updatePdfPrefs { it.copy(arrowSizePt = clamped) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfArrowSizePt(getApplication(), clamped) }
}

/**
 * Wired to the PDF options sheets and Settings → Drawing → "Fractions".
 *
 * The `updatePdfPrefs` call is what actually changes the ink — it mirrors the choice into
 * `FractionTypography.active`, which every draw site reads. The StateFlow exists so the UI can
 * show the selection and so each preview's render inputs change, forcing a re-raster.
 */
fun ShaftViewModel.setPdfFractionStyle(style: FractionStyle, persist: Boolean = true) {
    _pdfFractionStyle.value = style
    SettingsStore.updatePdfPrefs { it.copy(fractionStyle = style) }
    if (persist) viewModelScope.launch { SettingsStore.setPdfFractionStyle(getApplication(), style) }
}

fun ShaftViewModel.setPdfExportMode(mode: PdfExportMode, persist: Boolean = true) {
    _pdfExportMode.value = mode
    if (persist) {
        viewModelScope.launch { SettingsStore.setPdfExportMode(getApplication(), mode) }
    }
}

/** Session-only (never persisted) — see the [ShaftViewModel] property KDoc. */
fun ShaftViewModel.setPdfBlankDraft(enabled: Boolean) {
    _pdfBlankDraft.value = enabled
}

/** Session-only, like [setPdfBlankDraft]. Only affects sheets exported in blank mode. */
fun ShaftViewModel.setPdfBlankDiaCallouts(enabled: Boolean) {
    _pdfBlankDiaCallouts.value = enabled
}

// ── Appearance (app-wide theme) ──────────────────────────────────────────────

fun ShaftViewModel.setThemeMode(mode: AppThemeMode, persist: Boolean = true) {
    _themeMode.value = mode
    if (persist) {
        viewModelScope.launch { SettingsStore.setThemeMode(getApplication(), mode) }
    }
}

fun ShaftViewModel.setHighContrast(enabled: Boolean, persist: Boolean = true) {
    _highContrast.value = enabled
    if (persist) {
        viewModelScope.launch { SettingsStore.setHighContrast(getApplication(), enabled) }
    }
}

// ── Undercut drawing style (on-screen sheet only) ────────────────────────────

fun ShaftViewModel.setUndercutLineArt(enabled: Boolean, persist: Boolean = true) {
    _undercutStyle.value = _undercutStyle.value.copy(lineArt = enabled)
    if (persist) {
        viewModelScope.launch { SettingsStore.setUndercutLineArt(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setUndercutShadeColor(color: UndercutShadeColor, persist: Boolean = true) {
    _undercutStyle.value = _undercutStyle.value.copy(shadeColor = color)
    if (persist) {
        viewModelScope.launch { SettingsStore.setUndercutShadeColor(getApplication(), color) }
    }
}

fun ShaftViewModel.setUndercutShadeIntensity(intensity: UndercutShadeIntensity, persist: Boolean = true) {
    _undercutStyle.value = _undercutStyle.value.copy(intensity = intensity)
    if (persist) {
        viewModelScope.launch { SettingsStore.setUndercutShadeIntensity(getApplication(), intensity) }
    }
}

// ── Preview / rendering preferences ──────────────────────────────────────────

fun ShaftViewModel.setPreviewBlackWhiteOnly(enabled: Boolean, persist: Boolean = true) {
    _previewBlackWhiteOnly.value = enabled
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewBlackWhiteOnly(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setLineThicknessScale(scale: Float, persist: Boolean = true) {
    _lineThicknessScale.value = scale.coerceIn(0.5f, 2.0f)
    if (persist) {
        viewModelScope.launch { SettingsStore.setLineThicknessScale(getApplication(), scale) }
    }
}

fun ShaftViewModel.setPreviewOutlineSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewOutlineSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewOutlineSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setPreviewBodyFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewBodyFillSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewBodyFillSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setPreviewTaperFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewTaperFillSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewTaperFillSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setPreviewLinerFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewLinerFillSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewLinerFillSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setPreviewThreadFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewThreadFillSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewThreadFillSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setPreviewThreadHatchSetting(setting: PreviewColorSetting, persist: Boolean = true) {
    _previewThreadHatchSetting.value = setting
    if (persist) {
        viewModelScope.launch { SettingsStore.setPreviewThreadHatchSetting(getApplication(), setting) }
    }
}

fun ShaftViewModel.setShowComponentArrows(show: Boolean, persist: Boolean = true) {
    _showComponentArrows.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowComponentArrows(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowHighlightSelection(show: Boolean, persist: Boolean = true) {
    _showHighlightSelection.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowHighlightSelection(getApplication(), show) }
    }
}

fun ShaftViewModel.setComponentArrowWidthDp(widthDp: Int, persist: Boolean = true) {
    val clamped = widthDp.coerceIn(24, 72)
    _componentArrowWidthDp.value = clamped
    if (persist) {
        viewModelScope.launch { SettingsStore.setComponentArrowWidthDp(getApplication(), clamped) }
    }
}

// ── Debug / developer options ─────────────────────────────────────────────────

fun ShaftViewModel.resetDevFlagsOnStartup() {
    viewModelScope.launch { SettingsStore.resetDevSubFlagsIfDisabled(getApplication()) }
}

fun ShaftViewModel.setDevOptionsEnabled(enabled: Boolean, persist: Boolean = true) {
    _devOptionsEnabled.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setDevOptionsEnabled(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setShowOalDebugLabel(show: Boolean, persist: Boolean = true) {
    _showOalDebugLabel.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowOalDebugLabel(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowOalHelperLine(show: Boolean, persist: Boolean = true) {
    _showOalHelperLine.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowOalHelperLine(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowOalInPreviewBox(show: Boolean, persist: Boolean = true) {
    _showOalInPreviewBox.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowOalInPreviewBox(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowComponentDebugLabels(show: Boolean, persist: Boolean = true) {
    _showComponentDebugLabels.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowComponentDebugLabels(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowRenderLayoutDebugOverlay(show: Boolean, persist: Boolean = true) {
    _showRenderLayoutDebugOverlay.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowRenderLayoutDebugOverlay(getApplication(), show) }
    }
}

fun ShaftViewModel.setShowRenderOalMarkers(show: Boolean, persist: Boolean = true) {
    _showRenderOalMarkers.value = show
    if (persist) {
        viewModelScope.launch { SettingsStore.setShowRenderOalMarkers(getApplication(), show) }
    }
}

// ── Verbose logging ───────────────────────────────────────────────────────────

fun ShaftViewModel.setVerboseLoggingEnabled(enabled: Boolean, persist: Boolean = true) {
    _verboseLoggingEnabled.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setVerboseLoggingEnabled(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setVerboseLoggingRender(enabled: Boolean, persist: Boolean = true) {
    _verboseLoggingRender.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setVerboseLoggingRender(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setVerboseLoggingOal(enabled: Boolean, persist: Boolean = true) {
    _verboseLoggingOal.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setVerboseLoggingOal(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setVerboseLoggingPdf(enabled: Boolean, persist: Boolean = true) {
    _verboseLoggingPdf.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setVerboseLoggingPdf(getApplication(), enabled) }
    }
}

fun ShaftViewModel.setVerboseLoggingIo(enabled: Boolean, persist: Boolean = true) {
    _verboseLoggingIo.value = enabled
    syncVerboseLogConfig()
    if (persist) {
        viewModelScope.launch { SettingsStore.setVerboseLoggingIo(getApplication(), enabled) }
    }
}

// ── Achievements ──────────────────────────────────────────────────────────────

fun ShaftViewModel.setAchievementsEnabled(enabled: Boolean, persist: Boolean = true) {
    _achievementsEnabled.value = enabled
    if (persist) {
        viewModelScope.launch { SettingsStore.setAchievementsEnabled(getApplication(), enabled) }
    }
}

fun ShaftViewModel.unlockAchievement(id: String) {
    if (!_achievementsEnabled.value) return
    if (_unlockedAchievementIds.value.contains(id)) return
    viewModelScope.launch {
        SettingsStore.unlockAchievement(getApplication(), id)
    }
}
