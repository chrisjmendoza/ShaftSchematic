package com.android.shaftschematic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-group text sizes in a drawing profile: captured and applied with the rest of the look,
 * reset by "Restore Drawing defaults", and — for a profile saved before the groups existed —
 * loaded from that profile's OWN shared sizes, since every sheet printed at them when it was saved.
 */
class SheetTextGroupProfileTest {

    @Test
    fun `a profile captures and restores every group's sizes`() {
        val prefs = PdfPrefs(
            footerTextScale = 1.25f, dimTextScale = 1.5f,
            runoutFooterTextScale = 1.75f, runoutDimTextScale = 1f, undercutDimTextScale = 1.75f,
        )
        val back = decodeDrawingProfiles(
            encodeDrawingProfiles(mapOf("Groups" to DrawingProfile.of(prefs, lineThicknessScale = 1f))),
        ).getValue("Groups").toPdfPrefs()
        assertEquals(1.25f, back.footerTextScale, 0f)
        assertEquals(1.5f, back.dimTextScale, 0f)
        assertEquals(1.75f, back.runoutFooterTextScale, 0f)
        assertEquals(1f, back.runoutDimTextScale, 0f)
        assertEquals(1.75f, back.undercutDimTextScale, 0f)
    }

    @Test
    fun `a profile saved before the groups existed takes its own shared sizes`() {
        val raw = """{"Old":{"footerTextScale":1.5,"dimTextScale":1.25,"lineThicknessScale":1.0}}"""
        val prefs = decodeDrawingProfiles(raw).getValue("Old").toPdfPrefs()
        assertEquals(1.5f, prefs.footerTextScale, 0f)
        assertEquals(1.25f, prefs.dimTextScale, 0f)
        assertEquals(1.5f, prefs.runoutFooterTextScale, 0f)
        assertEquals(1.25f, prefs.runoutDimTextScale, 0f)
        assertEquals(1.25f, prefs.undercutDimTextScale, 0f)
    }

    @Test
    fun `restoring the drawing defaults resets every group to 100 percent`() {
        val prefs = DrawingProfile().toPdfPrefs()
        assertEquals(PDF_TEXT_SCALE_DEFAULT, prefs.runoutFooterTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_DEFAULT, prefs.runoutDimTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_DEFAULT, prefs.undercutDimTextScale, 0f)
    }

    @Test
    fun `out-of-range group sizes are clamped`() {
        val p = PdfPrefs(runoutFooterTextScale = 9f, runoutDimTextScale = 0.1f, undercutDimTextScale = 9f).clamped()
        assertEquals(PDF_TEXT_SCALE_MAX, p.runoutFooterTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_MIN, p.runoutDimTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_MAX, p.undercutDimTextScale, 0f)
    }
}
