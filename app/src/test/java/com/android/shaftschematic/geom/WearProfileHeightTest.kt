package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wear document's "Shaft height" multiplier. Pins: 100% is exactly the natural drawing
 * (so a job that never touches the slider prints the sheet it always did); the drawn height is
 * held to the absolute paper band; and neither end of that band moves a shaft away from its
 * natural height — the floor never fattens a small shaft, the ceiling never shrinks a big one.
 */
class WearProfileHeightTest {

    /** A slender shaft: 6" over 8 ft, fitted across the content column. */
    private val naturalSlenderPt = 6f * 25.4f * (720f / (96f * 25.4f))

    @Test
    fun `100 percent keeps the natural height on every shaft`() {
        listOf(naturalSlenderPt, 12f, 36f, 72f, 108f, 200f).forEach { natural ->
            assertEquals("natural=$natural", 1f, wearProfileHeightScale(1f, natural), 1e-6f)
            assertEquals("natural=$natural", natural, wearProfileDrawnHeightPt(1f, natural), 1e-4f)
        }
    }

    @Test
    fun `the drawn height is clamped to the absolute paper band`() {
        val natural = 72f // mid-band, so both ends of the band are reachable
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MAX, natural),
            1e-3f,
        )
        assertEquals(
            PROFILE_MIN_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MIN, natural),
            1e-3f,
        )
        // A multiplier past the stored bounds is coerced, never honoured.
        assertEquals(
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MAX, natural),
            wearProfileDrawnHeightPt(50f, natural),
            1e-3f,
        )
        assertEquals(
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MIN, natural),
            wearProfileDrawnHeightPt(0.01f, natural),
            1e-3f,
        )
    }

    @Test
    fun `the floor never raises a shaft above its natural height`() {
        // A shaft that naturally draws under the floor keeps that natural height at 100% and
        // cannot be dragged below it — flooring it would fatten a small shaft into something it
        // isn't, and the floor exists for the opposite case.
        val natural = 24f
        assertTrue("premise: natural is under the floor", natural < PROFILE_MIN_SHAFT_HEIGHT_PT)
        assertEquals(natural, wearProfileDrawnHeightPt(1f, natural), 1e-4f)
        assertEquals(natural, wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MIN, natural), 1e-4f)
        // It still grows to the ceiling like any other shaft.
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MAX, natural),
            1e-3f,
        )
    }

    @Test
    fun `the ceiling never shrinks a shaft that naturally draws taller`() {
        // A stubby shaft whose page-width fit already exceeds the ceiling stays where it is at
        // 100% (this sheet's natural scale is a width fit, not a solve the band already bounded),
        // and the slider only shrinks it.
        val natural = 144f
        assertTrue("premise: natural is over the ceiling", natural > PROFILE_MAX_SHAFT_HEIGHT_PT)
        assertEquals(natural, wearProfileDrawnHeightPt(1f, natural), 1e-4f)
        assertEquals(natural, wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MAX, natural), 1e-4f)
        assertEquals(
            PROFILE_MIN_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MIN, natural),
            1e-3f,
        )
    }

    @Test
    fun `between the ends the multiplier applies straight to the radius`() {
        val natural = 60f
        assertEquals(1.5f, wearProfileHeightScale(1.5f, natural), 1e-6f)
        assertEquals(90f, wearProfileDrawnHeightPt(1.5f, natural), 1e-4f)
        assertEquals(0.75f, wearProfileHeightScale(0.75f, natural), 1e-6f)
    }

    @Test
    fun `a degenerate natural height is a no-op`() {
        assertEquals(1f, wearProfileHeightScale(3f, 0f), 1e-6f)
        assertEquals(1f, wearProfileHeightScale(3f, -5f), 1e-6f)
    }

    @Test
    fun `a realistic shaft reaches the whole band`() {
        assertTrue(
            "premise: a slender shaft draws inside the band",
            naturalSlenderPt in PROFILE_MIN_SHAFT_HEIGHT_PT..PROFILE_MAX_SHAFT_HEIGHT_PT,
        )
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MAX, naturalSlenderPt),
            1e-3f,
        )
        assertEquals(
            PROFILE_MIN_SHAFT_HEIGHT_PT,
            wearProfileDrawnHeightPt(PROFILE_HEIGHT_SCALE_MIN, naturalSlenderPt),
            1e-3f,
        )
    }
}
