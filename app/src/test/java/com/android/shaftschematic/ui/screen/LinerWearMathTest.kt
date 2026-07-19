package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.WearSpotReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure liner-wear math helpers in `LinerWearMath.kt`:
 * tap-x→liner selection (incl. the tie case), liner-local→px band mapping, clamping of a
 * wear spot that extends past its liner's current length, and (2026-07-18 post-review spec)
 * the four "Measure from" reference conversions plus blocking in-span validation.
 *
 * See `docs/LinerWearAreas_Proposal.md` §2, §6.1, §7.
 */
class LinerWearMathTest {

    // ── pickLinerIdAtMm ──────────────────────────────────────────────────────

    @Test
    fun `tap inside a single liner span selects that liner`() {
        val liners = listOf(
            LinerSpanMm("A", 0f, 100f),
            LinerSpanMm("B", 200f, 300f),
        )
        assertEquals("A", pickLinerIdAtMm(50f, liners))
        assertEquals("B", pickLinerIdAtMm(250f, liners))
    }

    @Test
    fun `tap outside every liner span selects nothing`() {
        val liners = listOf(LinerSpanMm("A", 0f, 100f), LinerSpanMm("B", 200f, 300f))
        assertNull(pickLinerIdAtMm(150f, liners))
        assertNull(pickLinerIdAtMm(-10f, liners))
        assertNull(pickLinerIdAtMm(1000f, liners))
    }

    @Test
    fun `tap exactly on a liner edge selects that liner (inclusive bounds)`() {
        val liners = listOf(LinerSpanMm("A", 0f, 100f))
        assertEquals("A", pickLinerIdAtMm(0f, liners))
        assertEquals("A", pickLinerIdAtMm(100f, liners))
    }

    @Test
    fun `tap on a shared boundary between two touching liners breaks tie by nearer edge`() {
        // Two liners touching at x=100. A tap at exactly 100 is inside both spans.
        // Neither span has a "nearer" edge (both are 0mm away) — the tie-break must not throw
        // and must deterministically pick one (minByOrNull keeps the first minimal element).
        val liners = listOf(
            LinerSpanMm("A", 0f, 100f),
            LinerSpanMm("B", 100f, 200f),
        )
        val picked = pickLinerIdAtMm(100f, liners)
        assertTrue(picked == "A" || picked == "B")
    }

    @Test
    fun `tap nearer one liner's edge than the other's breaks tie correctly`() {
        // Overlapping spans (contrived — liners don't normally overlap, but the hit-test must
        // still resolve deterministically): tap at 95 is 5mm from A's end (100) and 15mm from
        // B's start (80), so A should win.
        val liners = listOf(
            LinerSpanMm("A", 0f, 100f),
            LinerSpanMm("B", 80f, 200f),
        )
        assertEquals("A", pickLinerIdAtMm(95f, liners))
    }

    @Test
    fun `empty liner list selects nothing`() {
        assertNull(pickLinerIdAtMm(50f, emptyList()))
    }

    // ── clampWearBandToLiner ─────────────────────────────────────────────────

    @Test
    fun `wear band fully inside the liner is unclamped`() {
        val band = clampWearBandToLiner(spotStartMm = 10f, spotLengthMm = 20f, linerLengthMm = 100f)
        assertEquals(10f, band.startMm, 0.001f)
        assertEquals(30f, band.endMm, 0.001f)
        assertEquals(20f, band.lengthMm, 0.001f)
        assertTrue(!band.isEmpty)
    }

    @Test
    fun `wear band extending past the liner end is clamped to the liner length`() {
        val band = clampWearBandToLiner(spotStartMm = 90f, spotLengthMm = 50f, linerLengthMm = 100f)
        assertEquals(90f, band.startMm, 0.001f)
        assertEquals(100f, band.endMm, 0.001f) // clamped, not 140
        assertEquals(10f, band.lengthMm, 0.001f)
    }

    @Test
    fun `wear band entirely past the liner end clamps to an empty band`() {
        val band = clampWearBandToLiner(spotStartMm = 150f, spotLengthMm = 20f, linerLengthMm = 100f)
        assertTrue(band.isEmpty)
        assertEquals(0f, band.lengthMm, 0.001f)
    }

    @Test
    fun `wear band with negative start clamps to zero`() {
        val band = clampWearBandToLiner(spotStartMm = -5f, spotLengthMm = 20f, linerLengthMm = 100f)
        assertEquals(0f, band.startMm, 0.001f)
        assertEquals(15f, band.endMm, 0.001f)
    }

    @Test
    fun `wear band clamping never mutates the caller's original values`() {
        // clampWearBandToLiner is pure — calling it twice with the same inputs must be idempotent
        // and must not depend on any mutable state.
        val a = clampWearBandToLiner(90f, 50f, 100f)
        val b = clampWearBandToLiner(90f, 50f, 100f)
        assertEquals(a, b)
    }

    // ── wearBandToPx ─────────────────────────────────────────────────────────

