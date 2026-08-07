package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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
 * The file also owns the pure **tuning layout** math ([tuningPageStripHeightDp],
 * [tuningSheetMaxHeightDp]) plus the one strip draw helper ([drawPageBand]) — where the page
 * sits and how tall its sheet may grow while that sheet is open. Live rendering is worthless
 * if the menu covers the page: the strip is sized from the page's INK BAND so blank paper
 * never takes room from the drawing, and the sheet budget counts the sheet's own chrome
 * ([TUNING_SHEET_CHROME_DP] + the navigation-bar inset), which no content cap covers.
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
 * The sheet chrome that sits OUTSIDE the content column's `heightIn` cap: M3's drag handle
 * (a 4 dp bar with 22 dp of padding above and below). The sheet's own bottom window inset
 * stacks outside the cap too, so callers add the measured `WindowInsets.navigationBars`
 * bottom to this constant. Both have to come out of the content budget — capping the column
 * alone leaves the sheet taller than the cap and overlapping the page strip, which cost the
 * drawing its lowest callouts and its footer (on-device report).
 */
internal const val TUNING_SHEET_CHROME_DP = 48f

/**
 * Height of the exported page drawn at fit-width, in dp. The sheets are LANDSCAPE
 * (`PDF_PAGE_WIDTH_PT` × `PDF_PAGE_HEIGHT_PT`), so a page fitted to a portrait screen's
 * width needs only this much height — the whole drawing lives in a strip near the top.
 */
internal fun fitWidthPageHeightDp(screenWidthDp: Float): Float =
    screenWidthDp * (PDF_PAGE_HEIGHT_PT.toFloat() / PDF_PAGE_WIDTH_PT.toFloat())

/**
 * Height of the page strip the preview draws into while a tuning sheet is open — the first
 * of the pair, with the sheet cap derived from it.
 *
 * The strip shows the page's INK BAND, not the whole sheet: [inkFrac] is the share of page
 * height that carries drawing (`InkBand.frac`), so the blank top margin never occupies the
 * strip while the sliders move ("The shaft rendering has a LOT of white space on top and
 * we're losing some of the items under the shaft" — on-device report). Ink is never cropped;
 * only paper is. A page whose band has not been measured passes 1f and gets the full sheet.
 *
 * [sheetChromeDp] is the sheet furniture below the strip that no content cap covers —
 * [TUNING_SHEET_CHROME_DP] plus the measured navigation-bar inset. On a screen too short to
 * host both, the SHEET keeps its [TUNING_SHEET_MIN_FRAC] floor and the strip yields the
 * remainder (the page fits to that height instead, and stays zoomable once the sheet closes).
 */
internal fun tuningPageStripHeightDp(
    screenWidthDp: Float,
    screenHeightDp: Float,
    sheetChromeDp: Float,
    inkFrac: Float = 1f,
): Float = minOf(
    fitWidthPageHeightDp(screenWidthDp) * inkFrac.coerceIn(0.05f, 1f),
    screenHeightDp - PREVIEW_TOP_CHROME_DP - sheetChromeDp -
        screenHeightDp * TUNING_SHEET_MIN_FRAC,
).coerceAtLeast(0f)

/**
 * Height cap for the CONTENT COLUMN of an options sheet that tunes the page behind it:
 * whatever is left under the page strip once the top chrome and the sheet's own
 * [sheetChromeDp] are paid for, so the drawing being judged stays visible while the sliders
 * move ("It may render live but the menu with the sliders is in the way" — on-device
 * report). Clamped between [TUNING_SHEET_MIN_FRAC] and [PREVIEW_SHEET_MAX_FRAC] of the
 * screen: the floor keeps the sheet usable on short/wide devices, the ceiling keeps the
 * historical swipe-down edge on very tall ones.
 *
 * Takes [stripHeightDp] rather than recomputing it, so cap and strip can never disagree.
 * Where `Configuration.screenHeightDp` excludes the system bars the cap comes out
 * conservative — a slightly shorter sheet, never a covered page.
 */
internal fun tuningSheetMaxHeightDp(
    screenHeightDp: Float,
    stripHeightDp: Float,
    sheetChromeDp: Float,
): Float = (screenHeightDp - stripHeightDp - PREVIEW_TOP_CHROME_DP - sheetChromeDp)
    .coerceAtLeast(screenHeightDp * TUNING_SHEET_MIN_FRAC)
    .coerceAtMost(screenHeightDp * PREVIEW_SHEET_MAX_FRAC)

/**
 * Draws [bitmap] into the tuning strip: cropped to [band] (its ink), fitted to the strip's
 * width and [stripHeightPx], horizontally centered and TOP-aligned. A null band draws the
 * whole page — the posture before a band has been measured.
 *
 * ONE implementation for both strip draw sites (the schematic preview's Canvas and the
 * shared `PdfPreviewOverlay`), so the two can never drift apart.
 */
internal fun DrawScope.drawPageBand(bitmap: ImageBitmap, band: InkBand?, stripHeightPx: Float) {
    val imgW = bitmap.width
    val imgH = bitmap.height
    if (imgW <= 0 || imgH <= 0 || stripHeightPx <= 0f || size.width <= 0f) return

    val srcTop = floor(imgH * (band?.topFrac ?: 0f)).toInt().coerceIn(0, imgH - 1)
    val srcBottom = ceil(imgH * (band?.bottomFrac ?: 1f)).toInt().coerceIn(srcTop + 1, imgH)
    val srcH = srcBottom - srcTop

    val fit = minOf(size.width / imgW, stripHeightPx / srcH)
    if (fit <= 0f) return
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(0, srcTop),
        srcSize = IntSize(imgW, srcH),
        dstOffset = IntOffset(((size.width - imgW * fit) / 2f).roundToInt(), 0),
        dstSize = IntSize((imgW * fit).roundToInt(), (srcH * fit).roundToInt()),
    )
}
