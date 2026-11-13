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
 * The piecewise map is the DETAIL CANVAS's only ([planUndercutStripXMap]) — an input surface,
 * where reading every value matters more than proportion. A printed strip is always the linear
 * map. Either way ONE map stands behind everything a strip draws along its length: profile,
 * notches, liner outline, rails, Ø leaders, cut numbers and the canvas tap. Radii never go
 * through it.
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
 * - The stretches that are NOT short keep at least [UNDERCUT_X_LONG_MIN_FRAC] of the chain's width
 *   between them (on-device report: a 31 15/16" cut drew the same width as the 9/16" slivers
 *   beside it — "it's tiny, that's wrong"); the short ones' shared width is cut down to leave it.
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
    if (chainWidth <= 0f) return linear
    // The long stretches stay visibly long: the short ones may take only what leaves the rest
    // [UNDERCUT_X_LONG_MIN_FRAC] of the chain (or their to-scale width, when that is less).
    val shortAtoms = short.flatMap { atomsOf(it) }.toSet()
    val longLin = chain.filter { it !in shortAtoms }.sumOf { lin[it].toDouble() }.toFloat()
    val shortBudget = chainWidth - minOf(longLin, UNDERCUT_X_LONG_MIN_FRAC * chainWidth)
    val shortSum = shortAtoms.sumOf { floor[it].toDouble() }.toFloat()
    if (shortSum > shortBudget && shortSum > 0f) shortAtoms.forEach { floor[it] *= shortBudget / shortSum }
    val floorSum = chain.sumOf { floor[it].toDouble() }.toFloat()
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
 * The detail canvas's x map. A PRINTED strip never takes it: the sheet shows the work done at its
 * true proportions (on-device ruling), so `planUndercutDetailStrip` always maps to scale and a
 * crowded sheet takes fallback rows instead.
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
    /** [planUndercutStripRails]' `nestedRows` — the rails planned here are the strip's own. */
    nestedRows: Boolean = false,
): UndercutStripXMap {
    val linear = UndercutStripXMap.linear(drawStartMm, drawEndMm, startX, linearScale)
    if (startedStrip || spans.isEmpty()) return linear
    val rails = planUndercutStripRails(
        chainStartMm, chainEndMm, spans, unit, dual, splitAtMm, startedStrip,
        xAt = linear::xAt, labelWidth = labelWidth, scale = scale, arrowSizePt = arrowSizePt,
        setLead = setLead, showTotal = showTotal, fromSet = fromSet, linerEnds = linerEnds,
        nestedRows = nestedRows,
    )
    if (!undercutRailsCrowded(rails)) return linear
    val seatRoom = (2f * (DIM_BREAK_TEXT_PAD_PT + arrowSizePt) + UNDERCUT_X_FLOOR_SLACK_PT) * scale
    val floors = (rails.chainSpans + rails.nestedSpans.flatten()).map { s ->
        UndercutXFloor(minOf(s.startMm, s.endMm), maxOf(s.startMm, s.endMm), labelWidth(s.label) + seatRoom)
    }
    return fitUndercutStripXMap(drawStartMm, drawEndMm, startX, linearScale, chainStartMm, chainEndMm, floors)
}

/**
 * The PRINTED strip's "Compress long sections" map (`UndercutRecord.longCompressFrac`, on-device
 * request: let the user choose how far the long areas compress so the small cuts are easier to
 * see). Every stretch between two shoulders ([edgesMm]) inside the chain keeps its length up to
 * [UNDERCUT_LONG_SECTION_FRAC] of the chain; only the EXCESS past that is shortened, by [frac].
 * The chain keeps its drawn width, so everything in it draws larger by one factor: cuts shorter
 * than the threshold stay in true proportion to each other, a longer stretch always draws longer
 * than a shorter one, and the stock outside the chain stays to scale (break room, S.E.T. lead).
 *
 * Returns the linear map when [frac] is 0 or nothing moves by [UNDERCUT_X_MOVED_EPS] — the sheet
 * then is to scale and prints no "Not to scale" note.
 */
internal fun compressLongUndercutStripXMap(
    drawStartMm: Float,
    drawEndMm: Float,
    startX: Float,
    linearScale: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    edgesMm: List<Float>,
    frac: Float,
): UndercutStripXMap {
    val linear = UndercutStripXMap.linear(drawStartMm, drawEndMm, startX, linearScale)
    val eps = UndercutStripXMap.KNOT_EPS_MM
    val chainLen = chainEndMm - chainStartMm
    if (frac <= 0f || linearScale <= 0f || chainLen <= eps || drawEndMm - drawStartMm <= eps) return linear
    val keep = 1f - frac.coerceAtMost(1f)
    val knots = (edgesMm.filter { it > chainStartMm && it < chainEndMm } + listOf(drawStartMm, drawEndMm, chainStartMm, chainEndMm))
        .map { it.coerceIn(drawStartMm, drawEndMm) }
        .sorted()
        .fold(mutableListOf<Float>()) { acc, k -> if (acc.isEmpty() || k - acc.last() > eps) acc += k; acc }
    knots[knots.lastIndex] = drawEndMm
    val n = knots.size - 1
    if (n < 2) return linear
    val threshold = UNDERCUT_LONG_SECTION_FRAC * chainLen
    val len = FloatArray(n) { knots[it + 1] - knots[it] }
    val isPad = BooleanArray(n) { i ->
        val mid = (knots[i] + knots[i + 1]) * 0.5f
        mid < chainStartMm || mid > chainEndMm
    }
    val eff = FloatArray(n) { i -> if (len[i] <= threshold) len[i] else threshold + (len[i] - threshold) * keep }
    val chain = (0 until n).filter { !isPad[it] }
    val chainWidth = chain.sumOf { (len[it] * linearScale).toDouble() }.toFloat()
    val effSum = chain.sumOf { eff[it].toDouble() }.toFloat()
    if (effSum <= 0f) return linear
    val xs = FloatArray(n + 1)
    xs[0] = startX
    var moved = 0f
    for (i in 0 until n) {
        xs[i + 1] = xs[i] + if (isPad[i]) len[i] * linearScale else chainWidth * eff[i] / effSum
        moved = maxOf(moved, kotlin.math.abs(xs[i + 1] - linear.xAt(knots[i + 1])))
    }
    if (moved < UNDERCUT_X_MOVED_EPS) return linear
    return UndercutStripXMap.piecewise(knots.toFloatArray(), xs, linearScale)
}

/** A stretch longer than this share of its chain is a "long section": only its excess compresses. */
const val UNDERCUT_LONG_SECTION_FRAC = 0.10f

/** A compressed map that moves no station this far (drawing units) is the to-scale map. */
private const val UNDERCUT_X_MOVED_EPS = 0.25f

/**
 * The least share of a crowded chain's drawn width its long (unfloored-short) stretches keep
 * between them, so the big section still reads as the big one beside widened slivers.
 */
const val UNDERCUT_X_LONG_MIN_FRAC = 0.45f

/** Slack over the exact seat width, so float rounding never leaves a floored value unseated. */
private const val UNDERCUT_X_FLOOR_SLACK_PT = DimensionRailLayout.ARROW_CLEAR + 1f
