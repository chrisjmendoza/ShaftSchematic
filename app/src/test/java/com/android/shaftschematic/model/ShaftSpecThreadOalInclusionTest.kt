package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [withThreadOalInclusion], the card's "Include thread in OAL" prompt: the thread goes out
 * at the end it sits at, comes in INSIDE the shaft at its end, and Shorten/Lengthen change the
 * OAL by the thread's length exactly as typing the OAL does ([withNewOal]) — FWD-measured parts
 * keep their FWD distance, AFT-measured parts stay put, and nothing else moves.
 */
class ShaftSpecThreadOalInclusionTest {

    private val eps = 1e-3f

    private val fwdTaper = Taper(
        id = "tp", startFromAftMm = 90f, lengthMm = 20f, startDiaMm = 50f, endDiaMm = 40f,
        authoredReference = LinerAuthoredReference.FWD,
    )
    private val aftLiner = Liner(id = "ln", startFromAftMm = 20f, lengthMm = 30f, odMm = 60f)

    private fun thread(start: Float, excluded: Boolean, isAftEnd: Boolean) = Threads(
        id = "th", startFromAftMm = start, lengthMm = 10f, majorDiaMm = 40f, pitchMm = 3f,
        excludeFromOAL = excluded, isAftEnd = isAftEnd,
    )

    /** FWD thread inside the shaft at `[100, 110]`, OAL 110, a FWD taper beside it. */
    private fun fwdIncluded(isAftEnd: Boolean = false) = ShaftSpec(
        overallLengthMm = 110f,
        tapers = listOf(fwdTaper),
        liners = listOf(aftLiner),
        threads = listOf(thread(100f, excluded = false, isAftEnd = isAftEnd)),
    )

    /** The same thread excluded at the FWD end, `[110, 120]` beyond OAL 110. */
    private fun fwdExcluded() = ShaftSpec(
        overallLengthMm = 110f,
        tapers = listOf(fwdTaper),
        liners = listOf(aftLiner),
        threads = listOf(thread(110f, excluded = true, isAftEnd = false)),
    )

    private fun ShaftSpec.th() = threads.single()
    private fun ShaftSpec.tp() = tapers.single()

    @Test
    fun `exclude and shorten leaves a FWD thread where it was, beyond the new FWD face`() {
        val after = fwdIncluded().withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
        assertEquals(100f, after.overallLengthMm, eps)
        assertTrue(after.th().excludeFromOAL)
        assertFalse(after.th().isAftEnd)
        assertEquals(100f, after.th().startFromAftMm, eps)
        assertEquals("FWD taper keeps its FWD distance", 80f, after.tp().startFromAftMm, eps)
        assertEquals("AFT liner never moves", aftLiner, after.liners.single())
    }

