// app/src/main/java/com/android/shaftschematic/pdf/UndercutRailDraw.kt
package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.util.drawDualLabelCentered
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.setsStacked

/** Clear gap between the drawn surface and a rail's witness lines, points. */
internal const val UNDERCUT_RAIL_WITNESS_GAP_PT = 3f

/** Rail arrowheads ([UNDERCUT_RAIL_LABEL_CLEAR_ARROW_PT]) → a fallback label's nearest edge, points. */
internal const val UNDERCUT_RAIL_LABEL_GAP_PT = 5f

/** How far a witness line runs past its rail line, points. */
private const val UNDERCUT_RAIL_WITNESS_EXT_PT = 3f

/** Horizontal / vertical pad of the page-colour halo behind a fallback value, points. */
private const val UNDERCUT_RAIL_HALO_PAD_X_PT = 1.5f
private const val UNDERCUT_RAIL_HALO_PAD_Y_PT = 0.5f

/**
 * Draws one undercut dimension rail — the ONE draw routine behind the printed strip
 * (`UndercutPdfComposer`) and the Undercut tab's detail canvas (`UndercutWindowDetailOverlay`),
 * fed by the ONE plan ([planUndercutStripRails]).
 *
 * Witness lines from [witnessBottomY] up past [railY], an arrowed dimension line per span, and
 * each span's value. A span's OPEN end ([WearRailSpanLayout.openStart]/`openEnd` — the S.E.T.
 * lead's half dimension) draws neither its witness line nor its head. A label that fits inside its own span (the inward-arrow test, which also
 * guarantees stub room at [DIM_BREAK_TEXT_PAD_PT]) **seats in a break cut in the line**,
 * vertically centred — the schematic's value-in-a-break convention. A label wider than its span
 * falls back to the stacked rows `layoutWearStripRail` assigned, clamped to [maxLabelRows].
 * [fallbackLabelAbove] stacks those rows ABOVE the line instead of below — the total-span rail
 * (whose space below is the chained rail's), a single-level chain with no total above it
 * (`planUndercutRailRows`: a small value pushed under the span line reads as orphaned when
 * nothing sits above the rail — on-device report), a chain LIFTED under its total on a strip with
 * the room (`undercutRailsLift`), and every nested row (below its line a value would land on the
 * shaft's top surface — on-device report). Baselines come from [undercutRailFallbackBaselineY].
 *
 * Every fallback value carries a **leader** — one straight stroke from its near halo edge to its
 * own dimension line ([undercutRailLeader]) — so a value lifted a row or two off its line still
 * says which span it states (on-device request). The leaders are drawn in the line pass, so a
 * later label's halo knocks out a leader that runs behind it exactly as it knocks out any line.
 *
 * [drawLabels] = false (blank write-in draft) keeps every line and arrowhead but skips the
 * values and their leaders — the lines-in/values-out template rule.
 *
 * [haloClearXs] are vertical lines ANOTHER rail drew through this rail's fallback rows (a lifted
 * chain's values sit across the total's witness lines): a halo whose edge would land inside such a
 * stroke widens just enough to cover all of it ([undercutRailHaloSpan]), so no sliver of the line
 * is left standing against the value.
 *
 * [arrowSizePt] is the head length (the app-wide `PdfPrefs.arrowSizePt`, in points) and must be
 * the value the plan was laid out with. [scale] multiplies every fixed point size here
 * (arrowhead, label gap, break pad, witness extension, halo pads, row pitch) for a canvas drawing
 * in another unit; [dim] and [dimText] are already in that unit. The page passes 1. [haloColor] is
 * the sheet's paper.
 *
 * This mirrors the wear sheet's `drawWearStripRail` rather than calling it: that function is
 * private to `WearPdfComposer.kt`, and this one additionally needs the
 * [witnessBottomY]/[fallbackLabelAbove] freedom the second (total) rail line requires. The
 * shared part — where labels seat, how arrows flip, how rows stack — is the pure
 * `layoutWearStripRail` both call.
 */
