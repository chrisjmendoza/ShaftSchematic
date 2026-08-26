package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The runout/consolidated sheet's profile must print a body's keyway.
 *
 * It did not: the sheet drew taper keyways and no body keyways at all, so a keyway authored on
 * a body showed on the schematic, showed on the tab's canvas (which renders through
 * `ShaftRenderer`), and was missing from the printed sheet (on-device report) — while the sheet
 * was already paying for it by pinning that body at true width.
 *
 * Renders the profile the way the composer does and asserts the slot lands INSIDE the body
 * silhouette, differentially against the same body with no keyway, so the pass cannot go
 * missing again without failing here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunoutProfileKeywayTest {

    private val w = 400
    private val h = 200
    private val cy = 100f

    private fun body(keyed: Boolean) = Body(
        id = "b1",
        startFromAftMm = 0f,
        lengthMm = 300f,
        diaMm = 100f,
        keywayWidthMm = if (keyed) 20f else 0f,
        keywayDepthMm = if (keyed) 8f else 0f,
        keywayLengthMm = if (keyed) 100f else 0f,
    )

    /**
     * The sheet draws resolved runs but must read keyways off the stored spec — a resolved
     * body carries none. Both specs are handed over exactly as the composer hands them over.
     */
    private fun render(keyed: Boolean): Bitmap {
        val stored = ShaftSpec(overallLengthMm = 300f, bodies = listOf(body(keyed)))
        val resolved = stored.withResolvedBodies(
            listOf(
                ResolvedBody(
                    id = "b1",
                    type = ResolvedComponentType.BODY,
                    source = ResolvedComponentSource.EXPLICIT,
                    startMmPhysical = 0f,
                    endMmPhysical = 300f,
                    diaMm = 100f,
                )
            )
        )
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.BLACK
        }
        drawShaftProfile(
            c = c,
            spec = resolved,
            authoredSpec = stored,
            cy = cy,
            outline = outline,
            geomRect = RectF(0f, 0f, w.toFloat(), h.toFloat()),
            xAt = { mm -> mm },
            rPx = { dia -> dia / 2f },
            ptPerMm = 1f,
        )
        return bmp
    }

    private fun ink(bmp: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        for (x in x0 until x1) for (y in y0 until y1) {
            if (bmp.getPixel(x, y) != Color.WHITE) n++
        }
        return n
    }

    /**
     * The band well inside the silhouette (Ø 100 mm → edges at y 50/150) carries nothing but
     * the keyway: its walls sit at cy ± 10 pt over the keyway's 100 mm run.
     */
    @Test
    fun `a body keyway prints inside the runout profile`() {
        assertTrue("the keyway slot must print on the sheet", ink(render(keyed = true), 5, 80, 100, 120) > 0)
    }

    @Test
    fun `a body without a keyway leaves the profile interior clear`() {
        assertEquals(0, ink(render(keyed = false), 5, 80, 100, 120))
    }

    /** The body itself draws either way — the differential above is the keyway, not the body. */
    @Test
    fun `the body silhouette draws with or without a keyway`() {
        assertTrue(ink(render(keyed = true), 0, 45, w, 55) > 0)
        assertTrue(ink(render(keyed = false), 0, 45, w, 55) > 0)
    }
}
