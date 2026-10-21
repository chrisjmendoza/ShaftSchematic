// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutDetail.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.UNDERCUT_BREAK_AMP_FRAC_OF_R
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutNotches
import com.android.shaftschematic.geom.buriedUndercutLinerEdgesMm
import com.android.shaftschematic.geom.canonicalToUndercutStartMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.effectiveUndercutReference
import com.android.shaftschematic.geom.isUndercutStaleOverrun
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.nearestSetReference
import com.android.shaftschematic.geom.UNDERCUT_SPAN_EPS_MM
import com.android.shaftschematic.geom.UNDERCUT_TOUCH_EPS_MM
import com.android.shaftschematic.geom.clampUndercutExtension
import com.android.shaftschematic.geom.maxUndercutAftExtensionMm
import com.android.shaftschematic.geom.maxUndercutFwdExtensionMm
import com.android.shaftschematic.geom.newLinerOverhangs
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.pickUndercutAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.resolveUndercutFloors
import com.android.shaftschematic.geom.undercutAddSeedSpan
import com.android.shaftschematic.geom.undercutCalloutAnchorsMm
import com.android.shaftschematic.geom.undercutNextCutSeed
import com.android.shaftschematic.geom.undercutCanonicalForNewLength
import com.android.shaftschematic.geom.undercutIsWholeLiner
import com.android.shaftschematic.geom.undercutLinerEdgeBuried
import com.android.shaftschematic.geom.undercutLinerExtensionsMm
import com.android.shaftschematic.geom.undercutOverlapIssue
import com.android.shaftschematic.geom.undercutPartialOverlapsMm
import com.android.shaftschematic.geom.undercutPreviewDrawRange
import com.android.shaftschematic.geom.undercutReferenceLabel
import com.android.shaftschematic.geom.undercutReferenceLinerFor
import com.android.shaftschematic.geom.undercutSetPositions
import com.android.shaftschematic.geom.undercutSpanIssue
import com.android.shaftschematic.geom.undercutSpanWithAftExtension
import com.android.shaftschematic.geom.undercutSpanWithFwdExtension
import com.android.shaftschematic.geom.undercutStartAtEndOf
import com.android.shaftschematic.geom.undercutGapAfterMm
import com.android.shaftschematic.geom.undercutGapBeforeMm
import com.android.shaftschematic.geom.undercutEndAtStartOf
import com.android.shaftschematic.geom.undercutOverlapMessage
import com.android.shaftschematic.geom.undercutOverlapPartnerId
import com.android.shaftschematic.geom.isParentUndercutReference
import com.android.shaftschematic.geom.undercutParentCandidate
import com.android.shaftschematic.geom.undercutReferenceParentSpan
import com.android.shaftschematic.geom.undercutStartToCanonicalMm
import com.android.shaftschematic.geom.shoulderEndMm
import com.android.shaftschematic.geom.threadHatchSpan
import com.android.shaftschematic.geom.threadReliefSpan
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.pdf.formatDiaWithUnit
import com.android.shaftschematic.pdf.buildUndercutStripTitle
import com.android.shaftschematic.geom.undercutSheetNumbers
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.pdf.UNDERCUT_RAIL_WITNESS_GAP_PT
import com.android.shaftschematic.pdf.UNDERCUT_TOTAL_RAIL_ABOVE_PT
import com.android.shaftschematic.pdf.UNDERCUT_TOTAL_RAIL_BAND_PT
import com.android.shaftschematic.pdf.UndercutStripRails
import com.android.shaftschematic.pdf.drawUndercutStripRails
import com.android.shaftschematic.pdf.planUndercutStripRails
import com.android.shaftschematic.pdf.undercutRailRowHeightPt
import com.android.shaftschematic.util.OutputTypography
import com.android.shaftschematic.util.measureDualLabel
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.UndercutStyle
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.ui.input.NumericInputField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * UndercutDetail
 *
 * A full-screen "zoom in" overlay on ONE undercut **detail strip** — the drawing the shop
 * actually reads (see `docs/contracts/UndercutDrawing.md`). Where the wear overlay breaks out one
 * *component*, this one breaks out an axial *strip*, because undercuts are not bound to
 * components. `geom/UndercutMath.kt`'s [UndercutStrip] supplies the two kinds:
 * - [UndercutStrip.LinerStrip] — the cuts live in a liner (or the machinist zoomed an
 *   undercut-free liner to author one). The draw range covers the **whole liner** plus any cut
 *   overhang past its edges, so the liner's real edges are always visible (on-device report: a
 *   grey slab with no liner edges was unreadable), and the chain rail anchors on those edges.
 * - [UndercutStrip.FreeStrip] — bare-shaft cuts: the padded cluster window, chain anchored at
 *   the window edges.
 *
 * Contents, top to bottom:
 * - a **top bar** — back, the strip title, Undo / Redo, and the drawing's **Preview** and
 *   **Drawing options** ([onPreview], [optionsSheet]; on-device request: both within reach of the
 *   zoomed page). Preview settles every card the way the back arrow does and then opens the tab's
 *   PDF preview over this page; Options opens the undercut options panel over it.
 * - a **fixed canvas** — a **dimension rail above**, the printed strip's rails by construction
 *   (its ONE planner `planUndercutStripRails` and draw sequence `drawUndercutStripRails` at canvas
 *   scale: the total on the upper line, the chain across the strip's chain range over the
 *   top-level cuts, a row under it per nesting level against the parent's shoulders; nothing
 *   outside the chain range is labelled, so the drawing never dimensions the arbitrary pad); the
 *   **strip profile** with the undercut **notches** cut
 *   into it (`geom/SurfaceProfileMath.kt` via [buildUndercutNotches], the identical pipeline the
 *   PDF composer uses, so the two draw the same notch by construction); and **Ø callouts below**
 *   through the shared `planDiaCallouts` engine — one station per undercut at its axial centre,
 *   leader down to the notch floor. A Ø-less undercut (`diaMm == 0`) shows "—" here so it stays
 *   findable, and never prints.
 * - a **card carousel** below it, one page per cut, aft → fwd — the `ComponentCarouselPager`
 *   presentation (a [HorizontalPager] with a neighbour peek, swipe to change card). The canvas
 *   never scrolls out from under the fields (on-device report: a vertical card stack forced
 *   scrolling between the drawing and the numbers).
 *
 * **Draft editing.** A card edits a LOCAL [UndercutDraft], not the record: field commits (blur,
 * per `docs/contracts/NumberField.md`), the "Measure From" chips, and the note all land in the draft, and
 * the canvas previews the SELECTED card's draft in place of its stored notch. Nothing reaches
 * `UndercutRecord` until the draft is **confirmed**, which requires it to differ from stored AND
 * to clear both blocking checks — [undercutSpanIssue] (shaft bounds) and [undercutOverlapIssue]
 * (no intruding into another cut's bounds). Because page order is keyed on **stored** starts,
 * cards reorder only on confirm — and the carousel then follows the confirmed cut to its new
 * aft → fwd position. "Add undercut" opens a **draft-only** page: it previews and orders like any
 * other card but enters the record only on confirm, so a discarded add leaves no ghost cut behind.
 *
 * **Saving is a floating pill, not card buttons** ([UndercutStatusPill], pinned at the boundary
 * between the canvas and the carousel so it can never scroll out of reach — on-device report: per-card
 * Confirm/Cancel buttons buried in the card's own scroll were missed). It states the selected card's
 * save state: *Recorded* (nothing dirty), *Record cut* / *Confirm change* (dirty and clear — a new
 * cut or an edit to a recorded one; one tap commits, staying on the card), or the blocking reason
 * (dirty and blocked, not confirmable); the last two also carry a discard (✕).
 *
 * **Leaving a card saves it.** A dirty draft that clears the confirm check commits by itself,
 * through the identical [confirmDraft] path (values verbatim — golden rule), when the machinist
 * leaves its card: swiping to another page, tapping another notch, or closing the overlay. A
 * BLOCKED draft is never silently committed and never silently dropped — the leave raises an
 * AlertDialog stating the blocking reason, with **Keep editing** (return to the card, cancelling
 * the close) and **Discard** (drop the draft and go). An "Add undercut" card left exactly as it
 * was seeded is DISCARDED, never committed: the machinist typed nothing, and committing the seeded
 * default span recorded stray cuts nobody authored (on-device report). Only the pill's explicit
 * Confirm records an untouched add. [undercutLeaveAction] is that decision.
 *
 * Selection and paging are one thing: swiping a card highlights its notch, and tapping a notch
 * pages the carousel to its card. A swipe changes the selection only once the pager SETTLES (a
 * half-way drag selects nothing and leaves nothing), and a selection pages the carousel with an
 * immediate jump, never an animation — an animated scroll passes through every intermediate page,
 * and adopting one of those would cancel the scroll half-way between two cards, leaving on screen
 * a trash that belongs to a card other than the selected one (on-device report). The trash acts
 * only on the selected, settled card.
 *
 * **Undo.** The top bar's Undo / Redo walk the document's ONE history. A card's delete stays
 * confirm-free but raises an "Undercut deleted" snackbar whose Undo puts that exact cut back
 * ([onRestoreUndercut]) and returns the carousel to it — the cut, not a history step, because the
 * history coalesces an edit made just before the delete into the same step (on-device report: a
 * cut deleted to restart it was lost with no way back).
 *
 * Same posture as [ComponentWearDetailOverlay]: a plain composable, not a nav destination,
 * dismissed via its own [BackHandler] or the back arrow, with pinch-to-zoom (0.5×–6×) +
 * two-finger pan whose transform the tap handler inverts, so placement/hit-testing always runs in
 * untransformed canvas space. The Canvas renderer and the tap handler share one layout
 * ([computeUndercutWindowLayout]) so a tapped undercut is the one that was drawn.
 *
 * Coordinate rule: everything here is canonical **shaft space** (mm from the AFT face) — undercuts
 * have no component key, so there is no component-local space to convert through. Only the
 * "Distance" field is re-projected, against the authored reference — the two S.E.T.s plus the
 * reference liner's edges ([canonicalToUndercutStartMm]/[undercutStartToCanonicalMm]).
 */
@Composable
fun UndercutWindowDetailOverlay(
    strip: UndercutStrip,
    /**
     * The open overlay's STABLE identity, fixed by the host when it opens the overlay (a liner
     * strip is its liner; a free strip the cut it was opened on). Strip VALUES carry member ids
     * and a draw range, so they change on every record edit, and a key derived from the members
     * changes on an add, delete, merge or split — wiping the drafts, the selection and the open
     * page those edits came from. Every piece of per-strip state here is keyed on it.
     */
    stripKey: String,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    undercutRecord: UndercutRecord,
    style: UndercutStyle = UndercutStyle(),
    /**
     * App-wide thread-hatch slant factor (`PdfPrefs.threadSlant`) for threaded spans in the
     * strip — the value the printed sheet's hatch reads, passed in so a change recomposes this
     * canvas.
     */
    threadSlant: Float = THREAD_SLANT_DEFAULT,
    /**
     * App-wide thread-hatch density (`PdfPrefs.threadDensity`) for the same spans — passed in for
     * the same reason as [threadSlant].
     */
    threadDensity: Float = THREAD_DENSITY_DEFAULT,
    onAddUndercut: (
        startFromAftMm: Float,
        lengthMm: Float,
        reference: UndercutReference,
        referenceLinerId: String,
        referenceCutId: String,
    ) -> String,
    onUpdateUndercut: (id: String, startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) -> Unit,
    onUpdateReference: (
        id: String,
        reference: UndercutReference,
        referenceLinerId: String,
        referenceCutId: String,
    ) -> Unit,
    onRemoveUndercut: (id: String) -> Unit,
    onClose: () -> Unit,
    /**
     * Put a just-deleted cut back verbatim at its prior record [index] — the delete snackbar's
     * Undo (`ShaftViewModel.restoreUndercut`). Restores the cut itself, never a history step.
     */
    onRestoreUndercut: (u: Undercut, index: Int) -> Unit = { _, _ -> },
    /** The ONE document history (`ShaftViewModel.canUndo`/`undoEdit`), shown in the top bar. */
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    /**
     * The cut to open on (a "Recorded undercuts" row opens its own card, not the strip's first);
     * null opens on the aft-most card. A cut that is not on this strip is healed away.
     */
    initialSelectedId: String? = null,
    /**
     * Open on a NEW cut: at the overlay's first composition, when no pending card exists, seed one
     * exactly as the page's own "Add undercut in this liner / here" button does — the tab's "Add
     * undercut" lands here. Nothing is recorded until the pill's "Record cut" (or leaving a typed
     * card); an untouched seed is discarded on leave like any pending card. [strip] may be a free
     * strip with no cuts yet (a bare-shaft add): the card seeds into its window.
     */
    seedNewCut: Boolean = false,
    /**
     * App-wide `PdfPrefs.undercutLinerOutline`: the dotted retrace of each liner's original edges
     * over the cuts, drawn here as on the printed sheet. Passed in so a change recomposes this canvas.
     */
    linerOutline: Boolean = true,
    /** Per-component display units: the strip title prints its liner's OD in the liner's own unit. */
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    /**
     * App-wide `PdfPrefs.undercutSplitAtLinerEdge`: whether the chain rail takes a node at each
     * buried liner edge, as on the printed sheet. Passed in so a change restyles the rail.
     */
    splitAtLinerEdge: Boolean = false,
    /**
     * App-wide `PdfPrefs.arrowSizePt` ("Dimension arrows"): the rail heads, and with them which
     * values seat in their break — the printed strip's rule. Passed in so a change re-plans the rail.
     */
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /**
     * Opens the undercut drawing's PDF preview — the Undercut tab's own preview, drawn over this
     * page. Called only after every card has been settled the way the back arrow settles them, so
     * the preview shows the record as it stands. Null hides the top bar's Preview icon.
     */
    onPreview: (() -> Unit)? = null,
    /** The tab's export gate, which its own Preview button shares. */
    previewEnabled: Boolean = true,
    /**
     * The undercut preview's options sheet, hosted here in the same non-modal
     * [PreviewOptionsPanel] — a remote control for the same app-wide prefs, so a change restyles
     * this canvas where it draws that element. Null hides the top bar's Options icon.
     */
    optionsSheet: (@Composable () -> Unit)? = null,
) {
    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents, spec) { surfaceSegsFrom(resolvedComponents, bodyBlends(spec, resolvedComponents)) }

    // Every liner on the shaft, in strip space — the pool the cards' liner references draw from.
    val linerSpans = remember(resolvedComponents) { linerSpansOf(resolvedComponents) }
    val linerOds = remember(resolvedComponents) { linerOdsOf(resolvedComponents) }
    val stripLiner = (strip as? UndercutStrip.LinerStrip)
        ?.let { UndercutLinerSpan(it.linerId, it.linerStartMm, it.linerEndMm) }

    // The strip's member undercuts, aft → fwd. Ids the record no longer holds simply drop out.
    val undercuts = remember(undercutRecord, strip) {
        strip.undercutIds
            .distinct()
            .mapNotNull { id -> undercutRecord.undercuts.firstOrNull { it.id == id } }
            .sortedBy { it.startFromAftMm }
    }

    // ── Draft state ──────────────────────────────────────────────────────────
    // Keyed on the strip's IDENTITY ([stripKey]), not the strip value: a strip carries its member
    // ids and its draw range, so every record edit yields a new (unequal) strip — keying on it
    // would wipe drafts and selection the instant a card was confirmed.
    // Per-card local edits; a card with no entry here shows stored values. The add flow's
    // not-yet-recorded page lives under [PENDING_UNDERCUT_ID]. Every entry is keyed by its own
    // draft's id, and only a card that has a page here can write one (`writeDraft` below).
    var drafts by remember(stripKey) { mutableStateOf(emptyMap<String, UndercutDraft>()) }
    // The add draft exactly as it was seeded. Its start is the pending card's ordering key, fixed
    // at creation — a pending card must not slide between pages while its Distance is being typed,
    // the same "reorder only on confirm" rule the recorded cards follow — and the whole draft is
    // what tells an untouched add (discarded on leave) from an edited one.
    var pendingSeed by remember(stripKey) { mutableStateOf<UndercutDraft?>(null) }
    val pendingSeedStartMm = pendingSeed?.startFromAftMm
    var selectedId by remember(stripKey) { mutableStateOf(initialSelectedId) }
    // The cut Confirm just added, until the record publishes it: the selection already names it,
    // but it has no page yet, and healing or adopting in that frame would drop the card the
    // machinist is about to be taken to.
    var awaitingRecordId by remember(stripKey) { mutableStateOf<String?>(null) }
    // [reselectAfterRestoreId] is the restored cut waiting for its page to exist — the record
    // publishes it a frame after the restore, so selecting it from the snackbar's own coroutine
    // would find no page to follow.
    val deleteSnackbar = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // The strip's members as the stale-draft sweep last saw them — see that effect below.
    var lastSeenUndercuts by remember(stripKey) { mutableStateOf(undercuts) }
    var reselectAfterRestoreId by remember { mutableStateOf<String?>(null) }

    // ── Leave handling: the card being left is the card being saved ───────────
    // Set while a blocked draft is being asked about; null means no dialog.
    var leavePrompt by remember(stripKey) { mutableStateOf<UndercutLeavePrompt?>(null) }
    // "Keep editing" returns to the blocked card programmatically — that is not the machinist
    // leaving the card they were moved to, so the next selection change skips leave handling
    // (without this, snapping back off a pending add page would auto-commit the add).
    var skipLeaveOnce by remember(stripKey) { mutableStateOf(false) }
    // The card a focused field sits on, or null — the one card a flush may blur.
    var focusedCardId by remember(stripKey) { mutableStateOf<String?>(null) }
    val flushGate = remember { UndercutFlushGate() }
    // The overlay's own "Past the liner edge" question, raised for an overhang a flush landed.
    var overlayOverhang by remember(stripKey) { mutableStateOf<UndercutOverlayOverhangAsk?>(null) }
    // Answers given at overlay level for stored cards, each waiting for its card to take it.
    var overhangAnswers by remember(stripKey) { mutableStateOf(emptyMap<String, UndercutOverhangAnswer>()) }
    // Where a settle-everything leave goes once every draft is settled: out of the overlay (the
    // back arrow, system Back), or to the PDF preview drawn over it (the top bar's Preview).
    var exitTo by remember(stripKey) { mutableStateOf(UndercutOverlayExit.CLOSE) }
    // The options panel over this page (the top bar's Options icon) — screen state only.
    var showOptions by remember(stripKey) { mutableStateOf(false) }

    val pageIds = remember(undercuts, pendingSeedStartMm) {
        buildUndercutPageIds(undercuts, pendingSeedStartMm)
    }
    val baselines = remember(undercuts, linerSpans) {
        undercuts.associate { it.id to undercutDraftOf(it, linerSpans) }
    }
    // Every STORED cut's 1-based page position — the number its card title carries — so an overlap
    // can name its partner. The pending card is not stored, so it is never anyone's partner.
    val pagePositionById = remember(pageIds) {
        pageIds.withIndex()
            .filter { it.value != PENDING_UNDERCUT_ID }
            .associate { it.value to it.index + 1 }
    }
    // This page's STORED cuts as (1-based position in [pageIds], clamped span) — the targets of
    // each card's "Start at end of" / "End at start of" chips, numbered as the card titles number
    // them. The pending card is not stored, so it is never a target.
    val pageButtTargets = remember(pageIds, undercuts, oalMm) {
        pageIds.mapIndexedNotNull { i, id ->
            undercuts.firstOrNull { it.id == id }?.let { u ->
                val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
                (i + 1) to UndercutSpanMm(u.id, c.startMm, c.endMm)
            }
        }.filter { (_, s) -> s.endMm > s.startMm }
    }

    // Only the SELECTED card previews: the canvas shows one candidate edit at a time, so what is
    // drawn is always the card in front of the machinist.
    val activeDraft = selectedId?.let { id -> drafts[id]?.takeIf { it != baselines[id] } }
    val drawUndercuts = remember(undercuts, activeDraft) { applyUndercutDraft(undercuts, activeDraft) }
    // The whole record rides along as the normalization pool: drawn depth is scaled to the
    // SHEET's deepest cut, so this strip's notches match what the printed drawing shows.
    val sheetUndercuts = remember(undercutRecord, activeDraft) {
        applyUndercutDraft(undercutRecord.undercuts, activeDraft)
    }
    val notches = remember(drawUndercuts, segs, oalMm, undercutRecord.exaggerationFrac, sheetUndercuts) {
        buildUndercutNotches(
            drawUndercuts, segs, oalMm,
            exaggerationFrac = undercutRecord.exaggerationFrac,
            sheetUndercuts = sheetUndercuts,
        )
    }
    val spans = remember(drawUndercuts, oalMm) {
        drawUndercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }
    // Every cut on the sheet as previewed, render-clamped.
    val sheetSpans = remember(sheetUndercuts, oalMm) {
        sheetUndercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }
    // With [splitAtLinerEdge] on, the strip liner's edges a cut on the sheet runs past into the
    // body — the previewed draft included — each become an extra node on the chain rail below,
    // the printed strip's rule (`buildUndercutRailSpans`' `splitAtMm`); off, none, so every cut
    // reads its full length.
    val railSplitAtMm = remember(sheetSpans, stripLiner, splitAtLinerEdge) {
        stripLiner?.takeIf { splitAtLinerEdge }
            ?.let { l -> buriedUndercutLinerEdgesMm(l.startMm, l.endMm, sheetSpans) }
            ?: emptyList()
    }
    // The window follows what is PREVIEWED, not just what is stored: a draft edited past the
    // strip's stored range (a cut overhanging the liner edge mid-edit — on-device report)
    // widens the drawing live, with the standard pad of neighbour stock beyond it — the same
    // range a confirmed overhang gets when the strip rebuilds. Never narrows while editing.
    val (drawStartMm, drawEndMm) = remember(strip, spans, oalMm, segs) {
        undercutPreviewDrawRange(strip, spans, oalMm, segs = segs, cutSpans = spans)
    }
    // Confirm-blocking status of the previewed draft: its notch draws dashed in the selection
    // color while valid, in the error color while this is non-null, and the status pill states
    // the same reason — one check over the whole sheet, so the drawing, the pill, and what
    // leaving the card does can never disagree.
    val activeDraftIssue = remember(activeDraft, selectedId, oalMm, undercutRecord, pagePositionById) {
        activeDraft?.let { d ->
            undercutConfirmIssue(
                d, oalMm, undercutDraftPool(undercutRecord.undercuts, d, selectedId, oalMm),
                pagePositionById,
            )
        }
    }
    // The overlap itself, while that is what blocks the draft (on-device report: the message
    // alone did not show where the cuts collide) — one shaft-space interval per partner the
    // draft partially overlaps, the same partners the confirm check blocks on.
    val activeOverlapBandsMm = remember(activeDraft, selectedId, oalMm, undercutRecord) {
        val d = activeDraft
        // The shaft-bounds check outranks the overlap check, exactly as in [undercutConfirmIssue].
        if (d == null || undercutSpanIssue(d.startFromAftMm, d.lengthMm, oalMm) != null) emptyList()
        else undercutPartialOverlapsMm(
            d.startFromAftMm, d.lengthMm,
            undercutDraftPool(undercutRecord.undercuts, d, selectedId, oalMm),
        )
    }

    // SET positions for the cards' "Measure From" re-projection. Physical shaft space already
    // (computeOalWindow's measureStartMm is always 0), so no further offset is applied.
    val setPositions = remember(spec) { undercutSetPositions(spec) }
    val aftSetXMm = setPositions.first
    val fwdSetXMm = setPositions.second

    // ── Carousel ↔ selection, a two-way binding that converges ───────────────
    // Never zero — the pager is simply not composed for an empty strip, and a 0-page state is a
    // needless edge case (the `ComponentCarouselPager` rule).
    val pageCount = pageIds.size.coerceAtLeast(1)
    // Keyed on the strip identity like the drafts, and opened on the initial selection's page so
    // the first settled-page emission adopts that card rather than the aft-most one.
    val pagerState = key(stripKey) {
        rememberPagerState(
            initialPage = pageIds.indexOf(initialSelectedId).coerceAtLeast(0),
            pageCount = { pageCount },
        )
    }
    // Read the live page list inside the never-restarted effects below.
    val pageIdsLive = rememberUpdatedState(pageIds)

    // Swipe → selection, so the card in front highlights its own notch. Only a SETTLED page is
    // adopted: while any scroll is in flight (a finger mid-drag, a programmatic jump) nothing is,
    // so a half-way drag neither moves the selection nor runs the leave sweep on a card the
    // machinist never left. `settledPage` is not used — it refreshes only when a scroll ends, so a
    // key-based reorder would leave it stale.
    //
    // The emission is the card's ID, de-duplicated: a reorder moves the open card to a new index
    // (the pager's `key` carries it), and that same card at a new index is not a swipe. Adopting
    // it would undo a deliberate selection that has not been paged to yet — "Add undercut" whose
    // pending page sorts to the open card's index displaces that card one page on, and adopting
    // it would run the leave sweep over the pending draft and COMMIT the default cut. Keyed on
    // the pager alone: restarting this on a page-list change would re-adopt whatever sits at the
    // current index.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .map { (page, _) -> pageIdsLive.value.getOrNull(page) }
            .distinctUntilChanged()
            .collect { id ->
                // A just-confirmed cut has no page until the record publishes it; the pager
                // clamping off the vanished pending page is not the machinist choosing a card.
                if (id != null && (awaitingRecordId == null || selectedId != awaitingRecordId)) {
                    selectedId = id
                }
            }
    }
    // Selection → page: a canvas tap opens that cut's card, and a confirmed cut is FOLLOWED to
    // its new aft → fwd position. One frame first, so a keyed pager move from a reorder has
    // landed and `currentPage` is the open card's post-move index. The jump is `scrollToPage`,
    // never an animation: an animated scroll reports every intermediate page as current, and
    // nothing here may treat one of those as the destination.
    LaunchedEffect(selectedId, pageIds) {
        withFrameNanos { }
        val sel = selectedId ?: return@LaunchedEffect
        val ids = pageIdsLive.value
        if (ids.getOrNull(pagerState.currentPage) == sel) return@LaunchedEffect
        val target = ids.indexOf(sel)
        if (target >= 0) pagerState.scrollToPage(target)
    }
    // Heal a selection with no page here — dropped on purpose (a cancelled add), its cut deleted,
    // moved off this strip by an Undo or by a confirmed edit, or an initial selection that was
    // never on it — by adopting the card the pager shows. A stale selection would leave the
    // visible card unhighlighted and the pill reporting a card nobody can see. The one exception
    // is Confirm's just-added cut while the record has not published it yet; once it has, it is
    // either on a page here (and followed) or on another strip (and healed away). Keyed on the
    // record too, because a cut that lands on ANOTHER strip leaves this page list unchanged.
    LaunchedEffect(pageIds, undercutRecord) {
        val sel = selectedId
        if (sel != null && sel == awaitingRecordId) {
            if (undercutRecord.undercuts.none { it.id == sel }) return@LaunchedEffect
            awaitingRecordId = null
        }
        if (pageIds.isEmpty()) return@LaunchedEffect
        if (sel == null || sel !in pageIds) {
            selectedId = pageIds.getOrNull(pagerState.currentPage.coerceIn(0, pageIds.lastIndex))
        }
    }
    // A cut the delete snackbar restored is selected once its page exists, so the carousel
    // returns to it.
    LaunchedEffect(pageIds, reselectAfterRestoreId) {
        val id = reselectAfterRestoreId ?: return@LaunchedEffect
        if (id in pageIds) {
            selectedId = id
            reselectAfterRestoreId = null
        }
    }
    // A draft whose cut no longer has a page here (an Undo removed it, or moved it off this
    // strip) is dropped: it is invisible, and the leave sweep would otherwise commit it over the
    // undone state, or raise a "can't be saved" prompt for a card that no longer exists. A dropped
    // draft that held an EDIT says so — typed values never vanish without a word (on-device
    // report). Its baseline is the cut as this strip last held it, so [lastSeenUndercuts] is
    // advanced only here.
    //
    // A PENDING draft with no pending page (its cut recorded, discarded or cancelled) is a ghost:
    // no card shows it, yet the sweep would check it against the cut it became and ask about an
    // overlap with that card on every leave (on-device report). It goes without a word — whatever
    // was typed into it reached the record through the confirm that retired its page.
    LaunchedEffect(undercuts, pendingSeed) {
        if (pendingSeed == null && PENDING_UNDERCUT_ID in drafts) {
            AppLog.w(UNDERCUT_DETAIL_LOG_TAG, "dropped a pending draft with no pending page")
            drafts = drafts - PENDING_UNDERCUT_ID
        }
        val live = undercuts.mapTo(HashSet()) { it.id }
        val dropped = drafts.filterKeys { it != PENDING_UNDERCUT_ID && it !in live }
        if (dropped.isNotEmpty()) {
            drafts = drafts - dropped.keys
            val editLost = dropped.any { (id, d) ->
                val prior = lastSeenUndercuts.firstOrNull { it.id == id }
                prior == null || d != undercutDraftOf(prior, linerSpans)
            }
            if (editLost) {
                snackbarScope.launch {
                    deleteSnackbar.showSnackbar(
                        message = UNDERCUT_DISCARDED_DRAFT_MSG,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
        lastSeenUndercuts = undercuts
    }

    // The ONE writer of a card's edit into [drafts]. A card delivers its edits under its page id,
    // and a field commits on BLUR — which can arrive after the card's page is gone, when the
    // disposed field loses focus: the add flow's card after its cut was recorded, or a card an Undo
    // took away. Such a write has no card to show it, and keeping it left a draft no page shadows
    // (the add flow's draft resurrected as an invisible duplicate of the cut it had just become,
    // blocking every leave with an overlap naming card 1 — on-device report). The entry carries
    // the page id it is keyed by, so a draft's id, its map key and its card are one value.
    //
    // A refused write that carried an EDIT says so, like the stale-draft drop above (a value typed
    // into a card an Undo then took away — typed values never vanish without a word). The add
    // flow's card is the exception: its page goes only when it is confirmed (the flush has already
    // landed its text) or deliberately cancelled.
    fun writeDraft(pageId: String, updated: UndercutDraft) {
        val hasPage = pageId in pageIdsLive.value &&
            (pageId != PENDING_UNDERCUT_ID || pendingSeed != null)
        if (!hasPage) {
            AppLog.w(UNDERCUT_DETAIL_LOG_TAG, "ignored a draft edit from a card with no page")
            if (pageId != PENDING_UNDERCUT_ID) {
                val prior = lastSeenUndercuts.firstOrNull { it.id == pageId }
                if (prior == null || updated.copy(id = pageId) != undercutDraftOf(prior, linerSpans)) {
                    snackbarScope.launch {
                        deleteSnackbar.showSnackbar(
                            message = UNDERCUT_DISCARDED_DRAFT_MSG,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
            return
        }
        drafts = drafts + (pageId to updated.copy(id = pageId))
    }

    // Lands the focused field's typed text in its card's draft before anything reads the drafts.
    // The numeric fields commit on blur, and neither a pill tap nor a swipe takes focus, so a value
    // still in the box when its card is confirmed or left would otherwise arrive only afterwards:
    // missing from what was confirmed, and — on the add flow's card — written to a page that no
    // longer exists (on-device report: the new cut's typed Ø was lost).
    //
    // Only the card holding focus is flushed, and only when it is a card being confirmed or left:
    // a field just focused on the card the machinist is moving TO keeps its focus (keyboard and
    // TalkBack navigation land there first). [flushGate] is up while the flush runs, so the card's
    // own "Past the liner edge" question stays quiet for a commit the overlay asks about itself.
    val focusManager = LocalFocusManager.current
    fun flushFocusedField(cardIsLeft: (String) -> Boolean) {
        val focused = focusedCardId ?: return
        if (!cardIsLeft(focused)) return
        flushGate.active = true
        try {
            focusManager.clearFocus(force = true)
        } finally {
            flushGate.active = false
        }
    }

    // Whether a flushed edit newly runs [after] past a liner edge [before] stayed on — the card's
    // own "Past the liner edge" rule ([newLinerOverhangs] against the strip's liner, else the
    // cut's reference liner), asked at overlay level because the card may not outlive the commit.
    fun overhangAskFor(
        id: String,
        before: UndercutDraft?,
        after: UndercutDraft,
        then: UndercutAfterOverhang,
    ): UndercutOverlayOverhangAsk? {
        if (before == null) return null
        val liner = stripLiner
            ?: undercutReferenceLinerFor(before.toUndercut(), linerSpans, null, oalMm)
            ?: return null
        val (aft, fwd) = newLinerOverhangs(
            before.startFromAftMm, before.lengthMm, after.startFromAftMm, after.lengthMm,
            liner.startMm, liner.endMm,
        )
        if (!aft && !fwd) return null
        val (aftMm, fwdMm) = undercutLinerExtensionsMm(
            after.startFromAftMm, after.lengthMm, liner.startMm, liner.endMm,
        )
        fun amount(mm: Float) = "${disp(mm, unit)} ${abbr(unit)}"
        return UndercutOverlayOverhangAsk(
            draftId = id,
            ends = UndercutLinerEnds(aft, fwd),
            aftAmount = if (aft) amount(aftMm) else null,
            fwdAmount = if (fwd) amount(fwdMm) else null,
            then = then,
        )
    }

    // The confirm check, run on the draft as it stands NOW — after a flush landed a typed value.
    fun confirmIssueOf(id: String, draft: UndercutDraft): String? = undercutConfirmIssue(
        draft, oalMm, undercutDraftPool(undercutRecord.undercuts, draft, id, oalMm), pagePositionById,
    )

    fun cancelDraft(id: String) {
        drafts = drafts - id
        if (id == PENDING_UNDERCUT_ID) {
            pendingSeed = null
            selectedId = null
        }
    }

    // Confirm — the ONE path from draft to record, taken by the pill's tap and by every
    // auto-commit-on-leave alike. Values land verbatim (golden rule); the reference is persisted
    // only when the chips actually moved, so a card whose stored `LINER_*` reference is displaying
    // its AFT_SET fallback is never silently rewritten.
    //
    // [follow] keeps the confirmed cut selected (the pill's own tap: stay put and watch the card
    // settle into its new aft → fwd index). A commit triggered by LEAVING the card passes false —
    // selection already belongs to the card being moved to, and stealing it back would land the
    // carousel on the card the machinist just left.
    fun confirmDraft(draft: UndercutDraft, follow: Boolean = true) {
        if (draft.isPending) {
            val newId = onAddUndercut(
                draft.startFromAftMm, draft.lengthMm, draft.reference, draft.referenceLinerId,
                draft.referenceCutId,
            )
            // addUndercut lands the span + reference; Ø and note ride the update op.
            if (draft.diaMm > 0f || draft.note.isNotBlank()) {
                onUpdateUndercut(newId, draft.startFromAftMm, draft.lengthMm, draft.diaMm, draft.note)
            }
            drafts = drafts - PENDING_UNDERCUT_ID
            pendingSeed = null
            if (follow) {
                awaitingRecordId = newId
                selectedId = newId
            }
        } else {
            onUpdateUndercut(draft.id, draft.startFromAftMm, draft.lengthMm, draft.diaMm, draft.note)
            val base = baselines[draft.id]
            if (base != null &&
                (base.reference != draft.reference ||
                    base.referenceLinerId != draft.referenceLinerId ||
                    base.referenceCutId != draft.referenceCutId)
            ) {
                onUpdateReference(draft.id, draft.reference, draft.referenceLinerId, draft.referenceCutId)
            }
            drafts = drafts - draft.id
            if (follow) selectedId = draft.id
        }
    }

    // What leaving the card with id [id] should do, plus the blocking reason behind a PROMPT.
    // The overlap check runs against the whole sheet, exactly as the pill and the canvas run it.
    fun leaveActionOf(id: String): Pair<UndercutLeaveAction, String?> {
        val draft = drafts[id]
        val issue = draft?.let {
            undercutConfirmIssue(
                it, oalMm, undercutDraftPool(undercutRecord.undercuts, it, id, oalMm), pagePositionById,
            )
        }
        val seed = if (id == PENDING_UNDERCUT_ID) pendingSeed else null
        return undercutLeaveAction(draft, baselines[id], issue, pendingSeed = seed) to issue
    }

    /**
     * Settle every draft except [keepId]'s: commit each one that clears its confirm check, and
     * stop at the first blocked one with the question to ask about it.
     *
     * The sweep covers the WHOLE draft map, not just the card just left, because a field commits
     * on blur: a value typed and then swiped away from lands in its draft only once focus goes,
     * which can be after that card is already behind the machinist. Settling only the outgoing
     * card would strand such an edit — dirty, unsaved, and invisible (the pill only ever states
     * the SELECTED card).
     */
    fun settleDraftsExcept(keepId: String?, closing: Boolean): UndercutSettleResult {
        // The card being left loses its focus first, so its typed text is in the draft the
        // decision below reads; every draft is read after the flush has landed.
        val flushedId = focusedCardId?.takeIf { it != keepId }
        val beforeFlush = flushedId?.let { drafts[it] ?: baselines[it] }
        flushFocusedField { it != keepId }
        val livePages = pageIdsLive.value
        drafts.entries.toList().forEach { (id, draft) ->
            if (id == keepId) return@forEach
            // A draft with no page is nobody's card to settle or ask about; the stale-draft effect
            // above retires it.
            if (id !in livePages || (id == PENDING_UNDERCUT_ID && pendingSeed == null)) return@forEach
            val (action, issue) = leaveActionOf(id)
            when (action) {
                UndercutLeaveAction.NONE -> Unit
                // `follow = false` is what keeps the carousel on the card being moved TO: the
                // confirmed cut may take a new aft → fwd index, and the pager's id keys carry the
                // open page along, but the SELECTION must not be dragged back to a settled card.
                // A flushed edit that newly runs past a liner edge is asked about first; the sweep
                // resumes on the answer.
                UndercutLeaveAction.COMMIT -> {
                    if (id == flushedId) {
                        overhangAskFor(id, beforeFlush, draft, UndercutAfterOverhang.Settle(keepId, closing))
                            ?.let {
                                overlayOverhang = it
                                return UndercutSettleResult.Asking
                            }
                    }
                    // Going to the preview leaves the overlay open underneath it, so the selected
                    // card follows its own commit — an add's new cut — rather than losing its page.
                    confirmDraft(
                        draft,
                        follow = closing && exitTo == UndercutOverlayExit.PREVIEW && id == selectedId,
                    )
                }
                // An untouched add goes without a word: nothing was typed into it. The selection
                // is left alone — it already belongs to the card being moved to.
                UndercutLeaveAction.DISCARD -> {
                    drafts = drafts - id
                    if (id == PENDING_UNDERCUT_ID) pendingSeed = null
                }
                UndercutLeaveAction.PROMPT -> return UndercutSettleResult.Prompt(
                    UndercutLeavePrompt(draftId = id, reason = issue.orEmpty(), closing = closing),
                )
            }
        }
        return UndercutSettleResult.Done
    }

    // Selection changed → every card the machinist is no longer on is settled. A clear draft
    // commits itself; a blocked one raises the dialog (they have already moved on visually, so
    // "Keep editing" is what snaps back).
    LaunchedEffect(selectedId) {
        if (skipLeaveOnce) {
            skipLeaveOnce = false
            return@LaunchedEffect
        }
        (settleDraftsExcept(keepId = selectedId, closing = false) as? UndercutSettleResult.Prompt)
            ?.let { leavePrompt = it.prompt }
    }

    // Closing is a leave too — the back arrow and the system back run through here, so a dirty
    // draft can never be lost by walking out of the overlay. A question raised on the way out
    // (blocked draft, or a flushed overhang) holds the overlay open until it is answered.
    fun finishSettle(result: UndercutSettleResult, closing: Boolean) {
        when (result) {
            UndercutSettleResult.Done -> {
                leavePrompt = null
                if (closing) when (exitTo) {
                    UndercutOverlayExit.CLOSE -> onClose()
                    UndercutOverlayExit.PREVIEW -> onPreview?.invoke()
                }
            }
            is UndercutSettleResult.Prompt -> leavePrompt = result.prompt
            UndercutSettleResult.Asking -> leavePrompt = null
        }
    }

    fun requestClose() {
        exitTo = UndercutOverlayExit.CLOSE
        finishSettle(settleDraftsExcept(keepId = null, closing = true), closing = true)
    }

    // The top bar's Preview: the same settle-everything leave as the back arrow — every typed
    // value flushed and committed, a blocked draft asked about with Keep editing / Discard — but
    // it ends on the PDF preview, with this page still open beneath it for when the preview closes.
    fun requestPreview() {
        showOptions = false
        exitTo = UndercutOverlayExit.PREVIEW
        finishSettle(settleDraftsExcept(keepId = null, closing = true), closing = true)
    }

    // The pill's Confirm: flush the selected card's focused field, re-check the draft as it now
    // stands (the pill offered Confirm for the value BEFORE the flush — a typed Length that
    // overlaps, leaves the shaft or is zero must not be recorded; the pill recomposes to Blocked
    // with the reason), ask about a newly overhung liner edge, then commit.
    fun confirmSelected() {
        val id = selectedId ?: return
        val before = drafts[id] ?: baselines[id]
        flushFocusedField { it == id }
        val draft = drafts[id]?.takeIf { it != baselines[id] } ?: return
        if (confirmIssueOf(id, draft) != null) return
        val ask = overhangAskFor(id, before, draft, UndercutAfterOverhang.Confirm)
        if (ask != null) overlayOverhang = ask else confirmDraft(draft)
    }

    // "Next cut"'s seed: a PENDING card butted on the FWD end of [current] (the cut just
    // confirmed, as confirmed), reading from its datum, opened as the selected card. The pending
    // page is fresh state — the card keys on the seed — and an untouched seed is discarded on
    // leave like any other add.
    fun plantNextCutSeed(current: UndercutDraft) {
        val next = undercutNextCutSeed(current.toUndercut(), oalMm, DEFAULT_UNDERCUT_LENGTH_MM) ?: return
        val pool = applyUndercutDraft(undercutRecord.undercuts, current).map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        val seed = undercutSeedInheriting(
            startMm = next.startFromAftMm,
            lengthMm = next.lengthMm,
            from = current,
            linerSpans = linerSpans,
            sheetSpans = pool,
            aftSetXMm = aftSetXMm,
            fwdSetXMm = fwdSetXMm,
        )
        drafts = drafts + (PENDING_UNDERCUT_ID to seed)
        pendingSeed = seed
        selectedId = PENDING_UNDERCUT_ID
    }

    // The card's "Next cut": the pill's Confirm path for the card (flush, re-check, the "Past the
    // liner edge" question), then the next cut's seed. A clean card has nothing to confirm and
    // seeds straight away. A draft the flush turned blocked, or one left with no stock FWD of it,
    // records nothing and seeds nothing — the pill and the button's caption say why.
    fun nextCutFrom(id: String) {
        // Only the SELECTED card acts, once the pager has settled on it — the trash's rule.
        if (id != selectedId || pagerState.isScrollInProgress) {
            val selPage = pageIdsLive.value.indexOf(selectedId)
            if (selPage >= 0) snackbarScope.launch { pagerState.scrollToPage(selPage) }
            return
        }
        val before = drafts[id] ?: baselines[id]
        flushFocusedField { it == id }
        val draft = drafts[id]?.takeIf { it != baselines[id] }
        val current = draft ?: baselines[id] ?: return
        if (undercutNextCutSeed(current.toUndercut(), oalMm, DEFAULT_UNDERCUT_LENGTH_MM) == null) return
        if (draft != null) {
            if (confirmIssueOf(id, draft) != null) return
            overhangAskFor(id, before, draft, UndercutAfterOverhang.NextCut)?.let {
                overlayOverhang = it
                return
            }
            confirmDraft(draft, follow = false)
        }
        plantNextCutSeed(current)
    }

    // An answer to the overlay's "Past the liner edge" question, then the action it held up. A
    // STORED card takes the answer into its own flag (its warning shows under the fields while the
    // end stays off the liner); the add flow's card does not outlive its confirm — the recorded cut
    // opens a new card — so there the answer is an acknowledgement only.
    fun answerOverlayOverhang(ask: UndercutOverlayOverhangAsk, declined: Boolean) {
        overlayOverhang = null
        if (ask.draftId != PENDING_UNDERCUT_ID) {
            overhangAnswers = overhangAnswers + (ask.draftId to UndercutOverhangAnswer(ask.ends, declined))
        }
        when (val then = ask.then) {
            UndercutAfterOverhang.Confirm -> {
                val draft = drafts[ask.draftId]?.takeIf { it != baselines[ask.draftId] } ?: return
                if (confirmIssueOf(ask.draftId, draft) == null) confirmDraft(draft)
            }
            UndercutAfterOverhang.NextCut -> {
                val draft = drafts[ask.draftId]?.takeIf { it != baselines[ask.draftId] } ?: return
                if (confirmIssueOf(ask.draftId, draft) != null) return
                confirmDraft(draft, follow = false)
                plantNextCutSeed(draft)
            }
            is UndercutAfterOverhang.Settle ->
                finishSettle(settleDraftsExcept(then.keepId, then.closing), then.closing)
        }
    }

    // The overlay's "Add undercut in this liner / here": a PENDING card for a new cut, selected and
    // paged to. ONE seeding path, taken by that button and by a host that opens the overlay to add
    // a cut ([seedNewCut]). The new page is a DRAFT — it previews here but enters the record only
    // when confirmed (pill tap, or leaving the page with a clear draft); an untouched one is
    // discarded on leave. The default span takes the first free gap in the strip so it doesn't
    // land on top of an existing cut.
    fun seedPendingCut() {
        val addRange = undercutAddRangeOf(strip, stripLiner)
        val occupied = undercutRecord.undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        // A strip that already holds cuts seeds the new one butted against them
        // (FWD of the FWD-most, else AFT of the AFT-most), reading from that
        // neighbour's datum — a run of cuts is recorded one after the next
        // (on-device request). An empty strip, or one with no clear stock on
        // either side, takes the default span below.
        val butted = undercutAddSeedSpan(
            stripCuts = undercuts.map { u ->
                val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
                UndercutSpanMm(u.id, c.startMm, c.endMm)
            },
            occupied = occupied,
            oalMm = oalMm,
            lengthMm = DEFAULT_UNDERCUT_LENGTH_MM,
        )
        val neighbour = butted?.let { b -> baselines[b.neighbourId] }
        val span = defaultUndercutSpan(addRange.first, addRange.second, occupied, oalMm)
        // A liner whose AFT edge an existing cut already runs past into the
        // body has no step left there to measure from, so the new cut authors
        // against the nearer S.E.T. instead (on-device request).
        val seedFromLiner = stripLiner != null &&
            !undercutLinerEdgeBuried(stripLiner.startMm, occupied)
        val seed = if (butted != null && neighbour != null) undercutSeedInheriting(
            startMm = butted.startMm,
            lengthMm = butted.lengthMm,
            from = neighbour,
            linerSpans = linerSpans,
            sheetSpans = occupied,
            aftSetXMm = aftSetXMm,
            fwdSetXMm = fwdSetXMm,
        ) else UndercutDraft(
                id = PENDING_UNDERCUT_ID,
                startFromAftMm = span.startMm,
                lengthMm = span.lengthMm,
                diaMm = 0f,
                note = "",
                // A liner strip authors against the datum the machinist is
                // standing at, so the very first typed Distance already reads
                // from the liner's own AFT edge. A bare-shaft (body-only) cut
                // has no liner edge — its only datums are the SETs, so the
                // nearer one wins (the same proximity rule the printed strip's
                // title anchor uses, so card and sheet read from the same SET).
                reference = if (seedFromLiner) UndercutReference.LINER_AFT
                            else nearestSetReference(
                                span.startMm, span.startMm + span.lengthMm,
                                aftSetXMm, fwdSetXMm,
                            ),
                referenceLinerId = if (seedFromLiner) stripLiner?.id.orEmpty() else "",
            )
        drafts = drafts + (PENDING_UNDERCUT_ID to seed)
        pendingSeed = seed
        selectedId = PENDING_UNDERCUT_ID
    }

    // A host that opened the overlay to ADD a cut ([seedNewCut]) gets the add button's pending
    // card at once — once per overlay, read at its first composition, so a later strip-identity
    // change (the new cut recorded onto its real strip) never seeds a second one. An empty free
    // strip (a bare-shaft add, no cuts yet) is a valid host: the card seeds into its window.
    LaunchedEffect(Unit) {
        if (seedNewCut && pendingSeed == null && PENDING_UNDERCUT_ID !in drafts) seedPendingCut()
    }

    BackHandler(enabled = leavePrompt == null && overlayOverhang == null) { requestClose() }

    // ── Colors captured here — the Canvas draw scope must not read MaterialTheme ──
    // Sheet ink is FIXED black, never theme onSurface: the strip canvas is a paper-white
    // sheet in every theme, and dark theme's near-white onSurface would print invisible ink.
    val outlineColor = Color.Black
    // The liner shade comes from the user's Undercut Drawing style (Settings → Preview
    // Colors) — a fixed ink color on the white sheet, deliberately NOT a theme role: a theme
    // tint (dark theme's near-white tertiary) washes out to nothing on white, and the
    // pure-white notch voids would then have no shade to read against. The default reproduces
    // the historical fixed grey at the PDF shade fill's weight (argb 40), keeping the
    // on-screen strip and the printed strip alike: grey liner, white cut sections (on-device
    // request). Line art empties the fill entirely.
    val linerFillColor = style.linerFill()
    val selectColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val textColorArgb = outlineColor.toArgb()
    val textPaint = remember(textColorArgb) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = textColorArgb
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 26f
        }
    }
    val cardShape = MaterialTheme.shapes.medium

    // Pinch-to-zoom + two-finger pan — the ComponentWearDetailOverlay transform pattern:
    // transformable state drives the Canvas graphicsLayer, and the tap handler inverts it.
    var zoomScale by remember(stripKey) { mutableFloatStateOf(1f) }
    // The drawing Box's laid-out width, so the chain rail can be planned (and its band sized)
    // before the canvas draws.
    var canvasWidthPx by remember { mutableIntStateOf(0) }
    var zoomOffset by remember(stripKey) { mutableStateOf(Offset.Zero) }
    val zoomTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 6f)
        zoomOffset = if (zoomScale <= 1f) Offset.Zero else zoomOffset + panChange
    }
    // Back to the fitted view — double-tap or the corner button, the schematic preview's pair
    // (on-device request: the section view zoomed but had no way back).
    val zoomResetScope = rememberCoroutineScope()
    fun resetZoom() {
        val fromScale = zoomScale
        val fromOffset = zoomOffset
        if (fromScale == 1f && fromOffset == Offset.Zero) return
        zoomResetScope.launch {
            animate(0f, 1f, animationSpec = tween(140)) { t, _ ->
                zoomScale = fromScale + (1f - fromScale) * t
                zoomOffset = fromOffset * (1f - t)
            }
        }
    }

    // Where the drawing block (canvas + save pill) ends, root px — the options panel's ceiling.
    var drawingBottomPx by remember { mutableFloatStateOf(0f) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val optionsMaxHeightDp = undercutDetailOptionsMaxHeightDp(
        screenHeightDp = screenHeightDp,
        drawingBottomDp = with(LocalDensity.current) { drawingBottomPx.toDp().value },
        chromeDp = TUNING_SHEET_CHROME_DP + navBottomDp,
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

                // ── Top bar ──────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Undercut Drawing")
                    }
                    Text(
                        text = undercutStripTitle(strip, spec, unit, displayUnits),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                    )
                    // The document's ONE history, the same steps the Schematic tab's Undo walks.
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("undercut_detail_undo"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("undercut_detail_redo"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    // The drawing's preview and its options without leaving this page or
                    // scrolling the tab to its foot (on-device request).
                    if (onPreview != null) {
                        IconButton(
                            onClick = { requestPreview() },
                            enabled = previewEnabled,
                            modifier = Modifier.testTag("undercut_detail_preview"),
                        ) {
                            Icon(Icons.Outlined.Preview, contentDescription = "Preview")
                        }
                    }
                    if (optionsSheet != null) {
                        // A toggle, like the preview's own Tune icon: the panel is non-modal, so
                        // this bar stays reachable while it is open.
                        IconButton(
                            onClick = {
                                // The keyboard would sit over the panel. A blur commits only into
                                // the card's draft, exactly as tapping the canvas does.
                                if (!showOptions) focusManager.clearFocus()
                                showOptions = !showOptions
                            },
                            modifier = Modifier.testTag("undercut_detail_options"),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Drawing options")
                        }
                    }
                }
                HorizontalDivider()

                // ── Fixed drawing block: the canvas never scrolls away from the cards ──
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val profileRowDp = UNDERCUT_PROFILE_ROW_DP
                    // Fixed band for the Ø callouts (leader + up to two staggered label rows), so the
                    // canvas height doesn't jump between one and two rows.
                    val diaBandDp = if (drawUndercuts.isEmpty()) 0.dp else 44.dp

                    val winLenMm = (drawEndMm - drawStartMm).coerceAtLeast(0.001f)
                    val maxOdMm = remember(segs, drawStartMm, drawEndMm) {
                        val env = maxOuterDiaOver(segs, drawStartMm, drawEndMm)
                        if (env > 0f) env else 1f
                    }

                    // The rail band runs the printed strip's ONE planner (`planUndercutStripRails`)
                    // and ONE draw routine (`drawUndercutStripRails`) at canvas scale: the level-0 chain
                    // over the top-level cuts, each nesting level on its own row against its
                    // parent's edges, the total across the top-level cuts, values seated in a break
                    // when they fit. Planned here, off the same x map the canvas draws with, so the
                    // reserved height and the drawn rows are one number. The authoring overlay stays
                    // single-unit, so no value is dual.
                    val density = LocalDensity.current
                    val railScale = density.density * UNDERCUT_DETAIL_DP_PER_PT
                    val railTypeface = OutputTypography.active
                    val railTextPaint = remember(textColorArgb, railScale, railTypeface) {
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.style = android.graphics.Paint.Style.FILL
                            color = textColorArgb
                            textSize = UNDERCUT_DETAIL_RAIL_TEXT_PT * railScale
                            typeface = railTypeface
                        }
                    }
                    val railDimPaint = remember(textColorArgb, density) {
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.style = android.graphics.Paint.Style.STROKE
                            color = textColorArgb
                            strokeWidth = with(density) { UNDERCUT_DETAIL_RAIL_STROKE_DP.dp.toPx() }
                        }
                    }
                    // Every input of the plan is one record, and the record is the remember key, so
                    // no input can be left out of the key.
                    val railInputs = with(density) {
                        UndercutDetailRailInputs(
                            spans = spans,
                            chainStartMm = strip.chainStartMm,
                            chainEndMm = strip.chainEndMm,
                            splitAtMm = railSplitAtMm,
                            unit = unit,
                            startedStrip = spans.isEmpty(),
                            widthPx = canvasWidthPx.toFloat(),
                            profileRowHeightPx = profileRowDp.toPx(),
                            edgePadPx = UNDERCUT_EDGE_PAD_DP.dp.toPx(),
                            drawStartMm = drawStartMm,
                            windowLengthMm = winLenMm,
                            maxOdMm = maxOdMm,
                            scale = railScale,
                            textSizePx = railTextPaint.textSize,
                            typeface = railTypeface,
                            fractionStyle = FractionTypography.active,
                            arrowSizePt = arrowSizePt,
                        )
                    }
                    val rails = remember(railInputs) {
                        planUndercutDetailRails(railInputs) { railTextPaint.measureDualLabel(it, false) }
                    }
                    val railBand = remember(rails, railScale, railTextPaint) {
                        undercutDetailRailBand(rails, railScale, undercutRailRowHeightPt(railTextPaint, false, railScale))
                    }
                    // The cylinder's top sits on the rail band's bottom, the printed strip's
                    // `cylTop = chainRailY + belowRows × row + headroom`: the profile row starts
                    // above it by the row's own slack over the drawn cylinder.
                    val profileTopPx = with(density) {
                        val lay0 = computeUndercutWindowLayout(
                            widthPx = canvasWidthPx.toFloat(),
                            profileTopPx = 0f,
                            profileRowHeightPx = profileRowDp.toPx(),
                            windowLengthMm = winLenMm,
                            maxOdMm = maxOdMm,
                            edgePadPx = UNDERCUT_EDGE_PAD_DP.dp.toPx(),
                        )
                        val slackPx = (lay0.cy - maxOdMm * 0.5f * lay0.pxPerMm).coerceAtLeast(0f)
                        railBand.cylTopPx - slackPx
                    }
                    // Cut numbers too wide for their cut sit BELOW the shaft, between its bottom
                    // and the first Ø label row. Placed here off the canvas's own maps (profile
                    // row at 0 — only offsets matter); where the leader zone is shorter than the
                    // number plus a pad each side, the Ø band's top gap grows by the shortfall, so
                    // a number never sits on a Ø value.
                    val cutNumbers = remember(undercutRecord.undercuts, oalMm) {
                        undercutSheetNumbers(undercutRecord.undercuts, oalMm)
                    }
                    val cutNumberGapPx = with(density) {
                        val lay0 = computeUndercutWindowLayout(
                            widthPx = canvasWidthPx.toFloat(),
                            profileTopPx = 0f,
                            profileRowHeightPx = profileRowDp.toPx(),
                            windowLengthMm = winLenMm,
                            maxOdMm = maxOdMm,
                            edgePadPx = UNDERCUT_EDGE_PAD_DP.dp.toPx(),
                        )
                        val r0: (Float) -> Float = { diaMm -> diaMm * 0.5f * lay0.pxPerMm }
                        val placements = undercutCutNumberPlacementsPx(
                            notches, cutNumbers,
                            xPx = { mm -> lay0.startPx + (mm - drawStartMm) * lay0.pxPerMm },
                            rPx = r0,
                            textSizePx = textPaint.textSize,
                        )
                        val bottom = undercutCutNumbersBottomPx(placements, lay0.cy, r0, textPaint.textSize)
                        if (bottom == null) 0f
                        else (bottom - (profileRowDp.toPx() + UNDERCUT_DIA_ROW0_GAP_PX)).coerceAtLeast(0f)
                    }
                    val canvasHeightDp = with(density) {
                        (profileTopPx + profileRowDp.toPx() + cutNumberGapPx).coerceAtLeast(0f).toDp()
                    } + diaBandDp

                    // Read live inside the tap pointerInput without re-keying it on every pinch.
                    val scaleForTap = rememberUpdatedState(zoomScale)
                    val offsetForTap = rememberUpdatedState(zoomOffset)
                    val profileTopForTap = rememberUpdatedState(profileTopPx)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(canvasHeightDp)
                            .onSizeChanged { canvasWidthPx = it.width }
                            .clip(cardShape)
                            .background(Color.White)
                            .semantics {
                                contentDescription = SheetSemantics.undercutDetail(spans.size)
                            }
                            .transformable(zoomTransformState)
                            .pointerInput(strip, spans, winLenMm, maxOdMm) {
                                detectTapGestures(onDoubleTap = { resetZoom() }) { rawTap ->
                                    val sc = scaleForTap.value
                                    val tap = Offset(
                                        (rawTap.x - offsetForTap.value.x - size.width / 2f) / sc + size.width / 2f,
                                        (rawTap.y - offsetForTap.value.y - size.height / 2f) / sc + size.height / 2f,
                                    )
                                    val lay = computeUndercutWindowLayout(
                                        widthPx = size.width.toFloat(),
                                        profileTopPx = profileTopForTap.value,
                                        profileRowHeightPx = profileRowDp.toPx(),
                                        windowLengthMm = winLenMm,
                                        maxOdMm = maxOdMm,
                                        edgePadPx = UNDERCUT_EDGE_PAD_DP.dp.toPx(),
                                    )
                                    val xMm = drawStartMm + (tap.x - lay.startPx) / lay.pxPerMm
                                    val padMm = 12.dp.toPx() / lay.pxPerMm
                                    // A miss keeps the current card: selection and the open page are
                                    // one thing now, so clearing it would leave the carousel showing
                                    // a card that nothing on the canvas points at.
                                    pickUndercutAt(xMm, spans, padMm)?.let { selectedId = it }
                                }
                            },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = zoomOffset.x,
                                    translationY = zoomOffset.y,
                                ),
                        ) {
                            val outlineWidthPx = 1.5.dp.toPx()
                            val profileRowPx = profileRowDp.toPx()
                            val lay = computeUndercutWindowLayout(
                                widthPx = size.width,
                                profileTopPx = profileTopPx,
                                profileRowHeightPx = profileRowPx,
                                windowLengthMm = winLenMm,
                                maxOdMm = maxOdMm,
                                edgePadPx = UNDERCUT_EDGE_PAD_DP.dp.toPx(),
                            )
                            val cy = lay.cy
                            val xPx: (Float) -> Float = { mm -> lay.startPx + (mm - drawStartMm) * lay.pxPerMm }
                            val rPx: (Float) -> Float = { diaMm -> diaMm * 0.5f * lay.pxPerMm }

                            // ── Strip profile: every resolved component clipped to the draw range ──
                            // Liners paint last so a liner over a body reads as the surface, matching
                            // the max-wins envelope the notch math uses.
                            val eps = 1e-3f
                            val drawn = resolvedComponents
                                .filter { it !is ResolvedCouplerBoltSlot }
                                .sortedBy { if (it is ResolvedLiner) 1 else 0 }
                            drawn.forEach { rc ->
                                val a = max(rc.startMmPhysical, drawStartMm)
                                val b = min(rc.endMmPhysical, drawEndMm)
                                if (b - a <= eps) return@forEach
                                // A relieved thread splits like the undercut sheet's strips
                                // (`geom/ThreadReliefMath.kt`): hatch + major-Ø outline over the
                                // hatch span, the groove as its reduced rectangle, the shoulder-end
                                // face at the relief Ø only.
                                if (rc is ResolvedThread) {
                                    val relief = threadReliefSpan(
                                        rc.startMmPhysical, rc.endMmPhysical - rc.startMmPhysical, rc.majorDiaMm,
                                        rc.reliefLenMm, rc.reliefDiaMm, rc.reliefAtAft,
                                    )
                                    if (relief != null) {
                                        val hatch = threadHatchSpan(
                                            rc.startMmPhysical, rc.endMmPhysical - rc.startMmPhysical, rc.majorDiaMm,
                                            rc.reliefLenMm, rc.reliefDiaMm, rc.reliefAtAft,
                                        )
                                        fun ownEdge(mm: Float) = mm > drawStartMm + eps && mm < drawEndMm - eps
                                        val r = rPx(rc.majorDiaMm)
                                        hatch?.let { hs ->
                                            val ha = max(hs.startMm, drawStartMm); val hb = min(hs.endMm, drawEndMm)
                                            if (hb - ha > eps) {
                                                val hxa = xPx(ha); val hxb = xPx(hb)
                                                drawThreadStubHatch(
                                                    hxa, cy - r, hxb, cy + r, outlineColor,
                                                    threadStubHatchLean(rc, threadSlant),
                                                    threadStubHatchSpacing(rc, 2f * r, threadDensity),
                                                )
                                                drawLine(outlineColor, Offset(hxa, cy - r), Offset(hxb, cy - r), outlineWidthPx)
                                                drawLine(outlineColor, Offset(hxa, cy + r), Offset(hxb, cy + r), outlineWidthPx)
                                                if (ownEdge(hs.startMm)) drawLine(outlineColor, Offset(hxa, cy - r), Offset(hxa, cy + r), outlineWidthPx)
                                                if (ownEdge(hs.endMm)) drawLine(outlineColor, Offset(hxb, cy - r), Offset(hxb, cy + r), outlineWidthPx)
                                            }
                                        }
                                        val ra = max(relief.startMm, drawStartMm); val rb = min(relief.endMm, drawEndMm)
                                        if (rb - ra > eps) {
                                            val rr = rPx(relief.diaMm)
                                            val rxa = xPx(ra); val rxb = xPx(rb)
                                            drawLine(outlineColor, Offset(rxa, cy - rr), Offset(rxb, cy - rr), outlineWidthPx)
                                            drawLine(outlineColor, Offset(rxa, cy + rr), Offset(rxb, cy + rr), outlineWidthPx)
                                            val shoulderMm = relief.shoulderEndMm(rc.reliefAtAft)
                                            val xs = if (rc.reliefAtAft) rxa else rxb
                                            if (ownEdge(shoulderMm)) drawLine(outlineColor, Offset(xs, cy - rr), Offset(xs, cy + rr), outlineWidthPx)
                                            if (hatch == null) {
                                                val otherMm = if (rc.reliefAtAft) relief.endMm else relief.startMm
                                                val xo = if (rc.reliefAtAft) rxb else rxa
                                                if (ownEdge(otherMm)) drawLine(outlineColor, Offset(xo, cy - rr), Offset(xo, cy + rr), outlineWidthPx)
                                            }
                                        }
                                        return@forEach
                                    }
                                }
                                val rA = rPx(componentDiaAt(rc, a))
                                val rB = rPx(componentDiaAt(rc, b))
                                if (rA <= 0f && rB <= 0f) return@forEach
                                val xa = xPx(a)
                                val xb = xPx(b)
                                val path = Path().apply {
                                    moveTo(xa, cy - rA)
                                    lineTo(xb, cy - rB)
                                    lineTo(xb, cy + rB)
                                    lineTo(xa, cy + rA)
                                    close()
                                }
                                if (rc is ResolvedThread) {
                                    drawThreadStubHatch(
                                        xa, cy - rA, xb, cy + rB, outlineColor,
                                        threadStubHatchLean(rc, threadSlant),
                                        threadStubHatchSpacing(rc, rA + rB, threadDensity),
                                    )
                                } else if (rc is ResolvedLiner) {
                                    // Only the liner is filled. Bodies/tapers draw outline-only —
                                    // a filled body sliver past the liner edge read as a mystery
                                    // second box (on-device report), while an outline reads as the
                                    // shaft continuing under the break edge, matching the wear
                                    // overlay's unfilled neighbor stubs.
                                    drawPath(path, color = linerFillColor)
                                }
                                drawLine(outlineColor, Offset(xa, cy - rA), Offset(xb, cy - rB), outlineWidthPx)
                                drawLine(outlineColor, Offset(xa, cy + rA), Offset(xb, cy + rB), outlineWidthPx)
                                // Vertical faces only where the edge is the component's own — an edge
                                // the draw range cut off is closed by the break edge / flat end below.
                                if (rc.startMmPhysical > drawStartMm + eps) {
                                    drawLine(outlineColor, Offset(xa, cy - rA), Offset(xa, cy + rA), outlineWidthPx)
                                }
                                if (rc.endMmPhysical < drawEndMm - eps) {
                                    drawLine(outlineColor, Offset(xb, cy - rB), Offset(xb, cy + rB), outlineWidthPx)
                                }
                            }

                            // ── Strip ends: flat at the shaft's own extent, S-curve break otherwise ──
                            val rAft = rPx(outerDiaAt(segs, drawStartMm + eps))
                            val rFwd = rPx(outerDiaAt(segs, drawEndMm - eps))
                            if (rAft > 0f) {
                                if (drawStartMm <= eps) {
                                    drawLine(
                                        outlineColor, Offset(lay.startPx, cy - rAft),
                                        Offset(lay.startPx, cy + rAft), outlineWidthPx,
                                    )
                                } else {
                                    drawBreakEdgeCompose(
                                        x = lay.startPx, yTop = cy - rAft, yBot = cy + rAft,
                                        amplitude = rAft * UNDERCUT_BREAK_AMP_FRAC_OF_R, color = outlineColor,
                                        strokeWidthPx = outlineWidthPx, eyeAtTop = true,
                                    )
                                }
                            }
                            if (rFwd > 0f) {
                                if (drawEndMm >= oalMm - eps) {
                                    drawLine(
                                        outlineColor, Offset(lay.endPx, cy - rFwd),
                                        Offset(lay.endPx, cy + rFwd), outlineWidthPx,
                                    )
                                } else {
                                    drawBreakEdgeCompose(
                                        x = lay.endPx, yTop = cy - rFwd, yBot = cy + rFwd,
                                        amplitude = rFwd * UNDERCUT_BREAK_AMP_FRAC_OF_R, color = outlineColor,
                                        strokeWidthPx = outlineWidthPx, eyeAtTop = false,
                                    )
                                }
                            }

                            // ── Notches — the void erases the surface inside the cut ──
                            // Settled cuts draw solid; the previewed DRAFT draws dashed in the
                            // selection color (error color while its confirm check fails), so a
                            // provisional cut is unmistakable against the liner and its
                            // neighbors. Confirm settles it into the normal outline.
                            val draftId = activeDraft?.id
                            drawUndercutNotches(
                                notches = notches.filter { it.id != draftId },
                                xPx = xPx,
                                rPx = rPx,
                                cy = cy,
                                voidColor = Color.White,
                                outlineColor = outlineColor,
                                strokeWidthPx = outlineWidthPx,
                                sectionFillColor = style.sectionFill(),
                            )
                            if (draftId != null) {
                                drawUndercutNotches(
                                    notches = notches.filter { it.id == draftId },
                                    xPx = xPx,
                                    rPx = rPx,
                                    cy = cy,
                                    voidColor = Color.White,
                                    outlineColor = if (activeDraftIssue != null) errorColor else selectColor,
                                    strokeWidthPx = outlineWidthPx,
                                    sectionFillColor = style.sectionFill(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                                )
                            }
                            // ── Liner outline — the liner's original edges, dotted over the cuts ──
                            if (linerOutline) {
                                drawLinerOutlines(
                                    liners = linerOds,
                                    notches = notches,
                                    xPx = xPx,
                                    rPx = rPx,
                                    cy = cy,
                                    color = SheetInk.Outline.copy(alpha = LINER_OUTLINE_CANVAS_ALPHA),
                                    strokeWidthPx = 1.dp.toPx(),
                                )
                            }
                            // ── Overlap bands — where a blocked draft collides with a neighbour ──
                            // Over the notches (so the band reads on top of the cut it intrudes into)
                            // and under the selection highlight; full profile height like the
                            // highlight, with a solid edge line each side so a hairline overlap
                            // still shows.
                            if (activeOverlapBandsMm.isNotEmpty()) {
                                val rTop = rPx(maxOdMm) + 6f
                                val edgeWidthPx = 1.dp.toPx()
                                activeOverlapBandsMm.forEach { (a, b) ->
                                    val x0 = xPx(a)
                                    val x1 = xPx(b)
                                    drawRect(
                                        color = errorColor.copy(alpha = 0.35f),
                                        topLeft = Offset(x0, cy - rTop),
                                        size = Size(x1 - x0, rTop * 2f),
                                    )
                                    drawLine(errorColor, Offset(x0, cy - rTop), Offset(x0, cy + rTop), edgeWidthPx)
                                    drawLine(errorColor, Offset(x1, cy - rTop), Offset(x1, cy + rTop), edgeWidthPx)
                                }
                            }

                            // ── Selection highlight — the card the carousel is showing ──
                            selectedId?.let { id ->
                                spans.firstOrNull { it.id == id }?.let { s ->
                                    val x0 = xPx(s.startMm)
                                    val x1 = xPx(s.endMm)
                                    val rTop = rPx(maxOdMm) + 6f
                                    drawRect(
                                        color = selectColor.copy(alpha = 0.14f),
                                        topLeft = Offset(x0, cy - rTop),
                                        size = Size(x1 - x0, rTop * 2f),
                                    )
                                    drawRect(
                                        color = selectColor.copy(alpha = 0.75f),
                                        topLeft = Offset(x0, cy - rTop),
                                        size = Size(x1 - x0, rTop * 2f),
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }
                            }

                            // ── Dimension rails above: the printed strip's rails at canvas scale ──
                            // Chain datums, not the draw range: a liner strip anchors the rail on the
                            // liner's own edges (extended by any cut overhang), a free strip on its
                            // window edges — the pad outside the chain range is never dimensioned.
                            rails?.let { plan ->
                                drawUndercutStripRails(
                                    drawContext.canvas.nativeCanvas, railDimPaint, railTextPaint, plan,
                                    chainRailY = railBand.chainRailY,
                                    totalRailY = railBand.totalRailY,
                                    witnessBottomY = cy - rPx(maxOdMm) - UNDERCUT_RAIL_WITNESS_GAP_PT * railScale,
                                    railLabelRows = plan.rows.belowRows,
                                    drawLabels = true,
                                    scale = railScale,
                                    arrowSizePt = arrowSizePt,
                                )
                            }

                            // ── Ø callouts below (shared planDiaCallouts engine) ──
                            if (notches.isNotEmpty()) {
                                val leaderColor = outlineColor.copy(alpha = 0.6f)
                                // Same shelf anchors the sheet uses, so a leader leaves the same
                                // point of the same floor on both draw sites.
                                val anchorById = undercutCalloutAnchorsMm(spans)
                                val stations = notches.mapNotNull { n ->
                                    val u = drawUndercuts.firstOrNull { it.id == n.id }
                                        ?: return@mapNotNull null
                                    // The authoring overlay stays single-unit (`docs/DualUnitStacking_PLAN.md`
                                    // §8), so its label has no second term and never stacks.
                                    val label = DualLabel.single(
                                        if (u.diaMm > 0f) formatDiaWithUnit(u.diaMm.toDouble(), unit) else "—"
                                    )
                                    DiaCalloutStation(
                                        key = n.id,
                                        stationX = xPx(anchorById[n.id] ?: ((n.startMm + n.endMm) / 2f)),
                                        label = label,
                                        labelWidth = textPaint.measureText(label.primary),
                                    )
                                }
                                val plan = planDiaCallouts(stations, 4f, size.width - 4f, minGap = 6.dp.toPx())
                                val placed = plan.finish(
                                    row0Top = profileTopPx + profileRowPx + UNDERCUT_DIA_ROW0_GAP_PX + cutNumberGapPx,
                                    labelTextHeight = textPaint.textSize,
                                    rowGap = 4f,
                                    surfaceYAt = { i ->
                                        val n = notches.first { it.id == plan.stations[i].key }
                                        cy + rPx(n.floorDiaMm) + 3f
                                    },
                                    leaderStartGap = 1f,
                                )
                                placed.forEach { p ->
                                    for (s in 0 until p.leader.size - 1) {
                                        drawLine(
                                            leaderColor,
                                            Offset(p.leader[s].x, p.leader[s].y),
                                            Offset(p.leader[s + 1].x, p.leader[s + 1].y),
                                            1.dp.toPx(),
                                        )
                                    }
                                    val fm = textPaint.fontMetrics
                                    // textPaint is CENTER-aligned, so x is the label centre.
                                    drawContext.canvas.nativeCanvas.drawText(
                                        p.label.inline(), p.labelCx, p.labelTopY - fm.ascent, textPaint,
                                    )
                                }
                            }

                            // ── Cut numbers — an input aid, always drawn here and printed on no
                            // sheet (on-device ruling); a pending draft has none. After the Ø
                            // callouts, so an outside number's halo knocks out a leader crossing it ──
                            drawUndercutCutNumbers(
                                notches = notches,
                                numbers = cutNumbers,
                                xPx = xPx,
                                rPx = rPx,
                                cy = cy,
                                textSizePx = textPaint.textSize,
                            )
                        }
                        // Shown only while zoomed: at the fitted view there is nothing to reset,
                        // and the corner belongs to the drawing.
                        if (zoomScale != 1f || zoomOffset != Offset.Zero) {
                            IconButton(
                                onClick = { resetZoom() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.6f), CircleShape)
                                    .testTag("undercut_detail_reset_view"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset view",
                                    tint = Color.Black.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }

                    // ── Shaft-direction reference + the floating save pill ────────────
                    // AFT at the left, FWD at the right, the pill riding the empty middle. It sits
                    // here — the boundary band between the canvas and the carousel — rather than
                    // inside the canvas: bottom-anchored inside it, the pill would sit on top of the
                    // Ø callouts, and inside a card it would scroll out of reach (the reason the
                    // per-card Confirm/Cancel row was missed on-device).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The options panel stops here, so the drawing and the pill stay in
                            // view above it.
                            .onGloballyPositioned { drawingBottomPx = it.boundsInRoot().bottom },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "← AFT",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "FWD →",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        UndercutStatusPill(
                            state = when {
                                activeDraft == null -> UndercutPillState.Saved
                                activeDraftIssue != null -> UndercutPillState.Blocked(activeDraftIssue)
                                else -> UndercutPillState.Unsaved(pending = activeDraft.isPending)
                            },
                            onConfirm = { confirmSelected() },
                            onDiscard = { activeDraft?.let { cancelDraft(it.id) } },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp),
                        )
                    }

                    // Authoring entry point. On a liner strip this is the reason a liner with no cuts
                    // yet is tappable on the overview at all ([seedPendingCut]).
                    Button(
                        onClick = { seedPendingCut() },
                        enabled = PENDING_UNDERCUT_ID !in drafts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                if (stripLiner != null) "undercut_add_in_liner" else "undercut_add_in_strip",
                            ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (stripLiner != null) "Add undercut in this liner" else "Add undercut here")
                    }
                }

                HorizontalDivider()

                // ── Card carousel — swipe through the cuts, aft → fwd ────────────
                if (pageIds.isEmpty()) {
                    Text(
                        text = "No undercuts recorded here yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("undercut_card_pager"),
                        // A sliver of the neighbouring cards, so a swipe is discoverable.
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 8.dp,
                        // Key pages by cut id so a card's own state (scroll, focus, field text)
                        // follows the cut when the order changes, instead of staying positional.
                        key = { page -> pageIds.getOrNull(page) ?: page },
                    ) { page ->
                        val id = pageIds.getOrNull(page) ?: return@HorizontalPager
                        val baseline = baselines[id]
                        val draft = drafts[id] ?: baseline ?: return@HorizontalPager
                        // "Next cut" can replace one pending card with the next under the same page
                        // id; keying the pending card on its seed gives the new cut fresh card state
                        // (scroll, the "Past the liner edge" flags) rather than the last one's.
                        key(if (draft.isPending) pendingSeed else null) {
                            UndercutDraftCard(
                                title = if (draft.isPending) "New undercut"
                                        else undercutCardTitle(page + 1, pageIds.size),
                                draft = draft,
                                dirty = draft != baseline,
                                selected = id == selectedId,
                                unit = unit,
                                oalMm = oalMm,
                                segs = segs,
                                aftSetXMm = aftSetXMm,
                                fwdSetXMm = fwdSetXMm,
                                linerSpans = linerSpans,
                                stripLiner = stripLiner,
                                sheetUndercuts = undercutRecord.undercuts,
                                buttTargets = pageButtTargets.filter { (_, s) -> s.id != id },
                                pagePositionById = pagePositionById,
                                nextCut = undercutNextCutState(
                                    draft = draft,
                                    blocked = draft != baseline && confirmIssueOf(id, draft) != null,
                                    otherPendingOpen = !draft.isPending &&
                                        (pendingSeed != null || PENDING_UNDERCUT_ID in drafts),
                                    oalMm = oalMm,
                                ),
                                onNextCut = { nextCutFrom(id) },
                                onDraftChange = { updated -> writeDraft(id, updated) },
                                flushActive = { flushGate.active },
                                overhangAnswer = overhangAnswers[id],
                                onOverhangAnswerTaken = { overhangAnswers = overhangAnswers - id },
                                modifier = Modifier.onFocusChanged { f ->
                                    if (f.hasFocus) focusedCardId = id
                                    else if (focusedCardId == id) focusedCardId = null
                                },
                                onDelete = if (draft.isPending) null else {
                                    deleteCard@{
                                        // The trash deletes only the SELECTED card, and only once the
                                        // pager has settled on it: a card still sliding in or out is
                                        // not the one in front of the machinist. Otherwise it brings
                                        // the selected card back to the front and deletes nothing.
                                        if (id != selectedId || pagerState.isScrollInProgress) {
                                            val selPage = pageIdsLive.value.indexOf(selectedId)
                                            if (selPage >= 0) {
                                                snackbarScope.launch { pagerState.scrollToPage(selPage) }
                                            }
                                            return@deleteCard
                                        }
                                        // Captured BEFORE the removal: the Undo puts back this exact cut
                                        // at this exact record position.
                                        val index = undercutRecord.undercuts.indexOfFirst { it.id == id }
                                        val removed = undercutRecord.undercuts.getOrNull(index)
                                        drafts = drafts - id
                                        // The selection is left alone: the heal adopts the card the
                                        // pager shows once the page goes. Clearing it would run the
                                        // leave sweep over every OTHER draft as if the machinist had
                                        // left them all, committing or prompting on cards never left.
                                        onRemoveUndercut(id)
                                        if (removed != null) {
                                            snackbarScope.launch {
                                                // One pending delete at a time: a second delete replaces
                                                // the first's offer rather than queueing behind it.
                                                deleteSnackbar.currentSnackbarData?.dismiss()
                                                val result = deleteSnackbar.showSnackbar(
                                                    message = "Undercut deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Long,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onRestoreUndercut(removed, index)
                                                    reselectAfterRestoreId = removed.id
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Delete Undo ──────────────────────────────────────────────────
            // Bottom of the screen = the card carousel's lower edge, clear of the top bar, the
            // canvas and the status pill above the carousel; the short message keeps it one line
            // over the card it briefly covers.
            SnackbarHost(
                hostState = deleteSnackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("undercut_delete_snackbar"),
            )

            // ── Drawing options — the undercut preview's panel, over this page ──────────
            // Composed last so it sits over the cards and takes the touches that land on it,
            // with no scrim: the canvas above it stays live and restyles as the options change.
            // Its own Back handler, registered after this overlay's, closes it first.
            if (showOptions && optionsSheet != null) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    PreviewOptionsPanel(
                        onClose = { showOptions = false },
                        maxHeightDp = optionsMaxHeightDp.dp,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        optionsSheet()
                    }
                }
            }
        }
    }

    // ── "Past the liner edge", for an overhang a flush landed ────────────────
    // The card's own question, raised here because the commit or close it holds up can take the
    // card away (the add flow's card on Confirm, every card on Close). Same strings, same tags,
    // answerable only by its two buttons.
    overlayOverhang?.let { ask ->
        UndercutOverhangDialog(
            aftAmount = ask.aftAmount,
            fwdAmount = ask.fwdAmount,
            onAccept = { answerOverlayOverhang(ask, declined = false) },
            onDecline = { answerOverlayOverhang(ask, declined = true) },
        )
    }

    // ── Blocked draft on the way out ─────────────────────────────────────────
    // The one case a leave cannot resolve by itself. Neither button is destructive by default:
    // dismissing (tap-outside / system back) is "Keep editing", so an edit is never lost to a
    // stray tap; only "Discard" drops it.
    leavePrompt?.let { prompt ->
        val keepEditing = {
            leavePrompt = null
            // Back to the blocked card, wherever the machinist had got to. Landing there is not
            // them LEAVING the card they were taken to, so that selection change must not be
            // handled as one — otherwise stepping back off a pending add page would commit the
            // add nobody asked for.
            if (selectedId != prompt.draftId && prompt.draftId in pageIdsLive.value) {
                skipLeaveOnce = true
                selectedId = prompt.draftId
            }
        }
        AlertDialog(
            onDismissRequest = keepEditing,
            title = { Text("Undercut can't be saved") },
            text = { Text(prompt.reason) },
            confirmButton = {
                TextButton(onClick = keepEditing, modifier = Modifier.testTag("undercut_leave_keep")) {
                    Text("Keep editing")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cancelDraft(prompt.draftId)
                        // Dropping this one RESUMES the sweep it interrupted: a second unsettled
                        // draft gets its own question rather than riding out on this answer.
                        finishSettle(
                            settleDraftsExcept(
                                keepId = if (prompt.closing) null else selectedId,
                                closing = prompt.closing,
                            ),
                            prompt.closing,
                        )
                    },
                    modifier = Modifier.testTag("undercut_leave_discard"),
                ) {
                    Text("Discard")
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Draft state — a card's local edit, previewed on the canvas, applied on Confirm
// ─────────────────────────────────────────────────────────────────────────────

/** Page id of the add flow's not-yet-recorded card; no `Undercut` ever carries it. */
internal const val PENDING_UNDERCUT_ID = "__undercut_draft__"

/** Where the overlay's settle-everything leave ends: out of the overlay, or on the PDF preview over it. */
private enum class UndercutOverlayExit { CLOSE, PREVIEW }

/**
 * Content cap for the detail page's options panel, dp: the screen below the drawing block (canvas
 * and save pill, whose bottom is [drawingBottomDp]) less the panel's [chromeDp] — its header row
 * and the navigation-bar inset — so the drawing being restyled stays in view above the panel.
 * Clamped between the tuning panel's [TUNING_SHEET_MIN_FRAC] floor (a usable panel on a short
 * screen, which then overlaps the drawing's foot) and the preview's [PREVIEW_SHEET_MAX_FRAC]
 * ceiling. Before the drawing block has been measured ([drawingBottomDp] 0) the ceiling applies.
 */
internal fun undercutDetailOptionsMaxHeightDp(
    screenHeightDp: Float,
    drawingBottomDp: Float,
    chromeDp: Float,
): Float = (screenHeightDp - drawingBottomDp - chromeDp)
    .coerceAtLeast(screenHeightDp * TUNING_SHEET_MIN_FRAC)
    .coerceAtMost(screenHeightDp * PREVIEW_SHEET_MAX_FRAC)

/** Height of the strip profile band; the rails and Ø callouts add their own rows above/below. */
private val UNDERCUT_PROFILE_ROW_DP = 140.dp

/** The profile row's bottom → the first Ø callout label row's top, px (before any cut-number gap). */
private const val UNDERCUT_DIA_ROW0_GAP_PX = 2f

/**
 * One card's in-progress values — everything the card can change, held locally until Confirm.
 * A draft whose [id] is [PENDING_UNDERCUT_ID] is not in the record at all (the add flow); every
 * other draft shadows the stored [Undercut] of the same id.
 *
 * [reference]/[referenceLinerId] are the "Measure From" chips: they re-project the DISPLAYED
 * Distance immediately, never the canonical [startFromAftMm] — and, like every other field here,
 * reach the record only on Confirm, so Cancel reverts them too.
 */
internal data class UndercutDraft(
    val id: String,
    val startFromAftMm: Float,
    val lengthMm: Float,
    val diaMm: Float,
    val note: String,
    val reference: UndercutReference,
    val referenceLinerId: String,
    /** The parent cut a `PARENT_*` reference measures from; empty for every other reference. */
    val referenceCutId: String = "",
) {
    val isPending: Boolean get() = id == PENDING_UNDERCUT_ID
}

/**
 * The draft a stored [u] starts from — its values plus the reference the card actually displays
 * ([effectiveUndercutReference], so a `LINER_*` reference whose liner is gone shows its AFT_SET
 * fallback). Equality against this baseline is the card's dirty test, which is why the fallback
 * belongs here: a card that only displays the fallback is NOT dirty and never rewrites the stored
 * reference behind the machinist's back.
 *
 * The buried-liner-edge fallback is deliberately NOT applied here: it depends on the spans being
 * previewed (the card's own typed Length can bury or uncover its edge), so the card applies it
 * live on top of the draft's reference and the baseline keeps the stored one. The parent-cut
 * fallback (parent gone, or no longer containing the cut) is span-dependent too, and is likewise
 * left to the card.
 */
internal fun undercutDraftOf(u: Undercut, linerSpans: List<UndercutLinerSpan>): UndercutDraft {
    val ref = effectiveUndercutReference(u, linerSpans)
    val usesLiner = ref == UndercutReference.LINER_AFT || ref == UndercutReference.LINER_FWD
    return UndercutDraft(
        id = u.id,
        startFromAftMm = u.startFromAftMm,
        lengthMm = u.lengthMm,
        diaMm = u.diaMm,
        note = u.note,
        reference = ref,
        referenceLinerId = if (usesLiner) u.referenceLinerId else "",
        referenceCutId = if (isParentUndercutReference(ref)) u.referenceCutId else "",
    )
}

/** The draft as a drawable [Undercut] — preview only; nothing built here is ever stored. */
internal fun UndercutDraft.toUndercut(): Undercut = Undercut(
    id = id,
    startFromAftMm = startFromAftMm,
    lengthMm = lengthMm,
    diaMm = diaMm,
    authoredReference = reference,
    referenceLinerId = referenceLinerId,
    note = note,
    referenceCutId = referenceCutId,
)

/**
 * The card's "Whole liner" preset: the draft spans exactly [liner] and authors its Distance from
 * that liner's AFT edge, so the Distance field then reads 0. A one-tap preset applied only on the
 * tap, never on its own.
 */
internal fun UndercutDraft.withWholeLiner(liner: UndercutLinerSpan): UndercutDraft = copy(
    startFromAftMm = liner.startMm,
    lengthMm = liner.endMm - liner.startMm,
    reference = UndercutReference.LINER_AFT,
    referenceLinerId = liner.id,
    referenceCutId = "",
)

/**
 * The card's "Start at end of cut N" preset: the draft's AFT end moved onto [other]'s FWD end, or
 * [gapMm] past it — the typed "Gap after cut N" ([undercutStartAtEndOf]). Length and reference are
 * untouched, so the Distance field re-seeds to the new position under the same datum. Shaft bounds
 * are the card's check.
 */
internal fun UndercutDraft.startingAtEndOf(other: UndercutSpanMm, gapMm: Float = 0f): UndercutDraft = copy(
    startFromAftMm = undercutStartAtEndOf(other.endMm, gapMm),
)

/**
 * The card's "End at start of cut N" preset: the draft's FWD end moved onto [other]'s AFT end, or
 * [gapMm] short of it ([undercutEndAtStartOf]), length and reference untouched — the mirror of
 * [startingAtEndOf].
 */
internal fun UndercutDraft.endingAtStartOf(other: UndercutSpanMm, gapMm: Float = 0f): UndercutDraft = copy(
    startFromAftMm = undercutEndAtStartOf(other.startMm, lengthMm, gapMm),
)

/**
 * The edge-to-edge chip a card's gap field measures from: [cutId]'s FWD end when [after] (the
 * "Start at end of" row), its AFT end otherwise (the "End at start of" row). Card-local screen
 * state, never stored — the gap itself is a view of the canonical span against that edge.
 */
internal data class UndercutGapAnchor(val after: Boolean, val cutId: String)

/** The gap field's label against cut [position]: "Gap after Cut 1 (in)" / "Gap before Cut 2 (in)". */
internal fun undercutGapFieldLabel(after: Boolean, position: Int, unitAbbr: String): String =
    "Gap ${if (after) "after" else "before"} ${undercutButtChipLabel(position)} ($unitAbbr)"

/** Whether a card's "Next cut" can act, and the caption under it when stock is what stops it. */
internal data class UndercutNextCutState(val enabled: Boolean, val caption: String?)

/** The "Next cut" caption when the shaft ends before a default-length cut fits FWD of this one. */
internal const val UNDERCUT_NO_STOCK_FWD_MSG = "No stock FWD of this cut"

/**
 * A card's "Next cut" state for [draft] as it stands: disabled while the draft is [blocked] (the
 * pill already says why — no second validator here), while ANOTHER card holds the one pending add
 * ([otherPendingOpen]), and when no default-length cut fits FWD of the draft on the shaft
 * ([undercutNextCutSeed]) — the one case it explains itself, [UNDERCUT_NO_STOCK_FWD_MSG].
 */
internal fun undercutNextCutState(
    draft: UndercutDraft,
    blocked: Boolean,
    otherPendingOpen: Boolean,
    oalMm: Float,
): UndercutNextCutState {
    val noStock = undercutNextCutSeed(draft.toUndercut(), oalMm, DEFAULT_UNDERCUT_LENGTH_MM) == null
    return UndercutNextCutState(
        enabled = !blocked && !noStock && !otherPendingOpen,
        caption = if (noStock && !blocked) UNDERCUT_NO_STOCK_FWD_MSG else null,
    )
}

/**
 * A PENDING draft for a new cut at `[startMm, startMm + lengthMm]` that reads from [from]'s datum —
 * the butted "Add undercut…" seed and the "Next cut" seed. The datum is taken as it DISPLAYS at the
 * new span ([effectiveUndercutReference] over [sheetSpans], every cut on the sheet render-clamped):
 * a liner edge some cut has buried, or a parent cut that does not contain the new span, gives way
 * to the nearer S.E.T., so the seed never stores a datum its own card cannot show. Ø unentered,
 * no note. A draft only — nothing is recorded until it is confirmed.
 */
internal fun undercutSeedInheriting(
    startMm: Float,
    lengthMm: Float,
    from: UndercutDraft,
    linerSpans: List<UndercutLinerSpan>,
    sheetSpans: List<UndercutSpanMm>,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): UndercutDraft {
    val probe = UndercutDraft(
        id = PENDING_UNDERCUT_ID,
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = 0f,
        note = "",
        reference = from.reference,
        referenceLinerId = from.referenceLinerId,
        referenceCutId = from.referenceCutId,
    )
    val pool = sheetSpans.filter { it.id != PENDING_UNDERCUT_ID } +
        UndercutSpanMm(PENDING_UNDERCUT_ID, startMm, startMm + lengthMm)
    val ref = effectiveUndercutReference(probe.toUndercut(), linerSpans, pool, aftSetXMm, fwdSetXMm)
    return probe.copy(
        reference = ref,
        referenceLinerId = if (ref == UndercutReference.LINER_AFT || ref == UndercutReference.LINER_FWD) {
            from.referenceLinerId
        } else {
            ""
        },
        referenceCutId = if (isParentUndercutReference(ref)) from.referenceCutId else "",
    )
}

/**
 * A stored cut's card title. The number is the cut's POSITION aft → fwd on this strip (the
 * printed sheet carries no cut numbers), and the title says so — a new cut landing between two
 * others takes the middle number (on-device report: "Undercut 2 of 3" read as the wrong cut).
 * "Cut N" is the same wording the edge-to-edge chips ([undercutButtChipLabel]) and the tab's list
 * rows use, so one cut reads by one name everywhere on the screen (usability review).
 */
internal fun undercutCardTitle(position: Int, count: Int): String =
    "Cut $position of $count"

/**
 * The card's Parent AFT / Parent FWD chip: the Distance re-authored from [parent]'s shoulder
 * ([reference] is `PARENT_AFT` or `PARENT_FWD`). The liner key is dropped; canonical never moves —
 * a reference switch re-projects the displayed Distance only.
 */
internal fun UndercutDraft.measuredFromParent(
    reference: UndercutReference,
    parent: UndercutSpanMm,
): UndercutDraft = copy(reference = reference, referenceCutId = parent.id, referenceLinerId = "")

/** An edge-to-edge preset chip: the other cut by its page position, the card title's number. */
internal fun undercutButtChipLabel(position: Int): String = "Cut $position"

/** What a refused one-tap preset says: it changed nothing, and why. */
internal fun undercutPresetRefusal(reason: String): String = "Not applied — $reason"

/** A per-end pair of flags against a liner's two edges — the "Past the liner edge" state. */
internal data class UndercutLinerEnds(val aft: Boolean, val fwd: Boolean) {
    fun with(other: UndercutLinerEnds) = UndercutLinerEnds(aft || other.aft, fwd || other.fwd)
    fun without(other: UndercutLinerEnds) = UndercutLinerEnds(aft && !other.aft, fwd && !other.fwd)

    companion object {
        val NONE = UndercutLinerEnds(aft = false, fwd = false)
    }
}

/**
 * The "Past the liner edge" question for the ends a commit newly carried off the liner — [aft]
 * and [fwd] are the formatted overhangs ("0.500 in"), null for an end not being asked about.
 * One sentence naming each end asked about, then a plain yes/no: the machinist is confirming what
 * the typed numbers describe, not choosing a drafting operation (usability review).
 */
internal fun undercutOverhangQuestion(aft: String?, fwd: String?): String {
    val runs = when {
        aft != null && fwd != null -> "$aft past the liner's AFT edge and $fwd past its FWD edge"
        aft != null -> "$aft past the liner's AFT edge"
        fwd != null -> "$fwd past the liner's FWD edge"
        else -> "past the liner's edge"
    }
    return "This cut runs $runs into the shaft. Is that right?"
}

/**
 * The card's warning for a Measured Ø at or above what the cut is taken against — the shaft
 * surface, or the surrounding cut's floor when [nested] — naming that figure ([dia] formatted,
 * [unitAbbr] its unit).
 */
internal fun undercutDiaNotBelowSurfaceWarning(nested: Boolean, dia: String, unitAbbr: String): String =
    if (nested) "Ø isn't smaller than the surrounding cut's floor here (Ø $dia $unitAbbr) — check the number"
    else "Ø isn't smaller than the shaft here (Ø $dia $unitAbbr) — check the number"

/** The Measure From caption under a disabled liner-edge chip — [end] AFT or FWD. */
internal fun undercutBuriedEdgeCaption(end: String): String =
    "Liner $end edge is cut into the body — measure from a S.E.T."

/** The card's standing warning after "No, let me check" — [amount] formatted with its unit, [end] AFT or FWD. */
internal fun undercutOverhangWarning(amount: String, end: String): String =
    "Runs $amount past the liner's $end edge — check Distance and Length"

/** The notice when an Undo takes away a cut whose card held unsaved edits. */
internal const val UNDERCUT_DISCARDED_DRAFT_MSG = "Unsaved changes to a removed cut were discarded"

/**
 * The result of typing an "Extend past liner" value: the new [draft], the extension actually
 * applied ([appliedMm], what the field re-seeds to), and whether the typed value asked for more
 * stock than the shaft has ([limited] — the card then says so).
 */
internal data class UndercutExtensionEdit(
    val draft: UndercutDraft,
    val appliedMm: Float,
    val limited: Boolean,
)

/**
 * The draft after typing an "Extend past liner" AFT value of [extMm] against [liner] — FWD end
 * pinned ([undercutSpanWithAftExtension]). The extension is limited to the stock between the
 * shaft's AFT end and the liner ([maxUndercutAftExtensionMm] / [clampUndercutExtension]): a cut
 * can only run into material that is there. The reference is untouched: the extension is another
 * view of the same span, not a datum.
 */
internal fun UndercutDraft.withAftLinerExtension(
    liner: UndercutLinerSpan,
    extMm: Float,
    @Suppress("UNUSED_PARAMETER") oalMm: Float,
): UndercutExtensionEdit {
    val clamp = clampUndercutExtension(extMm, maxUndercutAftExtensionMm(liner.startMm))
    val (s, l) = undercutSpanWithAftExtension(startFromAftMm, lengthMm, liner.startMm, clamp.appliedMm)
    return UndercutExtensionEdit(copy(startFromAftMm = s, lengthMm = l), clamp.appliedMm, clamp.limited)
}

/**
 * Mirror of [withAftLinerExtension] for the FWD edge — AFT end pinned
 * ([undercutSpanWithFwdExtension]), limited to the stock between the liner and [oalMm]
 * ([maxUndercutFwdExtensionMm]).
 */
internal fun UndercutDraft.withFwdLinerExtension(
    liner: UndercutLinerSpan,
    extMm: Float,
    oalMm: Float,
): UndercutExtensionEdit {
    val clamp = clampUndercutExtension(extMm, maxUndercutFwdExtensionMm(liner.endMm, oalMm))
    val (s, l) = undercutSpanWithFwdExtension(startFromAftMm, lengthMm, liner.endMm, clamp.appliedMm)
    return UndercutExtensionEdit(copy(startFromAftMm = s, lengthMm = l), clamp.appliedMm, clamp.limited)
}

/**
 * [list] with [draft] substituted for the cut it shadows — or appended when it is the add flow's
 * pending card, which has no stored counterpart. Result is aft → fwd, so notches, the chained
 * rail, and the Ø callouts all read the previewed position. Display-only: the caller passes the
 * result to the draw pipeline, never to the record.
 */
internal fun applyUndercutDraft(list: List<Undercut>, draft: UndercutDraft?): List<Undercut> {
    if (draft == null) return list
    val hit = list.any { it.id == draft.id }
    val merged = if (hit) {
        list.map { u -> if (u.id == draft.id) draft.toUndercut().copy(id = u.id) else u }
    } else {
        list + draft.toUndercut()
    }
    return merged.sortedBy { it.startFromAftMm }
}

/**
 * Carousel page ids for one strip, aft → fwd — the machinist's reading order (proximity to the
 * AFT edge). Ordering is keyed on **stored** starts and the pending card's fixed seed position,
 * so a card moves only when its draft is CONFIRMED; typing a new Distance must not slide the card
 * out from under the field being typed in.
 */
internal fun buildUndercutPageIds(
    stored: List<Undercut>,
    pendingSeedStartMm: Float?,
): List<String> {
    val keyed = stored.map { it.startFromAftMm to it.id } +
        (pendingSeedStartMm?.let { listOf(it to PENDING_UNDERCUT_ID) } ?: emptyList())
    // sortedBy is stable, so a pending card ties to the FWD side of an equally-placed stored cut.
    return keyed.sortedBy { it.first }.map { it.second }
}

/** A default span for a newly drafted cut, in canonical shaft mm. */
internal data class UndercutDraftSpan(val startMm: Float, val lengthMm: Float)

/**
 * Where a new cut is drafted inside `[rangeStartMm, rangeEndMm]` (a liner's span, or a free
 * strip's window): centred in the aft-most gap wide enough for [preferredLengthMm], else centred
 * in the widest gap left (shortened to fit). [occupied] is every recorded cut on the sheet, so a
 * draft never opens already overlapping a neighbour — which would block Confirm before a single
 * value had been typed. A fully occupied range falls back to the range start at full length; the
 * card then shows the overlap block until the machinist types real numbers.
 */
internal fun defaultUndercutSpan(
    rangeStartMm: Float,
    rangeEndMm: Float,
    occupied: List<UndercutSpanMm>,
    oalMm: Float,
    preferredLengthMm: Float = DEFAULT_UNDERCUT_LENGTH_MM,
): UndercutDraftSpan {
    val hiBound = oalMm.coerceAtLeast(0f)
    val lo = rangeStartMm.coerceIn(0f, hiBound)
    val hi = rangeEndMm.coerceIn(lo, hiBound)

    val gaps = mutableListOf<Pair<Float, Float>>()
    var cursor = lo
    occupied.filter { it.endMm > lo && it.startMm < hi }
        .sortedBy { it.startMm }
        .forEach { s ->
            if (s.startMm > cursor) gaps += cursor to min(s.startMm, hi)
            cursor = max(cursor, s.endMm)
        }
    if (cursor < hi) gaps += cursor to hi

    val gap = gaps.firstOrNull { it.second - it.first >= preferredLengthMm }
        ?: gaps.maxByOrNull { it.second - it.first }
    val width = if (gap == null) 0f else gap.second - gap.first
    val lengthMm = (if (width > 0f) min(preferredLengthMm, width) else preferredLengthMm)
        .coerceAtLeast(MIN_UNDERCUT_DRAFT_LENGTH_MM)
    val startMm = if (gap == null) lo else gap.first + ((width - lengthMm) / 2f).coerceAtLeast(0f)
    return UndercutDraftSpan(
        startMm = startMm.coerceIn(0f, (hiBound - lengthMm).coerceAtLeast(0f)),
        lengthMm = lengthMm,
    )
}

/** Floor on a drafted length, so a zero-width liner can't seed a degenerate (unconfirmable) cut. */
internal const val MIN_UNDERCUT_DRAFT_LENGTH_MM = 0.1f

/**
 * The blocking reason a draft cannot be confirmed, or `null` when it can: the shaft-bounds check
 * ([undercutSpanIssue]) first, then the adjacency check ([undercutOverlapIssue]) against every
 * OTHER cut on the sheet — which passes a cut sitting fully INSIDE another (a nested relief) or
 * fully clear of it, and blocks only a partial intrusion. Both are confirm-time gates on new
 * values only — nothing already stored is retroactively rejected.
 *
 * With [pagePositionById] (each stored cut's 1-based page position) the overlap reason NAMES the
 * partner ([undercutOverlapMessage] — "Overlaps cut 3 …", or "a cut outside this strip" when the
 * partner has no page here). The pill, the card's inline warning and the leave prompt all read
 * this one result, so all three say the same thing. Without it, the generic wording.
 */
internal fun undercutConfirmIssue(
    draft: UndercutDraft,
    oalMm: Float,
    otherSpans: List<UndercutSpanMm>,
    pagePositionById: Map<String, Int>? = null,
): String? {
    undercutSpanIssue(draft.startFromAftMm, draft.lengthMm, oalMm)?.let { return it }
    if (pagePositionById == null) {
        return undercutOverlapIssue(draft.startFromAftMm, draft.lengthMm, otherSpans)
    }
    val partnerId = undercutOverlapPartnerId(draft.startFromAftMm, draft.lengthMm, otherSpans)
        ?: return null
    return undercutOverlapMessage(pagePositionById[partnerId])
}

/**
 * Every cut on the sheet EXCEPT [excludeId], as render-clamped spans — the adjacency pool
 * [undercutConfirmIssue] checks against. One helper so the canvas's preview status, the card's
 * inline reason, the pill, and the leave decision all measure the same thing.
 */
internal fun undercutOtherSpans(
    all: List<Undercut>,
    excludeId: String,
    oalMm: Float,
): List<UndercutSpanMm> = all
    .filter { it.id != excludeId }
    .map { u ->
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
        UndercutSpanMm(u.id, c.startMm, c.endMm)
    }
    .filter { it.endMm > it.startMm }

/**
 * The adjacency pool for [draft] held under the page id [heldUnderId]: [undercutOtherSpans] less
 * the draft's own cut. A draft's id IS its page id (the overlay's `writeDraft` keys every entry by
 * it), so the second exclusion is a last line of defence: a draft held under a page whose own cut
 * is in its pool would be checked against itself and block with an overlap naming the card it is
 * shown on (on-device report). Such a pool is stale; the self entry is dropped and breadcrumbed.
 */
internal fun undercutDraftPool(
    all: List<Undercut>,
    draft: UndercutDraft,
    heldUnderId: String?,
    oalMm: Float,
): List<UndercutSpanMm> {
    val pool = undercutOtherSpans(all, draft.id, oalMm)
    if (heldUnderId == null || heldUnderId == draft.id || pool.none { it.id == heldUnderId }) return pool
    AppLog.w(UNDERCUT_DETAIL_LOG_TAG, "a draft's overlap pool held its own card's cut")
    return pool.filterNot { it.id == heldUnderId }
}

/** Breadcrumb tag for the undercut detail page (`util/AppLog` — events only, never values). */
internal const val UNDERCUT_DETAIL_LOG_TAG = "UndercutDetail"

/** What leaving a card does with its draft. */
internal enum class UndercutLeaveAction {
    /** Nothing to save — no draft, or one that still equals stored values. */
    NONE,

    /** Commit it, through the same path the pill's tap takes (values verbatim, golden rule). */
    COMMIT,

    /** Drop it silently: an "Add undercut" card left exactly as seeded — nothing was typed. */
    DISCARD,

    /** Ask: a blocked draft is neither committed nor dropped behind the machinist's back. */
    PROMPT,
}

/**
 * The leave decision for one card: dirty × blocked. [draft] is the card's local edit (null when it
 * has none), [baseline] the stored values it shadows (null for the add flow's pending card, which
 * is therefore always dirty), and [confirmIssue] the result of [undercutConfirmIssue] on that
 * draft. Pure, so the swipe, the notch tap, and the overlay close all resolve identically.
 *
 * [pendingSeed] is the add flow's draft exactly as it was seeded: a PENDING draft still equal to
 * it is DISCARDED, never committed and never asked about — the machinist typed nothing, and
 * committing the seeded default recorded cuts nobody authored (on-device report). A pending draft
 * changed in any field resolves like any other dirty draft.
 */
internal fun undercutLeaveAction(
    draft: UndercutDraft?,
    baseline: UndercutDraft?,
    confirmIssue: String?,
    pendingSeed: UndercutDraft? = null,
): UndercutLeaveAction = when {
    draft == null || draft == baseline -> UndercutLeaveAction.NONE
    draft.isPending && draft == pendingSeed -> UndercutLeaveAction.DISCARD
    confirmIssue != null -> UndercutLeaveAction.PROMPT
    else -> UndercutLeaveAction.COMMIT
}

/** The shaft-space range a new cut is drafted into: a liner strip's liner, else its window. */
private fun undercutAddRangeOf(
    strip: UndercutStrip,
    stripLiner: UndercutLinerSpan?,
): Pair<Float, Float> = when {
    stripLiner != null -> stripLiner.startMm to stripLiner.endMm
    else -> strip.drawStartMm to strip.drawEndMm
}

// ─────────────────────────────────────────────────────────────────────────────
// Undercut card — one carousel page
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One card in the carousel, editing a local [UndercutDraft]. Every control writes to the draft
 * through [onDraftChange]. The card has **no save controls**: committing and discarding live on
 * the overlay's floating [UndercutStatusPill], and leaving the card commits a clear draft by
 * itself — a Confirm/Cancel row inside this scroll was easy to miss and easy to forget (on-device
 * report). [onDelete] is the card's one immediate record action, and is null on the add flow's
 * pending card (nothing recorded to delete). "Next cut" ([onNextCut]) is the one exception to "no
 * save controls": it runs the pill's own Confirm for this card, then opens the next cut's seed.
 *
 * Order, top to bottom: the title and delete; the warnings; Measure From; Distance, Length and
 * Measured Ø (the three numbers a cut is recorded by, first — usability review); Notes; the
 * blocking reason; "Next cut"; and, collapsed behind "More ways to place this cut", the span
 * presets (Whole liner, Extend past liner, Start at end of / End at start of).
 *
 * Numeric fields keep the commit-on-blur contract (`docs/contracts/NumberField.md`); the commit lands in the
 * draft rather than the ViewModel. **No field on this card ever reverts a typed value**: the fields
 * carry no validator, so anything that parses lands in the draft verbatim and the canvas previews
 * it. ALL span validity — shaft bounds, a positive length, no partial overlap — is confirm-time
 * ([undercutConfirmIssue]): stated inline under the fields and on the pill, and leaving the card
 * raises the Keep editing / Discard prompt. A field-level refusal would put the previous value back
 * in the box with only the rule beside it — an input modified on error with no notification
 * (on-device report). Only text that does not parse is refused, and the field names it
 * (`"<typed> not applied — not a number"`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UndercutDraftCard(
    title: String,
    draft: UndercutDraft,
    dirty: Boolean,
    selected: Boolean,
    unit: UnitSystem,
    oalMm: Float,
    segs: List<SurfaceSeg>,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    linerSpans: List<UndercutLinerSpan>,
    stripLiner: UndercutLinerSpan?,
    sheetUndercuts: List<Undercut>,
    /**
     * The OTHER stored cuts on this page as (1-based position aft → fwd, clamped span) — the
     * "Start at end of" / "End at start of" chips, numbered as the card titles are. Empty hides
     * both rows.
     */
    buttTargets: List<Pair<Int, UndercutSpanMm>>,
    /** Every stored cut's page position, so an overlap reason names its partner. */
    pagePositionById: Map<String, Int>,
    /** The "Next cut" button's state ([undercutNextCutState]); [onNextCut] runs it. */
    nextCut: UndercutNextCutState,
    onNextCut: () -> Unit,
    onDraftChange: (UndercutDraft) -> Unit,
    onDelete: (() -> Unit)?,
    /** True while the overlay is flushing focus — it asks "Past the liner edge" for that commit itself. */
    flushActive: () -> Boolean = { false },
    /** The overlay's answer to its own "Past the liner edge" question for this card, to take. */
    overhangAnswer: UndercutOverhangAnswer? = null,
    onOverhangAnswerTaken: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Which liner the LINER_* chips convert against: the draft's own reference liner while it
    // resolves, else the strip's liner, else whichever liner holds most of this cut.
    val previewUndercut = draft.toUndercut()
    val refLiner = undercutReferenceLinerFor(previewUndercut, linerSpans, stripLiner, oalMm)
    val refLinerStartMm = refLiner?.startMm ?: 0f
    val refLinerEndMm = refLiner?.endMm ?: 0f

    // A liner edge some cut on the sheet runs past into the body — this card's own previewed
    // span included, so a Length typed across the edge counts the moment it lands — has no step
    // left to measure from (on-device request): its chip is disabled with the reason under the
    // row, and a draft authored against it reads from the nearer S.E.T. ([effectiveUndercutReference],
    // display-only: the draft's own reference and canonical start never move for it).
    val buriedPool = remember(sheetUndercuts, draft, oalMm) {
        applyUndercutDraft(sheetUndercuts, draft).map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }
    val linerAftBuried = refLiner != null && undercutLinerEdgeBuried(refLiner.startMm, buriedPool)
    val linerFwdBuried = refLiner != null && undercutLinerEdgeBuried(refLiner.endMm, buriedPool)
    val shownReference = effectiveUndercutReference(
        previewUndercut, linerSpans, buriedPool, aftSetXMm, fwdSetXMm,
    )
    // The cut this draft sits fully inside on the sheet (the forest's smallest container) — the
    // Parent chips' datum, offered only while there is one (on-device request: a nested cut in a
    // relief that buried its liner's edges could otherwise be placed only from a S.E.T.). The
    // shoulders a PARENT_* Distance converts against are the cut it names, while that cut still
    // contains this one; otherwise the reference displays from a S.E.T. ([effectiveUndercutReference]).
    val parentCandidate = remember(buriedPool, draft.id) { undercutParentCandidate(draft.id, buriedPool) }
    val refParent = if (isParentUndercutReference(shownReference)) {
        undercutReferenceParentSpan(previewUndercut, buriedPool)
    } else {
        null
    }
    val refParentStartMm = refParent?.startMm ?: 0f
    val refParentEndMm = refParent?.endMm ?: 0f

    // ── "Past the liner edge" (on-device request) ────────────────────────────────────────
    // A Distance or Length commit that carries the cut past a liner edge it did not overhang
    // before is ASKED about: the machinist may not know the typed numbers run the cut off the
    // liner, or a measurement may be wrong. Nothing is rewritten either way — the draft is the
    // typed value from the moment of the commit; the answer only decides whether the overhang
    // reads as intended ("Yes, it does": the Extend field already shows it) or stays flagged
    // with a warning until that end is back on the liner ("No, let me check"). Confirm is never blocked: the
    // geometry is legal. Only those two fields ask — the Extend fields, Whole liner, Butt
    // against and the Measure From chips are explicit span choices or no span change at all.
    // Card-local, keyed on the cut's identity, so field re-seeds keep it.
    val overhangLiner = stripLiner ?: refLiner
    val (overhangAftMm, overhangFwdMm) = overhangLiner?.let {
        undercutLinerExtensionsMm(draft.startFromAftMm, draft.lengthMm, it.startMm, it.endMm)
    } ?: (0f to 0f)
    val overhangsAft = overhangAftMm > UNDERCUT_TOUCH_EPS_MM
    val overhangsFwd = overhangFwdMm > UNDERCUT_TOUCH_EPS_MM
    var overhangQuestion by remember(draft.id) { mutableStateOf<UndercutLinerEnds?>(null) }
    var overhangAcknowledged by remember(draft.id) { mutableStateOf(UndercutLinerEnds.NONE) }
    var overhangFlagged by remember(draft.id) { mutableStateOf(UndercutLinerEnds.NONE) }
    // An end back on the liner retires both its flag and its acknowledgement, so the next time
    // it runs off it is a new overhang and is asked about again.
    LaunchedEffect(draft.id, overhangsAft, overhangsFwd) {
        val onLiner = UndercutLinerEnds(aft = !overhangsAft, fwd = !overhangsFwd)
        overhangFlagged = overhangFlagged.without(onLiner)
        overhangAcknowledged = overhangAcknowledged.without(onLiner)
    }
    LaunchedEffect(overhangAnswer) {
        val answer = overhangAnswer ?: return@LaunchedEffect
        if (answer.declined) {
            overhangFlagged = overhangFlagged.with(answer.ends)
            overhangAcknowledged = overhangAcknowledged.without(answer.ends)
        } else {
            overhangAcknowledged = overhangAcknowledged.with(answer.ends)
            overhangFlagged = overhangFlagged.without(answer.ends)
        }
        onOverhangAnswerTaken()
    }
    fun commitSpanEdit(updated: UndercutDraft) {
        // A commit the overlay's flush drove is asked about at overlay level instead.
        if (!flushActive()) overhangLiner?.let { l ->
            val (aft, fwd) = newLinerOverhangs(
                draft.startFromAftMm, draft.lengthMm,
                updated.startFromAftMm, updated.lengthMm,
                l.startMm, l.endMm,
            )
            if (aft || fwd) overhangQuestion = UndercutLinerEnds(aft, fwd)
        }
        onDraftChange(updated)
    }
    fun overhangAmount(mm: Float): String = "${disp(mm, unit)} ${abbr(unit)}"

    // Non-blocking classifiers, read off the DRAFT so the card warns about what the canvas is
    // previewing. Neither rewrites anything: the canvas renders the clamped span, and an
    // implausible Ø is a measurement — golden rule, never adjusted.
    val staleOverrun = isUndercutStaleOverrun(draft.startFromAftMm, draft.lengthMm, oalMm)
    // The Ø the draft is cut against: the shaft's own surface for a top-level cut, and the
    // SURROUNDING cut's floor for one nested inside another — a nested cut removes material from
    // its parent's floor, so measuring it against the shaft surface would let a Ø that reaches
    // the relief floor (and draws nothing) pass unremarked. The exaggeration is irrelevant to
    // both terms, so the resolution runs at true scale.
    val draftFloors = remember(sheetUndercuts, draft, segs, oalMm) {
        resolveUndercutFloors(
            applyUndercutDraft(sheetUndercuts, draft), segs, oalMm, exaggerationFrac = 0f,
        ).firstOrNull { it.span.id == draft.id }
    }
    val surfaceDiaMm = draftFloors?.surfaceDiaMm ?: 0f
    val draftIsNested = draftFloors?.nesting?.parentId != null
    val diaAtOrAboveSurface =
        draft.diaMm > 0f && surfaceDiaMm > 0f && draft.diaMm >= surfaceDiaMm

    // Every OTHER cut on the sheet — not just this strip's: a liner strip's pad shows neighbouring
    // stock, and a cut can be typed straight into it.
    val otherSpans = remember(sheetUndercuts, oalMm, draft.id) {
        undercutOtherSpans(sheetUndercuts, draft.id, oalMm)
    }
    val confirmIssue = undercutConfirmIssue(draft, oalMm, otherSpans, pagePositionById)

    // ── "More ways to place this cut" ──────────────────────────────────────────────────
    // The span presets — Whole liner, Extend past liner, and the edge-to-edge chips with their gap
    // fields — sit behind ONE expander at the foot of the card, collapsed by default. Most shop
    // users walk up with three numbers — where the cut is, how long, what Ø — so those are the
    // first three fields, and a row of chips above them read as steps that had to be taken first
    // (usability review). Every piece of the presets' state is held here, outside the expander, so
    // a collapse loses nothing.
    //
    // On a liner strip the presets act on the STRIP's liner, the one the machinist opened and is
    // looking at — never a different liner the cut's stored reference happens to name; off a liner
    // strip they fall back to the reference liner. ("Measure From" keeps the reference liner: that
    // is a datum choice, not a span preset.)
    val spanLiner = stripLiner ?: refLiner
    val (extAftMm, extFwdMm) = spanLiner?.let {
        undercutLinerExtensionsMm(draft.startFromAftMm, draft.lengthMm, it.startMm, it.endMm)
    } ?: (0f to 0f)
    val isWholeLiner = spanLiner != null && undercutIsWholeLiner(
        draft.startFromAftMm, draft.lengthMm, spanLiner.startMm, spanLiner.endMm,
    )
    // A limited extension commit can leave the draft — and so `initialDisplay` — unchanged (typing
    // 4" twice against 2" of stock), and the field keys its text on `initialDisplay`, so a per-side
    // counter bumped on every limited commit forces the re-seed to the applied value.
    // The note holds the applied maximum and shows only while the field still reads it, so a later
    // Whole-liner tap or Distance edit retires a note that no longer describes the span.
    var aftLimitedMm by remember { mutableStateOf<Float?>(null) }
    var fwdLimitedMm by remember { mutableStateOf<Float?>(null) }
    var aftReseed by remember { mutableIntStateOf(0) }
    var fwdReseed by remember { mutableIntStateOf(0) }
    // The edge-to-edge chips' state: a refused preset's reason, and the applied chip whose gap
    // field is open.
    var buttIssue by remember(draft) { mutableStateOf<String?>(null) }
    var gapAnchor by remember(draft.id) { mutableStateOf<UndercutGapAnchor?>(null) }
    val anchorTarget = gapAnchor?.let { a -> buttTargets.firstOrNull { it.second.id == a.cutId } }
    val anchor = gapAnchor?.takeIf { anchorTarget != null }
    // Shown only when there is something behind it: a liner to span against, or another cut to
    // butt against. It opens on its own for a cut that already IS one of its presets (Whole liner,
    // or running past the liner), so a card shows how its span was made; that is a starting
    // state, not a lock. An open gap field holds it open — a tapped "Cut N" chip with its field up
    // is never hidden by the collapse; collapsing then closes that field the way a second tap on
    // the chip does, keeping the cut where the gap put it.
    val hasMorePlacement = spanLiner != null || buttTargets.isNotEmpty()
    var morePlacementOpen by remember(draft.id) {
        mutableStateOf(isWholeLiner || extAftMm > UNDERCUT_TOUCH_EPS_MM || extFwdMm > UNDERCUT_TOUCH_EPS_MM)
    }
    val morePlacementShown = morePlacementOpen || anchor != null
    val cardFocusManager = LocalFocusManager.current
    fun toggleMorePlacement() {
        if (morePlacementShown) {
            // A value still in a field that is about to leave composition lands in the draft
            // first — a blur commits only into this card's draft, as tapping the canvas does.
            cardFocusManager.clearFocus()
            morePlacementOpen = false
            gapAnchor = null
        } else {
            morePlacementOpen = true
        }
    }

    OutlinedCard(
        modifier = modifier.fillMaxSize().testTag("undercut_card_${draft.id}"),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $title")
                    }
                }
            }

            // A STORED cut the shaft no longer holds (the OAL moved under it). A dirty draft with
            // the same problem states the precise reason in the confirm-issue line instead — "re-
            // measure" is advice about a recorded value, not about one just typed.
            if (staleOverrun && !dirty) {
                UndercutWarning("Extends past shaft end — re-measure")
            }
            // The warning carries the figure it was compared against — the same [surfaceDiaMm] the
            // predicate reads — so the machinist can see which number is off (usability review).
            if (diaAtOrAboveSurface) {
                UndercutWarning(undercutDiaNotBelowSurfaceWarning(draftIsNested, disp(surfaceDiaMm, unit), abbr(unit)))
            }

            // "Measure From" — which datum the Distance value is authored against: both S.E.T.s
            // always, the reference liner's edges while such a liner exists, and the shoulders of
            // the cut this one sits inside while it sits inside one. Tapping a chip re-projects the DISPLAYED distance
            // immediately (canonical `startFromAftMm` never moves) but persists only on Confirm,
            // so Cancel reverts the chip too.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Measure From:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearChip("AFT S.E.T.", shownReference == UndercutReference.AFT_SET) {
                        onDraftChange(
                            draft.copy(
                                reference = UndercutReference.AFT_SET,
                                referenceLinerId = "",
                                referenceCutId = "",
                            ),
                        )
                    }
                    WearChip("FWD S.E.T.", shownReference == UndercutReference.FWD_SET) {
                        onDraftChange(
                            draft.copy(
                                reference = UndercutReference.FWD_SET,
                                referenceLinerId = "",
                                referenceCutId = "",
                            ),
                        )
                    }
                }
                if (refLiner != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WearChip(
                            "Liner AFT",
                            shownReference == UndercutReference.LINER_AFT,
                            enabled = !linerAftBuried,
                            modifier = Modifier.testTag("undercut_ref_liner_aft"),
                        ) {
                            onDraftChange(
                                draft.copy(
                                    reference = UndercutReference.LINER_AFT,
                                    referenceLinerId = refLiner.id,
                                    referenceCutId = "",
                                ),
                            )
                        }
                        WearChip(
                            "Liner FWD",
                            shownReference == UndercutReference.LINER_FWD,
                            enabled = !linerFwdBuried,
                            modifier = Modifier.testTag("undercut_ref_liner_fwd"),
                        ) {
                            onDraftChange(
                                draft.copy(
                                    reference = UndercutReference.LINER_FWD,
                                    referenceLinerId = refLiner.id,
                                    referenceCutId = "",
                                ),
                            )
                        }
                    }
                    if (linerAftBuried) {
                        UndercutCaption(
                            undercutBuriedEdgeCaption("AFT"),
                            Modifier.testTag("undercut_ref_liner_aft_buried"),
                        )
                    }
                    if (linerFwdBuried) {
                        UndercutCaption(
                            undercutBuriedEdgeCaption("FWD"),
                            Modifier.testTag("undercut_ref_liner_fwd_buried"),
                        )
                    }
                }
                // The shoulders of the cut this one sits fully inside — the datum the printed
                // nested rail row dimensions it from.
                if (parentCandidate != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WearChip(
                            "Parent AFT",
                            shownReference == UndercutReference.PARENT_AFT,
                            modifier = Modifier.testTag("undercut_ref_parent_aft"),
                        ) {
                            onDraftChange(draft.measuredFromParent(UndercutReference.PARENT_AFT, parentCandidate))
                        }
                        WearChip(
                            "Parent FWD",
                            shownReference == UndercutReference.PARENT_FWD,
                            modifier = Modifier.testTag("undercut_ref_parent_fwd"),
                        ) {
                            onDraftChange(draft.measuredFromParent(UndercutReference.PARENT_FWD, parentCandidate))
                        }
                    }
                }
            }

            val displayedStartMm = canonicalToUndercutStartMm(
                reference = shownReference,
                canonicalStartMm = draft.startFromAftMm,
                lengthMm = draft.lengthMm,
                aftSetXMm = aftSetXMm,
                fwdSetXMm = fwdSetXMm,
                linerStartMm = refLinerStartMm,
                linerEndMm = refLinerEndMm,
                parentStartMm = refParentStartMm,
                parentEndMm = refParentEndMm,
            )
            // Negative is a real value here: a cut overhanging the datum's edge (the "Extend past
            // liner" case under a Liner reference) reads below zero and must be retypable as such.
            // No validator: a Distance that puts the cut off the shaft still lands in the draft
            // verbatim, and Confirm blocks it with the reason. The hint says fractions are
            // welcome — the shop's tape reads in them (usability review); one hint per card, on
            // its first field.
            UndercutNum(
                label = "Distance from ${undercutReferenceLabel(shownReference)} (${abbr(unit)})",
                initialDisplay = disp(displayedStartMm, unit),
                allowNegative = true,
                hint = UNDERCUT_FRACTION_HINT,
            ) { s ->
                val enteredMm = toMmOrNull(s, unit) ?: return@UndercutNum
                val canonicalMm = undercutStartToCanonicalMm(
                    shownReference, enteredMm, draft.lengthMm, aftSetXMm, fwdSetXMm,
                    refLinerStartMm, refLinerEndMm, refParentStartMm, refParentEndMm,
                )
                commitSpanEdit(draft.copy(startFromAftMm = canonicalMm))
            }

            // A length edit keeps the AUTHORED Distance fixed: canonical start is re-derived
            // from the active reference at the new length (identity under an AFT reference;
            // under a FWD reference the cut's FWD end stays pinned and the cut grows/shrinks
            // AFT-ward). Committing the new length against the old canonical would rewrite
            // the displayed Distance by the length delta — golden-rule violation (on-device
            // report). No validator, as for Distance: a length that leaves the shaft (or is zero)
            // lands in the draft verbatim and Confirm blocks it with the reason.
            fun canonicalAtLength(newLenMm: Float): Float = undercutCanonicalForNewLength(
                shownReference, draft.startFromAftMm, draft.lengthMm, newLenMm,
                aftSetXMm, fwdSetXMm, refLinerStartMm, refLinerEndMm, refParentStartMm, refParentEndMm,
            )
            UndercutNum(
                label = "Length (${abbr(unit)})",
                initialDisplay = disp(draft.lengthMm, unit),
            ) { s ->
                toMmOrNull(s, unit)?.let { newLenMm ->
                    commitSpanEdit(
                        draft.copy(
                            startFromAftMm = canonicalAtLength(newLenMm),
                            lengthMm = newLenMm,
                        ),
                    )
                }
            }
            // A declined "Past the liner edge" question, beside the numbers it is about — shown
            // while that end still runs off the liner.
            if (overhangFlagged.aft && overhangsAft) {
                UndercutWarning(
                    undercutOverhangWarning(overhangAmount(overhangAftMm), "AFT"),
                    Modifier.testTag("undercut_overhang_warning"),
                )
            }
            if (overhangFlagged.fwd && overhangsFwd) {
                UndercutWarning(
                    undercutOverhangWarning(overhangAmount(overhangFwdMm), "FWD"),
                    Modifier.testTag("undercut_overhang_warning"),
                )
            }

            // Measured Ø — a measurement, so any parseable value ≥ 0 lands **verbatim**
            // (golden rule). A Ø at or above the local surface is a warning above, never a
            // block and never an adjustment. An unentered Ø (0) shows an empty field, with an
            // example of what to type in it — the one field on the card that starts empty.
            UndercutNum(
                label = "Measured Ø (${abbr(unit)})",
                initialDisplay = if (draft.diaMm > 0f) disp(draft.diaMm, unit) else "",
                placeholder = UNDERCUT_DIA_PLACEHOLDER,
            ) { s ->
                val mm = toMmOrNull(s, unit) ?: return@UndercutNum
                onDraftChange(draft.copy(diaMm = mm))
            }

            // Notes — free text straight into the draft; Confirm/Cancel own persistence, so the
            // numeric fields' capture-on-focus discipline buys nothing here.
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraftChange(draft.copy(note = it)) },
                label = { Text("Notes") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // The blocking reason, inline where the numbers that caused it are. The pill above the
            // carousel states it too — this one survives being scrolled to, that one survives the
            // card being scrolled.
            if (dirty && confirmIssue != null) {
                UndercutWarning(confirmIssue)
            }

            // "Next cut": confirm this card and open a new cut butted on its FWD end — the
            // run-of-cuts workflow. At the foot of the card, after the fields it records, so it
            // reads as the step after this cut's numbers rather than a header control to tap
            // before typing them (usability review).
            OutlinedButton(
                onClick = onNextCut,
                enabled = nextCut.enabled,
                modifier = Modifier.fillMaxWidth().testTag("undercut_next_cut"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Next cut")
            }
            nextCut.caption?.let { UndercutCaption(it, Modifier.testTag("undercut_next_cut_caption")) }

            if (hasMorePlacement) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { toggleMorePlacement() })
                        .testTag("undercut_more_placement")
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "More ways to place this cut",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (morePlacementShown) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (morePlacementShown) "Collapse" else "Expand",
                    )
                }
            }

            // Span against a liner — "Whole liner" plus "Extend past liner" AFT/FWD (on-device
            // request: a cut that covers the liner and runs on into the adjacent body).
            // Nothing new is stored: the extensions are a VIEW of the stored shaft-space span
            // against the liner's edges, the "Distance from Liner AFT" relation with the sign
            // flipped, and nothing derived ever writes back into them. A typed extension maps to
            // canonical through the liner edge exactly as a typed Distance does, the opposite end
            // of the cut stays where it was, and the Distance/Length fields re-seed from the new
            // draft — golden rule. "Whole liner" is a one-tap preset applied only on the tap; it
            // also sets the Liner AFT reference against that liner, so Distance then reads 0.
            if (morePlacementShown && spanLiner != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Span:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.testTag("undercut_whole_liner")) {
                        WearChip("Whole liner", isWholeLiner) {
                            onDraftChange(draft.withWholeLiner(spanLiner))
                        }
                    }
                }

                Text(
                    "Extend past liner (${abbr(unit)})", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // An extension can only run into stock that is there (on-device request): a typed
                // value past the shaft's end applies the maximum and says so, rather than blocking —
                // the field is a derived view of the span, so limiting it to the shaft is not a
                // rewrite of a measurement. Like every field on this card the extensions carry no
                // validator: a result with no length lands in the draft as computed and Confirm
                // blocks it with the reason ([undercutConfirmIssue]). A side with no stock at all
                // (the liner sits at that shaft end) is disabled with a caption saying why.
                val maxAftMm = maxUndercutAftExtensionMm(spanLiner.startMm)
                val maxFwdMm = maxUndercutFwdExtensionMm(spanLiner.endMm, oalMm)
                val aftHasStock = maxAftMm > UNDERCUT_SPAN_EPS_MM
                val fwdHasStock = maxFwdMm > UNDERCUT_SPAN_EPS_MM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    key(aftReseed) {
                        WearNum(
                            label = "AFT",
                            initialDisplay = disp(extAftMm, unit),
                            enabled = aftHasStock,
                            modifier = Modifier.weight(1f).testTag("undercut_extend_aft"),
                        ) { raw ->
                            val extMm = toMmOrNull(raw, unit) ?: return@WearNum
                            val edit = draft.withAftLinerExtension(spanLiner, extMm, oalMm)
                            onDraftChange(edit.draft)
                            if (edit.limited) {
                                aftLimitedMm = edit.appliedMm
                                aftReseed++
                            } else {
                                aftLimitedMm = null
                            }
                        }
                    }
                    key(fwdReseed) {
                        WearNum(
                            label = "FWD",
                            initialDisplay = disp(extFwdMm, unit),
                            enabled = fwdHasStock,
                            modifier = Modifier.weight(1f).testTag("undercut_extend_fwd"),
                        ) { raw ->
                            val extMm = toMmOrNull(raw, unit) ?: return@WearNum
                            val edit = draft.withFwdLinerExtension(spanLiner, extMm, oalMm)
                            onDraftChange(edit.draft)
                            if (edit.limited) {
                                fwdLimitedMm = edit.appliedMm
                                fwdReseed++
                            } else {
                                fwdLimitedMm = null
                            }
                        }
                    }
                }
                if (!aftHasStock) {
                    UndercutCaption("Liner is at the shaft's AFT end — nothing to extend into")
                }
                if (!fwdHasStock) {
                    UndercutCaption("Liner is at the shaft's FWD end — nothing to extend into")
                }
                aftLimitedMm?.takeIf { abs(it - extAftMm) <= EXTENSION_NOTE_MATCH_MM }?.let {
                    UndercutWarning(
                        "Extension past the shaft's AFT end isn't allowed — " +
                            "set to the maximum, ${disp(it, unit)} ${abbr(unit)}",
                        Modifier.testTag("undercut_extend_aft_note"),
                    )
                }
                fwdLimitedMm?.takeIf { abs(it - extFwdMm) <= EXTENSION_NOTE_MATCH_MM }?.let {
                    UndercutWarning(
                        "Extension past the shaft's FWD end isn't allowed — " +
                            "set to the maximum, ${disp(it, unit)} ${abbr(unit)}",
                        Modifier.testTag("undercut_extend_fwd_note"),
                    )
                }
            }

            // Edge-to-edge presets against another cut on the page (on-device request: measuring
            // each new cut's Distance off the previous cut's end). Two explicit rows, every other
            // cut in both — one "After/Before cut N" chip that picked its side from the cuts'
            // midpoints read "After cut 5" as AFT of cut 5 (on-device report). A tap sets the
            // canonical start edge to edge and nothing else, so Distance re-seeds under the same
            // reference. A result that would leave the shaft changes nothing and says so ("Not
            // applied — <reason>") until the next tap or draft change — nothing was typed, so
            // refusing the preset loses no value.
            // An applied chip stays SELECTED and opens a gap field under its row (on-device request:
            // the next cut measured 2 1/4" past the previous cut's end rather than re-measured from
            // the liner edge). The gap is a VIEW of the canonical span against that cut's edge —
            // nothing new is stored, the Extend-past-liner posture: a typed gap moves the cut with
            // its length kept, and Distance re-seeds under the card's own datum, so the sheet still
            // reads from the liner or S.E.T. The typed gap commits verbatim; bounds and overlap are
            // confirm-time, like every other field on this card. A second tap on the selected chip
            // closes the field and changes nothing.
            if (morePlacementShown && buttTargets.isNotEmpty()) {
                fun applyPreset(moved: UndercutDraft, tapped: UndercutGapAnchor) {
                    if (anchor == tapped) {
                        gapAnchor = null
                        return
                    }
                    val issue = undercutSpanIssue(moved.startFromAftMm, moved.lengthMm, oalMm)
                    if (issue != null) {
                        buttIssue = undercutPresetRefusal(issue)
                    } else {
                        gapAnchor = tapped
                        onDraftChange(moved)
                    }
                }
                val gapField: @Composable (Boolean) -> Unit = gapField@{ after ->
                    val (position, other) = anchorTarget ?: return@gapField
                    if (anchor?.after != after) return@gapField
                    val gapMm = if (after) {
                        undercutGapAfterMm(draft.startFromAftMm, other.endMm)
                    } else {
                        undercutGapBeforeMm(draft.startFromAftMm, draft.lengthMm, other.startMm)
                    }
                    WearNum(
                        label = undercutGapFieldLabel(after, position, abbr(unit)),
                        initialDisplay = disp(gapMm, unit),
                        allowNegative = true,
                        modifier = Modifier.fillMaxWidth()
                            .testTag(if (after) "undercut_gap_after" else "undercut_gap_before"),
                    ) { raw ->
                        val typedMm = toMmOrNull(raw, unit) ?: return@WearNum
                        onDraftChange(
                            if (after) draft.startingAtEndOf(other, typedMm)
                            else draft.endingAtStartOf(other, typedMm),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Start at end of:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttTargets.forEach { (position, other) ->
                            val tapped = UndercutGapAnchor(after = true, cutId = other.id)
                            Box(Modifier.testTag("undercut_butt_after_$position")) {
                                WearChip(undercutButtChipLabel(position), selected = anchor == tapped) {
                                    applyPreset(draft.startingAtEndOf(other), tapped)
                                }
                            }
                        }
                    }
                    gapField(true)
                    Text(
                        "End at start of:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttTargets.forEach { (position, other) ->
                            val tapped = UndercutGapAnchor(after = false, cutId = other.id)
                            Box(Modifier.testTag("undercut_butt_before_$position")) {
                                WearChip(undercutButtChipLabel(position), selected = anchor == tapped) {
                                    applyPreset(draft.endingAtStartOf(other), tapped)
                                }
                            }
                        }
                    }
                    gapField(false)
                    buttIssue?.let { UndercutWarning(it, Modifier.testTag("undercut_butt_issue")) }
                }
            }
        }
    }

    // Answerable only by its two buttons: a tap outside or Back would have to pick one, and
    // either silent pick is the decision the machinist was asked to make.
    overhangQuestion?.let { ask ->
        UndercutOverhangDialog(
            aftAmount = if (ask.aft) overhangAmount(overhangAftMm) else null,
            fwdAmount = if (ask.fwd) overhangAmount(overhangFwdMm) else null,
            onAccept = {
                overhangAcknowledged = overhangAcknowledged.with(ask)
                overhangFlagged = overhangFlagged.without(ask)
                overhangQuestion = null
            },
            onDecline = {
                overhangFlagged = overhangFlagged.with(ask)
                overhangAcknowledged = overhangAcknowledged.without(ask)
                overhangQuestion = null
            },
        )
    }
}

