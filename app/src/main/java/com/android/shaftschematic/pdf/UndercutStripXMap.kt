package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.util.UnitSystem

/**
 * One undercut strip's axial map: strip mm → drawing x, and back.
 *
 * A strip is drawn to scale ([linear]) until its dimension values start stacking on each other
 * ([undercutRailsCrowded]) — a run of short cuts at the end of a long section, each a sliver at
 * the section's scale, with its value pushed off its line onto a neighbour's. Such a strip is
 * drawn through a PIECEWISE-linear map instead ([fitUndercutStripXMap]): every short dimensioned
 * stretch is given the width its own value needs to seat in its line, and the long stretches
 * give that width up in proportion to their length. The numbers on the rail are the stored
 * lengths either way; only the drawn proportions move, the way a hand sketch draws a 9/16" cut
 * wide enough to write in.
 *
 * ONE map per strip, built by the ONE planner [planUndercutStripXMap], behind both draw sites
 * (the printed strip and the detail canvas) and everything on them: profile, notches, liner
 * outline, rails, Ø leaders, cut numbers and the canvas tap. Radii never go through it.
 */
class UndercutStripXMap private constructor(
    private val knotsMm: FloatArray,
    private val knotsX: FloatArray,
    /** The strip's to-scale x per mm — the whole map when [isLinear], and the scale outside it. */
    val linearScale: Float,
    val isLinear: Boolean,
) {
    val startMm: Float get() = knotsMm.first()
    val endMm: Float get() = knotsMm.last()
    val startX: Float get() = knotsX.first()
    val endX: Float get() = knotsX.last()

    /** Drawing x of strip station [mm]. Past either end the map continues at [linearScale]. */
    fun xAt(mm: Float): Float {
        if (isLinear || mm <= startMm) return startX + (mm - startMm) * linearScale
        if (mm >= endMm) return endX + (mm - endMm) * linearScale
        var i = 0
        while (i < knotsMm.size - 2 && mm > knotsMm[i + 1]) i++
        val len = knotsMm[i + 1] - knotsMm[i]
        if (len <= 0f) return knotsX[i]
        return knotsX[i] + (mm - knotsMm[i]) / len * (knotsX[i + 1] - knotsX[i])
    }

    /** The strip station drawn at [x] — [xAt]'s inverse. */
    fun mmAt(x: Float): Float {
        if (linearScale <= 0f) return startMm
        if (isLinear || x <= startX) return startMm + (x - startX) / linearScale
        if (x >= endX) return endMm + (x - endX) / linearScale
        var i = 0
        while (i < knotsX.size - 2 && x > knotsX[i + 1]) i++
        val w = knotsX[i + 1] - knotsX[i]
        if (w <= 0f) return knotsMm[i]
        return knotsMm[i] + (x - knotsX[i]) / w * (knotsMm[i + 1] - knotsMm[i])
    }

    /**
     * The stations strictly inside `(aMm, bMm)` where the map changes scale, AFT → FWD. A sloped
     * surface (a taper) crossing one is drawn as a polyline through them, so it meets a cut's face
     * at the face's own height. Empty on a linear map.
     */
    fun knotsInside(aMm: Float, bMm: Float): List<Float> =
        if (isLinear) emptyList() else knotsMm.filter { it > aMm + KNOT_EPS_MM && it < bMm - KNOT_EPS_MM }

    companion object {
        internal const val KNOT_EPS_MM = 1e-3f

        /** The to-scale map: `startX + (mm − startMm) × scale`. */
        fun linear(startMm: Float, endMm: Float, startX: Float, scale: Float): UndercutStripXMap =
            UndercutStripXMap(
                floatArrayOf(startMm, endMm),
                floatArrayOf(startX, startX + (endMm - startMm) * scale),
                scale, isLinear = true,
            )

        internal fun piecewise(knotsMm: FloatArray, knotsX: FloatArray, linearScale: Float): UndercutStripXMap =
            UndercutStripXMap(knotsMm, knotsX, linearScale, isLinear = false)
    }
}

/** A dimensioned stretch `[startMm, endMm]` that must draw at least [minWidth] wide. */
internal data class UndercutXFloor(val startMm: Float, val endMm: Float, val minWidth: Float)

