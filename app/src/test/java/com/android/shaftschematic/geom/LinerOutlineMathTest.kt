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

    // Liner 1000–1500 mm, OD 254.
    private val liner = UndercutLinerOd("L", 1000f, 1500f, 254f)

    private fun notch(id: String, start: Float, end: Float, floor: Float) =
        UndercutNotch(id, start, end, floor, profiles = emptyList())

    @Test
    fun `a liner no cut touches draws nothing`() {
        assertTrue(linerOutlineSegments(liner, emptyList()).isEmpty())
        // A cut on the body elsewhere erases nothing of this liner.
        assertTrue(linerOutlineSegments(liner, listOf(notch("b", 2000f, 2100f, 200f))).isEmpty())
    }

    @Test
    fun `a cut inside the liner retraces only the OD over its span`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1100f, 1200f, 240f)))
        assertEquals(listOf(LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1100f, 1200f, 254f, 254f)), out)
    }

    @Test
    fun `a cut crossing the FWD edge retraces the erased OD stretch and one full-height FWD face`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1400f, 1600f, 220f)))
        val surface = out.single { it.edge == LinerOutlineEdge.SURFACE }
        assertEquals(1400f, surface.x0Mm, 1e-4f)
        assertEquals(1500f, surface.x1Mm, 1e-4f)
        // ONE piece at the edge, top OD to bottom OD through the body — not bounded by the cut
        // floor (220), and the body's seat Ø outside the edge never enters (on-device report: a
        // Ø247.65 seat under the Ø254 OD had left a 3.2 mm face that read as missing).
        assertEquals(
            LinerOutlineSeg(LinerOutlineEdge.FULL_FACE, 1500f, 1500f, 254f, 254f),
            out.single { it.edge == LinerOutlineEdge.FULL_FACE },
        )
    }

    @Test
    fun `two cuts nested across the FWD edge still retrace one full-height face`() {
        val out = linerOutlineSegments(
            liner,
            listOf(notch("p", 1400f, 1700f, 235f), notch("c", 1450f, 1550f, 215f)),
        )
        assertEquals(
            LinerOutlineSeg(LinerOutlineEdge.FULL_FACE, 1500f, 1500f, 254f, 254f),
            out.single { it.edge == LinerOutlineEdge.FULL_FACE },
        )
    }

    @Test
    fun `a cut across the AFT edge retraces one full-height face at the AFT edge`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 950f, 1050f, 240f)))
        assertEquals(
            LinerOutlineSeg(LinerOutlineEdge.FULL_FACE, 1000f, 1000f, 254f, 254f),
            out.single { it.edge == LinerOutlineEdge.FULL_FACE },
        )
    }

    @Test
    fun `a cut across both edges retraces a full-height face at each`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 900f, 1600f, 220f)))
        assertEquals(
            listOf(1000f, 1500f),
            out.filter { it.edge == LinerOutlineEdge.FULL_FACE }.map { it.x0Mm },
        )
    }

    @Test
    fun `a cut ending at the edge leaves the face alone`() {
        val out = linerOutlineSegments(liner, listOf(notch("a", 1400f, 1500f, 240f)))
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
    fun `a cut across a shouldered FWD end follows the shoulder step and the full face spans the shoulder OD`() {
        // FWD shoulder 1450–1500 at Ø240 on the Ø254 liner.
        val shouldered = UndercutLinerOd(
            "L", 1000f, 1500f, 254f,
            surface = listOf(SurfaceSeg(1000f, 1450f, 254f, 254f), SurfaceSeg(1450f, 1500f, 240f, 240f)),
        )
        val out = linerOutlineSegments(shouldered, listOf(notch("a", 1400f, 1600f, 220f)))
        assertEquals(
            listOf(
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1400f, 1450f, 254f, 254f),
                LinerOutlineSeg(LinerOutlineEdge.SURFACE, 1450f, 1500f, 240f, 240f),
                LinerOutlineSeg(LinerOutlineEdge.STEP, 1450f, 1450f, 240f, 254f),
                LinerOutlineSeg(LinerOutlineEdge.FULL_FACE, 1500f, 1500f, 240f, 240f),
            ),
            out,
        )
    }
}
