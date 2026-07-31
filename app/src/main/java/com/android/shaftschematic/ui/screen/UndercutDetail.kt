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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.NotchProfile
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.canonicalToUndercutStartMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.effectiveNotchDiaMm
import com.android.shaftschematic.geom.isUndercutStaleOverrun
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.notchProfiles
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.pickUndercutAt
import com.android.shaftschematic.geom.planDiaCallouts
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
import kotlin.math.max
import kotlin.math.min

/**
 * UndercutDetail
 *
 * A full-screen "zoom in" overlay on ONE undercut **cluster window** — the drawing the shop
 * actually reads (see `docs/UndercutDrawing_PLAN.md` §6). Where the wear overlay breaks out one
 * *component*, this one breaks out an axial *window*: undercuts are not bound to components, so
 * the zoom unit is the padded cluster window from `geom/UndercutMath.kt`'s `clusterUndercuts`,
 * and the profile inside it is whatever resolved geometry the window happens to cross (a liner,
 * a liner edge, a body step, a taper run).
 *
 * Contents, aft → fwd:
 * - a **dimension rail above** — the cluster total span on the upper line, and below it the
 *   chained run: window AFT edge → each shoulder → each gap → window FWD edge;
 * - the **window profile** with the undercut **notches** cut into it (`geom/SurfaceProfileMath.kt`
 *   via [buildUndercutNotches], the identical pipeline the PDF composer uses, so the two draw the
 *   same notch by construction);
 * - **Ø callouts below** through the shared `planDiaCallouts` engine — one station per undercut at
 *   its axial centre, leader down to the notch floor. A Ø-less undercut (`diaMm == 0`) shows "—"
 *   here so it stays findable, and never prints.
 *
 * Window ends: an end that lands on the shaft's own extent (x = 0 or x = OAL) is a flat edge —
 * the shaft physically stops there — and picks up the thread hatch when the component there is a
 * thread. Any other end is a truncation and gets the S-curve break
 * ([drawBreakEdgeCompose], AFT `eyeAtTop = true` / FWD `false`).
 *
 * Same posture as [ComponentWearDetailOverlay]: a plain composable, not a nav destination,
 * dismissed via its own [BackHandler] or the back arrow, with pinch-to-zoom (0.5×–6×) +
 * two-finger pan whose transform the tap handler inverts, so placement/hit-testing always runs in
 * untransformed canvas space. The Canvas renderer and the tap handler share one layout
 * ([computeUndercutWindowLayout]) so a tapped undercut is the one that was drawn.
 *
 * Coordinate rule: everything here is canonical **shaft space** (mm from the AFT face) — undercuts
 * have no component key, so there is no component-local space to convert through. Only the
 * "Distance" field is re-projected, against the authored S.E.T.
 * ([canonicalToUndercutStartMm]/[undercutStartToCanonicalMm]).
 */
