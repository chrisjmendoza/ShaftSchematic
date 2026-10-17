package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The printed undercut sheet carries no cut numbers — they are an input aid on the Undercut tab's
 * list and detail canvas only (on-device ruling). Pinned on the on-device job (fixture
 * `undercut_polar_ranger_stbd.shaft`, five cuts on one liner strip, a relief split by the cut
 * nested inside it): no standalone "1".."5" reaches the page. Fractions are set inline for the
 * run, so every dimension reaches `drawText` as ONE string — a built-up fraction would hand its
 * numerator and denominator over as separate digit pieces. The liner strip's title still carries
 * the liner's OD after its name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutSheetNoCutNumbersTest {

    private var styleBefore = FractionTypography.active.style

    @Before
    fun setUp() {
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.INLINE)
    }

    @After
    fun tearDown() {
        FractionTypography.setStyle(styleBefore)
    }

    /** Records every string drawn through `drawText(String, …)`. */
    private class TextRecordingCanvas(bmp: Bitmap) : Canvas(bmp) {
        val texts = mutableListOf<String>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            texts += text
            super.drawText(text, x, y, paint)
        }
    }

    private fun drawnTexts(prefs: PdfPrefs): List<String> {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        val doc = ShaftDocCodec.decode(raw)
        val w = 792
        val h = 612
        val canvas = TextRecordingCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java
            .getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        val page = ctor.newInstance(canvas, info)
        composeUndercutPdf(
            page, doc.spec, ProjectInfo(), UnitSystem.INCHES,
            pdfPrefs = prefs,
            resolvedComponents = resolveComponents(doc.spec),
            undercutRecord = doc.undercutRecord,
        )
        return canvas.texts
    }

    @Test
    fun `the printed sheet draws no cut numbers`() {
        val texts = drawnTexts(PdfPrefs())
        val cutLabels = setOf("1", "2", "3", "4", "5")
        val numbers = texts.filter { it in cutLabels }
        assertTrue("cut numbers reached the sheet: $numbers (all: $texts)", numbers.isEmpty())
    }

    @Test
    fun `the liner strip title carries the liner OD after its name`() {
        val texts = drawnTexts(PdfPrefs())
        // The title is rich text; with inline fractions it reaches drawText whole, name and OD first.
        assertTrue("no title run carries the OD: $texts", texts.any { "Liner Ø10\"" in it })
    }
}