/**
 * Whether a strip's planned rails are CROWDED: on some rail line a value that could not seat in
 * its own line also could not take the first row beside it, because a neighbour's value is
 * already there. One short cut on a long liner is not crowded — its value sits over it and reads
 * as its own; a run of them is.
 */
fun undercutRailsCrowded(rails: UndercutStripRails): Boolean =
    (listOf(rails.chainWithLead) + rails.nested).any { line -> line.any { !it.seatsInBreak && it.labelRow > 0 } }

/**
 * The piecewise map over `[drawStartMm, drawEndMm]`, starting at [startX] and exactly as wide as
 * the to-scale drawing (`length × linearScale`) — the strip keeps its place and its ends.
 *
 * - The stock outside the chain `[chainStartMm, chainEndMm]` keeps its to-scale width: the S-break
 *   room and the S.E.T. lead's room were both sized for it.
 * - Inside the chain each of [floors] draws at least its `minWidth`. The stretches that fall short
 *   to scale all take ONE width — the widest of their needs — so a longer short cut never draws
 *   narrower than a shorter one because its value happens to have fewer digits. Nested stretches
 *   are floored first, innermost out, so a parent is at least as wide as what it holds.
 * - Everything else shares the remaining width in proportion to its true length, so the long
 *   sections keep their relative lengths.
 * - Floors that cannot all fit are scaled down together; the values that then do not seat fall
 *   back above their line exactly as on a to-scale strip.
 *
 * Returns the linear map when no floor binds.
 */
internal fun fitUndercutStripXMap(
    drawStartMm: Float,
    drawEndMm: Float,
    startX: Float,
    linearScale: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    floors: List<UndercutXFloor>,
): UndercutStripXMap {
    val linear = UndercutStripXMap.linear(drawStartMm, drawEndMm, startX, linearScale)
    val eps = UndercutStripXMap.KNOT_EPS_MM
    if (drawEndMm - drawStartMm <= eps || linearScale <= 0f || floors.isEmpty()) return linear

    val knots = (floors.flatMap { listOf(it.startMm, it.endMm) } + listOf(drawStartMm, drawEndMm, chainStartMm, chainEndMm))
        .map { it.coerceIn(drawStartMm, drawEndMm) }
        .sorted()
        .fold(mutableListOf<Float>()) { acc, k -> if (acc.isEmpty() || k - acc.last() > eps) acc += k; acc }
    // The fold keeps the first of a near-duplicate pair, so pin the last knot to the draw end.
    knots[knots.lastIndex] = drawEndMm
    val n = knots.size - 1
    if (n < 2) return linear

    val len = FloatArray(n) { knots[it + 1] - knots[it] }
    val lin = FloatArray(n) { len[it] * linearScale }
    val isPad = BooleanArray(n) { i ->
        val mid = (knots[i] + knots[i + 1]) * 0.5f
        mid < chainStartMm || mid > chainEndMm
    }
    fun atomsOf(f: UndercutXFloor): List<Int> = (0 until n).filter { i ->
        val mid = (knots[i] + knots[i + 1]) * 0.5f
        !isPad[i] && mid > f.startMm && mid < f.endMm
    }
    fun linearWidth(f: UndercutXFloor): Float = atomsOf(f).sumOf { lin[it].toDouble() }.toFloat()

    val short = floors.filter { linearWidth(it) < it.minWidth }
    if (short.isEmpty()) return linear
    val shortWidth = short.maxOf { it.minWidth }
    val shortSet = short.toSet()

    val floor = FloatArray(n)
    floors.sortedBy { it.endMm - it.startMm }.forEach { f ->
        val atoms = atomsOf(f)
        val span = atoms.sumOf { len[it].toDouble() }.toFloat()
        if (atoms.isEmpty() || span <= 0f) return@forEach
        val need = if (f in shortSet) shortWidth else f.minWidth
        val have = atoms.sumOf { floor[it].toDouble() }.toFloat()
        if (have < need) atoms.forEach { floor[it] += (need - have) * len[it] / span }
    }

    val chain = (0 until n).filter { !isPad[it] }
    val chainWidth = chain.sumOf { lin[it].toDouble() }.toFloat()
    val floorSum = chain.sumOf { floor[it].toDouble() }.toFloat()
    if (chainWidth <= 0f) return linear
    if (floorSum > chainWidth) chain.forEach { floor[it] *= chainWidth / floorSum }

    // Water-fill: pin every stretch its floor binds, share the rest by true length.
    val pinned = BooleanArray(n)
    var scale = linearScale
    while (true) {
        val pinnedWidth = chain.filter { pinned[it] }.sumOf { floor[it].toDouble() }.toFloat()
        val freeLen = chain.filter { !pinned[it] }.sumOf { len[it].toDouble() }.toFloat()
        if (freeLen <= 0f) break
        scale = ((chainWidth - pinnedWidth) / freeLen).coerceAtLeast(0f)
        val newly = chain.filter { !pinned[it] && len[it] * scale < floor[it] }
        if (newly.isEmpty()) break
        newly.forEach { pinned[it] = true }
    }
    val width = FloatArray(n) { i ->
        when {
            isPad[i] -> lin[i]
            pinned[i] -> floor[i]
            else -> len[i] * scale
        }
    }
    // Every stretch pinned: spread any rounding remainder so the strip keeps its exact width.
    val drawn = chain.sumOf { width[it].toDouble() }.toFloat()
    if (drawn > 0f) chain.forEach { width[it] *= chainWidth / drawn }

    val xs = FloatArray(n + 1)
    xs[0] = startX
    for (i in 0 until n) xs[i + 1] = xs[i] + width[i]
    return UndercutStripXMap.piecewise(knots.toFloatArray(), xs, linearScale)
}

