package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutBubblePlan
import com.android.shaftschematic.geom.clockTickRimOffset
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.pickBubbleAt
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_DEFAULT
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import com.android.shaftschematic.util.writeShaftPdfToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@OptIn(ExperimentalTextApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RunoutRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
    /** Quick-save the document (prompts for a name when it has never been saved). */
    onSave: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
    val currentDocumentName by vm.currentDocumentName.collectAsState()
    val hasUnsavedChanges  by vm.hasUnsavedChanges.collectAsState()
    val runoutConfig       by vm.runoutConfig.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val unit               by vm.unit.collectAsState()
    val customer           by vm.customer.collectAsState()
    val vessel             by vm.vessel.collectAsState()
    val jobNumber          by vm.jobNumber.collectAsState()
    val shaftPosition      by vm.shaftPosition.collectAsState()
    val openAfterExport    by vm.openPdfAfterExport.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfShadedBodies    by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers    by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners    by vm.pdfShadedLiners.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    // Fraction style: a chip tap changes the renderer's active style, which the render loop
    // cannot observe, so it rides along as an input key.
    val pdfFractionStyle   by vm.pdfFractionStyle.collectAsState()
    val runoutReadings     by vm.runoutReadings.collectAsState()

    // Which bubble's editor dialog is open, if any (component id + station index + display title).
    var editingBubble by remember { mutableStateOf<EditingRunoutBubble?>(null) }

    val ctx = LocalContext.current
    var showPreview    by rememberSaveable { mutableStateOf(false) }
    // Blank-draft (write-in) copy: geometry + form layout, all values blanked for handwriting.
    var blankDraft     by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap  by remember { mutableStateOf<ImageBitmap?>(null) }
    // Where the composed page carries ink — the tuning strip crops to it so blank paper
    // never takes room from the drawing. Measured on sharp passes only (see the loop).
    var previewInkBand by remember { mutableStateOf<InkBand?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // Live slider drags on the preview's options sheet — visual-only overrides, never a
    // write. See [PreviewTuning].
    val tuning = rememberPreviewTuning()

    // This tab produces the classic standalone runout sheet; the consolidated sheet (and
    // batch export) lives on the Consolidated Output tab. Collisions corrupt any drawing,
    // so the shared export gate guards this surface too.
    val collidingIds = remember(spec) { spec.collidingIds() }
    val gate = remember(spec, collidingIds) { exportPdfGate(spec, collidingIds) }

    val outputFilename = buildOutputFilename(customer, vessel, jobNumber, OutputDoc.RUNOUT, blankDraft)

    /** The runout tab's one document: the classic standalone runout sheet. */
    fun composeClassicRunout(
        page: PdfDocument.Page,
        specSnap: ShaftSpec,
        configSnap: RunoutConfig,
        projectSnap: ProjectInfo,
        unitSnap: UnitSystem,
        prefsSnap: PdfPrefs,
        resolvedSnap: List<ResolvedComponent>,
        thicknessSnap: Float,
        readingsSnap: RunoutReadings,
        blankSnap: Boolean,
    ) = composeRunoutPdf(
        page = page, spec = specSnap, config = configSnap, project = projectSnap,
        unit = unitSnap,
        pdfPrefs = prefsSnap,
        resolvedComponents = resolvedSnap,
        lineThicknessScale = thicknessSnap,
        runoutReadings = readingsSnap,
        blankValues = blankSnap,
        consolidated = false,
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            // Hardened write: a composer throw yields a valid error page, never a
            // truncated file (util/PdfSafExport.kt — one implementation for every tab).
            val wrote = writeShaftPdfToUri(ctx, uri) { page ->
                composeClassicRunout(
                    page, spec, runoutConfig,
                    ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition),
                    unit, vm.currentPdfPrefs, resolvedComponents,
                    lineThicknessScale, runoutReadings, blankDraft,
                )
            }
            if (wrote && openAfterExport) openRunoutPdf(ctx, uri)
        }
    }

    // The render loop. One RenderInputs value carries EVERYTHING the composed sheet reads
    // (an omission here is a stale-preview bug); `snapshotFlow { … }.conflate()` renders
    // the newest inputs and drops the intermediate frames a slider drag produces while a
    // render is in flight. When a drag ends the overrides go null, the inputs change once
    // more, and that final pass rasterizes at full resolution.
    LaunchedEffect(Unit) {
        var previousWasDraft = false
        snapshotFlow {
            RunoutRenderInputs(
                showPreview = showPreview,
                spec = spec,
                config = tunedRunoutConfig(runoutConfig, tuning.heightScale, tuning.linerCompression),
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                resolved = resolvedComponents,
                lineThicknessScale = tuning.lineThickness ?: lineThicknessScale,
                shadedBodies = pdfShadedBodies,
                shadedTapers = pdfShadedTapers,
                shadedLiners = pdfShadedLiners,
                sBreakThresholdFrac = tuning.sBreakFrac ?: pdfSBreakThresholdFrac,
                fractionStyle = pdfFractionStyle,
                readings = runoutReadings,
                blankValues = blankDraft,
                draft = tuning.active,
            )
        }.conflate().collect { inputs ->
            if (!inputs.showPreview) {
                previewBitmap = null
                previousWasDraft = false
                return@collect
            }
            // A drag frame — and the sharp pass right after one — keeps the current page on
            // screen: swapping in the spinner between frames would strobe the preview.
            val quiet = inputs.draft || previousWasDraft
            previousWasDraft = inputs.draft
            if (!quiet) previewLoading = true
            val prefsSnapshot = tunedPdfPrefs(vm.currentPdfPrefs, inputs.sBreakThresholdFrac)
            // The ink band is measured on the raw raster, and only on a sharp (non-draft)
            // pass: a drag frame that resized the strip — and with it the sheet cap —
            // would shuffle the layout under a moving finger.
            val (bmp, band) = withContext(Dispatchers.IO) {
                val raster = renderPdfPageBitmap(
                    ctx,
                    renderScale = previewRenderScale(inputs.draft),
                ) { page ->
                    composeClassicRunout(
                        page, inputs.spec, inputs.config, inputs.project, inputs.unit,
                        prefsSnapshot, inputs.resolved, inputs.lineThicknessScale,
                        inputs.readings, inputs.blankValues,
                    )
                }
                raster to raster?.takeIf { !inputs.draft }?.inkBand()
            }
            previewBitmap = bmp?.asImageBitmap()
            if (!inputs.draft) previewInkBand = band
            previewLoading = false
        }
    }

    // Capture colors before the Canvas block (DrawScope is not composable). Sheet ink is
    // FIXED (SheetInk), never theme colors: the canvas is a paper-white sheet in every
    // theme, and dark theme's near-white onSurface would print invisible ink on it.
    val outlineArgb   = SheetInk.Outline.toArgb()
    val bodyFillArgb  = SheetInk.Outline.copy(alpha = 0.08f).toArgb()
    val hatchArgb     = SheetInk.Outline.copy(alpha = 0.55f).toArgb()
    val previewShape  = MaterialTheme.shapes.medium
    val textMeasurer  = rememberTextMeasurer()

    // Liners take the same tint as bodies/tapers when the pref is on: this canvas carries
    // runouts only, so no sheet-white value halo ever sits over a shaded liner here.
    val transparentArgb = Color.Transparent.toArgb()
    val previewOpts = remember(outlineArgb, bodyFillArgb, hatchArgb,
                               pdfShadedBodies, pdfShadedTapers, pdfShadedLiners) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = if (pdfShadedBodies) bodyFillArgb else transparentArgb,
            taperFillColor      = if (pdfShadedTapers) bodyFillArgb else transparentArgb,
            linerFillColor      = if (pdfShadedLiners) bodyFillArgb else transparentArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
        )
    }

    // Bodies, tapers, liners in axial order for the station count selector — the same rows
    // the Consolidated tab shows (`RunoutStationEditor.kt`, one builder for both).
    val entries: List<RunoutComponentEntry> = remember(spec, resolvedComponents) {
        buildRunoutStationEntries(spec, resolvedComponents)
    }

    // Zoom state for the shaft preview (hoisted so it survives spec updates)
    var previewScale  by remember { mutableFloatStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    val previewTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        previewScale  = (previewScale * zoomChange).coerceIn(0.5f, 5f)
        previewOffset += panChange
    }

    // ── Screen ────────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        // ── Document title strip ─────────────────────────────────────────────
        // Editing station counts or typing a TIR reading here is unsaved work exactly
        // like a spec edit, so the asterisk belongs on this tab too.
        EditorDocumentTitle(
            documentName = currentDocumentName,
            hasUnsavedChanges = hasUnsavedChanges,
        )

        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSidebar) {
                Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
            }
            Text(
                text = "Runout Sheet",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSave,
                modifier = Modifier.testTag("toolbar_save"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
        }

        HorizontalDivider()

        // ── Pinned live preview ───────────────────────────────────────────────
        // Deliberately OUTSIDE the scroll region: the whole point of the bubble-count
        // editor below is watching the profile change, so the preview must stay on
        // screen while the stations are scrolled to (on-device request). Anything added
        // here costs the scroll region height on a phone — keep this block to the
        // preview and its one-line hint.
        // The guard wraps the divider and padding too, so an OAL-less spec leaves no
        // orphan rule or gap above the controls.
        if (spec.overallLengthMm > 0f) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── Live shaft + bubble preview (pinch-to-zoom, tap a bubble to edit) ──
                // Read live inside the (non-restarting) tap pointerInput without re-keying it.
                val scaleForTap  = rememberUpdatedState(previewScale)
                val offsetForTap = rememberUpdatedState(previewOffset)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .transformable(state = previewTransformState)
                        .pointerInput(spec, resolvedComponents, runoutConfig) {
                            detectTapGestures { tap ->
                                val preview = computeRunoutPreview(
                                    size.width.toFloat(), size.height.toFloat(),
                                    spec, resolvedComponents, runoutConfig.componentOverrides,
                                )
                                // Invert the Canvas graphicsLayer transform (scale about centre
                                // pivot, then translate) to map the tap into plan space.
                                val pivotX = size.width / 2f
                                val pivotY = size.height / 2f
                                val sc = scaleForTap.value
                                val lx = (tap.x - offsetForTap.value.x - pivotX) / sc + pivotX
                                val ly = (tap.y - offsetForTap.value.y - pivotY) / sc + pivotY
                                pickBubbleAt(
                                    preview.bubbles, preview.geom.radius, lx, ly,
                                    tolerance = preview.geom.radius * 2f,
                                )?.let { b ->
                                    editingBubble = EditingRunoutBubble(
                                        componentId = b.componentId,
                                        stationIndex = b.stationIndex,
                                        title = runoutBubbleTitle(b, entries),
                                    )
                                }
                            }
                        },
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX       = previewScale,
                                scaleY       = previewScale,
                                translationX = previewOffset.x,
                                translationY = previewOffset.y,
                            ),
                    ) {
                        val preview = computeRunoutPreview(
                            size.width, size.height,
                            spec, resolvedComponents, runoutConfig.componentOverrides,
                        )
                        with(ShaftRenderer) {
                            draw(spec, preview.layout, previewOpts, resolvedComponents)
                        }
                        // Runouts only on this canvas: the tab is the runout authoring
                        // surface, so the profile carries just the bubbles. The wear
                        // marks/worn sections/in-profile values render on the Consolidated
                        // Output tab's preview (the rasterized real PDF).
                        drawRunoutMarkers(preview.bubbles, preview.geom, runoutReadings, unit, textMeasurer)
                    }

                    // Compact reset control (top-right) — mirrors the schematic preview.
                    IconButton(
                        onClick = {
                            previewScale  = 1f
                            previewOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset view",
                            tint = Color.Black.copy(alpha = 0.8f),
                        )
                    }
                }
                Text(
                    text = "Tap a bubble to enter its TIR reading and high spot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
        }

        // ── Scrollable content ────────────────────────────────────────────────
        // Order is deliberate: the document controls (TIR orientation, then the whole
        // export group) sit at the top so producing a sheet needs no scrolling, and the
        // measurement-station editor goes last — it is only reached when the document
        // actually needs adjusting, and it is the one section whose length grows with
        // the shaft (on-device request).
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── TIR orientation selector ──────────────────────────────────────
            Text("TIR orientation", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TirButton("Looking AFT",     runoutConfig.tirDirection == TirDirection.AFT)     { vm.setTirDirection(TirDirection.AFT) }
                TirButton("Looking FORWARD", runoutConfig.tirDirection == TirDirection.FORWARD) { vm.setTirDirection(TirDirection.FORWARD) }
                TirButton("Not set",         runoutConfig.tirDirection == TirDirection.UNSET)   { vm.setTirDirection(TirDirection.UNSET) }
            }

            // ── Blank draft toggle ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info, OAL, and TIR values are blanked for handwriting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Export gate ───────────────────────────────────────────────────
            if (!gate.enabled) {
                Text(
                    gate.disabledMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ── Preview button ────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showPreview = true },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Runout Sheet")
            }

            // ── Print button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val jobName = outputFilename.removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
                    val specSnapshot = spec
                    val configSnapshot = runoutConfig
                    val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition)
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = resolvedComponents
                    val thicknessSnapshot = lineThicknessScale
                    val readingsSnapshot = runoutReadings
                    val blankSnapshot = blankDraft
                    printShaftPdfPage(ctx, jobName) { page ->
                        composeClassicRunout(
                            page, specSnapshot, configSnapshot, projectSnapshot,
                            unitSnapshot, prefsSnapshot, resolvedSnapshot,
                            thicknessSnapshot, readingsSnapshot, blankSnapshot,
                        )
                    }
                },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Runout Sheet")
            }

            // ── Export button ─────────────────────────────────────────────────
            Button(
                onClick = { launcher.launch(outputFilename) },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Runout Sheet PDF")
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()

            // ── Measurement station selector ──────────────────────────────────
            // Last on the page: adjusting bubble counts is the exception, not the
            // routine path, and the pinned preview above shows the effect live.
            RunoutStationCountEditor(
                entries = entries,
                overrides = runoutConfig.componentOverrides,
                onSetCount = { id, count -> vm.setRunoutBubbleCount(id, count) },
            )

            // (Worn-section authoring, the consolidated variant picker, and the "Shaft
            // height" slider live on the Consolidated Output tab — this tab is the runout
            // authoring surface and produces the classic runout sheet.)
        }
    }

    // ── Full-screen preview overlay ───────────────────────────────────────────
    BackHandler(enabled = showPreview) { showPreview = false }
    if (showPreview) {
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Runout Sheet Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(outputFilename)
            },
            optionsSheet = {
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    vm = vm,
                    showSBreak = true,
                    sBreakThresholdFrac = pdfSBreakThresholdFrac,
                    fractionStyle = pdfFractionStyle,
                    tuning = tuning,
                )
            },
            sheetTunesPage = true,
            inkBand = previewInkBand,
        )
    }

    // ── Runout bubble editor dialog ───────────────────────────────────────────
    editingBubble?.let { editing ->
        val existing = runoutReadings.find(editing.componentId, editing.stationIndex)
        RunoutBubbleDialog(
            title = editing.title,
            unit = unit,
            initialValueMm = existing?.valueMm,
            initialHighSpotHalfHours = existing?.highSpotHalfHours,
            onSave = { valueMm, tick ->
                vm.setRunoutReading(editing.componentId, editing.stationIndex, valueMm, tick)
                editingBubble = null
            },
            onDismiss = { editingBubble = null },
        )
    }
}

