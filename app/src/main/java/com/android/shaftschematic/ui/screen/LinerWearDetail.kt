// file: app/src/main/java/com/android/shaftschematic/ui/screen/LinerWearDetail.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.PitHitTarget
import com.android.shaftschematic.geom.acrossFracFromTapY
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.pitCenterY
import com.android.shaftschematic.geom.pitHalfArm
import com.android.shaftschematic.geom.pickPitAt
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.ui.input.NumericInputField
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.maxDiaMm
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.util.UnitSystem

/**
 * ComponentWearDetail
 *
 * A full-screen "zoom in" overlay on ONE component, broken out of the shaft (S-curve break edges
 * on short neighbor stubs, shop-sketch convention — see `pdf/BreakSymbol.kt` for the PDF-layer
 * original; this file replicates the visual in Compose without importing pdf code).
 *
 * Originally liner-only (`docs/LinerWearAreas_Proposal.md` Phase 3); generalized 2026-07-21 so a
 * **body** or **taper** can be opened the same way (the proposal's §10.5 "wear on bodies/tapers"
 * open question). What the overlay offers depends on the component:
 * - **Liners** get the full liner-wear-band editor (hatched bands + per-spot dimension rail +
 *   `WearSpotCard`s with the "Measure From" references) **and** pit markers.
 * - **Bodies / tapers** get pit markers only (wear bands are a liner-only concept).
 *
 * **Pit markers** (all component types): the machinist's hand-drawn "X" for a pit / dye-penetrant
 * failure. Tap bare metal on the broken-out segment to drop an X (at the current brush size); tap
 * an existing X to remove it. Small vs large X = little hole vs bigger cavity (a *symbol* size,
 * not the pit's true diameter). Pits are pure reference data (`WearPit`, `geom/WearPitMath.kt`) —
 * they never touch geometry. The tap handler and the canvas renderer share one layout
 * ([computeSegDetailLayout]) so a tapped X removes the same X that was drawn.
 *
 * Same pattern as `PdfPreviewOverlay` (`RunoutRoute.kt`): a plain composable, not a nav
 * destination, dismissed via [BackHandler] or the back-arrow button — the caller composes this
 * conditionally (`if (selectedComponentId != null) ComponentWearDetailOverlay(...)`).
 *
 * Layout math is self-contained here — it draws ONE component + short neighbor stubs, not the
 * whole shaft, so it does not use `ShaftLayout`/`ShaftRenderer`. Neighbor geometry still comes
 * from the resolved component list (never raw `spec.bodies/tapers/...`).
 *
 * Coordinate rule: [WearSpot.startMm] and [WearPit.axialMm] are component-local (from the AFT
 * edge). Everything drawn here is already in component-local space; shaft-space conversion only
 * matters to callers that place features on the *whole-shaft* profile (the PDF).
 */
