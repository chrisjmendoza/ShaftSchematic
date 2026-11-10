package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole-shaft trace builder behind the wear sheet's main profile and the Wear tab's shaft
 * canvas — the two sites that answered the "Trace depth exaggeration" slider with nothing
 * (on-device report).
 */
class WearLinerTracesTest {

    private val aft = WearTraceLiner("aft", odMm = 190.5f, lengthMm = 895f)
    private val fwd = WearTraceLiner("fwd", odMm = 177.8f, lengthMm = 879f)
    private val band = Triple("fwd", 300f, 200f)
    private fun reading(dia: Float) = Triple("fwd", 400f, dia)

    @Test
    fun `a liner with a band and a valued reading traces at the slider depth`() {
        val shallow = wearLinerTraces(listOf(aft, fwd), listOf(band), listOf(reading(177f)), maxDepthFrac = 0.05f)
        val deep = wearLinerTraces(listOf(aft, fwd), listOf(band), listOf(reading(177f)), maxDepthFrac = 0.25f)
        assertEquals(setOf("fwd"), shallow.keys)
        assertEquals(setOf("fwd"), deep.keys)
        // The deepest (only) reading draws exactly at the cap, so the slider moves the trace.
        assertEquals(0.05f, shallow.getValue("fwd").maxOf { it.depthFrac }, 1e-6f)
        assertEquals(0.25f, deep.getValue("fwd").maxOf { it.depthFrac }, 1e-6f)
        // The band opens and closes at the surface, inside the liner.
        val verts = deep.getValue("fwd")
        assertEquals(300f, verts.first().localMm, 1e-3f)
        assertEquals(0f, verts.first().depthFrac, 1e-6f)
        assertEquals(500f, verts.last().localMm, 1e-3f)
        assertEquals(0f, verts.last().depthFrac, 1e-6f)
    }

    @Test
    fun `no reading, or a reading on another component traces nothing`() {
        assertTrue(wearLinerTraces(listOf(fwd), listOf(band), emptyList(), 0.25f).isEmpty())
        assertTrue(wearLinerTraces(listOf(fwd), listOf(band), listOf(Triple("taper", 400f, 150f)), 0.25f).isEmpty())
        // A placed-but-empty station (Ø 0) is not a measurement.
        assertTrue(wearLinerTraces(listOf(fwd), listOf(band), listOf(reading(0f)), 0.25f).isEmpty())
    }

    @Test
    fun `every liner exaggerates against the record's deepest reading`() {
        val traces = wearLinerTraces(
            liners = listOf(aft, fwd),
            spots = listOf(Triple("aft", 100f, 200f), band),
            readings = listOf(Triple("aft", 200f, 189.5f), reading(176.8f)),
            maxDepthFrac = 0.2f,
        )
        // AFT wear 0.5 mm radial, FWD 0.5 mm radial: equal depths draw equal, both at the cap.
        assertEquals(0.2f, traces.getValue("aft").maxOf { it.depthFrac }, 1e-6f)
        assertEquals(0.2f, traces.getValue("fwd").maxOf { it.depthFrac }, 1e-6f)
        val half = wearLinerTraces(
            liners = listOf(aft, fwd),
            spots = listOf(Triple("aft", 100f, 200f), band),
            readings = listOf(Triple("aft", 200f, 190f), reading(176.8f)),
            maxDepthFrac = 0.2f,
        )
        // Half the wear on AFT draws half as deep as the FWD liner that sets the baseline.
        assertEquals(0.1f, half.getValue("aft").maxOf { it.depthFrac }, 1e-6f)
    }

    @Test
    fun `a band past the liner end is clamped for drawing only`() {
        val traces = wearLinerTraces(listOf(fwd), listOf(Triple("fwd", 800f, 500f)), listOf(Triple("fwd", 850f, 177f)), 0.25f)
        assertEquals(879f, traces.getValue("fwd").last().localMm, 1e-3f)
    }

    @Test
    fun `a reading with no wear band still traces across the whole liner`() {
        // On-device report: a 9.973" reading on a 10" liner changed nothing without a wear area.
        val liner = WearTraceLiner("ln", odMm = 254f, lengthMm = 600f)
        val traces = wearLinerTraces(listOf(liner), emptyList(), listOf(Triple("ln", 300f, 253.31f)), 0.25f)
        val verts = traces.getValue("ln")
        assertEquals(0f, verts.first().localMm, 1e-3f)
        assertEquals(0f, verts.first().depthFrac, 1e-6f)
        assertEquals(600f, verts.last().localMm, 1e-3f)
        assertEquals(0f, verts.last().depthFrac, 1e-6f)
        assertEquals(0.25f, verts.maxOf { it.depthFrac }, 1e-6f)
    }

    @Test
    fun `a reading outside a band traces in its gap and the band keeps its own trace`() {
        val t = buildLinerWearTrace(
            linerLengthMm = 879f,
            bands = listOf(300f to 200f),
            readings = listOf(WearTraceReading(400f, 177f), WearTraceReading(700f, 177.4f)),
            nominalOdMm = 177.8f,
            deepestDepthMm = 0.4f,
            maxDepthFrac = 0.2f,
        )
        // The band's own trace (its grey fill) is unchanged: 300 → 500, at the cap.
        assertEquals(300f, t.bandTraces.single().first().localMm, 1e-3f)
        assertEquals(500f, t.bandTraces.single().last().localMm, 1e-3f)
        assertEquals(0.2f, t.bandTraces.single().maxOf { it.depthFrac }, 1e-6f)
        // The run goes on past the band to the reading at 700 and back up at the liner end.
        assertEquals(879f, t.run.last().localMm, 1e-3f)
        val beyond = t.run.filter { it.localMm > 500f }
        assertEquals(0.1f, beyond.maxOf { it.depthFrac }, 1e-4f)
        // Never reverses.
        t.run.zipWithNext().forEach { (a, b) -> assertTrue(b.localMm >= a.localMm) }
        // The AFT gap has no reading, so the run starts at the band.
        assertEquals(300f, t.run.first().localMm, 1e-3f)
    }

    @Test
    fun `a reading on a band edge belongs to the band and is not traced twice`() {
        val t = buildLinerWearTrace(
            linerLengthMm = 879f,
            bands = listOf(300f to 200f),
            readings = listOf(WearTraceReading(500f, 177f)),
            nominalOdMm = 177.8f,
            deepestDepthMm = 0.4f,
            maxDepthFrac = 0.2f,
        )
        assertEquals(t.bandTraces.single(), t.run)
    }

    @Test
    fun `overlapping bands leave no gap between them`() {
        val t = buildLinerWearTrace(
            linerLengthMm = 879f,
            bands = listOf(100f to 300f, 200f to 300f),
            readings = listOf(WearTraceReading(450f, 177f)),
            nominalOdMm = 177.8f,
            deepestDepthMm = 0.4f,
            maxDepthFrac = 0.2f,
        )
        // 450 is inside the second band only; the first band (100–400) has nothing to trace and
        // the union 100–500 leaves no gap that could re-trace it.
        assertTrue(t.bandTraces[0].isEmpty())
        assertEquals(t.bandTraces[1], t.run)
    }
}
