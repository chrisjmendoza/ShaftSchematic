package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UndercutStripXMap] — an undercut strip's axial map: to scale until its dimension values stack
 * on each other, then every short stretch wide enough to hold its own value. The crowded job is
 * the on-device one (fixture `undercut_crowded_royal_atlantic.shaft`: seven cuts on the AFT
 * liner, 9/16" to 1 1/2" slivers at each end of a 31 15/16" one).
 */
class UndercutStripXMapTest {

    private val unit = UnitSystem.INCHES
    private val inch = 25.4f

    /** A fixed-pitch measure: 5 pt a character. */
    private val labelWidth: (DualLabel) -> Float = { it.primary.length * 5f }

    private val doc by lazy {
        ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_crowded_royal_atlantic.shaft")).readText(),
        )
    }

    private val spans: List<UndercutSpanMm> by lazy {
        val oal = doc.spec.overallLengthMm
        doc.undercutRecord.undercuts.mapNotNull { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
    }

    private val chainStart get() = spans.minOf { it.startMm }
    private val chainEnd get() = spans.maxOf { it.endMm }
    private val drawStart get() = chainStart - 2f * inch
    private val drawEnd get() = chainEnd + 2f * inch
    private val startX = 40f
    private val widthPt = 640f
    private val scale get() = widthPt / (drawEnd - drawStart)

    private fun map(s: List<UndercutSpanMm>) = planUndercutStripXMap(
        drawStart, drawEnd, startX, scale, chainStart, chainEnd, s, unit,
        dual = false, splitAtMm = emptyList(), startedStrip = false, labelWidth = labelWidth,
    )

    private fun rails(s: List<UndercutSpanMm>, xAt: (Float) -> Float) = planUndercutStripRails(
        chainStart, chainEnd, s, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
        xAt = xAt, labelWidth = labelWidth,
    )

    @Test
    fun `the linear map is the to-scale formula`() {
        val m = UndercutStripXMap.linear(100f, 900f, 40f, 0.75f)
        assertTrue(m.isLinear)
        for (mm in listOf(-50f, 100f, 333.3f, 900f, 1200f)) {
            assertEquals(40f + (mm - 100f) * 0.75f, m.xAt(mm), 0f)
            assertEquals(mm, m.mmAt(m.xAt(mm)), 1e-2f)
        }
        assertTrue(m.knotsInside(100f, 900f).isEmpty())
    }

    @Test
    fun `one short cut on a long liner stays to scale`() {
        val lone = listOf(UndercutSpanMm("a", chainStart, chainStart + 0.5625f * inch), spans.maxBy { it.endMm })
        val linear = UndercutStripXMap.linear(drawStart, drawEnd, startX, scale)
        assertFalse(undercutRailsCrowded(rails(lone, linear::xAt)))
        assertTrue(map(lone).isLinear)
    }

    @Test
    fun `the crowded job seats every value in its own line`() {
        val linear = UndercutStripXMap.linear(drawStart, drawEnd, startX, scale)
        assertTrue("to scale, the job's values stack", undercutRailsCrowded(rails(spans, linear::xAt)))

        val m = map(spans)
        assertFalse(m.isLinear)
        val fitted = rails(spans, m::xAt)
        assertFalse(undercutRailsCrowded(fitted))
        (fitted.chain + fitted.nested.flatten()).forEach { l ->
            assertTrue("${l.label.primary} seats in its line", l.seatsInBreak)
            assertTrue("${l.label.primary} keeps inward heads", l.arrowInward)
        }
    }

    @Test
    fun `the strip keeps its ends, its end stock and its order`() {
        val m = map(spans)
        val linear = UndercutStripXMap.linear(drawStart, drawEnd, startX, scale)
        // Same place on the page, same width.
        assertEquals(linear.xAt(drawStart), m.xAt(drawStart), 1e-3f)
        assertEquals(linear.xAt(drawEnd), m.xAt(drawEnd), 1e-2f)
        // The stock outside the chain draws to scale: break room and S.E.T. lead room hold.
        assertEquals(linear.xAt(chainStart), m.xAt(chainStart), 1e-2f)
        assertEquals(linear.xAt(chainEnd), m.xAt(chainEnd), 1e-2f)
        // Past the ends the map runs on at the to-scale rate.
        assertEquals(m.xAt(drawStart) - 10f * scale, m.xAt(drawStart - 10f), 1e-3f)
        // Strictly increasing, and invertible.
        var prev = Float.NEGATIVE_INFINITY
        var mm = drawStart
        while (mm <= drawEnd) {
            val x = m.xAt(mm)
            assertTrue(x > prev)
            assertEquals(mm, m.mmAt(x), 0.05f)
            prev = x
            mm += 7.3f
        }
    }

    @Test
    fun `short stretches share one width and the long one gives it up`() {
        val m = map(spans)
        fun drawn(s: UndercutSpanMm) = m.xAt(s.endMm) - m.xAt(s.startMm)
        val top = spans.sortedBy { it.startMm }
        val long = top.maxBy { it.endMm - it.startMm }
        // The 31 15/16" cut is still by far the longest thing on the strip, and narrower than to scale.
        assertTrue(drawn(long) < (long.endMm - long.startMm) * scale)
        top.filter { it !== long }.forEach { assertTrue(drawn(long) > 2f * drawn(it)) }
        // The 9/16" cut at the AFT end and the 5/8" one at the FWD end draw the same width.
        assertEquals(drawn(top.first()), drawn(top.last()), 0.01f)
    }

    @Test
    fun `floors that cannot fit are shared down, never overflowing the strip`() {
        val floors = (0 until 10).map { UndercutXFloor(100f + it * 10f, 110f + it * 10f, 80f) }
        val m = fitUndercutStripXMap(90f, 210f, 0f, 2f, 100f, 200f, floors)
        assertEquals(240f, m.xAt(210f), 1e-2f)
        assertEquals(20f, m.xAt(100f), 1e-3f)
        assertEquals(220f, m.xAt(200f), 1e-2f)
    }

    @Test
    fun `a nested stretch widens its parent`() {
        // A 100 mm parent holding a 4 mm child at its FWD end, on 1000 mm of stock.
        val floors = listOf(
            UndercutXFloor(0f, 450f, 30f), UndercutXFloor(450f, 550f, 30f), UndercutXFloor(550f, 1000f, 30f),
            UndercutXFloor(450f, 546f, 30f), UndercutXFloor(546f, 550f, 30f),
        )
        val m = fitUndercutStripXMap(0f, 1000f, 0f, 0.2f, 0f, 1000f, floors)
        assertTrue(m.xAt(550f) - m.xAt(546f) >= 30f - 1e-2f)
        assertTrue(m.xAt(546f) - m.xAt(450f) >= 30f - 1e-2f)
        assertEquals(listOf(450f, 546f, 550f), m.knotsInside(0f, 1000f))
    }
}
