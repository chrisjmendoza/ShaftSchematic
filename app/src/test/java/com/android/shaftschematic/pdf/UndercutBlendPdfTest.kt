package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * A blended body face on the printed undercut strips, on the on-device job (fixture
 * `undercut_crowded_royal_atlantic.shaft`, its FWD body's AFT face set to a 45° blend up to the
 * liner): the ramp draws solid, and where a cut over the liner's edge takes part of it away the
 * solid line stops at the cut and the rest is retraced dotted up to the liner's face.
 *
 * With `SCHEMATIC_RENDER_DIR` set the sheets are also saved there as PNGs for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutBlendPdfTest {

    private class Stroke(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val dotted: Boolean) {
        val sloped: Boolean get() = abs(x1 - x0) > 0.05f && abs(y1 - y0) > 0.05f
    }

    private class RecordingCanvas(bmp: Bitmap) : Canvas(bmp) {
        val strokes = mutableListOf<Stroke>()
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            strokes += Stroke(startX, startY, stopX, stopY, paint.pathEffect is DashPathEffect)
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_crowded_royal_atlantic.shaft")).readText(),
    )

    /** The job with its FWD body's AFT face blended at 45° up to the liner's OD. */
    private val spec: ShaftSpec = doc.spec.copy(
        bodies = doc.spec.bodies.map { it.copy(blendAftAngleDeg = 45f, blendAftToLinerOd = true) },
    )
    private val fwdLiner = spec.liners.maxByOrNull { it.startFromAftMm }!!
    private val linerEndMm = fwdLiner.startFromAftMm + fwdLiner.lengthMm

    /** One cut on the FWD liner's FWD end; [pastEdgeMm] is how far it runs on over the body. */
    private fun record(pastEdgeMm: Float) = UndercutRecord(
        undercuts = listOf(
            Undercut(id = "c", startFromAftMm = linerEndMm - 60f, lengthMm = 60f + pastEdgeMm, diaMm = 150f),
        ),
    )

    private fun compose(record: UndercutRecord, name: String): RecordingCanvas {
        val scale = 3f
        val w = 792
        val h = 612
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = RecordingCanvas(bmp)
        canvas.scale(scale, scale)
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java
            .getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        composeUndercutPdf(
            ctor.newInstance(canvas, info), spec, ProjectInfo(), UnitSystem.INCHES,
            pdfPrefs = PdfPrefs(),
            resolvedComponents = resolveComponents(spec),
            undercutRecord = record,
        )
        System.getenv("SCHEMATIC_RENDER_DIR")?.let { dir ->
            File(dir).mkdirs()
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return canvas
    }

    @Test
    fun `a cut that stops at the liner edge leaves the whole ramp solid`() {
        val c = compose(record(pastEdgeMm = 0f), "undercut_blend_cut_to_edge")
        assertTrue("the blend's ramp is not drawn", c.strokes.any { it.sloped && !it.dotted })
        assertTrue("nothing was cut from the ramp, yet it is retraced", c.strokes.none { it.sloped && it.dotted })
    }

    @Test
    fun `a cut over the liner edge stops the ramp at the cut and dots the rest up to the liner`() {
        val c = compose(record(pastEdgeMm = 6f), "undercut_blend_cut_into_blend")
        val dotted = c.strokes.filter { it.sloped && it.dotted }
        // Mirrored about the centreline: one piece above, one below.
        assertEquals(2, dotted.size)
        // The dotted piece starts on the liner's dotted face line.
        val face = c.strokes.filter { it.dotted && abs(it.x1 - it.x0) < 0.01f }
        val xFace = dotted.minOf { minOf(it.x0, it.x1) }
        assertTrue("the dotted ramp does not reach the liner's dotted face", face.any { abs(it.x0 - xFace) < 0.05f })
    }
}
