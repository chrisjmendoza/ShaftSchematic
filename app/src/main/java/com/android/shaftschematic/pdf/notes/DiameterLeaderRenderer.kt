package com.android.shaftschematic.pdf.notes

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.geom.DiameterCalloutLayout
import com.android.shaftschematic.pdf.formatDiaWithUnitDualLabel
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.drawDualLabel
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.measureDualLabel

/**
 * Renders simple diameter leaders with a dog-leg and "Ø" label.
 *
 * Each [DiaCallout] carries its own resolved [DiaCallout.unit]/[DiaCallout.dual] (set by the
 * builder from `DisplayUnits.unitFor(componentId)`), so a mixed-unit sheet can show a body in
 * mm beside a liner in inches. Labels use [formatDiaWithUnitDual] (whole inches bare, otherwise
 * 3 decimals; single-unit output when a callout's `dual` is false) so an on-shaft callout
 * reads like the footer's L.E.T. / S.E.T. values.
 *
 * BELOW-side callouts that would collide horizontally are stacked onto a second row via
 * [DiameterCalloutLayout] — the same two-tier posture the runout bubbles use. ABOVE-side
 * callouts (none produced today) use plain single-row geometry — no tiering.
 */
class DiameterLeaderRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val shaftTopY: Float,
    private val shaftBottomY: Float,
    private val leaderRise: Float = 16f,
    private val leaderDogleg: Float = 14f,
    private val linePaint: Paint,
    private val textPaint: Paint,
    /** Blank-draft mode: print "Ø" + a writable rule instead of the value. */
    private val blankValues: Boolean = false,
    private val blankRuleWidth: Float = 40f,
    /** Set dual values as a two-line stack — see `util/DualLabelRenderer.kt`. */
    private val dualStacked: Boolean = false,
    /**
     * The content area a label must stay inside — a label that would run past [contentRight]
     * hangs left of its leader instead ([DiameterCalloutLayout.labelLeft]). Unbounded by default.
     */
    private val contentLeft: Float = Float.NEGATIVE_INFINITY,
    private val contentRight: Float = Float.POSITIVE_INFINITY,
) {
    /** Where [call]'s label starts, left of or right of its leader — ONE answer for tiers, occupancy and ink. */
    private fun textLeftFor(call: DiaCallout, anchorX: Float): Float =
        DiameterCalloutLayout.labelLeft(anchorX, labelWidth(call), leaderDogleg, contentLeft, contentRight)

    /**
     * Vertical distance between stacked BELOW-side rows.
     *
     * A stacked dual value is two lines tall, so the tier pitch has to clear the whole stack plus
     * the same air a single row got — otherwise the second tier's first line prints into the first
     * tier's second one.
     */
    private val tierStep: Float
        get() = if (dualStacked) textPaint.dualStackMetrics().height + textPaint.textSize * 0.4f
                else textPaint.textSize * 1.4f

    fun draw(canvas: Canvas, calls: List<DiaCallout>) {
        val below = calls.filter { it.side == LeaderSide.BELOW }
        val tiers = belowTiers(below)
        below.forEachIndexed { i, call -> drawOne(canvas, call, tier = tiers[i]) }

        calls.filter { it.side == LeaderSide.ABOVE }.forEach { drawOne(canvas, it, tier = 0) }
    }

    /** BELOW callouts share a tiering pass so body and liner labels never overlap. */
    private fun belowTiers(below: List<DiaCallout>): List<Int> {
        val footprints = below.map { call ->
            val anchorX = pageX(call.xMm)
            val labelLeft = textLeftFor(call, anchorX)
            val labelRight = labelLeft + labelWidth(call)
            DiameterCalloutLayout.Footprint(left = minOf(anchorX, labelLeft), right = maxOf(anchorX, labelRight))
        }
        return DiameterCalloutLayout.assignTiers(footprints)
    }

    /**
     * The page area this renderer will ink, for anything else that prints below the shaft.
     *
     * The component-name labels hang in the same band and anchor on the same component centers,
     * so they are placed against these boxes as obstacles
     * ([com.android.shaftschematic.geom.BelowShaftLabelLayout]) rather than in a collision space
     * of their own. Two boxes per callout: the value's text box, and the leader itself — the
     * vertical drop is a thin column at the anchor, but a name label crossing it reads as a
     * struck-through word just the same.
     *
     * Measured from the SAME geometry [drawOne] draws from, so the reservation and the ink can
     * never disagree. BELOW callouts only; the ABOVE side (none produced today) prints clear of
     * this band entirely.
     */
    fun occupancy(calls: List<DiaCallout>): List<BelowShaftLabelLayout.Box> {
        val below = calls.filter { it.side == LeaderSide.BELOW }
        if (below.isEmpty()) return emptyList()
        val tiers = belowTiers(below)
        val fm = textPaint.fontMetrics
        val textH = if (dualStacked) textPaint.dualStackMetrics().height else fm.descent - fm.ascent
        // A stack's box hangs BELOW its first baseline; a single line's box is ascent-to-descent
        // around it. Either way the top is the first line's ascent above the baseline.
        val above = -fm.ascent

        return buildList {
            below.forEachIndexed { i, call ->
                val x = pageX(call.xMm)
                val kinkY = shaftBottomY + leaderRise + tiers[i] * tierStep
                val textY = kinkY + 10f
                val textLeft = textLeftFor(call, x)
                val textRight = textLeft + labelWidth(call)
                add(
                    BelowShaftLabelLayout.Box(
                        left = textLeft,
                        right = textRight,
                        top = textY - above,
                        bottom = textY - above + textH,
                    )
                )
                // Leader: the vertical drop from the shaft plus the horizontal dogleg into the text.
                add(
                    BelowShaftLabelLayout.Box(
                        left = minOf(x - LEADER_HALF_W, textRight),
                        right = maxOf(textLeft, x + LEADER_HALF_W),
                        top = shaftBottomY,
                        bottom = kinkY + LEADER_HALF_W,
                    )
                )
            }
        }
    }

    /**
     * The callout's value as its two terms. The "Ø" identifier rides the PRIMARY, so a stacked
     * callout reads `Ø 11"` over `279.4 mm` instead of repeating the symbol.
     */
    private fun label(call: DiaCallout): DualLabel {
        if (blankValues) return DualLabel.single("Ø")
        val value = formatDiaWithUnitDualLabel(call.valueMm, call.unit, call.dual)
        return value.copy(primary = "Ø " + value.primary)
    }

    private fun labelWidth(call: DiaCallout): Float =
        textPaint.measureDualLabel(label(call), dualStacked) +
            if (blankValues) 4f + blankRuleWidth else 0f

    private fun drawOne(canvas: Canvas, call: DiaCallout, tier: Int) {
        val x = pageX(call.xMm)
        val (startY, kinkY, textY) = when (call.side) {
            LeaderSide.ABOVE -> {
                val s = shaftTopY
                Triple(s, s - leaderRise, s - leaderRise - 2f)
            }
            LeaderSide.BELOW -> {
                val s = shaftBottomY
                val depth = leaderRise + tier * tierStep
                Triple(s, s + depth, s + depth + 10f)
            }
        }
        val textX = if (call.side == LeaderSide.BELOW) textLeftFor(call, x) else x + leaderDogleg
        // The dogleg runs to the label's NEAR edge — its left edge normally, its right edge when
        // the label hangs left of the leader.
        val doglegEnd = if (textX >= x) textX else textX + labelWidth(call)

        canvas.drawLine(x, startY, x, kinkY, linePaint)
        canvas.drawLine(x, kinkY, doglegEnd, kinkY, linePaint)

        val lbl = label(call)
        canvas.drawDualLabel(lbl, textX, textY, textPaint, dualStacked)
        if (blankValues) {
            // Writable rule after the "Ø" so the machinist can pencil the measured OD in.
            val ruleStart = textX + textPaint.measureDualLabel(lbl, dualStacked) + 4f
            val rule = Paint(textPaint).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f }
            canvas.drawLine(ruleStart, textY + 2f, ruleStart + blankRuleWidth, textY + 2f, rule)
        }
    }
}

/** Half-width reserved around a leader line, so a neighbouring label clears the stroke itself. */
private const val LEADER_HALF_W = 1f
