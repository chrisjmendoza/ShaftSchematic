package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.DrawingProfile
import com.android.shaftschematic.settings.PDF_TEXT_SCALES
import com.android.shaftschematic.settings.PDF_TEXT_SCALE_DEFAULT
import com.android.shaftschematic.settings.PDF_TEXT_SCALE_MAX
import com.android.shaftschematic.settings.PDF_TEXT_SCALE_MIN
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.screen.textScaleLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Footer text size" / "Dimension text size": the two multipliers the schematic and consolidated
 * sheets apply to their footer block and to their dimension values.
 *
 * The defaults must change NOTHING — an untouched install prints the shipped sizes — and the
 * values must survive the clamp and a drawing-profile round trip, the two paths every other
 * look pref goes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetTextScalePrefsTest {

    @Test
    fun `defaults are neutral multipliers and the first step`() {
        val p = PdfPrefs()
        assertEquals(1f, p.footerTextScale, 0f)
        assertEquals(1f, p.dimTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_DEFAULT, PDF_TEXT_SCALES.first(), 0f)
        assertEquals(PDF_TEXT_SCALE_MAX, PDF_TEXT_SCALES.last(), 0f)
    }

    @Test
    fun `out-of-range values are clamped to the steps' bounds`() {
        val low = PdfPrefs(footerTextScale = 0.2f, dimTextScale = 0.2f).clamped()
        assertEquals(PDF_TEXT_SCALE_MIN, low.footerTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_MIN, low.dimTextScale, 0f)
        val high = PdfPrefs(footerTextScale = 9f, dimTextScale = 9f).clamped()
        assertEquals(PDF_TEXT_SCALE_MAX, high.footerTextScale, 0f)
        assertEquals(PDF_TEXT_SCALE_MAX, high.dimTextScale, 0f)
    }

    @Test
    fun `a drawing profile captures and restores both sizes`() {
        val prefs = PdfPrefs(footerTextScale = 1.5f, dimTextScale = 1.25f)
        val back = DrawingProfile.of(prefs, lineThicknessScale = 1f).toPdfPrefs()
        assertEquals(1.5f, back.footerTextScale, 0f)
        assertEquals(1.25f, back.dimTextScale, 0f)
        // A profile saved before the fields existed decodes to the shipped size.
        assertEquals(1f, DrawingProfile().toPdfPrefs().footerTextScale, 0f)
    }

    @Test
    fun `the dye pen row's measured width is the advance it draws`() {
        val c = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(400, 40, android.graphics.Bitmap.Config.ARGB_8888))
        for (s in PDF_TEXT_SCALES) {
            val text = sheetTextPaint(10f * s)
            val end = drawDyePenBoxes(c, text, 5f, 30f, "Dye pen test:", null, scale = s)
            assertEquals(end - 5f, dyePenRowWidth(text, "Dye pen test:", s), 1e-3f)
        }
    }

    @Test
    fun `chips read as percentages`() {
        assertEquals(listOf("100%", "125%", "150%", "175%"), PDF_TEXT_SCALES.map(::textScaleLabel))
    }
}
