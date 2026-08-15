package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure worn-profile trace math (`geom/WearTraceMath.kt`): wear depth from a
 * measured diameter, the record-wide exaggeration baseline, and the traced polyline a wear band
 * hands both draw sites (liner detail strip + detail overlay).
 */
class WearTraceMathTest {

    private fun inch(v: Float) = v * 25.4f

    // ── wearDepthMm / deepestWearDepthMm ─────────────────────────────────────

    @Test
    fun `wear depth is the radial difference from nominal`() {
        assertEquals(0.5f, wearDepthMm(100f, 99f), 1e-4f)
        assertEquals(0f, wearDepthMm(100f, 100f), 1e-4f)
    }

    @Test
    fun `a reading at or above nominal has no wear depth`() {
        assertEquals(0f, wearDepthMm(100f, 101f), 1e-4f)
    }

    @Test
    fun `deepest depth ignores empty and above-nominal readings`() {
        val deepest = deepestWearDepthMm(
            listOf(
                100f to 0f,      // placed-but-empty station
                100f to 120f,    // above nominal
                100f to 99f,     // depth 0.5
                100f to 95f,     // depth 2.5 — the deepest
            )
        )
        assertEquals(2.5f, deepest, 1e-4f)
    }

    @Test
    fun `deepest depth of a record with no valued readings is zero`() {
        assertEquals(0f, deepestWearDepthMm(listOf(100f to 0f)), 1e-4f)
        assertEquals(0f, deepestWearDepthMm(emptyList()), 1e-4f)
    }

    // ── buildWearTrace ───────────────────────────────────────────────────────

    @Test
    fun `a band with no valued reading traces nothing`() {
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 100f,
            readings = emptyList(), nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertTrue(trace.isEmpty())
    }

    @Test
    fun `placed-but-empty readings are excluded`() {
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 100f,
            readings = listOf(WearTraceReading(50f, 0f)),
            nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertTrue(trace.isEmpty())
    }

    @Test
    fun `a reading outside the band is excluded`() {
        val trace = buildWearTrace(
            bandStartMm = 20f, bandLengthMm = 30f,
            readings = listOf(WearTraceReading(10f, 95f), WearTraceReading(80f, 95f)),
            nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertTrue(trace.isEmpty())
    }

    @Test
    fun `a degenerate band traces nothing`() {
        val trace = buildWearTrace(
            bandStartMm = 20f, bandLengthMm = 0f,
            readings = listOf(WearTraceReading(20f, 95f)),
            nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertTrue(trace.isEmpty())
    }

    @Test
    fun `vertices run in mm order between zero-depth band edges`() {
        val trace = buildWearTrace(
            bandStartMm = 10f, bandLengthMm = 40f,
            readings = listOf(
                WearTraceReading(40f, 96f),
                WearTraceReading(20f, 95f),
                WearTraceReading(30f, 97f),
            ),
            nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertEquals(listOf(10f, 20f, 30f, 40f, 50f), trace.map { it.localMm })
        assertEquals(0f, trace.first().depthFrac, 1e-6f)
        assertEquals(0f, trace.last().depthFrac, 1e-6f)
        assertTrue(trace.drop(1).dropLast(1).all { it.depthFrac > 0f })
    }

    @Test
    fun `the deepest reading draws at the cap and shallower ones scale by their true ratio`() {
        // The on-device practice case: an 11" liner measured down to 10.5" at its worst.
        val nominal = inch(11f)
        val readings = listOf(
            WearTraceReading(10f, inch(10.5f)),
            WearTraceReading(20f, inch(10.535f)),
            WearTraceReading(30f, inch(10.618f)),
            WearTraceReading(40f, inch(10.618f)),
        )
        val deepest = deepestWearDepthMm(readings.map { nominal to it.diaMm })
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 50f,
            readings = readings, nominalOdMm = nominal, deepestDepthMm = deepest,
        )
        val byMm = trace.associate { it.localMm to it.depthFrac }
        assertEquals(WEAR_TRACE_MAX_DEPTH_FRAC, byMm.getValue(10f), 1e-5f)
        assertEquals((11f - 10.535f) / (11f - 10.5f) * WEAR_TRACE_MAX_DEPTH_FRAC, byMm.getValue(20f), 1e-5f)
        assertEquals((11f - 10.618f) / (11f - 10.5f) * WEAR_TRACE_MAX_DEPTH_FRAC, byMm.getValue(30f), 1e-5f)
        assertEquals(byMm.getValue(30f), byMm.getValue(40f), 1e-6f)
    }

    @Test
    fun `a wear deeper than the cap keeps its true proportion`() {
        // 45 mm of radius gone on a 100 mm shaft: true scale is 0.9, far past the 0.25 cap.
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 20f,
            readings = listOf(WearTraceReading(10f, 10f)),
            nominalOdMm = 100f, deepestDepthMm = 45f,
        )
        assertEquals(0.9f, trace.single { it.localMm == 10f }.depthFrac, 1e-5f)
    }

    @Test
    fun `a reading at or above nominal inside a worn band contributes a surface vertex`() {
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 40f,
            readings = listOf(
                WearTraceReading(10f, 100f),   // at nominal
                WearTraceReading(20f, 95f),    // worn — what makes the band trace at all
            ),
            nominalOdMm = 100f, deepestDepthMm = 2.5f,
        )
        assertEquals(listOf(0f, 10f, 20f, 40f), trace.map { it.localMm })
        assertEquals(0f, trace.single { it.localMm == 10f }.depthFrac, 1e-6f)
        assertEquals(WEAR_TRACE_MAX_DEPTH_FRAC, trace.single { it.localMm == 20f }.depthFrac, 1e-5f)
    }

    @Test
    fun `a zero exaggeration baseline still draws true-scale depth`() {
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 20f,
            readings = listOf(WearTraceReading(10f, 90f)),
            nominalOdMm = 100f, deepestDepthMm = 0f,
        )
        assertEquals(5f / 50f, trace.single { it.localMm == 10f }.depthFrac, 1e-5f)
    }

    // ── sequenceWearTraces ───────────────────────────────────────────────────

    @Test
    fun `sequencing concatenates bands left to right and drops empties`() {
        val a = listOf(WearTraceVertex(0f, 0f), WearTraceVertex(5f, 0.2f), WearTraceVertex(10f, 0f))
        val b = listOf(WearTraceVertex(20f, 0f), WearTraceVertex(25f, 0.1f), WearTraceVertex(30f, 0f))
        val seq = sequenceWearTraces(listOf(a, emptyList(), b))
        assertEquals(listOf(0f, 5f, 10f, 20f, 25f, 30f), seq.map { it.localMm })
    }

    @Test
    fun `an overlapping band that would run backwards is skipped`() {
        val a = listOf(WearTraceVertex(0f, 0f), WearTraceVertex(10f, 0.2f), WearTraceVertex(20f, 0f))
        val overlapping = listOf(WearTraceVertex(5f, 0f), WearTraceVertex(12f, 0.3f), WearTraceVertex(25f, 0f))
        val seq = sequenceWearTraces(listOf(a, overlapping))
        assertEquals(listOf(0f, 10f, 20f), seq.map { it.localMm })
    }
}
