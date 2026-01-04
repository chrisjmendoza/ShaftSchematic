package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS_EXACT = 1e-6
private const val EPS_LOOSE = 1e-3

class OalComputationsTest {

    private fun inToMm(inches: Double): Double = inches * 25.4

    private fun aftEndThread(spec: ShaftSpec): Threads =
        spec.threads.first { th -> abs(th.startFromAftMm.toDouble() - 0.0) <= EPS_LOOSE }

    private fun fwdEndThread(spec: ShaftSpec): Threads =
        spec.threads.first { th ->
            val end = (th.startFromAftMm + th.lengthMm).toDouble()
            abs(end - spec.overallLengthMm.toDouble()) <= EPS_LOOSE
        }

    private fun makeSpec(
        overallIn: Double,
        aftThreadIn: Double? = null,
        fwdThreadIn: Double? = null,
        excludeAft: Boolean = false,
        excludeFwd: Boolean = false,
        applyExcludeFromOalFlags: Boolean = true,
    ): ShaftSpec {
        // NOTE: Production `computeOalWindow()` consumes only per-thread `excludeFromOAL` flags.
        // This helper can optionally *not* apply those flags to simulate an "exclude flags off" case.
        val overallMm = inToMm(overallIn).toFloat()

        val threads = buildList {
            if (fwdThreadIn != null) {
                val lenMm = inToMm(fwdThreadIn).toFloat()
                add(
                    Threads(
                        startFromAftMm = overallMm - lenMm,
                        lengthMm = lenMm,
                        majorDiaMm = 50f,
                        pitchMm = 2f,
                        excludeFromOAL = applyExcludeFromOalFlags && excludeFwd
                    )
                )
            }
            if (aftThreadIn != null) {
                val lenMm = inToMm(aftThreadIn).toFloat()
                add(
                    Threads(
                        startFromAftMm = 0f,
                        lengthMm = lenMm,
                        majorDiaMm = 50f,
                        pitchMm = 2f,
                        excludeFromOAL = applyExcludeFromOalFlags && excludeAft
                    )
                )
            }
        }

        return ShaftSpec(
            overallLengthMm = overallMm,
            threads = threads,
        )
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

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

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, EPS_EXACT)
        assertEquals(0.0, win.toMeasureX(body.startFromAftMm.toDouble()), EPS_EXACT)
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

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, EPS_EXACT)
        assertEquals(expectedOal, win.oalMm, EPS_LOOSE)
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

        assertEquals(th.lengthMm.toDouble(), win.measureStartMm, EPS_EXACT)
        assertEquals(0.0, win.toMeasureX(taper.startFromAftMm.toDouble()), EPS_EXACT)
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

        assertEquals(expectedStart, win.measureStartMm, EPS_EXACT)
        assertEquals(expectedOal, win.oalMm, EPS_LOOSE)
        assertTrue("measure end must be >= measure start", win.measureEndMm >= win.measureStartMm)
    }

    @Test
    fun `excludeFromOAL flags off leaves OAL unchanged`() {
        val spec = makeSpec(
            overallIn = 96.0,
            aftThreadIn = 5.0,
            excludeAft = true,
            applyExcludeFromOalFlags = false
        )

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble(), win.oalMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble(), win.measureEndMm, EPS_EXACT)
    }

    @Test
    fun `aft-only excluded follows OalWindow contract`() {
        val spec = makeSpec(
            overallIn = 96.0,
            aftThreadIn = 5.0,
            excludeAft = true,
            applyExcludeFromOalFlags = true
        )
        val aftMm = aftEndThread(spec).lengthMm.toDouble()

        val win = computeOalWindow(spec)

        assertEquals(aftMm, win.measureStartMm, EPS_LOOSE)
        assertEquals(spec.overallLengthMm.toDouble() - aftMm, win.oalMm, EPS_LOOSE)
        assertTrue(win.measureEndMm >= win.measureStartMm)
        assertEquals(0.0, win.toMeasureX(aftMm), EPS_EXACT)
    }

    @Test
    fun `fwd-only excluded shortens window end`() {
        val spec = makeSpec(
            overallIn = 96.0,
            fwdThreadIn = 6.5,
            excludeFwd = true,
            applyExcludeFromOalFlags = true
        )
        val fwdMm = fwdEndThread(spec).lengthMm.toDouble()

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble() - fwdMm, win.oalMm, EPS_LOOSE)
        assertEquals(win.oalMm, win.measureEndMm - win.measureStartMm, EPS_EXACT)
        assertTrue(win.measureEndMm >= win.measureStartMm)
    }

    @Test
    fun `effective OAL clamps to zero when excluded length exceeds overall`() {
        val spec = makeSpec(
            overallIn = 8.0,
            aftThreadIn = 5.5,
            fwdThreadIn = 5.0,
            excludeAft = true,
            excludeFwd = true,
            applyExcludeFromOalFlags = true
        )

        val win = computeOalWindow(spec)

        val expectedStart = aftEndThread(spec).lengthMm.toDouble()
        assertEquals(expectedStart, win.measureStartMm, EPS_LOOSE)
        assertEquals(0.0, win.oalMm, EPS_EXACT)
        assertEquals(win.measureStartMm, win.measureEndMm, EPS_EXACT)
        assertTrue("measure end must be >= measure start", win.measureEndMm >= win.measureStartMm)
    }

    @Test
    fun `no excluded threads leaves OAL unchanged`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 10f, majorDiaMm = 10f, pitchMm = 2f, excludeFromOAL = false)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(1000.0, win.measureEndMm, EPS_EXACT)
        assertEquals(1000.0, win.oalMm, EPS_EXACT)
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

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(1000.0, win.measureEndMm, EPS_EXACT)
        assertEquals(1000.0, win.oalMm, EPS_EXACT)
    }

    @Test
    fun `internal excluded thread does not affect end-excluded OAL`() {
        val base = makeSpec(
            overallIn = 96.0,
            aftThreadIn = 5.0,
            excludeAft = true,
            applyExcludeFromOalFlags = true
        )
        val internal = Threads(
            startFromAftMm = inToMm(10.0).toFloat(),
            lengthMm = inToMm(2.0).toFloat(),
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val spec = base.copy(threads = base.threads + internal)

        val win = computeOalWindow(spec)
        val aftMm = aftEndThread(spec).lengthMm.toDouble()

        assertEquals(aftMm, win.measureStartMm, EPS_LOOSE)
        assertEquals(spec.overallLengthMm.toDouble() - aftMm, win.oalMm, EPS_LOOSE)
    }

    @Test
    fun `json round trip preserves excludeFromOAL and affects computeOalWindow`() {
        val spec = makeSpec(
            overallIn = 96.0,
            aftThreadIn = 5.0,
            excludeAft = true,
            applyExcludeFromOalFlags = true
        )

        val raw = json.encodeToString(spec)
        val decoded = json.decodeFromString<ShaftSpec>(raw)

        val aft = aftEndThread(decoded)
        assertTrue(aft.excludeFromOAL)

        val win = computeOalWindow(decoded)
        assertEquals(decoded.overallLengthMm.toDouble() - aft.lengthMm.toDouble(), win.oalMm, EPS_LOOSE)
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
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.aftExcludedMm, EPS_EXACT)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = 1000f, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.aftExcludedMm, EPS_EXACT)
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
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.fwdExcludedMm, EPS_EXACT)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = overall, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.fwdExcludedMm, EPS_EXACT)
    }
}