/**
 * Everything one composed classic-runout preview page depends on, in one
 * structural-equality value — the render loop's unit of work.
 *
 * The shade flags and the S-break threshold never reach the composer from here: they
 * travel inside the `PdfPrefs` snapshot taken at render time. They are held in this holder
 * because the loop must RE-RENDER when they change, and a `PdfPrefs` read is not snapshot
 * state.
 */
private data class RunoutRenderInputs(
    val showPreview: Boolean,
    val spec: ShaftSpec,
    val config: RunoutConfig,
    val project: ProjectInfo,
    val unit: UnitSystem,
    val resolved: List<ResolvedComponent>,
    val lineThicknessScale: Float,
    val shadedBodies: Boolean,
    val shadedTapers: Boolean,
    val shadedLiners: Boolean,
    val sBreakThresholdFrac: Float,
    /** Not a composer argument — it reaches the ink via `FractionTypography.active`. Key only. */
    val fractionStyle: FractionStyle,
    val readings: RunoutReadings,
    val blankValues: Boolean,
    /** A tuning slider is mid-drag: raster at draft resolution and hold the spinner back. */
    val draft: Boolean,
)

/** Identifies the bubble whose editor dialog is open. */
private data class EditingRunoutBubble(
    val componentId: String,
    val stationIndex: Int,
    val title: String,
)

