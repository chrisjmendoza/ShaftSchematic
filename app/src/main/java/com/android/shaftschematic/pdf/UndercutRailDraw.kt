// app/src/main/java/com/android/shaftschematic/pdf/UndercutRailDraw.kt
package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.util.drawDualLabelCentered
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.setsStacked

/** Clear gap between the drawn surface and a rail's witness lines, points. */
internal const val UNDERCUT_RAIL_WITNESS_GAP_PT = 3f

/** Rail arrowheads → a fallback label's nearest edge, points. */
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
 * each span's value. A label that fits inside its own span (the inward-arrow test, which also
 * guarantees stub room at [DIM_BREAK_TEXT_PAD_PT]) **seats in a break cut in the line**,
 * vertically centred — the schematic's value-in-a-break convention. A label wider than its span
 * falls back to the stacked rows `layoutWearStripRail` assigned, clamped to [maxLabelRows].
 * [fallbackLabelAbove] stacks those rows ABOVE the line instead of below — the total-span rail
 * (whose space below is the chained rail's) and a single-level chain with no total above it
 * (`planUndercutRailRows`: a small value pushed under the span line reads as orphaned when
 * nothing sits above the rail — on-device report).
 *
 * [drawLabels] = false (blank write-in draft) keeps every line and arrowhead but skips the
 * values — the lines-in/values-out template rule.
 *
 * [scale] multiplies every fixed point size here (arrowhead, label gap, break pad, witness
 * extension, halo pads, row pitch) for a canvas drawing in another unit; [dim] and [dimText] are
 * already in that unit. The page passes 1. [haloColor] is the sheet's paper.
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
) {
    if (layout.isEmpty()) return
    val arrow = UNDERCUT_RAIL_ARROW_PT * scale
    val labelGap = UNDERCUT_RAIL_LABEL_GAP_PT * scale
    // Row pitch comes from the LAYOUT constant, not the text size, so the rows drawn here and
    // the rows `computeUndercutStripInnerLayout` budgeted cannot drift apart.
    val rowStep = undercutRailRowHeightPt(dimText, dualStacked, scale)
    val witnessExt = UNDERCUT_RAIL_WITNESS_EXT_PT * scale
    val breakPad = DIM_BREAK_TEXT_PAD_PT * scale

    // Two passes: every line first, every label second. A fallback label for a span too
    // narrow to seat its value inevitably crosses its own (and often a neighbour's)
    // witness lines — the label pass paints a small page-colour halo behind each such
    // label, and running it after ALL lines means no later span can strike back through
    // (on-device report: the gap value between two cuts had lines running through it).
    layout.forEach { s ->
        c.drawLine(s.x0Pt, witnessBottomY, s.x0Pt, railY - witnessExt, dim)
        c.drawLine(s.x1Pt, witnessBottomY, s.x1Pt, railY - witnessExt, dim)

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
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x0Pt, railY, s.x0Pt + dirLeft * arrow, railY + arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY - arrow * 0.5f, dim)
        c.drawLine(s.x1Pt, railY, s.x1Pt + dirRight * arrow, railY + arrow * 0.5f, dim)
    }
    if (!drawLabels) return
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = haloColor }
    val haloPadX = UNDERCUT_RAIL_HALO_PAD_X_PT * scale
    val haloPadY = UNDERCUT_RAIL_HALO_PAD_Y_PT * scale
    val fm = dimText.fontMetrics
    layout.forEach { s ->
        val lw = dimText.measureDualLabel(s.label, dualStacked)
        if (s.seatsInBreak) {
            // Break-seated: the gap already isolates the value from every line. A stack straddles
            // the rail — primary above it, secondary below — so the stubs point into the seam.
            val seatBaseline =
                if (s.label.setsStacked(dualStacked))
                    railY - dimText.dualStackMetrics().height * 0.5f - fm.ascent
                else railY - (fm.ascent + fm.descent) * 0.5f
            c.drawDualLabelCentered(s.label, s.labelCxPt, seatBaseline, dimText, dualStacked)
            return@forEach
        }
        val row = s.labelRow.coerceAtMost(maxLabelRows - 1)
        if (row < 0) return@forEach
        val baselineY = if (fallbackLabelAbove) {
            // Above the line, clear of the arrowheads straddling it, stacking upward. On the
            // total-span rail (always one span) row 0 is the whole band
            // ([UNDERCUT_TOTAL_RAIL_ABOVE_PT] is sized to hold exactly it); on a single-level
            // chain the rows come from the reserved `chainAboveBandPt`.
            railY - arrow * 0.5f - labelGap - fm.descent - row * rowStep
        } else {
            // First row starts clear of the outward arrowheads straddling the rail line.
            railY + arrow + labelGap + dimText.textSize + row * rowStep
        }
        // The halo covers the WHOLE value, both lines of a stack included, or a line would run
        // through the term it did not cover.
        val stackH = if (s.label.setsStacked(dualStacked)) dimText.dualStackMetrics().advance else 0f
        c.drawRect(
            s.labelCxPt - lw * 0.5f - haloPadX, baselineY + fm.ascent - haloPadY,
            s.labelCxPt + lw * 0.5f + haloPadX, baselineY + fm.descent + stackH + haloPadY,
            halo,
        )
        c.drawDualLabelCentered(s.label, s.labelCxPt, baselineY, dimText, dualStacked)
    }
}

/**
 * Draws one strip's whole rail stack from its plan ([planUndercutStripRails]) — the level-0
 * chain, one extra row per nesting level stacked UNDER it, and the total above it — the ONE
 * sequence the printed strip and the detail canvas both run.
 *
 * Chained dimensions run most-detailed nearest the part, and a nested cut is located against its
 * PARENT's edges, not the strip's datums. Parents at one level are disjoint, so a level always
 * lays out on a single row. Rows step by the ONE row-height metric the reserved band was budgeted
 * with (`planUndercutRailRows`), each line clearing the fallback labels of the row above it.
 *
 * [railLabelRows] is the label rows the strip actually granted below the chain — the plan's
 * `belowRows`, or fewer where a short band clamps it — and the nested rows follow it down.
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
) {
    val plan = rails.rows
    drawUndercutRail(
        c, dim, dimText, rails.chain,
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
    )
    if (rails.nested.isNotEmpty()) {
        val rowStep = undercutRailRowHeightPt(dimText, dualStacked, scale)
        // Rows the chain's own labels occupy, off what the strip actually granted.
        val chainBelowRows = (railLabelRows - plan.nestedRows).coerceAtLeast(0)
        var rowY = chainRailY + chainBelowRows * rowStep
        rails.nested.forEach { lay ->
            rowY += rowStep
            drawUndercutRail(
                c, dim, dimText, lay,
                witnessBottomY = witnessBottomY,
                railY = rowY.coerceAtMost(witnessBottomY),
                maxLabelRows = WEAR_RAIL_MAX_LABEL_ROWS,
                drawLabels = drawLabels,
                fallbackLabelAbove = false,
                dualStacked = dualStacked,
                scale = scale,
                haloColor = haloColor,
            )
            rowY += undercutRailFallbackRows(lay) * rowStep
        }
    }
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
    )
}