/**
 * The "Past the liner edge" question — ONE dialog for the card's own field commits and the
 * overlay's flushed ones, so both read and test the same. Answerable only by its two buttons: a tap
 * outside or Back would have to pick one, and either silent pick is the decision the machinist was
 * asked to make. [aftAmount]/[fwdAmount] are the overhang past each end, null for an end that is
 * not newly off the liner.
 */
@Composable
private fun UndercutOverhangDialog(
    aftAmount: String?,
    fwdAmount: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        modifier = Modifier.testTag("undercut_overhang_dialog"),
        title = { Text("Past the liner edge") },
        text = { Text(undercutOverhangQuestion(aft = aftAmount, fwd = fwdAmount)) },
        confirmButton = {
            TextButton(onClick = onAccept, modifier = Modifier.testTag("undercut_overhang_accept")) {
                Text("Yes, it does")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, modifier = Modifier.testTag("undercut_overhang_decline")) {
                Text("No, let me check")
            }
        },
    )
}

/** Raised while the overlay flushes focus; a card's own overhang question stays quiet under it. */
internal class UndercutFlushGate {
    var active: Boolean = false
}

/** The overlay's answer to "Past the liner edge" for a stored card, for that card to take. */
internal data class UndercutOverhangAnswer(val ends: UndercutLinerEnds, val declined: Boolean)

