package com.android.shaftschematic.geom

import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.undercutBlendCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body-face blends on the undercut drawings: the solid ramp stops at a cut's edge and only the
 * part the cut took away is retraced, up to the face and on along the body's flat surface.
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

    /** The same ramp on a body whose flat surface runs on to 300 mm. */
    private val rampOnBody = ramp.copy(flatToMm = 300f)

    @Test
    fun `a cut past the ramp's foot retraces the body's flat surface on to the cut's edge`() {
        val t = erasedBlendTraces(rampOnBody, listOf(cut(60f, 130f))).single()
        assertEquals(100f, t.first().xMm, 1e-4f)
        assertEquals(listOf(110f, 130f), t.drop(1).map { it.xMm })
        assertEquals(200f, t.last().diaMm, 1e-4f) // the body's own Ø along the flat
        // A cut that stops on the ramp is unchanged by the flat run beyond it.
        assertEquals(104f, erasedBlendTraces(rampOnBody, listOf(cut(60f, 104f))).single().last().xMm, 1e-4f)
    }

    @Test
    fun `a cut wholly on the flat body, clear of the ramp, is not retraced`() {
        assertTrue(erasedBlendTraces(rampOnBody, listOf(cut(150f, 180f))).isEmpty())
    }

    @Test
    fun `a FWD-face ramp retraces the flat AFT of its foot`() {
        val fwd = UndercutBlendCurve(
            atAftFace = false,
            points = listOf(SurfacePoint(290f, 200f), SurfacePoint(300f, 220f)),
            flatToMm = 100f,
        )
        val t = erasedBlendTraces(fwd, listOf(cut(270f, 340f))).single()
        assertEquals(listOf(270f, 290f, 300f), t.map { it.xMm })
        assertEquals(200f, t.first().diaMm, 1e-4f)
    }

    /** A liner 0–100 mm, Ø 228, with a square body 100–300 mm, Ø 228, butted to its FWD end. */
    private val liner = ResolvedLiner(id = "L", startMmPhysical = 0f, endMmPhysical = 100f, odMm = 228f)
    private val body = ResolvedBody("B", ResolvedComponentType.BODY, ResolvedComponentSource.AUTO, 100f, 300f, 228f)

    @Test
    fun `a square body face beside a liner retraces the body's surface a cut over the liner edge took`() {
        val face = undercutBlendCurves(listOf(liner, body), emptyList()).single()
        assertTrue(face.square)
        assertTrue(face.atAftFace)
        assertEquals(300f, face.flatToMm!!, 1e-4f)
        val t = erasedBlendTraces(face, listOf(cut(60f, 130f))).single()
        assertEquals(100f, t.first().xMm, 1e-4f)
        assertEquals(130f, t.last().xMm, 1e-4f)
        assertEquals(228f, t.last().diaMm, 1e-4f)
        // A cut on the body clear of the liner's edge, and one that stops at the edge, retrace nothing.
        assertTrue(erasedBlendTraces(face, listOf(cut(150f, 180f))).isEmpty())
        assertTrue(erasedBlendTraces(face, listOf(cut(60f, 100f))).isEmpty())
        // It draws nothing: the body's top stays square.
        assertNull(blendedBodyTop(listOf(face), startMm = 100f, endMm = 300f, diaMm = 228f))
    }

    @Test
    fun `a body face with no liner beside it is no retrace source, and two sources split the flat`() {
        assertTrue(undercutBlendCurves(listOf(body), emptyList()).isEmpty())
        val fwdLiner = ResolvedLiner(id = "L2", startMmPhysical = 300f, endMmPhysical = 400f, odMm = 228f)
        val faces = undercutBlendCurves(listOf(liner, body, fwdLiner), emptyList())
        assertEquals(2, faces.size)
        faces.forEach { assertEquals(200f, it.flatToMm!!, 1e-4f) }
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
