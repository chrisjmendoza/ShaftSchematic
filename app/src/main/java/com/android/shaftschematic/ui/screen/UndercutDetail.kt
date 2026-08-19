// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutDetail.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.NotchProfile
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.assignUndercutLiner
import com.android.shaftschematic.geom.canonicalToUndercutStartMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.deepestUndercutDepthMm
import com.android.shaftschematic.geom.effectiveNotchDiaMm
import com.android.shaftschematic.geom.isUndercutStaleOverrun
import com.android.shaftschematic.geom.NOTCH_FACE_MIN_STEP_PX
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.nearestSetReference
import com.android.shaftschematic.geom.normalizedNotchFloorDiaMm
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.pickUndercutAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.undercutCanonicalForNewLength
import com.android.shaftschematic.geom.undercutOverlapIssue
import com.android.shaftschematic.geom.undercutPreviewDrawRange
import com.android.shaftschematic.geom.undercutSpanIssue
import com.android.shaftschematic.geom.undercutStartToCanonicalMm
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.pdf.formatDiaWithUnit
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UndercutStyle
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.buildLinerTitleById
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
 * - a **fixed canvas** — a **dimension rail above** (the cluster total span on the upper line,
 *   and below it the chained run across the strip's chain range: chain AFT datum → each shoulder
 *   → each gap → chain FWD datum; nothing outside the chain range is labelled, so the drawing
 *   never dimensions the arbitrary pad); the **strip profile** with the undercut **notches** cut
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
 * save state: *Saved* (nothing dirty), *Confirm change* (dirty and clear — one tap commits, staying
 * on the card), or the blocking reason (dirty and blocked, not confirmable); the last two also carry
 * a discard (✕).
 *
 * **Leaving a card saves it.** A dirty draft that clears the confirm check commits by itself,
 * through the identical [confirmDraft] path (values verbatim — golden rule), when the machinist
 * leaves its card: swiping to another page, tapping another notch, or closing the overlay. A
 * BLOCKED draft is never silently committed and never silently dropped — the leave raises an
 * AlertDialog stating the blocking reason, with **Keep editing** (return to the card, cancelling
 * the close) and **Discard** (drop the draft and go). [undercutLeaveAction] is that decision.
 *
 * Selection and paging are one thing: swiping a card highlights its notch, and tapping a notch
 * pages the carousel to its card.
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
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    undercutRecord: UndercutRecord,
    style: UndercutStyle = UndercutStyle(),
    onAddUndercut: (
        startFromAftMm: Float,
        lengthMm: Float,
        reference: UndercutReference,
        referenceLinerId: String,
    ) -> String,
    onUpdateUndercut: (id: String, startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) -> Unit,
    onUpdateReference: (id: String, reference: UndercutReference, referenceLinerId: String) -> Unit,
    onRemoveUndercut: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents, spec) { surfaceSegsFrom(resolvedComponents, bodyBlends(spec, resolvedComponents)) }

    // Every liner on the shaft, in strip space — the pool the cards' liner references draw from.
    val linerSpans = remember(resolvedComponents) { linerSpansOf(resolvedComponents) }
    val stripLiner = (strip as? UndercutStrip.LinerStrip)
        ?.let { UndercutLinerSpan(it.linerId, it.linerStartMm, it.linerEndMm) }

    // The strip's member undercuts, aft → fwd. Ids the record no longer holds simply drop out.
    val undercuts = remember(undercutRecord, strip) {
        strip.undercutIds
            .mapNotNull { id -> undercutRecord.undercuts.firstOrNull { it.id == id } }
            .sortedBy { it.startFromAftMm }
    }

    // ── Draft state ──────────────────────────────────────────────────────────
    // Keyed on the strip's IDENTITY, not the strip value: a strip carries its member ids and its
    // draw range, so every record edit yields a new (unequal) strip — keying on it would wipe
    // drafts and selection the instant a card was confirmed.
    val stripKey = remember(strip) { undercutStripKey(strip) }
    // Per-card local edits; a card with no entry here shows stored values. The add flow's
    // not-yet-recorded page lives under [PENDING_UNDERCUT_ID].
    var drafts by remember(stripKey) { mutableStateOf(emptyMap<String, UndercutDraft>()) }
    // The add draft's ordering key, fixed at creation — a pending card must not slide between
    // pages while its Distance is being typed, the same "reorder only on confirm" rule the
    // recorded cards follow.
    var pendingSeedStartMm by remember(stripKey) { mutableStateOf<Float?>(null) }
    var selectedId by remember(stripKey) { mutableStateOf<String?>(null) }

    // ── Leave handling: the card being left is the card being saved ───────────
    // Set while a blocked draft is being asked about; null means no dialog.
    var leavePrompt by remember(stripKey) { mutableStateOf<UndercutLeavePrompt?>(null) }
    // "Keep editing" returns to the blocked card programmatically — that is not the machinist
    // leaving the card they were moved to, so the next selection change skips leave handling
    // (without this, snapping back off a pending add page would auto-commit the add).
    var skipLeaveOnce by remember(stripKey) { mutableStateOf(false) }

    val pageIds = remember(undercuts, pendingSeedStartMm) {
        buildUndercutPageIds(undercuts, pendingSeedStartMm)
    }
    val baselines = remember(undercuts, linerSpans) {
        undercuts.associate { it.id to undercutDraftOf(it, linerSpans) }
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
    // The window follows what is PREVIEWED, not just what is stored: a draft edited past the
    // strip's stored range (a cut overhanging the liner edge mid-edit — on-device report)
    // widens the drawing live, with the standard pad of neighbour stock beyond it — the same
    // range a confirmed overhang gets when the strip rebuilds. Never narrows while editing.
    val (drawStartMm, drawEndMm) = remember(strip, spans, oalMm) {
        undercutPreviewDrawRange(strip, spans, oalMm)
    }
    // Confirm-blocking status of the previewed draft: its notch draws dashed in the selection
    // color while valid, in the error color while this is non-null, and the status pill states
    // the same reason — one check over the whole sheet, so the drawing, the pill, and what
    // leaving the card does can never disagree.
    val activeDraftIssue = remember(activeDraft, oalMm, undercutRecord) {
        activeDraft?.let { d ->
            undercutConfirmIssue(d, oalMm, undercutOtherSpans(undercutRecord.undercuts, d.id, oalMm))
        }
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
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    // Read the live page list inside the never-restarted collector below.
    val pageIdsLive = rememberUpdatedState(pageIds)

    // Swipe (or any scroll) → selection, so the card in front highlights its own notch. Keyed on
    // the pager alone: restarting this on every page-list change would re-adopt whatever id sits
    // at the current index and undo a deliberate selection (a just-confirmed cut, mid-animation).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            // Reads the LIVE page list, so an emission raised by a leave-commit's reorder adopts
            // the id the pager is actually showing — its own `key` keeps that the card the
            // machinist swiped to, never the one just committed behind them.
            pageIdsLive.value.getOrNull(page)?.let { selectedId = it }
        }
    }
    // Selection → page: a canvas tap opens that cut's card, and a confirmed cut is FOLLOWED to
    // its new aft → fwd position.
    LaunchedEffect(selectedId, pageIds) {
        val target = pageIds.indexOf(selectedId)
        if (target >= 0 && target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    // Heal a selection that was deliberately dropped (a deleted cut, a cancelled add) by adopting
    // the card the pager now shows. A selection pointing at a cut the record has not published
    // yet (the frame after Confirm added it) is left alone — it resolves on the next emission.
    LaunchedEffect(pageIds) {
        val sel = selectedId
        if (pageIds.isNotEmpty() && (sel == null || (sel == PENDING_UNDERCUT_ID && sel !in pageIds))) {
            selectedId = pageIds.getOrNull(pagerState.currentPage.coerceIn(0, pageIds.lastIndex))
        }
    }

    fun cancelDraft(id: String) {
        drafts = drafts - id
        if (id == PENDING_UNDERCUT_ID) {
            pendingSeedStartMm = null
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
            )
            // addUndercut lands the span + reference; Ø and note ride the update op.
            if (draft.diaMm > 0f || draft.note.isNotBlank()) {
                onUpdateUndercut(newId, draft.startFromAftMm, draft.lengthMm, draft.diaMm, draft.note)
            }
            drafts = drafts - PENDING_UNDERCUT_ID
            pendingSeedStartMm = null
            if (follow) selectedId = newId
        } else {
            onUpdateUndercut(draft.id, draft.startFromAftMm, draft.lengthMm, draft.diaMm, draft.note)
            val base = baselines[draft.id]
            if (base != null &&
                (base.reference != draft.reference || base.referenceLinerId != draft.referenceLinerId)
            ) {
                onUpdateReference(draft.id, draft.reference, draft.referenceLinerId)
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
            undercutConfirmIssue(it, oalMm, undercutOtherSpans(undercutRecord.undercuts, it.id, oalMm))
        }
        return undercutLeaveAction(draft, baselines[id], issue) to issue
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
    fun settleDraftsExcept(keepId: String?, closing: Boolean): UndercutLeavePrompt? {
        drafts.entries.toList().forEach { (id, draft) ->
            if (id == keepId) return@forEach
            val (action, issue) = leaveActionOf(id)
            when (action) {
                UndercutLeaveAction.NONE -> Unit
                // `follow = false` is what keeps the carousel on the card being moved TO: the
                // confirmed cut may take a new aft → fwd index, and the pager's id keys carry the
                // open page along, but the SELECTION must not be dragged back to a settled card.
                UndercutLeaveAction.COMMIT -> confirmDraft(draft, follow = false)
                UndercutLeaveAction.PROMPT ->
                    return UndercutLeavePrompt(draftId = id, reason = issue.orEmpty(), closing = closing)
            }
        }
        return null
    }

    // Selection changed → every card the machinist is no longer on is settled. A clear draft
    // commits itself; a blocked one raises the dialog (they have already moved on visually, so
    // "Keep editing" is what snaps back).
    LaunchedEffect(selectedId) {
        if (skipLeaveOnce) {
            skipLeaveOnce = false
            return@LaunchedEffect
        }
        settleDraftsExcept(keepId = selectedId, closing = false)?.let { leavePrompt = it }
    }

    // Closing is a leave too — the back arrow and the system back run through here, so a dirty
    // draft can never be lost by walking out of the overlay.
    fun requestClose() {
        val prompt = settleDraftsExcept(keepId = null, closing = true)
        if (prompt != null) leavePrompt = prompt else onClose()
    }

    BackHandler(enabled = leavePrompt == null) { requestClose() }

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
    val railColor = outlineColor.copy(alpha = 0.65f)
    val witnessColor = outlineColor.copy(alpha = 0.35f)
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
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val zoomTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 6f)
        zoomOffset = if (zoomScale <= 1f) Offset.Zero else zoomOffset + panChange
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                    text = undercutStripTitle(strip, spec, unit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            HorizontalDivider()

            // ── Fixed drawing block: the canvas never scrolls away from the cards ──
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The cluster-total rail exists only with ≥ 2 drawable cuts — for a single
                // cut it would restate that cut's own chain figure (a duplicate value, per
                // on-device report). Same rule as the PDF's buildUndercutTotalSpan; the row
                // collapses so the canvas doesn't keep a blank band.
                val totalRailRowDp = if (spans.size >= 2) 30.dp else 0.dp
                val chainRailRowDp = 30.dp
                val profileRowDp = UNDERCUT_PROFILE_ROW_DP
                // Fixed band for the Ø callouts (leader + up to two staggered label rows), so the
                // canvas height doesn't jump between one and two rows.
                val diaBandDp = if (drawUndercuts.isEmpty()) 0.dp else 44.dp
                val canvasHeightDp = totalRailRowDp + chainRailRowDp + profileRowDp + diaBandDp

                val winLenMm = (drawEndMm - drawStartMm).coerceAtLeast(0.001f)
                val maxOdMm = remember(segs, drawStartMm, drawEndMm) {
                    val env = maxOuterDiaOver(segs, drawStartMm, drawEndMm)
                    if (env > 0f) env else 1f
                }

                // Read live inside the tap pointerInput without re-keying it on every pinch.
                val scaleForTap = rememberUpdatedState(zoomScale)
                val offsetForTap = rememberUpdatedState(zoomOffset)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                        .clip(cardShape)
                        .background(Color.White)
                        .transformable(zoomTransformState)
                        .pointerInput(strip, spans, winLenMm, maxOdMm) {
                            detectTapGestures { rawTap ->
                                val sc = scaleForTap.value
                                val tap = Offset(
                                    (rawTap.x - offsetForTap.value.x - size.width / 2f) / sc + size.width / 2f,
                                    (rawTap.y - offsetForTap.value.y - size.height / 2f) / sc + size.height / 2f,
                                )
                                val lay = computeUndercutWindowLayout(
                                    widthPx = size.width.toFloat(),
                                    profileTopPx = (totalRailRowDp + chainRailRowDp).toPx(),
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
                        val profileTopPx = (totalRailRowDp + chainRailRowDp).toPx()
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
                                drawThreadStubHatch(xa, cy - rA, xb, cy + rB, outlineColor)
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
                                    amplitude = rAft * 0.6f, color = outlineColor,
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
                                    amplitude = rFwd * 0.6f, color = outlineColor,
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

                        // ── Dimension rail above: chained run + cluster total ──
                        val chainRailY = totalRailRowDp.toPx() + chainRailRowDp.toPx() * 0.62f
                        val totalRailY = totalRailRowDp.toPx() * 0.62f
                        val witnessBottomY = cy - rPx(maxOdMm) - 4f

                        // Chain datums, not the draw range: a liner strip anchors the rail on the
                        // liner's own edges (extended by any cut overhang), a free strip on its
                        // window edges. The pad outside the chain range is never dimensioned —
                        // the identical rule the PDF strips apply.
                        val stops = buildList {
                            add(strip.chainStartMm)
                            spans.forEach { add(it.startMm); add(it.endMm) }
                            add(strip.chainEndMm)
                        }
                        stops.forEach { mm ->
                            drawLine(
                                witnessColor, Offset(xPx(mm), chainRailY - 3f),
                                Offset(xPx(mm), witnessBottomY), 1f,
                            )
                        }
                        for (i in 0 until stops.size - 1) {
                            val a = stops[i]
                            val b = stops[i + 1]
                            if (b - a <= eps) continue
                            drawUndercutDimSpan(
                                xPx(a), xPx(b), chainRailY,
                                undercutDimLabel(b - a, unit), textPaint, railColor,
                            )
                        }
                        if (spans.size >= 2) {
                            val first = spans.first().startMm
                            val last = spans.last().endMm
                            if (last - first > eps) {
                                drawLine(
                                    witnessColor, Offset(xPx(first), totalRailY - 3f),
                                    Offset(xPx(first), chainRailY), 1f,
                                )
                                drawLine(
                                    witnessColor, Offset(xPx(last), totalRailY - 3f),
                                    Offset(xPx(last), chainRailY), 1f,
                                )
                                drawUndercutDimSpan(
                                    xPx(first), xPx(last), totalRailY,
                                    undercutDimLabel(last - first, unit), textPaint, railColor,
                                )
                            }
                        }

                        // ── Ø callouts below (shared planDiaCallouts engine) ──
                        if (notches.isNotEmpty()) {
                            val leaderColor = outlineColor.copy(alpha = 0.6f)
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
                                    stationX = xPx((n.startMm + n.endMm) / 2f),
                                    label = label,
                                    labelWidth = textPaint.measureText(label.primary),
                                )
                            }
                            val plan = planDiaCallouts(stations, 4f, size.width - 4f, minGap = 6.dp.toPx())
                            val placed = plan.finish(
                                row0Top = profileTopPx + profileRowPx + 2f,
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
                    }
                }

                // ── Shaft-direction reference + the floating save pill ────────────
                // AFT at the left, FWD at the right, the pill riding the empty middle. It sits
                // here — the boundary band between the canvas and the carousel — rather than
                // inside the canvas: bottom-anchored inside it, the pill would sit on top of the
                // Ø callouts, and inside a card it would scroll out of reach (the reason the
                // per-card Confirm/Cancel row was missed on-device).
                Box(modifier = Modifier.fillMaxWidth()) {
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
                            else -> UndercutPillState.Unsaved
                        },
                        onConfirm = { activeDraft?.let { confirmDraft(it) } },
                        onDiscard = { activeDraft?.let { cancelDraft(it.id) } },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp),
                    )
                }

                // Authoring entry point. On a liner strip this is the reason a liner with no cuts
                // yet is tappable on the overview at all; the new page is a DRAFT — it previews
                // here but enters the record only when confirmed (pill tap, or leaving the page
                // with a clear draft). The default span takes the first free gap in the strip so
                // it doesn't land on top of an existing cut.
                val addRange = undercutAddRangeOf(strip, stripLiner)
                Button(
                    onClick = {
                        val occupied = undercutRecord.undercuts.map { u ->
                            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
                            UndercutSpanMm(u.id, c.startMm, c.endMm)
                        }
                        val span = defaultUndercutSpan(addRange.first, addRange.second, occupied, oalMm)
                        drafts = drafts + (
                            PENDING_UNDERCUT_ID to UndercutDraft(
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
                                reference = if (stripLiner != null) UndercutReference.LINER_AFT
                                            else nearestSetReference(
                                                span.startMm, span.startMm + span.lengthMm,
                                                aftSetXMm, fwdSetXMm,
                                            ),
                                referenceLinerId = stripLiner?.id ?: "",
                            )
                            )
                        pendingSeedStartMm = span.startMm
                        selectedId = PENDING_UNDERCUT_ID
                    },
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
                    UndercutDraftCard(
                        title = if (draft.isPending) "New undercut"
                                else "Undercut ${page + 1} of ${pageIds.size}",
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
                        onDraftChange = { updated -> drafts = drafts + (id to updated) },
                        onDelete = if (draft.isPending) null else {
                            {
                                drafts = drafts - id
                                selectedId = null
                                onRemoveUndercut(id)
                            }
                        },
                    )
                }
            }
        }
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
            if (selectedId != prompt.draftId) {
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
                        val next = settleDraftsExcept(
                            keepId = if (prompt.closing) null else selectedId,
                            closing = prompt.closing,
                        )
                        leavePrompt = next
                        if (next == null && prompt.closing) onClose()
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

/** Height of the strip profile band; the rails and Ø callouts add their own rows above/below. */
private val UNDERCUT_PROFILE_ROW_DP = 140.dp

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
) {
    val isPending: Boolean get() = id == PENDING_UNDERCUT_ID
}

