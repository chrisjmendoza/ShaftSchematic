package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body-face blends on the undercut drawings: the solid ramp stops at a cut's edge and only the
 * part the cut took away is retraced dotted, up to the face.
 */
class UndercutBlendMathTest {

    /** A 45° ramp on a body's AFT face at 100 mm: Ø 220 at the face down to Ø 200 at 110 mm. */
    private val ramp = UndercutBlendCurve(
        atAftFace = true,
        points = listOf(SurfacePoint(100f, 220f), SurfacePoint(110f, 200f)),
    )

    /** A cut whose one drawn region is `[regionStart, regionEnd]` (its whole span by default). */
    private fun cut(start: Float, end: Float, regionStart: Float = start, regionEnd: Float = end) = UndercutNotch(
        "c", start, end, 190f,
        listOf(NotchProfile(regionStart, regionEnd, 190f, listOf(SurfacePoint(regionStart, 220f), SurfacePoint(regionEnd, 220f)))),
    )

    @Test
    fun `a blend no cut reaches has nothing to retrace`() {
        assertTrue(erasedBlendTraces(ramp, listOf(cut(20f, 100f))).isEmpty())
        assertTrue(erasedBlendTraces(ramp, emptyList()).isEmpty())
    }

    @Test
    fun `a cut over the liner edge retraces the ramp from the face to the cut's edge only`() {
        // The cut runs from inside the liner (60) across the face (100) to 104 on the ramp.
        val traces = erasedBlendTraces(ramp, listOf(cut(60f, 104f)))
        assertEquals(1, traces.size)
        val t = traces.single()
        assertEquals(100f, t.first().xMm, 1e-4f)
        assertEquals(220f, t.first().diaMm, 1e-4f) // meets the liner's face
        assertEquals(104f, t.last().xMm, 1e-4f)
        assertEquals(212f, t.last().diaMm, 1e-3f) // where the solid ramp now stops
    }

    @Test
    fun `only the region a cut really removed is retraced`() {
        // The cut's span reaches 108, but its floor meets the ramp at 105: the ramp past it still stands.
        val t = erasedBlendTraces(ramp, listOf(cut(60f, 108f, regionEnd = 105f))).single()
        assertEquals(105f, t.last().xMm, 1e-4f)
    }

    @Test
    fun `touching and nested cuts retrace as one stretch`() {
        val traces = erasedBlendTraces(ramp, listOf(cut(90f, 103f), cut(103f, 106f), cut(101f, 102f)))
        assertEquals(1, traces.size)
        assertEquals(100f, traces.single().first().xMm, 1e-4f)
        assertEquals(106f, traces.single().last().xMm, 1e-4f)
    }

    @Test
    fun `clipping keeps the vertices inside and adds the stations asked for`() {
        val pts = listOf(SurfacePoint(0f, 10f), SurfacePoint(10f, 20f), SurfacePoint(20f, 20f))
        val clipped = clipSurfacePolyline(pts, 5f, 18f, stations = listOf(7f, 30f))
        assertEquals(listOf(5f, 7f, 10f, 18f), clipped.map { it.xMm })
        assertEquals(listOf(15f, 17f, 20f, 20f), clipped.map { it.diaMm })
        assertTrue(clipSurfacePolyline(pts, 25f, 30f).isEmpty())
    }

    @Test
    fun `a body takes the ramp that starts at its own face`() {
        val top = blendedBodyTop(listOf(ramp), startMm = 100f, endMm = 300f, diaMm = 200f)
        assertNotNull(top)
        assertEquals(220f, top!!.aftFaceDiaMm, 1e-4f)
        assertEquals(200f, top.fwdFaceDiaMm, 1e-4f)
        assertEquals(listOf(100f, 110f, 300f), top.points.map { it.xMm })
        // Another run of the shaft, and a body whose AFT face is elsewhere, stay square.
        assertNull(blendedBodyTop(listOf(ramp), startMm = 400f, endMm = 500f, diaMm = 200f))
        assertNull(blendedBodyTop(emptyList(), startMm = 100f, endMm = 300f, diaMm = 200f))
    }
}
