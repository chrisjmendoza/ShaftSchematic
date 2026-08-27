package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The seam that makes a shaded broken body run fill cleanly: the stub's fill boundary and the
 * stroked S-break glyph are ONE curve, built by [appendBreakEdgeS].
 *
 * The two used to be independent — [breakStubFillPath]'s ancestor was an axis-aligned rectangle
 * ending on a straight vertical at the break x, while the glyph strokes a cubic that leaves that
 * vertical by √3/6·amplitude with opposite sign in its two halves. Anything that lets them drift
 * apart again reproduces the on-device report (body shading does not fill cleanly at the S
 * breaks), so this pins them point-for-point rather than by eye.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreakEdgeFillSeamTest {

    /**
     * Robolectric's native graphics stack has to be brought up by a Bitmap/Canvas before a bare
     * [Path] is constructed — a test that reaches for a Path first takes the whole test JVM down
     * at teardown (no exception, no report, just an EOF from the worker).
     */
    @Before
    fun warmNativeGraphics() {
        Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
    }

    private val x = 100f
    private val yTop = 20f
    private val yBot = 180f
    private val amp = 30f

    /** Points along one contour of [p], sampled by arc length from [from] to [to]. */
    private fun walk(p: Path, from: Float, to: Float, steps: Int = 64): List<FloatArray> {
        val pm = PathMeasure(p, false)
        val pos = FloatArray(2)
        return (0..steps).map { i ->
            pm.getPosTan(from + (to - from) * i / steps, pos, null)
            floatArrayOf(pos[0], pos[1])
        }
    }

    private fun length(p: Path) = PathMeasure(p, false).length

    @Test
    fun `the stub fill's break end IS the stroked S, point for point`() {
        val s = breakEdgeSPath(x, yTop, yBot, amp)
        val sLen = length(s)
        // The stub contour runs outer edge → break line → the S → back → close, so the curve
        // starts one straight edge in. Both stubs are built from the same call, so checking the
        // left one (outer < break) also covers the right one (outer > break).
        val outerX = 20f
        val stub = breakStubFillPath(outerX, x, yTop, yBot, amp)
        val lead = x - outerX

        val onS = walk(s, 0f, sLen)
        val onStub = walk(stub, lead, lead + sLen)
        assertEquals(onS.size, onStub.size)
        onS.forEachIndexed { i, a ->
            val b = onStub[i]
            assertEquals("x drifted at sample $i", a[0], b[0], 1e-2f)
            assertEquals("y drifted at sample $i", a[1], b[1], 1e-2f)
        }
    }

    /**
     * x of the S at height fraction [t]. The cubic's y term collapses to `yTop + t·h` exactly
     * (the control ordinates are h/3 and 2h/3), so the Bézier parameter IS the height fraction
     * and x reduces to `x + 3·amp·t(1−t)(1−2t)`. The composers' tests sample against this closed
     * form, so pinning it here keeps them honest about the real curve.
     */
    private fun sX(t: Float) = x + 3f * amp * t * (1f - t) * (1f - 2f * t)

    @Test
    fun `the closed form matches the drawn curve`() {
        val s = breakEdgeSPath(x, yTop, yBot, amp)
        val sLen = length(s)
        walk(s, 0f, sLen, steps = 200).forEach { p ->
            val t = (p[1] - yTop) / (yBot - yTop)
            // Tolerance covers PathMeasure's own flattening, nothing more.
            assertEquals("the S left its closed form at t=$t", sX(t), p[0], 0.3f)
        }
        // …including the ±√3/6·amp extremes the pair layout budgets its gap from.
        val peak = amp * kotlin.math.sqrt(3f) / 6f
        assertEquals(x + peak, (0..1000).maxOf { sX(it / 1000f) }, 1e-2f)
        assertEquals(x - peak, (0..1000).minOf { sX(it / 1000f) }, 1e-2f)
    }

    @Test
    fun `drawBreakEdge strokes that same curve`() {
        // The upper half of an eyeAtTop=false edge carries the main S and nothing else — no eye
        // wash, no return sweep — so ink there localizes the stroked curve exactly.
        val bmp = Bitmap.createBitmap(220, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        }
        drawBreakEdge(c, x, yTop, yBot, amp, p, eyeAtTop = false)

        listOf(0.1f, 0.2f, 0.3f, 0.4f).forEach { t ->
            val y = Math.round(yTop + t * (yBot - yTop))
            val cx = Math.round(sX(t))
            assertTrue("no ink on the S at t=$t", bmp.getPixel(cx, y) != Color.WHITE)
            assertTrue("ink 4 px left of the S at t=$t", bmp.getPixel(cx - 4, y) == Color.WHITE)
            assertTrue("ink 4 px right of the S at t=$t", bmp.getPixel(cx + 4, y) == Color.WHITE)
        }
    }

    @Test
    fun `a filled stub's boundary tracks the S at every height`() {
        val bmp = Bitmap.createBitmap(220, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
        c.drawPath(breakStubFillPath(20f, x, yTop, yBot, amp), fill)

        (1..9).map { it / 10f }.forEach { t ->
            val y = Math.round(yTop + t * (yBot - yTop))
            var edge = -1
            for (px in 219 downTo 0) if (bmp.getPixel(px, y) != Color.WHITE) { edge = px; break }
            assertEquals("the fill edge left the S at t=$t", sX(t), edge.toFloat(), 1.5f)
        }
    }
}
