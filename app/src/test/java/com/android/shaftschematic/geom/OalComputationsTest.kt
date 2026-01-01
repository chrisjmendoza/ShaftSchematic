package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OalComputationsTest {

    private fun inToMm(inches: Double): Double = inches * MM_PER_IN

    @Test
    fun `excluded aft thread shifts measure origin so following body starts at 0`() {
        val aftLen = inToMm(3.0).toFloat()
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = aftLen,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val body = Body(startFromAftMm = aftLen, lengthMm = 200f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), bodies = listOf(body))

        val win = computeOalWindow(spec)

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, 1e-9)
        assertEquals(0.0, win.toMeasureX(body.startFromAftMm.toDouble()), 1e-9)
    }

    @Test
    fun `excluded aft thread shortens OAL by its length`() {
        val aftLen = inToMm(5.5)
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = aftLen.toFloat(),
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val body = Body(startFromAftMm = aftLen.toFloat(), lengthMm = 200f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 2438.4f, threads = listOf(th), bodies = listOf(body))

        val win = computeOalWindow(spec)

        val expectedOal = spec.overallLengthMm.toDouble() - th.lengthMm.toDouble()

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, 1e-9)
        assertEquals(expectedOal, win.oalMm, 1e-3)
    }

    @Test
    fun `excluded aft thread shifts measure origin so following taper starts at 0`() {
        val aftLen = inToMm(4.0).toFloat()
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = aftLen,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val taper = Taper(
            startFromAftMm = aftLen,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), tapers = listOf(taper))

        val win = computeOalWindow(spec)

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, 1e-9)
        assertEquals(0.0, win.toMeasureX(taper.startFromAftMm.toDouble()), 1e-9)
    }

    @Test
    fun `excluded threads on both ends reduce effective OAL using per-end thread lengths`() {
        val aftLen = inToMm(3.0)
        val fwdLen = inToMm(5.5)
        val aft = Threads(
            startFromAftMm = 0f,
            lengthMm = aftLen.toFloat(),
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val overall = 1000.0
        val fwd = Threads(
            startFromAftMm = (overall - fwdLen).toFloat(),
            lengthMm = fwdLen.toFloat(),
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val spec = ShaftSpec(overallLengthMm = overall.toFloat(), threads = listOf(aft, fwd))

        val win = computeOalWindow(spec)

        val expectedStart = aft.lengthMm.toDouble()
        val expectedOal = spec.overallLengthMm.toDouble() - aft.lengthMm.toDouble() - fwd.lengthMm.toDouble()

        assertEquals(expectedStart, win.measureStartMm, 1e-9)
        assertEquals(expectedOal, win.oalMm, 1e-6)
        assertTrue("measure end must be >= measure start", win.measureEndMm >= win.measureStartMm)
    }

    @Test
    fun `effective OAL clamps to zero when excluded length exceeds overall`() {
        val overall = 50.0
        val aft = Threads(startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 10f, pitchMm = 2f, excludeFromOAL = true)
        val spec = ShaftSpec(overallLengthMm = overall.toFloat(), threads = listOf(aft))

        val win = computeOalWindow(spec)

        assertEquals(overall, win.measureStartMm, 1e-9)
        assertEquals(0.0, win.oalMm, 1e-9)
        assertTrue("measure end must be >= measure start", win.measureEndMm >= win.measureStartMm)
    }

    @Test
    fun `no excluded threads leaves OAL unchanged`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 10f, majorDiaMm = 10f, pitchMm = 2f, excludeFromOAL = false)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, 1e-9)
        assertEquals(1000.0, win.measureEndMm, 1e-9)
        assertEquals(1000.0, win.oalMm, 1e-9)
    }

    @Test
    fun `excluded internal thread does not affect OAL window`() {
        val internal = Threads(
            startFromAftMm = 200f,
            lengthMm = 50f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(internal))

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, 1e-9)
        assertEquals(1000.0, win.measureEndMm, 1e-9)
        assertEquals(1000.0, win.oalMm, 1e-9)
    }

    @Test
    fun `aft end thread detection is epsilon anchored`() {
        val withinEps = Threads(
            startFromAftMm = 0.0005f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val beyondEps = Threads(
            startFromAftMm = 0.5f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )

        val exWithin = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = 1000f, threads = listOf(withinEps)))
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.aftExcludedMm, 1e-9)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = 1000f, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.aftExcludedMm, 1e-9)
    }

    @Test
    fun `fwd end thread detection is epsilon anchored`() {
        val overall = 1000f
        val withinEps = Threads(
            startFromAftMm = (overall - 10f) - 0.0005f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val beyondEps = Threads(
            startFromAftMm = (overall - 10f) - 0.5f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )

        val exWithin = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = overall, threads = listOf(withinEps)))
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.fwdExcludedMm, 1e-9)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = overall, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.fwdExcludedMm, 1e-9)
    }
}
