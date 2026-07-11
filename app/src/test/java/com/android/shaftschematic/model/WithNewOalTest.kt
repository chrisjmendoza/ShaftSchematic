package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Test

private const val EPS = 0.001f

/**
 * Tests for [ShaftSpec.withNewOal].
 *
 * Contract:
 *  - AFT-referenced tapers and liners keep their [startFromAftMm] unchanged.
 *  - FWD-referenced tapers and liners keep their authored-from-FWD distance constant,
 *    so their physical start slides as OAL changes.
 *  - Excluded end threads are repositioned by syncExcludedThreadPositions().
 *  - OAL is clamped to ≥ 0.
 */
class WithNewOalTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun authoredFromFwd(spec: ShaftSpec, t: Taper): Float =
        spec.overallLengthMm - t.startFromAftMm - t.lengthMm

    private fun authoredFromFwd(spec: ShaftSpec, ln: Liner): Float =
        spec.overallLengthMm - ln.startFromAftMm - ln.lengthMm

    /** Distance from the FWD face to the fwd-most cutout center (the authored FWD value). */
    private fun authoredFromFwd(spec: ShaftSpec, cs: CouplerBoltSlot): Float {
        val rowSpan = (cs.count - 1).coerceAtLeast(0) * cs.spacingMm
        return spec.overallLengthMm - (cs.startFromAftMm + rowSpan)
    }

    // ── OAL update, no FWD components ────────────────────────────────────────

    @Test
    fun `empty spec - oal updated correctly`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        val result = spec.withNewOal(1000f)
        assertEquals(1000f, result.overallLengthMm, EPS)
    }

    @Test
    fun `oal clamped to zero when negative`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        val result = spec.withNewOal(-100f)
        assertEquals(0f, result.overallLengthMm, EPS)
    }

    @Test
    fun `aft-ref taper keeps startFromAftMm on oal grow`() {
        val taper = Taper(id = "t1", startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        val spec = ShaftSpec(overallLengthMm = 300f, tapers = listOf(taper))

        val result = spec.withNewOal(600f)

        assertEquals(50f, result.tapers[0].startFromAftMm, EPS)
        assertEquals(600f, result.overallLengthMm, EPS)
    }

    @Test
    fun `aft-ref taper keeps startFromAftMm on oal shrink`() {
        val taper = Taper(id = "t1", startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        val spec = ShaftSpec(overallLengthMm = 300f, tapers = listOf(taper))

        val result = spec.withNewOal(200f)

        assertEquals(50f, result.tapers[0].startFromAftMm, EPS)
        assertEquals(200f, result.overallLengthMm, EPS)
    }

    // ── FWD-ref taper anchoring ───────────────────────────────────────────────

    @Test
    fun `fwd-ref taper preserves authored-from-fwd distance on oal grow`() {
        // Taper: physStart=200, len=100, oal=400 → authoredFromFwd = 400-200-100 = 100mm
        val taper = Taper(id = "t1", startFromAftMm = 200f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))
        val originalFwdDist = authoredFromFwd(spec, taper)  // 100mm

        val result = spec.withNewOal(800f)

        val newFwdDist = authoredFromFwd(result, result.tapers[0])
        assertEquals("authored-from-fwd must be preserved", originalFwdDist, newFwdDist, EPS)
        // physStart = 800 - 100 - 100 = 600
        assertEquals(600f, result.tapers[0].startFromAftMm, EPS)
    }

    @Test
    fun `fwd-ref taper preserves authored-from-fwd distance on oal shrink`() {
        // Taper: physStart=200, len=100, oal=400 → authoredFromFwd=100mm
        val taper = Taper(id = "t1", startFromAftMm = 200f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))
        val originalFwdDist = authoredFromFwd(spec, taper)  // 100mm

        val result = spec.withNewOal(250f)

        val newFwdDist = authoredFromFwd(result, result.tapers[0])
        assertEquals("authored-from-fwd must be preserved", originalFwdDist, newFwdDist, EPS)
        // physStart = 250 - 100 - 100 = 50
        assertEquals(50f, result.tapers[0].startFromAftMm, EPS)
    }

    @Test
    fun `fwd-ref taper flush with fwd face stays flush after oal change`() {
        // Taper FWD end exactly at OAL — authoredFromFwd = 0
        val taper = Taper(id = "t1", startFromAftMm = 300f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))

        val result = spec.withNewOal(700f)

        // FWD end should be at new OAL
        val fwdEnd = result.tapers[0].startFromAftMm + result.tapers[0].lengthMm
        assertEquals("FWD end stays flush", 700f, fwdEnd, EPS)
    }

    @Test
    fun `fwd-ref taper anchored at aft face stays anchored on oal grow`() {
        // Taper FWD end at original AFT face (startFromAftMm=0, len=0 is degenerate, let's use a real one)
        // authoredFromFwd = oal - 0 - 100 = 300 (taper start at AFT face, growing into shaft)
        val taper = Taper(id = "t1", startFromAftMm = 0f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))
        val originalFwdDist = authoredFromFwd(spec, taper)  // 300mm

        val result = spec.withNewOal(600f)

        val newFwdDist = authoredFromFwd(result, result.tapers[0])
        assertEquals("authored-from-fwd preserved", originalFwdDist, newFwdDist, EPS)
    }

    @Test
    fun `fwd-ref length is preserved after oal change`() {
        val taper = Taper(id = "t1", startFromAftMm = 200f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))

        val result = spec.withNewOal(900f)

        assertEquals("length must not change", 100f, result.tapers[0].lengthMm, EPS)
    }

    // ── Mixed AFT + FWD tapers in same spec ──────────────────────────────────

    @Test
    fun `mixed spec - aft taper stays, fwd taper reanchors`() {
        val aftTaper = Taper(id = "aft", startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 55f, authoredReference = LinerAuthoredReference.AFT)
        // FWD taper: physStart=700, len=100, oal=1000 → authored=200mm
        val fwdTaper = Taper(id = "fwd", startFromAftMm = 700f, lengthMm = 100f,
            startDiaMm = 55f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(aftTaper, fwdTaper))

        val result = spec.withNewOal(1500f)

        val resultAft = result.tapers.first { it.id == "aft" }
        val resultFwd = result.tapers.first { it.id == "fwd" }

        assertEquals("AFT taper unchanged", 50f, resultAft.startFromAftMm, EPS)
        // FWD taper: new physStart = 1500 - 200 - 100 = 1200
        assertEquals("FWD taper reanchored", 1200f, resultFwd.startFromAftMm, EPS)
        assertEquals("authored-from-fwd unchanged", 200f,
            result.overallLengthMm - resultFwd.startFromAftMm - resultFwd.lengthMm, EPS)
    }

    // ── FWD-ref liner anchoring ───────────────────────────────────────────────

    @Test
    fun `fwd-ref liner preserves authored-from-fwd on oal grow`() {
        // Liner: physStart=300, len=150, oal=500 → authoredFromFwd=500-300-150=50
        val liner = Liner(id = "ln1", startFromAftMm = 300f, lengthMm = 150f,
            odMm = 55f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 500f, liners = listOf(liner))
        val originalFwdDist = authoredFromFwd(spec, liner)  // 50mm

        val result = spec.withNewOal(1000f)

        val newFwdDist = authoredFromFwd(result, result.liners[0])
        assertEquals("authored-from-fwd preserved", originalFwdDist, newFwdDist, EPS)
        // physStart = 1000 - 50 - 150 = 800
        assertEquals(800f, result.liners[0].startFromAftMm, EPS)
    }

    @Test
    fun `aft-ref liner keeps startFromAftMm on oal change`() {
        val liner = Liner(id = "ln1", startFromAftMm = 100f, lengthMm = 150f,
            odMm = 55f, authoredReference = LinerAuthoredReference.AFT)
        val spec = ShaftSpec(overallLengthMm = 500f, liners = listOf(liner))

        val result = spec.withNewOal(800f)

        assertEquals(100f, result.liners[0].startFromAftMm, EPS)
    }

    @Test
    fun `fwd-ref liner endMmPhysical is kept consistent`() {
        val liner = Liner(id = "ln1", startFromAftMm = 300f, lengthMm = 150f,
            odMm = 55f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 500f, liners = listOf(liner))

        val result = spec.withNewOal(1000f)
        val ln = result.liners[0]

        assertEquals("endMmPhysical = start + length",
            ln.startFromAftMm + ln.lengthMm, ln.endMmPhysical, EPS)
    }

    // ── Excluded end-thread repositioning ─────────────────────────────────────

    @Test
    fun `excluded fwd thread repositioned to new oal after grow`() {
        val threadLen = 50f
        // FWD-end excluded thread: isAftEnd=false, placed at oal by syncExcludedThreadPositions
        val fwdThread = Threads(
            id = "th_fwd",
            startFromAftMm = 1000f,  // oal = start position per sync convention
            lengthMm = threadLen,
            majorDiaMm = 50f, pitchMm = 2f,
            excludeFromOAL = true,
            isAftEnd = false,
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(fwdThread))

        val result = spec.withNewOal(1500f)

        // syncExcludedThreadPositions places FWD-end thread at startFromAftMm = newOal
        assertEquals("FWD excluded thread repositioned to new OAL", 1500f, result.threads[0].startFromAftMm, EPS)
    }

    @Test
    fun `excluded aft thread repositioned to minus-length after oal change`() {
        val threadLen = 50f
        // AFT-end excluded thread: isAftEnd=true (default), placed at -lengthMm by sync
        val aftThread = Threads(
            id = "th_aft",
            startFromAftMm = -threadLen,  // per sync convention
            lengthMm = threadLen,
            majorDiaMm = 50f, pitchMm = 2f,
            excludeFromOAL = true,
            isAftEnd = true,
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(aftThread))

        val result = spec.withNewOal(2000f)

        // syncExcludedThreadPositions places AFT-end thread at startFromAftMm = -lengthMm
        assertEquals("AFT excluded thread stays at -lengthMm", -threadLen, result.threads[0].startFromAftMm, EPS)
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    fun `withNewOal with same oal is a no-op`() {
        val taper = Taper(id = "t1", startFromAftMm = 200f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, tapers = listOf(taper))

        val result = spec.withNewOal(400f)

        assertEquals(400f, result.overallLengthMm, EPS)
        assertEquals(200f, result.tapers[0].startFromAftMm, EPS)
    }

    // ── Regression: old direct copy would have drifted FWD-ref tapers ────────

    @Test
    fun `old copy would have drifted fwd-ref taper but withNewOal does not`() {
        // OAL doubles: 500 → 1000
        // FWD-ref taper physStart=350, len=100 → authoredFromFwd=50mm
        val taper = Taper(id = "t1", startFromAftMm = 350f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 500f, tapers = listOf(taper))

        // Old behaviour: .copy(overallLengthMm = 1000) — physStart stays at 350, drifts to FWD end 450
        val oldBehaviourFwdEnd = taper.startFromAftMm + taper.lengthMm  // 450 (drifted, not at 950)

        // New behaviour: withNewOal reanchors physStart = 1000 - 50 - 100 = 850
        val result = spec.withNewOal(1000f)
        val newFwdEnd = result.tapers[0].startFromAftMm + result.tapers[0].lengthMm  // 950

        assertEquals("authored-from-fwd must still be 50mm",
            50f, 1000f - result.tapers[0].startFromAftMm - result.tapers[0].lengthMm, EPS)
        assertEquals("FWD end is now at 950 (oal - authored)", 950f, newFwdEnd, EPS)
        assertEquals("Old copy would have put FWD end at 450 (drifted)", 450f, oldBehaviourFwdEnd, EPS)
    }

    // ── Coupler bolt slots — FWD default reference must reanchor like tapers/liners ──

    @Test
    fun `fwd-ref coupler slot preserves authored-from-fwd distance on oal grow`() {
        // Row of 3 cutouts, spacing 20: aft-most center at 340, fwd-most at 380.
        // OAL 400 → authoredFromFwd = 400 − 380 = 20mm off the FWD face.
        val slot = CouplerBoltSlot(id = "cs1", startFromAftMm = 340f, holeDiaMm = 10f,
            count = 3, spacingMm = 20f, authoredReference = SlotAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, couplerBoltSlots = listOf(slot))
        val originalFwdDist = authoredFromFwd(spec, slot)  // 20mm

        val result = spec.withNewOal(800f)

        val newFwdDist = authoredFromFwd(result, result.couplerBoltSlots[0])
        assertEquals(originalFwdDist, newFwdDist, EPS)
        // aft-most center slides: 800 − 20 − 40 = 740
        assertEquals(740f, result.couplerBoltSlots[0].startFromAftMm, EPS)
    }

    @Test
    fun `fwd-ref coupler slot preserves authored-from-fwd distance on oal shrink`() {
        val slot = CouplerBoltSlot(id = "cs1", startFromAftMm = 340f, holeDiaMm = 10f,
            count = 3, spacingMm = 20f, authoredReference = SlotAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, couplerBoltSlots = listOf(slot))

        val result = spec.withNewOal(300f)

        assertEquals(20f, authoredFromFwd(result, result.couplerBoltSlots[0]), EPS)
        assertEquals(240f, result.couplerBoltSlots[0].startFromAftMm, EPS)
    }

    @Test
    fun `aft-ref coupler slot keeps startFromAftMm on oal change`() {
        val slot = CouplerBoltSlot(id = "cs1", startFromAftMm = 50f, holeDiaMm = 10f,
            count = 2, spacingMm = 25f, authoredReference = SlotAuthoredReference.AFT)
        val spec = ShaftSpec(overallLengthMm = 400f, couplerBoltSlots = listOf(slot))

        val result = spec.withNewOal(800f)

        assertEquals(50f, result.couplerBoltSlots[0].startFromAftMm, EPS)
    }

    @Test
    fun `single-cutout fwd-ref slot reanchors by its center`() {
        // count=1 → rowSpan 0; center at 390, OAL 400 → authored 10mm from FWD.
        val slot = CouplerBoltSlot(id = "cs1", startFromAftMm = 390f, holeDiaMm = 10f,
            count = 1, spacingMm = 0f, authoredReference = SlotAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 400f, couplerBoltSlots = listOf(slot))

        val result = spec.withNewOal(600f)

        assertEquals(590f, result.couplerBoltSlots[0].startFromAftMm, EPS)
    }
}
