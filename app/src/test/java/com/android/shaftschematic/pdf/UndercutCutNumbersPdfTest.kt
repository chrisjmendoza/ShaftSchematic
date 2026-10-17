package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The printed undercut sheet's cut numbers (`PdfPrefs.undercutCutNumbers`) on the on-device job
 * (fixture `undercut_polar_ranger_stbd.shaft`, five cuts on one liner strip): with the pref on
 * the composer prints "1".."5" — "2" on both pieces of the relief its nested cut splits — on top
 * of everything else the sheet draws, with it off it does not; and the liner strip's title carries the liner's OD after its name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutCutNumbersPdfTest {

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
    fun `the job's five cuts are numbered with the pref on and not at all with it off`() {
        val on = drawnTexts(PdfPrefs(undercutCutNumbers = true))
        val off = drawnTexts(PdfPrefs(undercutCutNumbers = false))

        // Exactly the numbers separate the two pages: everything else draws identically. The
        // relief (#2) is split in two by the cut machined inside it (#3), so it prints on both pieces.
        val extra = on.toMutableList()
        off.forEach { extra.remove(it) }
        assertEquals(listOf("1", "2", "2", "3", "4", "5"), extra.sorted())
        assertEquals(on.size - 6, off.size)
    }

    @Test
    fun `the liner strip title carries the liner OD after its name`() {
        val texts = drawnTexts(PdfPrefs())
        // The title is rich text: its plain runs reach drawText as pieces, the name and OD first.
        assertTrue("no title run carries the OD: $texts", texts.any { "Liner Ø10\"" in it })
    }
}
