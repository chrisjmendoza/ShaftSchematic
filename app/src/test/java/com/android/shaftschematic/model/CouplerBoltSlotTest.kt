package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 0.001f

/** First direct coverage for the coupler bolt slot model (reference-only feature). */
class CouplerBoltSlotTest {

    // ── Derived geometry ──────────────────────────────────────────────────────

    @Test
    fun `lengthMm is row span plus one hole diameter`() {
        val slot = CouplerBoltSlot(startFromAftMm = 100f, holeDiaMm = 10f, count = 3, spacingMm = 20f)
        // (3−1)·20 + 10
        assertEquals(50f, slot.lengthMm, EPS)
    }

    @Test
    fun `single cutout lengthMm is just the hole diameter`() {
        val slot = CouplerBoltSlot(startFromAftMm = 100f, holeDiaMm = 12f, count = 1, spacingMm = 999f)
        assertEquals(12f, slot.lengthMm, EPS)
    }

    @Test
    fun `centerMmAt walks the row by spacing`() {
        val slot = CouplerBoltSlot(startFromAftMm = 100f, holeDiaMm = 10f, count = 3, spacingMm = 20f)
        assertEquals(100f, slot.centerMmAt(0), EPS)
        assertEquals(120f, slot.centerMmAt(1), EPS)
        assertEquals(140f, slot.centerMmAt(2), EPS)
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    fun `valid row inside the shaft passes`() {
        val slot = CouplerBoltSlot(startFromAftMm = 100f, holeDiaMm = 10f, count = 3, spacingMm = 20f)
        assertTrue(slot.isValid(overallLengthMm = 400f))
    }

    @Test
    fun `row overrunning the fwd end fails`() {
        // Last center 140 + r5 = 145 > 140 OAL
        val slot = CouplerBoltSlot(startFromAftMm = 100f, holeDiaMm = 10f, count = 3, spacingMm = 20f)
        assertFalse(slot.isValid(overallLengthMm = 140f))
    }

    @Test
    fun `cutout biting past the aft face fails`() {
        // Center at 2, radius 5 → bites to −3
        val slot = CouplerBoltSlot(startFromAftMm = 2f, holeDiaMm = 10f, count = 1)
        assertFalse(slot.isValid(overallLengthMm = 400f))
    }

    @Test
    fun `zero count and negative fields fail`() {
        assertFalse(CouplerBoltSlot(startFromAftMm = 10f, holeDiaMm = 10f, count = 0).isValid(400f))
        assertFalse(CouplerBoltSlot(startFromAftMm = -1f, holeDiaMm = 10f, count = 1).isValid(400f))
        assertFalse(CouplerBoltSlot(startFromAftMm = 10f, holeDiaMm = -1f, count = 1).isValid(400f))
        assertFalse(CouplerBoltSlot(startFromAftMm = 10f, holeDiaMm = 10f, count = 2, spacingMm = -5f).isValid(400f))
    }

    // ── Reference-only invariants ─────────────────────────────────────────────

    @Test
    fun `slots never contribute to coverage or max OD`() {
        val slot = CouplerBoltSlot(startFromAftMm = 350f, holeDiaMm = 60f, count = 2, spacingMm = 30f)
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 50f)),
            couplerBoltSlots = listOf(slot),
        )
        // Coverage ends at the body, not at the slot row (which reaches past 380).
        assertEquals(300f, spec.coverageEndMm(), EPS)
        // Max OD comes from the body; the 60mm hole diameter is not an OD.
        assertEquals(50f, spec.maxOuterDiaMm(), EPS)
    }
}