internal fun drawUndercutRail(
    c: Canvas,
    dim: Paint,
    dimText: Paint,
    layout: List<WearRailSpanLayout>,
    witnessBottomY: Float,
    railY: Float,
    maxLabelRows: Int,
    drawLabels: Boolean,
    fallbackLabelAbove: Boolean = false,
    /** Set dual values as two-line stacks — decided once for the sheet by the composer. */
    dualStacked: Boolean = false,
    scale: Float = 1f,
    haloColor: Int = Color.WHITE,
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    haloClearXs: List<Float> = emptyList(),
) {
    if (layout.isEmpty()) return
    val arrow = arrowSizePt * scale
    // Row pitch comes from the LAYOUT constant, not the text size, so the rows drawn here and
    // the rows `computeUndercutStripInnerLayout` budgeted cannot drift apart.
    val rowStep = undercutRailRowHeightPt(dimText, dualStacked, scale)
    val witnessExt = UNDERCUT_RAIL_WITNESS_EXT_PT * scale
    val breakPad = DIM_BREAK_TEXT_PAD_PT * scale
    val haloPadX = UNDERCUT_RAIL_HALO_PAD_X_PT * scale
    val haloPadY = UNDERCUT_RAIL_HALO_PAD_Y_PT * scale
    val fm = dimText.fontMetrics

    // Where each fallback value lands — its baseline and its halo box — worked out once, so the
    // line pass (its leader) and the label pass (its halo and text) read the same box.
    class Fallback(val baselineY: Float, val haloTop: Float, val haloBottom: Float)
    val fallbacks: List<Fallback?> = layout.map { s ->
        if (!drawLabels || s.seatsInBreak) return@map null
        val row = s.labelRow.coerceAtMost(maxLabelRows - 1)
        if (row < 0) return@map null
        val baselineY = undercutRailFallbackBaselineY(
            railY, row, fallbackLabelAbove, rowStep, dimText.textSize, fm.descent, scale,
        )
        // The halo covers the WHOLE value, both lines of a stack included, or a line would run
        // through the term it did not cover.
        val stackH = if (s.label.setsStacked(dualStacked)) dimText.dualStackMetrics().advance else 0f
        Fallback(
            baselineY,
            haloTop = baselineY + fm.ascent - haloPadY,
            haloBottom = baselineY + fm.descent + stackH + haloPadY,
        )
    }

    // Two passes: every line first, every label second. A fallback label for a span too
    // narrow to seat its value inevitably crosses its own (and often a neighbour's)
    // witness lines — the label pass paints a small page-colour halo behind each such
    // label, and running it after ALL lines means no later span can strike back through
    // (on-device report: the gap value between two cuts had lines running through it).
    layout.forEachIndexed { i, s ->
        // An OPEN end (the S.E.T. lead's half dimension) has no witness line and no head: the
        // line simply runs on toward an S.E.T. drawn off the strip.
        if (!s.openStart) c.drawLine(s.x0Pt, witnessBottomY, s.x0Pt, railY - witnessExt, dim)
        if (!s.openEnd) c.drawLine(s.x1Pt, witnessBottomY, s.x1Pt, railY - witnessExt, dim)

        val lw = dimText.measureDualLabel(s.label, dualStacked)
        val seatsInBreak = drawLabels && s.seatsInBreak
        if (seatsInBreak) {
            val gapHalf = lw * 0.5f + breakPad
            c.drawLine(s.x0Pt, railY, s.labelCxPt - gapHalf, railY, dim)
            c.drawLine(s.labelCxPt + gapHalf, railY, s.x1Pt, railY, dim)
        } else {
            c.drawLine(s.x0Pt, railY, s.x1Pt, railY, dim)
        }

        val dirLeft = if (s.arrowInward) 1f else -1f
        val dirRight = if (s.arrowInward) -1f else 1f
        if (!s.openStart) {
            c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY - arrow * 0.5f, dim)
            c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY + arrow * 0.5f, dim)
        }
        if (!s.openEnd) {
            c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY - arrow * 0.5f, dim)
            c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY + arrow * 0.5f, dim)
        }

        fallbacks[i]?.let { f ->
            undercutRailLeader(
                s.x0Pt, s.x1Pt, s.labelCxPt, arrow, railY,
                labelNearEdgeY = if (fallbackLabelAbove) f.haloBottom else f.haloTop,
            )?.let { l -> c.drawLine(l.x, l.fromY, l.x, l.toY, dim) }
        }
    }
    if (!drawLabels) return
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = haloColor }
    layout.forEachIndexed { i, s ->
        val lw = dimText.measureDualLabel(s.label, dualStacked)
        if (s.seatsInBreak) {
            // Break-seated: the gap already isolates the value from every line. A stack straddles
            // the rail — primary above it, secondary below — so the stubs point into the seam.
            val seatBaseline =
                if (s.label.setsStacked(dualStacked))
                    railY - dimText.dualStackMetrics().height * 0.5f - fm.ascent
                else railY - (fm.ascent + fm.descent) * 0.5f
            c.drawDualLabelCentered(s.label, s.labelCxPt, seatBaseline, dimText, dualStacked)
            return@forEachIndexed
        }
        val f = fallbacks[i] ?: return@forEachIndexed
        val (haloLeft, haloRight) = undercutRailHaloSpan(
            s.labelCxPt - lw * 0.5f - haloPadX,
            s.labelCxPt + lw * 0.5f + haloPadX,
            haloClearXs,
            lineReach = dim.strokeWidth * 0.5f + UNDERCUT_RAIL_HALO_AA_PT * scale,
        )
        c.drawRect(haloLeft, f.haloTop, haloRight, f.haloBottom, halo)
        c.drawDualLabelCentered(s.label, s.labelCxPt, f.baselineY, dimText, dualStacked)
    }
}