/** What the overlay's "Past the liner edge" question is holding up. */
internal sealed interface UndercutAfterOverhang {
    /** The pill's Confirm of the selected card. */
    object Confirm : UndercutAfterOverhang

    /** The card's "Next cut": confirm the selected card, then seed the cut after it. */
    object NextCut : UndercutAfterOverhang

    /** A leave sweep (or the close), resumed with the same arguments. */
    data class Settle(val keepId: String?, val closing: Boolean) : UndercutAfterOverhang
}

/** The overlay-level question: which card, which ends, how far past each, and what waits on it. */
internal data class UndercutOverlayOverhangAsk(
    val draftId: String,
    val ends: UndercutLinerEnds,
    val aftAmount: String?,
    val fwdAmount: String?,
    val then: UndercutAfterOverhang,
)

/** How a leave sweep ended: everything settled, a blocked draft to ask about, or an overhang asked. */
private sealed interface UndercutSettleResult {
    object Done : UndercutSettleResult
    data class Prompt(val prompt: UndercutLeavePrompt) : UndercutSettleResult
    object Asking : UndercutSettleResult
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating status pill — the overlay's whole save affordance
// ─────────────────────────────────────────────────────────────────────────────

/** What the selected card's draft is doing, as the pill states it. */
internal sealed interface UndercutPillState {
    /** Nothing to save — the card matches the record. Not a button. */
    object Saved : UndercutPillState

