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
    fun `no band, no reading, or a reading on another component traces nothing`() {
        assertTrue(wearLinerTraces(listOf(fwd), emptyList(), listOf(reading(177f)), 0.25f).isEmpty())
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
}
