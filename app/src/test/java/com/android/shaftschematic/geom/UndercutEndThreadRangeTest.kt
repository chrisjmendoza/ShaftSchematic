package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An undercut strip that reaches a shaft end draws on over the end thread there
 * ([withUndercutEndThreads], on-device request: the shop's sketches always show the thread so it is
 * clear which end the section is at) — a thread excluded from the OAL included, whose span lies past
 * `0` or the OAL. A strip that stops short of the end, or a shaft with no thread past it, keeps its
 * range exactly. Chain datums never move.
 */
class UndercutEndThreadRangeTest {

    private val oal = 8858.25f

    /** The on-device job's AFT end: a 6 1/8" thread excluded from the OAL, behind the AFT taper. */
    private val aftThread = Threads(
        id = "t", startFromAftMm = -155.575f, lengthMm = 155.575f, majorDiaMm = 139.7f,
        excludeFromOAL = true, isAftEnd = true,
    )

    @Test
    fun `an end at the AFT face runs on over an excluded AFT thread`() {
        assertEquals(-155.575f to 1500f, withUndercutEndThreads(0f, 1500f, oal, aftEndMm = -155.575f))
    }

    @Test
    fun `an end at the FWD face runs on over an excluded FWD thread`() {
        assertEquals(5000f to oal + 120f, withUndercutEndThreads(5000f, oal, oal, fwdEndMm = oal + 120f))
    }

    @Test
    fun `an end short of the shaft end is unchanged`() {
        assertEquals(10f to 1500f, withUndercutEndThreads(10f, 1500f, oal, aftEndMm = -155.575f))
        assertEquals(5000f to oal - 10f, withUndercutEndThreads(5000f, oal - 10f, oal, fwdEndMm = oal + 120f))
    }

    @Test
    fun `no thread past the end leaves the range unchanged`() {
        assertEquals(0f to oal, withUndercutEndThreads(0f, oal, oal))
        // A hair inside the end stays where it is: nothing snaps it to the face.
        assertEquals(0.0005f to 1500f, withUndercutEndThreads(0.0005f, 1500f, oal))
    }

    @Test
    fun `the drawn shaft ends come from the drawn extent`() {
        val aft = ShaftSpec(overallLengthMm = oal, threads = listOf(aftThread))
        assertEquals(-155.575f to oal, undercutDrawnShaftEndsMm(aft))
        val fwd = ShaftSpec(
            overallLengthMm = oal,
            threads = listOf(aftThread.copy(startFromAftMm = oal, lengthMm = 120f, isAftEnd = false)),
        )
        assertEquals(0f to oal + 120f, undercutDrawnShaftEndsMm(fwd))
        // An end thread counted IN the OAL lies inside the shaft: nothing to run on to.
        val included = ShaftSpec(
            overallLengthMm = oal,
            threads = listOf(aftThread.copy(startFromAftMm = oal - 120f, lengthMm = 120f, excludeFromOAL = false)),
        )
        assertEquals(0f to oal, undercutDrawnShaftEndsMm(included))
    }

    @Test
    fun `a liner strip drawn to the AFT shaft end takes the excluded thread and keeps its chain`() {
        // The on-device job: AFT taper 0 → 18 3/8", the liner butted to it, three cuts in it.
        val liner = UndercutLinerSpan("L", 466.725f, 1385.8875f)
        val cuts = listOf(
            UndercutSpanMm("a", 492.125f, 968.375f),
            UndercutSpanMm("b", 968.375f, 1371.6f),
            UndercutSpanMm("c", 1371.6f, 1433.5125f),
        )
        fun strip(aftEnd: Float) = linerStripFor(
            liner, cuts, oal, cutSpans = cuts,
            reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 0f, fwdSetMm = oal,
            aftReachFromMm = 466.725f, fwdReachFromMm = oal,
            aftEndMm = aftEnd,
        )
        val without = strip(0f)
        val with = strip(-155.575f)
        assertEquals(0f, without.drawStartMm, 0f)
        assertEquals(-155.575f, with.drawStartMm, 0f)
        assertEquals(without.drawEndMm, with.drawEndMm, 0f)
        assertEquals(without.chainStartMm, with.chainStartMm, 0f)
        assertEquals(without.chainEndMm, with.chainEndMm, 0f)
    }

    @Test
    fun `an included FWD thread already lies inside a strip drawn to the FWD end`() {
        // Thread counted in the OAL: its span is [OAL − 120, OAL], so a range at the OAL holds it.
        val liner = UndercutLinerSpan("L", oal - 900f, oal - 400f)
        val cut = listOf(UndercutSpanMm("a", oal - 800f, oal - 700f))
        val ends = undercutDrawnShaftEndsMm(
            ShaftSpec(
                overallLengthMm = oal,
                threads = listOf(aftThread.copy(startFromAftMm = oal - 120f, lengthMm = 120f, excludeFromOAL = false)),
            ),
        )
        val s = linerStripFor(
            liner, cut, oal, cutSpans = cut,
            reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 0f, fwdSetMm = oal - 120f,
            aftEndMm = ends.first, fwdEndMm = ends.second,
        )
        assertEquals(oal, s.drawEndMm, 0f)
        assertEquals(oal - 120f, s.chainEndMm, 0f)
    }

    @Test
    fun `a strip that stops short of the shaft end is unchanged`() {
        val liner = UndercutLinerSpan("L", 3749.675f, 4786.3125f)
        val cut = listOf(UndercutSpanMm("a", 3900f, 4000f))
        val plain = linerStripFor(liner, cut, oal, cutSpans = cut)
        val withThread = linerStripFor(liner, cut, oal, cutSpans = cut, aftEndMm = -155.575f, fwdEndMm = oal + 120f)
        assertEquals(plain, withThread)
    }

    @Test
    fun `a free strip clamped at the AFT face takes the thread, its window untouched`() {
        val cut = listOf(UndercutSpanMm("a", 10f, 40f))
        val plain = buildUndercutStrips(cut, emptyList(), oal).single() as UndercutStrip.FreeStrip
        val with = buildUndercutStrips(cut, emptyList(), oal, aftEndMm = -155.575f).single() as UndercutStrip.FreeStrip
        assertEquals(0f, plain.drawStartMm, 0f)
        assertEquals(-155.575f, with.drawStartMm, 0f)
        assertEquals(plain.window, with.window)
    }

    @Test
    fun `the canvas's previewed range keeps the thread while a draft is live`() {
        val liner = UndercutLinerSpan("L", 466.725f, 1385.8875f)
        val cuts = listOf(UndercutSpanMm("a", 492.125f, 968.375f))
        val strip = linerStripFor(
            liner, cuts, oal, cutSpans = cuts,
            reachToSetMm = UNDERCUT_SET_REACH_MM, aftSetMm = 0f, fwdSetMm = oal,
            aftReachFromMm = 466.725f, aftEndMm = -155.575f,
        )
        val moved = listOf(UndercutSpanMm("a", 500f, 980f))
        val (start, _) = undercutPreviewDrawRange(strip, moved, oal, cutSpans = moved, aftEndMm = -155.575f)
        assertEquals(-155.575f, start, 0f)
        // Without the shaft's ends the previewed range clamps back to the face — the canvas passes them.
        val (plainStart, _) = undercutPreviewDrawRange(strip, moved, oal, cutSpans = moved)
        assertEquals(0f, plainStart, 0f)
    }
}