    /**
     * A dirty draft that clears [undercutConfirmIssue]: one tap commits it. [pending] is the add
     * flow's new cut, which the pill offers to RECORD rather than to confirm a change to.
     */
    data class Unsaved(val pending: Boolean) : UndercutPillState

    /** A dirty draft that cannot be committed; [reason] is the blocking check's own text. */
    data class Blocked(val reason: String) : UndercutPillState
}

/** A blocked draft the machinist tried to walk away from — [closing] when the exit was the overlay. */
private data class UndercutLeavePrompt(
    val draftId: String,
    val reason: String,
    val closing: Boolean,
)

/**
 * The persistent save pill, floated at the canvas ↔ carousel boundary. It replaces the per-card
 * Confirm/Cancel row, which lived inside the card's own vertical scroll and was easy to miss
 * (on-device report). Tapping it — "Record cut" on a new cut, "Confirm change" on a recorded
 * one — runs the identical `confirmDraft` path the auto-commit-on-leave runs, and stays on the
 * card.
 *
 * testTags: the container is `undercut_status_pill`, the confirm action keeps `undercut_confirm`
 * and the discard action `undercut_cancel` — one instance of each, since only the SELECTED card's
 * draft is ever represented here (the old per-card tags had to be suppressed on peeking pages).
 */
@Composable
private fun UndercutStatusPill(
    state: UndercutPillState,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when (state) {
        UndercutPillState.Saved -> MaterialTheme.colorScheme.surfaceVariant
        is UndercutPillState.Unsaved -> MaterialTheme.colorScheme.primaryContainer
        is UndercutPillState.Blocked -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (state) {
        UndercutPillState.Saved -> MaterialTheme.colorScheme.onSurfaceVariant
        is UndercutPillState.Unsaved -> MaterialTheme.colorScheme.onPrimaryContainer
        is UndercutPillState.Blocked -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.testTag("undercut_status_pill"),
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = onContainer,
        shadowElevation = if (state == UndercutPillState.Saved) 1.dp else 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                UndercutPillState.Saved ->
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = UNDERCUT_SAVED_TINT,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        // "Recorded": the cut is in this job's record. Saving the job file is the
                        // tab's step, and a pill that claimed it would read as that step being
                        // done here (usability review).
                        Text("Recorded", style = MaterialTheme.typography.labelLarge)
                    }

                is UndercutPillState.Unsaved ->
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onConfirm)
                            .testTag("undercut_confirm")
                            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            if (state.pending) "Record cut" else "Confirm change",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                is UndercutPillState.Blocked ->
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            state.reason,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                        )
                    }
            }
            // Discard rides alongside both editable states, so dropping an edit never needs the
            // card to be scrolled to — and is never the same tap target as confirming it.
            if (state != UndercutPillState.Saved) {
                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier.testTag("undercut_cancel"),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard change")
                }
            }
        }
    }
}

