package com.android.shaftschematic.geom

import kotlin.math.max
import kotlin.math.min

/**
 * RailValueLeader — the ONE leader construction joining a dimension value that does not sit on
 * its own line to the span it states.
 *
 * Two rail families draw it: the undercut strip rails (a fallback value lifted a row or two off
 * its line, `pdf/UndercutRailDraw.kt`) and the schematic / consolidated dimension rails (a value
 * placed just outside its span, or bumped up a band, by [DimensionRailLayout]). Both read the same
 * pure endpoints, so a value off its line reads with its span the same way on every sheet.
 *
 * Pure — no Android; callers stroke it with their own dimension paint.
 */

/**
 * One leader: a straight stroke from ([fromX], [fromY]) at the value to ([x], [toY]) on the
 * value's own dimension line. Vertical when the value stands over the landing point
 * ([fromX] == [x]); otherwise it slants in from the value's nearest bottom (or top) corner.
 */
data class RailValueLeader(val x: Float, val fromY: Float, val toY: Float, val fromX: Float = x)

/**
 * The leader joining a value to its own dimension line.
 *
 * It runs from [labelNearEdgeY] — the value's edge facing the line, its bottom for a value above
 * the line and its top for one below — to [railY], landing at [anchorX] (default: the value's
 * centre [labelCxPt]) clamped into `[x0Pt + arrowPt, x1Pt − arrowPt]`: on the span's own line between its
 * arrowheads, never on a head and never on a neighbour's span, even where the value slid along
 * its row or sits outside its span. A span too short to hold that interval takes its midpoint.
 *
 * The leader leaves the value at the landing x when the value's box (half width
 * [labelHalfWidth]) stands over it, and from the box's nearer corner otherwise — a value placed
 * beside its span slants in rather than dropping a line that never touches it. The default half
 * width is unbounded, which is the always-vertical leader.
 *
 * Null when the value already touches the line — there is nothing to bridge.
 */
fun railValueLeader(
    x0Pt: Float,
    x1Pt: Float,
    labelCxPt: Float,
    arrowPt: Float,
    railY: Float,
    labelNearEdgeY: Float,
    labelHalfWidth: Float = Float.POSITIVE_INFINITY,
    anchorX: Float = labelCxPt,
): RailValueLeader? {
    if (labelNearEdgeY == railY) return null
    val lo = min(x0Pt, x1Pt) + arrowPt
    val hi = max(x0Pt, x1Pt) - arrowPt
    val x = if (lo <= hi) anchorX.coerceIn(lo, hi) else (x0Pt + x1Pt) * 0.5f
    val fromX = x.coerceIn(labelCxPt - labelHalfWidth, labelCxPt + labelHalfWidth)
    return RailValueLeader(x, labelNearEdgeY, railY, fromX)
}
