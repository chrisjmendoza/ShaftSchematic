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

    @Test
    fun `a lowered cap scales the whole trace down without touching true scale`() {
        // The same 11" liner as above at the 5% low end: the deepest reading follows the cap,
        // and every shallower one keeps its ratio to it.
        val nominal = inch(11f)
        val readings = listOf(
            WearTraceReading(10f, inch(10.5f)),
            WearTraceReading(20f, inch(10.535f)),
        )
        val deepest = deepestWearDepthMm(readings.map { nominal to it.diaMm })
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 50f,
            readings = readings, nominalOdMm = nominal, deepestDepthMm = deepest,
            maxDepthFrac = WEAR_TRACE_MIN_DEPTH_FRAC,
        )
        val byMm = trace.associate { it.localMm to it.depthFrac }
        assertEquals(WEAR_TRACE_MIN_DEPTH_FRAC, byMm.getValue(10f), 1e-5f)
        assertEquals(
            (11f - 10.535f) / (11f - 10.5f) * WEAR_TRACE_MIN_DEPTH_FRAC,
            byMm.getValue(20f), 1e-5f,
        )
    }

    @Test
    fun `true scale still wins at the lowest cap`() {
        // 45 mm of radius gone on a 100 mm shaft: true scale is 0.9. Lowering the cap may not
        // draw a monstrous wear shallower than it truly is.
        val trace = buildWearTrace(
            bandStartMm = 0f, bandLengthMm = 20f,
            readings = listOf(WearTraceReading(10f, 10f)),
            nominalOdMm = 100f, deepestDepthMm = 45f,
            maxDepthFrac = WEAR_TRACE_MIN_DEPTH_FRAC,
        )
        assertEquals(0.9f, trace.single { it.localMm == 10f }.depthFrac, 1e-5f)
    }

    // ── effectiveWearTraceDepthFrac (per-job override over the global default) ─

    @Test
    fun `no per-job override follows the global default`() {
        assertEquals(0.12f, effectiveWearTraceDepthFrac(null, 0.12f), 1e-6f)
        assertEquals(
            WEAR_TRACE_MAX_DEPTH_FRAC,
            effectiveWearTraceDepthFrac(null, WEAR_TRACE_MAX_DEPTH_FRAC), 1e-6f,
        )
    }

    @Test
    fun `a per-job override wins over the global default`() {
        assertEquals(0.08f, effectiveWearTraceDepthFrac(0.08f, 0.25f), 1e-6f)
    }

    @Test
    fun `the effective value is coerced into the settable range at both ends`() {
        assertEquals(WEAR_TRACE_MIN_DEPTH_FRAC, effectiveWearTraceDepthFrac(0f, 0.25f), 1e-6f)
        assertEquals(WEAR_TRACE_MAX_DEPTH_FRAC, effectiveWearTraceDepthFrac(0.9f, 0.25f), 1e-6f)
        // A stored-out-of-range global is clamped too, not just the override.
        assertEquals(WEAR_TRACE_MIN_DEPTH_FRAC, effectiveWearTraceDepthFrac(null, -1f), 1e-6f)
        assertEquals(WEAR_TRACE_MAX_DEPTH_FRAC, effectiveWearTraceDepthFrac(null, 5f), 1e-6f)
    }

    @Test
    fun `the settable range runs from 5 to 25 percent`() {
        assertEquals(0.05f, WEAR_TRACE_MIN_DEPTH_FRAC, 1e-6f)
        assertEquals(0.25f, WEAR_TRACE_MAX_DEPTH_FRAC, 1e-6f)
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

    // ── smoothWearTrace (monotone cubic, no overshoot) ───────────────────────

    /** A band-shaped run: surface edge, three worn stations, surface edge. */
    private fun band(vararg pairs: Pair<Float, Float>) =
        pairs.map { (mm, depth) -> WearTraceVertex(mm, depth) }

    @Test
    fun `smoothing passes exactly through every input vertex in order`() {
        val verts = band(0f to 0f, 10f to 0.10f, 20f to 0.25f, 30f to 0.08f, 40f to 0f)
        val smooth = smoothWearTrace(verts)
        assertTrue("smoothing must densify the run", smooth.size > verts.size)
        // Every original vertex survives, verbatim and in order — the drawn depth at a station
        // stays exactly what buildWearTrace computed.
        var next = 0
        smooth.forEach { v -> if (next < verts.size && v == verts[next]) next++ }
        assertEquals("every input vertex must appear, in order", verts.size, next)
    }

    @Test
    fun `smoothing never overshoots a segment's endpoint depths`() {
        // A deep dip between shallow neighbours — the case an unlimited cubic (Catmull-Rom)
        // would bulge past, drawing metal that was never measured away (or a hump above the
        // surface just outside the pit).
        val verts = band(0f to 0f, 10f to 0.02f, 20f to 0.24f, 30f to 0.02f, 40f to 0f)
        val smooth = smoothWearTrace(verts)
        // Walk the smoothed run segment by segment between the original vertices.
        var vi = 0
        smooth.forEach { s ->
            while (vi < verts.size - 2 && s.localMm > verts[vi + 1].localMm) vi++
            val lo = minOf(verts[vi].depthFrac, verts[vi + 1].depthFrac)
            val hi = maxOf(verts[vi].depthFrac, verts[vi + 1].depthFrac)
            assertTrue(
                "sample at ${s.localMm} = ${s.depthFrac} escaped [$lo, $hi]",
                s.depthFrac >= lo - 1e-6f && s.depthFrac <= hi + 1e-6f,
            )
        }
    }

    @Test
    fun `the zero-depth gap between two bands stays exactly on the surface`() {
        val a = band(0f to 0f, 5f to 0.2f, 10f to 0f)
        val b = band(20f to 0f, 25f to 0.1f, 30f to 0f)
        val smooth = smoothWearTrace(sequenceWearTraces(listOf(a, b)))
        smooth.filter { it.localMm in 10f..20f }.forEach {
            assertEquals("the shaft between two bands is unworn", 0f, it.depthFrac, 0f)
        }
    }

    @Test
    fun `an equal-x vertical jump is kept verbatim`() {
        // A reading landing exactly on a band edge: two vertices at the same mm, different
        // depths. There is no slope to fit, so the pair passes through untouched.
        val verts = band(0f to 0f, 10f to 0f, 10f to 0.2f, 20f to 0.25f, 30f to 0f)
        val smooth = smoothWearTrace(verts)
        val atTen = smooth.filter { it.localMm == 10f }
        assertEquals(listOf(0f, 0.2f), atTen.map { it.depthFrac })
    }

    @Test
    fun `a run too short to curve is returned unchanged`() {
        val two = band(0f to 0f, 10f to 0.2f)
        assertEquals(two, smoothWearTrace(two))
        assertEquals(emptyList<WearTraceVertex>(), smoothWearTrace(emptyList()))
    }

    @Test
    fun `smoothed localMm never runs backwards`() {
        val verts = band(0f to 0f, 3f to 0.05f, 3f to 0.2f, 12f to 0.25f, 40f to 0.01f, 50f to 0f)
        val smooth = smoothWearTrace(verts)
        smooth.zipWithNext().forEach { (a, b) ->
            assertTrue("localMm ran backwards: ${a.localMm} → ${b.localMm}", b.localMm >= a.localMm)
        }
    }

    @Test
    fun `a smoothed band actually curves between its stations`() {
        // The point of the whole pass: the midpoint of a segment must not sit on the straight
        // chord between its stations (on-device request — real wear flows, it does not bevel).
        val verts = band(0f to 0f, 10f to 0.10f, 20f to 0.25f, 30f to 0.10f, 40f to 0f)
        val smooth = smoothWearTrace(verts)
        val mid = smooth.first { it.localMm > 5f }
        val chord = 0.10f * (mid.localMm / 10f)
        assertTrue("the lead-in must ease rather than run straight", kotlin.math.abs(mid.depthFrac - chord) > 1e-4f)
    }

    @Test
    fun `the sample count is the shipped constant`() {
        assertEquals(16, WEAR_TRACE_SMOOTH_SAMPLES)
        val verts = band(0f to 0f, 10f to 0.2f, 20f to 0f)
        // 3 vertices → 2 segments, each carrying WEAR_TRACE_SMOOTH_SAMPLES interior samples.
        assertEquals(3 + 2 * WEAR_TRACE_SMOOTH_SAMPLES, smoothWearTrace(verts).size)
    }
}
