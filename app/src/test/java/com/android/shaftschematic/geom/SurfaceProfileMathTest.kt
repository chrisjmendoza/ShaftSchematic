package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `geom/SurfaceProfileMath.kt` — the outer-surface envelope and undercut
 * notch geometry. Scenarios mirror the feature's real cases: a cut inside one body, a
 * cut crossing a liner edge (the "goes past the liner" requirement), a cut running onto
 * a taper, and implausible floors that must degrade to warnings, never adjusted data.
 */
class SurfaceProfileMathTest {

    private val body = SurfaceSeg(0f, 1000f, 114.3f, 114.3f)
    private val liner = SurfaceSeg(300f, 600f, 127f, 127f)

    // ── outerDiaAt / envelope ──

    @Test
    fun `outer dia is the max where a liner overlaps the body`() {
        val segs = listOf(body, liner)
        assertEquals(114.3f, outerDiaAt(segs, 100f), 1e-4f)
        assertEquals(127f, outerDiaAt(segs, 450f), 1e-4f)
        assertEquals(114.3f, outerDiaAt(segs, 800f), 1e-4f)
        assertEquals(0f, outerDiaAt(segs, 1200f), 1e-4f)
    }

    @Test
    fun `envelope over a liner-on-body region steps at the liner edges`() {
        val env = buildSurfaceEnvelope(listOf(body, liner), 200f, 700f)
        assertEquals(3, env.size)
        assertEquals(114.3f, env[0].dia0Mm, 1e-3f)
        assertEquals(127f, env[1].dia0Mm, 1e-3f)
        assertEquals(127f, env[1].dia1Mm, 1e-3f)
        assertEquals(114.3f, env[2].dia1Mm, 1e-3f)
        assertEquals(300f, env[0].x1Mm, 1e-3f)
        assertEquals(600f, env[2].x0Mm, 1e-3f)
    }

    @Test
    fun `envelope refines the crossing of two overlapping linear surfaces`() {
        // A constant 100 surface and a linear 90-to-110 surface crossing at x=500.
        val flat = SurfaceSeg(0f, 1000f, 100f, 100f)
        val ramp = SurfaceSeg(0f, 1000f, 90f, 110f)
        val env = buildSurfaceEnvelope(listOf(flat, ramp), 0f, 1000f)
        assertEquals(2, env.size)
        assertEquals(100f, env[0].dia0Mm, 1e-2f)
        assertEquals(500f, env[0].x1Mm, 0.1f)
        assertEquals(110f, env[1].dia1Mm, 1e-2f)
    }

    @Test
    fun `min and max over a span come from the envelope`() {
        val segs = listOf(body, liner)
        assertEquals(114.3f, minOuterDiaOver(segs, 200f, 700f), 1e-3f)
        assertEquals(127f, maxOuterDiaOver(segs, 200f, 700f), 1e-3f)
        assertEquals(0f, minOuterDiaOver(segs, 1100f, 1200f), 1e-3f)
    }

    // ── notchProfiles ──

    @Test
    fun `notch inside one body is a single flat-surface region`() {
        val profiles = notchProfiles(listOf(body), 400f, 500f, floorDiaMm = 101.6f)
        assertEquals(1, profiles.size)
        val p = profiles[0]
        assertEquals(400f, p.startMm, 1e-3f)
        assertEquals(500f, p.endMm, 1e-3f)
        assertEquals(101.6f, p.floorDiaMm, 1e-4f)
        assertEquals(2, p.surface.size)
        assertEquals(114.3f, p.surface[0].diaMm, 1e-3f)
        assertEquals(114.3f, p.surface[1].diaMm, 1e-3f)
    }

    @Test
    fun `notch crossing a liner edge keeps one region with a step face in the surface`() {
        // Cut spans the liner's FWD edge at x=600: liner surface on the AFT side,
        // bare body surface on the FWD side, floor below both.
        val profiles = notchProfiles(listOf(body, liner), 550f, 700f, floorDiaMm = 101.6f)
        assertEquals(1, profiles.size)
        val pts = profiles[0].surface
        assertEquals(550f, profiles[0].startMm, 1e-3f)
        assertEquals(700f, profiles[0].endMm, 1e-3f)
        // Polyline: (550,127) (600,127) (600,114.3) (700,114.3) — duplicate x encodes the step.
        assertEquals(4, pts.size)
        assertEquals(pts[1].xMm, pts[2].xMm, 1e-3f)
        assertEquals(127f, pts[1].diaMm, 1e-3f)
        assertEquals(114.3f, pts[2].diaMm, 1e-3f)
    }

    @Test
    fun `floor above part of the surface truncates the region there`() {
        // Floor sits above the bare body Ø but below the liner OD: only the liner
        // portion has material to remove; nothing draws past the liner edge.
        val profiles = notchProfiles(listOf(body, liner), 550f, 700f, floorDiaMm = 120f)
        assertEquals(1, profiles.size)
        assertEquals(550f, profiles[0].startMm, 1e-3f)
        assertEquals(600f, profiles[0].endMm, 1e-3f)
    }

    @Test
    fun `taper running down to the floor starts the region at the crossing`() {
        val taper = SurfaceSeg(0f, 200f, 100f, 120f)
        val profiles = notchProfiles(listOf(taper), 0f, 200f, floorDiaMm = 110f)
        assertEquals(1, profiles.size)
        // 110 is crossed at x = 100; the shoulder there is zero-height.
        assertEquals(100f, profiles[0].startMm, 0.1f)
        assertEquals(200f, profiles[0].endMm, 1e-3f)
        assertEquals(110f, profiles[0].surface[0].diaMm, 1e-2f)
    }

    @Test
    fun `coverage gap splits the notch into separate regions`() {
        val aftBody = SurfaceSeg(0f, 400f, 114.3f, 114.3f)
        val fwdBody = SurfaceSeg(500f, 1000f, 114.3f, 114.3f)
        val profiles = notchProfiles(listOf(aftBody, fwdBody), 300f, 700f, floorDiaMm = 101.6f)
        assertEquals(2, profiles.size)
        assertEquals(400f, profiles[0].endMm, 1e-3f)
        assertEquals(500f, profiles[1].startMm, 1e-3f)
    }

    @Test
    fun `floor at or above the whole surface yields no drawable region`() {
        assertTrue(notchProfiles(listOf(body), 400f, 500f, floorDiaMm = 114.3f).isEmpty())
        assertTrue(notchProfiles(listOf(body), 400f, 500f, floorDiaMm = 130f).isEmpty())
    }

    @Test
    fun `region surfaces stay ordered aft to fwd with endpoints included`() {
        val profiles = notchProfiles(listOf(body, liner), 200f, 700f, floorDiaMm = 101.6f)
        assertEquals(1, profiles.size)
        val pts = profiles[0].surface
        assertEquals(200f, pts.first().xMm, 1e-3f)
        assertEquals(700f, pts.last().xMm, 1e-3f)
        for (i in 1 until pts.size) {
            assertTrue(pts[i].xMm >= pts[i - 1].xMm - 1e-3f)
        }
    }
}
