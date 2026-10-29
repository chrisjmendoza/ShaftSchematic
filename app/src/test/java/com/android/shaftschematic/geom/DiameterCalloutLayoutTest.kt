package com.android.shaftschematic.geom

import com.android.shaftschematic.geom.DiameterCalloutLayout.Footprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiameterCalloutLayoutTest {

    private fun fp(left: Float, right: Float) = Footprint(left, right)

    @Test
    fun `empty input returns empty`() {
        assertTrue(DiameterCalloutLayout.assignTiers(emptyList()).isEmpty())
    }

    @Test
    fun `single footprint gets tier 0`() {
        assertEquals(listOf(0), DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f))))
    }

    @Test
    fun `two disjoint footprints both stay on tier 0`() {
        // Second starts well past the first's right edge + gap.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f), fp(100f, 120f)))
        assertEquals(listOf(0, 0), tiers)
    }

    @Test
    fun `two overlapping footprints split to tiers 0 and 1`() {
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 40f), fp(20f, 60f)))
        assertEquals(listOf(0, 1), tiers)
    }

    @Test
    fun `footprints closer than the gap bump to tier 1 even without literal overlap`() {
        // right=20, next left=22 → 20 + MIN_GAP(4) = 24 > 22, so it does not clear tier 0.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f), fp(22f, 42f)))
        assertEquals(listOf(0, 1), tiers)
    }

    @Test
    fun `third footprint clear of the first reuses tier 0`() {
        // A[0,40] and B[20,60] collide → tiers 0,1. C[80,100] clears A on tier 0.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 40f), fp(20f, 60f), fp(80f, 100f)))
        assertEquals(listOf(0, 1, 0), tiers)
    }

    @Test
    fun `three mutually overlapping footprints never exceed maxTiers`() {
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 50f), fp(10f, 60f), fp(20f, 70f)))
        assertEquals(3, tiers.size)
        assertTrue(tiers.all { it in 0 until DiameterCalloutLayout.MAX_TIERS })
    }

    @Test
    fun `result is parallel to input order for unsorted input`() {
        // Feed right-to-left; tiers must map back to original positions.
        // Input[0]=far-right disjoint → tier 0; Input[1],Input[2] overlap on the left.
        val tiers = DiameterCalloutLayout.assignTiers(
            listOf(fp(100f, 120f), fp(0f, 40f), fp(20f, 60f))
        )
        assertEquals(0, tiers[0])                 // the far-right one
        assertEquals(setOf(0, 1), setOf(tiers[1], tiers[2])) // the two left overlappers split
    }

    @Test
    fun `deterministic for identical input`() {
        val input = listOf(fp(0f, 40f), fp(20f, 60f), fp(15f, 55f), fp(200f, 220f))
        assertEquals(
            DiameterCalloutLayout.assignTiers(input),
            DiameterCalloutLayout.assignTiers(input),
        )
    }

    @Test
    fun `maxTiers of 1 forces everything to tier 0`() {
        val tiers = DiameterCalloutLayout.assignTiers(
            listOf(fp(0f, 40f), fp(20f, 60f), fp(30f, 70f)),
            maxTiers = 1,
        )
        assertEquals(listOf(0, 0, 0), tiers)
    }

    // ── Label placement against the content edge ──────────────────────────────────────

    @Test
    fun `a label with room hangs right of its leader as before`() {
        assertEquals(114f, DiameterCalloutLayout.labelLeft(100f, 40f, 14f, 36f, 756f), 1e-4f)
        assertEquals("unbounded = unchanged", 114f, DiameterCalloutLayout.labelLeft(100f, 40f, 14f), 1e-4f)
    }

    @Test
    fun `a label that would run past the content right flips left of its leader`() {
        // On-device report: Ø 5.998" ran into the page margin off a body at the FWD end.
        val left = DiameterCalloutLayout.labelLeft(730f, 40f, 14f, 36f, 756f)
        assertEquals(730f - 14f - 40f, left, 1e-4f)
        assertTrue(left + 40f <= 756f)
    }

    @Test
    fun `a label that fits neither side is kept inside the content`() {
        val left = DiameterCalloutLayout.labelLeft(60f, 60f, 14f, 36f, 100f)
        assertTrue(left >= 36f)
        assertTrue(left + 60f <= 100f + 1e-4f)
    }
}
