package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UNDERCUT_SET_REACH_MM
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.undercutNestingForest
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [planUndercutStripRails] — the ONE rail plan behind the printed strip and the Undercut tab's
 * detail canvas — on the on-device job (fixture `undercut_polar_ranger_stbd.shaft`: the FWD
 * liner's five cuts, a 2 3/8" cut nested inside the 12 5/8" relief). The AFT-most cut overhangs the
 * liner's AFT edge, so the chain datum is that cut's shoulder and the chain opens on the cut itself.
 */
class UndercutStripRailPlanTest {

    private val unit = UnitSystem.INCHES

    private val doc by lazy {
        ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
        )
    }

    private val spans: List<UndercutSpanMm> by lazy {
        val oal = doc.spec.overallLengthMm
        doc.undercutRecord.undercuts.mapNotNull { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
    }

    private fun strip(reachToSetMm: Float = 0f): UndercutStrip {
        val spec = doc.spec
        val segs = surfaceSegsFrom(resolveComponents(spec))
        val liners = spec.liners
            .filter { it.lengthMm > 0f && it.odMm > 0f }
            .map { UndercutLinerSpan(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        val strips = buildUndercutStrips(
            spans, liners, spec.overallLengthMm, segs = segs,
            reachToSetMm = reachToSetMm,
            aftSetMm = sets.aftSETxMm.toFloat(), fwdSetMm = sets.fwdSETxMm.toFloat(),
        )
        assertEquals("the job's cuts read as one strip", 1, strips.size)
        return strips.single()
    }

    private val xAt: (Float) -> Float = { mm -> (mm - 9900f) * 0.8f }
    private val labelWidth: (DualLabel) -> Float = { it.inline().length * 4.64f }

    private fun plan(s: UndercutStrip, scale: Float = 1f, x: (Float) -> Float = xAt) = planUndercutStripRails(
        s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
        xAt = x, labelWidth = labelWidth, scale = scale,
    )

    @Test
    fun `the chain carries the top-level cuts, the nested cut gets its own row against its parent`() {
        val rails = plan(strip())
        assertEquals(
            listOf("1 1/4\"", "12 5/8\"", "3 11/16\"", "9 1/4\""),
            rails.chainSpans.map { it.label.inline() },
        )
        assertEquals("one nesting level", 1, rails.nestedSpans.size)
        assertEquals(
            listOf("5.080\"", "2 3/8\"", "5.170\""),
            rails.nestedSpans.single().map { it.label.inline() },
        )
        // The nested row is located against the parent's shoulders: it spans the parent exactly.
        val parent = spans.single { kotlin.math.abs(it.endMm - it.startMm - 320.675f) < 1e-2f }
        val row = rails.nestedSpans.single()
        assertEquals(parent.startMm, row.first().startMm, 1e-3f)
        assertEquals(parent.endMm, row.last().endMm, 1e-3f)
        assertNotNull("five cuts carry a total", rails.totalSpan)
        assertEquals(1, rails.total.size)
        assertEquals("a total rides above the chain, so fallbacks tuck below", 0, rails.rows.aboveRows)
        assertTrue("the nested row claims reserved rows", rails.rows.nestedRows >= 1)
    }

    @Test
    fun `with the draw-to-S E T reach the chain closes on the run to the S E T`() {
        val rails = plan(strip(UNDERCUT_SET_REACH_MM))
        assertEquals(
            listOf("1 1/4\"", "12 5/8\"", "3 11/16\"", "9 1/4\"", "12 3/4\""),
            rails.chainSpans.map { it.label.inline() },
        )
    }

    @Test
    fun `at scale 1 the plan is the composer's former inline sequence exactly`() {
        val s = strip()
        val rails = plan(s)
        // The composer's sequence before the planner existed, verbatim.
        val sorted = spans.sortedBy { it.startMm }
        val nestingById = undercutNestingForest(sorted).associateBy { it.id }
        val topSpans = sorted.filter { (nestingById[it.id]?.level ?: 0) == 0 }
        val railSpans = buildUndercutRailSpans(s.chainStartMm, s.chainEndMm, topSpans, unit, false, splitAtMm = emptyList())
        val totalSpan = buildUndercutTotalSpan(topSpans, unit, false)
        val nestedRows = buildNestedUndercutRailRows(sorted, unit, false)
        val chain = layoutWearStripRail(railSpans, xAtStripMm = xAt, labelWidthPt = labelWidth)
        val nested = nestedRows.map { layoutWearStripRail(it, xAtStripMm = xAt, labelWidthPt = labelWidth) }
        val total = layoutWearStripRail(listOf(totalSpan!!), xAtStripMm = xAt, labelWidthPt = labelWidth)
        val rows = planUndercutRailRows(chain, false, hasTotalRail = true, nestedLayouts = nested)

        assertEquals(railSpans, rails.chainSpans)
        assertEquals(nestedRows, rails.nestedSpans)
        assertEquals(totalSpan, rails.totalSpan)
        assertEquals(chain, rails.chain)
        assertEquals(nested, rails.nested)
        assertEquals(total, rails.total)
        assertEquals(rows, rails.rows)
    }

    @Test
    fun `a scaled plan makes the page's seating and arrow decisions at its own size`() {
        val k = 3.3f
        val page = plan(strip())
        // Everything — the x map and every label — k times larger: the same decisions, k times the x.
        val canvas = planUndercutStripRails(
            strip().chainStartMm, strip().chainEndMm, spans, unit, false, emptyList(), false,
            xAt = { mm -> xAt(mm) * k }, labelWidth = { labelWidth(it) * k }, scale = k,
        )
        assertEquals(page.rows, canvas.rows)
        (page.chain + page.nested.flatten() + page.total).zip(canvas.chain + canvas.nested.flatten() + canvas.total)
            .forEach { (p, c) ->
                assertEquals(p.seatsInBreak, c.seatsInBreak)
                assertEquals(p.arrowInward, c.arrowInward)
                assertEquals(p.labelRow, c.labelRow)
                assertEquals(p.labelCxPt * k, c.labelCxPt, 1e-2f)
            }
    }

    @Test
    fun `a started strip dimensions nothing`() {
        val s = strip()
        val rails = planUndercutStripRails(
            s.chainStartMm, s.chainEndMm, spans, unit, false, emptyList(), startedStrip = true,
            xAt = xAt, labelWidth = labelWidth,
        )
        assertTrue(rails.chainSpans.isEmpty())
        assertTrue(rails.nested.isEmpty())
        assertNull(rails.totalSpan)
        assertTrue(rails.total.isEmpty())
        assertEquals(WEAR_RAIL_MAX_LABEL_ROWS, rails.rows.belowRows)
        assertFalse(rails.rows.aboveRows > 0)
    }
}