/**
 * The draft a stored [u] starts from — its values plus the reference the card actually displays
 * ([effectiveUndercutReference], so a `LINER_*` reference whose liner is gone shows its AFT_SET
 * fallback). Equality against this baseline is the card's dirty test, which is why the fallback
 * belongs here: a card that only displays the fallback is NOT dirty and never rewrites the stored
 * reference behind the machinist's back.
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
)

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
 * OTHER cut on the sheet. Both are confirm-time gates on new values only — nothing already stored
 * is retroactively rejected.
 */
internal fun undercutConfirmIssue(
    draft: UndercutDraft,
    oalMm: Float,
    otherSpans: List<UndercutSpanMm>,
): String? = undercutSpanIssue(draft.startFromAftMm, draft.lengthMm, oalMm)
    ?: undercutOverlapIssue(draft.startFromAftMm, draft.lengthMm, otherSpans)

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

/** What leaving a card does with its draft. */
internal enum class UndercutLeaveAction {
    /** Nothing to save — no draft, or one that still equals stored values. */
    NONE,

    /** Commit it, through the same path the pill's tap takes (values verbatim, golden rule). */
    COMMIT,

    /** Ask: a blocked draft is neither committed nor dropped behind the machinist's back. */
    PROMPT,
}

/**
 * The leave decision for one card: dirty × blocked. [draft] is the card's local edit (null when it
 * has none), [baseline] the stored values it shadows (null for the add flow's pending card, which
 * is therefore always dirty), and [confirmIssue] the result of [undercutConfirmIssue] on that
 * draft. Pure, so the swipe, the notch tap, and the overlay close all resolve identically.
 */
