package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.suggestedThreadReliefAtAft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ONE split of a thread's stored span into hatched thread + relief groove, read by every
 * thread draw site and by the surface envelope. Pins: no relief without both values; the relief
 * end; drawn clamps that never touch the stored values; the hatch span as the complement (empty
 * when the relief takes the whole thread); and the surface segs at TRUE stored mm.
 */
class ThreadReliefMathTest {

    private val eps = 1e-4f

    // A 100 mm thread from x = 40 on a 60 mm major Ø.
    private fun relief(len: Float, dia: Float, atAft: Boolean) =
        threadReliefSpan(40f, 100f, 60f, len, dia, atAft)

    private fun hatch(len: Float, dia: Float, atAft: Boolean) =
        threadHatchSpan(40f, 100f, 60f, len, dia, atAft)

    @Test
    fun `no relief unless length and diameter are both set`() {
        assertNull(relief(0f, 0f, atAft = false))
        assertNull(relief(10f, 0f, atAft = false))
        assertNull(relief(0f, 50f, atAft = false))
        assertNull(relief(-5f, 50f, atAft = false))
    }

    @Test
    fun `a thread with no length or diameter carries no relief`() {
        assertNull(threadReliefSpan(40f, 0f, 60f, 10f, 50f, false))
        assertNull(threadReliefSpan(40f, 100f, 0f, 10f, 50f, false))
    }

    @Test
    fun `a fwd relief sits at the thread's fwd end`() {
        val r = relief(10f, 52f, atAft = false)!!
        assertEquals(130f, r.startMm, eps)
        assertEquals(140f, r.endMm, eps)
        assertEquals(52f, r.diaMm, eps)
        assertEquals(140f, r.shoulderEndMm(reliefAtAft = false), eps)
    }

    @Test
    fun `an aft relief sits at the thread's aft end`() {
        val r = relief(10f, 52f, atAft = true)!!
        assertEquals(40f, r.startMm, eps)
        assertEquals(50f, r.endMm, eps)
        assertEquals(40f, r.shoulderEndMm(reliefAtAft = true), eps)
    }

    @Test
    fun `a relief longer than the thread draws the thread's length`() {
        val r = relief(250f, 52f, atAft = false)!!
        assertEquals(40f, r.startMm, eps)
        assertEquals(140f, r.endMm, eps)
    }

    @Test
    fun `a relief at or above the major diameter draws below it`() {
        assertEquals(60f * THREAD_RELIEF_INVALID_DIA_FRAC, relief(10f, 60f, atAft = false)!!.diaMm, eps)
        assertEquals(60f * THREAD_RELIEF_INVALID_DIA_FRAC, relief(10f, 75f, atAft = false)!!.diaMm, eps)
        // A legitimate relief just under the major Ø is drawn as typed.
        assertEquals(59.5f, relief(10f, 59.5f, atAft = false)!!.diaMm, eps)
    }

    @Test
    fun `the drawn clamps never rewrite the stored values`() {
        val th = Threads(startFromAftMm = 40f, lengthMm = 100f, majorDiaMm = 60f,
            reliefLenMm = 250f, reliefDiaMm = 75f)
        th.reliefSpan()
        th.hatchSpan()
        assertEquals(250f, th.reliefLenMm, 0f)
        assertEquals(75f, th.reliefDiaMm, 0f)
    }

    @Test
    fun `the hatch span is the whole thread without a relief`() {
        val h = hatch(0f, 0f, atAft = false)!!
        assertEquals(40f, h.startMm, eps)
        assertEquals(140f, h.endMm, eps)
    }

    @Test
    fun `the hatch span is the thread minus the relief`() {
        val fwd = hatch(10f, 52f, atAft = false)!!
        assertEquals(40f, fwd.startMm, eps)
        assertEquals(130f, fwd.endMm, eps)
        val aft = hatch(10f, 52f, atAft = true)!!
        assertEquals(50f, aft.startMm, eps)
        assertEquals(140f, aft.endMm, eps)
    }

    @Test
    fun `a relief that takes the whole thread leaves nothing to hatch`() {
        assertNull(hatch(100f, 52f, atAft = false))
        assertNull(hatch(400f, 52f, atAft = true))
    }

    @Test
    fun `no hatch span for a thread with no length`() {
        assertNull(threadHatchSpan(40f, 0f, 60f, 0f, 0f, false))
    }

    @Test
    fun `surface segs are the major envelope without a relief`() {
        val segs = threadSurfaceSegs(40f, 100f, 60f, 0f, 0f, false)
        assertEquals(listOf(SurfaceSeg(40f, 140f, 60f, 60f)), segs)
    }

    @Test
    fun `surface segs put the groove at its own diameter at true mm`() {
        assertEquals(
            listOf(SurfaceSeg(40f, 130f, 60f, 60f), SurfaceSeg(130f, 140f, 52f, 52f)),
            threadSurfaceSegs(40f, 100f, 60f, 10f, 52f, reliefAtAft = false),
        )
        assertEquals(
            listOf(SurfaceSeg(40f, 50f, 52f, 52f), SurfaceSeg(50f, 140f, 60f, 60f)),
            threadSurfaceSegs(40f, 100f, 60f, 10f, 52f, reliefAtAft = true),
        )
    }

    @Test
    fun `surface segs clamp the groove exactly as drawn`() {
        val segs = threadSurfaceSegs(40f, 100f, 60f, 10f, 80f, reliefAtAft = false)
        assertEquals(60f * THREAD_RELIEF_INVALID_DIA_FRAC, segs.last().diaStartMm, eps)
        assertEquals(listOf(SurfaceSeg(40f, 140f, 54f, 54f)),
            threadSurfaceSegs(40f, 100f, 60f, 500f, 54f, reliefAtAft = false))
    }

    // ── suggestedThreadReliefAtAft ──────────────────────────────────────────────

    @Test
    fun `an aft-end thread puts its relief fwd, against the SET`() {
        assertFalse(suggestedThreadReliefAtAft(0f, 150f, excludeFromOAL = false, isAftEnd = true, overallLengthMm = 5000f))
        assertFalse(suggestedThreadReliefAtAft(-150f, 150f, excludeFromOAL = true, isAftEnd = true, overallLengthMm = 5000f))
    }

    @Test
    fun `a fwd-end thread puts its relief aft`() {
        assertTrue(suggestedThreadReliefAtAft(4850f, 150f, excludeFromOAL = false, isAftEnd = true, overallLengthMm = 5000f))
        assertTrue(suggestedThreadReliefAtAft(5000f, 150f, excludeFromOAL = true, isAftEnd = false, overallLengthMm = 5000f))
    }

    @Test
    fun `a mid-shaft thread puts its relief fwd`() {
        assertFalse(suggestedThreadReliefAtAft(2000f, 150f, excludeFromOAL = false, isAftEnd = true, overallLengthMm = 5000f))
        // No OAL typed yet: nothing to call the FWD end.
        assertFalse(suggestedThreadReliefAtAft(2000f, 150f, excludeFromOAL = false, isAftEnd = true, overallLengthMm = 0f))
    }
}
