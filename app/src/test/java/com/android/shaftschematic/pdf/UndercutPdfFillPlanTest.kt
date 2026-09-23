package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which fills the printed undercut drawing draws.
 *
 * Two rules worth pinning: the detail strips' liner and the section core are shaded whatever the
 * "Shade in Components" preferences say (the notch voids are white, and the grey around them is
 * what makes the cut sections read), and print line art overrides ALL of it — including those
 * two — so the sheet comes out as outlines and the notch construction alone.
 *
 * Pure: the decision carries no Android dependency, which is what lets the composer hold nothing
 * but paints and Canvas work.
 */
class UndercutPdfFillPlanTest {

    @Test
    fun `shipped prefs shade the strip liner and the section core, kinds follow their prefs`() {
        val plan = undercutPdfFillPlan(PdfPrefs())

        assertFalse("bodies are unshaded by default", plan.body)
        assertFalse("tapers are unshaded by default", plan.taper)
        assertFalse("liners are unshaded by default", plan.liner)
        assertTrue("a detail strip always shades its liner", plan.stripLiner)
        assertTrue("the section core always takes its light fill", plan.sectionCore)
    }

    @Test
    fun `each component kind follows its own shade preference`() {
        assertEquals(
            UndercutPdfFillPlan(
                body = true, taper = false, liner = false, stripLiner = true, sectionCore = true,
            ),
            undercutPdfFillPlan(PdfPrefs(shadedBodies = true)),
        )
        assertEquals(
            UndercutPdfFillPlan(
                body = false, taper = true, liner = false, stripLiner = true, sectionCore = true,
            ),
            undercutPdfFillPlan(PdfPrefs(shadedTapers = true)),
        )
        assertEquals(
            UndercutPdfFillPlan(
                body = false, taper = false, liner = true, stripLiner = true, sectionCore = true,
            ),
            undercutPdfFillPlan(PdfPrefs(shadedLiners = true)),
        )
    }

    @Test
    fun `the strip liner shades even with the liner preference off`() {
        assertTrue(undercutPdfFillPlan(PdfPrefs(shadedLiners = false)).stripLiner)
    }

    @Test
    fun `line art drops every fill regardless of the shade preferences`() {
        val plan = undercutPdfFillPlan(
            PdfPrefs(
                shadedBodies = true,
                shadedTapers = true,
                shadedLiners = true,
                undercutLineArt = true,
            ),
        )

        assertFalse(plan.body)
        assertFalse(plan.taper)
        assertFalse(plan.liner)
        assertFalse("line art stands above the always-shaded strip liner", plan.stripLiner)
        assertFalse("line art stands above the section core fill", plan.sectionCore)
        assertFalse(plan.anyFill)
    }

    @Test
    fun `any shipped sheet draws at least one fill`() {
        assertTrue(undercutPdfFillPlan(PdfPrefs()).anyFill)
    }
}