    @Test
    fun `exclude and keep hangs the thread outside the unchanged OAL`() {
        val after = fwdIncluded().withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = false)
        assertEquals(110f, after.overallLengthMm, 0f)
        assertEquals(110f, after.th().startFromAftMm, eps)
        assertEquals(fwdTaper, after.tp())
        assertEquals(aftLiner, after.liners.single())
    }

    @Test
    fun `a FWD thread with a stale AFT end goes out at the FWD end`() {
        val after = fwdIncluded(isAftEnd = true).withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = false)
        assertFalse("seeded from where the thread sits", after.th().isAftEnd)
        assertEquals(110f, after.th().startFromAftMm, eps)
    }

    @Test
    fun `an AFT thread goes out at the AFT end whatever its stored end says`() {
        for (stale in listOf(true, false)) {
            val spec = ShaftSpec(overallLengthMm = 110f, threads = listOf(thread(0f, excluded = false, isAftEnd = stale)))
            val kept = spec.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = false)
            assertTrue(kept.th().isAftEnd)
            assertEquals(-10f, kept.th().startFromAftMm, eps)
            assertEquals(110f, kept.overallLengthMm, 0f)

            val shortened = spec.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
            assertEquals(100f, shortened.overallLengthMm, eps)
            assertEquals(-10f, shortened.th().startFromAftMm, eps)
        }
    }

    @Test
    fun `include and keep places the thread inside the shaft at its FWD end`() {
        val after = fwdExcluded().withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = false)
        assertFalse(after.th().excludeFromOAL)
        assertEquals(100f, after.th().startFromAftMm, eps)
        assertEquals(110f, after.overallLengthMm, 0f)
        assertEquals(fwdTaper, after.tp())
    }

    @Test
    fun `include and lengthen leaves a FWD thread where it was, now inside the new OAL`() {
        val after = fwdExcluded().withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = true)
        assertEquals(120f, after.overallLengthMm, eps)
        assertFalse(after.th().excludeFromOAL)
        assertEquals(110f, after.th().startFromAftMm, eps)
        assertEquals("FWD taper rides the FWD face", 100f, after.tp().startFromAftMm, eps)
        assertEquals(aftLiner, after.liners.single())
    }

    @Test
    fun `an excluded AFT thread comes in at start 0`() {
        val spec = ShaftSpec(overallLengthMm = 110f, threads = listOf(thread(-10f, excluded = true, isAftEnd = true)))
        val kept = spec.withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = false)
        assertEquals(0f, kept.th().startFromAftMm, 0f)
        assertEquals(110f, kept.overallLengthMm, 0f)

        val lengthened = spec.withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = true)
        assertEquals(120f, lengthened.overallLengthMm, eps)
        assertEquals(0f, lengthened.th().startFromAftMm, 0f)
    }

    @Test
    fun `with no OAL typed the flag flips and the OAL stays 0`() {
        val spec = ShaftSpec(overallLengthMm = 0f, threads = listOf(thread(0f, excluded = false, isAftEnd = false)))
        val out = spec.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
        assertTrue(out.th().excludeFromOAL)
        assertFalse("no OAL: the stored end stands", out.th().isAftEnd)
        assertEquals(0f, out.overallLengthMm, 0f)

        val back = out.withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = true)
        assertFalse(back.th().excludeFromOAL)
        assertEquals(0f, back.overallLengthMm, 0f)
        assertEquals("never placed before the AFT face", 0f, back.th().startFromAftMm, 0f)
    }

    @Test
    fun `an unknown id or an unchanged flag returns the same spec`() {
        val spec = fwdIncluded()
        assertSame(spec, spec.withThreadOalInclusion("nope", excludeFromOAL = true, adjustOal = true))
        assertSame(spec, spec.withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = true))
    }

    @Test
    fun `exclude-shorten then include-lengthen returns the original drawing`() {
        val original = fwdIncluded(isAftEnd = false)
        val back = original
            .withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
            .withThreadOalInclusion("th", excludeFromOAL = false, adjustOal = true)
        assertEquals(original.overallLengthMm, back.overallLengthMm, eps)
        assertEquals(original.th().startFromAftMm, back.th().startFromAftMm, eps)
        assertEquals(original.th().copy(startFromAftMm = 0f), back.th().copy(startFromAftMm = 0f))
        assertEquals(original.tp().startFromAftMm, back.tp().startFromAftMm, eps)
        assertEquals(original.tp().copy(startFromAftMm = 0f), back.tp().copy(startFromAftMm = 0f))
        assertEquals(original.liners, back.liners)
    }

    @Test
    fun `a relief at its suggested end follows the thread across the move`() {
        // AFT thread in the OAL: inboard is its FWD end (reliefAtAft = false). Out at AFT keeps it
        // inboard — nothing to move. Now a FWD-end thread: suggestion AFT (true).
        val fwd = fwdIncluded().let { s ->
            s.copy(threads = listOf(s.th().copy(reliefLenMm = 3f, reliefDiaMm = 35f, reliefAtAft = true)))
        }
        val out = fwd.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
        assertTrue("still inboard (AFT) beyond the FWD face", out.th().reliefAtAft)

        // A thread that sat nearer the FWD end without touching it: the old suggestion (mid-shaft,
        // FWD relief) is read against the OLD OAL, so the relief follows to the excluded thread's
        // inboard end.
        val mid = ShaftSpec(
            overallLengthMm = 110f,
            threads = listOf(thread(90f, excluded = false, isAftEnd = true)
                .copy(reliefLenMm = 3f, reliefDiaMm = 35f, reliefAtAft = false)),
        )
        val midOut = mid.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true)
        assertFalse(midOut.th().isAftEnd)
        assertTrue("followed to the FWD-end thread's inboard end", midOut.th().reliefAtAft)

        // A relief deliberately at the outboard end stays put.
        val outboard = fwd.copy(threads = listOf(fwd.th().copy(reliefAtAft = false)))
        assertFalse(outboard.withThreadOalInclusion("th", excludeFromOAL = true, adjustOal = true).th().reliefAtAft)
    }
}
