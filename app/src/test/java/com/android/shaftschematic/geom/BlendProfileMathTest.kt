package com.android.shaftschematic.geom

import com.android.shaftschematic.model.BlendProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * BlendProfileMath — the drawn curve joining two radii. Pins what both draw sites rely on:
 * the curve hits its endpoints exactly, never reverses, meets an eased end flat and a
 * non-eased end at a corner, and the drawn-width floor keeps a short blend visible without
 * letting it swallow its host run.
 */
class BlendProfileMathTest {

    private val eps = 1e-3f

    // ───────── blendRadiusFrac ─────────

    @Test
    fun `every profile spans exactly zero to one`() {
        for (p in BlendProfile.values()) {
            for (larger in listOf(true, false)) {
                val a = p.easeAftFrac(larger)
                val b = p.easeFwdFrac(larger)
                assertEquals("$p start", 0f, blendRadiusFrac(0f, a, b), eps)
                assertEquals("$p end", 1f, blendRadiusFrac(1f, a, b), eps)
            }
        }
    }

    @Test
    fun `every profile is monotone across the span`() {
        for (p in BlendProfile.values()) {
            val a = p.easeAftFrac(true)
            val b = p.easeFwdFrac(true)
            var prev = -1f
            for (i in 0..100) {
                val v = blendRadiusFrac(i / 100f, a, b)
                assertTrue("$p reversed at $i: $v < $prev", v >= prev - eps)
                prev = v
            }
        }
    }

    @Test
    fun `ogee is a symmetric S through the midpoint`() {
        val a = BlendProfile.OGEE.easeAftFrac(true)
        val b = BlendProfile.OGEE.easeFwdFrac(true)
        assertEquals(0.5f, blendRadiusFrac(0.5f, a, b), eps)
        // Point symmetry about (0.5, 0.5).
        for (i in 1..9) {
            val t = i / 20f
            val lo = blendRadiusFrac(t, a, b)
            val hi = blendRadiusFrac(1f - t, a, b)
            assertEquals("symmetry at t=$t", 1f, lo + hi, eps)
        }
    }

    /** Slope at an eased end must vanish — that is what "no corner" means geometrically. */
    @Test
    fun `an eased end meets its neighbour flat and a bare end meets it at a corner`() {
        val d = 1e-3f
        val ogeeA = BlendProfile.OGEE.easeAftFrac(true)
        val ogeeB = BlendProfile.OGEE.easeFwdFrac(true)
        assertTrue("ogee aft not flat", blendRadiusFrac(d, ogeeA, ogeeB) / d < 0.05f)
        assertTrue("ogee fwd not flat", (1f - blendRadiusFrac(1f - d, ogeeA, ogeeB)) / d < 0.05f)

        // FILLET with the larger radius aft eases only there; the fwd end keeps its corner.
        val filA = BlendProfile.FILLET.easeAftFrac(true)
        val filB = BlendProfile.FILLET.easeFwdFrac(true)
        assertTrue("fillet aft not flat", blendRadiusFrac(d, filA, filB) / d < 0.05f)
        assertTrue("fillet fwd should keep a corner", (1f - blendRadiusFrac(1f - d, filA, filB)) / d > 0.5f)
    }

    @Test
    fun `ease fractions summing past one are scaled, never discontinuous`() {
        // 0.8 + 0.8 renormalizes to 0.5 + 0.5; the curve must still land on its endpoints.
        assertEquals(0f, blendRadiusFrac(0f, 0.8f, 0.8f), eps)
        assertEquals(1f, blendRadiusFrac(1f, 0.8f, 0.8f), eps)
        var prev = -1f
        for (i in 0..50) {
            val v = blendRadiusFrac(i / 50f, 0.8f, 0.8f)
            assertTrue(v >= prev - eps)
            prev = v
        }
    }

    // ───────── blendPolyline ─────────

    @Test
    fun `polyline runs aft to fwd and lands on both diameters`() {
        val pts = blendPolyline(100f, 150f, 40f, 30f, BlendProfile.OGEE)
        assertEquals(BLEND_CURVE_STEPS + 1, pts.size)
        assertEquals(100f, pts.first().xMm, eps)
        assertEquals(150f, pts.last().xMm, eps)
        // Points carry DIAMETER, the SurfacePoint convention — radius 40 → Ø 80.
        assertEquals(80f, pts.first().diaMm, eps)
        assertEquals(60f, pts.last().diaMm, eps)
        for (i in 1 until pts.size) {
            assertTrue("x reversed", pts[i].xMm >= pts[i - 1].xMm - eps)
            assertTrue("Ø reversed", pts[i].diaMm <= pts[i - 1].diaMm + eps)
        }
    }

    @Test
    fun `an equal radius pair yields no curve to draw`() {
        val pts = blendPolyline(0f, 50f, 40f, 40f, BlendProfile.OGEE)
        assertEquals(2, pts.size)
    }

    @Test
    fun `a degenerate span yields no curve to draw`() {
        assertEquals(2, blendPolyline(50f, 50f, 40f, 30f, BlendProfile.OGEE).size)
    }

    @Test
    fun `an increasing pair blends upward just as an decreasing pair blends down`() {
        val up = blendPolyline(0f, 50f, 30f, 40f, BlendProfile.OGEE)
        val down = blendPolyline(0f, 50f, 40f, 30f, BlendProfile.OGEE)
        // Mirror images about the mean diameter (70f here: Ø60 ↔ Ø80).
        for (i in up.indices) {
            assertEquals("mirror at $i", 140f, up[i].diaMm + down[i].diaMm, eps)
        }
    }

    // ───────── drawnBlendWidthPx ─────────

    @Test
    fun `a sub-pixel blend is floored so the curve still reads`() {
        assertEquals(7f, drawnBlendWidthPx(trueWidthPx = 0.4f, hostWidthPx = 200f, minWidthPx = 7f), eps)
    }

    @Test
    fun `a blend already wider than the floor draws true`() {
        assertEquals(30f, drawnBlendWidthPx(trueWidthPx = 30f, hostWidthPx = 200f, minWidthPx = 7f), eps)
    }

    @Test
    fun `the floor yields to a short host rather than swallowing it`() {
        // Host is only 10 pt wide: the 7 pt floor would eat 70% of it, so the cap wins.
        val w = drawnBlendWidthPx(trueWidthPx = 0.2f, hostWidthPx = 10f, minWidthPx = 7f)
        assertEquals(10f * MAX_BLEND_FRAC_OF_HOST, w, eps)
        assertTrue(w < 7f)
    }

    @Test
    fun `a degenerate host or width draws nothing`() {
        assertEquals(0f, drawnBlendWidthPx(0f, 100f, 7f), eps)
        assertEquals(0f, drawnBlendWidthPx(10f, 0f, 7f), eps)
    }

    @Test
    fun `true width is never stretched past the host cap`() {
        val w = drawnBlendWidthPx(trueWidthPx = 500f, hostWidthPx = 100f, minWidthPx = 7f)
        assertTrue(abs(w - 40f) < eps)
    }
}
