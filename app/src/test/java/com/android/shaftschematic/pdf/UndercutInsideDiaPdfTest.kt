package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * "Diameters inside the shaft" (`PdfPrefs.undercutDiaInside`) on the printed undercut sheet: a cut
 * wide enough carries its Ø rotated inside the shaft and drops its below-shaft leader, a thin cut
 * keeps the leader — under line art and on a shaded sheet alike — and with the switch off the sheet
 * draws exactly what it drew before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutInsideDiaPdfTest {

    private fun inch(v: Float) = v * 25.4f

    /** Every text run and line, with whether the canvas was rotated when it drew. */
    private class RecordingCanvas(bmp: Bitmap) : Canvas(bmp) {
        val ops = mutableListOf<String>()
        val texts = mutableListOf<Pair<String, Boolean>>()
        private val m = Matrix()
        private val v = FloatArray(9)

        private fun rotated(): Boolean {
            @Suppress("DEPRECATION")
            getMatrix(m)
            m.getValues(v)
            return abs(v[Matrix.MSKEW_X]) > 0.5f
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            val r = rotated()
            texts += text to r
            ops += "T|$text|$x|$y|$r"
            super.drawText(text, x, y, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            ops += "L|$startX|$startY|$stopX|$stopY|${paint.strokeWidth}"
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            ops += "R|$left|$top|$right|$bottom|${paint.color}"
            super.drawRect(left, top, right, bottom, paint)
        }
    }

    // One liner: a wide 10" cut and a thin 3/16" sliver.
    private val spec = ShaftSpec(
        overallLengthMm = inch(100f),
        autoBodyDiaMm = inch(6.375f),
        liners = listOf(Liner(id = "L", startFromAftMm = inch(40f), lengthMm = inch(32f), odMm = inch(6.75f))),
    )
    private val record = UndercutRecord(
        undercuts = listOf(
            Undercut(id = "wide", startFromAftMm = inch(44f), lengthMm = inch(10f), diaMm = inch(6.25f)),
            Undercut(id = "thin", startFromAftMm = inch(62f), lengthMm = inch(0.1875f), diaMm = inch(6.1f)),
        ),
    )
    private val wideValue = "6.250"
    private val thinValue = "6.100"

    private fun compose(prefs: PdfPrefs): RecordingCanvas {
        val w = 792
        val h = 612
        val canvas = RecordingCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java
            .getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        composeUndercutPdf(
            ctor.newInstance(canvas, info), spec, ProjectInfo(), UnitSystem.INCHES,
            pdfPrefs = prefs,
            resolvedComponents = resolveComponents(spec),
            undercutRecord = record,
        )
        return canvas
    }

    private fun RecordingCanvas.drew(value: String, rotated: Boolean): Boolean =
        texts.any { (t, r) -> t.contains(value) && r == rotated }

    @Test
    fun `a wide cut writes its value inside and a thin cut keeps its leader`() {
        val c = compose(PdfPrefs(undercutLineArt = true, undercutDiaInside = true))
        assertTrue("the wide cut's value is not rotated inside the shaft", c.drew(wideValue, rotated = true))
        assertFalse("the wide cut still prints a below-shaft callout", c.drew(wideValue, rotated = false))
        assertTrue("the thin cut lost its below-shaft callout", c.drew(thinValue, rotated = false))
        assertFalse("the thin cut's value was squeezed inside", c.drew(thinValue, rotated = true))
    }

    @Test
    fun `a shaded sheet writes its values inside too`() {
        val c = compose(PdfPrefs(undercutLineArt = false, undercutDiaInside = true))
        assertTrue("the wide cut's value is not rotated inside the shaft", c.drew(wideValue, rotated = true))
        assertFalse("the wide cut still prints a below-shaft callout", c.drew(wideValue, rotated = false))
        assertTrue("the thin cut lost its below-shaft callout", c.drew(thinValue, rotated = false))
        assertFalse("the thin cut's value was squeezed inside", c.drew(thinValue, rotated = true))
    }

    @Test
    fun `with line art on but the switch off every value keeps its leader`() {
        val c = compose(PdfPrefs(undercutLineArt = true, undercutDiaInside = false))
        assertFalse("a value drew rotated with the switch off", c.texts.any { it.second })
        assertTrue(c.drew(wideValue, rotated = false))
        assertTrue(c.drew(thinValue, rotated = false))
    }
}
