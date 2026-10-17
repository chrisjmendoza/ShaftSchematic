package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
 * The printed undercut sheet's liner outline (`PdfPrefs.undercutLinerOutline`) on the on-device
 * job (fixture `undercut_polar_ranger_stbd.shaft`): with the pref on, the composer strokes the
 * dotted retrace of the liner's erased edges; with it off, not one dotted outline stroke.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutLinerOutlinePdfTest {

    /** Counts the strokes drawn with the liner outline's dotted paint. */
    private class OutlineCountingCanvas(bmp: Bitmap) : Canvas(bmp) {
        var outlineStrokes = 0
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (paint.pathEffect is DashPathEffect && paint.color == android.graphics.Color.argb(150, 0, 0, 0)) {
                outlineStrokes++
            }
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private fun outlineStrokes(prefs: PdfPrefs): Int {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft"))
            .readText()
        val doc = ShaftDocCodec.decode(raw)
        val w = 792
        val h = 612
        val canvas = OutlineCountingCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
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
        return canvas.outlineStrokes
    }

    @Test
    fun `the job's liner is retraced over its cuts with the outline on and not at all with it off`() {
        assertTrue("the outline drew nothing", outlineStrokes(PdfPrefs(undercutLinerOutline = true)) > 0)
        assertEquals(0, outlineStrokes(PdfPrefs(undercutLinerOutline = false)))
    }
}
