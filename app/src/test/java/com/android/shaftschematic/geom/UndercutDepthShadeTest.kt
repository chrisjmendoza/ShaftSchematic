package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.pdf.undercutPdfFillPlan
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Depth shading (`PdfPrefs.undercutDepthShade`): each notch's [UndercutNotch.depthFrac] is its
 * TRUE depth below the outer surface over the sheet's deepest measured cut, nested cuts counted
 * from the outer surface, and [undercutDepthShadeAlpha] ramps it light → dark.
 */
class UndercutDepthShadeTest {

    private val oalMm = 2000f
    private val surfaceDiaMm = 200f
    private val segs = listOf(SurfaceSeg(0f, oalMm, surfaceDiaMm, surfaceDiaMm))
    private val eps = 1e-4f

    private fun cut(id: String, startMm: Float, lengthMm: Float, diaMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)

    @Test
    fun `depth fraction runs from the sheet's shallowest cut to its deepest`() {
        val shallow = cut("s", 100f, 50f, diaMm = 196f) // 4 mm off Ø
        val deep = cut("d", 300f, 50f, diaMm = 190f)    // 10 mm off Ø
        val built = buildUndercutNotches(listOf(shallow, deep), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals("the shallowest cut is the light end", 0f, built.getValue("s").depthFrac, eps)
        assertEquals(1f, built.getValue("d").depthFrac, eps)
    }

    @Test
    fun `a nested cut counts its depth from the outer surface and shades darker than its parent`() {
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
        val inch = 25.4f
        val bodyDia = 6.375f * inch
        val linerDia = 6.75f * inch
        val linerEnd = 912f
        val segs = listOf(
            SurfaceSeg(0f, oalMm, bodyDia, bodyDia),
            SurfaceSeg(100f, linerEnd, linerDia, linerDia),
        )
        val cuts = listOf(
            cut("long", 100f, 811f, 6.260f * inch),
            cut("a", 911f, 14f, 6.045f * inch),  // straddles the liner end
            cut("b", 925f, 14f, 5.996f * inch),  // on the body
            cut("c", 939f, 38f, 6.187f * inch),
            cut("d", 977f, 16f, 6.291f * inch),
        )
        val frac = buildUndercutNotches(cuts, segs, oalMm, 0.25f).associate { it.id to it.depthFrac }
        val byFloor = cuts.sortedBy { it.diaMm }.map { it.id }
        val byShade = frac.entries.sortedByDescending { it.value }.map { it.key }
        assertEquals("deeper (smaller floor Ø) shades darker", byFloor, byShade)
        assertEquals(1f, frac.getValue("b"), eps)
    }

    @Test
    fun `cuts that do not touch are separate sections`() {
        val inch = 25.4f
        val segs = listOf(
            SurfaceSeg(0f, oalMm, 6f * inch, 6f * inch),
            SurfaceSeg(100f, 400f, 7f * inch, 7f * inch),
        )
        // One on the liner, one on bare shaft far away: each measured from its own surface.
        val onLiner = cut("l", 200f, 50f, 6.9f * inch)
        val onBody = cut("s", 1000f, 50f, 5.9f * inch)
        val frac = buildUndercutNotches(listOf(onLiner, onBody), segs, oalMm, 0.25f).associate { it.id to it.depthFrac }
        // Equal depths from their own surfaces: one depth on the sheet, the middle of the ramp.
        assertEquals(UNDERCUT_DEPTH_SHADE_SINGLE_FRAC, frac.getValue("l"), eps)
        assertEquals(UNDERCUT_DEPTH_SHADE_SINGLE_FRAC, frac.getValue("s"), eps)
    }

    @Test
    fun `a strip's slice normalizes against the whole sheet`() {
        val shallow = cut("s", 100f, 50f, diaMm = 196f)
        val deep = cut("d", 300f, 50f, diaMm = 190f)
        val stripOnly = buildUndercutNotches(
            listOf(shallow), segs, oalMm, 0.25f, sheetUndercuts = listOf(shallow, deep),
        ).single()
        assertEquals("the sheet's shallowest, not the strip's only cut", 0f, stripOnly.depthFrac, eps)
    }

    @Test
    fun `an unmeasured cut is the lightest`() {
        val blank = cut("b", 100f, 50f, diaMm = 0f)
        val deep = cut("d", 300f, 50f, diaMm = 190f)
        val built = buildUndercutNotches(listOf(blank, deep), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0f, built.getValue("b").depthFrac, eps)
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
}