@Composable
fun UndercutWindowDetailOverlay(
    window: UndercutWindow,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    undercutRecord: UndercutRecord,
    onUpdateUndercut: (id: String, startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) -> Unit,
    onUpdateReference: (id: String, reference: UndercutReference) -> Unit,
    onRemoveUndercut: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }

    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents) { surfaceSegsFrom(resolvedComponents) }

    // The window's member undercuts, aft → fwd. Ids the record no longer holds simply drop out.
    val undercuts = remember(undercutRecord, window) {
        window.undercutIds
            .mapNotNull { id -> undercutRecord.undercuts.firstOrNull { it.id == id } }
            .sortedBy { it.startFromAftMm }
    }
    val notches = remember(undercuts, segs, oalMm) { buildUndercutNotches(undercuts, segs, oalMm) }
    val spans = remember(undercuts, oalMm) {
        undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }

    // SET positions for the cards' "Measure From" re-projection. Physical shaft space already
    // (computeOalWindow's measureStartMm is always 0), so no further offset is applied.
    val setPositions = remember(spec) { undercutSetPositions(spec) }
    val aftSetXMm = setPositions.first
    val fwdSetXMm = setPositions.second

    var selectedId by remember(window) { mutableStateOf<String?>(null) }

    // ── Theme colors captured here — the Canvas draw scope must not read MaterialTheme ──
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val linerFillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
    val railColor = outlineColor.copy(alpha = 0.65f)
    val witnessColor = outlineColor.copy(alpha = 0.35f)
    val selectColor = MaterialTheme.colorScheme.primary
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
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

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
                    text = undercutWindowTitle(window, unit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            HorizontalDivider()

            // ── Scrollable content ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val totalRailRowDp = 30.dp
                val chainRailRowDp = 30.dp
                val profileRowDp = 160.dp
                // Fixed band for the Ø callouts (leader + up to two staggered label rows), so the
                // canvas height doesn't jump between one and two rows.
                val diaBandDp = if (undercuts.isEmpty()) 0.dp else 48.dp
                val canvasHeightDp = totalRailRowDp + chainRailRowDp + profileRowDp + diaBandDp

                val winLenMm = window.lengthMm.coerceAtLeast(0.001f)
                val maxOdMm = remember(segs, window) {
                    val env = maxOuterDiaOver(segs, window.startMm, window.endMm)
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
                        .pointerInput(window, spans, winLenMm, maxOdMm) {
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
                                val xMm = window.startMm + (tap.x - lay.startPx) / lay.pxPerMm
                                val padMm = 12.dp.toPx() / lay.pxPerMm
                                selectedId = pickUndercutAt(xMm, spans, padMm)
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
                        val xPx: (Float) -> Float = { mm -> lay.startPx + (mm - window.startMm) * lay.pxPerMm }
                        val rPx: (Float) -> Float = { diaMm -> diaMm * 0.5f * lay.pxPerMm }

                        // ── Window profile: every resolved component clipped to the window ──
                        // Liners paint last so a liner over a body reads as the surface, matching
                        // the max-wins envelope the notch math uses.
                        val eps = 1e-3f
                        val drawn = resolvedComponents
                            .filter { it !is ResolvedCouplerBoltSlot }
                            .sortedBy { if (it is ResolvedLiner) 1 else 0 }
                        drawn.forEach { rc ->
                            val a = max(rc.startMmPhysical, window.startMm)
                            val b = min(rc.endMmPhysical, window.endMm)
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
                            } else {
                                drawPath(path, color = if (rc is ResolvedLiner) linerFillColor else fillColor)
                            }
                            drawLine(outlineColor, Offset(xa, cy - rA), Offset(xb, cy - rB), outlineWidthPx)
                            drawLine(outlineColor, Offset(xa, cy + rA), Offset(xb, cy + rB), outlineWidthPx)
                            // Vertical faces only where the edge is the component's own — an edge
                            // the window cut off is closed by the break edge / flat end below.
                            if (rc.startMmPhysical > window.startMm + eps) {
                                drawLine(outlineColor, Offset(xa, cy - rA), Offset(xa, cy + rA), outlineWidthPx)
                            }
                            if (rc.endMmPhysical < window.endMm - eps) {
                                drawLine(outlineColor, Offset(xb, cy - rB), Offset(xb, cy + rB), outlineWidthPx)
                            }
                        }

                        // ── Window ends: flat at the shaft's own extent, S-curve break otherwise ──
                        val rAft = rPx(outerDiaAt(segs, window.startMm + eps))
                        val rFwd = rPx(outerDiaAt(segs, window.endMm - eps))
                        if (rAft > 0f) {
                            if (window.startMm <= eps) {
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
                            if (window.endMm >= oalMm - eps) {
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
                        drawUndercutNotches(
                            notches = notches,
                            xPx = xPx,
                            rPx = rPx,
                            cy = cy,
                            voidColor = Color.White,
                            outlineColor = outlineColor,
                            strokeWidthPx = outlineWidthPx,
                        )

                        // ── Selection highlight ──
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

                        val stops = buildList {
                            add(window.startMm)
                            spans.forEach { add(it.startMm); add(it.endMm) }
                            add(window.endMm)
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
                        if (spans.isNotEmpty()) {
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
                            val stations = notches.map { n ->
                                val u = undercuts.first { it.id == n.id }
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

                Text(
                    text = "Tap an undercut to highlight it. Distance, length, and Ø are typed " +
                        "below — the tap only selects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                undercuts.forEachIndexed { i, u ->
                    UndercutCard(
                        index = i,
                        undercut = u,
                        unit = unit,
                        oalMm = oalMm,
                        segs = segs,
                        aftSetXMm = aftSetXMm,
                        fwdSetXMm = fwdSetXMm,
                        selected = selectedId == u.id,
                        onSelect = { selectedId = u.id },
                        onCommit = { s, l, d, n -> onUpdateUndercut(u.id, s, l, d, n) },
                        onUpdateReference = { ref -> onUpdateReference(u.id, ref) },
                        onDelete = { onRemoveUndercut(u.id) },
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Undercut card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UndercutCard(
    index: Int,
    undercut: Undercut,
    unit: UnitSystem,
    oalMm: Float,
    segs: List<SurfaceSeg>,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onCommit: (startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) -> Unit,
    onUpdateReference: (UndercutReference) -> Unit,
    onDelete: () -> Unit,
) {
    val reference = undercut.authoredReference

    // Non-blocking classifiers. Neither rewrites stored data: the canvas already renders the
    // clamped span, and an implausible Ø is a measurement — golden rule, never adjusted.
    val staleOverrun = isUndercutStaleOverrun(undercut.startFromAftMm, undercut.lengthMm, oalMm)
    val clamped = clampUndercutSpan(undercut.startFromAftMm, undercut.lengthMm, oalMm)
    val minSurfaceDiaMm =
        if (clamped.isEmpty) 0f else minOuterDiaOver(segs, clamped.startMm, clamped.endMm)
    val diaAtOrAboveSurface =
        undercut.diaMm > 0f && minSurfaceDiaMm > 0f && undercut.diaMm >= minSurfaceDiaMm

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Undercut ${index + 1}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete undercut ${index + 1}")
                }
            }

            if (staleOverrun) {
                UndercutWarning("Extends past shaft end — re-measure")
            }
            if (diaAtOrAboveSurface) {
                UndercutWarning("Ø meets or exceeds shaft surface here")
            }

            // "Measure From" — which S.E.T. the Distance value is authored against. Tapping a
            // chip persists the reference immediately and re-projects the DISPLAYED distance
            // only; canonical `startFromAftMm` never moves.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Measure From:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearChip("AFT S.E.T.", reference == UndercutReference.AFT_SET) {
                        onSelect(); onUpdateReference(UndercutReference.AFT_SET)
                    }
                    WearChip("FWD S.E.T.", reference == UndercutReference.FWD_SET) {
                        onSelect(); onUpdateReference(UndercutReference.FWD_SET)
                    }
                }
            }

            val displayedStartMm = canonicalToUndercutStartMm(
                reference = reference,
                canonicalStartMm = undercut.startFromAftMm,
                lengthMm = undercut.lengthMm,
                aftSetXMm = aftSetXMm,
                fwdSetXMm = fwdSetXMm,
            )
            WearNum(
                label = "Distance from ${undercutReferenceLabel(reference)} (${abbr(unit)})",
                initialDisplay = disp(displayedStartMm, unit),
                validator = { raw ->
                    val enteredMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    val canonicalMm = undercutStartToCanonicalMm(
                        reference, enteredMm, undercut.lengthMm, aftSetXMm, fwdSetXMm,
                    )
                    undercutSpanIssue(canonicalMm, undercut.lengthMm, oalMm)
                },
            ) { s ->
                val enteredMm = toMmOrNull(s, unit) ?: return@WearNum
                val canonicalMm = undercutStartToCanonicalMm(
                    reference, enteredMm, undercut.lengthMm, aftSetXMm, fwdSetXMm,
                )
                onCommit(canonicalMm, undercut.lengthMm, undercut.diaMm, undercut.note)
            }

            WearNum(
                label = "Length (${abbr(unit)})",
                initialDisplay = disp(undercut.lengthMm, unit),
                validator = { raw ->
                    val enteredLenMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    undercutSpanIssue(undercut.startFromAftMm, enteredLenMm, oalMm)
                },
            ) { s ->
                toMmOrNull(s, unit)?.let {
                    onCommit(undercut.startFromAftMm, it, undercut.diaMm, undercut.note)
                }
            }

            // Measured Ø — a measurement, so any parseable value ≥ 0 commits **verbatim**
            // (golden rule). A Ø at or above the local surface is a warning above, never a
            // block and never an adjustment. An unentered Ø (0) shows an empty field.
            WearNum(
                label = "Measured Ø (${abbr(unit)})",
                initialDisplay = if (undercut.diaMm > 0f) disp(undercut.diaMm, unit) else "",
            ) { s ->
                val mm = toMmOrNull(s, unit) ?: return@WearNum
                onCommit(undercut.startFromAftMm, undercut.lengthMm, mm, undercut.note)
            }

            // Notes — same tap-and-leave no-op discipline as the numeric fields above
            // (NumberField.md): capture on focus, commit on blur only if changed.
            var noteDraft by remember(undercut.id, undercut.note) { mutableStateOf(undercut.note) }
            var noteFocusText by remember(undercut.id, undercut.note) { mutableStateOf<String?>(null) }
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                label = { Text("Notes") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { f ->
                        if (f.isFocused) {
                            noteFocusText = noteDraft
                            onSelect()
                        } else {
                            val captured = noteFocusText
                            noteFocusText = null
                            if (captured != null && noteDraft != captured) {
                                onCommit(undercut.startFromAftMm, undercut.lengthMm, undercut.diaMm, noteDraft)
                            }
                        }
                    },
            )
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
private fun undercutReferenceLabel(reference: UndercutReference): String = when (reference) {
    UndercutReference.AFT_SET -> "AFT S.E.T."
    UndercutReference.FWD_SET -> "FWD S.E.T."
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
// Notch pipeline — shared by the overview canvas and the detail overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One undercut's drawable notch: its render-clamped span, the floor Ø actually cut to
 * ([effectiveNotchDiaMm] — a placed-but-empty Ø gets a symbolic shallow floor so the section
 * stays visible), and the surface-relative regions from `geom/SurfaceProfileMath.kt`.
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
 */
internal fun buildUndercutNotches(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
): List<UndercutNotch> = undercuts.mapNotNull { u ->
    val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
    if (c.isEmpty) return@mapNotNull null
    val floor = effectiveNotchDiaMm(u.diaMm, minOuterDiaOver(segs, c.startMm, c.endMm))
    UndercutNotch(
        id = u.id,
        startMm = c.startMm,
        endMm = c.endMm,
        floorDiaMm = floor,
        profiles = notchProfiles(segs, c.startMm, c.endMm, floor),
    )
}.sortedBy { it.startMm }

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
) {
    notches.forEach { n ->
        n.profiles.forEach { p ->
            if (p.surface.size < 2) return@forEach
            val rFloor = rPx(p.floorDiaMm)
            val x0 = xPx(p.startMm)
            val x1 = xPx(p.endMm)
            val rSurfStart = rPx(p.surface.first().diaMm)
            val rSurfEnd = rPx(p.surface.last().diaMm)

            val topVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy - rSurfStart)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy - rPx(p.surface[i].diaMm))
                }
                lineTo(x1, cy - rFloor)
                lineTo(x0, cy - rFloor)
                close()
            }
            val botVoid = Path().apply {
                moveTo(xPx(p.surface.first().xMm), cy + rSurfStart)
                for (i in 1 until p.surface.size) {
                    lineTo(xPx(p.surface[i].xMm), cy + rPx(p.surface[i].diaMm))
                }
                lineTo(x1, cy + rFloor)
                lineTo(x0, cy + rFloor)
                close()
            }
            drawPath(topVoid, color = voidColor)
            drawPath(botVoid, color = voidColor)

            drawLine(outlineColor, Offset(x0, cy - rSurfStart), Offset(x0, cy - rFloor), strokeWidthPx)
            drawLine(outlineColor, Offset(x1, cy - rSurfEnd), Offset(x1, cy - rFloor), strokeWidthPx)
            drawLine(outlineColor, Offset(x0, cy - rFloor), Offset(x1, cy - rFloor), strokeWidthPx)
            drawLine(outlineColor, Offset(x0, cy + rSurfStart), Offset(x0, cy + rFloor), strokeWidthPx)
            drawLine(outlineColor, Offset(x1, cy + rSurfEnd), Offset(x1, cy + rFloor), strokeWidthPx)
            drawLine(outlineColor, Offset(x0, cy + rFloor), Offset(x1, cy + rFloor), strokeWidthPx)
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

/** Window title: the zoom range in shaft space, in the active unit. */
private fun undercutWindowTitle(window: UndercutWindow, unit: UnitSystem): String =
    "Undercuts ${disp(window.startMm, unit)}–${disp(window.endMm, unit)} ${abbr(unit)} from AFT"

/** AFT/FWD S.E.T. x positions in physical shaft space (mm from the AFT face). */
internal fun undercutSetPositions(spec: ShaftSpec): Pair<Float, Float> {
    val win = computeOalWindow(spec)
    val set = computeSetPositionsInMeasureSpace(win, spec)
    return set.aftSETxMm.toFloat() to set.fwdSETxMm.toFloat()
}
