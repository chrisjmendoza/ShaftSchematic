/**
 * Shared PDF preview chrome for the document tabs.
 *
 * Hosts the full-screen preview overlay ([PdfPreviewOverlay]) and the PDF options sheet
 * ([RunoutWearOptionsSheet]) consumed by the Runout, Wear, Undercut, and Consolidated
 * Output routes, plus the open-in-viewer helper their export paths share.
 */
package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.android.shaftschematic.ui.adaptive.restoreBaseOrientation
import com.android.shaftschematic.ui.adaptive.unlockRotation
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.pdf.WEAR_STRIP_SIZE_FRAC_DEFAULT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_DROP_SCALE_DEFAULT
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_SCALE_DEFAULT
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_DEFAULT_MM
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.setLineThicknessScale
import com.android.shaftschematic.ui.viewmodel.setLinerCompression
import com.android.shaftschematic.ui.viewmodel.setLinersProportional
import com.android.shaftschematic.ui.viewmodel.setPdfArrowSizePt
import com.android.shaftschematic.ui.viewmodel.setPdfThreadDensity
import com.android.shaftschematic.ui.viewmodel.setPdfThreadSlant
import com.android.shaftschematic.ui.viewmodel.setPdfDualUnitLayout
import com.android.shaftschematic.ui.viewmodel.setPdfFractionStyle
import com.android.shaftschematic.ui.viewmodel.setPdfRunoutBubbleDropScale
import com.android.shaftschematic.ui.viewmodel.setPdfRunoutBubbleScale
import com.android.shaftschematic.ui.viewmodel.setPdfSBreakThresholdFrac
import com.android.shaftschematic.ui.viewmodel.setPdfShadeExplicitBodiesOnly
import com.android.shaftschematic.ui.viewmodel.setPdfShadedBodies
import com.android.shaftschematic.ui.viewmodel.setPdfShadedLiners
import com.android.shaftschematic.ui.viewmodel.setPdfUndercutLineArt
import com.android.shaftschematic.ui.viewmodel.setPdfUndercutLinerOutline
import com.android.shaftschematic.ui.viewmodel.setPdfUndercutSplitAtLinerEdge
import com.android.shaftschematic.ui.viewmodel.setPdfShadedTapers
import com.android.shaftschematic.ui.viewmodel.setPdfTieringMode
import com.android.shaftschematic.ui.viewmodel.setPdfWearBandShadeFrac
import com.android.shaftschematic.ui.viewmodel.setPdfWearJoinGapMaxMm
import com.android.shaftschematic.ui.viewmodel.setRunoutHeightScale
import com.android.shaftschematic.ui.viewmodel.setShowCouplingFace
import com.android.shaftschematic.ui.viewmodel.setUndercutPageOrientation
import com.android.shaftschematic.ui.viewmodel.setWearCompactStrips
import com.android.shaftschematic.ui.viewmodel.setWearShowShaftProfile
import com.android.shaftschematic.ui.viewmodel.setWearStripComponents
import com.android.shaftschematic.ui.viewmodel.setWearStripSizeFrac
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildOpenPdfIntent

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun openRunoutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching { context.grantUriPermission(ri.activityInfo?.packageName ?: return@forEach, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared PDF preview overlay (Runout + Wear + Undercut + Output)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen modal overlay that shows a PDF rendered as a bitmap.
 *
 * Displayed while [loading] is true shows a spinner. Once the [bitmap] is ready
 * it fills the overlay with pinch-to-zoom support via a standard Image composable.
 * The top bar's actions let the user print — the primary path — or proceed to the SAF file
 * picker after verifying the layout looks correct.
 *
 * @param bitmap        The rendered PDF page (null while rendering or on error).
 * @param loading       Whether the bitmap is still being generated.
 * @param title         Title shown in the top bar of the overlay.
 * @param onClose       Called when the user taps × or navigates back.
 * @param onExport      Called when the user taps the Export icon. A compact icon: paper is
 *                      the daily output and a PDF file is the backup copy, so Export takes
 *                      the secondary treatment here as it does on the tab bodies.
 * @param onPrint       Sends THIS document to the platform print dialog. Each route passes
 *                      the same snapshot-and-compose action its tab-body Print button runs,
 *                      from one local function, so the two entry points cannot drift. Null
 *                      hides the action. Labelled and filled — the primary output action.
 * @param actionsEnabled False greys Print and Export (the page still previews) — the undercut
 *                      sheet with every section left off has nothing to print.
 * @param optionsSheet  Optional composable content shown in the non-modal options panel
 *                      ([PreviewOptionsPanel]) the Tune icon toggles. When null, no Tune icon
 *                      is shown. The page stays pinch-zoomable and pannable under the open
 *                      panel; the panel closes by its close button, the Tune icon, or Back.
 * @param sheetTunesPage Whether [optionsSheet] reshapes THIS page live. When true the open
 *                      panel switches the preview to the tuning layout — the page redrawn
 *                      as a strip pinned under the toolbar, cropped to its ink, and the panel
 *                      capped ([tuningSheetMaxHeightDp]) to what is left below the strip.
 *                      Wear passes true as well — its controls commit on release rather than
 *                      mid-drag, but the commit still redraws THIS page, and a panel that
 *                      covered it left the change unjudgeable (on-device report). Undercut
 *                      leaves it false: its options tune nothing on the page, which stays
 *                      centered full-size behind the panel.
 * @param inkBand       Where [bitmap] carries ink, from the route's render loop. The strip
 *                      crops to it so the page's blank top margin does not take room from
 *                      the drawing; null shows the whole page. Measured on sharp passes
 *                      only, so the strip never resizes under a moving finger.
 */
@Composable
internal fun PdfPreviewOverlay(
    bitmap: ImageBitmap?,
    loading: Boolean,
    title: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onPrint: (() -> Unit)? = null,
    optionsSheet: (@Composable () -> Unit)? = null,
    sheetTunesPage: Boolean = false,
    inkBand: InkBand? = null,
    actionsEnabled: Boolean = true,
) {
    var showOptions by remember { mutableStateOf(false) }

    // Tuning layout: page strip on top, panel below it. See [tuningPageStripHeightDp].
    // The panel's header row and the navigation-bar inset below it sit OUTSIDE the content
    // cap, so both are budgeted here — a cap that ignored them left the options overlapping
    // the strip and swallowing the drawing's lowest callouts (on-device report).
    val stripLayout = showOptions && sheetTunesPage
    val configuration = LocalConfiguration.current
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val sheetChromeDp = TUNING_SHEET_CHROME_DP + navBottomDp
    val stripHeightDp = tuningPageStripHeightDp(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat(),
        sheetChromeDp,
        inkBand?.frac ?: 1f,
    )
    val stripHeight = stripHeightDp.dp
    val sheetMaxHeight = if (sheetTunesPage) {
        tuningSheetMaxHeightDp(
            configuration.screenHeightDp.toFloat(),
            stripHeightDp,
            sheetChromeDp,
        ).dp
    } else {
        // Options that reshape nothing on the page have no strip to stay clear of, just a
        // band of page to keep in view at the top.
        (configuration.screenHeightDp * PREVIEW_SHEET_MAX_FRAC).dp
    }

    // ── Pan / zoom ─────────────────────────────────────────────────────────
    // Held HERE, not keyed by the bitmap and not inside the bitmap branch below: every
    // commit-on-release control — the thread sliders included — re-renders the page, which
    // replaces the bitmap and may flash the spinner in its place, and a zoom that reset on
    // either would make the change unwatchable (on-device request: zoom on the threads while
    // moving the thread sliders). Every draw path fits the bitmap into the same box, so a
    // half-res draft raster and the sharp pass land on the same geometry and the zoom stays
    // put.
    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scaleState.floatValue = (scaleState.floatValue * zoomChange).coerceIn(0.5f, 8f)
        offsetState.value = offsetState.value + panChange
    }
    // The zoom resets when the LAYOUT changes — the strip and the centered full page fit the
    // page differently, so a zoom carried across the switch would point at the wrong place.
    // That is every open and close of a page-tuning panel; on the undercut preview the page
    // keeps its centered layout under the panel, so opening it leaves the zoom alone.
    LaunchedEffect(stripLayout) {
        scaleState.floatValue = 1f
        offsetState.value = Offset.Zero
    }

    // Unlock device rotation while the preview is open so the landscape sheet can be viewed in
    // landscape (phones are otherwise locked to portrait); restore the device's BASE
    // orientation on dismiss — portrait on a phone, free rotation on a tablet. Same pattern as
    // the schematic `PdfPreviewScreen`.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.unlockRotation()
        onDispose { activity?.restoreBaseOrientation() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (optionsSheet != null) {
                    // A toggle: the panel is non-modal, so this bar stays reachable while it
                    // is open and the same icon closes it.
                    IconButton(onClick = { showOptions = !showOptions }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (onPrint != null) {
                    FilledTonalButton(onClick = onPrint, enabled = actionsEnabled) {
                        Icon(Icons.Filled.Print, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Print")
                    }
                }
                IconButton(onClick = onExport, enabled = actionsEnabled, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF",
                        tint = if (actionsEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            }

            // ── PDF preview area with pinch-to-zoom ────────────────────────
            // `clipToBounds` keeps the zoomed/panned page inside this Box: a `graphicsLayer`
            // scale/translate draws outside the layout node unless clipped, so a zoomed-in page
            // could slide up OVER the toolbar and hide Close/Export (on-device report). The
            // document tucks BEHIND the bar instead. Hit testing was always bounded by layout,
            // so the bar stayed tappable — this fixes what the user could see.
            Box(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                // The full spinner stands in for the page only until a FIRST page exists; a
                // re-render after that keeps the current page and its zoom on screen and
                // signals the pass with the corner indicator below (see `PdfPreviewScreen`).
                when {
                    loading && bitmap == null -> CircularProgressIndicator(color = Color.White)
                    bitmap != null -> {
                        val scale  by scaleState
                        val offset by offsetState

                        if (stripLayout) {
                            // The drawing, cropped to its ink band and fitted into the
                            // strip the panel was sized to leave free. One draw helper
                            // with the schematic preview's Canvas, so they cannot drift.
                            Canvas(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(stripHeight)
                                    .transformable(state = transformState)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                    )
                                    .semantics {
                                        contentDescription =
                                            "PDF preview — pinch to zoom, drag to pan"
                                    },
                            ) {
                                drawPageBand(bitmap, inkBand, stripHeight.toPx())
                            }
                        } else {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "PDF preview — pinch to zoom, drag to pan",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .transformable(state = transformState)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                    ),
                            )
                        }
                    }
                    else -> Text(
                        "Preview unavailable",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (loading && bitmap != null) {
                    PreviewRerenderIndicator(Modifier.align(Alignment.TopEnd))
                }

                // The options, as a non-modal panel in this Box — composed LAST so it draws
                // over the page and takes the touches that land on it, while the page above
                // it keeps its pinch and pan. No scrim: nothing here is modal. The cap is
                // computed here because only the overlay knows the strip the panel has to
                // stay clear of; the panel applies it. The root Column's `systemBarsPadding`
                // keeps this Box — and so the panel at its bottom — above the navigation bar.
                if (showOptions && optionsSheet != null) {
                    PreviewOptionsPanel(
                        onClose = { showOptions = false },
                        maxHeightDp = sheetMaxHeight,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        optionsSheet()
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared PDF options sheet (Runout + Wear + Undercut + Output)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun RunoutWearOptionsSheet(
    lineThicknessScale: Float,
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
    vm: ShaftViewModel,
    /** The app-wide `PdfPrefs.shadeExplicitBodiesOnly` — see [ShadeInPdfChecks]. */
    shadeExplicitBodiesOnly: Boolean = false,
    /**
     * Shows the "Explicit bodies only" row inside the shade section. Off for the wear and
     * undercut sheets: their `SimpleShaftProfile` pass shades every body run whatever that
     * pref says, so the row would be a checkbox the page visibly ignores.
     */
    showShadeExplicitBodiesOnly: Boolean = true,
    /** Locks the "Liners" shade row — see [ShadeInPdfChecks]. */
    linerShadeLocked: Boolean = false,
    /**
     * Shows the "Undercut drawing: line art (no shading)" row at the foot of the shade section.
     * On only for the UNDERCUT preview — `PdfPrefs.undercutLineArt` reaches one composer, so on
     * any other sheet the row would be a checkbox the page ignores.
     */
    showUndercutLineArt: Boolean = false,
    /** The app-wide `PdfPrefs.undercutLineArt`; read only when [showUndercutLineArt]. */
    undercutLineArt: Boolean = false,
    /** The app-wide `PdfPrefs.undercutLinerOutline`; read only when [showUndercutLineArt]. */
    undercutLinerOutline: Boolean = true,
    /**
     * Shows the per-job "Coupling face" election. On for the runout and consolidated sheets,
     * the two documents that can draw the end view; off for the wear and undercut sheets,
     * where the control would be inert. Both hosting surfaces bind the SAME
     * `RunoutConfig.showCouplingFace` through [setShowCouplingFace], so the
     * two sheets can never disagree about what this job prints.
     */
    showCouplingFaceRow: Boolean = false,
    /** This job's `RunoutConfig.showCouplingFace`; read only when [showCouplingFaceRow]. */
    couplingFaceOn: Boolean = false,
    /**
     * Shows the shared "Body S-break" threshold slider. On for the runout and consolidated
     * sheets, which draw compression breaks; off for the wear and undercut documents, whose
     * profiles never break, so the control would be inert noise there.
     */
    showSBreak: Boolean = false,
    /** The app-wide `PdfPrefs.sBreakThresholdFrac`; read only when [showSBreak]. */
    sBreakThresholdFrac: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
    /**
     * Shows the wear-document tuning block: the Components election, the "Strip size" slider, the
     * "Trace depth exaggeration" row (the Wear tab's own control, shared construction), the
     * "Wear area shade" slider, and the "Taper–liner join" threshold. On only for the wear
     * preview — the sheet exists so the drawing being looked at can be tuned against itself
     * (on-device request) — and inert on every other document, which draws no wear strips.
     */
    showWearControls: Boolean = false,
    /** This job's trace depth resolved against [traceDepthDefault]; read only when [showWearControls]. */
    traceDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
    /** The Settings → Drawing default, `PdfPrefs.wearTraceDepthFrac`; read only when [showWearControls]. */
    traceDepthDefault: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
    /** The app-wide `PdfPrefs.wearBandShadeFrac`; read only when [showWearControls]. */
    wearBandShadeFrac: Float = PDF_WEAR_BAND_SHADE_DEFAULT,
    /**
     * The app-wide `PdfPrefs.wearJoinGapMaxMm` (canonical mm); read only when [showWearControls].
     */
    wearJoinGapMaxMm: Float = PDF_WEAR_JOIN_GAP_DEFAULT_MM,
    /**
     * The session's display unit — the UI edge for [wearJoinGapMaxMm], the one length-valued
     * control on this sheet. Read only when [showWearControls].
     */
    unit: UnitSystem = UnitSystem.INCHES,
    /**
     * Strip-eligible components for the wear sheet's "Components" section, AFT→FWD
     * (`buildWearStripComponentOptions`); read only when [showWearControls].
     */
    wearStripOptions: List<WearStripComponentOption> = emptyList(),
    /** This job's authored strip election (`WearRecord.stripComponentIds`); `null` = the default. */
    wearStripSelection: List<String>? = null,
    /** The default election — every drawable liner — materialized on the first component toggle. */
    wearStripDefaultIds: List<String> = emptyList(),
    /** This job's `WearRecord.showShaftProfile`; read only when [showWearControls]. */
    wearShowShaftProfile: Boolean = true,
    /** This job's `WearRecord.compactStrips`; read only when [showWearControls]. */
    wearCompactStrips: Boolean = false,
    /** This job's `WearRecord.stripSizeFrac` — the strips' height ceiling multiplier. */
    wearStripSizeFrac: Float = WEAR_STRIP_SIZE_FRAC_DEFAULT,
    /**
     * Shows the shared "Dimension arrows" size picker. On for the consolidated sheet's dimension
     * rails and the undercut drawing's strip rails, the two documents here whose heads follow
     * `PdfPrefs.arrowSizePt`; the classic runout and wear sheets draw their own fixed-head marks,
     * so the control would be inert there.
     */
    showDimensionArrows: Boolean = false,
    /** The app-wide `PdfPrefs.arrowSizePt`; read only when [showDimensionArrows]. */
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /**
     * The app-wide `PdfPrefs.fractionStyle`. Ungated, unlike the arrowhead size: every document
     * this sheet serves prints lengths, so every one of them draws fractions.
     */
    fractionStyle: FractionStyle = FractionStyle.STACKED,
    /**
     * Shows the Thread section (slant + density). Every document this sheet serves draws the
     * shaft's threads, so the hosts pass [hasDrawnThreads] off the spec they draw; false on a
     * shaft with no thread, where the hatch controls would tune nothing.
     */
    hasThreads: Boolean = false,
    /** The app-wide `PdfPrefs.threadSlant`; read only when [hasThreads]. */
    threadSlant: Float = THREAD_SLANT_DEFAULT,
    /** The app-wide `PdfPrefs.threadDensity`; read only when [hasThreads]. */
    threadDensity: Float = THREAD_DENSITY_DEFAULT,
    /** The app-wide `PdfPrefs.dualUnitLayout`; ungated for the same reason as the fraction style. */
    dualUnitLayout: DualUnitLayout = DualUnitLayout.Default,
    /**
     * The DOCUMENT's dual-units flag and its setter. `dual_units` travels with the job, not with
     * the app, so it belongs on every sheet that offers a dual LAYOUT — offering the layout with no
     * way to turn the mode on from the same place is what sent this back for a second pass.
     */
    dualUnits: Boolean = false,
    onDualUnitsChange: ((Boolean) -> Unit)? = null,
    /**
     * Live-tuning sink for a preview that reshapes under the finger: each slider reports
     * its in-progress value here. Visual only — the commit path is unchanged and nothing
     * persists on a drag frame. Null (the default) on surfaces that don't tune live.
     */
    tuning: PreviewTuning? = null,
    /** The hosting tab's session-only blank-draft state. The row renders only when non-null. */
    blankDraft: Boolean = false,
    onSetBlankDraft: ((Boolean) -> Unit)? = null,
    /**
     * Shows the per-job "Shaft height" slider (`RunoutConfig.heightScale`). On for every
     * document whose composer takes the multiplier — the classic runout sheet, the
     * consolidated one, and the wear document; off for the undercut drawing, which draws no
     * whole-shaft profile in its normal form, so the slider would be inert there.
     *
     * Split from [showLinerCompression] because the wear composer sizes its profile band by
     * height but has no compression solve to raise liner floors against.
     */
    showHeightSlider: Boolean = false,
    heightScale: Float = 1f,
    heightSliderBase: Float = 1f,
    heightSliderMaxDiaMm: Float = 10f,
    /**
     * Shows the per-job "Liner compression" pair (`RunoutConfig.linersProportional` +
     * `linerCompression`). On for the two documents `composeRunoutPdf` produces — one
     * composer, one liner floor — and off everywhere else, whose composers take neither.
     */
    showLinerCompression: Boolean = false,
    linersProportional: Boolean = false,
    linerCompression: Float = 0f,
    estimateKeptFrac: (Float) -> Float = { it },
    /**
     * Shows the "Runout bubbles" section with the "Bubble size" and "Bubble height" sliders
     * (`PdfPrefs.runoutBubbleScale` / `runoutBubbleDropScale`). On for the runout and
     * consolidated sheets, the two documents that draw bubbles at all.
     */
    showBubbleControls: Boolean = false,
    /** The app-wide `PdfPrefs.runoutBubbleScale`; read only when [showBubbleControls]. */
    runoutBubbleScale: Float = PDF_RUNOUT_BUBBLE_SCALE_DEFAULT,
    /** The app-wide `PdfPrefs.runoutBubbleDropScale`; read only when [showBubbleControls]. */
    runoutBubbleDropScale: Float = PDF_RUNOUT_BUBBLE_DROP_SCALE_DEFAULT,
    /**
     * Shows the "Measurement reference" radios. On only for the consolidated sheet: its
     * dimension rails honor `PdfPrefs.tieringMode`, while the classic runout/wear/undercut
     * documents draw no rails, so the radios would be inert there.
     */
    showMeasurementReference: Boolean = false,
    pdfTieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    /**
     * The undercut sheet's per-job page shape (`UndercutRecord.pageOrientation`). Non-null shows
     * the "Page" row ([UndercutPageOrientationRow]) under the Content chips — on the undercut
     * sheet only; null (the default) keeps it off every other document.
     */
    undercutPageOrientation: UndercutPageOrientation? = null,
    /** Sections the undercut sheet prints (`undercutSheetStripCount`); read only with [undercutPageOrientation]. */
    undercutStripCount: Int = 0,
    /** The page that count resolves to (`undercutSheetOrientation`); read only with [undercutPageOrientation]. */
    undercutResolvedOrientation: PdfPageOrientation = PdfPageOrientation.LANDSCAPE,
    /**
     * The undercut sheet's other per-job content rows — "Distance to S.E.T." and "Sections to
     * print" — composed under the "Page" row; read only with [undercutPageOrientation].
     */
    undercutContentRows: (@Composable () -> Unit)? = null,
) {
    // Scrollable: without its own scroll a short screen clips the bottom rows mid-checkbox
    // (same posture as PdfOptionsSheet). The title and the height cap belong to the hosting
    // [PreviewOptionsPanel] — only `PdfPreviewOverlay` knows the page strip a tuning panel must
    // stay clear of — and the navigation-bar inset is paid by the overlay's
    // `systemBarsPadding`, so this column carries none of them.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        // One sheet serves the runout, consolidated, wear and undercut documents, and only
        // some of them carry per-job controls — a sheet with none gets the app-wide sentence
        // alone rather than a pointer to captions it does not show.
        OptionsScopeNote(
            hasJobControls = showHeightSlider || showLinerCompression ||
                showCouplingFaceRow || showWearControls || undercutPageOrientation != null,
        )
        Spacer(Modifier.height(12.dp))

        // ── Content ──────────────────────────────────────────────────────────
        // What this sheet CARRIES, as chips: the write-in draft and — on the two documents
        // that can draw the end view — the coupling face. Both are per-sheet elections, so
        // they lead; the coupling face is per-JOB (it rides the envelope's RunoutConfig, not
        // PdfPrefs — one job may measure the coupling and the next not).
        if (onSetBlankDraft != null || showCouplingFaceRow || undercutPageOrientation != null) {
            ContentChipRow {
                if (onSetBlankDraft != null) {
                    ContentChip(
                        label = "Blank draft",
                        selected = blankDraft,
                        onClick = { onSetBlankDraft(!blankDraft) },
                    )
                }
                if (showCouplingFaceRow) {
                    ContentChip(
                        label = "Coupling face",
                        selected = couplingFaceOn,
                        onClick = { vm.setShowCouplingFace(!couplingFaceOn) },
                    )
                }
            }
            // The undercut sheet's page shape — per job, beside the election that changes its
            // section count (a blank draft prints every liner's started strip).
            if (undercutPageOrientation != null) {
                Spacer(Modifier.height(12.dp))
                UndercutPageOrientationRow(
                    choice = undercutPageOrientation,
                    stripCount = undercutStripCount,
                    resolved = undercutResolvedOrientation,
                    onChoose = { vm.setUndercutPageOrientation(it) },
                )
                undercutContentRows?.invoke()
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        // ── Sections ─────────────────────────────────────────────────────────
        // Laid out in `OptionsSection` order (most-used first, Units last) whatever order
        // they are supplied in here — the same list `PdfOptionsSheet` builds from. A section
        // this document has nothing for is simply not supplied.
        val sections = mutableMapOf<OptionsSection, OptionsSectionBody>()

        // ── Shaft ────────────────────────────────────────────────────────────
        // The live-tuning group the page strip above this sheet exists to keep in view, so it
        // leads and starts open. Shaft height is the per-job `RunoutConfig.heightScale` — ONE
        // value behind every drawing; Body S-break the app-wide `PdfPrefs.sBreakThresholdFrac`;
        // the compression pair the other half of the per-job `RunoutConfig` pair, on the two
        // sheets whose composer solves liner floors.
        if (showHeightSlider || showSBreak || showLinerCompression) {
            sections[OptionsSection.SHAFT] = OptionsSectionBody {
                if (showHeightSlider) {
                    ShaftHeightSlider(
                        heightScale = heightScale,
                        baseScale = heightSliderBase,
                        maxDiaMm = heightSliderMaxDiaMm,
                        onCommit = { vm.setRunoutHeightScale(it) },
                        onDrag = { tuning?.heightScale = it },
                    )
                }
                if (showSBreak) {
                    SBreakThresholdSlider(
                        frac = sBreakThresholdFrac,
                        onCommit = { vm.setPdfSBreakThresholdFrac(it) },
                        onDrag = { tuning?.sBreakFrac = it },
                    )
                }
                if (showLinerCompression) {
                    LinerCompressionControl(
                        linersProportional = linersProportional,
                        linerCompression = linerCompression,
                        estimateKeptFrac = estimateKeptFrac,
                        onSetProportional = { vm.setLinersProportional(it) },
                        onSetCompression = { vm.setLinerCompression(it) },
                        onDrag = { tuning?.linerCompression = it },
                    )
                }
            }
        }

        // ── Wear (wear document only) ────────────────────────────────────────
        // The same controls the Wear tab and Settings → Drawing carry — here so the strip
        // height, the trace depth, the band's grey, and the join threshold can be judged
        // against the sheet they print on. Commit-on-release, like every slider on this
        // sheet; the wear preview re-renders from its own keys rather than a live tuning
        // channel. What the sheet draws comes first — the whole-shaft profile toggle and the
        // per-component strip election, then how tall the elected strips draw — above the
        // controls that restyle what's drawn.
        if (showWearControls) {
            sections[OptionsSection.WEAR] = OptionsSectionBody {
                WearStripComponentChecks(
                    options = wearStripOptions,
                    selection = wearStripSelection,
                    defaultIds = wearStripDefaultIds,
                    showShaftProfile = wearShowShaftProfile,
                    onSetShowShaftProfile = { vm.setWearShowShaftProfile(it) },
                    compactStrips = wearCompactStrips,
                    onSetCompactStrips = { vm.setWearCompactStrips(it) },
                    // Nullable: the "Default (all liners)" quick action clears the election so
                    // the sheet follows the shaft again.
                    onSetSelection = { vm.setWearStripComponents(it) },
                )
                WearStripSizeSlider(
                    frac = wearStripSizeFrac,
                    onCommit = { vm.setWearStripSizeFrac(it) },
                )
                WearTraceDepthControlRow(
                    vm = vm,
                    effectiveFrac = traceDepthFrac,
                    globalDefault = traceDepthDefault,
                )
                WearBandShadeSlider(
                    frac = wearBandShadeFrac,
                    onCommit = { vm.setPdfWearBandShadeFrac(it) },
                )
                // How much bare shaft a combined taper+liner strip draws true before it
                // breaks — the same app-wide pref Settings → Drawing sets.
                WearJoinGapSlider(
                    gapMm = wearJoinGapMaxMm,
                    unit = unit,
                    onCommit = { vm.setPdfWearJoinGapMaxMm(it) },
                )
            }
        }

        // ── Runout bubbles ───────────────────────────────────────────────────
        // App-wide `PdfPrefs`, so the same pair sizes the Runout tab's canvas markers and
        // the printed sheet's — one radius and one drop behind both draw sites.
        if (showBubbleControls) {
            sections[OptionsSection.BUBBLES] = OptionsSectionBody {
                BubbleSizeSlider(
                    scale = runoutBubbleScale,
                    onCommit = { vm.setPdfRunoutBubbleScale(it) },
                )
                BubbleDropSlider(
                    scale = runoutBubbleDropScale,
                    onCommit = { vm.setPdfRunoutBubbleDropScale(it) },
                )
            }
        }

        // ── Thread ───────────────────────────────────────────────────────────
        // Only on a shaft that draws a thread: on any other the hatch controls tune nothing.
        if (hasThreads) {
            sections[OptionsSection.THREAD] = OptionsSectionBody(
                summary = threadSectionSummary(threadSlant, threadDensity),
            ) {
                ThreadSlantSlider(
                    threadSlant = threadSlant,
                    onCommit = { vm.setPdfThreadSlant(it) },
                )
                ThreadDensitySlider(
                    threadDensity = threadDensity,
                    onCommit = { vm.setPdfThreadDensity(it) },
                )
            }
        }

        // ── Lines & text ─────────────────────────────────────────────────────
        // Line thickness and the fraction style are ungated: every document this sheet
        // serves draws lines and prints lengths. The arrowhead picker only where rails print.
        sections[OptionsSection.LINES] = OptionsSectionBody(
            summary = linesSectionSummary(lineThicknessScale),
        ) {
            LineThicknessSlider(
                scale = lineThicknessScale,
                onCommit = { vm.setLineThicknessScale(it) },
                onDrag = { tuning?.lineThickness = it },
            )
            if (showDimensionArrows) {
                DimensionArrowSizeChips(
                    arrowSizePt = arrowSizePt,
                    onCommit = { vm.setPdfArrowSizePt(it) },
                )
            }
            FractionStyleChips(
                fractionStyle = fractionStyle,
                onCommit = { vm.setPdfFractionStyle(it) },
            )
        }

        // ── Shading ──────────────────────────────────────────────────────────
        sections[OptionsSection.SHADING] = OptionsSectionBody {
            ShadeInPdfCheckRows(
                pdfShadedBodies = pdfShadedBodies,
                pdfShadedTapers = pdfShadedTapers,
                pdfShadedLiners = pdfShadedLiners,
                shadeExplicitBodiesOnly = shadeExplicitBodiesOnly,
                onSetShadedBodies = { vm.setPdfShadedBodies(it) },
                onSetShadedTapers = { vm.setPdfShadedTapers(it) },
                onSetShadedLiners = { vm.setPdfShadedLiners(it) },
                onSetShadeExplicitBodiesOnly = { vm.setPdfShadeExplicitBodiesOnly(it) },
                linerShadeLocked = linerShadeLocked,
                showExplicitBodiesOnly = showShadeExplicitBodiesOnly,
                showUndercutLineArt = showUndercutLineArt,
                undercutLineArt = undercutLineArt,
                onSetUndercutLineArt = { vm.setPdfUndercutLineArt(it) },
                undercutLinerOutline = undercutLinerOutline,
                onSetUndercutLinerOutline = { vm.setPdfUndercutLinerOutline(it) },
            )
        }

        // ── Measurement reference ────────────────────────────────────────────
        if (showMeasurementReference) {
            sections[OptionsSection.MEASUREMENT] = OptionsSectionBody {
                MeasurementReferenceRadios(
                    pdfTieringMode = pdfTieringMode,
                    onCommit = { vm.setPdfTieringMode(it) },
                )
            }
        }

        // ── Units ────────────────────────────────────────────────────────────
        // The document's own `dual_units` switch where the host offers it, plus the app-wide
        // layout chips, which style what the switch turns on and so read as disabled until
        // it is.
        sections[OptionsSection.UNITS] = OptionsSectionBody(
            summary = unitsSectionSummary(dualUnits, dualUnitLayout),
        ) {
            if (onDualUnitsChange != null) {
                DualUnitsSwitchRow(dualUnits = dualUnits, onDualUnitsChange = onDualUnitsChange)
            }
            DualUnitLayoutChips(
                layout = dualUnitLayout,
                enabled = dualUnits,
                onCommit = { vm.setPdfDualUnitLayout(it) },
            )
        }

        OptionsSections(sections)

        Spacer(Modifier.height(24.dp))
    }
}
