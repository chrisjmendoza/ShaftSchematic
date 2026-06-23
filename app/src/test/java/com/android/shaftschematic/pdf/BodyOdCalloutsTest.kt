package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.pdf.notes.LeaderSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyOdCalloutsTest {

    // ── Empty / zero-OD cases ─────────────────────────────────────────────────

    @Test
    fun `no bodies returns empty list`() {
        assertTrue(buildBodyOdCallouts(emptyList()).isEmpty())
    }

    @Test
    fun `body with zero diameter is skipped`() {
        val bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 0f))
        assertTrue(buildBodyOdCallouts(bodies).isEmpty())
    }

    // ── Single body ───────────────────────────────────────────────────────────

    @Test
    fun `single body produces one callout above`() {
        val body = Body(startFromAftMm = 100f, lengthMm = 400f, diaMm = 127f)
        val calls = buildBodyOdCallouts(listOf(body))

        assertEquals(1, calls.size)
        assertEquals(127.0, calls[0].valueMm, 0.001)
        assertEquals(LeaderSide.ABOVE, calls[0].side)
    }

    @Test
    fun `callout placed at body center`() {
        val body = Body(startFromAftMm = 200f, lengthMm = 300f, diaMm = 127f)
        val calls = buildBodyOdCallouts(listOf(body))

        // Center = 200 + 300/2 = 350
        assertEquals(350.0, calls[0].xMm, 0.001)
    }

    // ── Multiple bodies, same OD ──────────────────────────────────────────────

    @Test
    fun `two bodies with same OD produce one callout at the longer body center`() {
        val short = Body(startFromAftMm = 0f,    lengthMm = 100f, diaMm = 127f)
        val long  = Body(startFromAftMm = 200f,  lengthMm = 500f, diaMm = 127f)
        val calls = buildBodyOdCallouts(listOf(short, long))

        assertEquals(1, calls.size)
        // Placed at center of longer body: 200 + 250 = 450
        assertEquals(450.0, calls[0].xMm, 0.001)
    }

    // ── Multiple distinct ODs ─────────────────────────────────────────────────

    @Test
    fun `two distinct ODs produce two callouts`() {
        val b1 = Body(startFromAftMm = 0f,   lengthMm = 300f, diaMm = 127f)
        val b2 = Body(startFromAftMm = 400f, lengthMm = 200f, diaMm = 152f)
        val calls = buildBodyOdCallouts(listOf(b1, b2))

        assertEquals(2, calls.size)
        val diameters = calls.map { it.valueMm }.toSet()
        assertTrue(diameters.contains(127.0))
        assertTrue(diameters.contains(152.0))
    }

    @Test
    fun `two distinct ODs alternate sides ABOVE then BELOW`() {
        // Sorted descending by OD: 152 first (ABOVE), 127 second (BELOW)
        val b1 = Body(startFromAftMm = 0f,   lengthMm = 300f, diaMm = 127f)
        val b2 = Body(startFromAftMm = 400f, lengthMm = 200f, diaMm = 152f)
        val calls = buildBodyOdCallouts(listOf(b1, b2))

        val larger  = calls.first { it.valueMm == 152.0 }
        val smaller = calls.first { it.valueMm == 127.0 }
        assertEquals(LeaderSide.ABOVE, larger.side)
        assertEquals(LeaderSide.BELOW, smaller.side)
    }

    @Test
    fun `three distinct ODs cycle ABOVE BELOW ABOVE`() {
        val bodies = listOf(
            Body(startFromAftMm = 0f,    lengthMm = 200f, diaMm = 100f),
            Body(startFromAftMm = 300f,  lengthMm = 200f, diaMm = 130f),
            Body(startFromAftMm = 600f,  lengthMm = 200f, diaMm = 160f),
        )
        val calls = buildBodyOdCallouts(bodies)

        assertEquals(3, calls.size)
        // Descending OD order: 160 (ABOVE), 130 (BELOW), 100 (ABOVE)
        val by160 = calls.first { it.valueMm == 160.0 }
        val by130 = calls.first { it.valueMm == 130.0 }
        val by100 = calls.first { it.valueMm == 100.0 }
        assertEquals(LeaderSide.ABOVE, by160.side)
        assertEquals(LeaderSide.BELOW, by130.side)
        assertEquals(LeaderSide.ABOVE, by100.side)
    }

    // ── OD value accuracy ─────────────────────────────────────────────────────

    @Test
    fun `callout valueMm matches body diaMm exactly`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 130.175f)
        val calls = buildBodyOdCallouts(listOf(body))

        assertEquals(130.175, calls[0].valueMm, 0.0001)
    }
}
