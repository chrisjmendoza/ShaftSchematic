// file: app/src/main/java/com/android/shaftschematic/ui/screen/LinerWearDetail.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.ShaftSpec
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
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.util.UnitSystem

/**
 * LinerWearDetail
 *
 * Phase 3 of `docs/LinerWearAreas_Proposal.md`: a full-screen "zoom in" overlay on one liner,
 * broken out of the shaft (S-curve break edges on short neighbor stubs, shop-sketch convention —
 * see `pdf/BreakSymbol.kt` for the PDF-layer original; this file replicates the visual in Compose
 * without importing pdf code, per the proposal). Wear spots render as hatched bands at their true
 * position with a small dimension rail below, plus an editable card per spot.
 *
 * Same pattern as `PdfPreviewOverlay` (`RunoutRoute.kt`): a plain composable, not a nav
 * destination, dismissed via [BackHandler] or the back-arrow button — the caller composes this
 * conditionally (`if (selectedLinerId != null) LinerWearDetailOverlay(...)`).
 *
 * Layout math is self-contained here — it draws ONE liner + short neighbor stubs, not the whole
 * shaft, so it does not use `ShaftLayout`/`ShaftRenderer` (proposal §6.1). Neighbor geometry still
 * comes from the resolved component list (never raw `spec.bodies/tapers/...`), so a body split by
 * an adjacent liner shows its real subtracted-segment diameter, not a stale spec value.
 *
 * Coordinate rule: [WearSpot.startMm] is liner-local (from the liner's AFT edge). Shaft-space
 * conversion (`liner.startFromAftMm + spot.startMm`) is never needed here since everything drawn
 * in this overlay is already in liner-local space; it only matters to callers that place spots on
 * the *whole-shaft* profile (none yet — that is PDF Phase 4).
 */
