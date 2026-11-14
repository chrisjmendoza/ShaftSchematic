package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which fills the printed undercut drawing draws.
 *
 * Rules worth pinning: the undercut sheet's liners follow ITS OWN switch
 * (`PdfPrefs.undercutShadeLiners`), never the schematic's `shadedLiners`; bodies and tapers follow
 * their "Shade in Components" prefs; the section core always fills; and print line art overrides
 * ALL of it, so the sheet comes out as outlines and the notch construction alone.
 *
 * Pure: the decision carries no Android dependency, which is what lets the composer hold nothing
 * but paints and Canvas work.
 */
class UndercutPdfFillPlanTest {

    @Test
    fun `shipped prefs leave the liners white and shade the cut sections by depth`() {
        val shipped = PdfPrefs()
        val plan = undercutPdfFillPlan(shipped)

        assertEquals("bodies follow the shipped body default", shipped.shadedBodies, plan.body)
        assertEquals("tapers follow the shipped taper default", shipped.shadedTapers, plan.taper)
        assertFalse("the undercut liner switch ships off", plan.liner)
        assertFalse("the undercut liner switch ships off", plan.stripLiner)
        assertTrue("the cut sections shade by depth", plan.sectionCoreByDepth)
        assertTrue("the section core always fills", plan.sectionCore)
    }

    /** Every kind's shade switched off, so one kind at a time can be turned on below. */
    private val noKindsShaded = PdfPrefs(
        shadedBodies = false, shadedTapers = false, shadedLiners = false,
        undercutDepthShade = false, undercutShadeLiners = false,
    )

    @Test
    fun `each component kind follows its own shade preference`() {
        assertEquals(
            UndercutPdfFillPlan(
                body = true, taper = false, liner = false, stripLiner = false, sectionCore = true,
            ),
            undercutPdfFillPlan(noKindsShaded.copy(shadedBodies = true)),
        )
        assertEquals(
            UndercutPdfFillPlan(
                body = false, taper = true, liner = false, stripLiner = false, sectionCore = true,
            ),
            undercutPdfFillPlan(noKindsShaded.copy(shadedTapers = true)),
        )
        assertEquals(
            UndercutPdfFillPlan(
                body = false, taper = false, liner = true, stripLiner = true, sectionCore = true,
            ),
            undercutPdfFillPlan(noKindsShaded.copy(undercutShadeLiners = true)),
        )
    }

    @Test
    fun `the schematic's liner shading never reaches the undercut sheet`() {
        val plan = undercutPdfFillPlan(noKindsShaded.copy(shadedLiners = true))
        assertFalse(plan.liner)
        assertFalse(plan.stripLiner)
    }

    @Test
    fun `line art drops every fill regardless of the shade preferences`() {
        val plan = undercutPdfFillPlan(
            PdfPrefs(
                shadedBodies = true,
                shadedTapers = true,
                shadedLiners = true,
                undercutShadeLiners = true,
                undercutLineArt = true,
            ),
        )

        assertFalse(plan.body)
        assertFalse(plan.taper)
        assertFalse(plan.liner)
        assertFalse("line art stands above the undercut liner switch", plan.stripLiner)
        assertFalse("line art stands above the section core fill", plan.sectionCore)
        assertFalse(plan.anyFill)
    }

    @Test
    fun `any shipped sheet draws at least one fill`() {
        assertTrue(undercutPdfFillPlan(PdfPrefs()).anyFill)
    }
}