    @Test
    fun `wear band px mapping applies origin offset and scale`() {
        val band = ClampedWearBandMm(startMm = 10f, endMm = 30f)
        val (x0, x1) = wearBandToPx(band, linerOriginPx = 100f, pxPerMm = 2f)
        assertEquals(120f, x0, 0.001f) // 100 + 10*2
        assertEquals(160f, x1, 0.001f) // 100 + 30*2
    }

    @Test
    fun `wear band px mapping of an empty band collapses to a zero-width point`() {
        val band = ClampedWearBandMm(startMm = 50f, endMm = 50f)
        val (x0, x1) = wearBandToPx(band, linerOriginPx = 0f, pxPerMm = 3f)
        assertEquals(x0, x1, 0.001f)
    }

    // ── computeLinerDetailPxPerMm ────────────────────────────────────────────

    @Test
    fun `pxPerMm is width-driven for a normally proportioned liner`() {
        // width/length = 800/200 = 4.0; height term = (400*0.8)/50 = 6.4 -> width wins
        val pxPerMm = computeLinerDetailPxPerMm(
            usableWidthPx = 800f, linerLengthMm = 200f, maxOdMm = 50f, usableHeightPx = 400f,
        )
        assertEquals(4.0f, pxPerMm, 0.001f)
    }

    @Test
    fun `pxPerMm is capped by height for a very short liner`() {
        // A 10mm liner: width/length = 800/10 = 80.0 (would explode); height cap =
        // (400*0.8)/50 = 6.4 must win instead.
        val pxPerMm = computeLinerDetailPxPerMm(
            usableWidthPx = 800f, linerLengthMm = 10f, maxOdMm = 50f, usableHeightPx = 400f,
        )
        assertEquals(6.4f, pxPerMm, 0.001f)
        assertTrue(pxPerMm < 80f)
    }

    @Test
    fun `pxPerMm never returns zero or negative for degenerate inputs`() {
        assertTrue(computeLinerDetailPxPerMm(0f, 0f, 0f, 0f) > 0f)
        assertTrue(computeLinerDetailPxPerMm(800f, 0f, 50f, 400f) > 0f)
        assertTrue(computeLinerDetailPxPerMm(800f, 200f, 0f, 400f) > 0f)
    }

    // ── wearStartToCanonicalMm / canonicalToWearStartMm (Chris's post-review spec) ──────────

    // Shared fixture: liner starts at shaft-space 100mm, is 300mm long (so its FWD edge sits
    // at shaft-space 400mm). AFT SET at shaft-space 0mm, FWD SET at shaft-space 500mm.
    private val linerStartFromAftMm = 100f
    private val linerLengthMm = 300f
    private val aftSetXMm = 0f
    private val fwdSetXMm = 500f

    @Test
    fun `LINER_AFT canonical start equals the entered value as-is`() {
        val canonical = wearStartToCanonicalMm(
            WearSpotReference.LINER_AFT, enteredMm = 25f, lengthMm = 40f,
            linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
        )
        assertEquals(25f, canonical, 0.001f)
    }

    @Test
    fun `LINER_FWD locates the band's FWD edge, measuring aft from the liner FWD edge`() {
        // entered=25 -> band FWD edge is 25mm aft of the liner's own FWD edge (liner-local 300),
        // i.e. at liner-local 275; the band's AFT edge (canonical) is 40mm further aft: 235.
        val canonical = wearStartToCanonicalMm(
            WearSpotReference.LINER_FWD, enteredMm = 25f, lengthMm = 40f,
            linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
        )
        assertEquals(235f, canonical, 0.001f)
    }

    @Test
    fun `AFT_SET locates the band's AFT edge, measuring fwd from the AFT SET`() {
        // entered=120 -> band AFT edge sits at shaft-space 0+120=120, which is liner-local
        // 120-100=20 (the liner starts at shaft-space 100).
        val canonical = wearStartToCanonicalMm(
            WearSpotReference.AFT_SET, enteredMm = 120f, lengthMm = 40f,
            linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
        )
        assertEquals(20f, canonical, 0.001f)
    }

    @Test
    fun `FWD_SET locates the band's FWD edge, measuring aft from the FWD SET`() {
        // entered=130 -> band FWD edge sits at shaft-space 500-130=370, liner-local 370-100=270;
        // the band's AFT edge (canonical) is 40mm further aft: 230.
        val canonical = wearStartToCanonicalMm(
            WearSpotReference.FWD_SET, enteredMm = 130f, lengthMm = 40f,
            linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
        )
        assertEquals(230f, canonical, 0.001f)
    }

    @Test
    fun `canonicalToWearStartMm is the exact algebraic inverse for every reference`() {
        val lengthMm = 40f
        val entered = listOf(0f, 25f, 123.456f, 299f)

        for (reference in WearSpotReference.values()) {
            for (e in entered) {
                val canonical = wearStartToCanonicalMm(
                    reference, e, lengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
                )
                val roundTripped = canonicalToWearStartMm(
                    reference, canonical, lengthMm, linerStartFromAftMm, linerLengthMm, aftSetXMm, fwdSetXMm,
                )
                assertEquals("reference=$reference entered=$e", e, roundTripped, 0.01f)
            }
        }
    }

