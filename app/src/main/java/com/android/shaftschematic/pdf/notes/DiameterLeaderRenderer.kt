package com.android.shaftschematic.pdf.notes

import android.graphics.Canvas
import android.graphics.Paint
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
 * mm beside a liner in inches. Labels use [formatDiaWithUnitDual] (≤3 decimals, trailing
 * zeros trimmed; single-unit output when a callout's `dual` is false) so an on-shaft callout
 * reads identically to the footer's "Body: Ø …" line.
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
) {
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
        // BELOW callouts share a tiering pass so body and liner labels never overlap.
        val below = calls.filter { it.side == LeaderSide.BELOW }
        val footprints = below.map { call ->
            val anchorX = pageX(call.xMm)
            val labelLeft = anchorX + leaderDogleg
            val labelRight = labelLeft + labelWidth(call)
            DiameterCalloutLayout.Footprint(left = anchorX, right = labelRight)
        }
        val tiers = DiameterCalloutLayout.assignTiers(footprints)
        below.forEachIndexed { i, call -> drawOne(canvas, call, tier = tiers[i]) }

        calls.filter { it.side == LeaderSide.ABOVE }.forEach { drawOne(canvas, it, tier = 0) }
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
        val textX = x + leaderDogleg

        canvas.drawLine(x, startY, x, kinkY, linePaint)
        canvas.drawLine(x, kinkY, textX, kinkY, linePaint)

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
