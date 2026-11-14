package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.pdf.undercutPdfFillPlan
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Depth shading (`PdfPrefs.undercutDepthShade`): each notch's [UndercutNotch.depthMm] is its TRUE
 * depth below its section's reference surface (a run of touching/nested cuts, measured from the
 * highest surface it spans), and [withDepthShadeStretched] places every cut of a list between that
 * list's shallowest (0) and deepest (1), which [undercutDepthShadeAlpha] ramps light → dark.
 */
class UndercutDepthShadeTest {

    private val oalMm = 2000f
    private val surfaceDiaMm = 200f
    private val segs = listOf(SurfaceSeg(0f, oalMm, surfaceDiaMm, surfaceDiaMm))
    private val eps = 1e-4f
    private val inch = 25.4f

    private fun cut(id: String, startMm: Float, lengthMm: Float, diaMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)

    @Test
    fun `the shallowest cut is the light end and the deepest the dark end`() {
        val shallow = cut("s", 100f, 50f, diaMm = 196f)
        val mid = cut("m", 200f, 50f, diaMm = 193f)
        val deep = cut("d", 300f, 50f, diaMm = 190f)
        val built = buildUndercutNotches(listOf(shallow, mid, deep), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0f, built.getValue("s").depthFrac, eps)
        assertEquals(0.5f, built.getValue("m").depthFrac, eps)
        assertEquals(1f, built.getValue("d").depthFrac, eps)
        assertEquals(10f, built.getValue("d").depthMm, eps)
    }

    @Test
    fun `a nested cut shades darker than the cut it sits in`() {
        val parent = cut("p", 200f, 200f, diaMm = 194f)
        val child = cut("c", 250f, 50f, diaMm = 188f)
        val built = buildUndercutNotches(listOf(parent, child), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0f, built.getValue("p").depthFrac, eps)
        assertEquals(1f, built.getValue("c").depthFrac, eps)
        assertTrue(
            undercutDepthShadeAlpha(built.getValue("c").depthFrac) >
                undercutDepthShadeAlpha(built.getValue("p").depthFrac),
        )
    }

    @Test
    fun `a run off a liner end onto the body shades by floor dia, measured from the liner OD`() {
        // On-device report: a 6.260" cut on a liner shaded DARKER than the 6.045" and 5.996" cuts
        // butted to it, because those two ran past the liner end and were measured from the smaller
        // body. One butted run is one section: every cut is measured from the liner OD.
        val bodyDia = 6.375f * inch
        val linerDia = 6.75f * inch
        val segs = listOf(
            SurfaceSeg(0f, oalMm, bodyDia, bodyDia),
            SurfaceSeg(100f, 912f, linerDia, linerDia),
        )
        val cuts = listOf(
            cut("long", 100f, 811f, 6.260f * inch),
            cut("a", 911f, 14f, 6.045f * inch),  // straddles the liner end
            cut("b", 925f, 14f, 5.996f * inch),  // on the body
            cut("c", 939f, 38f, 6.187f * inch),
            cut("d", 977f, 16f, 6.291f * inch),
        )
        val built = buildUndercutNotches(cuts, segs, oalMm, 0.25f)
        val frac = built.associate { it.id to it.depthFrac }
        val byFloor = cuts.sortedBy { it.diaMm }.map { it.id }
        val byShade = frac.entries.sortedByDescending { it.value }.map { it.key }
        assertEquals("deeper (smaller floor Ø) shades darker", byFloor, byShade)
        // And DRAWS deeper (on-device report: the 5.996" cut off the liner end drew taller than the
        // 6.045" one on the liner, its drawn depth measured from the smaller body).
        val byDrawnFloor = built.sortedBy { it.floorDiaMm }.map { it.id }
        assertEquals("deeper (smaller floor Ø) draws deeper", byFloor, byDrawnFloor)
        assertEquals(1f, frac.getValue("b"), eps)
        assertEquals(0f, frac.getValue("d"), eps)
    }

