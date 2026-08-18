package com.android.shaftschematic.pdf.notes

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.DiameterCalloutLayout
import com.android.shaftschematic.pdf.formatDiaWithUnitDual

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
    private val blankRuleWidth: Float = 40f
) {
    /** Vertical distance between stacked BELOW-side rows. */
    private val tierStep: Float get() = textPaint.textSize * 1.4f

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

    private fun label(call: DiaCallout): String =
        if (blankValues) "Ø" else "Ø " + formatDiaWithUnitDual(call.valueMm, call.unit, call.dual)

    private fun labelWidth(call: DiaCallout): Float =
        textPaint.measureText(label(call)) + if (blankValues) 4f + blankRuleWidth else 0f

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
        canvas.drawText(lbl, textX, textY, textPaint)
        if (blankValues) {
            // Writable rule after the "Ø" so the machinist can pencil the measured OD in.
            val ruleStart = textX + textPaint.measureText(lbl) + 4f
            val rule = Paint(textPaint).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f }
            canvas.drawLine(ruleStart, textY + 2f, ruleStart + blankRuleWidth, textY + 2f, rule)
        }
    }
}
