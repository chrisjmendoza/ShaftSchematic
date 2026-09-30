package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.ui.resolved.BodyBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A sealed body still BREAKS — its seal areas are avoid ranges for the break gap, the same
 * mechanism as a keyway window, never a reason to lose the break (on-device report: a run with
 * two seal areas and a ramp at each end printed plain and read as a short body).
 *
 * `breakGapCenter` takes the seal areas' drawn x-ranges (`BodyDrawEdges.sealSpansX`) beside the
 * keyway windows: span midpoint by convention, shifted the minimal distance that clears every
 * range, plain-rect fallback only when nothing clears. A gap cut through a seal area would leave
 * a dashed seal line floating in the paper gap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreakGapSealAvoidanceTest {

    // ── The pure placement ────────────────────────────────────────────────────

    @Test
    fun `seal areas at both ends leave the centered gap alone`() {
        assertEquals(50f, breakGapCenter(0f, 100f, 20f, listOf(0f..20f, 80f..100f))!!, 1e-3f)
    }

    @Test
    fun `seal areas leaving no clear lane yield no placement`() {
        assertNull(breakGapCenter(0f, 100f, 20f, listOf(0f..45f, 55f..100f)))
    }

    @Test
    fun `an off-centre seal area shifts the gap minimally off it`() {
        val c = breakGapCenter(0f, 400f, 20f, listOf(180f..260f))!!
        // Gap [c−10, c+10] must clear [180,260]; the nearest legal center is 170.
        assertTrue("gap must clear the seal area", c + 10f <= 180f + 1e-2f || c - 10f >= 260f - 1e-2f)
        assertEquals(170f, c, 2e-2f)
    }

    // ── The draw pass feeds the seal areas in ─────────────────────────────────

    private val w = 500
    private val h = 200
    private val cy = 100f
    private val strokePt = 1.5f

    /** 400 mm at 1 pt/mm = 400 pt of run — far past the 220 pt long-span trigger. */
    private val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 80f)

    /** A ramp up to a Ø100 neighbour plus a 150 mm seal area, at [end] of the run. */
    private fun sealedFace(end: LinerAuthoredReference) = BodyBlend(
        bodyId = "b1", end = end,
        faceMm = if (end == LinerAuthoredReference.AFT) 0f else 400f,
        lengthMm = 40f, bodyDiaMm = 80f, neighbourDiaMm = 100f, profile = BlendProfile.OGEE,
        seal = true, sealLenMm = 150f,
    )

    private fun render(blends: List<BodyBlend>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = strokePt; color = Color.BLACK
        }
        drawBodyRunsWithBreaks(
            c, listOf(body), cy, { it }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            truePtPerMm = 1f, blends = blends,
        )
        return bmp
    }

    /** Columns of the run's top edge (y ≈ cy − r = 60) carrying ink, over [x0,x1). */
    private fun topLineInk(bmp: Bitmap, x0: Int, x1: Int): Int {
        var n = 0
        for (x in x0 until x1) for (y in 57..63) {
            if (bmp.getPixel(x, y) != Color.WHITE) { n++; break }
        }
        return n
    }

    @Test
    fun `a run with two ramps and two seal areas keeps its break`() {
        // Seal areas: 150 mm each from the faces, capped together at half the run → [0,100]
        // and [300,400]. Ramps: [0,40] and [360,400]. The flat span (40..360) hosts the gap,
        // which lands at the centre, clear of both seal areas.
        val bmp = render(listOf(sealedFace(LinerAuthoredReference.AFT), sealedFace(LinerAuthoredReference.FWD)))
        assertTrue("the centered gap must be open", topLineInk(bmp, 195, 205) < 5)
        assertEquals("the gap must never cut the aft seal area", 56, topLineInk(bmp, 42, 98))
        assertEquals("the gap must never cut the fwd seal area", 56, topLineInk(bmp, 302, 358))
    }
}
