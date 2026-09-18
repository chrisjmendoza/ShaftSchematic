package com.android.shaftschematic.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    // ── Hole style (draw-only) ────────────────────────────────────────────────

    @Test
    fun `hole style defaults to the seam cutout`() {
        assertEquals(BoltHoleStyle.SEAM, CouplerBoltSlot().holeStyle)
    }

    @Test
    fun `a row saved before the style existed decodes as a seam cutout`() {
        // Same serializer shape as the codec (ignoreUnknownKeys, defaults filled) — no holeStyle key.
        val raw = """{"id":"legacy","startFromAftMm":250.0,"holeDiaMm":20.0,"count":1}"""
        val slot = Json { ignoreUnknownKeys = true }.decodeFromString<CouplerBoltSlot>(raw)
        assertEquals(BoltHoleStyle.SEAM, slot.holeStyle)
        assertEquals(250f, slot.startFromAftMm, EPS)
    }

    @Test
    fun `cross-drilled style round-trips through the serializer`() {
        val json = Json { encodeDefaults = true }
        val slot = CouplerBoltSlot(startFromAftMm = 250f, holeDiaMm = 20f, holeStyle = BoltHoleStyle.CROSS)
        val back = json.decodeFromString<CouplerBoltSlot>(json.encodeToString(slot))
        assertEquals(BoltHoleStyle.CROSS, back.holeStyle)
        assertEquals(slot, back)
    }

    @Test
    fun `hole style changes nothing about footprint, validity, coverage or OD`() {
        val seam = CouplerBoltSlot(startFromAftMm = 350f, holeDiaMm = 60f, count = 2, spacingMm = 30f)
        val cross = seam.copy(holeStyle = BoltHoleStyle.CROSS)
        assertEquals(seam.lengthMm, cross.lengthMm, EPS)
        assertEquals(seam.isValid(400f), cross.isValid(400f))
        assertEquals(seam.isValid(390f), cross.isValid(390f))
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 300f, diaMm = 50f)),
            couplerBoltSlots = listOf(cross),
        )
        assertEquals(300f, spec.coverageEndMm(), EPS)
        assertEquals(50f, spec.maxOuterDiaMm(), EPS)
    }

    // ── Clocking against the keyway (cross-drilled only) ──────────────────────

    @Test
    fun `clocking defaults to 90 degrees from the keyway`() {
        assertEquals(BoltHoleClocking.DEG_90, CouplerBoltSlot().clocking)
    }

    @Test
    fun `only a cross-drilled hole at 90 degrees is a hidden bore`() {
        val cross90 = CouplerBoltSlot(holeStyle = BoltHoleStyle.CROSS, clocking = BoltHoleClocking.DEG_90)
        assertTrue(cross90.isHiddenCrossBore)
        assertFalse(cross90.copy(clocking = BoltHoleClocking.IN_LINE).isHiddenCrossBore)
        // A seam cutout is on the surface whatever its clocking says.
        assertFalse(cross90.copy(holeStyle = BoltHoleStyle.SEAM).isHiddenCrossBore)
    }

    @Test
    fun `authored center is the distance from the reference face to the nearest hole`() {
        // FWD: 400 OAL, single hole at 350 → 50 from the FWD face.
        val fwd = CouplerBoltSlot(startFromAftMm = 350f, holeDiaMm = 20f, authoredReference = SlotAuthoredReference.FWD)
        assertEquals(50f, fwd.authoredCenterMm(400f), EPS)
        // FWD, a row: fwd-most center 350 + 30 = 380 → 20 from the face.
        val row = fwd.copy(count = 2, spacingMm = 30f)
        assertEquals(20f, row.authoredCenterMm(400f), EPS)
        // AFT: the aft-most center, stored directly.
        assertEquals(350f, row.copy(authoredReference = SlotAuthoredReference.AFT).authoredCenterMm(400f), EPS)
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
