package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** LinerShoulderMath — the drawn spec clamps and the silhouette both draw sites decompose. */
class LinerShoulderMathTest {

    // ── shoulderDrawSpec ─────────────────────────────────────────────────────

    @Test
    fun `no step to draw when the shoulder OD reaches the liner OD`() {
        assertNull(shoulderDrawSpec(20f, 200f, linerRPx = 50f, shoulderRPx = 50f, filletRPx = 5f, minWidthPx = 7f))
        assertNull(shoulderDrawSpec(20f, 200f, linerRPx = 50f, shoulderRPx = 60f, filletRPx = 5f, minWidthPx = 7f))
    }

    @Test
    fun `zero length or zero run draws nothing`() {
        assertNull(shoulderDrawSpec(0f, 200f, 50f, 40f, 0f, 7f))
        assertNull(shoulderDrawSpec(20f, 0f, 50f, 40f, 0f, 7f))
    }

    @Test
    fun `a sub-pixel shoulder takes the visibility floor`() {
        val s = shoulderDrawSpec(trueLenPx = 0.5f, runWidthPx = 200f, linerRPx = 50f,
            shoulderRPx = 40f, filletRPx = 0f, minWidthPx = 7f)!!
        assertTrue(s.lenPx >= 7f - 1e-3f)
    }

    @Test
    fun `a shoulder never exceeds the blend host-fraction cap`() {
        // The cap rides drawnBlendWidthPx's MAX_BLEND_FRAC_OF_HOST — per end, so even two
        // capped shoulders leave flat span between them.
        val s = shoulderDrawSpec(trueLenPx = 500f, runWidthPx = 200f, linerRPx = 50f,
            shoulderRPx = 40f, filletRPx = 0f, minWidthPx = 7f)!!
        assertEquals(200f * MAX_BLEND_FRAC_OF_HOST, s.lenPx, 1e-3f)
    }

    @Test
    fun `the fillet is capped by step height and run width`() {
        // Step is 4 px, so a 50 px radius must cap at 0.9 × 4.
        val byStep = shoulderDrawSpec(20f, 400f, linerRPx = 50f, shoulderRPx = 46f,
            filletRPx = 50f, minWidthPx = 7f)!!
        assertEquals(4f * SHOULDER_FILLET_MAX_FRAC_OF_STEP, byStep.filletRPx, 1e-3f)
        // Step is huge, so a 50 px radius caps at 10% of the 100 px run.
        val byRun = shoulderDrawSpec(20f, 100f, linerRPx = 200f, shoulderRPx = 40f,
            filletRPx = 50f, minWidthPx = 7f)!!
        assertEquals(100f * SHOULDER_FILLET_MAX_FRAC_OF_RUN, byRun.filletRPx, 1e-3f)
    }

    // ── linerTopSilhouette ───────────────────────────────────────────────────

    @Test
    fun `no shoulders is the plain rectangle`() {
        val pts = linerTopSilhouette(10f, 110f, 50f, aft = null, fwd = null)
        assertEquals(2, pts.size)
        assertEquals(ShoulderPoint(10f, 50f), pts.first())
        assertEquals(ShoulderPoint(110f, 50f), pts.last())
    }

    @Test
    fun `an aft shoulder starts reduced, steps, and lands tangent on the OD`() {
        val aft = ShoulderDrawSpec(lenPx = 30f, odRPx = 40f, filletRPx = 5f)
        val pts = linerTopSilhouette(0f, 200f, 50f, aft = aft, fwd = null)
        assertEquals(ShoulderPoint(0f, 40f), pts.first())
        assertEquals(ShoulderPoint(30f, 40f), pts[1])          // shoulder surface
        assertEquals(ShoulderPoint(30f, 45f), pts[2])          // face rises to the spring point
        // The arc's last sample is exactly one radius past the face, on the OD.
        val arcEnd = pts[2 + SHOULDER_ARC_STEPS]
        assertEquals(35f, arcEnd.xPx, 1e-3f)
        assertEquals(50f, arcEnd.rPx, 1e-3f)
        assertEquals(ShoulderPoint(200f, 50f), pts.last())
    }

    @Test
    fun `a fwd shoulder mirrors the aft construction`() {
        val fwd = ShoulderDrawSpec(lenPx = 30f, odRPx = 40f, filletRPx = 5f)
        val pts = linerTopSilhouette(0f, 200f, 50f, aft = null, fwd = fwd)
        assertEquals(ShoulderPoint(0f, 50f), pts.first())
        // First arc sample sits one radius BEFORE the face, on the OD.
        assertEquals(165f, pts[1].xPx, 1e-3f)
        assertEquals(50f, pts[1].rPx, 1e-3f)
        assertEquals(ShoulderPoint(170f, 40f), pts[pts.size - 2])
        assertEquals(ShoulderPoint(200f, 40f), pts.last())
    }

    @Test
    fun `x never runs backwards, shoulders on both ends included`() {
        val aft = ShoulderDrawSpec(25f, 42f, 4f)
        val fwd = ShoulderDrawSpec(35f, 38f, 6f)
        val pts = linerTopSilhouette(0f, 300f, 55f, aft, fwd)
        for (i in 1 until pts.size) {
            assertTrue("x regressed at $i: ${pts[i - 1].xPx} -> ${pts[i].xPx}",
                pts[i].xPx >= pts[i - 1].xPx - 1e-3f)
        }
    }

    @Test
    fun `sharp corners draw a plain step with no arc points`() {
        val aft = ShoulderDrawSpec(lenPx = 30f, odRPx = 40f, filletRPx = 0f)
        val pts = linerTopSilhouette(0f, 200f, 50f, aft = aft, fwd = null)
        // (x0, 40) (30, 40) (30, 50) (200, 50) — nothing else.
        assertEquals(4, pts.size)
        assertEquals(ShoulderPoint(30f, 50f), pts[2])
    }

    @Test
    fun `the standard radius list starts sharp and grows`() {
        assertEquals(0f, LINER_SHOULDER_STD_RADII_IN.first(), 0f)
        assertTrue(LINER_SHOULDER_STD_RADII_IN.zipWithNext().all { (a, b) -> b > a })
    }
}