/** Antialiasing fringe a halo edge must clear past a covered line's stroke, points. */
private const val UNDERCUT_RAIL_HALO_AA_PT = 0.5f

/**
 * A fallback value's halo `[left, right]`, widened over every vertical line in [clearXs] whose
 * stroke ([lineReach] each side of its x, antialiasing included) the halo would otherwise cut
 * through: a line half under the halo would leave a sliver standing against the value. A line
 * wholly inside or wholly clear of the halo changes nothing, so with an empty [clearXs] the halo
 * is exactly `[left, right]`.
 */
internal fun undercutRailHaloSpan(
    left: Float,
    right: Float,
    clearXs: List<Float>,
    lineReach: Float,
): Pair<Float, Float> {
    var l = left
    var r = right
    clearXs.forEach { x ->
        if (x > left - lineReach && x < right + lineReach) {
            l = minOf(l, x - lineReach)
            r = maxOf(r, x + lineReach)
        }
    }
    return l to r
}

/** A fallback value's leader: one vertical stroke at [x] from [fromY] (the value) to [toY] (its line). */
internal data class UndercutRailLeader(val x: Float, val fromY: Float, val toY: Float)

/**
 * The leader joining a fallback rail value to its own dimension line — the pure endpoints
 * `drawUndercutRail` strokes.
 *
 * It drops (or rises) straight from [labelNearEdgeY] — the value's halo edge facing the line, its
 * bottom for a value above the line and its top for one below — to [railY], at the value's centre
 * [labelCxPt] clamped into `[x0Pt + arrowPt, x1Pt − arrowPt]`: it lands on the span's own line
 * between its arrowheads, never on a head and never on a neighbour's span, even where the value
 * slid along its row or overhangs its span. A span too short to hold that interval takes its
 * midpoint. [arrowPt] is the drawn head length in the caller's unit. Null when the value already
 * touches the line — there is nothing to bridge.
 */
internal fun undercutRailLeader(
    x0Pt: Float,
    x1Pt: Float,
    labelCxPt: Float,
    arrowPt: Float,
    railY: Float,
    labelNearEdgeY: Float,
): UndercutRailLeader? {
    if (labelNearEdgeY == railY) return null
    val lo = minOf(x0Pt, x1Pt) + arrowPt
    val hi = maxOf(x0Pt, x1Pt) - arrowPt
    val x = if (lo <= hi) labelCxPt.coerceIn(lo, hi) else (x0Pt + x1Pt) * 0.5f
    return UndercutRailLeader(x, labelNearEdgeY, railY)
}

