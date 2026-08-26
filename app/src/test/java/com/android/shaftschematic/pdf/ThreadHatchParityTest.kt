package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One thread must print IDENTICALLY on every sheet ("no sense in having different forms
 * with different outputs" — on-device direction). All hatch sites share `drawThreadHatch`
 * plus one pitch/paint recipe (pitch = thread's own pitch capped 4–18 pt; 60%-dim-weight
 * alpha-160 paint); the schematic's former private convention (short ±4 pt ticks at
 * max(8, pitch)) is gone.
 *
 * Pinned by pixel equality: the same thread rendered through the schematic's pass, the
 * wear/undercut shared profile, and the runout profile must produce byte-identical
 * bitmaps. A recipe drift on any sheet fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThreadHatchParityTest {

    private val w = 400
    private val h = 160
    private val cy = 80f

    private fun thread() = Threads(
        id = "t1", startFromAftMm = 40f, lengthMm = 120f, majorDiaMm = 60f, pitchMm = 6f,
    )

    private fun bmp(draw: (Canvas, Paint, Paint) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.0f; color = Color.BLACK
        }
        val dim = Paint(outline).apply { strokeWidth = 1.2f }
        draw(c, outline, dim)
        return b
    }

    private fun schematic(): Bitmap = bmp { c, outline, dim ->
        drawThreads(c, listOf(thread()), cy, { it }, { d -> d / 2f }, outline, dim, ptPerMm = 1f)
    }

    private fun simpleProfile(): Bitmap = bmp { c, outline, dim ->
        drawSimpleShaftProfile(
            c, ShaftSpec(overallLengthMm = 400f, threads = listOf(thread())), cy, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()), { it }, { d -> d / 2f },
            bodyFill = null, taperFill = null, linerFill = null,
            ptPerMm = 1f, dimStrokeWidthPt = dim.strokeWidth,
        )
    }

    private fun runoutProfile(): Bitmap = bmp { c, outline, _ ->
        val spec = ShaftSpec(overallLengthMm = 400f, threads = listOf(thread()))
        drawShaftProfile(
            c, spec, spec, cy, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()), { it }, { d -> d / 2f },
            ptPerMm = 1f,
        )
    }

    @Test
    fun `schematic and wear-undercut profile print the same thread pixel-for-pixel`() {
        assertTrue(schematic().sameAs(simpleProfile()))
    }

    @Test
    fun `schematic and runout profile print the same thread pixel-for-pixel`() {
        assertTrue(schematic().sameAs(runoutProfile()))
    }
}
