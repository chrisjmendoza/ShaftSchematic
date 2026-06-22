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
    ): ShaftSpec {
        val overallMm = inToMm(overallIn).toFloat()
        val threads = buildList {
            if (fwdThreadIn != null) {
                val lenMm = inToMm(fwdThreadIn).toFloat()
                add(Threads(startFromAftMm = overallMm - lenMm, lengthMm = lenMm,
                    majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = excludeFwd))
            }
            if (aftThreadIn != null) {
                val lenMm = inToMm(aftThreadIn).toFloat()
                add(Threads(startFromAftMm = 0f, lengthMm = lenMm,
                    majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = excludeAft))
            }
        }
        return ShaftSpec(overallLengthMm = overallMm, threads = threads)
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ─── computeOalWindow — immutable-OAL contract ────────────────────────────
    // The window always equals the user's input. excludeFromOAL never mutates it.

    @Test
    fun `window always spans full input when aft thread excluded`() {
        val aftLen = inToMm(5.0).toFloat()
        val th = Threads(startFromAftMm = 0f, lengthMm = aftLen, majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), bodies = listOf(
            Body(startFromAftMm = aftLen, lengthMm = 200f, diaMm = 40f)
        ))

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(1000.0, win.measureEndMm, EPS_EXACT)
        assertEquals(1000.0, win.oalMm, EPS_EXACT)
    }

    @Test
    fun `window always spans full input when both end threads excluded`() {
        val spec = makeSpec(overallIn = 96.0, aftThreadIn = 5.0, fwdThreadIn = 6.5,
            excludeAft = true, excludeFwd = true)

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble(), win.oalMm, EPS_LOOSE)
        assertEquals(spec.overallLengthMm.toDouble(), win.measureEndMm, EPS_LOOSE)
    }

    @Test
    fun `window always spans full input when fwd thread excluded`() {
        val spec = makeSpec(overallIn = 96.0, fwdThreadIn = 6.5, excludeFwd = true)

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble(), win.oalMm, EPS_LOOSE)
        assertEquals(spec.overallLengthMm.toDouble(), win.measureEndMm, EPS_LOOSE)
    }

    @Test
    fun `window unchanged when threads are included in OAL`() {
        val spec = makeSpec(overallIn = 96.0, aftThreadIn = 5.0, excludeAft = false)

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(spec.overallLengthMm.toDouble(), win.oalMm, EPS_LOOSE)
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
        val internal = Threads(startFromAftMm = 200f, lengthMm = 50f, majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(internal))

        val win = computeOalWindow(spec)

        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
        assertEquals(1000.0, win.measureEndMm, EPS_EXACT)
        assertEquals(1000.0, win.oalMm, EPS_EXACT)
    }

    @Test
    fun `json round trip preserves excludeFromOAL flag and window still spans full input`() {
        val spec = makeSpec(overallIn = 96.0, aftThreadIn = 5.0, excludeAft = true)

        val raw = json.encodeToString(spec)
        val decoded = json.decodeFromString<ShaftSpec>(raw)

        val aft = aftEndThread(decoded)
        assertTrue(aft.excludeFromOAL)

        val win = computeOalWindow(decoded)
        assertEquals(decoded.overallLengthMm.toDouble(), win.oalMm, EPS_LOOSE)
        assertEquals(0.0, win.measureStartMm, EPS_EXACT)
    }

    // ─── computeExcludedThreadLengths ─────────────────────────────────────────
    // Unchanged helper — still correctly identifies end-thread engagement lengths.

    @Test
    fun `aft end thread detection is epsilon anchored`() {
        val withinEps = Threads(startFromAftMm = 0.0005f, lengthMm = 10f,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val beyondEps = Threads(startFromAftMm = 0.5f, lengthMm = 10f,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)

        val exWithin = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = 1000f, threads = listOf(withinEps)))
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.aftExcludedMm, EPS_EXACT)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = 1000f, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.aftExcludedMm, EPS_EXACT)
    }

    @Test
    fun `fwd end thread detection is epsilon anchored`() {
        val overall = 1000f
        val withinEps = Threads(startFromAftMm = (overall - 10f) - 0.0005f, lengthMm = 10f,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val beyondEps = Threads(startFromAftMm = (overall - 10f) - 0.5f, lengthMm = 10f,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)

        val exWithin = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = overall, threads = listOf(withinEps)))
        assertEquals(withinEps.lengthMm.toDouble(), exWithin.fwdExcludedMm, EPS_EXACT)

        val exBeyond = computeExcludedThreadLengths(ShaftSpec(overallLengthMm = overall, threads = listOf(beyondEps)))
        assertEquals(0.0, exBeyond.fwdExcludedMm, EPS_EXACT)
    }

    // ─── computeSetPositionsInMeasureSpace ────────────────────────────────────
    // With measureStartMm = 0 always, SET positions are physical shaft coordinates.

    @Test
    fun `SET is at physical taper start regardless of excludeFromOAL`() {
        val aftThreadLen = inToMm(5.0).toFloat()
        val taperLen = inToMm(16.0).toFloat()
        val th = Threads(startFromAftMm = 0f, lengthMm = aftThreadLen,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val tp = Taper(startFromAftMm = aftThreadLen, lengthMm = taperLen, startDiaMm = 60f, endDiaMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), tapers = listOf(tp))

        val win = computeOalWindow(spec)
        val sets = computeSetPositionsInMeasureSpace(win, spec)

        // SET is at the physical taper start (thread length from shaft end)
        assertEquals(aftThreadLen.toDouble(), sets.aftSETxMm, EPS_LOOSE)
        // FWD SET defaults to window end (no FWD taper)
        assertEquals(win.oalMm, sets.fwdSETxMm, EPS_EXACT)
    }

    @Test
    fun `SET position is identical whether thread is included or excluded`() {
        val aftThreadLen = inToMm(5.0).toFloat()
        val taperLen = inToMm(16.0).toFloat()
        val tp = Taper(startFromAftMm = aftThreadLen, lengthMm = taperLen, startDiaMm = 60f, endDiaMm = 100f)

        fun setsFor(excluded: Boolean): SetPositions {
            val th = Threads(startFromAftMm = 0f, lengthMm = aftThreadLen,
                majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = excluded)
            val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), tapers = listOf(tp))
            return computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        }

        val setsExcluded = setsFor(true)
        val setsIncluded = setsFor(false)

        assertEquals(setsIncluded.aftSETxMm, setsExcluded.aftSETxMm, EPS_EXACT)
        assertEquals(setsIncluded.fwdSETxMm, setsExcluded.fwdSETxMm, EPS_EXACT)
    }

    @Test
    fun `SET positions default to window bounds when no tapers present`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        val win = computeOalWindow(spec)
        val sets = computeSetPositionsInMeasureSpace(win, spec)

        assertEquals(0.0, sets.aftSETxMm, EPS_EXACT)
        assertEquals(win.oalMm, sets.fwdSETxMm, EPS_EXACT)
    }

    @Test
    fun `SET at physical position zero when taper starts at shaft AFT end`() {
        // Taper starting at x=0 with an overlapping excluded thread — SET is at shaft AFT face
        val excludedThread = Threads(startFromAftMm = 0f, lengthMm = 100f,
            majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val taper = Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 60f, endDiaMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(excludedThread), tapers = listOf(taper))

        val win = computeOalWindow(spec)
        assertEquals(0.0, win.measureStartMm, EPS_EXACT)

        val sets = computeSetPositionsInMeasureSpace(win, spec)

        // Taper starts at physical x=0 — SET is at the shaft AFT face
        assertEquals(0.0, sets.aftSETxMm, EPS_EXACT)
    }
}
