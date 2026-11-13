package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The From-S.E.T. baseline rails ([buildUndercutSetRailSpans] through [planUndercutStripRails]
 * `fromSet`): one rail per cut shoulder from the strip's S.E.T., nearest lowest, over the chain that
 * still carries the cut lengths. The job is the on-device one (fixture
 * `undercut_crowded_royal_atlantic.shaft`), whose hand sketch dimensions every cut's start from the
 * AFT S.E.T.: 15 1/2, 16 1/16, 18 13/16, 50 3/4, 51 5/16, 51 7/8, 53 3/8 and 54 to the far end.
 */
class UndercutSetRailPlanTest {

    private val unit = UnitSystem.INCHES
    private val inch = 25.4f

    /** A fixed-pitch measure: 5 pt a character. */
    private val labelWidth: (DualLabel) -> Float = { it.primary.length * 5f }

    private val doc by lazy {
        ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_crowded_royal_atlantic.shaft")).readText(),
        )
    }

    private val sets by lazy { computeSetPositionsInMeasureSpace(computeOalWindow(doc.spec), doc.spec) }

    private val spans: List<UndercutSpanMm> by lazy {
        val oal = doc.spec.overallLengthMm
        doc.undercutRecord.undercuts.mapNotNull { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
    }

    private val chainStart get() = spans.minOf { it.startMm } - 1f * inch
    private val chainEnd get() = spans.maxOf { it.endMm } + 1f * inch
    private val drawStart get() = chainStart - 3f * inch
    private val drawEnd get() = chainEnd + 3f * inch
    private val startX = 40f
    private val widthPt = 1200f
    private val scale get() = widthPt / (drawEnd - drawStart)
    private fun xAt(mm: Float) = startX + (mm - drawStart) * scale

    private fun aftLead(from: Float = drawStart, to: Float = drawEnd) =
        UndercutSetLead(UndercutAnchorSide.AFT_SET, sets.aftSETxMm.toFloat(), from, to)

    private fun fwdLead(from: Float = drawStart, to: Float = drawEnd) =
        UndercutSetLead(UndercutAnchorSide.FWD_SET, sets.fwdSETxMm.toFloat(), from, to)

    private fun plan(lead: UndercutSetLead?, fromSet: Boolean, s: List<UndercutSpanMm> = spans) = planUndercutStripRails(
        chainStart, chainEnd, s, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
        xAt = ::xAt, labelWidth = labelWidth, setLead = lead, fromSet = fromSet,
    )

    private fun label(inches: Double) = formatLenDimDualLabel(inches * 25.4, unit, false)

    @Test
    fun `the AFT S-E-T rails are the hand sketch's values, nearest first`() {
        val rails = plan(aftLead(), fromSet = true)
        val expected = listOf(15.5, 16.0625, 18.8125, 50.75, 51.3125, 51.875, 53.375, 54.0).map(::label)
        assertEquals(expected, rails.setRailSpans.map { it.span.label })
        assertEquals(expected, rails.setRails.map { it.label })
        assertEquals((0 until 8).toList(), rails.setRailSpans.map { it.tier })
        // Off the strip, every rail runs open from the AFT drawn edge to its station.
        rails.setRailSpans.forEach { r ->
            assertTrue(r.openAtSet)
            assertEquals(drawStart, r.span.startMm, 0f)
            assertEquals(r.stationMm, r.span.endMm, 0f)
        }
        rails.setRails.forEach { l ->
            assertTrue(l.openStart)
            assertFalse(l.openEnd)
            assertTrue("${l.label.primary} seats in its own line", l.seatsInBreak)
        }
    }

    @Test
    fun `the total and the lead drop out and the chain is untouched`() {
        val chained = plan(aftLead(), fromSet = false)
        val fromSet = plan(aftLead(), fromSet = true)
        assertTrue("the chained strip carries a total", chained.totalSpan != null)
        assertTrue("the chained strip carries a lead", chained.lead != null)
        assertNull(fromSet.totalSpan)
        assertTrue(fromSet.total.isEmpty())
        assertNull(fromSet.leadSpan)
        assertNull(fromSet.lead)
        assertFalse(fromSet.canLiftFallbacks)
    }

    @Test
    fun `a cut holding a nested cut is dimensioned as its floor pieces on the one chain`() {
        // The job's 1 1/8" cut holds a 9/16" cut at its FWD end.
        val chained = plan(aftLead(), fromSet = false)
        val fromSet = plan(aftLead(), fromSet = true)
        assertTrue(chained.chain.any { it.label == label(1.125) })
        assertEquals(1, chained.nested.size)
        assertFalse(fromSet.chain.any { it.label == label(1.125) })
        assertEquals(
            chained.chain.count { it.label == label(0.5625) } + 2,
            fromSet.chain.count { it.label == label(0.5625) },
        )
        assertTrue(fromSet.nested.isEmpty())
        assertTrue(fromSet.nestedSpans.isEmpty())
        // The chain still sums to its own length, and every station keeps its rail.
        assertEquals(chained.chainSpans.sumOf { (it.endMm - it.startMm).toDouble() },
            fromSet.chainSpans.sumOf { (it.endMm - it.startMm).toDouble() }, 1e-2)
        assertEquals(chained.chainSpans.size + 1, fromSet.chainSpans.size)
        assertEquals(8, fromSet.setRailSpans.size)
    }

    @Test
    fun `fromSet off plans exactly the chained strip`() {
        val off = plan(aftLead(), fromSet = false)
        val default = planUndercutStripRails(
            chainStart, chainEnd, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = ::xAt, labelWidth = labelWidth, setLead = aftLead(),
        )
        assertEquals(default, off)
        assertTrue(off.setRails.isEmpty())
        assertEquals(0, off.rows.setTiers)
        assertEquals(0, off.rows.setRows)
    }

    @Test
    fun `no lead means no baseline rails`() {
        val rails = plan(null, fromSet = true)
        assertTrue(rails.setRails.isEmpty())
        assertEquals(plan(null, fromSet = false), rails)
    }

    @Test
    fun `the FWD S-E-T mirrors - FWD shoulders plus the AFT-most cut's start`() {
        val rails = plan(fwdLead(), fromSet = true)
        val fwd = sets.fwdSETxMm.toFloat()
        val stations = (spans.map { it.endMm } + spans.minOf { it.startMm })
            .map { fwd - it }.sorted()
            .fold(mutableListOf<Float>()) { acc, d -> if (acc.isEmpty() || d - acc.last() > 1e-2f) acc += d; acc }
        assertEquals(stations.map { formatLenDimDualLabel(it.toDouble(), unit, false) }, rails.setRails.map { it.label })
        rails.setRailSpans.forEach { r ->
            assertTrue(r.openAtSet)
            assertEquals(drawEnd, r.span.endMm, 0f)
            assertEquals(r.stationMm, r.span.startMm, 0f)
        }
        rails.setRails.forEach { l ->
            assertFalse(l.openStart)
            assertTrue(l.openEnd)
        }
        // Nearest the FWD S.E.T. is the FWD-most cut's end.
        assertEquals(spans.maxOf { it.endMm }, rails.setRailSpans.first().stationMm, 0f)
        assertEquals(spans.minOf { it.startMm }, rails.setRailSpans.last().stationMm, 0f)
    }

    @Test
    fun `an S-E-T on the drawn strip starts every rail at it with a head`() {
        val set = sets.aftSETxMm.toFloat()
        val rails = plan(aftLead(from = set - 2f * inch), fromSet = true)
        assertTrue(rails.setDatumOnStrip)
        rails.setRailSpans.forEach { r ->
            assertFalse(r.openAtSet)
            assertEquals(set, r.span.startMm, 0f)
        }
        rails.setRails.forEach { assertFalse(it.openStart || it.openEnd) }
        assertEquals(xAt(set), rails.setDatumX!!, 1e-3f)
    }

    @Test
    fun `a nested cut sharing its parent's shoulder is one rail`() {
        val parent = UndercutSpanMm("p", 1000f, 1100f)
        val child = UndercutSpanMm("c", 1000f, 1040f)
        val other = UndercutSpanMm("o", 1200f, 1250f)
        val r = buildUndercutSetRailSpans(listOf(parent, child, other), UndercutSetLead(UndercutAnchorSide.AFT_SET, 0f, 900f, 1400f), unit)
        // 1000 (parent = child), 1200, and the far end 1250.
        assertEquals(listOf(1000f, 1200f, 1250f), r.map { it.stationMm })
        assertEquals(listOf(1000f, 1200f, 1250f), r.map { it.valueMm })
    }

    @Test
    fun `a station on the far side of its S-E-T has no rail`() {
        val r = buildUndercutSetRailSpans(
            listOf(UndercutSpanMm("a", 100f, 150f), UndercutSpanMm("b", 300f, 350f)),
            UndercutSetLead(UndercutAnchorSide.AFT_SET, 200f, 50f, 400f), unit,
        )
        assertEquals(listOf(300f, 350f), r.map { it.stationMm })
        assertTrue(r.none { it.openAtSet })
    }

    @Test
    fun `the tier count drives the reserved rows`() {
        val rails = plan(aftLead(), fromSet = true)
        assertEquals(8, rails.rows.setTiers)
        assertEquals(8 + rails.setRails.count { !it.seatsInBreak }, rails.rows.setRows)
        assertEquals(undercutSetRailRows(rails.setRails), rails.rows.setRows)
        val ys = undercutSetRailLineYs(rails, chainRailY = 500f, rowStep = 17f, tierPitch = 15f)
        // Tier 0 just above the chain's own above-line rows; one pitch per tier, strictly climbing.
        assertEquals(500f - rails.rows.aboveRows * 17f - 15f, ys.first(), 1e-3f)
        ys.zipWithNext().forEach { (lo, hi) -> assertTrue(hi < lo) }
        assertEquals(500f - rails.rows.aboveRows * 17f - rails.rows.setRows * 15f, ys.last(), 1e-3f)
    }

    @Test
    fun `a tier too short for its value falls back above its line and reserves a row`() {
        // A tight strip: the AFT drawn edge right against the first cut.
        val first = spans.minOf { it.startMm }
        val tight = planUndercutStripRails(
            first, chainEnd, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = ::xAt, labelWidth = labelWidth,
            setLead = UndercutSetLead(UndercutAnchorSide.AFT_SET, sets.aftSETxMm.toFloat(), first - 0.2f * inch, drawEnd),
            fromSet = true,
        )
        val tier0 = tight.setRails.first()
        assertFalse(tier0.seatsInBreak)
        assertEquals(tight.setRails.size + tight.setRails.count { !it.seatsInBreak }, tight.rows.setRows)
        assertTrue(tight.rows.setRows > tight.setRails.size)
    }

    @Test
    fun `the nearest value is the one the drawn range makes room for`() {
        val v = undercutSetRailNearestValueMm(spans, UndercutAnchorSide.AFT_SET, sets.aftSETxMm.toFloat())
        assertEquals(15.5f * inch, v!!, 0.01f)
    }
}