/**
 * Baseline of a rail value that falls back off its line, in row [row] (0 nearest the line).
 * [above] stacks the rows upward from just over the arrowheads straddling the line; otherwise
 * row 0 starts clear of the arrowheads below it. On the total-span rail (always one span) row 0
 * above is the whole band ([UNDERCUT_TOTAL_RAIL_ABOVE_PT] is sized to hold exactly it); on a
 * single-level chain and on a nested row the rows above come from the band reserved over that
 * line. [textSize] and [descent] are the label paint's (descent positive); [scale] is
 * `drawUndercutRail`'s.
 */
internal fun undercutRailFallbackBaselineY(
    railY: Float,
    row: Int,
    above: Boolean,
    rowStep: Float,
    textSize: Float,
    descent: Float,
    scale: Float = 1f,
): Float {
    val arrow = UNDERCUT_RAIL_LABEL_CLEAR_ARROW_PT * scale
    val labelGap = UNDERCUT_RAIL_LABEL_GAP_PT * scale
    return if (above) railY - arrow * 0.5f - labelGap - descent - row * rowStep
    else railY + arrow + labelGap + textSize + row * rowStep
}

/**
 * The air a below-line fallback band needs under its LAST row so that row's value stays off the
 * shaft, points: the overhang of that value's ink — its descent, and a stacked value's second line
 * — past the band's bottom edge ([undercutRailFallbackBaselineY]: row 0's baseline sits a label
 * gap and a text height under the line, so the ink runs past one row pitch), plus a point of
 * daylight, and never less than the witness gap plus a point. The printed strip reserves it under
 * the chain-only compressed plan, the one whose below band has no clear row
 * (`UndercutStripPlan.innerLayout`).
 */
internal fun undercutRailBelowClearPt(dimText: Paint, dualStacked: Boolean): Float {
    val stackH = if (dualStacked) dimText.dualStackMetrics().advance else 0f
    val overhang = UNDERCUT_RAIL_LABEL_CLEAR_ARROW_PT + UNDERCUT_RAIL_LABEL_GAP_PT + dimText.textSize +
        dimText.fontMetrics.descent + stackH - undercutRailRowHeightPt(dimText, dualStacked)
    return maxOf(UNDERCUT_RAIL_WITNESS_GAP_PT + 1f, overhang + 1f)
}

/**
 * Y of each nesting level's rail line, in [UndercutStripRails.nested] order — the ONE stepping
 * behind [drawUndercutStripRails] and every test that checks it. The levels stack directly under
 * the chain's OWN below-line label rows ([UndercutRailRowPlan.chainBelowRows] — none when the
 * chain's fallbacks sit above its line), never under the plan's clear-air row: that row is the
 * LOWEST of the band, between the last nested line and the shaft. Each level claims its fallback
 * rows ([undercutRailFallbackRows]) and then one row for its line, so its fallback values sit
 * ABOVE the line, between the previous line's labels and this one. The band is the one
 * `planUndercutRailRows` reserved — only the order within it is fixed here. Where a short band
 * granted fewer rows ([railLabelRows]) than the plan asked for, the chain's rows shrink to what
 * is left after the nested rows, and a line never drops below [witnessBottomY].
 */
internal fun undercutNestedRailLineYs(
    rails: UndercutStripRails,
    chainRailY: Float,
    railLabelRows: Int,
    rowStep: Float,
    witnessBottomY: Float,
): List<Float> {
    val chainBelowRows = minOf(
        rails.rows.chainBelowRows,
        (railLabelRows - rails.rows.nestedRows).coerceAtLeast(0),
    )
    var rowY = chainRailY + chainBelowRows * rowStep
    return rails.nested.map { lay ->
        rowY += (1 + undercutRailFallbackRows(lay)) * rowStep
        rowY.coerceAtMost(witnessBottomY)
    }
}