@Composable
fun LinerWearDetailOverlay(
    linerId: String,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    wearRecord: WearRecord,
    onAddSpot: (linerId: String) -> Unit,
    onUpdateSpot: (id: String, startMm: Float, lengthMm: Float, minDiaMm: Float, note: String) -> Unit,
    onUpdateSpotReference: (id: String, reference: WearSpotReference) -> Unit,
    onRemoveSpot: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }

    val liner = resolvedComponents.filterIsInstance<ResolvedLiner>().firstOrNull { it.id == linerId }
    if (liner == null) {
        // Liner no longer resolves (e.g. deleted from the Shaft tab while this overlay was open,
        // or an autosave restore raced the tap). Nothing sane to draw — bounce back.
        LaunchedEffect(linerId) { onClose() }
        return
    }

    val linerTitle = remember(spec, linerId) { buildLinerTitleById(spec)[linerId] ?: "Liner" }
    val spots = remember(wearRecord, linerId) {
        wearRecord.spots.filter { it.linerId == linerId }.sortedBy { it.startMm }
    }
    val linerLenMm = remember(liner) { (liner.endMmPhysical - liner.startMmPhysical).coerceAtLeast(0.001f) }

    // SET positions (AFT/FWD SET, "Measure from" reference options — Change 1, 2026-07-18
    // post-review spec). `computeOalWindow`'s measureStartMm is always 0.0, so the returned
    // measure-space X values already are physical shaft-space mm from AFT — the same space
    // as `liner.startMmPhysical` — see `geom/OalComputations.kt`.
    val setPositions = remember(spec) {
        val win = computeOalWindow(spec)
        computeSetPositionsInMeasureSpace(win, spec)
    }
    val aftSetXMm = setPositions.aftSETxMm.toFloat()
    val fwdSetXMm = setPositions.fwdSETxMm.toFloat()

    // Nearest non-overlay neighbor on each side, from the RESOLVED list (proposal §7.5) —
    // coupler bolt slots are overlays and never real geometry neighbors (CLAUDE.md).
    val eps = 1e-3f
    val leftNeighbor = remember(resolvedComponents, liner) {
        resolvedComponents
            .filter { it.id != liner.id && it !is ResolvedCouplerBoltSlot && it.endMmPhysical <= liner.startMmPhysical + eps }
            .maxByOrNull { it.endMmPhysical }
    }
    val rightNeighbor = remember(resolvedComponents, liner) {
        resolvedComponents
            .filter { it.id != liner.id && it !is ResolvedCouplerBoltSlot && it.startMmPhysical >= liner.endMmPhysical - eps }
            .minByOrNull { it.startMmPhysical }
    }

    // ── Theme colors captured here — the Canvas draw scope below must not read MaterialTheme ──
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val linerFillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val wearTintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
    val wearHatchColor = MaterialTheme.colorScheme.error.copy(alpha = 0.80f)
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
                    text = "$linerTitle — wear inspection",
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
                // ── Broken-out liner canvas ──────────────────────────────────
                val stubWidthDp = 24.dp
                val stubRowHeightDp = 140.dp
                val railRowHeightDp = 32.dp
                val railTopGapDp = 12.dp
                val canvasHeightDp = stubRowHeightDp +
                    if (spots.isEmpty()) 0.dp else (railTopGapDp + railRowHeightDp * spots.size)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                        .clip(cardShape)
                        .background(Color.White),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outlineWidthPx = 1.5.dp.toPx()
                        val stubWidthPx = stubWidthDp.toPx()
                        val stubRowHeightPx = stubRowHeightDp.toPx()
                        val railRowHeightPx = railRowHeightDp.toPx()
                        val railTopGapPx = railTopGapDp.toPx()

                        val maxOdMm = maxOf(
                            liner.maxDiaMm(),
                            leftNeighbor?.maxDiaMm() ?: 0f,
                            rightNeighbor?.maxDiaMm() ?: 0f,
                        ).coerceAtLeast(1f)
                        val usableWidthPx = (size.width - 2f * stubWidthPx).coerceAtLeast(1f)
                        val pxPerMm = computeLinerDetailPxPerMm(
                            usableWidthPx = usableWidthPx,
                            linerLengthMm = linerLenMm,
                            maxOdMm = maxOdMm,
                            usableHeightPx = stubRowHeightPx,
                            heightFillFraction = 0.72f,
                        )

                        // Center the whole stub+liner+stub assembly when it doesn't fill the width
                        // (a short, height-capped liner leaves room on both sides).
                        val assemblyWidthPx = 2f * stubWidthPx + linerLenMm * pxPerMm
                        val leftMarginPx = ((size.width - assemblyWidthPx) / 2f).coerceAtLeast(0f)
                        val linerStartPx = leftMarginPx + stubWidthPx
                        val linerEndPx = linerStartPx + linerLenMm * pxPerMm
                        val cy = stubRowHeightPx / 2f

                        fun rPx(diaMm: Float) = diaMm * 0.5f * pxPerMm

                        // ── Liner body ──
                        val linerR = rPx(liner.odMm)
                        val linerTop = cy - linerR
                        val linerBot = cy + linerR
                        drawRect(
                            color = linerFillColor,
                            topLeft = Offset(linerStartPx, linerTop),
                            size = Size(linerEndPx - linerStartPx, linerBot - linerTop),
                        )
                        drawRect(
                            color = outlineColor,
                            topLeft = Offset(linerStartPx, linerTop),
                            size = Size(linerEndPx - linerStartPx, linerBot - linerTop),
                            style = Stroke(width = outlineWidthPx),
                        )

                        // ── Neighbor stubs — real edge at the liner, S-curve break at the far end ──
                        if (leftNeighbor != null) {
                            val r = rPx(touchingDiaMm(leftNeighbor, neighborIsLeft = true))
                            val top = cy - r; val bot = cy + r
                            val outerX = linerStartPx - stubWidthPx
                            drawLine(outlineColor, Offset(outerX, top), Offset(linerStartPx, top), outlineWidthPx)
                            drawLine(outlineColor, Offset(outerX, bot), Offset(linerStartPx, bot), outlineWidthPx)
                            drawLine(outlineColor, Offset(linerStartPx, top), Offset(linerStartPx, bot), outlineWidthPx)
                            drawBreakEdgeCompose(
                                x = outerX, yTop = top, yBot = bot, amplitude = r * 0.6f,
                                color = outlineColor, strokeWidthPx = outlineWidthPx, eyeAtTop = true,
                            )
                        }
                        if (rightNeighbor != null) {
                            val r = rPx(touchingDiaMm(rightNeighbor, neighborIsLeft = false))
                            val top = cy - r; val bot = cy + r
                            val outerX = linerEndPx + stubWidthPx
                            drawLine(outlineColor, Offset(linerEndPx, top), Offset(outerX, top), outlineWidthPx)
                            drawLine(outlineColor, Offset(linerEndPx, bot), Offset(outerX, bot), outlineWidthPx)
                            drawLine(outlineColor, Offset(linerEndPx, top), Offset(linerEndPx, bot), outlineWidthPx)
                            drawBreakEdgeCompose(
                                x = outerX, yTop = top, yBot = bot, amplitude = r * 0.6f,
                                color = outlineColor, strokeWidthPx = outlineWidthPx, eyeAtTop = false,
                            )
                        }

                        // ── Wear bands + per-spot dimension rail row ──
                        spots.forEachIndexed { i, spot ->
                            val band = clampWearBandToLiner(spot.startMm, spot.lengthMm, linerLenMm)
                            val (bx0, bx1) = wearBandToPx(band, linerStartPx, pxPerMm)
                            if (!band.isEmpty) {
                                drawWearBand(bx0, bx1, linerTop, linerBot, wearTintColor, wearHatchColor)
                            }
                            val railY = stubRowHeightPx + railTopGapPx + railRowHeightPx * i + railRowHeightPx * 0.5f
                            drawDimSegment(linerStartPx, bx0, railY, dimLabel(spot.startMm, unit), textPaint)
                            drawDimSegment(bx0, bx1, railY, dimLabel(spot.lengthMm, unit), textPaint)
                        }
                    }
                }

                if (spots.isEmpty()) {
                    Text(
                        text = "No wear spots recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Wear spots header + add button ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Wear spots", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onAddSpot(linerId) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add spot")
                    }
                }

                spots.forEachIndexed { i, spot ->
                    WearSpotCard(
                        index = i,
                        spot = spot,
                        unit = unit,
                        linerStartFromAftMm = liner.startMmPhysical,
                        linerLengthMm = linerLenMm,
                        aftSetXMm = aftSetXMm,
                        fwdSetXMm = fwdSetXMm,
                        onCommit = { s, l, d, n -> onUpdateSpot(spot.id, s, l, d, n) },
                        onUpdateReference = { ref -> onUpdateSpotReference(spot.id, ref) },
                        onDelete = { onRemoveSpot(spot.id) },
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot card
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
            // reference immediately (`onUpdateReference`, mirroring
            // `updateLinerAuthoredReference`/`updateCouplerBoltSlotReference`) and re-projects
            // the DISPLAYED Start value only — canonical `spot.startMm` is untouched, same rule
            // as the Liner/CouplerBoltSlot AFT/FWD chips.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Measure From:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearReferenceChip("AFT SET", reference == WearSpotReference.AFT_SET) {
                        onUpdateReference(WearSpotReference.AFT_SET)
                    }
                    WearReferenceChip("FWD SET", reference == WearSpotReference.FWD_SET) {
                        onUpdateReference(WearSpotReference.FWD_SET)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WearReferenceChip("Liner AFT", reference == WearSpotReference.LINER_AFT) {
                        onUpdateReference(WearSpotReference.LINER_AFT)
                    }
                    WearReferenceChip("Liner FWD", reference == WearSpotReference.LINER_FWD) {
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

/** One "Measure From" chip — same [FilterChip]/border convention as `ComponentCarousel.kt`. */
@Composable
private fun WearReferenceChip(
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

/** The diameter of [rc] at the edge that touches the liner (its own end if it sits to the left, its own start if it sits to the right). */
private fun touchingDiaMm(rc: ResolvedComponent, neighborIsLeft: Boolean): Float = when (rc) {
    is ResolvedBody -> rc.diaMm
    is ResolvedTaper -> if (neighborIsLeft) rc.endDiaMm else rc.startDiaMm
    is ResolvedThread -> rc.majorDiaMm
    is ResolvedLiner -> rc.odMm
    is ResolvedCouplerBoltSlot -> 0f
}

private fun dimLabel(mm: Float, unit: UnitSystem): String = disp(mm, unit) + abbr(unit)

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
 * math, redrawn with Compose [Path]/[DrawScope] instead of `android.graphics.Canvas`/`Paint`
 * directly, per the instruction not to import pdf code from the UI layer.
 *
 * [eyeAtTop] must be chosen so the eye's larger "sweep" curve bulges into the **void** side of
 * the break, never the material side (2026-07-18 device review: an inward-facing eye visibly
 * overlapped the S-curve nearer the liner). This is the opposite of the flag choice used for a
 * *centered compression* break (`ShaftPdfComposer`/`WearPdfComposer`'s body-shortening breaks),
 * where the two break edges face a shared gap in the middle — there, left edge = false, right
 * edge = true. Here each stub has a single break at its own **far/outer** end (away from the
 * liner) with the void beyond it and material toward the liner, so the mapping inverts: the
 * left (AFT) stub's break has void to its left → `eyeAtTop = true`; the right (FWD) stub's
 * break has void to its right → `eyeAtTop = false`.
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
    // Light translucent wash inside the eye, same recipe as the PDF (~ black 18%, here softened
    // to keep contrast reasonable on the white detail canvas).
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
