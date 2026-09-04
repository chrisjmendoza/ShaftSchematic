package com.android.shaftschematic.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.notes.DiameterLeaderRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two below-shaft passes, measured against each other with real text metrics.
 *
 * The Ø callouts and the component-name labels both anchor on a component's CENTER, so on the
 * reported sheet a liner printed "AFT Liner" straight through its own "Ø 7.936"" (on-device
 * report). They share ONE collision space now: the callouts' [DiameterLeaderRenderer.occupancy]
 * boxes are obstacles to the label planner, and nothing either pass draws may overlap the other.
 *
 * [BelowShaftLabelLayoutTest] pins the placement rules on synthetic geometry; this test pins the
 * wiring — that the boxes the renderer reserves are the boxes the planner is handed, at the real
 * measured widths, across the compression settings that move the labels around.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentLabelCalloutClearanceTest {

    private val shaftBottomY = 300f
    private val geomRect = RectF(36f, 60f, 756f, 480f)

    private fun textPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        color = Color.BLACK
    }

    private fun leaderPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.BLACK
    }

    /**
     * The reported shaft: a 150 3/4" body with a named liner at each end, every one of them
     * printing both its name and its Ø.
     */
    private fun spec(): ShaftSpec {
        val oal = 3829.05f   // 150 3/4"
        return ShaftSpec(
            overallLengthMm = oal,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = oal, diaMm = 190f, showDiaOnDrawing = true),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 0f, lengthMm = 298.45f, odMm = 201.6f, label = "AFT Liner"),
                Liner(id = "l2", startFromAftMm = oal - 449.3f, lengthMm = 449.3f, odMm = 203.2f, label = "FWD Liner"),
            ),
        )
    }

    /** mm → page x at [ptPerMm], the way a compressed map narrows the drawing. */
    private fun xMap(spec: ShaftSpec, widthPt: Float): (Float) -> Float {
        val ptPerMm = widthPt / spec.overallLengthMm
        return { mm -> geomRect.left + mm * ptPerMm }
    }

    private fun overlaps(a: BelowShaftLabelLayout.Box, b: BelowShaftLabelLayout.Box): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /**
     * Plans the sheet's labels against its callouts and returns every overlap found, as readable
     * failure text.
     */
    private fun collisions(widthPt: Float, dualStacked: Boolean = false, blank: Boolean = false): List<String> {
        val spec = spec()
        val xAt = xMap(spec, widthPt)
        val calls = buildBodyOdCallouts(spec.bodies) + buildLinerOdCallouts(spec.liners)
        assertTrue("the fixture must actually print callouts", calls.size >= 2)

        val leader = DiameterLeaderRenderer(
            pageX = { mm -> xAt(mm.toFloat()) },
            shaftTopY = shaftBottomY - 90f,
            shaftBottomY = shaftBottomY,
            linePaint = Paint(),
            textPaint = leaderPaint(),
            blankValues = blank,
            dualStacked = dualStacked,
        )
        val reserved = leader.occupancy(calls)

        val labelPaint = Paint(textPaint()).apply { textSize = 10f }
        val spans = componentLabelSpans(spec, titlesDefault = true)
        assertTrue("the fixture must actually print names", spans.size >= 2)

        val plan = planComponentLabels(
            spans = spans,
            labelPaint = labelPaint,
            geomRect = geomRect,
            baseY = shaftBottomY + 32f,
            xAt = xAt,
            reserved = reserved,
        )
        val fm = labelPaint.fontMetrics

        val out = mutableListOf<String>()
        plan.placements.forEachIndexed { i, p ->
            val baseline = shaftBottomY + 32f + p.row * plan.rowStep
            val box = BelowShaftLabelLayout.Box(
                left = p.left,
                right = p.left + labelPaint.measureText(spans[i].text),
                top = baseline + fm.ascent,
                bottom = baseline + fm.descent,
            )
            reserved.forEach { o ->
                if (overlaps(box, o)) out += "\"${spans[i].text}\" at $box overlaps callout $o"
            }
        }
        return out
    }

    @Test
    fun `a name label never overlaps a diameter callout at full width`() {
        val hits = collisions(widthPt = 700f)
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `a name label never overlaps a diameter callout on a compressed sheet`() {
        // Sweep the widths a compressed x map produces — the labels bunch up as the drawing
        // narrows, which is exactly when a per-pass collision space goes blind.
        val hits = (160..700 step 20).flatMap { collisions(widthPt = it.toFloat()) }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `a name label never overlaps a stacked dual callout`() {
        // A stacked dual value is two lines tall, so its box reaches further down the band.
        val hits = (200..700 step 50).flatMap { collisions(widthPt = it.toFloat(), dualStacked = true) }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `a name label never overlaps a blank draft's write-in rule`() {
        val hits = (200..700 step 50).flatMap { collisions(widthPt = it.toFloat(), blank = true) }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `the reported sheet's liner name slides instead of dropping out of its band`() {
        val spec = spec()
        val xAt = xMap(spec, 700f)
        val leader = DiameterLeaderRenderer(
            pageX = { mm -> xAt(mm.toFloat()) },
            shaftTopY = shaftBottomY - 90f,
            shaftBottomY = shaftBottomY,
            linePaint = Paint(),
            textPaint = leaderPaint(),
        )
        val reserved = leader.occupancy(buildBodyOdCallouts(spec.bodies) + buildLinerOdCallouts(spec.liners))

        val plan = planComponentLabels(
            spans = componentLabelSpans(spec, titlesDefault = true),
            labelPaint = Paint(textPaint()).apply { textSize = 10f },
            geomRect = geomRect,
            baseY = shaftBottomY + 32f,
            xAt = xAt,
            reserved = reserved,
        )

        assertTrue("every name found a clear placement", plan.placements.all { it.fitted })
        assertFalse("a slide costs no vertical room", plan.placements.any { it.row > 1 })
    }
}