/**
 * Draws one strip's whole rail stack from its plan ([planUndercutStripRails]) — the level-0
 * chain with its S.E.T. lead, one extra row per nesting level stacked UNDER it, and the total
 * above it — the ONE
 * sequence the printed strip and the detail canvas both run.
 *
 * The order is fixed — total, then chain, then the nested rows. The total's witness lines stand on
 * the chain line and run up through the band where a lifted chain's fallback values sit
 * ([UndercutStripRails.withFallbacksLifted]); drawing the chain after the total lets its label pass
 * paint each value's halo over those witnesses. With nothing lifted the two rails meet only where
 * a total witness joins the chain line, so the order changes nothing visible there (an antialiased
 * junction pixel may round one grey level differently).
 *
 * Chained dimensions run most-detailed nearest the part, and a nested cut is located against its
 * PARENT's edges, not the strip's datums. Parents at one level are disjoint, so a level always
 * lays out on a single row. Rows step by the ONE row-height metric the reserved band was budgeted
 * with (`planUndercutRailRows`, [undercutNestedRailLineYs]): the nested rows stack directly under
 * the chain's own below-line rows, and the plan's clear-air row stays the lowest, between the last
 * nested line and the shaft. A nested row's fallback values sit above its own line, clear of the
 * chain's labels and of the shaft below.
 *
 * [railLabelRows] is the label rows the strip actually granted below the chain — the plan's
 * `belowRows`, or fewer where a short band clamps it.
 * [witnessBottomY] is where every witness line of the chain and the nested rows starts (just
 * above the drawn surface); the total's witness lines stand on the chain line.
 */
internal fun drawUndercutStripRails(
    c: Canvas,
    dim: Paint,
    dimText: Paint,
    rails: UndercutStripRails,
    chainRailY: Float,
    totalRailY: Float,
    witnessBottomY: Float,
    railLabelRows: Int,
    drawLabels: Boolean,
    dualStacked: Boolean = false,
    scale: Float = 1f,
    haloColor: Int = Color.WHITE,
    /** The app-wide "Dimension arrows" head, points — the value [rails] was planned with. */
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
) {
    val plan = rails.rows
    // The total first: its witness lines stand on the chain line, and the chain's label pass below
    // must paint over them where a lifted value sits in their way.
    drawUndercutRail(
        c, dim, dimText, rails.total,
        witnessBottomY = chainRailY,
        railY = totalRailY,
        maxLabelRows = 1,
        drawLabels = drawLabels,
        fallbackLabelAbove = true,
        dualStacked = dualStacked,
        scale = scale,
        haloColor = haloColor,
        arrowSizePt = arrowSizePt,
    )
    // The level-0 line carries the S.E.T. lead at its outboard end, laid out with the chain.
    drawUndercutRail(
        c, dim, dimText, rails.chainWithLead,
        witnessBottomY = witnessBottomY,
        railY = chainRailY,
        // The chain's own label rows stop where the nested rows begin: the rest of the reserved
        // band belongs to the levels stacked under it.
        maxLabelRows = if (plan.aboveRows > 0) plan.aboveRows
        else (railLabelRows - plan.nestedRows).coerceAtLeast(1),
        drawLabels = drawLabels,
        fallbackLabelAbove = plan.aboveRows > 0,
        dualStacked = dualStacked,
        scale = scale,
        haloColor = haloColor,
        arrowSizePt = arrowSizePt,
        // Lifted values sit across the total's witness lines (chain line → total line).
        haloClearXs = if (plan.aboveRows > 0) rails.total.flatMap { listOf(it.x0Pt, it.x1Pt) } else emptyList(),
    )
    if (rails.nested.isNotEmpty()) {
        val rowStep = undercutRailRowHeightPt(dimText, dualStacked, scale)
        val lineYs = undercutNestedRailLineYs(rails, chainRailY, railLabelRows, rowStep, witnessBottomY)
        rails.nested.forEachIndexed { i, lay ->
            drawUndercutRail(
                c, dim, dimText, lay,
                witnessBottomY = witnessBottomY,
                railY = lineYs[i],
                maxLabelRows = undercutRailFallbackRows(lay).coerceAtLeast(1),
                drawLabels = drawLabels,
                // A nested row's fallbacks sit ABOVE its line, in the rows reserved over it: below
                // it they would land on the shaft's top surface (on-device report).
                fallbackLabelAbove = true,
                dualStacked = dualStacked,
                scale = scale,
                haloColor = haloColor,
                arrowSizePt = arrowSizePt,
            )
        }
    }
}
