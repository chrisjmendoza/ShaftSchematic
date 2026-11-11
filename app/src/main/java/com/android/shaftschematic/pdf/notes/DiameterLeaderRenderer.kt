package com.android.shaftschematic.pdf.notes

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.geom.DiameterCalloutLayout
import com.android.shaftschematic.geom.formatBlendAngleDeg
import com.android.shaftschematic.pdf.formatDiaWithUnitDualLabel
import com.android.shaftschematic.pdf.formatLenWithUnitDualLabel
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
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
 * BELOW-side callouts that would collide are moved to a second row, or hung on the other side of
 * their leader, by [DiameterCalloutLayout.place]. ABOVE-side
 * callouts (none produced today) use plain single-row geometry — no tiering.
 *
 * An Angle-mode blend's angle and corner radius ([CalloutKind.ANGLE], ONE callout for the pair)
 * ride the SAME pass — same rows, same occupancy, same leader construction — so they share the
 * one below-shaft collision space with the Ø values and the component names. Their leader leaves
 * from the feature itself ([DiaCallout.anchorXPt]/[DiaCallout.anchorYPt]).
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
    /** The page x [call]'s leader stands at: its feature's anchor when it has one, else its station. */
    private fun anchorXFor(call: DiaCallout): Float = call.anchorXPt ?: pageX(call.xMm)

    /**
     * The page-x range [call]'s leader may slide over to clear a neighbour: its component's span,
     * held [ANCHOR_END_INSET_PT] in from each end so the leader never stands on a face line or a
     * blend. A feature's leader, and a diameter with no span, stays on its anchor.
     */
    private fun anchorRangeFor(call: DiaCallout, anchor: Float): Pair<Float, Float> {
        val start = call.spanStartMm
        val end = call.spanEndMm
        if (call.anchorXPt != null || start == null || end == null) return anchor to anchor
        val a = pageX(start)
        val b = pageX(end)
        val lo = minOf(a, b) + ANCHOR_END_INSET_PT
        val hi = maxOf(a, b) - ANCHOR_END_INSET_PT
        return if (lo >= hi) anchor to anchor else minOf(lo, anchor) to maxOf(hi, anchor)
    }

    /** Where a BELOW leader leaves from: its feature's outline when it has one, else the shaft's bottom edge. */
    private fun belowStartYFor(call: DiaCallout): Float = call.anchorYPt ?: shaftBottomY

    /**
     * Row and label side of every BELOW callout — ONE answer for occupancy and ink, placed
     * together so no leader drops through another callout's value ([DiameterCalloutLayout.place]).
     */
    private fun belowPlacements(below: List<DiaCallout>): List<DiameterCalloutLayout.Placement> =
        DiameterCalloutLayout.place(
            items = below.map { call ->
                val anchor = anchorXFor(call)
                val (lo, hi) = anchorRangeFor(call, anchor)
                DiameterCalloutLayout.Item(
                    anchor, labelWidth(call), feature = call.anchorXPt != null,
                    anchorMin = lo, anchorMax = hi,
                )
            },
            dogleg = leaderDogleg,
            contentLeft = contentLeft,
            contentRight = contentRight,
        )

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

    /**
     * How far a BELOW label's first baseline sits under its leader's kink: [TEXT_DROP_MIN_PT], or
     * one text size once the text is larger (`PdfPrefs.dimTextScale`), so a larger value still
     * hangs below the dogleg rather than rising across it.
     */
    private val textDrop: Float
        get() = maxOf(TEXT_DROP_MIN_PT, textPaint.textSize)

    fun draw(canvas: Canvas, calls: List<DiaCallout>) {
        val below = calls.filter { it.side == LeaderSide.BELOW }
        val placements = belowPlacements(below)
        below.forEachIndexed { i, call ->
            drawOne(canvas, call, placements[i].tier, placements[i].labelLeft, placements[i].anchorX)
        }

        calls.filter { it.side == LeaderSide.ABOVE }
            .forEach { drawOne(canvas, it, tier = 0, textX = anchorXFor(it) + leaderDogleg, x = anchorXFor(it)) }
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
        val placements = belowPlacements(below)
        val fm = textPaint.fontMetrics
        val textH = if (dualStacked) textPaint.dualStackMetrics().height else fm.descent - fm.ascent
        // A stack's box hangs BELOW its first baseline; a single line's box is ascent-to-descent
        // around it. Either way the top is the first line's ascent above the baseline.
        val above = -fm.ascent

        return buildList {
            below.forEachIndexed { i, call ->
                val x = placements[i].anchorX
                val kinkY = shaftBottomY + leaderRise + placements[i].tier * tierStep
                val textY = kinkY + textDrop
                val textLeft = placements[i].labelLeft
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
                        top = belowStartYFor(call),
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
    private fun label(call: DiaCallout): DualLabel = when (call.kind) {
        CalloutKind.DIAMETER -> {
            if (blankValues) DualLabel.single("Ø")
            else formatDiaWithUnitDualLabel(call.valueMm, call.unit, call.dual).let { it.copy(primary = "Ø " + it.primary) }
        }
        // A blank draft keeps the identifier and leaves the number to be written in, as for Ø.
        CalloutKind.ANGLE -> when {
            blankValues -> DualLabel.single(if (call.valueMm > 0.0) "° R" else "°")
            call.valueMm > 0.0 -> blendAngleRadiusCalloutLabel(call.angleDeg, call.valueMm, call.unit, call.dual)
            else -> DualLabel.single(blendAngleCalloutText(call.angleDeg))
        }
    }

    private fun labelWidth(call: DiaCallout): Float =
        textPaint.measureDualLabel(label(call), dualStacked) +
            if (blankValues) 4f + blankRuleWidth else 0f

    private fun drawOne(canvas: Canvas, call: DiaCallout, tier: Int, textX: Float, x: Float) {
        val (startY, kinkY, textY) = when (call.side) {
            LeaderSide.ABOVE -> {
                val s = shaftTopY
                Triple(s, s - leaderRise, s - leaderRise - 2f)
            }
            LeaderSide.BELOW -> {
                // The kink and the text keep the shaft's bottom edge as their datum, so a feature
                // leader's label sits in the same tier rows as every Ø value; only the line's
                // start moves up onto the feature.
                val depth = leaderRise + tier * tierStep
                Triple(belowStartYFor(call), shaftBottomY + depth, shaftBottomY + depth + textDrop)
            }
        }
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

/**
 * An Angle-mode blend's angle as its callout prints it: the STORED angle, trailing zeros trimmed,
 * with a degree sign — `45°`, `30.5°`. Unitless, so it never takes a dual secondary.
 */
fun blendAngleCalloutText(angleDeg: Float): String = formatBlendAngleDeg(angleDeg) + "°"

/**
 * An Angle-mode blend's corner radius as its callout prints it: the STORED radius as a length with
 * an `R` — inches set as a fraction to 1/32 like every printed length (`3/8" R`), millimetres
 * compact (`10 mm R`). The `R` rides the primary, so a dual value reads `3/8" R [9.5 mm]`.
 */
fun blendRadiusCalloutLabel(radiusMm: Double, unit: UnitSystem, dual: Boolean): DualLabel =
    formatLenWithUnitDualLabel(radiusMm, unit, dual).let { it.copy(primary = it.primary + " R") }

/**
 * An Angle-mode blend's ONE callout when it has a corner radius: the angle, then the radius —
 * `45°, 3/8" R`. One leader for the pair: the chamfer and its round are a point or two apart on
 * paper, so a leader each drew as a doubled line with the two values printed on top of each other
 * (on-device report).
 */
fun blendAngleRadiusCalloutLabel(angleDeg: Float, radiusMm: Double, unit: UnitSystem, dual: Boolean): DualLabel =
    blendRadiusCalloutLabel(radiusMm, unit, dual).let {
        it.copy(primary = blendAngleCalloutText(angleDeg) + ", " + it.primary)
    }

/** How far inside its component's ends a slid Ø leader stays. */
private const val ANCHOR_END_INSET_PT = 6f

/** Half-width reserved around a leader line, so a neighbouring label clears the stroke itself. */
private const val LEADER_HALF_W = 1f

/** The baseline drop under the kink at the shipped 10 pt callout size and below. */
private const val TEXT_DROP_MIN_PT = 10f
