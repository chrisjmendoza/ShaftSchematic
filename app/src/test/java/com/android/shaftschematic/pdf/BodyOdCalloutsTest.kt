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
    fun `single body produces one callout below`() {
        val body = Body(startFromAftMm = 100f, lengthMm = 400f, diaMm = 127f)
        val calls = buildBodyOdCallouts(listOf(body))

        assertEquals(1, calls.size)
        assertEquals(127.0, calls[0].valueMm, 0.001)
        assertEquals(LeaderSide.BELOW, calls[0].side)
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

    // ── All callouts hang BELOW; overlap is resolved by tiering, not side flips ─

    @Test
    fun `all callouts are BELOW regardless of count`() {
        val one = buildBodyOdCallouts(
            listOf(Body(startFromAftMm = 0f, lengthMm = 200f, diaMm = 100f))
        )
        val two = buildBodyOdCallouts(
            listOf(
                Body(startFromAftMm = 0f,   lengthMm = 300f, diaMm = 127f),
                Body(startFromAftMm = 400f, lengthMm = 200f, diaMm = 152f),
            )
        )
        val three = buildBodyOdCallouts(
            listOf(
                Body(startFromAftMm = 0f,   lengthMm = 200f, diaMm = 100f),
                Body(startFromAftMm = 300f, lengthMm = 200f, diaMm = 130f),
                Body(startFromAftMm = 600f, lengthMm = 200f, diaMm = 160f),
            )
        )
        assertTrue((one + two + three).all { it.side == LeaderSide.BELOW })
    }

    // ── OD value accuracy ─────────────────────────────────────────────────────

    @Test
    fun `callout valueMm matches body diaMm exactly`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 130.175f)
        val calls = buildBodyOdCallouts(listOf(body))

        assertEquals(130.175, calls[0].valueMm, 0.0001)
    }
}