/** Green check on the "Recorded" pill — a settled state reads at a glance, outside the theme ramp. */
private val UNDERCUT_SAVED_TINT = Color(0xFF2E7D32)

/**
 * How close the field's re-derived extension must sit to a limited commit's applied maximum for
 * the card to keep its "set to the maximum" note. Looser than the span eps: the extension is
 * re-derived through a Float shaft-space end, whose rounding at a long shaft's FWD end exceeds
 * 1e-3 mm.
 */
private const val EXTENSION_NOTE_MATCH_MM = UNDERCUT_TOUCH_EPS_MM

/** The Distance field's hint: fractions are typed as the tape reads them. */
internal const val UNDERCUT_FRACTION_HINT = "Decimals or fractions — 5.748, 3 1/2, 7/8"

/** The Measured Ø field's placeholder — the one card field that starts empty. */
internal const val UNDERCUT_DIA_PLACEHOLDER = "e.g. 5.625 or 5 5/8"

/**
 * A card's numeric field: [WearNum]'s arguments (fractions allowed, parsed by
 * [parseFractionOrDecimal], no validator — every card field commits what parses, verbatim) plus
 * the field's [placeholder] and [hint].
 */
@Composable
private fun UndercutNum(
    label: String,
    initialDisplay: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    allowNegative: Boolean = false,
    placeholder: String? = null,
    hint: String? = null,
    onCommit: (String) -> Unit,
) {
    NumericInputField(
        label = label,
        initialText = initialDisplay,
        modifier = modifier,
        allowNegative = allowNegative,
        allowFraction = true,
        placeholder = placeholder,
        hint = hint,
        parseValid = { parseFractionOrDecimal(it) != null },
        onCommit = onCommit,
    )
}