@Composable
fun ComponentWearDetailOverlay(
    componentId: String,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    wearRecord: WearRecord,
    onAddSpot: (linerId: String) -> Unit,
    onUpdateSpot: (id: String, startMm: Float, lengthMm: Float, minDiaMm: Float, note: String) -> Unit,
    onUpdateSpotReference: (id: String, reference: WearSpotReference) -> Unit,
    onRemoveSpot: (id: String) -> Unit,
    onAddPit: (componentId: String, axialMm: Float, acrossFrac: Float, size: PitSize) -> Unit,
    onRemovePit: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }

    // Only body / taper / liner are pit-eligible (threads are the threaded ends; coupler slots
    // are overlays). Anything else — or a component that no longer resolves (deleted on the Shaft
    // tab while this overlay was open, or an autosave restore raced the tap) — bounces back.
    val component = resolvedComponents.firstOrNull { it.id == componentId }
    if (component == null || !component.isPitEligible) {
        LaunchedEffect(componentId) { onClose() }
        return
    }
    val liner = component as? ResolvedLiner

    val title = remember(spec, componentId) { componentWearTitle(spec, component) }
    val spots = remember(wearRecord, componentId) {
        wearRecord.spots.filter { it.linerId == componentId }.sortedBy { it.startMm }
    }
    val pits = remember(wearRecord, componentId) {
        wearRecord.pits.filter { it.componentId == componentId }
    }
    val lenMm = remember(component) {
        (component.endMmPhysical - component.startMmPhysical).coerceAtLeast(0.001f)
    }
    val (startDiaMm, endDiaMm) = remember(component) { componentEdgeDias(component) }

    // SET positions (AFT/FWD SET "Measure from" references — liner spots only).
    val setPositions = remember(spec) {
        val win = computeOalWindow(spec)
        computeSetPositionsInMeasureSpace(win, spec)
    }
    val aftSetXMm = setPositions.aftSETxMm.toFloat()
    val fwdSetXMm = setPositions.fwdSETxMm.toFloat()

    // Nearest non-overlay neighbor on each side, from the RESOLVED list — coupler bolt slots are
    // overlays and never real geometry neighbors (CLAUDE.md).
    val eps = 1e-3f
    val leftNeighbor = remember(resolvedComponents, component) {
        resolvedComponents
            .filter { it.id != component.id && it !is ResolvedCouplerBoltSlot && it.endMmPhysical <= component.startMmPhysical + eps }
            .maxByOrNull { it.endMmPhysical }
    }
    val rightNeighbor = remember(resolvedComponents, component) {
        resolvedComponents
            .filter { it.id != component.id && it !is ResolvedCouplerBoltSlot && it.startMmPhysical >= component.endMmPhysical - eps }
            .minByOrNull { it.startMmPhysical }
    }

    // ── Theme colors captured here — the Canvas draw scope below must not read MaterialTheme ──
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val wearTintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
    val wearHatchColor = MaterialTheme.colorScheme.error.copy(alpha = 0.80f)
    val pitColor = MaterialTheme.colorScheme.error
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

    // Brush size for the NEXT placed pit (small X's mark little holes, large for bigger
    // cavities — matching the hand convention).
    var brushSize by remember { mutableStateOf(PitSize.SMALL) }
    // Explicit Add / Remove tool so a stray tap can't place or delete unexpectedly — in Remove
    // mode a miss is a no-op; in Add mode a tap always places (no accidental deletes).
    var pitTool by remember { mutableStateOf(PitTool.ADD) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Wear Document")
                }
                Text(
                    text = "$title — wear inspection",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            HorizontalDivider()

            // ── Scrollable content ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Broken-out component canvas ──────────────────────────────
                val stubWidthDp = 24.dp
                val stubRowHeightDp = 140.dp
                val railRowHeightDp = 32.dp
                val railTopGapDp = 12.dp
                val canvasHeightDp = stubRowHeightDp +
                    if (spots.isEmpty()) 0.dp else (railTopGapDp + railRowHeightDp * spots.size)

                val maxOdMm = maxOf(
                    component.maxDiaMm(),
                    leftNeighbor?.maxDiaMm() ?: 0f,
                    rightNeighbor?.maxDiaMm() ?: 0f,
                ).coerceAtLeast(1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                        .clip(cardShape)
                        .background(Color.White)
                        .pointerInput(componentId, pits, brushSize, pitTool, lenMm, startDiaMm, endDiaMm) {
                            detectTapGestures { tap ->
                                val lay = computeSegDetailLayout(
                                    widthPx = size.width.toFloat(),
                                    stubRowHeightPx = stubRowHeightDp.toPx(),
                                    stubWidthPx = stubWidthDp.toPx(),
                                    lenMm = lenMm,
                                    maxOdMm = maxOdMm,
                                )
                                val smallHalfPx = PIT_SMALL_HALF_DP.dp.toPx()
                                when (pitTool) {
                                    PitTool.REMOVE -> {
                                        // Tap an X to delete it; a miss does nothing.
                                        val targets = pits.map { p ->
                                            val (cx, cy) = pitCenterPx(lay, startDiaMm, endDiaMm, lenMm, p)
                                            PitHitTarget(p.id, cx, cy, pitHalfArm(p.size, smallHalfPx))
                                        }
                                        pickPitAt(tap.x, tap.y, targets, padPx = 11.dp.toPx())
                                            ?.let { onRemovePit(it) }
                                    }
                                    PitTool.ADD -> {
                                        // Only taps landing on the segment (between its edges) count.
                                        if (tap.x < lay.startPx - 4f || tap.x > lay.endPx + 4f) return@detectTapGestures
                                        val localMm = ((tap.x - lay.startPx) / lay.pxPerMm).coerceIn(0f, lenMm)
                                        val r = radiusLocalPx(lay, startDiaMm, endDiaMm, lenMm, localMm)
                                        val frac = acrossFracFromTapY(tap.y, lay.cy - r, lay.cy + r)
                                        onAddPit(componentId, localMm, frac, brushSize)
                                    }
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outlineWidthPx = 1.5.dp.toPx()
                        val stubWidthPx = stubWidthDp.toPx()
                        val stubRowHeightPx = stubRowHeightDp.toPx()
                        val railRowHeightPx = railRowHeightDp.toPx()
                        val railTopGapPx = railTopGapDp.toPx()
                        val smallHalfPx = PIT_SMALL_HALF_DP.dp.toPx()

                        val lay = computeSegDetailLayout(
                            widthPx = size.width,
                            stubRowHeightPx = stubRowHeightPx,
                            stubWidthPx = stubWidthPx,
                            lenMm = lenMm,
                            maxOdMm = maxOdMm,
                        )
                        val startPx = lay.startPx
                        val endPx = lay.endPx
                        val cy = lay.cy
                        fun rPx(diaMm: Float) = diaMm * 0.5f * lay.pxPerMm

                        // ── Focus component body (trapezoid — a rect when start Ø == end Ø) ──
                        val rStart = rPx(startDiaMm)
                        val rEnd = rPx(endDiaMm)
                        val bodyPath = Path().apply {
                            moveTo(startPx, cy - rStart)
                            lineTo(endPx, cy - rEnd)
                            lineTo(endPx, cy + rEnd)
                            lineTo(startPx, cy + rStart)
                            close()
                        }
                        drawPath(bodyPath, color = fillColor)
                        drawPath(bodyPath, color = outlineColor, style = Stroke(width = outlineWidthPx))

                        // ── Neighbor stubs — real edge at the component, S-curve break at far end ──
                        if (leftNeighbor != null) {
                            val r = rPx(touchingDiaMm(leftNeighbor, neighborIsLeft = true))
                            val top = cy - r; val bot = cy + r
                            val outerX = startPx - stubWidthPx
                            drawLine(outlineColor, Offset(outerX, top), Offset(startPx, top), outlineWidthPx)
                            drawLine(outlineColor, Offset(outerX, bot), Offset(startPx, bot), outlineWidthPx)
                            drawLine(outlineColor, Offset(startPx, top), Offset(startPx, bot), outlineWidthPx)
                            drawBreakEdgeCompose(
                                x = outerX, yTop = top, yBot = bot, amplitude = r * 0.6f,
                                color = outlineColor, strokeWidthPx = outlineWidthPx, eyeAtTop = true,
                            )
                        }
                        if (rightNeighbor != null) {
                            val r = rPx(touchingDiaMm(rightNeighbor, neighborIsLeft = false))
                            val top = cy - r; val bot = cy + r
                            val outerX = endPx + stubWidthPx
                            drawLine(outlineColor, Offset(endPx, top), Offset(outerX, top), outlineWidthPx)
                            drawLine(outlineColor, Offset(endPx, bot), Offset(outerX, bot), outlineWidthPx)
                            drawLine(outlineColor, Offset(endPx, top), Offset(endPx, bot), outlineWidthPx)
                            drawBreakEdgeCompose(
                                x = outerX, yTop = top, yBot = bot, amplitude = r * 0.6f,
                                color = outlineColor, strokeWidthPx = outlineWidthPx, eyeAtTop = false,
                            )
                        }

                        // ── Liner wear bands + per-spot dimension rail row (liners only) ──
                        if (liner != null) {
                            spots.forEachIndexed { i, spot ->
                                val band = clampWearBandToLiner(spot.startMm, spot.lengthMm, lenMm)
                                val (bx0, bx1) = wearBandToPx(band, startPx, lay.pxPerMm)
                                if (!band.isEmpty) {
                                    drawWearBand(bx0, bx1, cy - rStart, cy + rStart, wearTintColor, wearHatchColor)
                                }
                                val railY = stubRowHeightPx + railTopGapPx + railRowHeightPx * i + railRowHeightPx * 0.5f
                                drawDimSegment(startPx, bx0, railY, dimLabel(spot.startMm, unit), textPaint)
                                drawDimSegment(bx0, bx1, railY, dimLabel(spot.lengthMm, unit), textPaint)
                            }
                        }

                        // ── Pit "X" markers (all component types) ──
                        pits.forEach { p ->
                            val (px, py) = pitCenterPx(lay, startDiaMm, endDiaMm, lenMm, p)
                            drawPitX(px, py, pitHalfArm(p.size, smallHalfPx), pitColor)
                        }
                    }
                }

                // ── Pit controls ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pits", style = MaterialTheme.typography.titleMedium)
                    if (pits.isNotEmpty()) {
                        Text(
                            "${pits.size} placed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Tool: Add X (place on tap) / Remove X (delete on tap). Explicit so a stray tap
                // can't place or delete by accident.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tool:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WearChip("Add X", pitTool == PitTool.ADD) { pitTool = PitTool.ADD }
                    WearChip("Remove X", pitTool == PitTool.REMOVE) { pitTool = PitTool.REMOVE }
                }

                // Size — only affects newly placed pits, so shown while adding.
                if (pitTool == PitTool.ADD) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Size:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WearChip("Small", brushSize == PitSize.SMALL) { brushSize = PitSize.SMALL }
                        WearChip("Large", brushSize == PitSize.LARGE) { brushSize = PitSize.LARGE }
                    }
                }

                Text(
                    text = when (pitTool) {
                        PitTool.ADD -> "Add mode — tap the segment to place an X."
                        PitTool.REMOVE -> "Remove mode — tap an X to delete it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (pits.isNotEmpty()) {
                    OutlinedButton(onClick = { pits.forEach { onRemovePit(it.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear all pits")
                    }
                }

                // ── Liner wear spots (bands) — liners only ───────────────────
                if (liner != null) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Wear spots", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { onAddSpot(componentId) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add spot")
                        }
                    }

                    if (spots.isEmpty()) {
                        Text(
                            text = "No wear spots recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    spots.forEachIndexed { i, spot ->
                        WearSpotCard(
                            index = i,
                            spot = spot,
                            unit = unit,
                            linerStartFromAftMm = liner.startMmPhysical,
                            linerLengthMm = lenMm,
                            aftSetXMm = aftSetXMm,
                            fwdSetXMm = fwdSetXMm,
                            onCommit = { s, l, d, n -> onUpdateSpot(spot.id, s, l, d, n) },
                            onUpdateReference = { ref -> onUpdateSpotReference(spot.id, ref) },
                            onDelete = { onRemoveSpot(spot.id) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus-component geometry (shared by the Canvas renderer and the tap handler)
// ─────────────────────────────────────────────────────────────────────────────

/** Which pit action a tap performs on the detail canvas (explicit, to avoid accidental edits). */
private enum class PitTool { ADD, REMOVE }

/** True for the pit-eligible subtypes (bodies, tapers, liners); used to gate the overlay. */
private val ResolvedComponent.isPitEligible: Boolean
    get() = this is ResolvedBody || this is ResolvedTaper || this is ResolvedLiner

private data class SegDetailLayout(
    val pxPerMm: Float,
    val startPx: Float,
    val endPx: Float,
    val cy: Float,
)

/**
 * On-screen layout of the broken-out focus component. Pure function of the canvas size + the
 * component's length/OD, so the Canvas renderer and the tap handler compute IDENTICAL geometry
 * (a tapped X removes the same X that was drawn). Mirrors the original liner-detail math.
 */
private fun computeSegDetailLayout(
    widthPx: Float,
    stubRowHeightPx: Float,
    stubWidthPx: Float,
    lenMm: Float,
    maxOdMm: Float,
): SegDetailLayout {
    val usableWidthPx = (widthPx - 2f * stubWidthPx).coerceAtLeast(1f)
    val pxPerMm = computeLinerDetailPxPerMm(
        usableWidthPx = usableWidthPx,
        linerLengthMm = lenMm,
        maxOdMm = maxOdMm,
        usableHeightPx = stubRowHeightPx,
        heightFillFraction = 0.72f,
    )
    val assemblyWidthPx = 2f * stubWidthPx + lenMm * pxPerMm
    val leftMarginPx = ((widthPx - assemblyWidthPx) / 2f).coerceAtLeast(0f)
    val startPx = leftMarginPx + stubWidthPx
    return SegDetailLayout(pxPerMm, startPx, startPx + lenMm * pxPerMm, stubRowHeightPx / 2f)
}

/** The focus component's diameters at its AFT and FWD edges (equal for a body/liner). */
private fun componentEdgeDias(rc: ResolvedComponent): Pair<Float, Float> = when (rc) {
    is ResolvedBody -> rc.diaMm to rc.diaMm
    is ResolvedLiner -> rc.odMm to rc.odMm
    is ResolvedTaper -> rc.startDiaMm to rc.endDiaMm
    else -> 1f to 1f
}

/** Half-height (radius, px) of the focus component at component-local [localMm], interpolating a taper. */
private fun radiusLocalPx(
    lay: SegDetailLayout,
    startDiaMm: Float,
    endDiaMm: Float,
    lenMm: Float,
    localMm: Float,
): Float {
    val t = if (lenMm > 0f) (localMm / lenMm).coerceIn(0f, 1f) else 0f
    val dia = startDiaMm + (endDiaMm - startDiaMm) * t
    return dia * 0.5f * lay.pxPerMm
}

/** The drawn centre (px) of a pit's "X" on the focus component. */
private fun pitCenterPx(
    lay: SegDetailLayout,
    startDiaMm: Float,
    endDiaMm: Float,
    lenMm: Float,
    pit: WearPit,
): Pair<Float, Float> {
    val localMm = pit.axialMm.coerceIn(0f, lenMm)
    val cx = lay.startPx + localMm * lay.pxPerMm
    val r = radiusLocalPx(lay, startDiaMm, endDiaMm, lenMm, localMm)
    val cy = pitCenterY(lay.cy - r, lay.cy + r, pit.acrossFrac)
    return cx to cy
}

/** Base half-arm (dp) of a SMALL pit "X" on the detail canvas; LARGE scales by the shared ratio. */
private const val PIT_SMALL_HALF_DP = 4.5f

private fun componentWearTitle(spec: ShaftSpec, rc: ResolvedComponent): String = when (rc) {
    is ResolvedLiner -> buildLinerTitleById(spec)[rc.id] ?: "Liner"
    is ResolvedTaper -> buildTaperTitleById(spec)[rc.id] ?: "Taper"
    is ResolvedBody -> buildBodyTitleById(spec)[rc.id] ?: "Body"
    else -> "Component"
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot card (liner wear bands — unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WearSpotCard(
    index: Int,
    spot: WearSpot,
    unit: UnitSystem,
    linerStartFromAftMm: Float,
    linerLengthMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onCommit: (startMm: Float, lengthMm: Float, minDiaMm: Float, note: String) -> Unit,
    onUpdateReference: (WearSpotReference) -> Unit,
    onDelete: () -> Unit,
) {
    val reference = spot.authoredReference

    // Stale-overrun classifier (Change 2, 2026-07-18 post-review spec): a spot recorded when
    // the liner was longer can become out-of-span after the liner is shortened. This never
    // blocks — the canvas above already renders the safety-net clamp (`clampWearBandToLiner`)
    // — it only surfaces a warning so the machinist knows to re-measure.
    val staleOverrun = isWearSpotStaleOverrun(spot.startMm, spot.lengthMm, linerLengthMm)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Spot ${index + 1}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete spot ${index + 1}")
                }
            }

            if (staleOverrun) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        "Extends past liner end — re-measure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // "Measure from" — Start's authoring reference (Change 1, 2026-07-18 post-review
            // spec): AFT SET / FWD SET / Liner AFT / Liner FWD. Tapping a chip persists the
            // reference immediately (`onUpdateReference`) and re-projects the DISPLAYED Start
            // value only — canonical `spot.startMm` is untouched.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Measure From:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearChip("AFT SET", reference == WearSpotReference.AFT_SET) {
                        onUpdateReference(WearSpotReference.AFT_SET)
                    }
                    WearChip("FWD SET", reference == WearSpotReference.FWD_SET) {
                        onUpdateReference(WearSpotReference.FWD_SET)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearChip("Liner AFT", reference == WearSpotReference.LINER_AFT) {
                        onUpdateReference(WearSpotReference.LINER_AFT)
                    }
                    WearChip("Liner FWD", reference == WearSpotReference.LINER_FWD) {
                        onUpdateReference(WearSpotReference.LINER_FWD)
                    }
                }
            }

            val displayedStartMm = canonicalToWearStartMm(
                reference = reference,
                canonicalStartMm = spot.startMm,
                lengthMm = spot.lengthMm,
                linerStartFromAftMm = linerStartFromAftMm,
                linerLengthMm = linerLengthMm,
                aftSetXMm = aftSetXMm,
                fwdSetXMm = fwdSetXMm,
            )
            WearNum(
                label = "Start from ${wearReferenceLabel(reference)} (${abbr(unit)})",
                initialDisplay = disp(displayedStartMm, unit),
                validator = { raw ->
                    val enteredMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    val canonicalMm = wearStartToCanonicalMm(
                        reference, enteredMm, spot.lengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
                    )
                    wearSpotSpanIssue(canonicalMm, spot.lengthMm, linerLengthMm)
                },
            ) { s ->
                val enteredMm = toMmOrNull(s, unit) ?: return@WearNum
                val canonicalMm = wearStartToCanonicalMm(
                    reference, enteredMm, spot.lengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
                )
                onCommit(canonicalMm, spot.lengthMm, spot.minDiaMm, spot.note)
            }

            WearNum(
                label = "Length (${abbr(unit)})",
                initialDisplay = disp(spot.lengthMm, unit),
                validator = { raw ->
                    val enteredLenMm = toMmOrNull(raw, unit) ?: return@WearNum "Invalid number"
                    wearSpotSpanIssue(spot.startMm, enteredLenMm, linerLengthMm)
                },
            ) { s -> toMmOrNull(s, unit)?.let { onCommit(spot.startMm, it, spot.minDiaMm, spot.note) } }

            WearNum(
                label = "Min diameter measured (${abbr(unit)})",
                initialDisplay = disp(spot.minDiaMm, unit),
            ) { s -> toMmOrNull(s, unit)?.let { onCommit(spot.startMm, spot.lengthMm, it, spot.note) } }

            // Notes — plain text, same tap-and-leave no-op discipline as the numeric fields
            // above (NumberField.md): capture on focus, commit on blur only if changed.
            var noteDraft by remember(spot.id, spot.note) { mutableStateOf(spot.note) }
            var noteFocusText by remember(spot.id, spot.note) { mutableStateOf<String?>(null) }
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
                        } else {
                            val captured = noteFocusText
                            noteFocusText = null
                            if (captured != null && noteDraft != captured) {
                                onCommit(spot.startMm, spot.lengthMm, spot.minDiaMm, noteDraft)
                            }
                        }
                    },
            )
        }
    }
}

