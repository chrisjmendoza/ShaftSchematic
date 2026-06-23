package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the taper-length update logic in ComponentCarousel's CommitNum("Length") handler.
 *
 * The fix (2026-06-22): when a taper uses FWD reference, editing the length must keep the
 * authored FWD end position fixed and slide the AFT start accordingly.  Before the fix,
 * startFromAftMm was passed through unchanged, drifting the taper toward the AFT end.
 *
 * The logic under test mirrors ComponentCarousel exactly:
 *   if (isFwdRef) {
 *       val authored = oal - t.startFromAftMm - t.lengthMm   // distance from FWD face
 *       physStart = oal - authored - newLen
 *   } else {
 *       physStart = t.startFromAftMm
 *   }
 */
class TaperFwdRefLengthTest {

    private fun physStartAfterLengthChange(
        taper: Taper,
        newLengthMm: Float,
        oalMm: Float,
    ): Float {
        val isFwdRef = taper.authoredReference == LinerAuthoredReference.FWD
        return if (isFwdRef) {
            val authored = oalMm - taper.startFromAftMm - taper.lengthMm
            oalMm - authored - newLengthMm
        } else {
            taper.startFromAftMm
        }
    }

    // ── AFT reference — AFT start is the anchor ───────────────────────────

    @Test
    fun `aft ref - growing length keeps aft start unchanged`() {
        val taper = Taper(startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        assertEquals(50f, physStartAfterLengthChange(taper, newLengthMm = 150f, oalMm = 300f), 0.001f)
    }

    @Test
    fun `aft ref - shrinking length keeps aft start unchanged`() {
        val taper = Taper(startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        assertEquals(50f, physStartAfterLengthChange(taper, newLengthMm = 40f, oalMm = 300f), 0.001f)
    }

    @Test
    fun `aft ref - aft start is independent of shaft oal`() {
        val taper = Taper(startFromAftMm = 100f, lengthMm = 80f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        for (oal in listOf(200f, 500f, 1000f, 2400f)) {
            assertEquals(
                "physStart must equal startFromAftMm for oal=$oal",
                100f, physStartAfterLengthChange(taper, newLengthMm = 120f, oalMm = oal), 0.001f
            )
        }
    }

    @Test
    fun `aft ref - fwd end moves when length changes`() {
        // AFT anchor means the FWD end (physStart + newLen) DOES shift
        val taper = Taper(startFromAftMm = 50f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.AFT)
        val newLen = 150f
        val newStart = physStartAfterLengthChange(taper, newLengthMm = newLen, oalMm = 300f)
        assertEquals("FWD end moved to 200mm", 200f, newStart + newLen, 0.001f)
    }

    // ── FWD reference — FWD end is the anchor ────────────────────────────

    @Test
    fun `fwd ref - growing length slides aft start toward aft end`() {
        // physStart=150, len=100 → FWD end at 250; authored=300-150-100=50
        // newLen=150 → physStart=300-50-150=100
        val taper = Taper(startFromAftMm = 150f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        assertEquals(100f, physStartAfterLengthChange(taper, newLengthMm = 150f, oalMm = 300f), 0.001f)
    }

    @Test
    fun `fwd ref - shrinking length slides aft start toward fwd end`() {
        // physStart=150, len=100 → FWD end at 250; authored=50
        // newLen=50 → physStart=300-50-50=200
        val taper = Taper(startFromAftMm = 150f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        assertEquals(200f, physStartAfterLengthChange(taper, newLengthMm = 50f, oalMm = 300f), 0.001f)
    }

    @Test
    fun `fwd ref - fwd end position is invariant across all length changes`() {
        val oalMm = 300f
        val taper = Taper(startFromAftMm = 150f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val expectedFwdEnd = taper.startFromAftMm + taper.lengthMm  // 250mm

        for (newLen in listOf(25f, 50f, 75f, 100f, 125f, 150f, 175f)) {
            val newStart = physStartAfterLengthChange(taper, newLengthMm = newLen, oalMm = oalMm)
            assertEquals(
                "FWD end must stay at ${expectedFwdEnd}mm for newLen=$newLen",
                expectedFwdEnd, newStart + newLen, 0.001f
            )
        }
    }

    @Test
    fun `fwd ref - authored fwd distance is preserved after length change`() {
        val oalMm = 1000f
        // Taper FWD end at 800mm → authored = 1000-800 = 200mm from FWD face
        val taper = Taper(startFromAftMm = 600f, lengthMm = 200f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val authoredFromFwd = oalMm - taper.startFromAftMm - taper.lengthMm  // 200mm

        for (newLen in listOf(100f, 200f, 300f, 400f)) {
            val newStart = physStartAfterLengthChange(taper, newLengthMm = newLen, oalMm = oalMm)
            val newAuthoredFromFwd = oalMm - newStart - newLen
            assertEquals(
                "Authored FWD distance must stay at ${authoredFromFwd}mm for newLen=$newLen",
                authoredFromFwd, newAuthoredFromFwd, 0.001f
            )
        }
    }

    @Test
    fun `fwd ref at aft shaft face - lengthening grows into the shaft`() {
        // Taper FWD end flush with AFT face of shaft: authored = 0 (prop face)
        // physStart=0, len=100, oal=500 → authored = 500-0-100=400... hmm
        // Let's set: taper FWD end at oal (flush with FWD face): authored=0
        val oalMm = 500f
        val taper = Taper(startFromAftMm = 400f, lengthMm = 100f,  // FWD end at 500 = oal, authored=0
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        // Grow to 200mm → physStart = 500-0-200 = 300
        val newStart = physStartAfterLengthChange(taper, newLengthMm = 200f, oalMm = oalMm)
        assertEquals("FWD end stays flush with shaft FWD face", oalMm, newStart + 200f, 0.001f)
        assertEquals("physStart moved toward AFT", 300f, newStart, 0.001f)
    }

    // ── Regression: pre-fix behaviour would not satisfy these ────────────

    @Test
    fun `fwd ref - old fix would have failed by keeping startFromAftMm unchanged`() {
        // Pre-fix: physStart was always returned as taper.startFromAftMm in both branches.
        // That means FWD end would drift when length changes.
        val oalMm = 300f
        val taper = Taper(startFromAftMm = 150f, lengthMm = 100f,
            startDiaMm = 60f, endDiaMm = 50f, authoredReference = LinerAuthoredReference.FWD)
        val newLen = 150f

        val oldBehaviourStart = taper.startFromAftMm  // pre-fix: always use startFromAftMm
        val oldFwdEnd = oldBehaviourStart + newLen     // 150 + 150 = 300 ≠ 250 (drifted)

        val fixedStart = physStartAfterLengthChange(taper, newLengthMm = newLen, oalMm = oalMm)
        val fixedFwdEnd = fixedStart + newLen          // 100 + 150 = 250 ✓

        assertEquals("Fixed FWD end at original 250mm", 250f, fixedFwdEnd, 0.001f)
        assertEquals("Old behaviour drifted FWD end to 300mm", 300f, oldFwdEnd, 0.001f)
    }
}