/** Display title for the editor dialog, e.g. "Body 1 · Station 2". */
private fun runoutBubbleTitle(bubble: PlacedRunoutBubble, entries: List<RunoutComponentEntry>): String {
    val label = entries.firstOrNull { it.id == bubble.componentId }?.label ?: "Component"
    return "$label · Station ${bubble.stationIndex + 1}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Local composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TirButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else TextButton(onClick = onClick) { Text(label) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

/** The shaft layout + planned bubbles for the runout preview canvas, computed by [computeRunoutPreview]. */
private class RunoutPreview(
    val layout: ShaftLayout.Result,
    val geom: RunoutBubbleGeometry,
    val bubbles: List<PlacedRunoutBubble>,
)

/**
 * Compute the shaft layout and fully-placed runout bubbles for a canvas of [widthPx]×[heightPx].
 * Uses the SAME shared engine (`geom/RunoutBubbleLayout.kt`) as the PDF, so the preview matches the
 * export. Hoisted out of the Canvas draw lambda so the tap handler and the renderer plan identical
 * geometry from the same inputs. Runs on a [Density] scope (both `DrawScope` and `PointerInputScope`
 * qualify) so it can resolve dp sizes.
 */
private fun Density.computeRunoutPreview(
    widthPx: Float,
    heightPx: Float,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    overrides: Map<String, Int>,
): RunoutPreview {
    val marginPx = 12.dp.toPx()
    val bubbleGeom = RunoutBubbleGeometry(
        radius = 7.dp.toPx(),
        minGap = 5.dp.toPx(),
        shortLeader = 5.dp.toPx(),
        contentLeft = 0f,
        contentRight = widthPx,
    )
    val spans = runoutComponentSpans(resolvedComponents)

    // Reserve vertical space for the planned bubble rows so shaft + bubbles are centred together.
    // First pass assumes the typical two-row layout; re-plan once if the actual row count differs.
    fun planFor(reservedH: Float): Pair<ShaftLayout.Result, RunoutBubblePlan> {
        val layout = ShaftLayout.compute(
            spec = spec, leftPx = 0f, topPx = 0f, rightPx = widthPx,
            bottomPx = heightPx - reservedH, marginPx = marginPx,
            resolvedComponents = resolvedComponents,
        )
        val stations = collectRunoutStations(
            spans, overrides,
            xAtMm = { mm -> layout.xPx(mm) },
            mmAtX = { px -> layout.xMmFromPx(px) },
        )
        return layout to planRunoutBubbles(stations, bubbleGeom)
    }

    val twoRowH = bubbleGeom.shortLeader + 2f * bubbleGeom.radius + bubbleGeom.rowStep
    var (layout, plan) = planFor(twoRowH)
    val neededH = plan.sectionHeight(0f)
    if (abs(neededH - twoRowH) > 0.5f) {
        val replanned = planFor(neededH)
        layout = replanned.first
        plan = replanned.second
    }

    val maxOdMm = runoutMaxOdMm(resolvedComponents)
    val result = plan.finish(
        anchorY = layout.centerlineYPx + layout.rPx(maxOdMm),
        surfaceYAtMm = { mm -> layout.centerlineYPx + layout.rPx(runoutOdMmAt(mm, resolvedComponents)) },
    )
    return RunoutPreview(layout, bubbleGeom, result.bubbles)
}

/** Outer-surface diameter (mm) at an axial station, across the resolved components. */
private fun runoutOdMmAt(stMm: Float, components: List<ResolvedComponent>): Float {
    var od = 10f
    components.forEach { rc ->
        val inRange = stMm >= rc.startMmPhysical - 0.1f && stMm <= rc.endMmPhysical + 0.1f
        if (!inRange) return@forEach
        when (rc) {
            is ResolvedBody  -> od = maxOf(od, rc.diaMm)
            is ResolvedTaper -> {
                val len  = rc.endMmPhysical - rc.startMmPhysical
                val frac = if (len > 0f) ((stMm - rc.startMmPhysical) / len).coerceIn(0f, 1f) else 0f
                od = maxOf(od, rc.startDiaMm + (rc.endDiaMm - rc.startDiaMm) * frac)
            }
            is ResolvedLiner -> od = maxOf(od, rc.odMm)
            else -> {}
        }
    }
    return od
}

/** Largest outer diameter (mm) across the resolved components (min 10mm). */
private fun runoutMaxOdMm(components: List<ResolvedComponent>): Float =
    components.maxOfOrNull { rc ->
        when (rc) {
            is ResolvedBody  -> rc.diaMm
            is ResolvedTaper -> maxOf(rc.startDiaMm, rc.endDiaMm)
            is ResolvedLiner -> rc.odMm
            else -> 0f
        }
    }?.coerceAtLeast(10f) ?: 10f

/**
 * Draw the planned runout bubbles: leader polylines, the circle with a keyway cutout at 12 o'clock,
 * and — when recorded — the TIR value (centred) and the high-spot marker (a short dash straddling
 * the rim at the clock position). The
 * keyway cutout and marker geometry mirror the PDF (`RunoutPdfComposer.drawPlacedBubbles`) so the
 * preview matches the export exactly.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRunoutMarkers(
    bubbles: List<PlacedRunoutBubble>,
    geom: RunoutBubbleGeometry,
    readings: RunoutReadings,
    unit: com.android.shaftschematic.util.UnitSystem,
    textMeasurer: TextMeasurer,
) {
    val strokeW     = 1.2.dp.toPx()
    val markerColor = Color.Black.copy(alpha = 0.70f)
    val highSpotColor = Color(0xFFC62828) // red — the high spot, per shop convention
    val r = geom.radius

    bubbles.forEach { b ->
        val center = Offset(b.bubbleX, b.bubbleCenterY)
        b.leader.zipWithNext { p, q ->
            drawLine(markerColor, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = strokeW)
        }
        drawRunoutBubbleRing(center, r, markerColor, strokeW)

        val reading = readings.find(b.componentId, b.stationIndex)
        // TIR value, centred in the circle.
        reading?.valueMm?.let { valueMm ->
            val txt = com.android.shaftschematic.util.formatRunoutValue(valueMm, unit)
            val style = androidx.compose.ui.text.TextStyle(
                color = markerColor,
                // Small value inside the (larger) circle — lockstep with the PDF bubble.
                fontSize = with(this) { (r * 0.60f).toSp() },
            )
            val measured = textMeasurer.measure(txt, style)
            drawText(
                textMeasurer = textMeasurer, text = txt, style = style,
                topLeft = Offset(
                    b.bubbleX - measured.size.width / 2f,
                    b.bubbleCenterY - measured.size.height / 2f,
                ),
            )
        }
        // High-spot marker: a short dash straddling the rim at the clock position (no radial
        // line — it would crowd the centred value). Matches the hand-drawn shop convention.
        reading?.highSpotHalfHours?.let { tick ->
            val (ux, uy) = clockTickRimOffset(tick, 1f) // unit outward direction
            val inner = r * 0.70f
            val outer = r * 1.30f
            drawLine(
                highSpotColor,
                Offset(b.bubbleX + ux * inner, b.bubbleCenterY + uy * inner),
                Offset(b.bubbleX + ux * outer, b.bubbleCenterY + uy * outer),
                strokeWidth = strokeW * 1.7f,
            )
        }
    }
}

/**
 * Draw a runout bubble ring with a keyway cutout at 12 o'clock: the top arc is broken across the
 * slot mouth and an open-topped slot descends into the circle (matches the shop's key-at-top
 * convention). Shared geometry with the PDF renderer.
 */
private fun DrawScope.drawRunoutBubbleRing(center: Offset, r: Float, color: Color, strokeW: Float) {
    val stroke = Stroke(width = strokeW)
    // Slot half-width and the angular gap it subtends at the rim (measured from 12 o'clock).
    val slotHalf = r * 0.22f
    val slotDepth = r * 0.42f
    val gapDeg = Math.toDegrees(kotlin.math.asin((slotHalf / r).coerceIn(0f, 1f).toDouble())).toFloat()

    // Arc everywhere except the gap at the top (top = -90° in the drawArc convention).
    drawArc(
        color = color,
        startAngle = -90f + gapDeg,
        sweepAngle = 360f - 2f * gapDeg,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(2f * r, 2f * r),
        style = stroke,
    )
    // Slot: two verticals descending from the gap edges + a bottom connector.
    val leftX = center.x - slotHalf
    val rightX = center.x + slotHalf
    val topY = center.y - r * cos(Math.toRadians(gapDeg.toDouble())).toFloat()
    val botY = topY + slotDepth
    drawLine(color, Offset(leftX, topY), Offset(leftX, botY), strokeWidth = strokeW)
    drawLine(color, Offset(rightX, topY), Offset(rightX, botY), strokeWidth = strokeW)
    drawLine(color, Offset(leftX, botY), Offset(rightX, botY), strokeWidth = strokeW)
}

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
// Shared PDF preview overlay (also used by WearRoute)
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
 *                      Wear and undercut leave it false: their sheets tune nothing.
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
        // The wear and undercut sheets tune nothing: no strip to stay clear of, just the
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
// Shared PDF options sheet (Runout + Wear)
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
     * Shows the shared "Body S-break" threshold slider. On for the runout and consolidated
     * sheets, which draw compression breaks; off for the wear and undercut documents, whose
     * profiles never break, so the control would be inert noise there.
     */
    showSBreak: Boolean = false,
    /** The app-wide `PdfPrefs.sBreakThresholdFrac`; read only when [showSBreak]. */
    sBreakThresholdFrac: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
    /**
     * Shows the wear-document tuning block: the "Trace depth exaggeration" row (the Wear tab's
     * own control, shared construction) and the "Wear area shade" slider. On only for the wear
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
     * the consolidated sheet, whose preview these tune live; off elsewhere, where the pair
     * has nothing to act on.
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
        // The same two controls the Wear tab and Settings → Drawing carry — here so the
        // trace depth and the band's grey can be judged against the sheet they print on.
        // Commit-on-release, like every slider on this sheet; the wear preview re-renders
        // from its own keys rather than a live tuning channel.
        if (showWearControls) {
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

        // ── Shaft height / Liner compression ────────────────────────────────
        // Same per-job pair as the Consolidated Output tab (`RunoutConfig`).
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

        Spacer(Modifier.height(24.dp))
    }
}