/** A muted explanatory line under a control — why it is disabled, not a problem to fix. */
@Composable
private fun UndercutCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UndercutWarning(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Window geometry (shared by the Canvas renderer and the tap handler)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clear space (dp) reserved at each side of the window canvas for the S-curve break edges,
 * whose bulge reaches `r × 0.6` past the window edge. The same budget reasoning as the wear
 * overlay's `SEG_EDGE_PAD_DP`.
 */
internal const val UNDERCUT_EDGE_PAD_DP = 32f

internal data class UndercutWindowLayout(
    val pxPerMm: Float,
    val startPx: Float,
    val endPx: Float,
    val cy: Float,
)

/**
 * On-screen layout of one zoomed undercut window. Pure function of the canvas size and the
 * window's length/OD, so the Canvas renderer and the tap handler compute IDENTICAL geometry (a
 * tapped undercut is the one that was drawn). Width-driven scale capped by the height budget,
 * the [computeLinerDetailPxPerMm] rule, so a very short window can't render as a screen-filling
 * slab.
 */
internal fun computeUndercutWindowLayout(
    widthPx: Float,
    profileTopPx: Float,
    profileRowHeightPx: Float,
    windowLengthMm: Float,
    maxOdMm: Float,
    edgePadPx: Float = 0f,
): UndercutWindowLayout {
    val usableWidthPx = (widthPx - 2f * edgePadPx).coerceAtLeast(1f)
    val pxPerMm = computeLinerDetailPxPerMm(
        usableWidthPx = usableWidthPx,
        linerLengthMm = windowLengthMm,
        maxOdMm = maxOdMm,
        usableHeightPx = profileRowHeightPx,
        heightFillFraction = 0.72f,
    )
    val drawnWidthPx = windowLengthMm * pxPerMm
    val startPx = ((widthPx - drawnWidthPx) / 2f).coerceAtLeast(0f)
    return UndercutWindowLayout(
        pxPerMm = pxPerMm,
        startPx = startPx,
        endPx = startPx + drawnWidthPx,
        cy = profileTopPx + profileRowHeightPx / 2f,
    )
}

/** A component's outer Ø at shaft-space [xMm]; tapers interpolate, coupler slots contribute none. */
internal fun componentDiaAt(rc: ResolvedComponent, xMm: Float): Float = when (rc) {
    is ResolvedBody -> rc.diaMm
    is ResolvedLiner -> rc.odMm
    is ResolvedThread -> rc.majorDiaMm
    is ResolvedTaper -> {
        val len = rc.endMmPhysical - rc.startMmPhysical
        val t = if (len > 1e-3f) ((xMm - rc.startMmPhysical) / len).coerceIn(0f, 1f) else 0f
        rc.startDiaMm + t * (rc.endDiaMm - rc.startDiaMm)
    }
    is ResolvedCouplerBoltSlot -> 0f
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Size of one printed point on the detail canvas, dp. The canvas draws the printed strip's rails
 * through the same plan and draw routine as the page, with every fixed point size multiplied by
 * `density × this`: at 1.25 the rail's 8 pt values set at 10 dp — the size the canvas's other
 * labels read at — and its 4 pt arrowheads at 5 dp.
 */
internal const val UNDERCUT_DETAIL_DP_PER_PT = 1.25f

/** The rail values' size in printed points — the printed strip's dimension text (sheet text − 2). */
internal const val UNDERCUT_DETAIL_RAIL_TEXT_PT = 8f

/** Rail stroke, dp — the printed dimension-to-outline weight ratio (1.2 : 2) on the 1.5 dp outline. */
internal const val UNDERCUT_DETAIL_RAIL_STROKE_DP = 0.9f

/** Clear air above the top rail line inside the canvas, in printed points. */
internal const val UNDERCUT_DETAIL_RAIL_TOP_PAD_PT = 8f

/**
 * Air between the last reserved rail row and the cylinder top, in printed points — the printed
 * strip's surplus rail headroom (`UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT`, up to 15 pt), fixed: the
 * canvas has no band surplus to spend, and without it a fallback value on the lowest row stands
 * on the cylinder outline.
 */
internal const val UNDERCUT_DETAIL_RAIL_HEADROOM_PT = 6f

/**
 * Every input of the detail canvas's rail plan in one record — the `remember` key, so no input
 * can be left out of it. [spans] are the PREVIEWED spans (stored cuts with the live draft applied),
 * render-clamped.
 */
internal data class UndercutDetailRailInputs(
    val spans: List<UndercutSpanMm>,
    val chainStartMm: Float,
    val chainEndMm: Float,
    val splitAtMm: List<Float>,
    val unit: UnitSystem,
    /** No cuts on the strip: the canvas draws no rail. */
    val startedStrip: Boolean,
    /** The drawing Box's laid-out width; 0 before the first layout (no plan yet). */
    val widthPx: Float,
    val profileRowHeightPx: Float,
    val edgePadPx: Float,
    val drawStartMm: Float,
    val windowLengthMm: Float,
    val maxOdMm: Float,
    /** Canvas px per printed point ([UNDERCUT_DETAIL_DP_PER_PT] × density). */
    val scale: Float,
    /** The label measure depends on these, so a change re-plans. */
    val textSizePx: Float,
    val typeface: Any?,
    val fractionStyle: Any?,
    /** The "Dimension arrows" head, printed points — the seat-in-break decisions depend on it. */
    val arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
)

/**
 * The detail canvas's rails: the printed strip's ONE planner ([planUndercutStripRails]) over the
 * canvas's own x map ([computeUndercutWindowLayout] at the Box width), single-unit, at the canvas
 * [UndercutDetailRailInputs.scale]. [labelWidthPx] measures a value as the canvas draws it. Null
 * until the Box has a width.
 */
internal fun planUndercutDetailRails(
    inputs: UndercutDetailRailInputs,
    labelWidthPx: (DualLabel) -> Float,
): UndercutStripRails? {
    if (inputs.widthPx <= 0f) return null
    val lay = computeUndercutWindowLayout(
        widthPx = inputs.widthPx,
        profileTopPx = 0f,
        profileRowHeightPx = inputs.profileRowHeightPx,
        windowLengthMm = inputs.windowLengthMm,
        maxOdMm = inputs.maxOdMm,
        edgePadPx = inputs.edgePadPx,
    )
    return planUndercutStripRails(
        inputs.chainStartMm, inputs.chainEndMm, inputs.spans, inputs.unit,
        dual = false,
        splitAtMm = inputs.splitAtMm,
        startedStrip = inputs.startedStrip,
        xAt = { mm -> lay.startPx + (mm - inputs.drawStartMm) * lay.pxPerMm },
        labelWidth = labelWidthPx,
        scale = inputs.scale,
        arrowSizePt = inputs.arrowSizePt,
    )
}

/** The detail canvas's rail band, px from the canvas top — see [undercutDetailRailBand]. */
internal data class UndercutDetailRailBand(
    val totalRailY: Float,
    val chainRailY: Float,
    /** Where the drawn cylinder's top sits: the band's bottom. */
    val cylTopPx: Float,
)

/**
 * The detail canvas's rail band, built the printed strip's way (`computeUndercutStripInnerLayout`)
 * at canvas [scale]: a top pad, then the total rail line [UNDERCUT_TOTAL_RAIL_ABOVE_PT] down with
 * the rest of [UNDERCUT_TOTAL_RAIL_BAND_PT] below it when a total exists — else the chain's
 * above-the-line fallback rows — then the chain line, then the plan's `belowRows` rows (clear air,
 * the chain's below-line fallbacks and every nested row) and [UNDERCUT_DETAIL_RAIL_HEADROOM_PT],
 * ending on the cylinder top. A strip with no rail (no plan yet, or no cuts) keeps only the top
 * pad.
 */
internal fun undercutDetailRailBand(
    rails: UndercutStripRails?,
    scale: Float,
    rowStepPx: Float,
): UndercutDetailRailBand {
    val topPad = UNDERCUT_DETAIL_RAIL_TOP_PAD_PT * scale
    if (rails == null || rails.chainSpans.isEmpty()) return UndercutDetailRailBand(topPad, topPad, topPad)
    val totalY: Float
    val chainY: Float
    if (rails.totalSpan != null) {
        totalY = topPad + UNDERCUT_TOTAL_RAIL_ABOVE_PT * scale
        chainY = totalY + (UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT) * scale
    } else {
        chainY = topPad + rails.rows.aboveRows * rowStepPx
        totalY = chainY
    }
    return UndercutDetailRailBand(
        totalY, chainY,
        chainY + rails.rows.belowRows * rowStepPx + UNDERCUT_DETAIL_RAIL_HEADROOM_PT * scale,
    )
}

/**
 * Strip title: a liner strip is named after its liner (`buildLinerTitleById`, the same positional
 * names the wear document uses) followed by its OD in the liner's own display unit
 * (`buildUndercutStripTitle`, the printed strip title's construction); a free strip states its
 * zoom range in shaft space. A liner the spec no longer holds falls back to the range text.
 */
private fun undercutStripTitle(
    strip: UndercutStrip,
    spec: ShaftSpec,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): String {
    val rangeText =
        "Undercuts ${disp(strip.drawStartMm, unit)}–${disp(strip.drawEndMm, unit)} ${abbr(unit)} from AFT"
    return when (strip) {
        is UndercutStrip.FreeStrip -> rangeText
        is UndercutStrip.LinerStrip -> buildLinerTitleById(spec)[strip.linerId]?.let { name ->
            // The liner's OD after its name, in the liner's own unit — the printed strip title's rule.
            val odMm = spec.liners.firstOrNull { it.id == strip.linerId }?.odMm?.takeIf { it > 0f }
            buildUndercutStripTitle(
                name, "",
                odMm?.let { "Ø" + formatDiaWithUnit(it.toDouble(), displayUnits.unitFor(strip.linerId)) },
            )
        } ?: rangeText
    }
}
