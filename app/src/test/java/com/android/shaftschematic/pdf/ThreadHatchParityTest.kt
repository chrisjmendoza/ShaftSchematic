package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_DENSITY_MIN
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_MAX
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.ThreadHatchStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One thread must print IDENTICALLY on every sheet ("no sense in having different forms
 * with different outputs" — on-device direction). All hatch sites share `drawThreadHatch`
 * plus one pitch/lean/paint recipe (spacing = the thread's own pitch at the drawn height's
 * diametral scale over the app-wide density, `ThreadHatchStyle.density`, capped 3–18 pt; lean =
 * that pitch over its own diameter at the app-wide slant, `ThreadHatchStyle.slant`;
 * 60%-dim-weight alpha-160 paint); the schematic's former private convention (short ±4 pt ticks
 * at max(8, pitch)) is gone.
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

    private fun schematic(xAt: (Float) -> Float = { it }): Bitmap = bmp { c, outline, dim ->
        drawThreads(c, listOf(thread()), cy, xAt, { d -> d / 2f }, outline, dim)
    }

    private fun simpleProfile(): Bitmap = bmp { c, outline, dim ->
        drawSimpleShaftProfile(
            c, ShaftSpec(overallLengthMm = 400f, threads = listOf(thread())), cy, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()), { it }, { d -> d / 2f },
            bodyFill = null, taperFill = null, linerFill = null,
            dimStrokeWidthPt = dim.strokeWidth,
        )
    }

    private fun runoutProfile(ptPerMm: Float = 1f): Bitmap = bmp { c, outline, _ ->
        val spec = ShaftSpec(overallLengthMm = 400f, threads = listOf(thread()))
        drawShaftProfile(
            c, spec, spec, cy, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()), { it }, { d -> d / 2f },
            ptPerMm = ptPerMm,
        )
    }

    /** True when [a] and [b] agree on every pixel left of column [xEnd]. */
    private fun sameLeftOf(a: Bitmap, b: Bitmap, xEnd: Int): Boolean {
        for (x in 0 until xEnd) for (y in 0 until h) {
            if (a.getPixel(x, y) != b.getPixel(x, y)) return false
        }
        return true
    }

    /** The hatch mirrors are process-wide, so a test that moves them must put them back. */
    @After
    fun restoreShippedHatch() {
        SettingsStore.updatePdfPrefs {
            it.copy(threadSlant = THREAD_SLANT_DEFAULT, threadDensity = THREAD_DENSITY_DEFAULT)
        }
    }

    @Test
    fun `schematic and wear-undercut profile print the same thread pixel-for-pixel`() {
        assertTrue(schematic().sameAs(simpleProfile()))
    }

    @Test
    fun `schematic and runout profile print the same thread pixel-for-pixel`() {
        assertTrue(schematic().sameAs(runoutProfile()))
    }

    @Test
    fun `every sheet prints the same thread pixel-for-pixel at a non-default slant`() {
        SettingsStore.updatePdfPrefs { it.copy(threadSlant = 8f) }
        val schematic = schematic()
        assertTrue(schematic.sameAs(simpleProfile()))
        assertTrue(schematic.sameAs(runoutProfile()))
    }

    @Test
    fun `every sheet prints the same thread pixel-for-pixel at a non-default density`() {
        SettingsStore.updatePdfPrefs { it.copy(threadDensity = 0.8f) }
        val schematic = schematic()
        assertTrue(schematic.sameAs(simpleProfile()))
        assertTrue(schematic.sameAs(runoutProfile()))
    }

    @Test
    fun `a slant change reaches the printed hatch`() {
        val shipped = schematic()
        SettingsStore.updatePdfPrefs { it.copy(threadSlant = 8f) }
        assertFalse(shipped.sameAs(schematic()))
    }

    @Test
    fun `a density change reaches the printed hatch`() {
        val shipped = schematic()
        SettingsStore.updatePdfPrefs { it.copy(threadDensity = 1f) }
        assertFalse(shipped.sameAs(schematic()))
    }

    @Test
    fun `a sheet's own axial scale never changes the hatch`() {
        // The runout pass is handed a pt/mm that once spaced its hatch; it no longer can.
        assertTrue(runoutProfile(ptPerMm = 1f).sameAs(runoutProfile(ptPerMm = 0.3f)))
        // An axially stretched thread (same drawn height) strokes the same over the span both
        // drawings share — left of the unstretched thread's FWD end face at x = 160.
        assertTrue(sameLeftOf(schematic(), schematic(xAt = { 40f + (it - 40f) * 2f }), xEnd = 155))
    }

    @Test
    fun `the pref write is what moves the mirrors, clamped`() {
        SettingsStore.updatePdfPrefs { it.copy(threadSlant = 6.5f, threadDensity = 0.35f) }
        assertEquals(6.5f, ThreadHatchStyle.slant, 0f)
        assertEquals(0.35f, ThreadHatchStyle.density, 0f)
        SettingsStore.updatePdfPrefs { it.copy(threadSlant = 40f, threadDensity = 0.01f) }
        assertEquals(THREAD_SLANT_MAX, ThreadHatchStyle.slant, 0f)
        assertEquals(THREAD_DENSITY_MIN, ThreadHatchStyle.density, 0f)
    }
}
