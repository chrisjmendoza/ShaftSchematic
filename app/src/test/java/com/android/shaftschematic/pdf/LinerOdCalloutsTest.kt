package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.pdf.notes.LeaderSide
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinerOdCalloutsTest {

    // ── Empty / zero-OD cases ─────────────────────────────────────────────────

    @Test
    fun `no liners returns empty list`() {
        assertTrue(buildLinerOdCallouts(emptyList()).isEmpty())
    }

    @Test
    fun `liner with zero OD is skipped`() {
        val liners = listOf(Liner(startFromAftMm = 0f, lengthMm = 300f, odMm = 0f))
        assertTrue(buildLinerOdCallouts(liners).isEmpty())
    }

    // ── Single liner ──────────────────────────────────────────────────────────

    @Test
    fun `single liner produces one callout below at its center`() {
        val liner = Liner(startFromAftMm = 200f, lengthMm = 300f, odMm = 152f)
        val calls = buildLinerOdCallouts(listOf(liner))

        assertEquals(1, calls.size)
        assertEquals(LeaderSide.BELOW, calls[0].side)
        assertEquals(152.0, calls[0].valueMm, 0.001)
        // Center = 200 + 300/2 = 350
        assertEquals(350.0, calls[0].xMm, 0.001)
    }

    // ── Same OD groups to one; distinct ODs each get their own ────────────────

    @Test
    fun `two liners same OD produce one callout at the longer liner center`() {
        val short = Liner(startFromAftMm = 0f,   lengthMm = 100f, odMm = 152f)
        val long  = Liner(startFromAftMm = 200f, lengthMm = 500f, odMm = 152f)
        val calls = buildLinerOdCallouts(listOf(short, long))

        assertEquals(1, calls.size)
        // Center of longer liner: 200 + 250 = 450
        assertEquals(450.0, calls[0].xMm, 0.001)
    }

    @Test
    fun `two distinct ODs produce two callouts both below`() {
        val l1 = Liner(startFromAftMm = 0f,   lengthMm = 300f, odMm = 140f)
        val l2 = Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 165f)
        val calls = buildLinerOdCallouts(listOf(l1, l2))

        assertEquals(2, calls.size)
        assertTrue(calls.all { it.side == LeaderSide.BELOW })
        val diameters = calls.map { it.valueMm }.toSet()
        assertTrue(diameters.contains(140.0))
        assertTrue(diameters.contains(165.0))
    }

    // ── Bodies and liners are separate groups — no cross-group dedup ───────────

    @Test
    fun `body OD equal to liner OD yields two callouts across the builders`() {
        // Body callouts are opt-in — shown explicitly so this pins the separate-group rule.
        val bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 127f, showDiaOnDrawing = true))
        val liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 127f))

        val combined = buildBodyOdCallouts(bodies) + buildLinerOdCallouts(liners)

        assertEquals(2, combined.size)
        assertTrue(combined.all { it.valueMm == 127.0 })
        assertTrue(combined.all { it.side == LeaderSide.BELOW })
    }

    // ── Per-liner Ø visibility (showDiaOnDrawing) ─────────────────────────────

    @Test
    fun `hidden liner produces no callout`() {
        val liner = Liner(startFromAftMm = 100f, lengthMm = 400f, odMm = 140f, showDiaOnDrawing = false)
        assertTrue(buildLinerOdCallouts(listOf(liner)).isEmpty())
    }

    @Test
    fun `hiding the longer liner moves the anchor to the visible one`() {
        val hidden = Liner(startFromAftMm = 0f,   lengthMm = 500f, odMm = 140f, showDiaOnDrawing = false)
        val shown  = Liner(startFromAftMm = 600f, lengthMm = 100f, odMm = 140f)
        val calls = buildLinerOdCallouts(listOf(hidden, shown))

        assertEquals(1, calls.size)
        assertEquals(650.0, calls[0].xMm, 0.001)
    }

    @Test
    fun `hiding a liner never hides a body sharing its OD`() {
        val bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 127f, showDiaOnDrawing = true))
        val liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 127f, showDiaOnDrawing = false))

        val combined = buildBodyOdCallouts(bodies) + buildLinerOdCallouts(liners)

        assertEquals(1, combined.size)
        assertEquals(150.0, combined[0].xMm, 0.001)
    }

    @Test
    fun `liners are visible by default`() {
        assertTrue(Liner().showDiaOnDrawing)
    }

    // ── joinSharedOdCallouts: a liner and a touching body of its Ø share ONE value ──────────

    private fun body(id: String, start: Float, len: Float, dia: Float, shown: Boolean = true) =
        Body(id = id, startFromAftMm = start, lengthMm = len, diaMm = dia, showDiaOnDrawing = shown)

    private fun liner(id: String, start: Float, len: Float, od: Float) =
        Liner(id = id, startFromAftMm = start, lengthMm = len, odMm = od)

    @Test
    fun `a liner and a touching body of the same diameter print one value with two leaders`() {
        val bodies = listOf(body("b", 0f, 400f, 247.65f), body("c", 600f, 300f, 200f))
        val liners = listOf(liner("l", 400f, 200f, 247.65f))

        val calls = joinSharedOdCallouts(bodies, liners)

        assertEquals("the 200 body keeps its own callout; the shared Ø prints once", 2, calls.size)
        val joined = calls.single { it.valueMm == 247.65f.toDouble() }
        // First leader: the liner (centre + span), as the liner builder sets it.
        assertEquals(500.0, joined.xMm, 0.001)
        assertEquals(400.0, joined.spanStartMm!!, 0.001)
        assertEquals(600.0, joined.spanEndMm!!, 0.001)
        // Second leader: the touching body.
        assertEquals(200.0, joined.secondXMm!!, 0.001)
        assertEquals(0.0, joined.secondSpanStartMm!!, 0.001)
        assertEquals(400.0, joined.secondSpanEndMm!!, 0.001)
        assertEquals(LeaderSide.BELOW, joined.side)
        assertNull(calls.single { it.valueMm == 200.0 }.secondXMm)
    }

    @Test
    fun `a body touching the liner's FWD end joins too, and the longest pair wins`() {
        val bodies = listOf(body("short", 600f, 50f, 247.65f), body("long", 900f, 500f, 247.65f))
        val liners = listOf(liner("l1", 400f, 200f, 247.65f), liner("l2", 1400f, 300f, 247.65f))

        val joined = joinSharedOdCallouts(bodies, liners).single()

        // l2 (300 long) touches "long" at 1400; l1 (200) only touches "short". The longest liner wins.
        assertEquals(1550.0, joined.xMm, 0.001)
        assertEquals(1150.0, joined.secondXMm!!, 0.001)
    }

    @Test
    fun `equal diameters that do not touch keep two callouts as before`() {
        val bodies = listOf(body("b", 0f, 300f, 127f))
        val liners = listOf(liner("l", 400f, 200f, 127f))

        assertEquals(
            buildBodyOdCallouts(bodies) + buildLinerOdCallouts(liners),
            joinSharedOdCallouts(bodies, liners),
        )
    }

    @Test
    fun `different display units keep two callouts`() {
        val bodies = listOf(body("b", 0f, 400f, 127f))
        val liners = listOf(liner("l", 400f, 200f, 127f))
        val units = DisplayUnits(UnitSystem.INCHES, overrides = mapOf("l" to UnitSystem.MILLIMETERS))

        val calls = joinSharedOdCallouts(bodies, liners, units)

        assertEquals(2, calls.size)
        assertTrue(calls.all { it.secondXMm == null })
    }

    @Test
    fun `a hidden body leaves the liner's callout alone with one leader`() {
        val bodies = listOf(body("b", 0f, 400f, 127f, shown = false))
        val liners = listOf(liner("l", 400f, 200f, 127f))

        val calls = joinSharedOdCallouts(bodies, liners)

        assertEquals(1, calls.size)
        assertEquals(500.0, calls[0].xMm, 0.001)
        assertNull(calls[0].secondXMm)
    }

    @Test
    fun `with no equal pair the list is the two builders' list unchanged`() {
        val bodies = listOf(body("b1", 0f, 400f, 190f), body("b2", 600f, 300f, 180f))
        val liners = listOf(liner("l1", 400f, 200f, 201.6f), liner("l2", 900f, 100f, 203.2f))

        assertEquals(
            buildBodyOdCallouts(bodies) + buildLinerOdCallouts(liners),
            joinSharedOdCallouts(bodies, liners),
        )
    }

    @Test
    fun `two liners or two bodies of one diameter never join each other`() {
        val liners = listOf(liner("l1", 0f, 200f, 127f), liner("l2", 200f, 200f, 127f))
        val bodies = listOf(body("b1", 0f, 200f, 127f), body("b2", 200f, 200f, 127f))

        assertTrue(joinSharedOdCallouts(emptyList(), liners).all { it.secondXMm == null })
        assertTrue(joinSharedOdCallouts(bodies, emptyList()).all { it.secondXMm == null })
    }
}
