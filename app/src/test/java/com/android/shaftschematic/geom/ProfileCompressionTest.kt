package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProfileCompression — the hand-sheet convention's piecewise x mapping. Pins:
 * uniform mapping when everything fits at the diameter scale; detail spans always at
 * true scale; equal-cap waterfill (short bodies never shrink, long ones share the
 * squeeze); exact width consumption; monotonicity; edge extrapolation.
 */
class ProfileCompressionTest {

    @Test
    fun `fits at true scale - single uncompressed linear segment`() {
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 100f,
            fixedSpansMm = listOf(0f to 20f, 80f to 100f),
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
    fun `compression - details keep true scale and total width is consumed exactly`() {
        // Window 1000mm; details: taper 0..100, liner 400..500 (200mm fixed).
        // Scale 1 pt/mm → total true 1000pt > 500pt page.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 1000f,
            fixedSpansMm = listOf(0f to 100f, 400f to 500f),
            contentLeft = 0f, contentRight = 500f,
            diaPtPerMm = 1f,
        )
        // Detail segments draw at exactly 1 pt/mm.
        val taperSeg = map.segments.first { it.startMm == 0f && it.endMm == 100f }
        assertEquals(100f, taperSeg.x1 - taperSeg.x0, 1e-2f)
        assertFalse(taperSeg.compressed)
        val linerSeg = map.segments.first { it.startMm == 400f && it.endMm == 500f }
        assertEquals(100f, linerSeg.x1 - linerSeg.x0, 1e-2f)
        // Full width consumed, endpoints anchored.
        assertEquals(0f, map.x0, 1e-3f)
        assertEquals(500f, map.x1, 1e-2f)
        // Both body runs (300mm and 500mm true) share 300pt: equal cap → 150pt each.
        val gap1 = map.segments.first { it.startMm == 100f }
        val gap2 = map.segments.first { it.startMm == 500f }
        assertEquals(150f, gap1.x1 - gap1.x0, 1e-2f)
        assertEquals(150f, gap2.x1 - gap2.x0, 1e-2f)
        assertTrue(gap1.compressed && gap2.compressed)
    }

    @Test
    fun `short body keeps true width while long ones absorb the squeeze`() {
        // Bodies: 20mm (short, between the liners) and 880mm (long). Fixed: two 50mm liners.
        // Scale 1: true total 1000 > 400. Avail = 400 - 100 = 300.
        // Cap solve over [20, 880]: 20 stays, long gets 280.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 1000f,
            fixedSpansMm = listOf(0f to 50f, 70f to 120f),
            contentLeft = 0f, contentRight = 400f,
            diaPtPerMm = 1f,
        )
        val shortBody = map.segments.first { it.startMm == 50f && it.endMm == 70f }
        assertEquals(20f, shortBody.x1 - shortBody.x0, 1e-2f)
        assertFalse("a body at or under the cap never compresses", shortBody.compressed)
        val longBody = map.segments.first { it.startMm == 120f }
        assertEquals(280f, longBody.x1 - longBody.x0, 1e-2f)
        assertTrue(longBody.compressed)
        assertEquals(400f, map.x1, 1e-2f)
    }

    @Test
    fun `mapping is strictly monotonic across segment boundaries`() {
        val map = buildCompressedProfileXMap(
            0f, 1000f, listOf(100f to 200f, 600f to 750f), 0f, 500f, 1f,
        )
        var prev = map.xAt(0f)
        var mm = 5f
        while (mm <= 1000f) {
            val x = map.xAt(mm)
            assertTrue("x must increase at mm=$mm", x > prev)
            prev = x
            mm += 5f
        }
    }

    @Test
    fun `positions outside the window extrapolate at the edge scale`() {
        val map = buildCompressedProfileXMap(
            100f, 1100f, listOf(100f to 200f), 0f, 500f, 1f,
        )
        // First segment is the fixed span at scale 1 → 50mm before the window = 50pt left.
        assertEquals(-50f, map.xAt(50f), 1e-2f)
        assertTrue(map.xAt(1200f) > map.x1)
    }

    @Test
    fun `equal cap solve consumes exactly the available width`() {
        val widths = listOf(20f, 300f, 880f)
        val avail = 400f
        val cap = solveEqualCap(widths, avail)
        val consumed = widths.sumOf { minOf(it, cap).toDouble() }.toFloat()
        assertEquals(avail, consumed, 1e-2f)
        assertTrue("short run stays under the cap", 20f < cap)
    }

    @Test
    fun `plan stats count fixed length and compressible gaps`() {
        val stats = compressedProfilePlanStats(
            0f, 1000f,
            listOf(0f to 100f, 400f to 500f, 480f to 520f), // last two overlap → merge
        )
        assertEquals(220f, stats.fixedLenMm, 1e-2f)
        // Gaps: 100..400 and 520..1000.
        assertEquals(2, stats.compressibleGapCount)
    }

    @Test
    fun `no fixed spans - bodies-only shaft still compresses to the page`() {
        val map = buildCompressedProfileXMap(0f, 2000f, emptyList(), 0f, 600f, 1f)
        assertEquals(0f, map.x0, 1e-3f)
        assertEquals(600f, map.x1, 1e-2f)
        assertTrue(map.isCompressedOver(0f, 2000f))
    }
}
