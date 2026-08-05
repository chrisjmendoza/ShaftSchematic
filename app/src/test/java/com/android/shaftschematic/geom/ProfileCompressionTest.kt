package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProfileCompression v2 — the hand-sheet convention's piecewise x mapping. Pins:
 * uniform mapping when everything fits at the visual scale; per-kind floors (never
 * stretching a span past true scale); proportional-above-floor allocation so a longer
 * body run draws visibly longer and equal runs draw equal (on-device request); exact
 * page-width consumption; monotonicity; edge extrapolation; degenerate floor squeeze.
 */
class ProfileCompressionTest {

    private fun feature(s: Float, e: Float, floor: Float) = ProfileFeatureSpan(s, e, floor)

    @Test
    fun `fits at true scale - single uncompressed linear segment`() {
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 100f,
            features = listOf(feature(0f, 20f, 80f), feature(80f, 100f, 100f)),
            contentLeft = 0f, contentRight = 720f,
            diaPtPerMm = 2f, // 100mm * 2 = 200pt < 720pt
        )
        assertEquals(1, map.segments.size)
        assertFalse(map.segments.single().compressed)
        assertEquals(0f, map.xAt(0f), 1e-3f)
        assertEquals(200f, map.xAt(100f), 1e-3f)
        assertEquals(100f, map.xAt(50f), 1e-3f)
    }

    @Test
    fun `equal body runs draw equal, longer runs draw longer`() {
        // Window 2000mm; two liners split it into three body runs of 300 / 300 / 900 mm.
        // Scale 1 pt/mm → total true 2000pt >> 700pt page.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(feature(300f, 550f, 100f), feature(850f, 1100f, 100f)),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
        )
        val runs = map.segments.filter { it.startMm == 0f || it.startMm == 550f || it.startMm == 1100f }
        assertEquals(3, runs.size)
        val w1 = runs[0].x1 - runs[0].x0 // 300mm run
        val w2 = runs[1].x1 - runs[1].x0 // 300mm run
        val w3 = runs[2].x1 - runs[2].x0 // 900mm run
        assertEquals("equal true lengths draw equal", w1, w2, 1e-2f)
        assertTrue("longer run draws visibly longer", w3 > w1 + 1f)
        // Full width consumed, endpoints anchored.
        assertEquals(0f, map.x0, 1e-3f)
        assertEquals(700f, map.x1, 0.1f)
    }

    @Test
    fun `floors hold - every span keeps its writable minimum`() {
        // Heavy squeeze: only 600pt for 2000mm at scale 1. Liners floored at 100,
        // body runs at the default PROFILE_MIN_BODY_RUN_PT.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(feature(200f, 900f, 100f), feature(1100f, 1800f, 100f)),
            contentLeft = 0f, contentRight = 600f,
            diaPtPerMm = 1f,
        )
        val liner1 = map.segments.first { it.startMm == 200f }
        val liner2 = map.segments.first { it.startMm == 1100f }
        assertTrue(liner1.x1 - liner1.x0 >= 100f - 1e-2f)
        assertTrue(liner2.x1 - liner2.x0 >= 100f - 1e-2f)
        map.segments.filter { it.compressed }.forEach {
            assertTrue(
                "no compressed span below the smallest floor",
                it.x1 - it.x0 >= minOf(PROFILE_MIN_BODY_RUN_PT, 100f) - 1e-2f,
            )
        }
        assertEquals(600f, map.x1, 0.1f)
    }

    @Test
    fun `a tiny run stays at true scale - floors never stretch`() {
        // 20mm body run between two liners: true width 20pt < floor → draws 20pt, uncompressed.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(feature(0f, 500f, 100f), feature(520f, 1000f, 100f)),
            contentLeft = 0f, contentRight = 600f,
            diaPtPerMm = 1f,
        )
        val tiny = map.segments.first { it.startMm == 500f && it.endMm == 520f }
        assertEquals(20f, tiny.x1 - tiny.x0, 0.5f)
        assertFalse(tiny.compressed)
    }

    @Test
    fun `pinned span (MAX_VALUE floor) always draws true scale`() {
        // A keyway-bearing body pinned at true scale amid heavy compression.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = listOf(feature(1000f, 1150f, Float.MAX_VALUE)),
            contentLeft = 0f, contentRight = 600f,
            diaPtPerMm = 1f,
        )
        val pinned = map.segments.first { it.startMm == 1000f }
        assertEquals(150f, pinned.x1 - pinned.x0, 0.5f)
        assertFalse(pinned.compressed)
    }

    @Test
    fun `mapping is strictly monotonic across segment boundaries`() {
        val map = buildCompressedProfileXMap(
            0f, 2000f,
            listOf(feature(100f, 400f, 100f), feature(900f, 1200f, 100f)),
            0f, 650f, 1f,
        )
        var prev = map.xAt(0f)
        var mm = 5f
        while (mm <= 2000f) {
            val x = map.xAt(mm)
            assertTrue("x must increase at mm=$mm", x > prev)
            prev = x
            mm += 5f
        }
    }

    @Test
    fun `positions outside the window extrapolate at the edge scale`() {
        val map = buildCompressedProfileXMap(
            100f, 2100f, listOf(feature(100f, 350f, 100f)), 0f, 500f, 1f,
        )
        assertTrue(map.xAt(50f) < map.x0)
        assertTrue(map.xAt(2200f) > map.x1)
    }

    @Test
    fun `degenerate floor overflow squeezes floors proportionally`() {
        // Floors alone (3 × 100 + gaps) exceed a tiny page → everything scales down,
        // still monotonic and full-width.
        val map = buildCompressedProfileXMap(
            0f, 4000f,
            listOf(
                feature(0f, 800f, 100f),
                feature(1200f, 2000f, 100f),
                feature(2400f, 3200f, 100f),
            ),
            0f, 200f, 1f,
        )
        assertEquals(200f, map.x1, 0.5f)
        var prev = map.xAt(0f)
        var mm = 20f
        while (mm <= 4000f) {
            val x = map.xAt(mm)
            assertTrue(x > prev)
            prev = x
            mm += 20f
        }
    }

    @Test
    fun `pinned liners force the scale down until they fit - height yields, never the liner`() {
        // Three pinned liners totaling 2100mm on a 700pt page: at the desired 0.4 the
        // liners alone need 840pt → the solve must come back below 700/2100 ≈ 0.333.
        val features = listOf(
            feature(100f, 800f, Float.MAX_VALUE),
            feature(1000f, 1700f, Float.MAX_VALUE),
            feature(2000f, 2700f, Float.MAX_VALUE),
        )
        val scale = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = features, contentWidth = 700f, scaleHi = 0.4f,
        )
        assertTrue(scale < 0.34f)
        // At the solved scale the map keeps every liner at TRUE width.
        val map = buildCompressedProfileXMap(0f, 3000f, features, 0f, 700f, scale)
        features.forEach { f ->
            val seg = map.segments.first { it.startMm == f.startMm }
            assertEquals((f.endMm - f.startMm) * scale, seg.x1 - seg.x0, 0.5f)
            assertFalse(seg.compressed)
        }
        assertEquals(700f, map.x1, 1.5f)
    }

    @Test
    fun `scale solve returns the desired scale when everything already fits`() {
        val scale = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 500f,
            features = listOf(feature(100f, 300f, Float.MAX_VALUE)),
            contentWidth = 700f, scaleHi = 0.4f,
        )
        assertEquals(0.4f, scale, 1e-6f)
    }

    @Test
    fun `width solve consumes the target exactly and respects clamps`() {
        val lens = listOf(300f, 300f, 900f, 20f)
        val caps = lens.map { it * 1f }
        val floors = listOf(64f, 64f, 64f, 20f).mapIndexed { i, f -> minOf(f, caps[i]) }
        val w = solveSpanWidths(lens, floors, caps, targetWidth = 500f)
        assertEquals(500f, w.sum(), 0.1f)
        w.forEachIndexed { i, wi ->
            assertTrue(wi >= floors[i] - 1e-3f)
            assertTrue(wi <= caps[i] + 1e-3f)
        }
        assertEquals("equal lengths → equal widths", w[0], w[1], 1e-2f)
        assertTrue("longer → wider", w[2] > w[0])
    }
}
