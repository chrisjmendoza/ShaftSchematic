package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the undercut sheet's liner outline (`geom/LinerOutlineMath.kt`): the dotted retrace of a
 * liner's original OD and end faces covers exactly the stretches its cuts erased, so the canvas
 * and the PDF, which both draw from it, show where a cut ran past the liner into the body.
 */
class LinerOutlineMathTest {

    // Liner 1000–1500 mm, OD 254, on a 228.6 body each side.
    private val liner = UndercutLinerOd("L", 1000f, 1500f, 254f)
    private val segs = listOf(
        SurfaceSeg(0f, 1000f, 228.6f, 228.6f),
        SurfaceSeg(1000f, 1500f, 254f, 254f),
        SurfaceSeg(1500f, 3000f, 228.6f, 228.6f),
    )

    private fun notch(id: String, start: Float, end: Float, floor: Float) =
        UndercutNotch(id, start, end, floor, profiles = emptyList())

    @Test
    fun `a liner no cut touches draws nothing`() {
        assertTrue(linerOutlineSegments(liner, emptyList(), segs).isEmpty())
        // A cut on the body elsewhere erases nothing of this liner.
        assertTrue(linerOutlineSegments(liner, listOf(notch("b", 2000f, 2100f, 200f)), segs).isEmpty())
    }

    @Test
    fun `a cut inside the liner retraces only the OD over its span`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1100f, 1200f, 240f)), segs)
        assertEquals(listOf(LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1100f, 1200f, 254f, 254f)), out)
    }

    @Test
    fun `a cut crossing the FWD edge retraces the erased OD stretch and the FWD face`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1400f, 1600f, 220f)), segs)
        val surface = out.single { it.edge == LinerOutlineEdge.SURFACE }
        assertEquals(1400f, surface.x0Mm, 1e-4f)
        assertEquals(1500f, surface.x1Mm, 1e-4f)
        val face = out.single { it.edge == LinerOutlineEdge.FWD_FACE }
        assertEquals(1500f, face.x0Mm, 1e-4f)
        // The floor (220) sits below the body seat (228.6): the face is retraced from the seat,
        // where the original face stood, up to the OD.
        assertEquals(228.6f, face.dia0Mm, 1e-4f)
        assertEquals(254f, face.dia1Mm, 1e-4f)
        assertTrue(out.none { it.edge == LinerOutlineEdge.AFT_FACE })
    }

    @Test
    fun `a face erased only down to a floor above the seat is retraced from that floor`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 950f, 1050f, 240f)), segs)
        val face = out.single { it.edge == LinerOutlineEdge.AFT_FACE }
        assertEquals(240f, face.dia0Mm, 1e-4f)
        assertEquals(254f, face.dia1Mm, 1e-4f)
    }

    @Test
    fun `a cut ending at the edge leaves the face alone`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1400f, 1500f, 240f)), segs)
        assertTrue(out.none { it.edge != LinerOutlineEdge.SURFACE })
    }

    @Test
    fun `touching, overlapping and nested cuts merge into one OD stretch`() {
        val out = linerOutlineSegments(
            liner,
            listOf(
                notch("p", 1100f, 1300f, 240f),
                notch("c", 1150f, 1200f, 230f),
                notch("n", 1300f, 1350f, 245f),
                notch("far", 1400f, 1420f, 245f),
            ),
            segs,
        )
        assertEquals(
            listOf(
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1100f, 1350f, 254f, 254f),
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1400f, 1420f, 254f, 254f),
            ),
            out,
        )
    }

    @Test
    fun `a cut across a shouldered FWD end follows the shoulder step and rises to the shoulder OD`() {
        // FWD shoulder 1450–1500 at Ø240 on the Ø254 liner.
        val shouldered = UndercutLinerOd(
            "L", 1000f, 1500f, 254f,
            surface = listOf(SurfaceSeg(1000f, 1450f, 254f, 254f), SurfaceSeg(1450f, 1500f, 240f, 240f)),
        )
        val out = linerOutlineSegments(shouldered, listOf(notch("a", 1400f, 1600f, 220f)), segs)
        assertEquals(
            listOf(
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1400f, 1450f, 254f, 254f),
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1450f, 1500f, 240f, 240f),
                LinerOutlineSeg(LinerOutlineEdge.STEP, 1450f, 1450f, 240f, 254f),
                LinerOutlineSeg(LinerOutlineEdge.FWD_FACE, 1500f, 1500f, 228.6f, 240f),
            ),
            out,
        )
    }
}
