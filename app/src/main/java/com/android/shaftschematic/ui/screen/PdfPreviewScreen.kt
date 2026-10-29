// file: app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt
package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.Context
import com.android.shaftschematic.ui.adaptive.currentWindowWidthClass
import com.android.shaftschematic.ui.adaptive.restoreBaseOrientation
import com.android.shaftschematic.ui.adaptive.unlockRotation
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.android.shaftschematic.settings.PdfTieringMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.nav.FINAL_DRAWING_LABEL
import com.android.shaftschematic.ui.nav.FinalSheetKind
import com.android.shaftschematic.ui.nav.composeSchematicSheet
import com.android.shaftschematic.ui.nav.finalDrawingSuffix
import com.android.shaftschematic.ui.nav.finalSheetKind
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.SpecTarget
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.OutputFont
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import kotlin.math.roundToInt

/**
 * PdfPreviewScreen
 *
 * Purpose
 * Shows a full-resolution preview of the PDF that will be exported, rasterized through the
 * shared `util/PdfRaster.renderPdfPageBitmap` (the real composed page, never a separate
 * draw path). Supports pinch-to-zoom (and double-tap to reset) so users can inspect
 * dimension labels before committing to an export.
 *
 * Contract
 * - Rendering runs on Dispatchers.IO; a loading indicator is shown meanwhile.
 * - The Tune action toggles a non-modal options panel ([PreviewOptionsPanel]) in this
 *   screen's own layout. While it is open the page redraws as a fit-width strip pinned under
 *   the app bar, and the panel is capped to what is left below it — the sliders tune the page
 *   live, so the page has to stay in sight. At EXPANDED the panel docks as a right-hand
 *   column instead and the whole page fits, at full height, into the width beside it. The
 *   page stays pinch-zoomable and pannable under the open panel, and a zoom survives a
 *   re-render; opening or closing the panel resets it, since the layouts fit the page
 *   differently.
 * - If rendering fails, a plain error message is shown (no crash).
 * - The "Export PDF" action in the top bar calls [onExport] to proceed to the SAF picker.
 * - No model state is mutated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    /**
     * Which of the document's two drawings is previewed. From the route argument, never a flag
     * on the ViewModel: the preview, its Print, and the export it hands off to must all be the
     * same drawing. It rides [SchematicRenderInputs] as a re-render key, since switching target
     * changes nothing else the loop already watches.
     */
    target: SpecTarget = SpecTarget.ORIGINAL,
) {
    val ctx = LocalContext.current
    val isFinal = target == SpecTarget.FINAL

    // Unlock rotation for this screen only; restore the device's base orientation when leaving
    // (portrait on a phone, free rotation on a tablet).
    val activity = ctx as? Activity
    DisposableEffect(Unit) {
        activity?.unlockRotation()
        onDispose { activity?.restoreBaseOrientation() }
    }

    // The final drawing is the document's SECOND geometry — its own spec and resolved
    // components, not a view of the original.
    val originalSpec by vm.spec.collectAsState()
    val finalSpec by vm.finalSpec.collectAsState()
    val spec = if (isFinal) finalSpec ?: ShaftSpec() else originalSpec
    val unit by vm.unit.collectAsState()
    // The per-job "Shaft height" multiplier lives in RunoutConfig — ONE value behind the
    // schematic, the runout sheet, and the consolidated output.
    val runoutConfig by vm.runoutConfig.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val pdfBlankDraft by vm.pdfBlankDraft.collectAsState()
    val pdfBlankDiaCallouts by vm.pdfBlankDiaCallouts.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val item by vm.item.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val originalResolved by vm.resolvedComponents.collectAsState()
    val finalResolved by vm.finalResolvedComponents.collectAsState()
    val resolvedComponents = if (isFinal) finalResolved else originalResolved
    val pdfShowComponentTitles by vm.pdfShowComponentTitles.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()
    val pdfShadeExplicitBodiesOnly by vm.pdfShadeExplicitBodiesOnly.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    // Arrowhead size: a chip tap commits straight to PdfPrefs, so the render loop needs it as
    // an input key or the page would keep the old heads.
    val pdfArrowSizePt by vm.pdfArrowSizePt.collectAsState()
    val pdfThreadSlant by vm.pdfThreadSlant.collectAsState()
    val pdfThreadDensity by vm.pdfThreadDensity.collectAsState()
    // Fraction style: same posture — a chip tap changes the renderer's active style, which the
    // loop cannot observe, so it rides along as an input key.
    val pdfFractionStyle by vm.pdfFractionStyle.collectAsState()
    val pdfOutputFont by vm.pdfOutputFont.collectAsState()
    val pdfDualUnitLayout by vm.pdfDualUnitLayout.collectAsState()
    // Sizing-curve anchors: the composer sizes the drawn shaft off them, so a Settings
    // change to "Default drawing size" has to re-render an open preview. Collected here,
    // not only inside the Tune sheet, or the page would keep its old height until some
    // other input moved.
    val curveLoHeightIn by vm.pdfCurveLoHeightIn.collectAsState()
    val curveHiHeightIn by vm.pdfCurveHiHeightIn.collectAsState()
    // Per-component display units + inline-dual flag: the composer reads them through
    // `displayUnits`, which neither `unit` nor any field above already covers, so both
    // ride along as explicit render-loop inputs (same posture as `fractionStyle`).
    val unitOverrides by vm.unitOverrides.collectAsState()
    val dualUnits by vm.dualUnits.collectAsState()
    // The final drawing's optional runout stations. Session-only and off by default; consulted
    // only on the FINAL target — see `ShaftViewModel.finalRunoutBubbles`.
    val finalRunoutBubbles by vm.finalRunoutBubbles.collectAsState()

    // A final sheet is marked so it can never be mistaken for the original — the label
    // prints in the footer job block and as a badge beside the Side badge.
    val drawingLabel = if (isFinal) FINAL_DRAWING_LABEL else ""
    val project = remember(customer, vessel, shaftPosition, jobNumber, item, drawingLabel) {
        ProjectInfo(
            customer = customer, vessel = vessel, side = shaftPosition,
            jobNumber = jobNumber, item = item, drawingLabel = drawingLabel,
        )
    }
    val options = remember(pdfExportMode, pdfBlankDraft, pdfBlankDiaCallouts) {
        PdfExportOptions(
            mode = pdfExportMode,
            blankValues = pdfBlankDraft,
            blankDiaCallouts = pdfBlankDiaCallouts,
        )
    }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // Where the composed page actually carries ink — the tuning strip crops to it so blank
    // paper never takes room from the drawing. Measured on sharp passes only (see below).
    var inkBand by remember { mutableStateOf<InkBand?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    // Live slider drags — visual-only overrides, never a write. See [PreviewTuning].
    val tuning = rememberPreviewTuning()

    // The render loop. One RenderInputs value carries EVERYTHING the composed page reads
    // (an omission here is a stale-preview bug); `snapshotFlow { … }.conflate()` renders
    // the newest inputs and drops the intermediate frames a slider drag produces while a
    // render is in flight. When a drag ends the overrides go null, the inputs change once
    // more, and that final pass rasterizes at full resolution.
    LaunchedEffect(Unit) {
        var previousWasDraft = false
        snapshotFlow {
            val config = tunedRunoutConfig(runoutConfig, tuning.heightScale, tuning.linerCompression)
            SchematicRenderInputs(
                spec = spec,
                unit = unit,
                project = ProjectInfo(
                    customer = customer, vessel = vessel,
                    side = shaftPosition, jobNumber = jobNumber, item = item,
                    drawingLabel = drawingLabel,
                ),
                options = PdfExportOptions(
                    mode = pdfExportMode,
                    blankValues = pdfBlankDraft,
                    blankDiaCallouts = pdfBlankDiaCallouts,
                ),
                resolved = resolvedComponents,
                lineThicknessScale = tuning.lineThickness ?: lineThicknessScale,
                showComponentTitles = pdfShowComponentTitles,
                tieringMode = pdfTieringMode,
                shadedBodies = pdfShadedBodies,
                shadedTapers = pdfShadedTapers,
                shadedLiners = pdfShadedLiners,
                shadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
                sBreakThresholdFrac = tuning.sBreakFrac ?: pdfSBreakThresholdFrac,
                arrowSizePt = pdfArrowSizePt,
                fractionStyle = pdfFractionStyle,
                outputFont = pdfOutputFont,
                threadSlant = pdfThreadSlant,
                threadDensity = pdfThreadDensity,
                dualUnitLayout = pdfDualUnitLayout,
                curveLoHeightIn = curveLoHeightIn,
                curveHiHeightIn = curveHiHeightIn,
                runoutConfig = config,
                displayUnits = DisplayUnits(unit, unitOverrides, dualUnits),
                target = target,
                sheetKind = finalSheetKind(target, finalRunoutBubbles),
                draft = tuning.active,
            )
        }.conflate().collect { inputs ->
            // A drag frame — and the sharp pass right after one — keeps the current page on
            // screen: swapping in the spinner between frames would strobe the preview.
            val quiet = inputs.draft || previousWasDraft
            previousWasDraft = inputs.draft
            if (!quiet) isLoading = true
            errorMessage = null
            // Snapshot on the main thread before switching to IO.
            val pdfPrefsSnapshot = tunedPdfPrefs(vm.currentPdfPrefs, inputs.sBreakThresholdFrac)
            val versionSnapshot = appVersionName(ctx)
            // The ink band is measured on the raw raster, and only on a sharp (non-draft)
            // pass: a drag frame that resized the strip — and with it the sheet cap — would
            // shuffle the layout under a moving finger.
            val (bmp, band) = withContext(Dispatchers.IO) {
                val raster = renderPdfPageBitmap(
                    ctx,
                    renderScale = previewRenderScale(inputs.draft),
                ) { page ->
                    composeSchematicSheet(
                        page = page,
                        kind = inputs.sheetKind,
                        spec = inputs.spec,
                        unit = inputs.unit,
                        project = inputs.project,
                        appVersion = versionSnapshot,
                        filename = "preview",
                        pdfPrefs = pdfPrefsSnapshot,
                        options = inputs.options,
                        resolvedComponents = inputs.resolved.takeIf { it.isNotEmpty() },
                        lineThicknessScale = inputs.lineThicknessScale,
                        runoutConfig = inputs.runoutConfig,
                        displayUnits = inputs.displayUnits,
                    )
                }
                raster to raster?.takeIf { !inputs.draft }?.inkBand()
            }
            if (bmp != null) {
                previewBitmap = bmp.asImageBitmap()
                if (!inputs.draft) inkBand = band
            } else {
                errorMessage = "Could not render preview."
            }
            isLoading = false
        }
    }

    // ── Pan / Zoom state ──────────────────────────────────────────────────────
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // ── Tuning layout ─────────────────────────────────────────────────────────
    // While the options panel is open the page moves into a strip pinned under the app bar
    // and the panel is capped just below it, so a slider's effect is visible as it is
    // dragged ("I can see the PDF Preview area lighten up on moving a slider but I can't
    // see anything" — on-device report). The strip carries the page's INK BAND, so the
    // drawing gets the room the blank top margin used to hold. Strip and cap are sized inside
    // the preview box below, from its measured height, and cannot disagree.
    // At EXPANDED the panel docks beside the page instead, and the page keeps the full-page
    // layout at full height in the width left of it ([previewPageLayout]).
    val configuration = LocalConfiguration.current
    val pageLayout = previewPageLayout(
        optionsOpen = showOptions,
        sheetTunesPage = true,
        docksBeside = previewPanelDocksBeside(currentWindowWidthClass()),
    )
    // Zoom/pan resets when the panel opens AND when it closes: the strip, the docked page and
    // the full-page layout fit the page into different boxes, so a zoom carried across any
    // transition would point at the wrong place. Between those transitions the zoom is
    // deliberately KEPT — a re-render swaps the bitmap but every pass fits it into the same box,
    // so a zoom on the threads stays on the threads while their sliders commit (on-device
    // request).
    LaunchedEffect(pageLayout) {
        scale.snapTo(1f)
        offset.snapTo(Offset.Zero)
    }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val old = scale.value
                val new = (old * zoom).coerceIn(0.5f, 8.0f)
                val z = if (old != 0f) new / old else 1f
                val newOffset = offset.value * z + centroid * (1f - z) + pan
                scope.launch {
                    if (new != old) scale.snapTo(new)
                    offset.snapTo(newOffset)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    scope.launch {
                        scale.animateTo(1f, tween(140))
                        offset.animateTo(Offset.Zero, tween(140))
                    }
                }
            )
        }

    // ── UI ───────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                // At most two lines, ellipsized — the Print button beside it wraps the title, and
                // at a large font scale an uncapped title breaks mid-word over four lines.
                title = {
                    Text(
                        if (isFinal) "Final PDF Preview" else "PDF Preview",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // A toggle: the panel is non-modal, so the app bar stays reachable while
                    // it is open and the same icon closes it.
                    IconButton(onClick = { showOptions = !showOptions }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                scale.animateTo(1f, tween(140))
                                offset.animateTo(Offset.Zero, tween(140))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom"
                        )
                    }
                    // Print leads Export in weight: paper is the daily output, a PDF file
                    // the backup copy. Same treatment as the document tabs' preview overlay.
                    FilledTonalButton(onClick = {
                        val suggested = DocumentNaming.suggestedBaseName(
                            jobNumber = jobNumber,
                            customer = customer,
                            vessel = vessel,
                            suffix = shaftPosition.printableLabelOrNull(),
                        ) ?: "Shaft Schematic"
                        // The print job carries the drawing's identity too — a job queue full
                        // of same-named sheets is how a final gets handed out as the original.
                        val baseName =
                            if (isFinal) suggested + finalDrawingSuffix(finalRunoutBubbles)
                            else suggested
                        val jobName = if (pdfBlankDraft) "$baseName (blank draft)" else baseName
                        // Snapshot state on the UI thread; onWrite runs on a binder thread.
                        val kindSnapshot = finalSheetKind(target, finalRunoutBubbles)
                        val specSnapshot = spec
                        val unitSnapshot = unit
                        val projectSnapshot = project
                        val optionsSnapshot = options
                        val resolvedSnapshot = resolvedComponents.takeIf { it.isNotEmpty() }
                        val prefsSnapshot = vm.currentPdfPrefs
                        val thicknessSnapshot = lineThicknessScale
                        val configSnapshot = runoutConfig
                        val displayUnitsSnapshot = vm.currentDisplayUnits()
                        val versionSnapshot = appVersionName(ctx)
                        printShaftPdfPage(ctx, jobName) { page ->
                            composeSchematicSheet(
                                page = page,
                                kind = kindSnapshot,
                                spec = specSnapshot,
                                unit = unitSnapshot,
                                project = projectSnapshot,
                                appVersion = versionSnapshot,
                                filename = "$jobName.pdf",
                                pdfPrefs = prefsSnapshot,
                                options = optionsSnapshot,
                                resolvedComponents = resolvedSnapshot,
                                lineThicknessScale = thicknessSnapshot,
                                runoutConfig = configSnapshot,
                                displayUnits = displayUnitsSnapshot,
                            )
                        }
                    }) {
                        Icon(imageVector = Icons.Filled.Print, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Print")
                    }
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Export PDF"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentError = errorMessage
        val currentBitmap = previewBitmap
        // `clipToBounds` keeps the zoomed/panned page inside this Box: the canvas transform
        // draws outside the layout node unless clipped, so a zoomed-in page could slide up OVER
        // the app bar and hide its actions. The page tucks BEHIND the bar instead (the shared
        // overlay clips for the same reason). `paddingValues` carries the Scaffold's default
        // content insets — the navigation bar included — so the options panel anchored to this
        // Box's bottom sits above the navigation bar without paying the inset itself.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            // Budgeted from THIS box's measured size: it already sits under the app bar and above
            // the navigation bar, so the only panel chrome inside it is the header row. An
            // allowance for the bars instead fell short of a tall status bar and let the panel
            // cover the strip's last rows (on-device report).
            val stripHeightDp = tuningPageStripHeightDp(
                maxWidth.value,
                configuration.screenHeightDp.toFloat(),
                TUNING_SHEET_CHROME_DP,
                inkBand?.frac ?: 1f,
                availableHeightDp = maxHeight.value,
            )
            val maxSheetHeight = tuningSheetMaxHeightDp(
                configuration.screenHeightDp.toFloat(),
                stripHeightDp,
                TUNING_SHEET_CHROME_DP,
                availableHeightDp = maxHeight.value,
            ).dp
            val docked = pageLayout == PreviewPageLayout.DOCKED
            // The page block: the whole box, or — docked — the width left of the panel at full
            // height. ONE block in either layout; only its box changes.
            Box(
                modifier = if (docked) {
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(dockedPageWidthDp(maxWidth.value).dp)
                } else {
                    Modifier.fillMaxSize()
                },
                contentAlignment = Alignment.Center,
            ) {
                // The full spinner stands in for the page only until a FIRST page exists. A
                // re-render after that — a commit-on-release slider such as Thread slant — keeps
                // the current page (and the zoom on it) on screen and signals the pass with the
                // small corner indicator below: swapping the page out for a spinner at exactly
                // the moment a change is being watched made the change unwatchable.
                when {
                    isLoading && currentBitmap == null -> {
                        CircularProgressIndicator()
                    }

                    currentError != null -> {
                        Text(
                            text = currentError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    currentBitmap != null -> {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(gestures)
                                .testTag("pdf_preview_canvas")
                        ) {
                            withTransform({
                                translate(offset.value.x, offset.value.y)
                                scale(scale.value, scale.value, Offset.Zero)
                            }) {
                                if (pageLayout == PreviewPageLayout.STRIP) {
                                    // Tuning layout: the drawing, cropped to its ink band,
                                    // fitted into the strip the panel was sized to leave free
                                    // and top-aligned under the app bar.
                                    drawPageBand(currentBitmap, inkBand, stripHeightDp.dp.toPx())
                                } else {
                                    // Normal preview, and the docked page: the whole page — real
                                    // paper, blank margins included — fitted to the canvas and
                                    // centered.
                                    val imgW = currentBitmap.width.toFloat()
                                    val imgH = currentBitmap.height.toFloat()
                                    val fitScale = minOf(size.width / imgW, size.height / imgH)
                                    val fittedW = imgW * fitScale
                                    val fittedH = imgH * fitScale
                                    drawImage(
                                        image = currentBitmap,
                                        dstOffset = androidx.compose.ui.unit.IntOffset(
                                            ((size.width - fittedW) / 2f).toInt(),
                                            ((size.height - fittedH) / 2f).toInt()
                                        ),
                                        dstSize = androidx.compose.ui.unit.IntSize(
                                            fittedW.toInt(),
                                            fittedH.toInt()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (isLoading && currentBitmap != null) {
                    PreviewRerenderIndicator(Modifier.align(Alignment.TopEnd))
                }

                // Always-visible blank-draft toggle, overlaid on the preview — the SAME
                // session-only state as the options panel's chip, so the two can never
                // disagree. Surfaced here because the options buried it (on-device report:
                // hard to find, even when demoing the app). Toggling re-renders the
                // preview live, so what's shown is always what will print. Hidden while the
                // options panel is open: it would sit on the page strip, and the panel's own
                // first row is this same election.
                if (!showOptions) {
                    // The chip floats over the page, so its container is OPAQUE and it casts a
                    // shadow: an M3 filter chip's unselected container is transparent by default,
                    // which let the drawing's dimension rails and OAL value read straight through
                    // the label as the page was panned under it (on-device report). Theme-driven,
                    // not sheet ink — this is an interactive affordance over the paper, not
                    // something printed on it, and a forced-white box would take dark theme's
                    // near-white label with it.
                    val chipShape = MaterialTheme.shapes.small
                    FilterChip(
                        selected = pdfBlankDraft,
                        onClick = { vm.setPdfBlankDraft(!pdfBlankDraft) },
                        label = { Text("Blank draft (write-in)") },
                        leadingIcon = if (pdfBlankDraft) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                        shape = chipShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .shadow(2.dp, chipShape)
                            .testTag("pdf_blank_toggle"),
                    )
                }
            }

            // The options, as a non-modal panel in this Box — composed LAST so it draws over
            // the page and takes the touches that land on it, while the page strip above it
            // keeps its pinch and pan. No scrim: nothing here is modal, and the page is the
            // thing being judged. Docked, it is the column right of the page block instead.
            if (showOptions) {
                PreviewOptionsPanel(
                    onClose = { showOptions = false },
                    maxHeightDp = maxSheetHeight,
                    docked = docked,
                    modifier = if (docked) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(dockedPanelWidthDp(maxWidth.value).dp)
                    } else {
                        Modifier.align(Alignment.BottomCenter)
                    },
                ) {
                    PdfOptionsSheet(
                        vm = vm,
                        spec = spec,
                        pdfShowComponentTitles = pdfShowComponentTitles,
                        pdfTieringMode = pdfTieringMode,
                        lineThicknessScale = lineThicknessScale,
                        sBreakThresholdFrac = pdfSBreakThresholdFrac,
                        arrowSizePt = pdfArrowSizePt,
                        threadSlant = pdfThreadSlant,
                        threadDensity = pdfThreadDensity,
                        fractionStyle = pdfFractionStyle,
                        dualUnitLayout = pdfDualUnitLayout,
                        dualUnits = dualUnits,
                        heightScale = runoutConfig.heightScale,
                        linersProportional = runoutConfig.linersProportional,
                        linerCompression = runoutConfig.linerCompression,
                        curveLoHeightIn = curveLoHeightIn,
                        curveHiHeightIn = curveHiHeightIn,
                        pdfShadedBodies = pdfShadedBodies,
                        pdfShadedTapers = pdfShadedTapers,
                        pdfShadedLiners = pdfShadedLiners,
                        pdfShadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
                        pdfBlankDraft = pdfBlankDraft,
                        pdfBlankDiaCallouts = pdfBlankDiaCallouts,
                        // A FINAL-drawing election only — the original schematic's stations
                        // are the Runout and Consolidated Output tabs' documents.
                        showFinalRunoutBubbles = isFinal,
                        finalRunoutBubbles = finalRunoutBubbles,
                        tuning = tuning,
                    )
                }
            }
        }
    }
}

/**
 * Everything one composed schematic preview page depends on, in one structural-equality
 * value — the render loop's unit of work.
 *
 * Some fields never reach [composeShaftPdf] directly: the shade flags, component titles,
 * tiering mode, S-break threshold, arrowhead size and sizing-curve anchors travel inside the
 * `PdfPrefs` snapshot taken at render time, and the fraction style through
 * `FractionTypography.active`. They are held here because the loop must RE-RENDER when
 * they change, and neither of those reads is snapshot state.
 */
private data class SchematicRenderInputs(
    val spec: ShaftSpec,
    val unit: UnitSystem,
    val project: ProjectInfo,
    val options: PdfExportOptions,
    val resolved: List<ResolvedComponent>,
    val lineThicknessScale: Float,
    val showComponentTitles: Boolean,
    val tieringMode: PdfTieringMode,
    val shadedBodies: Boolean,
    val shadedTapers: Boolean,
    val shadedLiners: Boolean,
    /** Narrows the body shade to authored sections. Key only — it travels inside the
     *  `PdfPrefs` snapshot. The bubble prefs never reach this composer, so they stay out. */
    val shadeExplicitBodiesOnly: Boolean,
    val sBreakThresholdFrac: Float,
    val arrowSizePt: Float,
    val fractionStyle: FractionStyle,
    /**
     * The typeface the sheet is set in. Not a composer argument either — it reaches the ink via
     * `OutputTypography.active`. Key only.
     */
    val outputFont: OutputFont,
    /**
     * The thread-hatch slant. Not a composer argument — it reaches the ink via
     * `ThreadHatchStyle.slant`. Key only.
     */
    val threadSlant: Float,
    /**
     * The thread-hatch density. Not a composer argument — it reaches the ink via
     * `ThreadHatchStyle.density`. Key only.
     */
    val threadDensity: Float,
    /**
     * A LAYOUT input, not just a key: the composers take it as a parameter, and a sheet whose
     * budget cannot absorb the taller stacked value falls back to inline on its own.
     */
    val dualUnitLayout: DualUnitLayout,
    val curveLoHeightIn: Float,
    val curveHiHeightIn: Float,
    /**
     * The per-job fit, already tuned by any in-progress slider drag. Held whole rather than as
     * its two derived terms because the bubbled sheet reads more of it — the coupling-face
     * election rides here too, and a field the record cannot express is a stale-preview bug.
     */
    val runoutConfig: RunoutConfig,
    val displayUnits: DisplayUnits,
    /**
     * Which drawing this page is. [spec] and [project] already differ between the two, so it
     * is a key rather than a composer input — held here because the target is what SELECTS
     * them, and a record that cannot express it invites a future field being read off the
     * wrong drawing without the loop noticing.
     */
    val target: SpecTarget,
    /**
     * Which sheet this page IS — the plain schematic, or the final drawing's Schematic +
     * Runout variant. A composer input AND a re-render key: the "Runout bubbles" chip changes
     * nothing else the loop already watches.
     */
    val sheetKind: FinalSheetKind,
    /** A tuning slider is mid-drag: raster at draft resolution and hold the spinner back. */
    val draft: Boolean,
)

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun appVersionName(context: Context): String = runCatching {
    val pm = context.packageManager
    val pkg = context.packageName
    if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0"
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, 0).versionName ?: "0"
    }
}.getOrDefault("0")

@Composable
internal fun PdfOptionsSheet(
    vm: ShaftViewModel,
    spec: ShaftSpec,
    pdfShowComponentTitles: Boolean,
    pdfTieringMode: PdfTieringMode,
    lineThicknessScale: Float,
    sBreakThresholdFrac: Float,
    arrowSizePt: Float,
    threadSlant: Float,
    threadDensity: Float,
    fractionStyle: FractionStyle,
    dualUnitLayout: DualUnitLayout,
    dualUnits: Boolean,
    heightScale: Float,
    linersProportional: Boolean,
    linerCompression: Float,
    curveLoHeightIn: Float,
    curveHiHeightIn: Float,
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
    pdfShadeExplicitBodiesOnly: Boolean,
    pdfBlankDraft: Boolean,
    pdfBlankDiaCallouts: Boolean,
    /**
     * Whether the "Runout bubbles" election is offered at all. True only on the FINAL drawing:
     * the original schematic's stations already have their own two documents, and a second way
     * to print them would leave two sheets claiming to be the runout record.
     */
    showFinalRunoutBubbles: Boolean = false,
    finalRunoutBubbles: Boolean = false,
    /**
     * Live-tuning sink: every slider here reports its in-progress value so the preview
     * above the panel reshapes under the finger. Visual only — the commit path is
     * unchanged and nothing persists on a drag frame.
     */
    tuning: PreviewTuning,
) {
    // Scrollable: the content is taller than a phone screen. The height cap and the title
    // belong to the hosting [PreviewOptionsPanel], and the navigation-bar inset is paid by the
    // Scaffold's content padding the panel is laid out inside, so this column carries neither.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        // This sheet carries the per-job Shaft height and Liner compression alongside the
        // app-wide look prefs, so the note points at their captions.
        OptionsScopeNote(hasJobControls = true)
        Spacer(Modifier.height(12.dp))

        // ── Content ──────────────────────────────────────────────────────────
        // What this sheet CARRIES, as chips. The captions moved to Help: on a sheet capped
        // below the page strip, three explained switch rows cost exactly the room the
        // sliders under them need. "Ø callouts" is a sub-election of the blank draft — a
        // write-in sheet either carries Ø leaders ready to fill in or prints clear so the
        // diameters can be hand-written wherever they belong — so it is visible but
        // untappable until blank mode is on, where it has no effect otherwise.
        ContentChipRow {
            ContentChip(
                label = "Blank draft",
                selected = pdfBlankDraft,
                onClick = { vm.setPdfBlankDraft(!pdfBlankDraft) },
            )
            // The consolidated composer behind a bubbled final sheet takes no Ø-callout
            // election (nor Template mode), so the chip is inert there and greys out.
            ContentChip(
                label = "Ø callouts",
                selected = pdfBlankDiaCallouts,
                enabled = pdfBlankDraft && !(showFinalRunoutBubbles && finalRunoutBubbles),
                onClick = { vm.setPdfBlankDiaCallouts(!pdfBlankDiaCallouts) },
                modifier = Modifier.testTag("pdf_blank_dia_callouts_toggle"),
            )
            // The global default under each component's tri-state "Show name on drawing"
            // gate — an authored per-component choice still wins over it.
            ContentChip(
                label = "Component names",
                selected = pdfShowComponentTitles,
                onClick = { vm.setPdfShowComponentTitles(!pdfShowComponentTitles) },
                modifier = Modifier.testTag("pdf_component_names_chip"),
            )
            // Content, not styling — it decides which sheet this is — so it sits with the
            // chips rather than at the foot of the sheet with the rarely-used options.
            if (showFinalRunoutBubbles) {
                ContentChip(
                    label = "Runout bubbles",
                    selected = finalRunoutBubbles,
                    onClick = { vm.setFinalRunoutBubbles(!finalRunoutBubbles) },
                    modifier = Modifier.testTag("final_runout_bubbles_toggle"),
                )
            }
        }

        if (showFinalRunoutBubbles) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Adds runout stations to the final drawing. Off for the welding and machining " +
                    "copy; on for the pre-ship measurement sheet, which prints as the " +
                    "consolidated sheet — Ø callouts and Template mode do not apply to it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // ── Sections ─────────────────────────────────────────────────────────
        // Laid out in `OptionsSection` order (most-used first, Units last) whatever order
        // they are supplied in here — the same list `RunoutWearOptionsSheet` builds from.
        val sliderDiaMm = remember(spec) { heightSliderMaxDiaFor(spec) }
        val sliderBase = schematicHeightSliderBase(sliderDiaMm, curveLoHeightIn, curveHiHeightIn)
        val sections = mutableMapOf<OptionsSection, OptionsSectionBody>()

        // ── Shaft ────────────────────────────────────────────────────────────
        // The live-tuning group the page strip above this sheet exists to keep in view, so it
        // leads and starts open. Shaft height is the same per-job multiplier the runout /
        // consolidated sheets carry (`RunoutConfig.heightScale`) — one value behind every
        // drawing — selected by drawn-height VALUE in paper inches off the default sizing
        // curve at the configured anchor heights, with no width-fit term. Body S-break is the
        // app-wide `PdfPrefs.sBreakThresholdFrac`; the compression pair is the same per-job
        // `RunoutConfig` pair as the Consolidated Output tab.
        sections[OptionsSection.SHAFT] = OptionsSectionBody {
            ShaftHeightSlider(
                heightScale = heightScale,
                baseScale = sliderBase,
                maxDiaMm = sliderDiaMm,
                onCommit = { vm.setRunoutHeightScale(it) },
                onDrag = { tuning.heightScale = it },
            )
            SBreakThresholdSlider(
                frac = sBreakThresholdFrac,
                onCommit = { vm.setPdfSBreakThresholdFrac(it) },
                onDrag = { tuning.sBreakFrac = it },
            )
            LinerCompressionControl(
                linersProportional = linersProportional,
                linerCompression = linerCompression,
                estimateKeptFrac = { frac ->
                    estimatedLinerKeptFracOfTrue(spec, sliderBase, heightScale, frac)
                },
                onSetProportional = { vm.setLinersProportional(it) },
                onSetCompression = { vm.setLinerCompression(it) },
                onDrag = { tuning.linerCompression = it },
            )
        }

        // ── Thread ───────────────────────────────────────────────────────────
        // Only on a shaft that draws a thread: on any other the hatch controls tune nothing.
        if (hasDrawnThreads(spec)) {
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
        sections[OptionsSection.LINES] = OptionsSectionBody(
            summary = linesSectionSummary(lineThicknessScale),
        ) {
            LineThicknessSlider(
                scale = lineThicknessScale,
                onCommit = { vm.setLineThicknessScale(it) },
                onDrag = { tuning.lineThickness = it },
            )
            DimensionArrowSizeChips(
                arrowSizePt = arrowSizePt,
                onCommit = { vm.setPdfArrowSizePt(it) },
            )
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
                shadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
                onSetShadedBodies = { vm.setPdfShadedBodies(it) },
                onSetShadedTapers = { vm.setPdfShadedTapers(it) },
                onSetShadedLiners = { vm.setPdfShadedLiners(it) },
                onSetShadeExplicitBodiesOnly = { vm.setPdfShadeExplicitBodiesOnly(it) },
            )
        }

        // ── Measurement reference ────────────────────────────────────────────
        sections[OptionsSection.MEASUREMENT] = OptionsSectionBody {
            MeasurementReferenceRadios(
                pdfTieringMode = pdfTieringMode,
                onCommit = { vm.setPdfTieringMode(it) },
            )
        }

        // ── Units ────────────────────────────────────────────────────────────
        // The document's own `dual_units` flag plus the app-wide layout it is set in. The
        // layout chips style what the switch turns on, so they read as disabled until it is.
        sections[OptionsSection.UNITS] = OptionsSectionBody(
            summary = unitsSectionSummary(dualUnits, dualUnitLayout),
        ) {
            DualUnitsSwitchRow(dualUnits = dualUnits, onDualUnitsChange = { vm.setDualUnits(it) })
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