internal fun undercutLeaveAction(
    draft: UndercutDraft?,
    baseline: UndercutDraft?,
    confirmIssue: String?,
): UndercutLeaveAction = when {
    draft == null || draft == baseline -> UndercutLeaveAction.NONE
    confirmIssue != null -> UndercutLeaveAction.PROMPT
    else -> UndercutLeaveAction.COMMIT
}

/**
 * A strip's stable identity — a liner strip is its liner, a free strip its aft-most member. Strip
 * VALUES carry member ids and a draw range, so they change on every record edit; card drafts and
 * the open page must survive that (they are what produced the edit).
 */
internal fun undercutStripKey(strip: UndercutStrip): String = when (strip) {
    is UndercutStrip.LinerStrip -> "liner:${strip.linerId}"
    is UndercutStrip.FreeStrip -> "free:${strip.window.undercutIds.minOrNull() ?: strip.window.startMm}"
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
 * pending card (nothing recorded to delete).
 *
 * Numeric fields keep the commit-on-blur contract (`docs/contracts/NumberField.md`); the commit lands in the
 * draft rather than the ViewModel. The Distance/Length validators still block a span that leaves
 * the shaft — a value that could never be confirmed has no business reaching the draft — while the
 * adjacency check is confirm-time only, since a cut is legitimately dragged past a neighbour by
 * two separate field edits.
 */
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
    onDraftChange: (UndercutDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    // Which liner the LINER_* chips convert against: the draft's own reference liner while it
    // resolves, else the strip's liner, else whichever liner holds most of this cut.
    val previewUndercut = draft.toUndercut()
    val refLiner = undercutReferenceLinerFor(previewUndercut, linerSpans, stripLiner, oalMm)
    val refLinerStartMm = refLiner?.startMm ?: 0f
    val refLinerEndMm = refLiner?.endMm ?: 0f

    // Non-blocking classifiers, read off the DRAFT so the card warns about what the canvas is
    // previewing. Neither rewrites anything: the canvas renders the clamped span, and an
    // implausible Ø is a measurement — golden rule, never adjusted.
    val staleOverrun = isUndercutStaleOverrun(draft.startFromAftMm, draft.lengthMm, oalMm)
    val clamped = clampUndercutSpan(draft.startFromAftMm, draft.lengthMm, oalMm)
    val minSurfaceDiaMm =
        if (clamped.isEmpty) 0f else minOuterDiaOver(segs, clamped.startMm, clamped.endMm)
    val diaAtOrAboveSurface =
        draft.diaMm > 0f && minSurfaceDiaMm > 0f && draft.diaMm >= minSurfaceDiaMm

    // Every OTHER cut on the sheet — not just this strip's: a liner strip's pad shows neighbouring
    // stock, and a cut can be typed straight into it.
    val otherSpans = remember(sheetUndercuts, oalMm, draft.id) {
        undercutOtherSpans(sheetUndercuts, draft.id, oalMm)
    }
    val confirmIssue = undercutConfirmIssue(draft, oalMm, otherSpans)

    OutlinedCard(
        modifier = Modifier.fillMaxSize(),
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
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete $title")
                    }
                }
            }

            if (staleOverrun) {
                UndercutWarning("Extends past shaft end — re-measure")
            }
            if (diaAtOrAboveSurface) {
                UndercutWarning("Ø meets or exceeds shaft surface here")
            }

            // "Measure From" — which datum the Distance value is authored against, the wear
            // overlay's four-reference set: both S.E.T.s always, plus the reference liner's
            // edges while such a liner exists. Tapping a chip re-projects the DISPLAYED distance
            // immediately (canonical `startFromAftMm` never moves) but persists only on Confirm,
            // so Cancel reverts the chip too.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Measure From:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearChip("AFT S.E.T.", draft.reference == UndercutReference.AFT_SET) {
                        onDraftChange(
                            draft.copy(reference = UndercutReference.AFT_SET, referenceLinerId = ""),
                        )
                    }
                    WearChip("FWD S.E.T.", draft.reference == UndercutReference.FWD_SET) {
                        onDraftChange(
                            draft.copy(reference = UndercutReference.FWD_SET, referenceLinerId = ""),
                        )
                    }
                }
                if (refLiner != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WearChip("Liner AFT", draft.reference == UndercutReference.LINER_AFT) {
                            onDraftChange(
                                draft.copy(
                                    reference = UndercutReference.LINER_AFT,
                                    referenceLinerId = refLiner.id,
                                ),
                            )
                        }
                        WearChip("Liner FWD", draft.reference == UndercutReference.LINER_FWD) {
                            onDraftChange(
                                draft.copy(
                                    reference = UndercutReference.LINER_FWD,
                                    referenceLinerId = refLiner.id,
                                ),
                            )
                        }
                    }
                }
            }

            val displayedStartMm = canonicalToUndercutStartMm(
                reference = draft.reference,
                canonicalStartMm = draft.startFromAftMm,
                lengthMm = draft.lengthMm,
                aftSetXMm = aftSetXMm,
                fwdSetXMm = fwdSetXMm,
                linerStartMm = refLinerStartMm,
                linerEndMm = refLinerEndMm,
            )
            WearNum(
                label = "Distance from ${undercutReferenceLabel(draft.reference)} (${abbr(unit)})",
                initialDisplay = disp(displayedStartMm, unit),
                validator = { raw ->
                    val enteredMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    val canonicalMm = undercutStartToCanonicalMm(
                        draft.reference, enteredMm, draft.lengthMm, aftSetXMm, fwdSetXMm,
                        refLinerStartMm, refLinerEndMm,
                    )
                    undercutSpanIssue(canonicalMm, draft.lengthMm, oalMm)
                },
            ) { s ->
                val enteredMm = toMmOrNull(s, unit) ?: return@WearNum
                val canonicalMm = undercutStartToCanonicalMm(
                    draft.reference, enteredMm, draft.lengthMm, aftSetXMm, fwdSetXMm,
                    refLinerStartMm, refLinerEndMm,
                )
                onDraftChange(draft.copy(startFromAftMm = canonicalMm))
            }

            // A length edit keeps the AUTHORED Distance fixed: canonical start is re-derived
            // from the active reference at the new length (identity under an AFT reference;
            // under a FWD reference the cut's FWD end stays pinned and the cut grows/shrinks
            // AFT-ward). Committing the new length against the old canonical would rewrite
            // the displayed Distance by the length delta — golden-rule violation (on-device
            // report). Validation runs against the same recomputed canonical.
            fun canonicalAtLength(newLenMm: Float): Float = undercutCanonicalForNewLength(
                draft.reference, draft.startFromAftMm, draft.lengthMm, newLenMm,
                aftSetXMm, fwdSetXMm, refLinerStartMm, refLinerEndMm,
            )
            WearNum(
                label = "Length (${abbr(unit)})",
                initialDisplay = disp(draft.lengthMm, unit),
                validator = { raw ->
                    val enteredLenMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    undercutSpanIssue(canonicalAtLength(enteredLenMm), enteredLenMm, oalMm)
                },
            ) { s ->
                toMmOrNull(s, unit)?.let { newLenMm ->
                    onDraftChange(
                        draft.copy(
                            startFromAftMm = canonicalAtLength(newLenMm),
                            lengthMm = newLenMm,
                        ),
                    )
                }
            }

            // Measured Ø — a measurement, so any parseable value ≥ 0 lands **verbatim**
            // (golden rule). A Ø at or above the local surface is a warning above, never a
            // block and never an adjustment. An unentered Ø (0) shows an empty field.
            WearNum(
                label = "Measured Ø (${abbr(unit)})",
                initialDisplay = if (draft.diaMm > 0f) disp(draft.diaMm, unit) else "",
            ) { s ->
                val mm = toMmOrNull(s, unit) ?: return@WearNum
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
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating status pill — the overlay's whole save affordance
// ─────────────────────────────────────────────────────────────────────────────

/** What the selected card's draft is doing, as the pill states it. */
internal sealed interface UndercutPillState {
    /** Nothing to save — the card matches the record. Not a button. */
    object Saved : UndercutPillState

    /** A dirty draft that clears [undercutConfirmIssue]: one tap commits it. */
    object Unsaved : UndercutPillState

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
 * (on-device report). Tapping "Confirm change" runs the identical `confirmDraft` path the
 * auto-commit-on-leave runs, and stays on the card.
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
        UndercutPillState.Unsaved -> MaterialTheme.colorScheme.primaryContainer
        is UndercutPillState.Blocked -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (state) {
        UndercutPillState.Saved -> MaterialTheme.colorScheme.onSurfaceVariant
        UndercutPillState.Unsaved -> MaterialTheme.colorScheme.onPrimaryContainer
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
                        Text("Saved", style = MaterialTheme.typography.labelLarge)
                    }

                UndercutPillState.Unsaved ->
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
                        Text("Confirm change", style = MaterialTheme.typography.labelLarge)
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

/** Green check on the "Saved" pill — a settled state reads at a glance, outside the theme ramp. */
private val UNDERCUT_SAVED_TINT = Color(0xFF2E7D32)

@Composable
private fun UndercutWarning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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

/** Chip labels for [UndercutReference], used in the Distance field's dynamic label. */
internal fun undercutReferenceLabel(reference: UndercutReference): String = when (reference) {
    UndercutReference.AFT_SET -> "AFT S.E.T."
    UndercutReference.FWD_SET -> "FWD S.E.T."
    UndercutReference.LINER_AFT -> "Liner AFT"
    UndercutReference.LINER_FWD -> "Liner FWD"
}

// ─────────────────────────────────────────────────────────────────────────────
// Liner references — shared by the overlay's cards and the route's undercut list
// ─────────────────────────────────────────────────────────────────────────────

/** Every resolved liner as an [UndercutLinerSpan], aft → fwd — the liner-reference pool. */
internal fun linerSpansOf(resolvedComponents: List<ResolvedComponent>): List<UndercutLinerSpan> =
    resolvedComponents
        .filterIsInstance<ResolvedLiner>()
        .sortedBy { it.startMmPhysical }
        .map { UndercutLinerSpan(it.id, it.startMmPhysical, it.endMmPhysical) }

/**
 * The reference actually used to display/convert an undercut's Distance. A `LINER_*` reference
 * whose [Undercut.referenceLinerId] no longer resolves falls back to
 * [UndercutReference.AFT_SET] — the model's documented display rule. Canonical storage is never
 * touched by the fallback, and the stored reference is never rewritten behind the machinist's
 * back; the card just shows the AFT S.E.T. chip selected until a reference is picked again.
 */
internal fun effectiveUndercutReference(
    undercut: Undercut,
    linerSpans: List<UndercutLinerSpan>,
): UndercutReference =
    if (undercut.authoredReference == UndercutReference.LINER_AFT ||
        undercut.authoredReference == UndercutReference.LINER_FWD
    ) {
        if (linerSpans.any { it.id == undercut.referenceLinerId }) undercut.authoredReference
        else UndercutReference.AFT_SET
    } else {
        undercut.authoredReference
    }

/**
 * The liner an undercut's `LINER_*` chips convert against, or `null` when no liner is available
 * (the chips are then hidden). Preference order: the undercut's own stored reference liner while
 * it resolves, then the liner of the strip being viewed, then the liner holding the largest share
 * of the cut ([assignUndercutLiner]). The stored liner wins so a cut authored against one liner
 * keeps reading against it even while viewed from a neighbor's strip.
 */
internal fun undercutReferenceLinerFor(
    undercut: Undercut,
    linerSpans: List<UndercutLinerSpan>,
    stripLiner: UndercutLinerSpan?,
    oalMm: Float,
): UndercutLinerSpan? {
    linerSpans.firstOrNull { it.id == undercut.referenceLinerId }?.let { return it }
    stripLiner?.let { return it }
    val clamped = clampUndercutSpan(undercut.startFromAftMm, undercut.lengthMm, oalMm)
    if (clamped.isEmpty) return null
    val assignedId = assignUndercutLiner(
        UndercutSpanMm(undercut.id, clamped.startMm, clamped.endMm), linerSpans,
    ) ?: return null
    return linerSpans.firstOrNull { it.id == assignedId }
}

/**
 * The Distance an undercut reads under its effective reference, in canonical mm — the value the
 * card's field shows and the route's list row summarizes, so the two never disagree.
 */
internal fun undercutDisplayedDistanceMm(
    undercut: Undercut,
    reference: UndercutReference,
    refLiner: UndercutLinerSpan?,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = canonicalToUndercutStartMm(
    reference = reference,
    canonicalStartMm = undercut.startFromAftMm,
    lengthMm = undercut.lengthMm,
    aftSetXMm = aftSetXMm,
    fwdSetXMm = fwdSetXMm,
    linerStartMm = refLiner?.startMm ?: 0f,
    linerEndMm = refLiner?.endMm ?: 0f,
)

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
// Notch pipeline — shared by the overview canvas and the detail overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One undercut's drawable notch: its render-clamped span, the **drawn** floor Ø
 * ([normalizedNotchFloorDiaMm] over [effectiveNotchDiaMm] — drawn depth is exaggerated
 * against the sheet's deepest cut so a 1/16" cut still reads as a cut; a placed-but-empty Ø
 * gets a symbolic shallow floor first), and the surface-relative regions from
 * `geom/SurfaceProfileMath.kt`.
 */
internal data class UndercutNotch(
    val id: String,
    val startMm: Float,
    val endMm: Float,
    val floorDiaMm: Float,
    val profiles: List<NotchProfile>,
)

/**
 * Build every drawable notch for [undercuts] against the local outer surface [segs]. The single
 * pipeline behind both draw sites (this overlay's canvas and the undercut PDF), so the notch a
 * machinist taps on screen is the notch that prints.
 *
 * Regions come from `notchProfiles` at the TRUE effective floor (topology stays honest — a
 * cut that never reached the neighboring body must not draw into it); only the floor Ø on
 * the returned profiles is then swapped for the display-exaggerated one, deepening the
 * drawn floor and shoulders. Printed/stored Ø values are untouched.
 *
 * [exaggerationFrac] is the sheet's drawn-depth setting
 * ([com.android.shaftschematic.model.UndercutRecord.exaggerationFrac]) and
 * [sheetUndercuts] is the WHOLE sheet's cut list — the normalization reference
 * ([deepestUndercutDepthMm]) is per sheet, not per strip, so a strip holding only shallow
 * cuts draws them at the same reduced depth the full drawing gives them. It defaults to
 * [undercuts] for callers that already pass the whole sheet.
 */
internal fun buildUndercutNotches(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
    exaggerationFrac: Float,
    sheetUndercuts: List<Undercut> = undercuts,
): List<UndercutNotch> {
    val deepest = deepestUndercutDepthMm(sheetUndercuts, segs, oalMm)
    return undercuts.mapNotNull { u ->
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
        if (c.isEmpty) return@mapNotNull null
        val minSurface = minOuterDiaOver(segs, c.startMm, c.endMm)
        val floor = effectiveNotchDiaMm(u.diaMm, minSurface)
        val drawnFloor = normalizedNotchFloorDiaMm(u.diaMm, minSurface, deepest, exaggerationFrac)
        UndercutNotch(
            id = u.id,
            startMm = c.startMm,
            endMm = c.endMm,
            floorDiaMm = drawnFloor,
            profiles = notchProfiles(segs, c.startMm, c.endMm, floor)
                .map { it.copy(floorDiaMm = drawnFloor) },
        )
    }.sortedBy { it.startMm }
}

/**
 * Draw notches as **steps in the silhouette**: [voidColor] fill from the local surface down to
 * the floor (mirrored about the centreline), erasing the profile strokes inside the cut — the
 * mouth stays OPEN at the surface, never closed by a lid — then the outline: a full-height
 * **section face** at each region end (top surface to bottom surface, like any machined
 * diameter step, only where that end's surface stands above the floor) and the floor lines
 * across the span. Each cut reads as its own reduced-Ø rectangle section between two faces —
 * the hand-sketch convention (on-device report: a lid along the surface read as a white box
 * pasted ON the liner instead of material removed FROM it). Coordinate mapping is supplied by
 * the caller ([xPx]/[rPx]) so the overview canvas and the zoomed window run the same
 * construction at their own scales.
 */
internal fun DrawScope.drawUndercutNotches(
    notches: List<UndercutNotch>,
    xPx: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    voidColor: Color,
    outlineColor: Color,
    strokeWidthPx: Float,
    // Section-core refill — one step lighter than the caller's liner shade (see
    // UndercutStyle). Transparent in line-art mode, which leaves the core sheet-white.
    sectionFillColor: Color = Color.Black.copy(alpha = UNDERCUT_SECTION_FILL_ALPHA),
    // Dashed shoulders/floor mark a DRAFT notch (provisional, not yet in the record) —
    // the overlay passes a dash + a status color (primary while valid, error while its
    // confirm check fails); settled notches and the overview pass neither.
    pathEffect: PathEffect? = null,
) {
    notches.forEach { n ->
        n.profiles.forEach { p ->
            if (p.surface.size < 2) return@forEach
            val rFloor = rPx(p.floorDiaMm)
            val x0 = xPx(p.startMm)
            val x1 = xPx(p.endMm)
            val rSurfStart = rPx(p.surface.first().diaMm)
            val rSurfEnd = rPx(p.surface.last().diaMm)
            // The void's surface boundary overdraws OUTWARD by the stroke width: the
            // component outline is stroked centred on the surface line, so a fill that
            // stops exactly there would leave half of the *component's* stroke ragged
            // across the mouth. The mouth is then closed by the notch's own top edge
            // below, at the notch outline's weight/colour.
            val od = strokeWidthPx

            val topVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy - rSurfStart - od)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy - rPx(p.surface[i].diaMm) - od)
                }
                lineTo(x1, cy - rFloor)
                lineTo(x0, cy - rFloor)
                close()
            }
            val botVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy + rSurfStart + od)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy + rPx(p.surface[i].diaMm) + od)
                }
                lineTo(x1, cy + rFloor)
                lineTo(x0, cy + rFloor)
                close()
            }
            drawPath(topVoid, color = voidColor)
            drawPath(botVoid, color = voidColor)

            // Remaining core: erased to the sheet colour, then refilled one step LIGHTER
            // than the liner shade ([sectionFillColor] — half its alpha) so the section
            // reads distinct from the liner around it (on-device request).
            drawRect(voidColor, topLeft = Offset(x0, cy - rFloor), size = Size(x1 - x0, 2f * rFloor))
            drawRect(
                sectionFillColor,
                topLeft = Offset(x0, cy - rFloor),
                size = Size(x1 - x0, 2f * rFloor),
            )

            // Step-section outline: full-height faces where the surface stands above the
            // floor, then the floor lines. No lid — the mouth stays open.
            if (rSurfStart > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                drawLine(outlineColor, Offset(x0, cy - rSurfStart), Offset(x0, cy + rSurfStart), strokeWidthPx, pathEffect = pathEffect)
            }
            if (rSurfEnd > rFloor + NOTCH_FACE_MIN_STEP_PX) {
                drawLine(outlineColor, Offset(x1, cy - rSurfEnd), Offset(x1, cy + rSurfEnd), strokeWidthPx, pathEffect = pathEffect)
            }
            drawLine(outlineColor, Offset(x0, cy - rFloor), Offset(x1, cy - rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x0, cy + rFloor), Offset(x1, cy + rFloor), strokeWidthPx, pathEffect = pathEffect)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

