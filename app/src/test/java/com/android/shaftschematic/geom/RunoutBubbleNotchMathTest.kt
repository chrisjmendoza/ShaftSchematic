package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.asin

/**
 * The runout bubble's keyway notch draws at the proportion of the shaft's own keyway
 * (W/Ø → half-width over r, 2·D/Ø → depth over r), with legibility floors and caps applied after.
 */
class RunoutBubbleNotchMathTest {

    private val inch = 25.4f
    private val r = 100f
    private val hairline = 0.1f

    private fun gapOf(slotHalf: Float, r: Float) =
        Math.toDegrees(asin((slotHalf / r).toDouble())).toFloat()

    @Test fun `fixture-like keyway draws at true proportion`() {
        // 1.75" wide, 0.69" deep on a 10.36" host: W/Ø ≈ 0.169, 2·D/Ø ≈ 0.133.
        val n = bubbleKeywayNotch(r, 1.75f / 10.36f, 0.69f / 10.36f, stroke = hairline)
        assertEquals(16.9f, n.slotHalf, 0.05f)
        assertEquals(13.3f, n.slotDepth, 0.05f)
        assertEquals(gapOf(n.slotHalf, r), n.gapDeg, 1e-4f)
    }

    @Test fun `fallback is the square key, quarter radius each way`() {
        val n = bubbleKeywayNotch(
            r, BUBBLE_NOTCH_FALLBACK_WIDTH_OVER_DIA, BUBBLE_NOTCH_FALLBACK_DEPTH_OVER_DIA, hairline,
        )
        assertEquals(25f, n.slotHalf, 1e-4f)
        assertEquals(25f, n.slotDepth, 1e-4f)
        assertEquals(gapOf(25f, r), n.gapDeg, 1e-4f)
    }

    @Test fun `half-width floor follows the stroke`() {
        // True half-width 1; 1.5 × stroke 6 = 9 beats 0.06 r = 6.
        val n = bubbleKeywayNotch(r, 0.01f, 0.1f, stroke = 6f)
        assertEquals(9f, n.slotHalf, 1e-4f)
        assertEquals(gapOf(9f, r), n.gapDeg, 1e-4f)
    }

    @Test fun `half-width floor follows the radius under a hairline stroke`() {
        val n = bubbleKeywayNotch(r, 0.01f, 0.1f, stroke = hairline)
        assertEquals(6f, n.slotHalf, 1e-4f)
    }

    @Test fun `half-width cap keeps the ring`() {
        val n = bubbleKeywayNotch(r, 0.9f, 0.1f, stroke = hairline)
        assertEquals(35f, n.slotHalf, 1e-4f)
        assertEquals(gapOf(35f, r), n.gapDeg, 1e-4f)
    }

    @Test fun `depth floor follows the stroke`() {
        val n = bubbleKeywayNotch(r, 0.2f, 0.001f, stroke = 3f)
        assertEquals(BUBBLE_NOTCH_MIN_DEPTH_STROKES * 3f, n.slotDepth, 1e-4f)
    }

    @Test fun `depth cap keeps the slot off the centred value`() {
        val n = bubbleKeywayNotch(r, 0.2f, 0.6f, stroke = hairline)
        assertEquals(50f, n.slotDepth, 1e-4f)
    }

    @Test fun `proportion falls back with no keyway`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(Body(id = "b", lengthMm = 2000f, diaMm = 200f)))
        assertEquals(
            BUBBLE_NOTCH_FALLBACK_WIDTH_OVER_DIA to BUBBLE_NOTCH_FALLBACK_DEPTH_OVER_DIA,
            sheetKeywayProportion(spec),
        )
    }

    @Test fun `proportion takes the AFT-most keyway and a taper's large-end diameter`() {
        val aftTaper = Taper(
            id = "aftTaper", startFromAftMm = 0f, lengthMm = 20f * inch,
            startDiaMm = 10f * inch, endDiaMm = 9f * inch,
            keywayWidthMm = 2f * inch, keywayDepthMm = 0.75f * inch, keywayLengthMm = 12f * inch,
        )
        val fwdBody = Body(
            id = "fwdBody", startFromAftMm = 100f * inch, lengthMm = 40f * inch, diaMm = 8f * inch,
            keywayWidthMm = 1f * inch, keywayDepthMm = 0.5f * inch, keywayLengthMm = 6f * inch,
        )
        // Listed body-first to prove the pick is positional, not list order.
        val spec = ShaftSpec(overallLengthMm = 200f * inch, bodies = listOf(fwdBody), tapers = listOf(aftTaper))
        val (w, d) = sheetKeywayProportion(spec)
        assertEquals(2f / 10f, w, 1e-5f)       // large end, never the 9" small end
        assertEquals(0.75f / 10f, d, 1e-5f)
    }

    @Test fun `proportion reads a body keyway when it is the AFT-most`() {
        val aftBody = Body(
            id = "aftBody", startFromAftMm = 0f, lengthMm = 40f * inch, diaMm = 8f * inch,
            keywayWidthMm = 2f * inch, keywayDepthMm = 0.5f * inch, keywayLengthMm = 6f * inch,
        )
        val fwdTaper = Taper(
            id = "fwdTaper", startFromAftMm = 180f * inch, lengthMm = 20f * inch,
            startDiaMm = 9f * inch, endDiaMm = 10f * inch,
            keywayWidthMm = 1f * inch, keywayDepthMm = 0.25f * inch, keywayLengthMm = 12f * inch,
        )
        val spec = ShaftSpec(overallLengthMm = 200f * inch, bodies = listOf(aftBody), tapers = listOf(fwdTaper))
        val (w, d) = sheetKeywayProportion(spec)
        assertEquals(2f / 8f, w, 1e-5f)
        assertEquals(0.5f / 8f, d, 1e-5f)
    }
}
