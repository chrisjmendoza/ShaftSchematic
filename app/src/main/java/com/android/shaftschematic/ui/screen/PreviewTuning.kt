package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE

/**
 * PreviewTuning — the live, visual-only overrides behind an open PDF preview while a
 * tuning slider is being dragged.
 *
 * The four tuning sliders (Line thickness, Body S-break, Shaft height, Liner compression)
 * report their in-progress value through their `onDrag` channel; the hosting preview
 * screen parks it here and renders the page from it, so the drawing reshapes under the
 * finger instead of only on release ("see the differences without choosing, closing menu,
 * opening menu, choosing" — on-device request).
 *
 * **These overrides are drawing state, never storage.** No DataStore write and no
 * `RunoutConfig` update may happen on a drag frame: persistence and the per-job dirty mark
 * stay on the sliders' commit-on-release path, exactly as before. A field returns to null
 * when its drag ends, and the committed value takes over again.
 *
 * [active] is a `derivedStateOf` so the screens that read it (the raster resolution and the
 * bottom-sheet scrim) recompose when the drag STARTS and ENDS, not on every frame.
 *
 * The file also owns the pure **tuning layout** math ([tuningSheetMaxHeightDp],
 * [tuningPageStripHeightDp]) — where the page sits and how tall its sheet may grow while
 * that sheet is open. Live rendering is worthless if the menu covers the page.
 */
class PreviewTuning {
    /** In-progress "Line thickness" multiplier, or null when no thickness drag is live. */
    var lineThickness by mutableStateOf<Float?>(null)

    /** In-progress "Body S-break" threshold fraction, or null. */
    var sBreakFrac by mutableStateOf<Float?>(null)

    /** In-progress "Shaft height" multiplier — undetented, see `ShaftHeightSlider`. */
    var heightScale by mutableStateOf<Float?>(null)

    /** In-progress raw "Liner compression" value (not the derived width floor). */
    var linerCompression by mutableStateOf<Float?>(null)

    private val activeState = derivedStateOf {
        lineThickness != null || sBreakFrac != null ||
            heightScale != null || linerCompression != null
    }

    /** True while any tuning slider is being dragged. */
    val active: Boolean get() = activeState.value
}

/** Screen-scoped [PreviewTuning] — one holder per preview surface. */
@Composable
internal fun rememberPreviewTuning(): PreviewTuning = remember { PreviewTuning() }

/**
 * [prefs] carrying the [sBreakThresholdFrac] this render pass should draw with — the
 * committed pref normally, the in-progress value during a "Body S-break" drag. The
 * composers read the threshold off `PdfPrefs`, so the drag value rides in on a copy and
 * the stored pref is untouched.
 */
internal fun tunedPdfPrefs(prefs: PdfPrefs, sBreakThresholdFrac: Float): PdfPrefs =
    if (sBreakThresholdFrac == prefs.sBreakThresholdFrac) prefs
    else prefs.copy(sBreakThresholdFrac = sBreakThresholdFrac)

/**
 * [config] with live "Shaft height" / "Liner compression" drags applied. The liner floor
 * the composers consume is derived by `RunoutConfig.linerMinFracOfTrue` off this copy —
 * never a re-stated formula — so the drag draws exactly what the release will commit.
 */
internal fun tunedRunoutConfig(
    config: RunoutConfig,
    heightScale: Float?,
    linerCompression: Float?,
): RunoutConfig =
    if (heightScale == null && linerCompression == null) {
        config
    } else {
        config.copy(
            heightScale = heightScale ?: config.heightScale,
            linerCompression = linerCompression ?: config.linerCompression,
        )
    }

/**
 * Raster resolution for one preview pass: half res (≈¼ the pixels) while a slider drag is
 * live so the page keeps up with the finger, full [PDF_PREVIEW_RENDER_SCALE] otherwise —
 * the release pass always restores the sharp page.
 */
internal fun previewRenderScale(tuningActive: Boolean): Int =
    if (tuningActive) 1 else PDF_PREVIEW_RENDER_SCALE

// ─────────────────────────────────────────────────────────────────────────────
// Tuning layout — the page strip above an open options sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Status bar + preview app bar, in dp. Covers the M3 small top app bar (64 dp) on the
 * schematic preview and the shared overlay's icon-button toolbar (≈56 dp) plus the status
 * bar, so ONE allowance serves both surfaces.
 */
internal const val PREVIEW_TOP_CHROME_DP = 88f

/**
 * Floor for a tunable sheet's height, as a fraction of the screen. A short/wide device
 * cannot host both a full-width page and a usable sheet; the sheet keeps this much and the
 * page strip takes what is left (it is zoomable, the sliders are not).
 */
internal const val TUNING_SHEET_MIN_FRAC = 0.40f

/**
 * Ceiling for any options sheet, as a fraction of the screen — a sheet expanded to the
 * status bar leaves no edge to swipe it back down by (on-device report). The non-tunable
 * sheets (wear, undercut) use this alone.
 */
internal const val PREVIEW_SHEET_MAX_FRAC = 0.78f

/**
 * Height of the exported page drawn at fit-width, in dp. The sheets are LANDSCAPE
 * (`PDF_PAGE_WIDTH_PT` × `PDF_PAGE_HEIGHT_PT`), so a page fitted to a portrait screen's
 * width needs only this much height — the whole drawing lives in a strip near the top.
 */
internal fun fitWidthPageHeightDp(screenWidthDp: Float): Float =
    screenWidthDp * (PDF_PAGE_HEIGHT_PT.toFloat() / PDF_PAGE_WIDTH_PT.toFloat())

/**
 * Height cap for an options sheet that tunes the page behind it: whatever is left under the
 * fit-width page strip and the top chrome, so the drawing being judged stays visible while
 * the sliders move ("It may render live but the menu with the sliders is in the way" —
 * on-device report). Clamped between [TUNING_SHEET_MIN_FRAC] and [PREVIEW_SHEET_MAX_FRAC]
 * of the screen: the floor keeps the sheet usable on short/wide devices, the ceiling keeps
 * the historical swipe-down edge on very tall ones.
 */
internal fun tuningSheetMaxHeightDp(screenWidthDp: Float, screenHeightDp: Float): Float =
    (screenHeightDp - fitWidthPageHeightDp(screenWidthDp) - PREVIEW_TOP_CHROME_DP)
        .coerceAtLeast(screenHeightDp * TUNING_SHEET_MIN_FRAC)
        .coerceAtMost(screenHeightDp * PREVIEW_SHEET_MAX_FRAC)

/**
 * Height of the page strip the preview draws into while a tuning sheet is open. Normally
 * the fit-width height; on a screen too short to host both, the SHEET keeps its floor first
 * and the strip yields the remainder (the page fits to that height instead, and stays
 * zoomable once the sheet closes).
 */
internal fun tuningPageStripHeightDp(screenWidthDp: Float, screenHeightDp: Float): Float =
    minOf(
        fitWidthPageHeightDp(screenWidthDp),
        screenHeightDp - PREVIEW_TOP_CHROME_DP -
            tuningSheetMaxHeightDp(screenWidthDp, screenHeightDp),
    ).coerceAtLeast(0f)
