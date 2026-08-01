// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutDetail.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.normalizedNotchFloorDiaMm
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.pickUndercutAt
import com.android.shaftschematic.geom.planDiaCallouts
import com.android.shaftschematic.geom.undercutOverlapIssue
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
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import kotlin.math.max
import kotlin.math.min

/**
 * UndercutDetail
 *
 * A full-screen "zoom in" overlay on ONE undercut **detail strip** — the drawing the shop
 * actually reads (see `docs/UndercutDrawing.md`). Where the wear overlay breaks out one
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
 * per `docs/NumberField.md`), the "Measure From" chips, and the note all land in the draft, and
 * the canvas previews the SELECTED card's draft in place of its stored notch. Nothing reaches
 * `UndercutRecord` until **Confirm**, which is enabled only while the draft differs from stored
 * AND clears both blocking checks — [undercutSpanIssue] (shaft bounds) and [undercutOverlapIssue]
 * (no intruding into another cut's bounds). **Cancel** drops the draft back to stored. Because
 * page order is keyed on **stored** starts, cards reorder only on confirm — and the carousel then
 * follows the confirmed cut to its new aft → fwd position. "Add undercut" opens a **draft-only**
 * page: it previews and orders like any other card but enters the record only on Confirm, so a
 * cancelled add leaves no ghost cut behind.
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
    BackHandler { onClose() }

    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents) { surfaceSegsFrom(resolvedComponents) }
    val drawStartMm = strip.drawStartMm
    val drawEndMm = strip.drawEndMm

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
    // Confirm-blocking status of the previewed draft, for the canvas: its notch draws
    // dashed in the selection color while valid, in the error color while this is
    // non-null — the same check that gates the card's Confirm button, recomputed here
    // against the sheet so the drawing and the button can never disagree.
    val activeDraftIssue = remember(activeDraft, oalMm, undercutRecord) {
        activeDraft?.let { d ->
            val others = undercutRecord.undercuts
                .filter { it.id != d.id }
                .map { u ->
                    val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
                    UndercutSpanMm(u.id, c.startMm, c.endMm)
                }
                .filter { it.endMm > it.startMm }
            undercutConfirmIssue(d, oalMm, others)
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

    // Confirm — the ONE path from draft to record. Values land verbatim (golden rule); the
    // reference is persisted only when the chips actually moved, so a card whose stored
    // `LINER_*` reference is displaying its AFT_SET fallback is never silently rewritten.
    fun confirmDraft(draft: UndercutDraft) {
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
            selectedId = newId
        } else {
            onUpdateUndercut(draft.id, draft.startFromAftMm, draft.lengthMm, draft.diaMm, draft.note)
            val base = baselines[draft.id]
            if (base != null &&
                (base.reference != draft.reference || base.referenceLinerId != draft.referenceLinerId)
            ) {
                onUpdateReference(draft.id, draft.reference, draft.referenceLinerId)
            }
            drafts = drafts - draft.id
            selectedId = draft.id
        }
    }

    // ── Theme colors captured here — the Canvas draw scope must not read MaterialTheme ──
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val linerFillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
    val railColor = outlineColor.copy(alpha = 0.65f)
    val witnessColor = outlineColor.copy(alpha = 0.35f)
    val selectColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
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
                IconButton(onClick = onClose) {
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
                                val label = if (u.diaMm > 0f) formatDiaWithUnit(u.diaMm.toDouble(), unit) else "—"
                                DiaCalloutStation(
                                    key = n.id,
                                    stationX = xPx((n.startMm + n.endMm) / 2f),
                                    label = label,
                                    labelWidth = textPaint.measureText(label),
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
                                    p.label, p.labelCx, p.labelTopY - fm.ascent, textPaint,
                                )
                            }
                        }
                    }
                }

                // ── Shaft-direction reference: AFT at the left, FWD at the right ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                // Authoring entry point. On a liner strip this is the reason a liner with no cuts
                // yet is tappable on the overview at all; the new page is a DRAFT — it previews
                // here but enters the record only on Confirm. The default span takes the first
                // free gap in the strip so it doesn't land on top of an existing cut.
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
                                // from the liner's own AFT edge.
                                reference = if (stripLiner != null) UndercutReference.LINER_AFT
                                            else UndercutReference.AFT_SET,
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
                        onConfirm = { confirmDraft(draft) },
                        onCancel = { cancelDraft(id) },
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
 * through [onDraftChange]; [onConfirm] is the only path to the record and is enabled only while
 * the draft is [dirty] and [undercutConfirmIssue] clears. [onCancel] restores stored values (and
 * discards the page entirely when this is the add flow's pending card, whose [onDelete] is null —
 * there is nothing recorded to delete).
 *
 * Numeric fields keep the commit-on-blur contract (`docs/NumberField.md`); the commit lands in the
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
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
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
        sheetUndercuts.filter { it.id != draft.id }.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
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

            WearNum(
                label = "Length (${abbr(unit)})",
                initialDisplay = disp(draft.lengthMm, unit),
                validator = { raw ->
                    val enteredLenMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    undercutSpanIssue(draft.startFromAftMm, enteredLenMm, oalMm)
                },
            ) { s ->
                toMmOrNull(s, unit)?.let { onDraftChange(draft.copy(lengthMm = it)) }
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

            if (dirty && confirmIssue != null) {
                UndercutWarning(confirmIssue)
            }

            // Confirm/Cancel. The plain testTags name the VISIBLE card's actions — neighbouring
            // pages stay composed for the peek, and duplicate tags would be ambiguous.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = dirty,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (selected) Modifier.testTag("undercut_cancel") else Modifier),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    enabled = dirty && confirmIssue == null,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (selected) Modifier.testTag("undercut_confirm") else Modifier),
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

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
 * Draw notches as **voids**: [voidColor] fill from the local surface down to the floor (mirrored
 * about the centreline), erasing the profile strokes inside the cut, then the outline — shoulder
 * at each end plus the floor line, top and bottom. Coordinate mapping is supplied by the caller
 * ([xPx]/[rPx]) so the overview canvas and the zoomed window run the same construction at their
 * own scales.
 */
internal fun DrawScope.drawUndercutNotches(
    notches: List<UndercutNotch>,
    xPx: (Float) -> Float,
    rPx: (Float) -> Float,
    cy: Float,
    voidColor: Color,
    outlineColor: Color,
    strokeWidthPx: Float,
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
            // stops exactly there leaves half the stroke as a line across the notch
            // mouth (on-device report) — the cut removed that surface, the mouth is open.
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

            drawLine(outlineColor, Offset(x0, cy - rSurfStart), Offset(x0, cy - rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x1, cy - rSurfEnd), Offset(x1, cy - rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x0, cy - rFloor), Offset(x1, cy - rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x0, cy + rSurfStart), Offset(x0, cy + rFloor), strokeWidthPx, pathEffect = pathEffect)
            drawLine(outlineColor, Offset(x1, cy + rSurfEnd), Offset(x1, cy + rFloor), strokeWidthPx, pathEffect = pathEffect)
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
    drawContext.canvas.nativeCanvas.drawText(label, (lo + hi) / 2f, y - 6.dp.toPx(), paint)
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