/**
 * The ONE x map behind both undercut strip draw sites — the printed strip and the detail canvas.
 *
 * Plans the rails on the to-scale map first ([planUndercutStripRails], the same arguments the
 * caller then plans its real rails with); a strip whose values do not crowd
 * ([undercutRailsCrowded]) keeps that map, so it draws exactly as it always has. A crowded strip
 * gets [fitUndercutStripXMap] with a floor under every chain and nested span: its value's width
 * plus the break's padding and two arrowheads — the width at which `layoutWearStripRail` seats it.
 * Baseline rails (From S.E.T., liner ends) need no floor: each is its own line, so they never crowd.
 *
 * [startX] / [linearScale] are the caller's to-scale layout (page points or canvas pixels);
 * [labelWidth], [scale] and [arrowSizePt] are exactly what the rail plan is handed, since the
 * floor and the seat test must agree.
 */
fun planUndercutStripXMap(
    drawStartMm: Float,
    drawEndMm: Float,
    startX: Float,
    linearScale: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
    dual: Boolean,
    splitAtMm: List<Float>,
    startedStrip: Boolean,
    labelWidth: (DualLabel) -> Float,
    scale: Float = 1f,
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    setLead: UndercutSetLead? = null,
    showTotal: Boolean = true,
    /** The job's "Dimensions" are From S.E.T.: the rails planned here are the strip's own. */
    fromSet: Boolean = false,
    /** "Liner ends from S.E.T." on a liner strip: the rails planned here are the strip's own. */
    linerEnds: UndercutLinerSpanMm? = null,
): UndercutStripXMap {
    val linear = UndercutStripXMap.linear(drawStartMm, drawEndMm, startX, linearScale)
    if (startedStrip || spans.isEmpty()) return linear
    val rails = planUndercutStripRails(
        chainStartMm, chainEndMm, spans, unit, dual, splitAtMm, startedStrip,
        xAt = linear::xAt, labelWidth = labelWidth, scale = scale, arrowSizePt = arrowSizePt,
        setLead = setLead, showTotal = showTotal, fromSet = fromSet, linerEnds = linerEnds,
    )
    if (!undercutRailsCrowded(rails)) return linear
    val seatRoom = (2f * (DIM_BREAK_TEXT_PAD_PT + arrowSizePt) + UNDERCUT_X_FLOOR_SLACK_PT) * scale
    val floors = (rails.chainSpans + rails.nestedSpans.flatten()).map { s ->
        UndercutXFloor(minOf(s.startMm, s.endMm), maxOf(s.startMm, s.endMm), labelWidth(s.label) + seatRoom)
    }
    return fitUndercutStripXMap(drawStartMm, drawEndMm, startX, linearScale, chainStartMm, chainEndMm, floors)
}

/** Slack over the exact seat width, so float rounding never leaves a floored value unseated. */
private const val UNDERCUT_X_FLOOR_SLACK_PT = DimensionRailLayout.ARROW_CLEAR + 1f
