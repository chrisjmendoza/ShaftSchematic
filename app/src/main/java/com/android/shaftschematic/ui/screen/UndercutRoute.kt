// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UNDERCUT_DEFAULT_LENGTH_MM
import com.android.shaftschematic.geom.UNDERCUT_EXAGGERATION_MAX_FRAC
import com.android.shaftschematic.geom.UNDERCUT_WINDOW_PAD_MM
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.assignUndercutLiners
import com.android.shaftschematic.geom.buildUndercutNotches
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.effectiveUndercutReference
import com.android.shaftschematic.geom.isUndercutStaleOverrun
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.geom.undercutStripsReachThread
import com.android.shaftschematic.geom.UNDERCUT_SET_REACH_MM
import com.android.shaftschematic.geom.undercutSetReachMm
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.pickUndercutStripAt
import com.android.shaftschematic.geom.undercutDisplayedDistanceMm
import com.android.shaftschematic.geom.undercutReferenceParentSpan
import com.android.shaftschematic.geom.undercutReferenceLabel
import com.android.shaftschematic.geom.undercutReferenceLinerFor
import com.android.shaftschematic.geom.undercutSetPositions
import com.android.shaftschematic.geom.undercutSheetNumbers
import com.android.shaftschematic.geom.undercutMissingDiaMessage
import com.android.shaftschematic.geom.undercutsMissingDia
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.pdf.buildUndercutStripTitle
import com.android.shaftschematic.pdf.composeUndercutPdf
import com.android.shaftschematic.pdf.resolveUndercutPageOrientation
import com.android.shaftschematic.pdf.isUndercutStripPrinted
import com.android.shaftschematic.pdf.undercutSheetOrientation
import com.android.shaftschematic.pdf.undercutSheetStripCount
import com.android.shaftschematic.pdf.formatDiaWithUnit
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.ui.adaptive.LocalSidebarPermanent
import com.android.shaftschematic.ui.adaptive.WindowWidthClass
import com.android.shaftschematic.ui.adaptive.currentWindowWidthClass
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addUndercut
import com.android.shaftschematic.ui.viewmodel.removeUndercut
import com.android.shaftschematic.ui.viewmodel.restoreUndercut
import com.android.shaftschematic.ui.viewmodel.setPdfUndercutSplitAtLinerEdge
import com.android.shaftschematic.ui.viewmodel.setUndercutDrawToSet
import com.android.shaftschematic.ui.viewmodel.setUndercutPageOrientation
import com.android.shaftschematic.ui.viewmodel.setUndercutSectionPrinted
import com.android.shaftschematic.ui.viewmodel.setUndercutSetDatum
import com.android.shaftschematic.ui.viewmodel.setUndercutExaggeration
import com.android.shaftschematic.ui.viewmodel.updateUndercut
import com.android.shaftschematic.ui.viewmodel.updateUndercutReference
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.util.launchPicker
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import com.android.shaftschematic.util.writeShaftPdfToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * UndercutRoute
 *
 * Screen for the Undercut Drawing tab — the shop's record of machined-below-surface sections
 * (weld-repair undercuts, cleanup cuts). See `docs/archive/UndercutDrawing_PLAN.md`.
 *
 * ## Layout
 * - **Overview canvas** — `ShaftLayout.compute` + `ShaftRenderer.draw` over
 *   `resolvedComponents` (never raw spec), then an affordance pass: every undercut's notch cut
 *   into the profile, plus a faint tint + count badge over **every liner** and over each
 *   bare-shaft **cluster window**. Liners are tap targets whether or not they hold cuts (the
 *   wear document's idiom) — on-device report: tapping a liner that had no cut yet did nothing,
 *   leaving no way in. A tap hit-tests in mm space via [ShaftLayout.Result.xMmFromPx]:
 *   `pickUndercutStripAt` first, then liner containment, and opens
 *   [UndercutWindowDetailOverlay] on that strip (an empty liner yields an empty
 *   `linerStripFor` strip — the authoring entry point). No pinch-zoom here; the overlay owns zoom.
 * - **Preview / Options** — a compact row directly under the overview canvas: the same preview
 *   the foot of the tab opens, and the undercut preview's options panel ([UndercutOptionsSheet]
 *   in a non-modal [PreviewOptionsPanel]) over the tab, so neither needs the tab scrolled to its
 *   foot (on-device request). The detail page's top bar carries the same pair.
 * - **Add undercut** — asks "Where is the cut?" (one row per liner, AFT → FWD, then bare shaft
 *   from either S.E.T.) and opens that strip on a PENDING "New undercut" card, the detail page's
 *   own add seed (`seedNewCut`). Nothing is recorded until the card is confirmed, so backing out
 *   leaves no cut behind; a bare-shaft add opens a synthetic empty window at the chosen S.E.T.
 *   ([undercutBareShaftAddStrip]) until the confirmed cut's real strip takes over. Precision
 *   comes from the overlay's numeric fields, never from the tap (wear posture).
 * - **Undercut list** — one compact row per recorded cut, numbered and ordered AFT → FWD
 *   (`undercutSheetNumbers` — the numbers the detail page shows; they print on no sheet): its
 *   distance under its own authored reference, length, Ø ("Ø not entered" when unmeasured), and a stale-overrun warning. Tapping a
 *   row opens that cut's strip; the trailing icon deletes it confirm-free, with a "Cut N deleted"
 *   snackbar Undo (`restoreUndercut`).
 * - **Drawing settings** — collapsed under the list, out of the authoring flow (usability
 *   review: nothing between Add and the list may read as a required step): "Page" (per job —
 *   Auto | Landscape | Portrait, [UndercutPageOrientationRow]), "Drawn depth" (a
 *   slider, 0 – `UNDERCUT_EXAGGERATION_MAX_FRAC`, storing the sheet's drawn-depth setting in the
 *   record — display-only styling for every notch draw site, never a stored or printed Ø),
 *   "Draw to the shaft end" (per job) and "Split cuts at liner edges" (app-wide).
 * - Blank-draft toggle, Preview, Print, Export — straight ports of the wear document's flows,
 *   all calling `composeUndercutPdf`. Print and Export (tab body and preview alike) first ask
 *   "Print without diameters?" while any cut has no Ø and no blank draft is elected.
 *
 * Undercuts are reference-only data: nothing on this screen touches geometry, OAL, or
 * collision.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun UndercutRoute(
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
    // Print line art for THIS document: it suppresses every fill on the sheet, so it is both an
    // options-sheet control and a preview re-render key.
    val pdfUndercutLineArt by vm.pdfUndercutLineArt.collectAsState()
    // The dotted liner outline over the cuts: a preview re-render key, an options-sheet control,
    // and read by the overview canvas and the detail overlay alike.
    val pdfUndercutLinerOutline by vm.pdfUndercutLinerOutline.collectAsState()
    // Chain-rail nodes at buried liner edges: a preview re-render key, an options-sheet control,
    // and read by the detail overlay's rail.
    val pdfUndercutSplitAtLinerEdge by vm.pdfUndercutSplitAtLinerEdge.collectAsState()
    val pdfFractionStyle   by vm.pdfFractionStyle.collectAsState()
    val pdfOutputFont      by vm.pdfOutputFont.collectAsState()
    val pdfThreadSlant     by vm.pdfThreadSlant.collectAsState()
    val pdfThreadDensity   by vm.pdfThreadDensity.collectAsState()
    // Dual-unit layout: this document stacks its dual values like every other
    // (`wantDualStacked`), so its options sheet has to show the stored choice — and the
    // preview has to redraw when it changes.
    val pdfDualUnitLayout  by vm.pdfDualUnitLayout.collectAsState()
    val undercutRecord     by vm.undercutRecord.collectAsState()
    val undercutStyle      by vm.undercutStyle.collectAsState()
    // Per-component display units + inline-dual flag: same posture as `pdfFractionStyle` —
    // neither reaches the composer through a field already collected above, so both ride
    // along as explicit LaunchedEffect keys below.
    val unitOverrides      by vm.unitOverrides.collectAsState()
    val dualUnits          by vm.dualUnits.collectAsState()
    // The document's ONE undo history — the same steps the Schematic tab's Undo walks.
    val canUndo            by vm.canUndo.collectAsState()
    val canRedo            by vm.canRedo.collectAsState()

    val pdfArrowSizePt     by vm.pdfArrowSizePt.collectAsState()

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    // The options panel over the tab body (the "Options" button under the overview canvas).
    var showTabOptions by rememberSaveable { mutableStateOf(false) }
    // Where the overview canvas ends, root px — the tab options panel's ceiling.
    var overviewBottomPx by remember { mutableFloatStateOf(0f) }
    // Blank-draft (write-in) copy: outline + form layout, all values blanked for handwriting.
    var blankDraft by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // Where the rendered page carries ink, for the tuning page strip (`PdfPreviewOverlay`'s
    // `inkBand`). This tab has no live-drag channel — every control commits on release and
    // re-renders the whole page — so there are no draft frames to skip: each pass measures.
    var previewInkBand by remember { mutableStateOf<InkBand?>(null) }
    // What anchors the open detail overlay. Strips are derived state (they re-cluster on every
    // record edit), so the anchor is an id rather than a strip index and the strip is looked up
    // fresh below. Two anchors, one live at a time: a **liner** id keeps a liner strip open even
    // while it holds no cuts (so an emptied liner strip doesn't slam shut mid-authoring), an
    // **undercut** id anchors a bare-shaft cluster. The liner anchor wins when both are set.
    var anchorLinerId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorUndercutId by rememberSaveable { mutableStateOf<String?>(null) }
    // The open overlay's identity and the cut it opens on, both fixed at OPEN time. The free-strip
    // anchor moves to a sibling when its own cut is deleted, so the overlay's state cannot key on
    // the anchor itself — the identity outlives every edit made inside the overlay.
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var overlayInitialSelectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The "Add undercut" path: the overlay opens on a PENDING "New undercut" card (its own add
    // seed) instead of a recorded cut, so backing out records nothing. Only the add chooser sets
    // it; every other opening (canvas tap, list row) and every close clears it.
    var seedNewCutOnOpen by rememberSaveable { mutableStateOf(false) }
    // A bare-shaft add has no strip to open yet — no cut is recorded there — so the overlay opens
    // on a synthetic empty window at the chosen S.E.T. ([undercutBareShaftAddStrip]): true = AFT,
    // false = FWD, null = none. The side is held rather than the strip so it survives recreation;
    // the confirmed cut's real strip supersedes it (see `activeStrip`).
    var bareShaftAddFromAft by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // "Where is the cut?" — the add chooser.
    var showAddWhere by rememberSaveable { mutableStateOf(false) }
    // The collapsed "Drawing settings" section under the list.
    var showDrawingSettings by rememberSaveable { mutableStateOf(false) }

    // Every way the overlay opens comes through here, so the identity is always set with the
    // anchor. [selectId] is the card to open on; null opens on the strip's aft-most card.
    // [seedNewCut] opens it on a pending new cut (the add chooser's liner rows).
    fun openOverlay(linerId: String?, undercutId: String?, selectId: String?, seedNewCut: Boolean = false) {
        // The detail page carries its own options panel; the tab's would sit forgotten under it.
        if (linerId != null || undercutId != null) showTabOptions = false
        anchorLinerId = linerId
        anchorUndercutId = undercutId
        overlayInitialSelectedId = selectId
        overlayKey = linerId?.let { "liner:$it" } ?: undercutId?.let { "free:$it" }
        seedNewCutOnOpen = seedNewCut
        bareShaftAddFromAft = null
    }

    // The add chooser's bare-shaft rows: the synthetic window at that S.E.T., on a pending cut.
    fun openBareShaftAdd(fromAft: Boolean) {
        showTabOptions = false
        anchorLinerId = null
        anchorUndercutId = null
        overlayInitialSelectedId = null
        overlayKey = if (fromAft) "free:new:aft" else "free:new:fwd"
        seedNewCutOnOpen = true
        bareShaftAddFromAft = fromAft
    }

    // Collisions corrupt any drawing, so the shared export gate guards this surface too —
    // the same posture as the runout and schematic surfaces.
    val collidingIds = remember(spec) { spec.collidingIds() }
    val gate = remember(spec, collidingIds) { exportPdfGate(spec, collidingIds) }

    /**
     * Sends the undercut drawing to the platform print dialog. ONE action behind the tab
     * body's Print button and the preview overlay's Print icon, so the two entry points
     * cannot drift. Every value is snapshotted here on the UI thread — `onWrite` runs on a
     * binder thread.
     */
    fun printUndercutDrawing() {
        val jobName = buildOutputFilename(customer, vessel, jobNumber, shaftPosition, OutputDoc.UNDERCUT, blankDraft)
            .removeSuffix(".pdf")
        val specSnapshot = spec
        val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
            jobNumber = jobNumber, side = shaftPosition, item = item)
        val unitSnapshot = unit
        val prefsSnapshot = vm.currentPdfPrefs
        val resolvedSnapshot = resolvedComponents
        val thicknessSnapshot = lineThicknessScale
        val recordSnapshot = undercutRecord
        val blankSnapshot = blankDraft
        val displayUnitsSnapshot = vm.currentDisplayUnits()
        // The page the sheet's section count calls for — the same resolution the preview and
        // the export start.
        val orientation = undercutSheetOrientation(specSnapshot, resolvedSnapshot, recordSnapshot, blankSnapshot)
        printShaftPdfPage(ctx, jobName, orientation) { page ->
            composeUndercutPdf(
                page = page, spec = specSnapshot,
                project = projectSnapshot, unit = unitSnapshot,
                displayUnits = displayUnitsSnapshot,
                pdfPrefs = prefsSnapshot,
                resolvedComponents = resolvedSnapshot,
                undercutRecord = recordSnapshot,
                lineThicknessScale = thicknessSnapshot,
                blankValues = blankSnapshot,
            )
        }
    }

    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents, spec) { surfaceSegsFrom(resolvedComponents, bodyBlends(spec, resolvedComponents)) }
    // In-progress exaggeration drag (PreviewTuning doctrine): the canvas restyles from this
    // local value while the finger is on the track, and the ViewModel sees ONE write on
    // release — a drag frame must never touch the record or the per-job dirty mark.
    var dragExaggeration by remember { mutableStateOf<Float?>(null) }
    val shownExaggeration = dragExaggeration ?: undercutRecord.exaggerationFrac
    val notches = remember(undercutRecord, segs, oalMm, shownExaggeration) {
        buildUndercutNotches(
            undercutRecord.undercuts, segs, oalMm,
            exaggerationFrac = shownExaggeration,
        )
    }
    val linerSpans = remember(resolvedComponents) { linerSpansOf(resolvedComponents) }
    val linerOds = remember(resolvedComponents) { linerOdsOf(resolvedComponents) }
    val spans = remember(undercutRecord, oalMm) {
        undercutRecord.undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }
    // "Draw to the shaft end" (per-job): a liner strip near an S.E.T. chains out to it and draws
    // on to the shaft's physical end.
    val setReachMm = undercutSetReachMm(undercutRecord.drawToSet)
    val (stripAftSetMm, stripFwdSetMm) = remember(spec) { undercutSetPositions(spec) }
    val strips = remember(spans, linerSpans, oalMm, segs, setReachMm, stripAftSetMm, stripFwdSetMm) {
        buildUndercutStrips(
            spans, linerSpans, oalMm, segs = segs,
            reachToSetMm = setReachMm, aftSetMm = stripAftSetMm, fwdSetMm = stripFwdSetMm,
        )
    }
    // Whether the printed sheet draws a thread anywhere — the gate on its options' Thread section.
    // The sheet prints the cut strips, or a started strip per liner when nothing is recorded, or
    // the whole-shaft profile when it has neither (the composer's own order).
    // Only the PRINTED sections count ("Sections to print"): a thread reached only by a section left
    // off draws nowhere, and a sheet with every section left off draws no shaft at all.
    val sheetDrawsThread = remember(
        strips, linerSpans, oalMm, segs, setReachMm, stripAftSetMm, stripFwdSetMm, spec, undercutRecord,
    ) {
        val allStrips = strips.ifEmpty {
            linerSpans.sortedBy { it.startMm }.map {
                linerStripFor(
                    it, emptyList(), oalMm, segs = segs,
                    reachToSetMm = setReachMm, aftSetMm = stripAftSetMm, fwdSetMm = stripFwdSetMm,
                )
            }
        }
        val pageStrips = allStrips.filter { isUndercutStripPrinted(it, undercutRecord) }
        when {
            allStrips.isEmpty() -> hasDrawnThreads(spec)
            pageStrips.isEmpty() -> false
            else -> undercutStripsReachThread(
                pageStrips,
                spec.threads.map { it.startFromAftMm to it.startFromAftMm + it.lengthMm },
            )
        }
    }
    // The printed sheet's section count and the page it resolves to — the print path's own
    // computation (`undercutSheetStripCount`, blank draft included), so the Page caption names
    // the page that will print.
    val sheetStripCount = remember(spec, resolvedComponents, undercutRecord, blankDraft) {
        undercutSheetStripCount(spec, resolvedComponents, undercutRecord, blankDraft)
    }
    val sheetOrientation = resolveUndercutPageOrientation(undercutRecord.pageOrientation, sheetStripCount)
    // "Sections to print" (per job): every section the sheet could print, AFT → FWD and before the
    // election, labelled as its strip title names it; the cuts in a section left off; and whether
    // the job leaves EVERY section off, which takes Print and Export away (Preview shows the note).
    val printSections = remember(spec, resolvedComponents, undercutRecord, blankDraft, unit, unitOverrides, dualUnits) {
        undercutPrintSections(
            spec, resolvedComponents, undercutRecord, blankDraft, DisplayUnits(unit, unitOverrides, dualUnits),
        )
    }
    val notPrintedCutIds = remember(spec, resolvedComponents, undercutRecord) {
        undercutCutIdsNotPrinted(spec, resolvedComponents, undercutRecord)
    }
    val noSectionSelected = printSections.isNotEmpty() && printSections.none { it.printed }
    val outputEnabled = gate.enabled && !noSectionSelected
    val freeWindows = remember(strips) {
        strips.filterIsInstance<UndercutStrip.FreeStrip>().map { it.window }
    }
    // Each cut's liner, chained cuts included — the assignment `buildUndercutStrips` makes.
    val linerOfCut = remember(spans, linerSpans) { assignUndercutLiners(spans, linerSpans) }
    val cutCountByLiner = remember(linerOfCut) {
        linerOfCut.values.filterNotNull().groupingBy { it }.eachCount()
    }

    // One liner's strip, cuts included — the same construction `buildUndercutStrips` uses, so a
    // liner tapped with zero cuts and the same liner tapped later with three read identically.
    fun stripForLiner(liner: UndercutLinerSpan): UndercutStrip.LinerStrip {
        val members = spans.filter { linerOfCut[it.id] == liner.id }
        return linerStripFor(
            liner, members, oalMm, segs = segs, cutSpans = members,
            reachToSetMm = setReachMm, aftSetMm = stripAftSetMm, fwdSetMm = stripFwdSetMm,
        )
    }

    // An anchor that no longer resolves (the undercut was deleted, the liner removed, or the
    // shaft shrank past the cut) simply yields no overlay — an anchor is never auto-cleared,
    // because the record and the anchor can update in either order within a frame and clearing
    // on the stale pass would close an overlay that was just opened.
    val anchoredUndercutId = anchorUndercutId
    val anchoredStrip = anchoredUndercutId?.let { id -> strips.firstOrNull { id in it.undercutIds } }
    // The bare-shaft add's synthetic window. It stays the fallback until the confirmed cut's own
    // strip resolves: the record reaches this composition through a collected flow, so the frame
    // after the confirm can carry the new anchor before the new cut — dropping the overlay for
    // that frame would wipe its state (the selection that follows the new card included).
    val syntheticStrip = bareShaftAddFromAft?.let { fromAft ->
        undercutBareShaftAddStrip(fromAft, stripAftSetMm, stripFwdSetMm, oalMm)
    }
    val activeStrip: UndercutStrip? = when {
        anchorLinerId != null ->
            linerSpans.firstOrNull { it.id == anchorLinerId }?.let { ln -> stripForLiner(ln) }
        anchoredStrip != null -> anchoredStrip
        syntheticStrip != null -> syntheticStrip
        else -> null
    }
    // Once the real strip holds the confirmed cut, the synthetic window has done its job; from then
    // on the overlay follows the anchor like any free strip (deleting its last cut closes it).
    val syntheticSuperseded = bareShaftAddFromAft != null && anchoredStrip != null
    LaunchedEffect(syntheticSuperseded) {
        if (syntheticSuperseded) bareShaftAddFromAft = null
    }

    // Open the strip an undercut belongs to, from the list below the canvas — on that cut's card.
    fun anchorToUndercut(id: String) {
        when (val s = strips.firstOrNull { id in it.undercutIds }) {
            is UndercutStrip.LinerStrip -> openOverlay(linerId = s.linerId, undercutId = null, selectId = id)
            else -> openOverlay(linerId = null, undercutId = id, selectId = id)
        }
    }

    // The cut numbers the list and the detail page show (AFT → FWD), and the cuts that would print with no Ø.
    val sheetNumbers = remember(undercutRecord, oalMm) { undercutSheetNumbers(undercutRecord.undercuts, oalMm) }
    val missingDia = remember(undercutRecord) { undercutsMissingDia(undercutRecord.undercuts) }

    // The list's delete: confirm-free, with the detail page's snackbar Undo — the removed cut and
    // its record index are captured BEFORE the removal, so the Undo puts back that exact cut at
    // that exact position (`restoreUndercut`, verbatim; a no-op when a history Undo already
    // restored it). Nothing navigates: the list stays where it is.
    val listSnackbar = remember { SnackbarHostState() }
    val listSnackbarScope = rememberCoroutineScope()
    fun deleteFromList(u: Undercut, number: Int) {
        val index = undercutRecord.undercuts.indexOfFirst { it.id == u.id }
        if (index < 0) return
        val removed = undercutRecord.undercuts[index]
        vm.removeUndercut(u.id)
        listSnackbarScope.launch {
            // One pending delete at a time: a second delete replaces the first's offer.
            listSnackbar.currentSnackbarData?.dismiss()
            val result = listSnackbar.showSnackbar(
                message = "Cut $number deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreUndercut(removed, index)
        }
    }

    // Print / Export with cuts that have no Ø: asked first, unless a blank draft is elected (a
    // blank draft prints no values by design). [missingDiaAsk] holds the action waiting on the
    // answer; Preview is never gated.
    var missingDiaAsk by remember { mutableStateOf<UndercutMissingDiaAsk?>(null) }
    fun guardMissingDia(export: Boolean, action: () -> Unit) {
        if (!blankDraft && missingDia.isNotEmpty()) {
            missingDiaAsk = UndercutMissingDiaAsk(count = missingDia.size, export = export, action = action)
        } else {
            action()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            // Hardened write: a composer throw yields a valid error page, never a
            // truncated file (util/PdfSafExport.kt — one implementation for every tab).
            val orientation = undercutSheetOrientation(spec, resolvedComponents, undercutRecord, blankDraft)
            val wrote = writeShaftPdfToUri(ctx, uri, orientation) { page ->
                composeUndercutPdf(
                    page = page, spec = spec,
                    project = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition, item = item),
                    unit = unit,
                    displayUnits = vm.currentDisplayUnits(),
                    pdfPrefs = vm.currentPdfPrefs,
                    resolvedComponents = resolvedComponents,
                    undercutRecord = undercutRecord,
                    lineThicknessScale = lineThicknessScale,
                    blankValues = blankDraft,
                )
            }
            if (wrote && openAfterExport) openUndercutPdf(ctx, uri)
        }
    }

    // pdfFractionStyle is a key only: the style reaches the ink through
    // FractionTypography.active, which is not snapshot state — without the key a style
    // change would leave the rasterized preview drawing the old construction. unitOverrides
    // and dualUnits are keys for the same reason: they reach the composer only through the
    // displayUnits snapshot built below, not through any field already keyed above.
    // pdfDualUnitLayout joins them — the composer reads it off the PdfPrefs snapshot, so
    // without the key the sheet's own layout chips would change nothing on the page.
    // pdfOutputFont is a key for the same reason: the typeface reaches the ink through
    // OutputTypography.active, which is not snapshot state either; so are pdfThreadSlant and
    // pdfThreadDensity, which reach the hatch through ThreadHatchStyle. pdfArrowSizePt reaches the
    // composer only through the PdfPrefs snapshot, so it is a key like the dual-unit layout.
    LaunchedEffect(showPreview, spec, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   pdfUndercutLineArt, pdfUndercutLinerOutline, pdfUndercutSplitAtLinerEdge,
                   undercutRecord, blankDraft, pdfFractionStyle, pdfOutputFont, pdfThreadSlant, unitOverrides,
                   dualUnits, pdfDualUnitLayout, pdfThreadDensity, pdfArrowSizePt) {
        if (!showPreview) { previewBitmap = null; previewInkBand = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot     = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val displayUnitsSnapshot = DisplayUnits(unit, unitOverrides, dualUnits)
        val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
            jobNumber = jobNumber, side = shaftPosition, item = item)
        // The ink band is measured off the raw raster, on the same IO pass that produced it, so
        // a portrait page's blank paper is cropped by the band of THAT page.
        val (bmp, band) = withContext(Dispatchers.IO) {
            // The page shape follows the sheet's section count and the job's choice; both of its
            // inputs (`undercutRecord`, `blankDraft`, plus the geometry) are keys above.
            val orientation = undercutSheetOrientation(spec, resolvedComponents, undercutRecord, blankDraft)
            val raster = renderPdfPageBitmap(ctx, orientation = orientation) { page ->
                composeUndercutPdf(
                    page = page, spec = spec, project = projectSnapshot, unit = unit,
                    displayUnits = displayUnitsSnapshot,
                    pdfPrefs = prefsSnapshot, resolvedComponents = resolvedComponents,
                    undercutRecord = undercutRecord, lineThicknessScale = thicknessSnapshot,
                    blankValues = blankDraft,
                )
            }
            raster to raster?.inkBand()
        }
        previewBitmap = bmp?.asImageBitmap()
        previewInkBand = band
        previewLoading = false
    }

    // Capture colors before the Canvas block (DrawScope is not composable) — same technique
    // as WearRoute's overview canvas. Sheet ink is FIXED black, never theme onSurface: the
    // overview canvas is a paper-white sheet in every theme, and dark theme's near-white
    // onSurface would print invisible ink on it.
    // Component fills come from the user's Undercut Drawing style (Settings → Preview
    // Colors): fixed ink colors on the white sheet, never theme roles — a dark-theme tint
    // (near-white) would vanish into the paper and the pure-white notch voids would lose the
    // shade they read against. Defaults reproduce the historical fixed shades — grey liner,
    // white cut sections (on-device request); bodies/tapers stay lighter so the liner still
    // reads as the outer surface. Line art empties every fill.
    val outlineArgb    = Color.Black.toArgb()
    val bodyFillArgb   = undercutStyle.bodyFill().toArgb()
    val linerFillArgb  = undercutStyle.linerFill().toArgb()
    val hatchArgb      = Color.Black.copy(alpha = 0.55f).toArgb()
    val outlineColor   = Color.Black
    // The liner outline's fixed ink: sheet black, dimmed — a retrace, never a component edge.
    val linerOutlineColor = SheetInk.Outline.copy(alpha = LINER_OUTLINE_CANVAS_ALPHA)
    val tapTintColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val tapBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val badgeColor     = MaterialTheme.colorScheme.primary
    val badgeTextArgb  = MaterialTheme.colorScheme.onPrimary.toArgb()
    val previewShape   = MaterialTheme.shapes.medium

    val previewOpts = remember(outlineArgb, bodyFillArgb, linerFillArgb, hatchArgb, pdfThreadSlant, pdfThreadDensity) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = bodyFillArgb,
            taperFillColor      = bodyFillArgb,
            linerFillColor      = linerFillArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
            threadSlant         = pdfThreadSlant,
            threadDensity       = pdfThreadDensity,
        )
    }

    // ── Window width ────────────────────────────────────────────────────────
    // One skeleton decision for the whole tab; every block below is composed once and
    // called from both branches, so a phone and a tablet cannot drift apart.
    val widthClass = currentWindowWidthClass()
    val canvasModifier = sheetCanvasModifier(widthClass)

    val introText: @Composable ColumnScope.() -> Unit = {
        Text(
            text = "Record each cut — where it is, how long, and the diameter it was cut to. " +
                "Prints a zoomed drawing for the welder or machinist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ── Overview canvas — tap a cluster window to open its detail view ───────
    val canvasBlock: @Composable ColumnScope.() -> Unit = {
        if (oalMm > 0f) {
            Box(
                modifier = canvasModifier
                    // The tab's options panel stops here, so the overview it restyles stays in
                    // view above it.
                    .onGloballyPositioned { overviewBottomPx = it.boundsInRoot().bottom }
                    .clip(previewShape)
                    .background(Color.White)
                    .semantics {
                        contentDescription =
                            SheetSemantics.undercutOverview(undercutRecord.undercuts.size)
                    }
                    .pointerInput(spec, resolvedComponents, strips, linerSpans) {
                        detectTapGestures { tapOffset ->
                            val layout = ShaftLayout.compute(
                                spec               = spec,
                                leftPx             = 0f,
                                topPx              = 0f,
                                rightPx            = size.width.toFloat(),
                                bottomPx           = size.height.toFloat(),
                                marginPx           = 12.dp.toPx(),
                                resolvedComponents = resolvedComponents,
                            )
                            val tapMm = layout.xMmFromPx(tapOffset.x)
                            // A strip claims the tap first (it covers its cuts plus context);
                            // failing that, any liner opens as an empty strip to author in.
                            val hit = pickUndercutStripAt(tapMm, strips)
                                ?: pickLinerIdAtMm(
                                    tapMm,
                                    linerSpans.map { LinerSpanMm(it.id, it.startMm, it.endMm) },
                                )?.let { id -> linerSpans.firstOrNull { it.id == id } }
                                    ?.let { ln -> stripForLiner(ln) }
                            when (hit) {
                                is UndercutStrip.LinerStrip ->
                                    openOverlay(linerId = hit.linerId, undercutId = null, selectId = null)
                                is UndercutStrip.FreeStrip -> openOverlay(
                                    linerId = null,
                                    undercutId = hit.window.undercutIds.firstOrNull(),
                                    selectId = null,
                                )
                                null -> Unit
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val marginPx = 12.dp.toPx()
                    val layout = ShaftLayout.compute(
                        spec               = spec,
                        leftPx             = 0f,
                        topPx              = 0f,
                        rightPx            = size.width,
                        bottomPx           = size.height,
                        marginPx           = marginPx,
                        resolvedComponents = resolvedComponents,
                    )
                    with(ShaftRenderer) {
                        draw(spec, layout, previewOpts, resolvedComponents)
                    }
                    // Notches first (they erase profile strokes inside each cut), then the
                    // window tint + badge on top as the tap affordance.
                    drawUndercutNotches(
                        notches = notches,
                        xPx = { mm -> layout.xPx(mm) },
                        rPx = { dia -> layout.rPx(dia) },
                        cy = layout.centerlineYPx,
                        voidColor = Color.White,
                        outlineColor = outlineColor,
                        strokeWidthPx = 1.5f,
                        sectionFillColor = undercutStyle.sectionFill(),
                    )
                    if (pdfUndercutLinerOutline) {
                        drawLinerOutlines(
                            liners = linerOds,
                            notches = notches,
                            xPx = { mm -> layout.xPx(mm) },
                            rPx = { dia -> layout.rPx(dia) },
                            cy = layout.centerlineYPx,
                            color = linerOutlineColor,
                            strokeWidthPx = 1.dp.toPx(),
                        )
                    }
                    // No cut numbers on this whole-shaft overview: they are an input aid on the
                    // zoomed detail page only, and print on no sheet (on-device ruling). At shaft
                    // scale a liner's cuts sit a few pixels apart, so numbers here would pile up
                    // under the strip badge and name nothing.
                    drawUndercutStripAffordances(
                        layout = layout,
                        liners = linerSpans,
                        cutCountByLiner = cutCountByLiner,
                        windows = freeWindows,
                        segs = segs,
                        tapTintColor = tapTintColor,
                        tapBorderColor = tapBorderColor,
                        badgeColor = badgeColor,
                        badgeTextArgb = badgeTextArgb,
                    )
                }
            }
            Text(
                text = when {
                    linerSpans.isNotEmpty() ->
                        "Tap a liner — or any highlighted section — to zoom in and record cuts there."
                    freeWindows.isEmpty() ->
                        "No undercuts recorded yet — add one to start the drawing."
                    else ->
                        "Tap a highlighted section to open its zoomed detail view."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ── Preview / Options, within reach of the drawing ───────────────────────
    // The foot of the tab keeps its full Print / Preview / Export group; this row puts the two
    // inspection actions under the canvas they concern. Preview shares that group's gate.
    val previewOptionsRow: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showTabOptions = false; showPreview = true },
                enabled = gate.enabled,
                modifier = Modifier.weight(1f).testTag("undercut_tab_preview"),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview")
            }
            OutlinedButton(
                onClick = { showTabOptions = !showTabOptions },
                modifier = Modifier.weight(1f).testTag("undercut_tab_options"),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Options")
            }
        }
    }

    // "Add undercut" asks where the cut is and opens that strip on a pending card; it records
    // nothing itself (usability review: a cut recorded on tap would stay on the list and the sheet
    // when the machinist backs out, and a fixed bare-shaft seed would open the wrong strip for the
    // common cut, the one in a liner, with no liner datums to measure from).
    val addButton: @Composable ColumnScope.() -> Unit = {
        Button(
            onClick = { showAddWhere = true },
            enabled = oalMm > 0f,
            modifier = Modifier.fillMaxWidth().testTag("undercut_add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add undercut")
        }
    }

    // ── Recorded undercuts ───────────────────────────────────────────────────
    // Visibility + removal without hunting for the right strip first: every cut on the
    // shaft is listed here, aft → fwd, whichever strip it belongs to.
    val recordList: @Composable ColumnScope.() -> Unit = {
        if (undercutRecord.undercuts.isNotEmpty()) {
            HorizontalDivider()

            // Own spacing: the surrounding 16 dp rhythm would read as separate blocks
            // rather than one list.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Recorded undercuts",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                // Cuts with no measured Ø print without one — said here once, and on each row.
                if (missingDia.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp).testTag("undercut_missing_dia_note"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            undercutMissingDiaMessage(missingDia.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                val setPositions = undercutSetPositions(spec)
                val linerTitles = buildLinerTitleById(spec)
                // The one numbering (`undercutSheetNumbers`, AFT → FWD), so row N is the cut the
                // detail page labels N. Display order only — the record keeps its own.
                undercutRecord.undercuts
                    .sortedBy { sheetNumbers[it.id] ?: Int.MAX_VALUE }
                    .forEach { u ->
                        val number = sheetNumbers[u.id] ?: 0
                        UndercutListRow(
                            number = number,
                            undercut = u,
                            notPrinted = u.id in notPrintedCutIds,
                            unit = unit,
                            oalMm = oalMm,
                            linerSpans = linerSpans,
                            sheetSpans = spans,
                            linerTitles = linerTitles,
                            aftSetXMm = setPositions.first,
                            fwdSetXMm = setPositions.second,
                            onOpen = { anchorToUndercut(u.id) },
                            onDelete = { deleteFromList(u, number) },
                        )
                    }
            }
        }
    }

    // ── Drawing settings, collapsed under the list ───────────────────────────
    // How the sheet DRAWS the cuts, not what was cut: kept out of the authoring flow and folded
    // away by default (usability review — between Add and the list it would read as a required
    // step).
    val drawingSettings: @Composable ColumnScope.() -> Unit = {
        if (undercutRecord.undercuts.isNotEmpty()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showDrawingSettings = !showDrawingSettings }
                    .testTag("undercut_drawing_settings_toggle")
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Drawing settings",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showDrawingSettings) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showDrawingSettings) "Hide drawing settings" else "Show drawing settings",
                )
            }
            if (showDrawingSettings) {
                // Sheet-wide drawn-depth styling; the overview canvas restyles while dragging.
                // Display-only: the deepest cut draws at this fraction of its local surface Ø and
                // shallower cuts scale relative to it, so sheets with very different absolute
                // depths read alike; stored and printed Ø values never move (golden rule). The
                // canvas follows the drag from route-local state; the record takes ONE write on
                // release (PreviewTuning doctrine) — committing per frame would mark the document
                // dirty on every pixel of travel.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Per-job page shape: a fit for how many sections this sheet prints.
                    UndercutPageOrientationRow(
                        choice = undercutRecord.pageOrientation,
                        stripCount = sheetStripCount,
                        resolved = sheetOrientation,
                        onChoose = { vm.setUndercutPageOrientation(it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Per-job: which S.E.T. the strip titles and the lead at each strip's edge
                    // measure to.
                    UndercutSetDatumRow(
                        choice = undercutRecord.setDatum,
                        onChoose = { vm.setUndercutSetDatum(it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Beside the other dimensioning choice, not among the shading rows: it decides
                    // how an overhanging cut is DIMENSIONED, and under "Shade in Components" it
                    // could not be found (on-device report).
                    UndercutSplitAtLinerEdgeRow(
                        checked = pdfUndercutSplitAtLinerEdge,
                        onCheckedChange = { vm.setPdfUndercutSplitAtLinerEdge(it) },
                        testTag = "undercut_split_at_liner_edge_switch",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Drawn depth", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${(shownExaggeration * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = shownExaggeration,
                        onValueChange = { dragExaggeration = it },
                        onValueChangeFinished = {
                            dragExaggeration?.let { vm.setUndercutExaggeration(it) }
                            dragExaggeration = null
                        },
                        valueRange = 0f..UNDERCUT_EXAGGERATION_MAX_FRAC,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("undercut_exaggeration_slider"),
                    )
                    Text(
                        "Makes shallow cuts easier to see — 0% is true scale. Printed Ø values " +
                            "never change. Saved with this job.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Per-job fit, not a look: a liner strip near a shaft end draws out to the S.E.T.
                    // and its chain dimensions the run to it. The reach in the caption is the constant
                    // itself, so the text cannot drift from what the strips do.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Draw to the shaft end", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "A liner strip within " +
                                    formatLenWithUnit(UNDERCUT_SET_REACH_MM.toDouble(), unit) +
                                    " of its S.E.T. draws out to the shaft end and dimensions the run " +
                                    "to it. Saved with this job.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = undercutRecord.drawToSet,
                            onCheckedChange = { vm.setUndercutDrawToSet(it) },
                            modifier = Modifier.testTag("undercut_draw_to_set_switch"),
                        )
                    }
                    // App-wide look pref, offered here because the chain-rail rule is reached for from
                    // this tab (on-device request); the options-sheet and Settings rows are the same
                    // value.
                }
            }
        }
    }

    // ── Blank draft, export gate, Print / Preview / Export ───────────────────
    val printGroup: @Composable ColumnScope.() -> Unit = {
        // What prints, beside Print and never folded away — it changes the sheet (per job).
        UndercutPrintSectionsList(
            sections = printSections,
            onSetPrinted = { strip, printed -> vm.setUndercutSectionPrinted(strip, printed) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Job info and overall length are blanked; recorded undercuts are omitted — a fresh form.",
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
        } else if (noSectionSelected) {
            Text(
                UNDERCUT_NONE_SELECTED_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("undercut_none_selected_message"),
            )
        }

        DocumentActionButtons(
            documentName = "Undercut Drawing",
            onPrint = { guardMissingDia(export = false) { printUndercutDrawing() } },
            onPreview = { showPreview = true },
            onExport = {
                guardMissingDia(export = true) {
                    launcher.launchPicker(
                        buildOutputFilename(customer, vessel, jobNumber, shaftPosition, OutputDoc.UNDERCUT, blankDraft),
                        what = "undercut export",
                    )
                }
            },
            enabled = gate.enabled,
            printEnabled = outputEnabled,
        )
    }

    // ── Main UI ─────────────────────────────────────────────────────────────
    // A Box only so the list delete's snackbar can float over the tab body.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Document title strip ─────────────────────────────────────────────
            // Adding or editing an undercut here is unsaved work exactly like a spec edit,
            // so the asterisk belongs on this tab too.
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
                    text = "Undercut Drawing",
                    style = MaterialTheme.typography.titleLarge,
                    // Weighted so the actions are measured first: on a phone the title yields
                    // (ellipsized) rather than squeezing the trailing Save / Help icons to nothing.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                IconButton(
                    onClick = vm::undoEdit,
                    enabled = canUndo,
                    modifier = Modifier.testTag("undercut_undo"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = vm::redoEdit,
                    enabled = canRedo,
                    modifier = Modifier.testTag("undercut_redo"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("toolbar_save"),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save")
                }
                TabHelpButton(HELP_TOPIC_RECORD_UNDERCUT, onOpenHelpTopic)
            }

            HorizontalDivider()

            // ── Tab body ──────────────────────────────────────────────────────────
            // One column on a phone or a MEDIUM window; two panes on an EXPANDED one. The
            // blocks are the same composables either way (see their declarations above), so
            // the two skeletons can never drift apart.
            if (widthClass == WindowWidthClass.EXPANDED) {
                SheetTwoPane(
                    // The drawing and the actions that print it. The canvas is pinned out of
                    // the pane's scroll: the cut list opposite it reshapes what it draws.
                    pinnedCanvas = canvasBlock,
                    canvasPaneScrolling = {
                        previewOptionsRow()
                        introText()
                        printGroup()
                    },
                    // Authoring: adding cuts and listing them, with how the sheet draws them
                    // folded away under the list.
                    controlsPane = {
                        addButton()
                        recordList()
                        drawingSettings()
                    },
                )
            } else {
                // ── Scrollable content ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    introText()
                    canvasBlock()
                    previewOptionsRow()
                    addButton()
                    recordList()
                    drawingSettings()

                    HorizontalDivider()

                    printGroup()
                }
            }
        }

        // The list delete's Undo, at the bottom of the tab clear of the top bar.
        SnackbarHost(
            hostState = listSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("undercut_list_delete_snackbar"),
        )

        // The undercut preview's options panel over the tab — non-modal, closed by its close
        // button, the Options button (a toggle) or Back. The overview canvas above it restyles
        // where it draws what an option changes (the liner outline).
        if (showTabOptions) {
            // Capped like the detail page's panel: the screen below the overview canvas, less the
            // panel's header row and the navigation-bar inset, so the drawing whose look these
            // options change stays in view (on-device report: the plain 78% cap covered it on a
            // phone). Before the canvas is measured the ceiling applies.
            val tabOptionsMaxHeightDp = undercutDetailOptionsMaxHeightDp(
                screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat(),
                drawingBottomDp = with(LocalDensity.current) { overviewBottomPx.toDp().value },
                chromeDp = TUNING_SHEET_CHROME_DP +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value,
            )
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                PreviewOptionsPanel(
                    onClose = { showTabOptions = false },
                    maxHeightDp = tabOptionsMaxHeightDp.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    UndercutOptionsSheet(
                        showThreadControls = sheetDrawsThread,
                        vm = vm,
                        blankDraft = blankDraft,
                        onSetBlankDraft = { blankDraft = it },
                    )
                }
            }
        }
    }

    // ── "Print without diameters?" ───────────────────────────────────────────
    // Cuts placed but never measured print with no Ø; the machinist decides whether that sheet
    // goes out. Cancel, back and tap-outside all leave nothing printed or exported.
    missingDiaAsk?.let { ask ->
        AlertDialog(
            onDismissRequest = { missingDiaAsk = null },
            modifier = Modifier.testTag("undercut_missing_dia_dialog"),
            title = { Text("Print without diameters?") },
            text = { Text(undercutMissingDiaMessage(ask.count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        missingDiaAsk = null
                        ask.action()
                    },
                    modifier = Modifier.testTag("undercut_missing_dia_proceed"),
                ) {
                    Text(if (ask.export) "Export anyway" else "Print anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { missingDiaAsk = null },
                    modifier = Modifier.testTag("undercut_missing_dia_cancel"),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── "Where is the cut?" — the add chooser ────────────────────────────────
    // One row per liner (AFT → FWD, named as the detail page names its strip), then bare shaft from
    // either S.E.T. Each row opens that strip on a pending card; Cancel, Back and tap-outside open
    // nothing and record nothing.
    if (showAddWhere) {
        val addLinerTitles = remember(spec, unit, unitOverrides, dualUnits) {
            undercutAddLinerTitles(spec, DisplayUnits(unit, unitOverrides, dualUnits))
        }
        val choices = remember(linerSpans, addLinerTitles) { undercutAddChoices(linerSpans, addLinerTitles) }
        AlertDialog(
            onDismissRequest = { showAddWhere = false },
            modifier = Modifier.testTag("undercut_add_where"),
            title = { Text("Where is the cut?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        ListItem(
                            headlineContent = { Text(choice.label) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    showAddWhere = false
                                    when (choice) {
                                        is UndercutAddChoice.InLiner -> openOverlay(
                                            linerId = choice.linerId, undercutId = null, selectId = null,
                                            seedNewCut = true,
                                        )
                                        is UndercutAddChoice.BareShaft -> openBareShaftAdd(choice.fromAft)
                                    }
                                }
                                .testTag(choice.testTag),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAddWhere = false },
                    modifier = Modifier.testTag("undercut_add_where_cancel"),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Undercut strip detail overlay ─────────────────────────────────────────
    // UndercutWindowDetailOverlay hosts its own BackHandler; only compose it while a strip
    // is active.
    activeStrip?.let { s ->
        UndercutWindowDetailOverlay(
            strip = s,
            stripKey = overlayKey ?: anchorLinerId?.let { "liner:$it" } ?: "free:$anchorUndercutId",
            initialSelectedId = overlayInitialSelectedId,
            // The add chooser's opening: a pending "New undercut" card, seeded by the overlay's
            // own add path on first composition; nothing reaches the record until it is confirmed.
            seedNewCut = seedNewCutOnOpen,
            spec = spec,
            resolvedComponents = resolvedComponents,
            unit = unit,
            undercutRecord = undercutRecord,
            style = undercutStyle,
            threadSlant = pdfThreadSlant,
            threadDensity = pdfThreadDensity,
            onAddUndercut = { startMm, lengthMm, reference, referenceLinerId, referenceCutId ->
                // Called when the overlay CONFIRMS a drafted new cut (a cancelled draft never
                // reaches here, so the record gains no ghosts). The returned id lets the overlay
                // land the draft's Ø/note and follow the new card. A liner or recorded-cut anchor
                // already covers this strip; the bare-shaft add's synthetic window has no anchor,
                // so the new cut becomes it, and `activeStrip` moves to the real strip holding it
                // under the same overlay identity — drafts and selection carry over. These state
                // writes recompose later, so the overlay's follow-up Ø/note update still runs.
                val newId = vm.addUndercut(startMm, lengthMm, reference, referenceLinerId, referenceCutId)
                if (bareShaftAddFromAft != null && anchorLinerId == null && anchorUndercutId == null) {
                    anchorUndercutId = newId
                }
                // The open seed is spent; a recreation must not seed a second pending card.
                seedNewCutOnOpen = false
                newId
            },
            onUpdateUndercut = vm::updateUndercut,
            onUpdateReference = vm::updateUndercutReference,
            onRemoveUndercut = { id ->
                // A liner strip stays open on its liner regardless. On a free strip, keep the
                // overlay anchored when a sibling is left behind; deleting the last member
                // closes it via the anchor-resolution above.
                if (anchorLinerId == null && id == anchorUndercutId) {
                    anchorUndercutId = s.undercutIds.firstOrNull { it != id }
                }
                vm.removeUndercut(id)
            },
            onClose = { openOverlay(linerId = null, undercutId = null, selectId = null) },
            onRestoreUndercut = vm::restoreUndercut,
            linerOutline = pdfUndercutLinerOutline,
            displayUnits = DisplayUnits(unit, unitOverrides, dualUnits),
            splitAtLinerEdge = pdfUndercutSplitAtLinerEdge,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = vm::undoEdit,
            onRedo = vm::redoEdit,
            arrowSizePt = pdfArrowSizePt,
            // The tab's own preview, opened over this page once its cards are settled.
            onPreview = { showPreview = true },
            previewEnabled = gate.enabled,
            // The undercut preview's options, in the same panel, over this page. Blank draft is
            // the tab's own state either way, so the two can never disagree.
            optionsSheet = {
                UndercutOptionsSheet(
                    showThreadControls = sheetDrawsThread,
                    vm = vm,
                    blankDraft = blankDraft,
                    onSetBlankDraft = { blankDraft = it },
                )
            },
        )
    }

    // ── Undercut drawing preview ──────────────────────────────────────────────
    // After the detail overlay, so a preview opened from the detail page's top bar draws OVER it.
    // Its Back handler is composed only while it is open, so it registers after the detail
    // overlay's and wins: Back closes the preview back to the detail page.
    if (showPreview) {
        BackHandler { showPreview = false }
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Undercut Drawing Preview",
            onClose = { showPreview = false },
            onExport = {
                guardMissingDia(export = true) {
                    showPreview = false
                    launcher.launchPicker(
                        buildOutputFilename(customer, vessel, jobNumber, shaftPosition, OutputDoc.UNDERCUT, blankDraft),
                        what = "undercut export",
                    )
                }
            },
            // The tab body's Print action, unchanged — one function behind both, behind the same
            // missing-Ø question.
            onPrint = { guardMissingDia(export = false) { printUndercutDrawing() } },
            // Every section left off: the page previews its note, but there is nothing to print.
            actionsEnabled = !noSectionSelected,
            optionsSheet = {
                // Same blank-draft state as the tab body's switch — ONE state behind both, so
                // the tab and the sheet can never disagree about what the preview is showing.
                UndercutOptionsSheet(
                    showThreadControls = sheetDrawsThread,
                    vm = vm,
                    blankDraft = blankDraft,
                    onSetBlankDraft = { blankDraft = it },
                )
            },
            // The panel's controls redraw this page, so it must not cover it: the plain cap
            // over the centered full-size page left the page behind the panel and the changes
            // unwatchable (on-device report). Each control commits on release and re-renders
            // the page under the open panel's strip — the wear sheet's posture.
            sheetTunesPage = true,
            inkBand = previewInkBand,
        )
    }
}

/** Default length of a newly added undercut (1 in), clamped to the remaining shaft extent. */
internal const val DEFAULT_UNDERCUT_LENGTH_MM = UNDERCUT_DEFAULT_LENGTH_MM

/** One row of the "Where is the cut?" add chooser. */
internal sealed class UndercutAddChoice {
    abstract val label: String
    abstract val testTag: String

    /** The cut is in this liner: its strip opens on a pending card with the liner's datums. */
    data class InLiner(val linerId: String, override val label: String) : UndercutAddChoice() {
        override val testTag: String get() = "undercut_add_where_liner_$linerId"
    }

    /** The cut is on bare shaft, measured from the AFT ([fromAft]) or the FWD S.E.T. */
    data class BareShaft(val fromAft: Boolean) : UndercutAddChoice() {
        override val label: String
            get() = if (fromAft) "Bare shaft — from the AFT S.E.T." else "Bare shaft — from the FWD S.E.T."
        override val testTag: String
            get() = if (fromAft) "undercut_add_where_aft" else "undercut_add_where_fwd"
    }
}

/**
 * The add chooser's rows: one per liner, AFT → FWD by span start, titled from [linerTitles]
 * (a liner missing from it reads "Liner N" by its AFT → FWD position), then bare shaft from the
 * AFT S.E.T. and from the FWD S.E.T. — always both, whatever the shaft carries.
 */
internal fun undercutAddChoices(
    linerSpans: List<UndercutLinerSpan>,
    linerTitles: Map<String, String>,
): List<UndercutAddChoice> =
    linerSpans.sortedBy { it.startMm }.mapIndexed { i, ln ->
        UndercutAddChoice.InLiner(ln.id, linerTitles[ln.id] ?: "Liner ${i + 1}")
    } + UndercutAddChoice.BareShaft(fromAft = true) + UndercutAddChoice.BareShaft(fromAft = false)

/**
 * Every liner's chooser title: its positional name (`buildLinerTitleById`) followed by its OD in
 * the liner's own display unit — the detail page's strip title, so the row tapped and the page it
 * opens read alike.
 */
internal fun undercutAddLinerTitles(spec: ShaftSpec, displayUnits: DisplayUnits): Map<String, String> =
    buildLinerTitleById(spec).mapValues { (id, name) ->
        val odMm = spec.liners.firstOrNull { it.id == id }?.odMm?.takeIf { it > 0f }
        buildUndercutStripTitle(
            name, "",
            odMm?.let { "Ø" + formatDiaWithUnit(it.toDouble(), displayUnits.unitFor(id)) },
        )
    }

/**
 * The window a bare-shaft add opens on before anything is recorded there: a [lengthMm] span just
 * inside the chosen S.E.T. (AFT: starting at [aftSetMm]; FWD: ending at [fwdSetMm]), kept inside
 * `[0, oalMm]`, padded by up to [padMm] — the SAME pad on both sides, shrunk to what the nearer
 * shaft end allows. The overlay seeds its pending card centred in the window's free stock, so a
 * symmetric pad is what lands that card exactly on the span (Distance 0 from its S.E.T.); a pad
 * clamped on one side only would shift it off the S.E.T. No member cuts; the overlay's own preview
 * range adds the break room and the pad around the pending cut.
 */
internal fun undercutBareShaftAddStrip(
    fromAft: Boolean,
    aftSetMm: Float,
    fwdSetMm: Float,
    oalMm: Float,
    lengthMm: Float = DEFAULT_UNDERCUT_LENGTH_MM,
    padMm: Float = UNDERCUT_WINDOW_PAD_MM,
): UndercutStrip.FreeStrip {
    val hi = oalMm.coerceAtLeast(0f)
    val len = lengthMm.coerceIn(0f, hi)
    val maxStart = (hi - len).coerceAtLeast(0f)
    val start = (if (fromAft) aftSetMm else fwdSetMm - len).coerceIn(0f, maxStart)
    val end = start + len
    val pad = minOf(padMm, start, hi - end).coerceAtLeast(0f)
    return UndercutStrip.FreeStrip(UndercutWindow(start - pad, end + pad, emptyList()))
}

/** A Print ([export] false) or Export waiting on "Print without diameters?" — [count] cuts lack a Ø. */
private class UndercutMissingDiaAsk(val count: Int, val export: Boolean, val action: () -> Unit)

/**
 * The undercut drawing's PDF options — ONE configuration of the shared [RunoutWearOptionsSheet]
 * behind every surface that opens it: the preview's Tune panel, the tab's "Options" button and the
 * detail page's Options icon. Every row is a remote control for the one app-wide `PdfPrefs` value
 * (or the document's own dual-units switch), except the per-job "Page" row, which writes this job's
 * `UndercutRecord.pageOrientation` and says so; the three surfaces can never disagree. [blankDraft]
 * is the tab's session-only write-in election.
 */
@Composable
internal fun UndercutOptionsSheet(
    vm: ShaftViewModel,
    blankDraft: Boolean,
    onSetBlankDraft: (Boolean) -> Unit,
    /**
     * Whether this sheet draws a thread ([undercutStripsReachThread], or the whole-shaft profile on
     * a threaded shaft) — the Thread section shows only then, since its sliders tune nothing else
     * here (on-device request: fewer controls).
     */
    showThreadControls: Boolean,
) {
    val spec by vm.spec.collectAsState()
    val dualUnits by vm.dualUnits.collectAsState()
    val pdfDualUnitLayout by vm.pdfDualUnitLayout.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()
    val pdfUndercutLineArt by vm.pdfUndercutLineArt.collectAsState()
    val pdfUndercutLinerOutline by vm.pdfUndercutLinerOutline.collectAsState()
    val pdfUndercutSplitAtLinerEdge by vm.pdfUndercutSplitAtLinerEdge.collectAsState()
    val pdfFractionStyle by vm.pdfFractionStyle.collectAsState()
    val pdfThreadSlant by vm.pdfThreadSlant.collectAsState()
    val pdfThreadDensity by vm.pdfThreadDensity.collectAsState()
    val pdfArrowSizePt by vm.pdfArrowSizePt.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val undercutRecord by vm.undercutRecord.collectAsState()
    val unitOverrides by vm.unitOverrides.collectAsState()
    val docUnit by vm.unit.collectAsState()
    // The print path's own count and page (blank draft included), for the Page row's caption.
    val stripCount = remember(spec, resolvedComponents, undercutRecord, blankDraft) {
        undercutSheetStripCount(spec, resolvedComponents, undercutRecord, blankDraft)
    }
    val printSections = remember(spec, resolvedComponents, undercutRecord, blankDraft, docUnit, unitOverrides, dualUnits) {
        undercutPrintSections(
            spec, resolvedComponents, undercutRecord, blankDraft, DisplayUnits(docUnit, unitOverrides, dualUnits),
        )
    }
    RunoutWearOptionsSheet(
        dualUnits = dualUnits,
        onDualUnitsChange = { vm.setDualUnits(it) },
        dualUnitLayout = pdfDualUnitLayout,
        lineThicknessScale = lineThicknessScale,
        pdfShadedBodies = pdfShadedBodies,
        pdfShadedTapers = pdfShadedTapers,
        pdfShadedLiners = pdfShadedLiners,
        // This document's profile shades every body run whatever the explicit-only pref says,
        // so its row is hidden here rather than shown as a checkbox the page ignores.
        showShadeExplicitBodiesOnly = false,
        // The one document `PdfPrefs.undercutLineArt` reaches, so the row is offered here and
        // nowhere else.
        showUndercutLineArt = true,
        undercutLineArt = pdfUndercutLineArt,
        undercutLinerOutline = pdfUndercutLinerOutline,
        vm = vm,
        // The strip rails' heads follow `PdfPrefs.arrowSizePt` (`drawUndercutRail`), on the
        // printed strips and the detail canvas alike.
        showDimensionArrows = true,
        arrowSizePt = pdfArrowSizePt,
        fractionStyle = pdfFractionStyle,
        hasThreads = showThreadControls,
        threadSlant = pdfThreadSlant,
        threadDensity = pdfThreadDensity,
        blankDraft = blankDraft,
        onSetBlankDraft = onSetBlankDraft,
        undercutPageOrientation = undercutRecord.pageOrientation,
        undercutStripCount = stripCount,
        undercutResolvedOrientation = resolveUndercutPageOrientation(undercutRecord.pageOrientation, stripCount),
        undercutContentRows = {
            Spacer(Modifier.height(12.dp))
            UndercutSetDatumRow(
                choice = undercutRecord.setDatum,
                onChoose = { vm.setUndercutSetDatum(it) },
            )
            Spacer(Modifier.height(12.dp))
            // With the S.E.T. choice, where a dimensioning option is looked for — not in the
            // shading section (on-device report: it could not be found there).
            UndercutSplitAtLinerEdgeRow(
                checked = pdfUndercutSplitAtLinerEdge,
                onCheckedChange = { vm.setPdfUndercutSplitAtLinerEdge(it) },
                testTag = "pdf_undercut_split_at_liner_edge",
            )
            if (printSections.size >= 2) {
                Spacer(Modifier.height(12.dp))
                UndercutPrintSectionsList(
                    sections = printSections,
                    onSetPrinted = { strip, printed -> vm.setUndercutSectionPrinted(strip, printed) },
                )
            }
        },
    )
}

private fun openUndercutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching {
            context.grantUriPermission(
                ri.activityInfo?.packageName ?: return@forEach, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Undercut list row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One recorded undercut, summarized: its [number] (`undercutSheetNumbers`, the number the detail
 * page shows inside its section — an input aid, never printed), its distance under the reference it was authored against (with that
 * reference's short tag), its length, and its Ø — "—" while unmeasured, with a "Ø not entered" note
 * under it, since such a cut prints with no diameter. A cut whose span no longer fits the shaft
 * (`isUndercutStaleOverrun`) leads with a warning icon, the same non-blocking classifier the card
 * shows in full.
 *
 * The whole row opens the cut's strip; the trailing icon deletes it outright — confirm-free, with a
 * snackbar Undo from the caller. Neither path touches canonical values.
 */
@Composable
private fun UndercutListRow(
    number: Int,
    undercut: Undercut,
    /** The cut sits in a section the job leaves off the printout ("Sections to print"). */
    notPrinted: Boolean = false,
    unit: UnitSystem,
    oalMm: Float,
    linerSpans: List<UndercutLinerSpan>,
    /** Every cut on the sheet, render-clamped — what can bury a liner-edge reference. */
    sheetSpans: List<UndercutSpanMm>,
    linerTitles: Map<String, String>,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val reference = effectiveUndercutReference(undercut, linerSpans, sheetSpans, aftSetXMm, fwdSetXMm)
    val refLiner = undercutReferenceLinerFor(undercut, linerSpans, stripLiner = null, oalMm = oalMm)
    val refParent = undercutReferenceParentSpan(undercut, sheetSpans)
    val distanceMm = undercutDisplayedDistanceMm(
        undercut, reference, refLiner, aftSetXMm, fwdSetXMm, refParent = refParent,
    )
    val stale = isUndercutStaleOverrun(undercut.startFromAftMm, undercut.lengthMm, oalMm)

    // A liner reference names its liner, so two cuts in different liners never read alike.
    val referenceTag = when (reference) {
        UndercutReference.LINER_AFT, UndercutReference.LINER_FWD -> {
            val title = refLiner?.id?.let { linerTitles[it] }
            val edge = if (reference == UndercutReference.LINER_AFT) "AFT" else "FWD"
            if (title != null) "$title $edge edge" else undercutReferenceLabel(reference)
        }
        else -> undercutReferenceLabel(reference)
    }
    val diaText = if (undercut.diaMm > 0f) disp(undercut.diaMm, unit) + abbr(unit) else "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .testTag("undercut_row_${undercut.id}")
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stale) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Extends past shaft end",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$number. ${disp(distanceMm, unit)}${abbr(unit)} from $referenceTag",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Length ${disp(undercut.lengthMm, unit)}${abbr(unit)}  ·  Ø $diaText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (notPrinted) {
                Text(
                    text = "Not on the printout",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("undercut_row_not_printed_${undercut.id}"),
                )
            }
            if (undercut.diaMm <= 0f) {
                Text(
                    text = "Ø not entered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("undercut_row_no_dia_${undercut.id}"),
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("undercut_delete_${undercut.id}"),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete undercut $number")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Strip tap affordances — drawn after ShaftRenderer.draw() and the notches
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw a faint tint + border over every tap target — **every liner** (whether or not it holds
 * cuts) and every bare-shaft cluster window — plus a small count badge carrying how many cuts
 * that target holds. Purely a rendering overlay: reads the spans but never mutates the record.
 *
 * Liners are unconditional targets so an empty one can be zoomed and authored in, matching the
 * wear document; the free window remains the target for cuts that belong to no liner, since
 * undercuts are not component-bound. A liner with no cuts gets no badge (nothing to count).
 */
private fun DrawScope.drawUndercutStripAffordances(
    layout: ShaftLayout.Result,
    liners: List<UndercutLinerSpan>,
    cutCountByLiner: Map<String, Int>,
    windows: List<UndercutWindow>,
    segs: List<SurfaceSeg>,
    tapTintColor: Color,
    tapBorderColor: Color,
    badgeColor: Color,
    badgeTextArgb: Int,
) {
    if (liners.isEmpty() && windows.isEmpty()) return

    val badgePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = badgeTextArgb
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 24f
    }
    val badgeRadiusPx = 11.dp.toPx()
    val badgeGapPx = 4.dp.toPx()

    fun target(startMm: Float, endMm: Float, count: Int) {
        val x0 = layout.xPx(startMm)
        val x1 = layout.xPx(endMm)
        val r = layout.rPx(maxOuterDiaOver(segs, startMm, endMm)).coerceAtLeast(4.dp.toPx())
        val top = layout.centerlineYPx - r
        val bot = layout.centerlineYPx + r

        drawRect(color = tapTintColor, topLeft = Offset(x0, top), size = Size(x1 - x0, bot - top))
        drawRect(
            color = tapBorderColor,
            topLeft = Offset(x0, top),
            size = Size(x1 - x0, bot - top),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        if (count <= 0) return
        val cx = (x0 + x1) / 2f
        val cy = (top - badgeGapPx - badgeRadiusPx).coerceAtLeast(badgeRadiusPx)
        drawCircle(color = badgeColor, radius = badgeRadiusPx, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.drawText(
            "$count", cx, cy + badgeRadiusPx * 0.35f, badgePaint,
        )
    }

    liners.forEach { ln -> target(ln.startMm, ln.endMm, cutCountByLiner[ln.id] ?: 0) }
    windows.forEach { w -> target(w.startMm, w.endMm, w.undercutIds.size) }
}