/** Chip labels for [WearSpot.authoredReference], used in the Start field's dynamic label. */
private fun wearReferenceLabel(reference: WearSpotReference): String = when (reference) {
    WearSpotReference.LINER_AFT -> "liner AFT edge"
    WearSpotReference.LINER_FWD -> "liner FWD edge"
    WearSpotReference.AFT_SET -> "AFT SET"
    WearSpotReference.FWD_SET -> "FWD SET"
}

/** One selection chip — same [FilterChip]/border convention as `ComponentCarousel.kt`. */
@Composable
private fun WearChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
            containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = if (selected) BorderStroke(1.dp, Color.Black) else null,
    )
}

/** Thin wrapper around [NumericInputField] mirroring `ComponentCarousel`'s private `CommitNum`. */
@Composable
private fun WearNum(
    label: String,
    initialDisplay: String,
    validator: ((String) -> String?)? = null,
    onCommit: (String) -> Unit,
) {
    NumericInputField(
        label = label,
        initialText = initialDisplay,
        modifier = Modifier.fillMaxWidth(),
        allowNegative = false,
        allowFraction = true,
        parseValid = { parseFractionOrDecimal(it) != null },
        validator = validator,
        onCommit = onCommit,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

/** The diameter of [rc] at the edge that touches the focus component. */
private fun touchingDiaMm(rc: ResolvedComponent, neighborIsLeft: Boolean): Float = when (rc) {
    is ResolvedBody -> rc.diaMm
    is ResolvedTaper -> if (neighborIsLeft) rc.endDiaMm else rc.startDiaMm
    is ResolvedThread -> rc.majorDiaMm
    is ResolvedLiner -> rc.odMm
    is ResolvedCouplerBoltSlot -> 0f
}

private fun dimLabel(mm: Float, unit: UnitSystem): String = disp(mm, unit) + abbr(unit)

/**
 * A pit "X" — two crossed strokes centred on `(cx, cy)`, half-arm [halfArm]. Drawn identically
 * (by construction) to the PDF's `drawWearPitX` (`WearPdfComposer.kt`); see `geom/WearPitMath.kt`.
 */
private fun DrawScope.drawPitX(cx: Float, cy: Float, halfArm: Float, color: Color) {
    val w = (halfArm * 0.30f).coerceAtLeast(2f)
    drawLine(color, Offset(cx - halfArm, cy - halfArm), Offset(cx + halfArm, cy + halfArm), w, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - halfArm, cy + halfArm), Offset(cx + halfArm, cy - halfArm), w, cap = StrokeCap.Round)
}

/** Hatched/tinted wear band, reusing the diagonal-hatch approach from `ShaftRenderer`'s thread hatch. */
private fun DrawScope.drawWearBand(x0: Float, x1: Float, top: Float, bottom: Float, tint: Color, hatch: Color) {
    if (x1 <= x0 || bottom <= top) return
    drawRect(color = tint, topLeft = Offset(x0, top), size = Size(x1 - x0, bottom - top))
    val h = bottom - top
    withTransform({ clipRect(x0, top, x1, bottom) }) {
        var hx = x0 - h
        while (hx <= x1 + h) {
            drawLine(hatch, Offset(hx, bottom), Offset(hx + h, top), strokeWidth = 1.4f)
            hx += 10f
        }
    }
    drawRect(color = hatch, topLeft = Offset(x0, top), size = Size(x1 - x0, bottom - top), style = Stroke(width = 1.2f))
}

/** One labeled dimension segment (witness ticks + centered value) on the rail below the liner. */
private fun DrawScope.drawDimSegment(x0: Float, x1: Float, y: Float, label: String, paint: android.graphics.Paint) {
    val lo = kotlin.math.min(x0, x1)
    val hi = kotlin.math.max(x0, x1)
    if (hi - lo < 1f) return
    val tickH = 3.dp.toPx()
    val lineColor = Color.Black.copy(alpha = 0.55f)
    drawLine(lineColor, Offset(lo, y), Offset(hi, y), strokeWidth = 1f)
    drawLine(lineColor, Offset(lo, y - tickH), Offset(lo, y + tickH), strokeWidth = 1f)
    drawLine(lineColor, Offset(hi, y - tickH), Offset(hi, y + tickH), strokeWidth = 1f)
    drawContext.canvas.nativeCanvas.drawText(label, (lo + hi) / 2f, y - 6f, paint)
}

/**
 * Compose port of the pdf-layer `drawBreakEdge` S-curve convention (`pdf/BreakSymbol.kt`) — same
 * math, redrawn with Compose [Path]/[DrawScope]. [eyeAtTop] must be chosen so the eye's larger
 * "sweep" curve bulges into the **void** side of the break: left (AFT) stub = `true`, right (FWD)
 * stub = `false` (see the original derivation in the 2026-07-18 KDoc history).
 */
private fun DrawScope.drawBreakEdgeCompose(
    x: Float,
    yTop: Float,
    yBot: Float,
    amplitude: Float,
    color: Color,
    strokeWidthPx: Float,
    eyeAtTop: Boolean,
) {
    val h = yBot - yTop
    if (h <= 0f) return
    val cy = yTop + h / 2f
    val k = 1.5f // mirrors BreakSymbol.RETURN_SWEEP_FULLNESS

    val sweep = Path().apply {
        if (eyeAtTop) {
            moveTo(x, yTop)
            cubicTo(x - k * amplitude / 2f, yTop + h / 6f, x - k * amplitude / 4f, yTop + h / 3f, x, cy)
        } else {
            moveTo(x, yBot)
            cubicTo(x + k * amplitude / 2f, yBot - h / 6f, x + k * amplitude / 4f, yBot - h / 3f, x, cy)
        }
    }
    val eye = Path().apply {
        if (eyeAtTop) {
            moveTo(x, yTop)
            cubicTo(x - k * amplitude / 2f, yTop + h / 6f, x - k * amplitude / 4f, yTop + h / 3f, x, cy)
            cubicTo(x + amplitude / 4f, yTop + h / 3f, x + amplitude / 2f, yTop + h / 6f, x, yTop)
        } else {
            moveTo(x, yBot)
            cubicTo(x + k * amplitude / 2f, yBot - h / 6f, x + k * amplitude / 4f, yBot - h / 3f, x, cy)
            cubicTo(x - amplitude / 4f, yBot - h / 3f, x - amplitude / 2f, yBot - h / 6f, x, yBot)
        }
        close()
    }
    drawPath(eye, color = Color.Black.copy(alpha = 0.10f))

    drawPath(
        path = Path().apply {
            moveTo(x, yTop)
            cubicTo(x + amplitude, yTop + h / 3f, x - amplitude, yBot - h / 3f, x, yBot)
        },
        color = color,
        style = Stroke(width = strokeWidthPx),
    )
    drawPath(sweep, color = color, style = Stroke(width = strokeWidthPx))
}