    @Test
    fun `FWD SET conversion places a band correctly against a hand-computed asymmetric SET pair`() {
        // Asymmetric SETs, liner not flush with either shaft end: liner at shaft-space
        // [137, 437] (length 300), AFT SET at shaft-space -15 (excluded-thread overhang, can be
        // negative per computeSetPositionsInMeasureSpace's docs), FWD SET at shaft-space 480.
        val start = 137f
        val length = 300f
        val aftSet = -15f
        val fwdSet = 480f

        // entered=50 under FWD_SET -> band FWD edge at shaft-space 480-50=430, liner-local
        // 430-137=293; band length 20 -> canonical AFT edge = 293-20=273.
        val canonical = wearStartToCanonicalMm(
            WearSpotReference.FWD_SET, enteredMm = 50f, lengthMm = 20f,
            linerStartFromAftMm = start, linerLengthMm = length, aftSetXMm = aftSet, fwdSetXMm = fwdSet,
        )
        assertEquals(273f, canonical, 0.001f)

        // And AFT_SET entered=155 -> band AFT edge at shaft-space -15+155=140, liner-local
        // 140-137=3.
        val canonicalAft = wearStartToCanonicalMm(
            WearSpotReference.AFT_SET, enteredMm = 155f, lengthMm = 20f,
            linerStartFromAftMm = start, linerLengthMm = length, aftSetXMm = aftSet, fwdSetXMm = fwdSet,
        )
        assertEquals(3f, canonicalAft, 0.001f)
    }

    // ── wearSpotSpanIssue (Chris's blocking in-span validation) ─────────────────────────────

    private val IN_TO_MM = 25.4f

    @Test
    fun `start beyond the liner's own length is rejected (Chris's 37in-on-a-36in-liner example)`() {
        val linerLenMm = 36f * IN_TO_MM
        val startMm = 37f * IN_TO_MM
        assertTrue(wearSpotSpanIssue(startMm, lengthMm = 10f, linerLengthMm = linerLenMm) != null)
    }

    @Test
    fun `start plus length beyond the liner's length is rejected (Chris's 30in start + 12in example)`() {
        val linerLenMm = 36f * IN_TO_MM
        val startMm = 30f * IN_TO_MM
        val lengthMm = 12f * IN_TO_MM
        assertTrue(wearSpotSpanIssue(startMm, lengthMm, linerLenMm) != null)
    }

    @Test
    fun `boundary-exact band spanning the full liner is accepted`() {
        val linerLenMm = 36f * IN_TO_MM
        assertNull(wearSpotSpanIssue(canonicalStartMm = 0f, lengthMm = linerLenMm, linerLengthMm = linerLenMm))
    }

    @Test
    fun `boundary-exact band ending exactly at the liner's FWD edge is accepted`() {
        val linerLenMm = 300f
        assertNull(wearSpotSpanIssue(canonicalStartMm = 260f, lengthMm = 40f, linerLengthMm = linerLenMm))
    }

    @Test
    fun `a band comfortably inside the liner is accepted`() {
        assertNull(wearSpotSpanIssue(canonicalStartMm = 50f, lengthMm = 100f, linerLengthMm = 300f))
    }

    @Test
    fun `a negative start is rejected`() {
        assertTrue(wearSpotSpanIssue(canonicalStartMm = -5f, lengthMm = 10f, linerLengthMm = 300f) != null)
    }

    // ── isWearSpotStaleOverrun (non-blocking display classifier) ────────────────────────────

    @Test
    fun `a spot that fit when recorded is not flagged stale`() {
        assertFalse(isWearSpotStaleOverrun(canonicalStartMm = 50f, lengthMm = 100f, linerLengthMm = 300f))
    }

    @Test
    fun `a spot that no longer fits after the liner was shortened is flagged stale, not blocked`() {
        // Recorded when the liner was 300mm long (band comfortably inside); liner later
        // shortened to 120mm — the classifier must flag it (for a warning), never throw or
        // otherwise "block" (there is no commit path here — it's a pure boolean read).
        assertTrue(isWearSpotStaleOverrun(canonicalStartMm = 50f, lengthMm = 100f, linerLengthMm = 120f))
    }

    @Test
    fun `stale classifier agrees with the span-issue function it wraps`() {
        val cases = listOf(
            Triple(0f, 300f, 300f),
            Triple(0f, 301f, 300f),
            Triple(-1f, 10f, 300f),
            Triple(299f, 2f, 300f),
        )
        for ((startMm, lengthMm, linerLenMm) in cases) {
            assertEquals(
                wearSpotSpanIssue(startMm, lengthMm, linerLenMm) != null,
                isWearSpotStaleOverrun(startMm, lengthMm, linerLenMm),
            )
        }
    }
}
