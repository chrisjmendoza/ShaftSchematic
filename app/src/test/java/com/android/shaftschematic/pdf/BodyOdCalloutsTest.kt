package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.pdf.notes.LeaderSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyOdCalloutsTest {

    /**
     * Body callouts are OPT-IN ([Body.showDiaOnDrawing] defaults false), so every body meant
     * to print in these tests says so explicitly.
     */
    private fun shown(startFromAftMm: Float, lengthMm: Float, diaMm: Float) =
        Body(startFromAftMm = startFromAftMm, lengthMm = lengthMm, diaMm = diaMm, showDiaOnDrawing = true)

    // ── Empty / zero-OD cases ─────────────────────────────────────────────────

    @Test
    fun `no bodies returns empty list`() {
        assertTrue(buildBodyOdCallouts(emptyList()).isEmpty())
    }

    @Test
    fun `body with zero diameter is skipped`() {
        assertTrue(buildBodyOdCallouts(listOf(shown(0f, 500f, diaMm = 0f))).isEmpty())
    }

    // ── Single body ───────────────────────────────────────────────────────────

    @Test
    fun `single shown body produces one callout below`() {
        val calls = buildBodyOdCallouts(listOf(shown(100f, 400f, 127f)))

        assertEquals(1, calls.size)
        assertEquals(127.0, calls[0].valueMm, 0.001)
        assertEquals(LeaderSide.BELOW, calls[0].side)
    }

    @Test
    fun `callout placed at body center`() {
        val calls = buildBodyOdCallouts(listOf(shown(200f, 300f, 127f)))

        // Center = 200 + 300/2 = 350
        assertEquals(350.0, calls[0].xMm, 0.001)
    }

    // ── Multiple bodies, same OD ──────────────────────────────────────────────

    @Test
    fun `two bodies with same OD produce one callout at the longer body center`() {
        val calls = buildBodyOdCallouts(
            listOf(shown(0f, 100f, 127f), shown(200f, 500f, 127f))
        )

        assertEquals(1, calls.size)
        // Placed at center of longer body: 200 + 250 = 450
        assertEquals(450.0, calls[0].xMm, 0.001)
    }

    // ── Multiple distinct ODs ─────────────────────────────────────────────────

    @Test
    fun `two distinct ODs produce two callouts`() {
        val calls = buildBodyOdCallouts(
            listOf(shown(0f, 300f, 127f), shown(400f, 200f, 152f))
        )

        assertEquals(2, calls.size)
        val diameters = calls.map { it.valueMm }.toSet()
        assertTrue(diameters.contains(127.0))
        assertTrue(diameters.contains(152.0))
    }

    // ── All callouts hang BELOW; overlap is resolved by tiering, not side flips ─

    @Test
    fun `all callouts are BELOW regardless of count`() {
        val one = buildBodyOdCallouts(listOf(shown(0f, 200f, 100f)))
        val two = buildBodyOdCallouts(
            listOf(shown(0f, 300f, 127f), shown(400f, 200f, 152f))
        )
        val three = buildBodyOdCallouts(
            listOf(shown(0f, 200f, 100f), shown(300f, 200f, 130f), shown(600f, 200f, 160f))
        )
        assertTrue((one + two + three).all { it.side == LeaderSide.BELOW })
    }

    // ── Per-body Ø visibility (showDiaOnDrawing) ──────────────────────────────

    @Test
    fun `hidden body produces no callout`() {
        val body = Body(startFromAftMm = 100f, lengthMm = 400f, diaMm = 127f, showDiaOnDrawing = false)
        assertTrue(buildBodyOdCallouts(listOf(body)).isEmpty())
    }

    @Test
    fun `hiding the longer body moves the anchor to the shown one`() {
        // The reported case: a long run under fiberglass shares its Ø with a short bare
        // window that could actually be measured. With the covered run hidden, the callout
        // must not vanish — it anchors on the run the reading came from.
        val covered = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 127f, showDiaOnDrawing = false)
        val window  = shown(600f, 100f, 127f)
        val calls = buildBodyOdCallouts(listOf(covered, window))

        assertEquals(1, calls.size)
        assertEquals(650.0, calls[0].xMm, 0.001)
    }

    @Test
    fun `hiding every body of one OD drops that callout and keeps the others`() {
        val hidden1 = Body(startFromAftMm = 0f,   lengthMm = 300f, diaMm = 127f, showDiaOnDrawing = false)
        val hidden2 = Body(startFromAftMm = 400f, lengthMm = 100f, diaMm = 127f, showDiaOnDrawing = false)
        val shown   = shown(600f, 200f, 152f)
        val calls = buildBodyOdCallouts(listOf(hidden1, hidden2, shown))

        assertEquals(1, calls.size)
        assertEquals(152.0, calls[0].valueMm, 0.001)
    }

    @Test
    fun `bodies are hidden by default — callouts are opt-in`() {
        // On-device preference: the schematic stays clean unless a body's Ø is deliberately
        // shown; the footer's "Body:" list still carries every Ø.
        assertFalse(Body().showDiaOnDrawing)
        val defaultBody = Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 127f)
        assertTrue(buildBodyOdCallouts(listOf(defaultBody)).isEmpty())
    }

    // ── OD value accuracy ─────────────────────────────────────────────────────

    @Test
    fun `callout valueMm matches body diaMm exactly`() {
        val calls = buildBodyOdCallouts(listOf(shown(0f, 300f, 130.175f)))

        assertEquals(130.175, calls[0].valueMm, 0.0001)
    }
}
