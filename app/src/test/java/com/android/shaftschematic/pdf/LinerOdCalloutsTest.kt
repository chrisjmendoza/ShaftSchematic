package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.pdf.notes.LeaderSide
import org.junit.Assert.assertEquals
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
        val bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 127f))
        val liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 127f))

        val combined = buildBodyOdCallouts(bodies) + buildLinerOdCallouts(liners)

        assertEquals(2, combined.size)
        assertTrue(combined.all { it.valueMm == 127.0 })
        assertTrue(combined.all { it.side == LeaderSide.BELOW })
    }
}
