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
    fun `depth fraction is true depth over the sheet's deepest cut`() {
        val shallow = cut("s", 100f, 50f, diaMm = 196f) // 4 mm off Ø
        val deep = cut("d", 300f, 50f, diaMm = 190f)    // 10 mm off Ø
        val built = buildUndercutNotches(listOf(shallow, deep), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0.4f, built.getValue("s").depthFrac, eps)
        assertEquals(1f, built.getValue("d").depthFrac, eps)
    }

    @Test
    fun `a nested cut counts its depth from the outer surface and shades darker than its parent`() {
        val parent = cut("p", 200f, 200f, diaMm = 194f)
        val child = cut("c", 250f, 50f, diaMm = 188f)
        val built = buildUndercutNotches(listOf(parent, child), segs, oalMm, 0.25f).associateBy { it.id }
        assertEquals(0.5f, built.getValue("p").depthFrac, eps)
        assertEquals(1f, built.getValue("c").depthFrac, eps)
        assertTrue(
            undercutDepthShadeAlpha(built.getValue("c").depthFrac) >
                undercutDepthShadeAlpha(built.getValue("p").depthFrac),
        )
    }

    @Test
    fun `a strip's slice normalizes against the whole sheet`() {
        val shallow = cut("s", 100f, 50f, diaMm = 196f)
        val deep = cut("d", 300f, 50f, diaMm = 190f)
        val stripOnly = buildUndercutNotches(
            listOf(shallow), segs, oalMm, 0.25f, sheetUndercuts = listOf(shallow, deep),
        ).single()
        assertEquals(0.4f, stripOnly.depthFrac, eps)
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
