package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RunoutBubbleGeometry] is radius-agnostic: every spacing it exposes is a function of radius
 * and gap. That is what lets a user-set "Bubble size" multiplier re-proportion the whole bubble
 * field by scaling one number — so this pins the derivations rather than restating them at the
 * call sites.
 */
class RunoutBubbleGeometryScaleTest {

    private fun geom(radius: Float, gap: Float = 5f) =
        RunoutBubbleGeometry(radius, gap, shortLeader = 18f, contentLeft = 0f, contentRight = 720f)

    @Test
    fun `pitches follow the radius`() {
        val g = geom(23f)
        assertEquals(2f * 23f + 5f, g.sameRowPitch, 1e-4f)
        assertEquals(23f + 5f, g.crossRowPitch, 1e-4f)
        assertEquals(2f * 23f + 5f, g.rowStep, 1e-4f)
    }

    @Test
    fun `a bigger bubble spaces its field wider on every axis`() {
        val small = geom(23f)
        val big = geom(23f * 1.5f)
        assertTrue(big.sameRowPitch > small.sameRowPitch)
        assertTrue(big.crossRowPitch > small.crossRowPitch)
        assertTrue(big.rowStep > small.rowStep)
        assertTrue(big.spreadPitch > small.spreadPitch)
        assertTrue(big.spreadMaxOffset > small.spreadMaxOffset)
    }

    @Test
    fun `spread cap and station-fidelity bound stay tied to the same-row pitch`() {
        val g = geom(34.5f)
        assertEquals(g.sameRowPitch * BUBBLE_SPREAD_PITCH_CAP_FACTOR, g.spreadPitch, 1e-4f)
        assertEquals(g.sameRowPitch * BUBBLE_SPREAD_MAX_OFFSET_FACTOR, g.spreadMaxOffset, 1e-4f)
    }

    /**
     * The cross-row pitch is what a leader's final drop needs to pass the rows above it, and
     * the class KDoc's rule 3 leans on `crossRowPitch × 2 ≥ sameRowPitch` to justify checking
     * adjacent pairs only. Scaling the radius must not break that.
     */
    @Test
    fun `adjacent-pair sufficiency survives any radius`() {
        listOf(11.5f, 23f, 34.5f, 46f).forEach { r ->
            val g = geom(r)
            assertTrue("radius $r", 2f * g.crossRowPitch >= g.sameRowPitch)
        }
    }
}
