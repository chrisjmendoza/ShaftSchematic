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
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.pdf.WEAR_STRIP_SIZE_FRAC_DEFAULT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_DEFAULT_MM
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.setLineThicknessScale
import com.android.shaftschematic.ui.viewmodel.setLinerCompression
import com.android.shaftschematic.ui.viewmodel.setLinersProportional
import com.android.shaftschematic.ui.viewmodel.setPdfArrowSizePt
import com.android.shaftschematic.ui.viewmodel.setPdfDualUnitLayout
import com.android.shaftschematic.ui.viewmodel.setPdfFractionStyle
import com.android.shaftschematic.ui.viewmodel.setPdfSBreakThresholdFrac
import com.android.shaftschematic.ui.viewmodel.setPdfShadedBodies
import com.android.shaftschematic.ui.viewmodel.setPdfShadedLiners
import com.android.shaftschematic.ui.viewmodel.setPdfShadedTapers
import com.android.shaftschematic.ui.viewmodel.setPdfTieringMode
import com.android.shaftschematic.ui.viewmodel.setPdfWearBandShadeFrac
import com.android.shaftschematic.ui.viewmodel.setPdfWearJoinGapMaxMm
import com.android.shaftschematic.ui.viewmodel.setRunoutHeightScale
import com.android.shaftschematic.ui.viewmodel.setShowCouplingFace
import com.android.shaftschematic.ui.viewmodel.setWearCompactStrips
import com.android.shaftschematic.ui.viewmodel.setWearShowShaftProfile
import com.android.shaftschematic.ui.viewmodel.setWearStripComponents
import com.android.shaftschematic.ui.viewmodel.setWearStripSizeFrac
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.InkBand
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
 * The "Export" button in the top bar lets the user proceed to the SAF file picker
 * after verifying the layout looks correct.
 *
 * @param bitmap        The rendered PDF page (null while rendering or on error).
 * @param loading       Whether the bitmap is still being generated.
 * @param title         Title shown in the top bar of the overlay.
 * @param onClose       Called when the user taps × or navigates back.
 * @param onExport      Called when the user taps the Export button.
 * @param optionsSheet  Optional composable content shown in a bottom sheet when the user
 *                      taps the Tune icon. When null, no Tune icon is shown.
 * @param sheetTunesPage Whether [optionsSheet] reshapes THIS page live. When true the open
 *                      sheet switches the preview to the tuning layout — the page redrawn
 *                      as a strip pinned under the toolbar, cropped to its ink, zoom/pan
 *                      reset, and the sheet capped ([tuningSheetMaxHeightDp]) to what is
 *                      left below the strip — and the sheet's scrim comes off, because
 *                      dimming the page being judged is exactly what the layout exists to
 *                      prevent (the black surround already separates strip from sheet).
 *                      Wear passes true as well — its controls commit on release rather than
 *                      mid-drag, but the commit still redraws THIS page, and a sheet that
 *                      covered it left the change unjudgeable (on-device report). Undercut
 *                      leaves it false: its sheet tunes nothing on the page.
 * @param inkBand       Where [bitmap] carries ink, from the route's render loop. The strip
 *                      crops to it so the page's blank top margin does not take room from
 *                      the drawing; null shows the whole page. Measured on sharp passes
 *                      only, so the strip never resizes under a moving finger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPreviewOverlay(
    bitmap: ImageBitmap?,
    loading: Boolean,
    title: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    optionsSheet: (@Composable () -> Unit)? = null,
    sheetTunesPage: Boolean = false,
    inkBand: InkBand? = null,
) {
    var showOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tuning layout: page strip on top, sheet below it. See [tuningPageStripHeightDp].
    // The sheet's drag handle and its navigation-bar inset stack OUTSIDE the content cap,
    // so both are budgeted here — a cap that ignored them left the sheet overlapping the
    // strip and swallowing the drawing's lowest callouts (on-device report).
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
        // A sheet that reshapes nothing on the page has no strip to stay clear of, just the
        // swipe-down edge at the top.
        (configuration.screenHeightDp * PREVIEW_SHEET_MAX_FRAC).dp
    }

    // Unlock device rotation while the preview is open so the landscape sheet can be viewed in
    // landscape (the app is otherwise locked to portrait); restore portrait on dismiss. Same
    // pattern as the schematic `PdfPreviewScreen`.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                FilledTonalButton(onClick = onExport, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
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
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    bitmap != null -> {
                        val scaleState  = remember(bitmap) { mutableFloatStateOf(1f) }
                        val offsetState = remember(bitmap) { mutableStateOf(Offset.Zero) }
                        val scale  by scaleState
                        val offset by offsetState

                        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                            scaleState.floatValue  = (scaleState.floatValue * zoomChange).coerceIn(0.5f, 8f)
                            offsetState.value = offsetState.value + panChange
                        }

                        // Opening a tuning sheet RESETS zoom/pan: predictable beats
                        // preserved — an inspection zoom would put the strip off-screen
                        // exactly when the sliders need it visible.
                        LaunchedEffect(stripLayout) {
                            if (stripLayout) {
                                scaleState.floatValue = 1f
                                offsetState.value = Offset.Zero
                            }
                        }

                        if (stripLayout) {
                            // The drawing, cropped to its ink band and fitted into the
                            // strip the sheet was sized to leave free. One draw helper
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
            }
        }
    }

    if (showOptions && optionsSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState,
            // `ModalBottomSheet`'s scrim is one full-window rect — it cannot be restricted
            // to the area below the page strip. A tuning sheet therefore takes none of it;
            // the overlay's black surround already reads as separation, and dimming the
            // page under a slider is what this layout exists to prevent.
            scrimColor = if (sheetTunesPage) Color.Transparent else BottomSheetDefaults.ScrimColor,
        ) {
            // The cap lives here, not in the sheet content: only the overlay knows the
            // strip the sheet has to stay clear of, and one owner keeps the two in step.
            Box(Modifier.heightIn(max = sheetMaxHeight)) { optionsSheet() }
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
    /** Locks the "Liners" shade row — see [ShadeInPdfChecks]. */
    linerShadeLocked: Boolean = false,
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
     * Shows the shared "Dimension arrows" size picker. On for the consolidated sheet, the only
     * document here that draws dimension rails; the classic runout/wear/undercut sheets draw
     * their own fixed-head marks, so the control would be inert there.
     */
    showDimensionArrows: Boolean = false,
    /** The app-wide `PdfPrefs.arrowSizePt`; read only when [showDimensionArrows]. */
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /**
     * The app-wide `PdfPrefs.fractionStyle`. Ungated, unlike the arrowhead size: every document
     * this sheet serves prints lengths, so every one of them draws fractions.
     */
    fractionStyle: FractionStyle = FractionStyle.STACKED,
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
     * Shows the per-job "Shaft height" + "Liner compression" pair (`RunoutConfig`). On for
     * both documents `composeRunoutPdf` produces — the classic runout sheet and the
     * consolidated one — since one composer means one drawn height and one liner floor; off
     * for the wear and undercut documents, whose composers take neither, so the pair would
     * be inert noise there.
     */
    showHeightControls: Boolean = false,
    heightScale: Float = 1f,
    heightSliderBase: Float = 1f,
    heightSliderMaxDiaMm: Float = 10f,
    linersProportional: Boolean = false,
    linerCompression: Float = 0f,
    estimateKeptFrac: (Float) -> Float = { it },
    /**
     * Shows the "Measurement reference" radios. On only for the consolidated sheet: its
     * dimension rails honor `PdfPrefs.tieringMode`, while the classic runout/wear/undercut
     * documents draw no rails, so the radios would be inert there.
     */
    showMeasurementReference: Boolean = false,
    pdfTieringMode: PdfTieringMode = PdfTieringMode.AUTO,
) {
    // Scrollable + inset-padded: without its own scroll a short screen clips the bottom
    // rows mid-checkbox behind the navigation bar (same posture as PdfOptionsSheet). The
    // height cap belongs to the hosting `PdfPreviewOverlay` — only it knows the page strip
    // a tuning sheet must stay clear of.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text("PDF Options", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // ── Blank draft (write-in) ───────────────────────────────────────────
        if (onSetBlankDraft != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = blankDraft,
                    onCheckedChange = onSetBlankDraft,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info, dimensions, and recorded values are blanked for handwriting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Coupling face (runout + consolidated sheets) ─────────────────────
        // A content election, so it sits with the other "what does this sheet carry"
        // controls rather than the styling sliders. Per-job: it rides the envelope's
        // RunoutConfig, not PdfPrefs — one job may measure the coupling and the next not.
        if (showCouplingFaceRow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = couplingFaceOn,
                    onCheckedChange = { vm.setShowCouplingFace(it) },
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Coupling face", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "End view in the bottom-right: coupling OD, pilot bore, bolt circle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Line thickness ───────────────────────────────────────────────────
        LineThicknessSlider(
            scale = lineThicknessScale,
            onCommit = { vm.setLineThicknessScale(it) },
            onDrag = { tuning?.lineThickness = it },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Wear drawing (wear document only) ────────────────────────────────
        // The same controls the Wear tab and Settings → Drawing carry — here so the strip
        // height, the trace depth, the band's grey, and the join threshold can be judged
        // against the sheet they print on.
        // Commit-on-release, like every slider on this sheet; the wear preview re-renders
        // from its own keys rather than a live tuning channel.
        if (showWearControls) {
            // What the sheet draws comes first: the whole-shaft profile toggle and the
            // per-component strip election, above the controls that restyle what's drawn.
            WearStripComponentChecks(
                options = wearStripOptions,
                selection = wearStripSelection,
                defaultIds = wearStripDefaultIds,
                showShaftProfile = wearShowShaftProfile,
                onSetShowShaftProfile = { vm.setWearShowShaftProfile(it) },
                compactStrips = wearCompactStrips,
                onSetCompactStrips = { vm.setWearCompactStrips(it) },
                // Nullable: the "Default (all liners)" quick action clears the election so the
                // sheet follows the shaft again.
                onSetSelection = { vm.setWearStripComponents(it) },
            )

            Spacer(Modifier.height(12.dp))

            // A primary layout control — how tall the elected strips draw — so it sits with the
            // election rather than with the restyling sliders below.
            WearStripSizeSlider(
                frac = wearStripSizeFrac,
                onCommit = { vm.setWearStripSizeFrac(it) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            WearTraceDepthControlRow(
                vm = vm,
                effectiveFrac = traceDepthFrac,
                globalDefault = traceDepthDefault,
            )

            Spacer(Modifier.height(12.dp))

            WearBandShadeSlider(
                frac = wearBandShadeFrac,
                onCommit = { vm.setPdfWearBandShadeFrac(it) },
            )

            Spacer(Modifier.height(12.dp))

            // How much bare shaft a combined taper+liner strip draws true before it breaks —
            // the same app-wide pref Settings → Drawing sets.
            WearJoinGapSlider(
                gapMm = wearJoinGapMaxMm,
                unit = unit,
                onCommit = { vm.setPdfWearJoinGapMaxMm(it) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Body S-break ─────────────────────────────────────────────────────
        // The same app-wide `PdfPrefs.sBreakThresholdFrac` Settings → Drawing sets —
        // here so the threshold can be judged against the drawing it changes.
        if (showSBreak) {
            SBreakThresholdSlider(
                frac = sBreakThresholdFrac,
                onCommit = { vm.setPdfSBreakThresholdFrac(it) },
                onDrag = { tuning?.sBreakFrac = it },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Shaft height / Liner compression ────────────────────────────────
        // Same per-job pair as the Consolidated Output tab (`RunoutConfig`), and they sit
        // with Line thickness and Body S-break: those are the sliders that reshape the page
        // under a finger, the live-tuning group the page strip above this sheet exists to
        // keep in view. The sheet is taller than its cap and scrolls, so a tuning slider
        // parked below the typography rows reads as absent (on-device report). Same order as
        // the schematic's `PdfOptionsSheet`.
        if (showHeightControls) {
            ShaftHeightSlider(
                heightScale = heightScale,
                baseScale = heightSliderBase,
                maxDiaMm = heightSliderMaxDiaMm,
                onCommit = { vm.setRunoutHeightScale(it) },
                onDrag = { tuning?.heightScale = it },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            LinerCompressionControl(
                linersProportional = linersProportional,
                linerCompression = linerCompression,
                estimateKeptFrac = estimateKeptFrac,
                onSetProportional = { vm.setLinersProportional(it) },
                onSetCompression = { vm.setLinerCompression(it) },
                onDrag = { tuning?.linerCompression = it },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Dimension arrows ─────────────────────────────────────────────────
        if (showDimensionArrows) {
            DimensionArrowSizeChips(
                arrowSizePt = arrowSizePt,
                onCommit = { vm.setPdfArrowSizePt(it) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Fractions ────────────────────────────────────────────────────────
        // Ungated: every document this sheet serves prints lengths.
        FractionStyleChips(
            fractionStyle = fractionStyle,
            onCommit = { vm.setPdfFractionStyle(it) },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Measurement reference ────────────────────────────────────────────
        if (showMeasurementReference) {
            Text("Measurement reference", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            listOf(
                PdfTieringMode.AUTO to "Auto (closest end)",
                PdfTieringMode.AFT  to "AFT",
                PdfTieringMode.FWD  to "FWD",
            ).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = pdfTieringMode == mode,
                        onClick = { vm.setPdfTieringMode(mode) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Shade in PDF ─────────────────────────────────────────────────────
        ShadeInPdfChecks(
            pdfShadedBodies = pdfShadedBodies,
            pdfShadedTapers = pdfShadedTapers,
            pdfShadedLiners = pdfShadedLiners,
            onSetShadedBodies = { vm.setPdfShadedBodies(it) },
            onSetShadedTapers = { vm.setPdfShadedTapers(it) },
            onSetShadedLiners = { vm.setPdfShadedLiners(it) },
            linerShadeLocked = linerShadeLocked,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Dual units — LAST by design ──────────────────────────────────────
        // Drawing- and output-specific controls lead the sheet; rarely used options
        // trail (on-device request). The layout chips style what the switch turns on,
        // so they read as disabled until it is.
        if (onDualUnitsChange != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = dualUnits, onCheckedChange = onDualUnitsChange)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Dual units", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Print every dimension in both inches and millimetres. Saved with the document.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        DualUnitLayoutChips(
            layout = dualUnitLayout,
            enabled = dualUnits,
            onCommit = { vm.setPdfDualUnitLayout(it) },
        )

        Spacer(Modifier.height(24.dp))
    }
}
