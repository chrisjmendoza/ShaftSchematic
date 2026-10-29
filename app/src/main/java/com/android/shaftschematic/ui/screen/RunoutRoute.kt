package com.android.shaftschematic.ui.screen

import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.BubbleKeywayNotch
import com.android.shaftschematic.geom.bubbleKeywayNotch
import com.android.shaftschematic.geom.sheetKeywayProportion
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutBubblePlan
import com.android.shaftschematic.geom.RunoutStationX
import com.android.shaftschematic.geom.clampDraggedStationMm
import com.android.shaftschematic.geom.clockTickRimOffset
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.localStationPositions
import com.android.shaftschematic.geom.pickBubbleAt
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.geom.runoutComponentOriginMm
import com.android.shaftschematic.geom.runoutComponentSpanMm
import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.geom.clockTickLabel
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.pdf.runoutSheetMaxShaftHeightPt
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.adaptive.LocalSidebarPermanent
import com.android.shaftschematic.ui.adaptive.WindowWidthClass
import com.android.shaftschematic.ui.adaptive.currentWindowWidthClass
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.componentAxialExtentMm
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.formatRunoutValue
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.OutputFont
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.launchPicker
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import com.android.shaftschematic.util.writeShaftPdfToUri
import kotlinx.coroutines.CancellationException
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
    /** Tap on the document title strip — names an unsaved document, renames a saved one. */
    onTitleClick: (() -> Unit)? = null,
    /** Open Help at one topic — the toolbar's "?" opens this tab's own guide. */
    onOpenHelpTopic: (String) -> Unit = {},
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
    val item               by vm.item.collectAsState()
    val shaftPosition      by vm.shaftPosition.collectAsState()
    val openAfterExport    by vm.openPdfAfterExport.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfShadedBodies    by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers    by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners    by vm.pdfShadedLiners.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    val pdfShadeExplicitBodiesOnly by vm.pdfShadeExplicitBodiesOnly.collectAsState()
    // Bubble size and drop: canvas inputs AND render-loop keys. The canvas below plans its own
    // geometry from them; the composed sheet reads them off the `PdfPrefs` snapshot, which the
    // loop cannot observe.
    val pdfRunoutBubbleScale by vm.pdfRunoutBubbleScale.collectAsState()
    val pdfRunoutBubbleDropScale by vm.pdfRunoutBubbleDropScale.collectAsState()
    // Fraction style: a chip tap changes the renderer's active style, which the render loop
    // cannot observe, so it rides along as an input key.
    val pdfFractionStyle   by vm.pdfFractionStyle.collectAsState()
    val pdfOutputFont      by vm.pdfOutputFont.collectAsState()
    val pdfThreadSlant     by vm.pdfThreadSlant.collectAsState()
    val pdfThreadDensity   by vm.pdfThreadDensity.collectAsState()
    val pdfDualUnitLayout  by vm.pdfDualUnitLayout.collectAsState()
    // Sizing-curve anchors: the composer sizes the drawn shaft off them and the preview's
    // "Shaft height" slider states its track in paper inches from the same pair, so a
    // Settings change to "Default drawing size" has to reach both.
    val curveLoHeightIn    by vm.pdfCurveLoHeightIn.collectAsState()
    val curveHiHeightIn    by vm.pdfCurveHiHeightIn.collectAsState()
    val runoutReadings     by vm.runoutReadings.collectAsState()
    val stationPlacements  by vm.runoutStationPlacements.collectAsState()
    // Per-component display units + inline-dual flag: same posture as `pdfFractionStyle` —
    // neither reaches the composer through a field already collected above, so both ride
    // along as explicit render-loop inputs.
    val unitOverrides      by vm.unitOverrides.collectAsState()
    val dualUnits          by vm.dualUnits.collectAsState()

    // Which bubble's editor dialog is open, if any (component id + station index + display title).
    // Saveable, like the dialog's own value and high spot, so a rotation keeps the edit open.
    var editingBubble by rememberSaveable(stateSaver = EditingRunoutBubbleSaver) {
        mutableStateOf<EditingRunoutBubble?>(null)
    }

    // The bubble currently under a finger, if any. Purely visual state: the drag re-plans the
    // canvas from these in-progress positions, and the ViewModel write happens once on
    // finger-up (the commit-on-release rule the tuning sliders already follow — a per-frame
    // write would raise the unsaved-changes asterisk instantly and fill the undo history).
    var draggingStation by remember { mutableStateOf<DraggingRunoutStation?>(null) }
    // A pickup and a tap share one press: without this the finger-up that ends a drag would
    // also read as a tap and open the reading dialog on the bubble just moved. Set when a
    // drag picks a bubble up (long press, or Move mode's instant pickup), cleared by the
    // next press.
    var suppressBubbleTap by remember { mutableStateOf(false) }
    // One-shot undo for the most recent committed drag ("Undo move" beside the canvas hint).
    // Session-scoped screen state, not persistence: it holds what the moved station's stored
    // pin was BEFORE the drag — null meaning it was derived, so undoing a first drag un-pins
    // it back to automatic placement instead of freezing it at its old derived spot. Replaced
    // by the next drag, cleared when used or when a reset makes it moot. The session Undo
    // button also covers a drag (placements are in EditState); this chip is the zero-thought
    // path for "I nudged that by accident".
    var lastBubbleMove by remember { mutableStateOf<LastBubbleMove?>(null) }
    // Move mode (the canvas's corner toggle): a tap selects instead of opening the reading
    // dialog, a bubble drags the instant a finger lands on it, and −/+ under the canvas act
    // on the selected component (on-device request: press-and-hold was not discoverable and
    // slowed down placing several bubbles). OFF is the plain authoring canvas, unchanged.
    var moveMode by rememberSaveable { mutableStateOf(false) }
    // What Move mode has selected. Screen state only — cleared when the mode turns off, and
    // read through `liveMoveSelection` so a component that stopped resolving reads as no
    // selection without being cleared eagerly.
    var moveSelection by remember { mutableStateOf<RunoutMoveSelection?>(null) }

    // What the canvas draws with: stored placements, plus the one station under the finger.
    // Only the dragged station is overlaid — its siblings stay derived, exactly as they will
    // once the drag commits.
    val livePlacements = draggingStation?.let {
        stationPlacements.withPosition(
            it.componentId, it.stationIndex, it.positionsMm[it.stationIndex],
        )
    } ?: stationPlacements
    val haptics = LocalHapticFeedback.current

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
    // The bubbles' keyway notch proportion — the shaft's own keyway, once per spec, as the PDF.
    val bubbleKeywayProportion = remember(spec) { sheetKeywayProportion(spec) }

    val outputFilename =
        buildOutputFilename(customer, vessel, jobNumber, shaftPosition, OutputDoc.RUNOUT, blankDraft, currentDocumentName)

    // "Shaft height" / "Liner compression" slider inputs. The classic sheet honors both
    // (`config.heightScale`, `config.linerMinFracOfTrue`) exactly as the consolidated one
    // does — one composer — so its preview offers the same pair, off the same shared base.
    val heightSliderDiaMm = remember(spec) { heightSliderMaxDiaFor(spec) }
    val heightSliderBase = remember(spec, heightSliderDiaMm, curveLoHeightIn, curveHiHeightIn) {
        runoutHeightSliderBase(spec, heightSliderDiaMm, curveLoHeightIn, curveHiHeightIn)
    }
    // Filled by the preview's render loop — the sheet's own answer, `Float.MAX_VALUE` until then.
    var heightCapPt by remember { mutableFloatStateOf(Float.MAX_VALUE) }

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
        placementsSnap: RunoutStationPlacements,
        blankSnap: Boolean,
        displayUnitsSnap: DisplayUnits,
    ) = composeRunoutPdf(
        page = page, spec = specSnap, config = configSnap, project = projectSnap,
        unit = unitSnap,
        displayUnits = displayUnitsSnap,
        pdfPrefs = prefsSnap,
        resolvedComponents = resolvedSnap,
        lineThicknessScale = thicknessSnap,
        runoutReadings = readingsSnap,
        runoutStationPlacements = placementsSnap,
        blankValues = blankSnap,
        consolidated = false,
    )

    /**
     * Sends the classic runout sheet to the platform print dialog. ONE action behind the tab
     * body's Print button and the preview overlay's Print icon, so the two entry points
     * cannot drift. Every value is snapshotted here on the UI thread — `onWrite` runs on a
     * binder thread.
     */
    fun printClassicRunout() {
        val jobName = outputFilename.removeSuffix(".pdf")
        val specSnapshot = spec
        val configSnapshot = runoutConfig
        val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
            jobNumber = jobNumber, side = shaftPosition, item = item)
        val unitSnapshot = unit
        val prefsSnapshot = vm.currentPdfPrefs
        val resolvedSnapshot = resolvedComponents
        val thicknessSnapshot = lineThicknessScale
        val readingsSnapshot = runoutReadings
        val placementsSnapshot = stationPlacements
        val blankSnapshot = blankDraft
        val displayUnitsSnapshot = vm.currentDisplayUnits()
        printShaftPdfPage(ctx, jobName) { page ->
            composeClassicRunout(
                page, specSnapshot, configSnapshot, projectSnapshot,
                unitSnapshot, prefsSnapshot, resolvedSnapshot,
                thicknessSnapshot, readingsSnapshot, placementsSnapshot,
                blankSnapshot, displayUnitsSnapshot,
            )
        }
    }

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
                        jobNumber = jobNumber, side = shaftPosition, item = item),
                    unit, vm.currentPdfPrefs, resolvedComponents,
                    lineThicknessScale, runoutReadings, stationPlacements, blankDraft,
                    vm.currentDisplayUnits(),
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
                    jobNumber = jobNumber, side = shaftPosition, item = item),
                unit = unit,
                resolved = resolvedComponents,
                lineThicknessScale = tuning.lineThickness ?: lineThicknessScale,
                shadedBodies = pdfShadedBodies,
                shadedTapers = pdfShadedTapers,
                shadedLiners = pdfShadedLiners,
                sBreakThresholdFrac = tuning.sBreakFrac ?: pdfSBreakThresholdFrac,
                shadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
                runoutBubbleScale = pdfRunoutBubbleScale,
                runoutBubbleDropScale = pdfRunoutBubbleDropScale,
                fractionStyle = pdfFractionStyle,
                outputFont = pdfOutputFont,
                threadSlant = pdfThreadSlant,
                threadDensity = pdfThreadDensity,
                dualUnitLayout = pdfDualUnitLayout,
                curveLoHeightIn = curveLoHeightIn,
                curveHiHeightIn = curveHiHeightIn,
                readings = runoutReadings,
                stationPlacements = stationPlacements,
                blankValues = blankDraft,
                displayUnits = DisplayUnits(unit, unitOverrides, dualUnits),
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
                        inputs.readings, inputs.stationPlacements, inputs.blankValues,
                        inputs.displayUnits,
                    )
                }
                raster to raster?.takeIf { !inputs.draft }?.inkBand()
            }
            previewBitmap = bmp?.asImageBitmap()
            if (!inputs.draft) previewInkBand = band
            previewLoading = false
            // The tallest this sheet draws the shaft, for the preview's height slider — re-solved
            // on sharp passes only (a drag frame keeps the last answer).
            if (!inputs.draft) {
                heightCapPt = withContext(Dispatchers.Default) {
                    runoutSheetMaxShaftHeightPt(
                        spec = inputs.spec, config = inputs.config, project = inputs.project,
                        unit = inputs.unit, displayUnits = inputs.displayUnits, pdfPrefs = prefsSnapshot,
                        resolvedComponents = inputs.resolved,
                        lineThicknessScale = inputs.lineThicknessScale,
                        runoutReadings = inputs.readings,
                        runoutStationPlacements = inputs.stationPlacements,
                        blankValues = inputs.blankValues,
                        consolidated = false,
                    )
                }
            }
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
                               pdfShadedBodies, pdfShadedTapers, pdfShadedLiners, pdfThreadSlant,
                               pdfThreadDensity) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = if (pdfShadedBodies) bodyFillArgb else transparentArgb,
            taperFillColor      = if (pdfShadedTapers) bodyFillArgb else transparentArgb,
            linerFillColor      = if (pdfShadedLiners) bodyFillArgb else transparentArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
            threadSlant         = pdfThreadSlant,
            threadDensity       = pdfThreadDensity,
        )
    }

    // Bodies, tapers, liners in axial order for the station count selector — the same rows
    // the Consolidated tab shows (`RunoutStationEditor.kt`, one builder for both).
    val entries: List<RunoutComponentEntry> = remember(spec, resolvedComponents) {
        buildRunoutStationEntries(spec, resolvedComponents)
    }

    // The Move-mode selection as it applies to the drawing on screen: null while the mode is
    // off, while nothing is chosen, or once the chosen component no longer resolves. Counts
    // come from the station editor's own derivation, so a station past the current count
    // falls back to its component.
    val liveSelection: RunoutMoveSelection? = if (!moveMode) null else liveMoveSelection(
        moveSelection,
        entries.associate { it.id to stationCountFor(it, runoutConfig.componentOverrides) },
    )

    // Zoom state for the shaft preview (hoisted so it survives spec updates)
    var previewScale  by remember { mutableFloatStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    val previewTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        previewScale  = (previewScale * zoomChange).coerceIn(0.5f, 5f)
        previewOffset += panChange
    }

    // ── Window width ──────────────────────────────────────────────────────────
    // One skeleton decision for the whole tab; every block below is composed once and
    // called from both branches, so a phone and a tablet cannot drift apart.
    val widthClass = currentWindowWidthClass()
    val canvasModifier = sheetCanvasModifier(widthClass)

    // ── The drawing, with its gestures and its one-line hint ──────────────────
    // Pinned out of the scroll region at every width: the whole point of the bubble-count
    // editor is watching the profile change, so the preview must stay on screen while the
    // stations are reached (on-device request). Keep this block to the preview and its hint —
    // anything added here costs the scroll region height on a phone.
    val previewBlock: @Composable ColumnScope.() -> Unit = {
        // Pinch to zoom, tap a bubble to edit its reading, press-and-hold one to drag
        // it along its component. In Move mode a tap selects and a bubble drags at once.
        //
        // Read live inside the (non-restarting) gesture pointerInputs without
        // re-keying them: a re-key mid-gesture would abandon the drag.
        val scaleForTap  = rememberUpdatedState(previewScale)
        val offsetForTap = rememberUpdatedState(previewOffset)
        val placementsForDrag = rememberUpdatedState(stationPlacements)
        // Same reason: the gesture handlers re-plan the bubble field to hit-test it, and
        // a resize must reach that plan without re-keying the gesture.
        val bubbleScaleForTap = rememberUpdatedState(pdfRunoutBubbleScale)
        val bubbleDropForTap = rememberUpdatedState(pdfRunoutBubbleDropScale)
        // Station count only — the closures resolve mm↔px for the derived-body
        // even-spread rule, which pixel scale never changes the COUNT of, so an
        // identity mapping is enough here without hoisting the canvas's real layout
        // out of its draw scope.
        val previewStationCount = remember(resolvedComponents, runoutConfig, livePlacements) {
            collectRunoutStations(
                runoutComponentSpans(resolvedComponents),
                runoutConfig.componentOverrides,
                xAtMm = { it },
                mmAtX = { it },
                placements = livePlacements,
            ).size
        }

        // The drag's phases, shared by the press-and-hold detector and Move mode's instant
        // pickup — ONE body per phase, so the two gesture paths cannot drift. The pickup and
        // the follow run on the gesture's PointerInputScope for its size and density.

        /**
         * Picks up the bubble under [raw], if any, and starts dragging it; returns the bubble
         * when a drag started. [preferSelected] lets Move mode's selected station win a press
         * that lands within reach of it — without it the nearest bubble wins, as a tap decides.
         */
        fun PointerInputScope.beginStationDrag(
            raw: Offset,
            preferSelected: Boolean,
            haptic: HapticFeedbackType,
        ): PlacedRunoutBubble? {
            val preview = computeRunoutPreview(
                size.width.toFloat(), size.height.toFloat(),
                spec, resolvedComponents,
                runoutConfig.componentOverrides, placementsForDrag.value,
                bubbleScaleForTap.value, bubbleDropForTap.value,
            )
            val p = toPlanSpace(
                raw, size.width.toFloat(), size.height.toFloat(),
                scaleForTap.value, offsetForTap.value,
            )
            val hit = pickBubbleForMove(
                preview.bubbles, preview.geom.radius, p.x, p.y,
                tolerance = preview.geom.radius * 2f,
                selected = if (preferSelected) moveSelection else null,
            ) ?: return null
            // The full current set is only the CLAMP fence — the neighbours the drag may not
            // cross. Nothing but the station under the finger gets stored; its siblings stay
            // derived and keep behaving automatically.
            val positions = currentStationPositions(
                hit.componentId, preview.bubbles,
                resolvedComponents, placementsForDrag.value,
            )
            if (hit.stationIndex !in positions.indices) return null
            suppressBubbleTap = true
            haptics.performHapticFeedback(haptic)
            draggingStation = DraggingRunoutStation(
                componentId = hit.componentId,
                stationIndex = hit.stationIndex,
                positionsMm = positions,
                originalPositionsMm = positions,
                previousPinMm = placementsForDrag.value
                    .position(hit.componentId, hit.stationIndex),
            )
            return hit
        }

        /** Follows the finger with the station being dragged, if any. */
        fun PointerInputScope.continueStationDrag(change: PointerInputChange) {
            val drag = draggingStation ?: return
            change.consume()
            // Plan against the in-progress set itself rather than a recomposition-lagged
            // copy, so the mm the finger maps to is read off the layout actually on screen.
            val preview = computeRunoutPreview(
                size.width.toFloat(), size.height.toFloat(),
                spec, resolvedComponents, runoutConfig.componentOverrides,
                placementsForDrag.value.withPosition(
                    drag.componentId, drag.stationIndex,
                    drag.positionsMm[drag.stationIndex],
                ),
                bubbleScaleForTap.value, bubbleDropForTap.value,
            )
            val p = toPlanSpace(
                change.position, size.width.toFloat(), size.height.toFloat(),
                scaleForTap.value, offsetForTap.value,
            )
            val runs = runoutComponentSpans(resolvedComponents)
                .filter { it.id == drag.componentId }
            // Follow the finger in mm, then clamp inside the component and off its
            // neighbours — station order never changes, so a typed TIR always stays on its
            // own bubble.
            val targetLocalMm = preview.layout.xMmFromPx(p.x) -
                runoutComponentOriginMm(runs)
            val clamped = clampDraggedStationMm(
                positionsMm = drag.positionsMm,
                index = drag.stationIndex,
                targetMm = targetLocalMm,
                spanMm = runoutComponentSpanMm(runs),
            )
            draggingStation = drag.copy(
                positionsMm = drag.positionsMm.toMutableList()
                    .also { it[drag.stationIndex] = clamped },
            )
        }

        /**
         * The one write of the whole gesture — see the commit-on-release note on
         * `draggingStation`. A pickup that never moved commits nothing at all, and only the
         * station that moved is pinned.
         */
        fun endStationDrag() {
            draggingStation?.let {
                if (it.positionsMm != it.originalPositionsMm) {
                    vm.setRunoutStationPosition(
                        it.componentId, it.stationIndex,
                        it.positionsMm[it.stationIndex],
                    )
                    lastBubbleMove = LastBubbleMove(
                        componentId = it.componentId,
                        stationIndex = it.stationIndex,
                        previousMm = it.previousPinMm,
                        countAtMove = entries.firstOrNull { e -> e.id == it.componentId }
                            ?.let { e -> stationCountFor(e, runoutConfig.componentOverrides) },
                    )
                }
            }
            draggingStation = null
        }

        fun cancelStationDrag() {
            draggingStation = null
        }

        Box(
            modifier = canvasModifier
                .clip(previewShape)
                .background(Color.White)
                .semantics {
                    contentDescription = SheetSemantics.runoutPreview(
                        stationCount = previewStationCount,
                        readingCount = runoutReadings.readings.size,
                        moveMode = moveMode,
                    )
                }
                .transformable(state = previewTransformState)
                .pointerInput(spec, resolvedComponents, runoutConfig) {
                    detectTapGestures(
                        // A fresh press: whatever the previous gesture was, this one
                        // starts as a candidate tap again.
                        onPress = { suppressBubbleTap = false },
                        onTap = { tap ->
                            if (suppressBubbleTap) return@detectTapGestures
                            val preview = computeRunoutPreview(
                                size.width.toFloat(), size.height.toFloat(),
                                spec, resolvedComponents,
                                runoutConfig.componentOverrides, placementsForDrag.value,
                                bubbleScaleForTap.value, bubbleDropForTap.value,
                            )
                            val p = toPlanSpace(
                                tap, size.width.toFloat(), size.height.toFloat(),
                                scaleForTap.value, offsetForTap.value,
                            )
                            if (moveMode) {
                                // Move mode selects — a bubble, else the component run
                                // under the tap, else nothing — and never opens the
                                // reading dialog.
                                val hit = pickBubbleForMove(
                                    preview.bubbles, preview.geom.radius, p.x, p.y,
                                    tolerance = preview.geom.radius * 2f,
                                    selected = moveSelection,
                                )
                                moveSelection = moveModeSelectionForTap(
                                    hit,
                                    if (hit == null) runoutComponentAt(preview, resolvedComponents, p) else null,
                                )
                                if (hit != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                return@detectTapGestures
                            }
                            pickBubbleAt(
                                preview.bubbles, preview.geom.radius, p.x, p.y,
                                tolerance = preview.geom.radius * 2f,
                            )?.let { b ->
                                editingBubble = EditingRunoutBubble(
                                    componentId = b.componentId,
                                    stationIndex = b.stationIndex,
                                    title = runoutBubbleTitle(b, entries),
                                )
                            }
                        },
                    )
                }
                // Declared after the tap detector so it takes the main pass first and
                // its consumed changes keep `transformable` from panning the canvas
                // out from under the finger.
                .pointerInput(spec, resolvedComponents, runoutConfig) {
                    // Whether this detector's own long press started the drag in progress.
                    // In Move mode the instant pickup below owns every bubble press; a long
                    // press that fires under it must neither restart nor end that drag.
                    var ownsDrag = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { raw ->
                            ownsDrag = !moveMode && beginStationDrag(
                                raw, preferSelected = false,
                                haptic = HapticFeedbackType.LongPress,
                            ) != null
                        },
                        onDrag = { change, _ -> if (ownsDrag) continueStationDrag(change) },
                        onDragEnd = {
                            if (ownsDrag) endStationDrag()
                            ownsDrag = false
                        },
                        onDragCancel = {
                            if (ownsDrag) cancelStationDrag()
                            ownsDrag = false
                        },
                    )
                }
                // Move mode's instant pickup: no long press. Declared after the long-press
                // detector so it takes the main pass first. A press on a bubble is consumed
                // here and drags at once; a press on bare paper is left unconsumed, so the
                // canvas still pans and zooms from it.
                .then(
                    if (moveMode) {
                        Modifier.pointerInput(spec, resolvedComponents, runoutConfig) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // A corner button already took this press.
                                if (down.isConsumed) return@awaitEachGesture
                                val hit = beginStationDrag(
                                    down.position, preferSelected = true,
                                    haptic = HapticFeedbackType.TextHandleMove,
                                ) ?: return@awaitEachGesture
                                down.consume()
                                moveSelection = RunoutMoveSelection(hit.componentId, hit.stationIndex)
                                val completed = try {
                                    drag(down.id) { change ->
                                        change.consume()
                                        continueStationDrag(change)
                                    }
                                } catch (e: CancellationException) {
                                    cancelStationDrag()
                                    throw e
                                }
                                if (completed) endStationDrag() else cancelStationDrag()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
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
                    livePlacements,
                    pdfRunoutBubbleScale, pdfRunoutBubbleDropScale,
                )
                with(ShaftRenderer) {
                    draw(spec, preview.layout, previewOpts, resolvedComponents)
                }
                // Runouts only on this canvas: the tab is the runout authoring
                // surface, so the profile carries just the bubbles. The wear
                // marks/worn sections/in-profile values render on the Consolidated
                // Output tab's preview (the rasterized real PDF).
                liveSelection?.takeIf { it.stationIndex == null }?.let { sel ->
                    drawRunoutComponentSelection(preview.layout, sel.componentId, resolvedComponents)
                }
                drawRunoutMarkers(
                    preview.bubbles, preview.geom, runoutReadings, unit, textMeasurer,
                    keywayProportion = bubbleKeywayProportion,
                    dragging = draggingStation,
                    selected = liveSelection,
                )
            }

            // Corner controls (top-right). Chrome over the sheet, not sheet ink, so the
            // toggle's ON state may take the theme's primary.
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                // Move mode toggle — tinted while on, so the changed tap/drag behaviour is
                // never a hidden mode.
                IconToggleButton(
                    checked = moveMode,
                    onCheckedChange = { on ->
                        moveMode = on
                        if (!on) moveSelection = null
                    },
                    modifier = Modifier
                        .padding(6.dp)
                        .size(40.dp)
                        .background(
                            if (moveMode) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.6f),
                            CircleShape,
                        )
                        .testTag("runout_move_mode"),
                ) {
                    Icon(
                        imageVector = if (moveMode) Icons.Filled.Edit else Icons.Outlined.Edit,
                        contentDescription = if (moveMode) "Done moving bubbles" else "Move bubbles",
                        tint = if (moveMode) MaterialTheme.colorScheme.onPrimary
                            else Color.Black.copy(alpha = 0.8f),
                    )
                }
                // Compact reset control — mirrors the schematic preview.
                IconButton(
                    onClick = {
                        previewScale  = 1f
                        previewOffset = Offset.Zero
                    },
                    modifier = Modifier
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
        }
        // −/+ on the selection, only while Move mode is on. Same count and same − rule as
        // the station editor's rows, and the same ViewModel calls; after either the component
        // stays selected but its station drops, because the change renumbers its stations —
        // which is also why either one retires that component's "Undo move": the stored index
        // may now name a different bubble (the session Undo covers −/+).
        if (moveMode) {
            val selectedEntry = liveSelection?.let { sel ->
                entries.firstOrNull { it.id == sel.componentId }
            }
            val selectedCount = selectedEntry?.let {
                stationCountFor(it, runoutConfig.componentOverrides)
            }
            RunoutMoveToolbar(
                selectionLabel = moveSelectionLabel(liveSelection, entries),
                hasSelection = selectedEntry != null,
                canRemove = selectedCount != null && canRemoveStation(selectedCount),
                onRemove = {
                    if (selectedEntry != null && selectedCount != null) {
                        vm.removeRunoutStation(selectedEntry.id, selectedCount)
                        moveSelection = selectionAfterCountChange(moveSelection)
                        lastBubbleMove = lastBubbleMove.retiredBy(selectedEntry.id)
                    }
                },
                onAdd = {
                    if (selectedEntry != null && selectedCount != null) {
                        vm.addRunoutStation(selectedEntry.id, selectedCount)
                        moveSelection = selectionAfterCountChange(moveSelection)
                        lastBubbleMove = lastBubbleMove.retiredBy(selectedEntry.id)
                    }
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (moveMode) {
                    "Move mode: tap a bubble or a component to select it, drag a bubble to " +
                        "move it. Use − / + to remove or add a station on the selected component."
                } else {
                    "Tap a bubble to enter its TIR reading and high spot. " +
                        "Press and hold one to drag it along its component."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Appears only after a committed drag: one tap puts the moved bubble back
            // where it was (a first drag undoes to derived, un-pinning just that
            // station — no other bubble is touched). RETIRED once the component's station
            // count differs from the one the drag was made at (a −/+ here, or a session
            // Undo/Redo of one): the stored index may then name a different bubble, and a
            // later Redo that brings the count back must not revive a chip whose drag may
            // itself have been undone since — so the state is cleared, not merely filtered.
            val moveCount = lastBubbleMove?.let { move ->
                entries.firstOrNull { it.id == move.componentId }
                    ?.let { stationCountFor(it, runoutConfig.componentOverrides) }
            }
            LaunchedEffect(lastBubbleMove, moveCount) {
                val move = lastBubbleMove ?: return@LaunchedEffect
                if (!undoMoveStillApplies(move.countAtMove, moveCount)) lastBubbleMove = null
            }
            lastBubbleMove?.takeIf { move ->
                undoMoveStillApplies(move.countAtMove, moveCount)
            }?.let { move ->
                TextButton(
                    onClick = {
                        if (move.previousMm == null) {
                            vm.clearRunoutStationPosition(
                                move.componentId, move.stationIndex,
                            )
                        } else {
                            vm.setRunoutStationPosition(
                                move.componentId, move.stationIndex, move.previousMm,
                            )
                        }
                        lastBubbleMove = null
                    },
                    modifier = Modifier.testTag("runout_undo_move"),
                ) {
                    Text("Undo move")
                }
            }
        }
    }

    // ── TIR orientation selector ──────────────────────────────────────────────
    val tirSection: @Composable ColumnScope.() -> Unit = {
        Text("TIR orientation", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TirButton("Looking AFT",     runoutConfig.tirDirection == TirDirection.AFT)     { vm.setTirDirection(TirDirection.AFT) }
            TirButton("Looking FORWARD", runoutConfig.tirDirection == TirDirection.FORWARD) { vm.setTirDirection(TirDirection.FORWARD) }
            TirButton("Not set",         runoutConfig.tirDirection == TirDirection.UNSET)   { vm.setTirDirection(TirDirection.UNSET) }
        }
    }

    // ── Coupling face + its pilot runout ──────────────────────────────────────
    // Same per-job field the PDF options sheets elect (one field, three surfaces —
    // they cannot drift). The pilot runout opens the ordinary bubble editor: the
    // reading rides the readings list under the reserved coupling-pilot id, so the
    // face's value and a station's value are authored identically.
    val couplingSection: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = runoutConfig.showCouplingFace,
                onCheckedChange = { vm.setShowCouplingFace(it) },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Coupling face", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "End view drawn bottom-right, taken looking forward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The tab draws no end view, so the entered pilot reading is shown here — the
                // only place to check it without reopening the dialog or previewing the sheet.
                pilotRunoutSummary(
                    runoutReadings.find(COUPLING_PILOT_COMPONENT_ID, 0), unit,
                )?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("runout_pilot_summary"),
                    )
                }
            }
            TextButton(
                enabled = runoutConfig.showCouplingFace,
                onClick = {
                    editingBubble = EditingRunoutBubble(
                        componentId = COUPLING_PILOT_COMPONENT_ID,
                        stationIndex = 0,
                        title = "Coupling pilot",
                    )
                },
            ) { Text("Pilot runout…") }
        }
    }

    // ── Blank draft, export gate, Print / Preview / Export ────────────────────
    // The "look at the sheet and print it" group: it sits with the drawing in a two-pane
    // layout, and in the phone's one column it keeps its historical place.
    val printGroup: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = blankDraft,
                onCheckedChange = { blankDraft = it },
                modifier = Modifier.testTag(RUNOUT_BLANK_DRAFT_TAG),
            )
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

        if (!gate.enabled) {
            Text(
                gate.disabledMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        DocumentActionButtons(
            documentName = "Runout Sheet",
            onPrint = { printClassicRunout() },
            onPreview = { showPreview = true },
            onExport = { launcher.launchPicker(outputFilename, what = "runout export") },
            enabled = gate.enabled,
        )
    }

    // ── Measurement station selector ──────────────────────────────────────────
    // Last on the page: adjusting bubble counts is the exception, not the routine path,
    // and the pinned preview shows the effect live.
    val stationSection: @Composable ColumnScope.() -> Unit = {
        RunoutStationCountEditor(
            entries = entries,
            overrides = runoutConfig.componentOverrides,
            placements = stationPlacements,
            // A count change renumbers the component's stations, so it retires its "Undo move".
            onIncrement = { entry, count ->
                vm.addRunoutStation(entry.id, count)
                lastBubbleMove = lastBubbleMove.retiredBy(entry.id)
            },
            onDecrement = { entry, count ->
                vm.removeRunoutStation(entry.id, count)
                lastBubbleMove = lastBubbleMove.retiredBy(entry.id)
            },
            // Resets also drop the "Undo move" chip: undoing a drag onto a component the
            // user just returned to derived would silently re-author it.
            onResetPositions = { id ->
                vm.resetRunoutStationPositions(id)
                if (lastBubbleMove?.componentId == id) lastBubbleMove = null
            },
            onResetAllPositions = {
                vm.resetAllRunoutStationPositions()
                lastBubbleMove = null
            },
        )

        // (Worn-section authoring and the consolidated variant picker live on the
        // Consolidated Output tab — this tab is the runout authoring surface and
        // produces the classic runout sheet. The per-job "Shaft height" / "Liner
        // compression" pair reaches this document from the preview's Tune menu, where
        // the drawing they reshape is on screen while they move.)
    }

    // ── Screen ────────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        // ── Document title strip ─────────────────────────────────────────────
        // Editing station counts or typing a TIR reading here is unsaved work exactly
        // like a spec edit, so the asterisk belongs on this tab too.
        EditorDocumentTitle(
            documentName = currentDocumentName,
            hasUnsavedChanges = hasUnsavedChanges,
            onClick = onTitleClick,
        )

        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Nothing to open when the sidebar is already laid out beside the tabs.
            if (!LocalSidebarPermanent.current) {
                IconButton(onClick = onOpenSidebar) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                }
            }
            Text(
                text = "Runout Sheet",
                style = MaterialTheme.typography.titleLarge,
                // Weighted so the actions are measured first: on a phone the title yields
                // (ellipsized) rather than squeezing the trailing icons to nothing.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
            // The document's ONE history — this tab's edits are steps in it too.
            UndoRedoButtons(vm, tagPrefix = "runout")
            IconButton(
                onClick = onSave,
                modifier = Modifier.testTag("toolbar_save"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
            TabHelpButton(HELP_TOPIC_RECORD_RUNOUT, onOpenHelpTopic)
        }

        HorizontalDivider()

        // ── Tab body ──────────────────────────────────────────────────────────
        // One column on a phone or a MEDIUM window; two panes on an EXPANDED one. The
        // blocks are the same composables either way (see their declarations above), so
        // the two skeletons can never drift apart.
        if (widthClass == WindowWidthClass.EXPANDED) {
            SheetTwoPane(
                // The drawing and the actions that print it.
                pinnedCanvas = { if (spec.overallLengthMm > 0f) previewBlock() },
                canvasPaneScrolling = { printGroup() },
                // Everything that shapes the sheet.
                controlsPane = {
                    tirSection()
                    couplingSection()
                    HorizontalDivider()
                    stationSection()
                },
            )
        } else {
            // ── Pinned live preview ───────────────────────────────────────────
            // Deliberately OUTSIDE the scroll region: the whole point of the bubble-count
            // editor below is watching the profile change, so the preview must stay on
            // screen while the stations are scrolled to (on-device request).
            // The guard wraps the divider and padding too, so an OAL-less spec leaves no
            // orphan rule or gap above the controls.
            if (spec.overallLengthMm > 0f) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = previewBlock,
                )

                HorizontalDivider()
            }

            // ── Scrollable content ────────────────────────────────────────────
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
                tirSection()
                couplingSection()
                printGroup()

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()

                stationSection()
            }
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
                launcher.launchPicker(outputFilename, what = "runout export")
            },
            // The tab body's Print action, unchanged — one function behind both.
            onPrint = { printClassicRunout() },
            optionsSheet = {
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    shadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
                    vm = vm,
                    showCouplingFaceRow = true,
                    couplingFaceOn = runoutConfig.showCouplingFace,
                    showSBreak = true,
                    sBreakThresholdFrac = pdfSBreakThresholdFrac,
                    fractionStyle = pdfFractionStyle,
                    hasThreads = hasDrawnThreads(spec),
                    threadSlant = pdfThreadSlant,
                    threadDensity = pdfThreadDensity,
                    dualUnitLayout = pdfDualUnitLayout,
                    dualUnits = dualUnits,
                    onDualUnitsChange = { vm.setDualUnits(it) },
                    tuning = tuning,
                    // The same session-only state as the tab body's switch — ONE value, so
                    // the two can never disagree about what the preview is showing.
                    blankDraft = blankDraft,
                    onSetBlankDraft = { blankDraft = it },
                    // The classic sheet's drawn height and liner floors come off the same
                    // `RunoutConfig` fields as the consolidated sheet's, so the sheet being
                    // looked at can be tuned against itself here too.
                    showHeightSlider = true,
                    heightScale = runoutConfig.heightScale,
                    heightSliderBase = heightSliderBase,
                    heightSliderMaxDrawnPt = heightCapPt,
                    heightSliderMaxDiaMm = heightSliderDiaMm,
                    showLinerCompression = true,
                    linersProportional = runoutConfig.linersProportional,
                    linerCompression = runoutConfig.linerCompression,
                    estimateKeptFrac = { frac ->
                        estimatedLinerKeptFracOfTrue(spec, heightSliderBase, runoutConfig.heightScale, frac)
                    },
                    // This sheet's bubbles are the document, so their size and drop are
                    // tuned here as well as from Settings.
                    showBubbleControls = true,
                    runoutBubbleScale = pdfRunoutBubbleScale,
                    runoutBubbleDropScale = pdfRunoutBubbleDropScale,
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
 * The coupling-face row's line for an entered pilot reading — "Pilot runout: .003 in · high spot
 * 3:00", in the bubble's own 3-decimal format — or null when nothing is entered.
 */
internal fun pilotRunoutSummary(reading: RunoutReading?, unit: UnitSystem): String? {
    if (reading == null) return null
    val value = reading.valueMm?.let { "${formatRunoutValue(it, unit)} ${abbr(unit)}" }
    val spot = reading.highSpotHalfHours?.let { "high spot ${clockTickLabel(it)}" }
    return when {
        value != null && spot != null -> "Pilot runout: $value · $spot"
        value != null -> "Pilot runout: $value"
        spot != null -> "Pilot runout: $spot"
        else -> null
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
    val shadeExplicitBodiesOnly: Boolean,
    /** Bubble size and drop. Keys only — both travel to the composer inside the `PdfPrefs`
     *  snapshot, and both also feed the canvas preview's own plan. */
    val runoutBubbleScale: Float,
    val runoutBubbleDropScale: Float,
    /** Not a composer argument — it reaches the ink via `FractionTypography.active`. Key only. */
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
    /** Sizing-curve anchors — the drawn shaft height rides on them. Keys only; they travel
     *  to the composer inside the `PdfPrefs` snapshot. */
    val curveLoHeightIn: Float,
    val curveHiHeightIn: Float,
    val readings: RunoutReadings,
    val stationPlacements: RunoutStationPlacements,
    val blankValues: Boolean,
    val displayUnits: DisplayUnits,
    /** A tuning slider is mid-drag: raster at draft resolution and hold the spinner back. */
    val draft: Boolean,
)

/** Test tag on the tab's "Blank draft (write-in)" switch. */
internal const val RUNOUT_BLANK_DRAFT_TAG = "runout_blank_draft"

/** Identifies the bubble whose editor dialog is open. */
private data class EditingRunoutBubble(
    val componentId: String,
    val stationIndex: Int,
    val title: String,
)

/**
 * Bundles an open bubble editor across an activity recreation (a tablet rotation), so the
 * dialog — and the value and high spot it holds — come back where they were.
 */
private val EditingRunoutBubbleSaver: Saver<EditingRunoutBubble?, Any> = listSaver<EditingRunoutBubble?, Any>(
    save = { bubble ->
        if (bubble == null) emptyList() else listOf(bubble.componentId, bubble.stationIndex, bubble.title)
    },
    restore = { parts ->
        if (parts.size < 3) null
        else EditingRunoutBubble(
            componentId = parts[0] as String,
            stationIndex = parts[1] as Int,
            title = parts[2] as String,
        )
    },
)

/**
 * A bubble being dragged along its component, with that component's complete in-progress
 * station set (component-local mm, AFT→FWD).
 *
 * The whole set travels rather than just the moved value because the canvas re-plans from it
 * every frame and the commit stores it wholesale — a component is authored as a unit.
 */
private data class DraggingRunoutStation(
    val componentId: String,
    val stationIndex: Int,
    /**
     * The component's complete current set — the clamp fence the drag works inside. Only
     * `positionsMm[stationIndex]` moves; the rest ride along solely so the neighbour clamp
     * has something to clamp against, and none of them are ever stored.
     */
    val positionsMm: List<Float>,
    /**
     * The set as it stood at pickup. A long press that never moves must commit NOTHING — it
     * would otherwise pin the station and mark the document dirty for a gesture that changed
     * nothing on screen.
     */
    val originalPositionsMm: List<Float>,
    /**
     * The station's STORED pin at pickup, null when it was derived. What "Undo move"
     * restores — null undoes a first drag all the way back to automatic placement.
     */
    val previousPinMm: Float?,
)

/** What "Undo move" restores: the moved station's pin before the last committed drag. */
private data class LastBubbleMove(
    val componentId: String,
    val stationIndex: Int,
    /** Pre-drag pin, or null when the station was derived (undo un-pins it). */
    val previousMm: Float?,
    /** The component's station count when the drag committed; null if it was not listed. */
    val countAtMove: Int?,
)

/** This move, or null when a station-count change on [componentId] has retired it. */
private fun LastBubbleMove?.retiredBy(componentId: String): LastBubbleMove? =
    this?.takeUnless { it.componentId == componentId }

/**
 * Whether "Undo move" may still act: only while the moved component's station count is the
 * one the drag was made at. A different count — or a component no longer listed — means its
 * stations were renumbered, and the stored index could un-pin a different bubble.
 */
internal fun undoMoveStillApplies(countAtMove: Int?, currentCount: Int?): Boolean =
    countAtMove != null && currentCount == countAtMove

/**
 * Invert the Canvas `graphicsLayer` transform — scale about the centre pivot, then translate —
 * so a raw touch maps into the space the bubble plan was solved in. Shared by the tap and drag
 * handlers; hit-testing against a different transform than the one drawn is how a gesture
 * starts missing its target as soon as the preview is zoomed.
 */
private fun toPlanSpace(
    raw: Offset,
    widthPx: Float,
    heightPx: Float,
    scale: Float,
    offset: Offset,
): Offset {
    val pivotX = widthPx / 2f
    val pivotY = heightPx / 2f
    return Offset(
        (raw.x - offset.x - pivotX) / scale + pivotX,
        (raw.y - offset.y - pivotY) / scale + pivotY,
    )
}

/**
 * The component-local positions of every station currently drawn — the clamp fence a drag
 * works inside, one entry per drawn station. Stored pins are kept verbatim; derived stations
 * read off the drawn bubbles, so the fence always matches what the user is looking at, even
 * when a session undo has left the count and the pins briefly disagreeing.
 *
 * A stored pin wins over the drawn value because a pin stranded in a gap between a fragmented
 * body's runs draws pulled onto metal — reading the drawn value there would quietly substitute
 * the repaired position for what the user authored. Nothing here is committed wholesale; a
 * drag stores only the single station it moved.
 */
private fun currentStationPositions(
    componentId: String,
    bubbles: List<PlacedRunoutBubble>,
    resolvedComponents: List<ResolvedComponent>,
    placements: RunoutStationPlacements,
): List<Float> {
    val runs = runoutComponentSpans(resolvedComponents).filter { it.id == componentId }
    val drawn = localStationPositions(
        runs,
        bubbles.filter { it.componentId == componentId }.map {
            RunoutStationX(it.componentId, it.stationMm, it.stationX, it.stationIndex)
        },
    )
    val stored = placements.positionsFor(componentId)
    return drawn.mapIndexed { i, mm -> stored[i] ?: mm }
}

/** Display title for the editor dialog, e.g. "Body 1 · Station 2". */
private fun runoutBubbleTitle(bubble: PlacedRunoutBubble, entries: List<RunoutComponentEntry>): String =
    moveSelectionLabel(RunoutMoveSelection(bubble.componentId, bubble.stationIndex), entries)

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
    placements: RunoutStationPlacements = RunoutStationPlacements(),
    /** `PdfPrefs.runoutBubbleScale` — the same multiplier the sheet applies to its radius. */
    bubbleScale: Float = 1f,
    /** `PdfPrefs.runoutBubbleDropScale` — the same multiplier the sheet applies to its drop. */
    bubbleDropScale: Float = 1f,
): RunoutPreview {
    val marginPx = 12.dp.toPx()
    // The planner keeps a bubble's EDGE inside the content bounds, but its ring is stroked
    // centred on that edge (2.5× wide while dragged or selected) and the card clips to rounded
    // corners, so a bubble at the canvas edge lost a sliver of its ring (on-device report).
    val ringAllowancePx = 2.dp.toPx()
    // The two user-set bubble multipliers, applied exactly as the composer applies them
    // (`pdf/RunoutPdfComposer.kt`): radius and drop scale, `minGap` stays a fixed clearance
    // floor. Canvas and sheet must agree — the bubbles are one drawing in two renderings.
    val bubbleGeom = RunoutBubbleGeometry(
        radius = 7.dp.toPx() * bubbleScale,
        minGap = 5.dp.toPx(),
        shortLeader = 5.dp.toPx() * bubbleDropScale,
        contentLeft = ringAllowancePx,
        contentRight = widthPx - ringAllowancePx,
    )
    val spans = runoutComponentSpans(resolvedComponents)

    // Reserve vertical space for the planned bubble rows so shaft + bubbles are centred together.
    // First pass assumes the typical two-row layout; re-plan once if the actual row count differs.
    fun planFor(reservedH: Float): Pair<ShaftLayout.Result, RunoutBubblePlan> {
        val layout = ShaftLayout.compute(
            spec = spec, leftPx = 0f, topPx = 0f, rightPx = widthPx,
            bottomPx = heightPx - reservedH, marginPx = marginPx,
            resolvedComponents = resolvedComponents,
            // The window holds every drawn part, so a body running past a shortened OAL — and
            // its bubbles — stay on the canvas instead of being cut at its right edge
            // (on-device report).
            extentMm = resolvedComponents.componentAxialExtentMm(),
        )
        val stations = collectRunoutStations(
            spans, overrides,
            xAtMm = { mm -> layout.xPx(mm) },
            mmAtX = { px -> layout.xMmFromPx(px) },
            placements = placements,
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

/** Bubble leaders, rings and values on the preview canvas — and Move mode's selection frame. */
private val RunoutMarkerInk = SheetInk.Outline.copy(alpha = 0.70f)

/**
 * The component whose drawn run sits under plan-space point [p], or null. Axially the run's
 * span decides ([runoutSpanIdAt]); vertically the point must fall inside the drawn profile at
 * that station — the outer surface either side of the centreline, read off the same
 * [runoutOdMmAt] the bubble leaders land on — widened by one bubble radius so a finger on the
 * outline still counts. Below that band is the bubble field, where a miss is bare paper.
 */
private fun runoutComponentAt(
    preview: RunoutPreview,
    resolvedComponents: List<ResolvedComponent>,
    p: Offset,
): String? {
    val mm = preview.layout.xMmFromPx(p.x)
    val id = runoutSpanIdAt(runoutComponentSpans(resolvedComponents), mm) ?: return null
    val halfHeight = preview.layout.rPx(runoutOdMmAt(mm, resolvedComponents)) + preview.geom.radius
    return id.takeIf { abs(p.y - preview.layout.centerlineYPx) <= halfHeight }
}

/**
 * Move mode's component selection: a thin dashed frame around each drawn run of [componentId],
 * a few dp clear of the outline. Screen affordance only — the printed sheet carries no
 * selection, so there is no PDF counterpart — and dashed so it never reads as a drawn edge.
 */
private fun DrawScope.drawRunoutComponentSelection(
    layout: ShaftLayout.Result,
    componentId: String,
    resolvedComponents: List<ResolvedComponent>,
) {
    val pad = 4.dp.toPx()
    val stroke = Stroke(
        width = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    runoutComponentSpans(resolvedComponents).filter { it.id == componentId }.forEach { run ->
        val endMm = run.startMm + run.lengthMm
        // Sampled just inside the run's ends (and mid-run) so the neighbour across a face
        // never sets the frame's height; a taper's largest Ø is at one of its ends.
        val inset = minOf(1f, run.lengthMm / 4f)
        val maxOdMm = maxOf(
            runoutOdMmAt(run.startMm + inset, resolvedComponents),
            runoutOdMmAt(run.startMm + run.lengthMm / 2f, resolvedComponents),
            runoutOdMmAt(endMm - inset, resolvedComponents),
        )
        val halfHeight = layout.rPx(maxOdMm) + pad
        val left = layout.xPx(run.startMm) - pad
        val right = layout.xPx(endMm) + pad
        drawRect(
            color = RunoutMarkerInk,
            topLeft = Offset(left, layout.centerlineYPx - halfHeight),
            size = Size(right - left, 2f * halfHeight),
            style = stroke,
        )
    }
}

/**
 * Draw the planned runout bubbles: leader polylines, the circle with a keyway cutout at 12 o'clock,
 * and — when recorded — the TIR value (centred) and the high-spot marker (a short dash straddling
 * the rim at the clock position). The
 * keyway cutout and marker geometry mirror the PDF (`RunoutPdfComposer.drawPlacedBubbles`) so the
 * preview matches the export exactly.
 *
 * [dragging] is transient screen affordance, never ink: the bubble under the finger takes a
 * heavier ring so the user can see which one they picked up. [selected] — Move mode's selected
 * station — takes the same ring: one look for "this bubble is the active one". Neither has a
 * PDF counterpart, so the lockstep rule above is untouched — nothing about the printed sheet
 * changes.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRunoutMarkers(
    bubbles: List<PlacedRunoutBubble>,
    geom: RunoutBubbleGeometry,
    readings: RunoutReadings,
    unit: com.android.shaftschematic.util.UnitSystem,
    textMeasurer: TextMeasurer,
    /** (W/Ø, D/Ø) of the shaft's own keyway — `sheetKeywayProportion`, once per spec. */
    keywayProportion: Pair<Float, Float>,
    dragging: DraggingRunoutStation? = null,
    selected: RunoutMoveSelection? = null,
) {
    val strokeW     = 1.2.dp.toPx()
    val markerColor = RunoutMarkerInk
    val highSpotColor = Color(0xFFC62828) // red — the high spot, per shop convention
    val r = geom.radius
    // One notch for every bubble, sized at the plain ring stroke so a heavier dragged/selected
    // ring never reshapes its slot.
    val notch = bubbleKeywayNotch(r, keywayProportion.first, keywayProportion.second, stroke = strokeW)

    bubbles.forEach { b ->
        val isDragged = dragging != null &&
            b.componentId == dragging.componentId && b.stationIndex == dragging.stationIndex
        val isSelected = selected?.stationIndex != null &&
            b.componentId == selected.componentId && b.stationIndex == selected.stationIndex
        val center = Offset(b.bubbleX, b.bubbleCenterY)
        b.leader.zipWithNext { p, q ->
            drawLine(markerColor, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = strokeW)
        }
        drawRunoutBubbleRing(
            center, r, notch, markerColor,
            if (isDragged || isSelected) strokeW * 2.5f else strokeW,
        )

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
 * convention). The slot is [notch] — sized from the shaft's own keyway by
 * `geom/RunoutBubbleNotchMath.kt`, the same notch the PDF renderer draws.
 */
private fun DrawScope.drawRunoutBubbleRing(
    center: Offset, r: Float, notch: BubbleKeywayNotch, color: Color, strokeW: Float,
) {
    val stroke = Stroke(width = strokeW)
    // Slot half-width, depth, and the angular gap it subtends at the rim (from 12 o'clock).
    val slotHalf = notch.slotHalf
    val slotDepth = notch.slotDepth
    val gapDeg = notch.gapDeg

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
