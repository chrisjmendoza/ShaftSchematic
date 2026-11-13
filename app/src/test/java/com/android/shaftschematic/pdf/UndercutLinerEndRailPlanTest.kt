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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Liner ends from S.E.T." ([planUndercutStripRails] `linerEnds`): two baseline rails from the
 * strip's S.E.T. to the liner's near and far ends over an unchanged chained strip, the S.E.T. lead
 * dropped only where it would repeat one of them. The job is the on-device one (fixture
 * `undercut_crowded_royal_atlantic.shaft`): the AFT liner sits 16 1/8" from the AFT S.E.T. and runs
 * 35 1/4", and its first cut overhangs its AFT end, 15 1/2" from the S.E.T.
 */
class UndercutLinerEndRailPlanTest {

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

    private val liner by lazy {
        val l = doc.spec.liners.single { it.id == "6e96b333-30a6-4a3c-80b2-5d2aaebb15ed" }
        UndercutLinerSpanMm(l.startFromAftMm, l.startFromAftMm + l.lengthMm)
    }

    private val spans: List<UndercutSpanMm> by lazy {
        val oal = doc.spec.overallLengthMm
        doc.undercutRecord.undercuts.mapNotNull { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
    }

    // The liner strip's chain: the liner's edges, extended by the cut overhanging its AFT end.
    private val chainStart get() = minOf(liner.startMm, spans.minOf { it.startMm })
    private val chainEnd get() = maxOf(liner.endMm, spans.maxOf { it.endMm })
    private val drawStart get() = chainStart - 3f * inch
    private val drawEnd get() = chainEnd + 3f * inch
    private val startX = 40f
    private val widthPt = 1200f
    private val scale get() = widthPt / (drawEnd - drawStart)
    private fun xAt(mm: Float) = startX + (mm - drawStart) * scale

    private fun aftLead() = UndercutSetLead(UndercutAnchorSide.AFT_SET, sets.aftSETxMm.toFloat(), drawStart, drawEnd)
    private fun fwdLead() = UndercutSetLead(UndercutAnchorSide.FWD_SET, sets.fwdSETxMm.toFloat(), drawStart, drawEnd)

    private fun plan(
        lead: UndercutSetLead?,
        ends: UndercutLinerSpanMm?,
        fromSet: Boolean = false,
        cStart: Float = chainStart,
        cEnd: Float = chainEnd,
        s: List<UndercutSpanMm> = spans,
    ) = planUndercutStripRails(
        cStart, cEnd, s, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
        xAt = ::xAt, labelWidth = labelWidth, setLead = lead, fromSet = fromSet, linerEnds = ends,
    )

    private fun label(inches: Double) = formatLenDimDualLabel(inches * 25.4, unit, false)

    @Test
    fun `the AFT liner reads 16 1-8 and 51 3-8 from the AFT S-E-T`() {
        val rails = plan(aftLead(), liner)
        assertEquals(listOf(label(16.125), label(51.375)), rails.setRails.map { it.label })
        assertEquals(listOf(liner.startMm, liner.endMm), rails.setRailSpans.map { it.stationMm })
        rails.setRailSpans.forEach { r ->
            assertTrue(r.openAtSet)
            assertEquals(drawStart, r.span.startMm, 0f)
        }
        rails.setRails.forEach { assertTrue("${it.label.primary} seats in its line", it.seatsInBreak) }
        assertEquals(2, rails.rows.setTiers)
        assertEquals(2, rails.rows.setRows)
    }

    @Test
    fun `the chain, nested rows and total are the toggle-off plan's and the overhanging lead stays`() {
        val off = plan(aftLead(), null)
        val on = plan(aftLead(), liner)
        assertNotNull(off.totalSpan)
        assertEquals(off.totalSpan, on.totalSpan)
        assertEquals(off.total, on.total)
        assertEquals(off.chainSpans, on.chainSpans)
        assertEquals(off.chain, on.chain)
        assertEquals(off.nestedSpans, on.nestedSpans)
        assertEquals(off.nested, on.nested)
        // The chain starts at the cut overhanging the liner, 15 1/2" out — not a liner-end figure.
        assertEquals(off.leadSpan, on.leadSpan)
        assertEquals(label(15.5), on.leadSpan!!.span.label)
        assertEquals(off.rows.copy(setTiers = 2, setRows = 2), on.rows)
    }

    @Test
    fun `a chain starting at the liner edge drops the lead that would repeat the near-end figure`() {
        val inside = spans.filter { it.startMm >= liner.startMm }
        val on = plan(aftLead(), liner, cStart = liner.startMm, s = inside)
        val off = plan(aftLead(), null, cStart = liner.startMm, s = inside)
        assertNotNull(off.leadSpan)
        assertEquals(label(16.125), off.leadSpan!!.span.label)
        assertNull(on.leadSpan)
        assertNull(on.lead)
        assertEquals(label(16.125), on.setRails.first().label)
    }

    @Test
    fun `the FWD S-E-T mirrors - liner FWD end nearest`() {
        val rails = plan(fwdLead(), liner)
        val fwd = sets.fwdSETxMm.toFloat()
        assertEquals(listOf(liner.endMm, liner.startMm), rails.setRailSpans.map { it.stationMm })
        assertEquals(
            listOf(fwd - liner.endMm, fwd - liner.startMm).map { formatLenDimDualLabel(it.toDouble(), unit, false) },
            rails.setRails.map { it.label },
        )
        rails.setRailSpans.forEach { r ->
            assertTrue(r.openAtSet)
            assertEquals(drawEnd, r.span.endMm, 0f)
        }
        rails.setRails.forEach { assertTrue(it.openEnd); assertFalse(it.openStart) }
    }

    @Test
    fun `no liner span - a bare-shaft strip - plans exactly the chained strip`() {
        assertEquals(plan(aftLead(), null), planUndercutStripRails(
            chainStart, chainEnd, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = ::xAt, labelWidth = labelWidth, setLead = aftLead(),
        ))
        assertTrue(plan(aftLead(), null).setRails.isEmpty())
        assertTrue("no lead, no S.E.T. to measure from", plan(null, liner).setRails.isEmpty())
    }

    @Test
    fun `liner ends with From S-E-T is From S-E-T alone`() {
        assertEquals(plan(aftLead(), null, fromSet = true), plan(aftLead(), liner, fromSet = true))
    }

    @Test
    fun `the tiers stand above the total's band`() {
        val rails = plan(aftLead(), liner)
        val ys = undercutSetRailLineYs(rails, chainRailY = 500f, rowStep = 17f, tierPitch = 15f, totalRailY = 440f, scale = 2f)
        assertEquals(440f - UNDERCUT_TOTAL_RAIL_ABOVE_PT * 2f - 15f, ys[0], 1e-3f)
        assertEquals(ys[0] - 15f, ys[1], 1e-3f)
        // Without a total the tiers sit over the chain's above-line rows, the From-S.E.T. rule.
        val noTotal = planUndercutStripRails(
            chainStart, chainEnd, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = ::xAt, labelWidth = labelWidth, setLead = aftLead(), showTotal = false, linerEnds = liner,
        )
        assertNull(noTotal.totalSpan)
        val ys2 = undercutSetRailLineYs(noTotal, chainRailY = 500f, rowStep = 17f, tierPitch = 15f, totalRailY = 440f)
        assertEquals(500f - noTotal.rows.aboveRows * 17f - 15f, ys2[0], 1e-3f)
    }

    @Test
    fun `the near-end value is the room the drawn range makes`() {
        assertEquals(
            16.125f * inch,
            undercutLinerNearEndValueMm(liner, UndercutAnchorSide.AFT_SET, sets.aftSETxMm.toFloat())!!, 0.01f,
        )
    }

    @Test
    fun `a liner end at or beyond the S-E-T has no rail`() {
        val r = buildUndercutLinerEndRailSpans(
            UndercutLinerSpanMm(100f, 400f), UndercutSetLead(UndercutAnchorSide.AFT_SET, 250f, 50f, 500f), unit,
        )
        assertEquals(listOf(400f), r.map { it.stationMm })
        assertFalse(r.single().openAtSet)
    }
}