    @Test
    fun `butted cuts of different dia always draw the line between them`() {
        // On-device report: a 6.187" and a 6.291" cut butted together drew within a pixel of each
        // other once exaggerated, the face fell under the minimum step, and the line vanished. The
        // deeper cut owns the face and always draws it; the heights are left as they are.
        val a = cut("a", 100f, 50f, diaMm = 190f)
        val b = cut("b", 150f, 50f, diaMm = 189.5f)
        val c = cut("c", 200f, 50f, diaMm = 190f)
        val built = buildUndercutNotches(listOf(a, b, c), segs, oalMm, 0.25f).associateBy { it.id }
        val deeper = built.getValue("b").profiles.single()
        assertTrue("the deeper cut's AFT face always draws", deeper.startFaceAlways)
        assertTrue("the deeper cut's FWD face always draws", deeper.endFaceAlways)
        assertFalse(built.getValue("a").profiles.single().endFaceAlways)
        assertFalse(built.getValue("c").profiles.single().startFaceAlways)
    }

    @Test
    fun `butted cuts of one dia keep one continuous floor`() {
        val a = cut("a", 100f, 50f, diaMm = 190f)
        val b = cut("b", 150f, 50f, diaMm = 190f)
        val built = buildUndercutNotches(listOf(a, b), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(built.getValue("a").floorDiaMm, built.getValue("b").floorDiaMm, eps)
        assertFalse(built.getValue("a").profiles.single().endFaceAlways)
        assertFalse(built.getValue("b").profiles.single().startFaceAlways)
    }

    @Test
    fun `each strip stretches its own cuts, so a shallow liner does not flatten a deep one`() {
        // On-device report: a second liner of shallow cuts squeezed the first liner's cuts into one
        // grey when the stretch spanned the whole sheet.
        val segs = listOf(
            SurfaceSeg(0f, oalMm, 6f * inch, 6f * inch),
            SurfaceSeg(100f, 900f, 6.75f * inch, 6.75f * inch),
            SurfaceSeg(1200f, 1900f, 6.8f * inch, 6.8f * inch),
        )
        val deepRun = listOf(cut("a1", 200f, 100f, 6.26f * inch), cut("a2", 300f, 20f, 5.996f * inch))
        val shallowRun = listOf(cut("b1", 1300f, 100f, 6.76f * inch), cut("b2", 1400f, 20f, 6.535f * inch))
        val sheet = buildUndercutNotches(deepRun + shallowRun, segs, oalMm, 0.25f)
        val strip = sheet.filter { it.id.startsWith("a") }.withDepthShadeStretched().associateBy { it.id }
        assertEquals(0f, strip.getValue("a1").depthFrac, eps)
        assertEquals(1f, strip.getValue("a2").depthFrac, eps)
    }

    @Test
    fun `cuts of one depth sit mid-ramp`() {
        val one = buildUndercutNotches(listOf(cut("x", 100f, 50f, 190f)), segs, oalMm, 0.25f).single()
        assertEquals(UNDERCUT_DEPTH_SHADE_SINGLE_FRAC, one.depthFrac, eps)
    }

    @Test
    fun `an unmeasured cut is the lightest`() {
        val blank = cut("b", 100f, 50f, diaMm = 0f)
        val deep = cut("d", 300f, 50f, diaMm = 190f)
        val built = buildUndercutNotches(listOf(blank, deep), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0f, built.getValue("b").depthFrac, eps)
        assertEquals(0f, built.getValue("b").depthMm, eps)
    }

    @Test
    fun `the ramp runs from the light end to the dark end`() {
        assertEquals(UNDERCUT_DEPTH_SHADE_MIN_ALPHA, undercutDepthShadeAlpha(0f), eps)
        assertEquals(UNDERCUT_DEPTH_SHADE_MAX_ALPHA, undercutDepthShadeAlpha(1f), eps)
        assertEquals(UNDERCUT_DEPTH_SHADE_MAX_ALPHA, undercutDepthShadeAlpha(3f), eps)
    }

    @Test
    fun `the printed sheet shades by depth unless the pref or line art turns it off`() {
        assertTrue(undercutPdfFillPlan(PdfPrefs()).sectionCoreByDepth)
        assertFalse(undercutPdfFillPlan(PdfPrefs(undercutDepthShade = false)).sectionCoreByDepth)
        assertFalse(undercutPdfFillPlan(PdfPrefs(undercutLineArt = true)).sectionCoreByDepth)
    }

    @Test
    fun `the undercut liner switch is its own, never the schematic's`() {
        assertFalse(undercutPdfFillPlan(PdfPrefs(shadedLiners = true)).stripLiner)
        assertTrue(undercutPdfFillPlan(PdfPrefs(shadedLiners = false, undercutShadeLiners = true)).stripLiner)
        assertTrue(undercutPdfFillPlan(PdfPrefs(shadedLiners = false, undercutShadeLiners = true)).liner)
    }
}