/** A dimension value in the active unit, e.g. `7.688in` — the wear overlay's rail convention. */
internal fun undercutDimLabel(mm: Float, unit: UnitSystem): String = disp(mm, unit) + abbr(unit)

/**
 * One dimension span on the rail: a line between [x0] and [x1] with arrowheads (inward when the
 * span can host them, outward otherwise) and the value centred above. The canvas twin of the
 * PDF strip rail — modest by design; the PDF owns the printed typography.
 */
internal fun DrawScope.drawUndercutDimSpan(
    x0: Float,
    x1: Float,
    y: Float,
    label: String,
    paint: android.graphics.Paint,
    color: Color,
) {
    val lo = min(x0, x1)
    val hi = max(x0, x1)
    if (hi - lo < 1f) return
    val w = 1f
    val arrow = 5.dp.toPx()
    drawLine(color, Offset(lo, y), Offset(hi, y), w)

    fun head(x: Float, dir: Float) {
        drawLine(color, Offset(x, y), Offset(x + dir * arrow, y - arrow * 0.4f), w)
        drawLine(color, Offset(x, y), Offset(x + dir * arrow, y + arrow * 0.4f), w)
    }
    if (hi - lo > arrow * 3f) {
        head(lo, 1f)
        head(hi, -1f)
    } else {
        // Too tight for inward arrows: extend the line past each end and point in from outside.
        drawLine(color, Offset(lo - arrow, y), Offset(lo, y), w)
        drawLine(color, Offset(hi, y), Offset(hi + arrow, y), w)
        head(lo, -1f)
        head(hi, 1f)
    }
    drawContext.canvas.nativeCanvas.drawRichText(label, (lo + hi) / 2f, y - 6.dp.toPx(), paint)
}

/**
 * Strip title: a liner strip is named after its liner (`buildLinerTitleById`, the same positional
 * names the wear document uses), a free strip states its zoom range in shaft space. A liner the
 * spec no longer holds falls back to the range text.
 */
private fun undercutStripTitle(strip: UndercutStrip, spec: ShaftSpec, unit: UnitSystem): String {
    val rangeText =
        "Undercuts ${disp(strip.drawStartMm, unit)}–${disp(strip.drawEndMm, unit)} ${abbr(unit)} from AFT"
    return when (strip) {
        is UndercutStrip.FreeStrip -> rangeText
        is UndercutStrip.LinerStrip -> buildLinerTitleById(spec)[strip.linerId] ?: rangeText
    }
}

/** AFT/FWD S.E.T. x positions in physical shaft space (mm from the AFT face). */
internal fun undercutSetPositions(spec: ShaftSpec): Pair<Float, Float> {
    val win = computeOalWindow(spec)
    val set = computeSetPositionsInMeasureSpace(win, spec)
    return set.aftSETxMm.toFloat() to set.fwdSETxMm.toFloat()
}
