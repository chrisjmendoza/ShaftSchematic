package com.android.shaftschematic.geom

import com.android.shaftschematic.model.RunoutStationPlacement
import com.android.shaftschematic.model.RunoutStationPlacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Derived body stations on a broken run spread with equal gaps, ends included, over the run's
 * DRAWN length — the visible pieces either side of the S-break gap laid end to end (on-device
 * reports: the middle station of an odd count first sat in the paper gap, then — snapped to the
 * gap edge — on its neighbour's anchor).
 */
class RunoutStationBreakGapTest {

    private val xAt: (Float) -> Float = { it }
    private val mmAt: (Float) -> Float = { it }
    private val body = listOf(RunoutComponentSpan("b1", RunoutComponentKind.BODY, 0f, 400f))
    private val gap = listOf(190f..210f)
    private val clear = 4f

    /** The run's visible pieces under [gap] widened by [clear]. */
    private val pieces = listOf(0f..186f, 214f..400f)

    private fun stations(
        count: Int,
        gaps: List<ClosedFloatingPointRange<Float>> = gap,
        placements: RunoutStationPlacements = RunoutStationPlacements(),
        spans: List<RunoutComponentSpan> = body,
    ) = collectRunoutStations(
        spans, mapOf("b1" to count), xAt, mmAtX = mmAt, placements = placements,
        bodyBreakGapsX = gaps, breakGapClearX = clear,
    )

    private fun inGap(x: Float) = gap.any { x > it.start - clear && x < it.endInclusive + clear }

    /** Distance along the visible pieces from the run's AFT end to [x]. */
    private fun drawnPos(x: Float): Float {
        var acc = 0f
        for (p in pieces) {
            if (x <= p.endInclusive) return acc + (x - p.start).coerceAtLeast(0f)
            acc += p.endInclusive - p.start
        }
        return acc
    }

    @Test
    fun `one to four stations space evenly over the drawn length and never sit in the gap`() {
        val visible = pieces.sumOf { (it.endInclusive - it.start).toDouble() }.toFloat()
        for (n in 1..4) {
            val xs = stations(n).map { it.stationX }
            assertTrue("count $n: none in the gap", xs.none { inGap(it) })
            // Drawn gaps: run AFT end → s0 → … → s(n−1) → run FWD end, all equal.
            val pos = listOf(0f) + xs.map { drawnPos(it) } + listOf(visible)
            val gaps = pos.zipWithNext { a, b -> b - a }
            val expected = visible / (n + 1)
            gaps.forEach { assertEquals("count $n drawn gap", expected, it, 1f) }
            // No two anchors share a point — the regression was two leaders on one anchor.
            xs.zipWithNext { a, b -> assertTrue("count $n: anchors $a, $b too close", b - a >= 20f) }
            assertEquals("count $n: order", xs.sorted(), xs)
        }
    }

    @Test
    fun `the middle station of three lands on a gap edge, its neighbours a quarter away`() {
        val s = stations(3)
        assertEquals(93f, s[0].stationX, 1e-3f)
        assertEquals(186f, s[1].stationX, 1e-3f)
        assertEquals(307f, s[2].stationX, 1e-3f)
        assertEquals("station mm follows the drawn x", s[1].stationX, s[1].stationMm, 1e-3f)
    }

    @Test
    fun `an unbroken run keeps its plain equal spacing`() {
        for (n in 1..4) {
            val xs = stations(n, gaps = emptyList()).map { it.stationX }
            xs.forEachIndexed { i, x -> assertEquals(400f * (i + 1) / (n + 1), x, 1e-3f) }
        }
    }

    @Test
    fun `a gap on another run leaves this run's spacing alone`() {
        assertEquals(
            stations(3, gaps = emptyList()).map { it.stationX },
            stations(3, gaps = listOf(590f..610f)).map { it.stationX },
        )
    }

    @Test
    fun `a fragmented body apportions per run and spaces only the broken run over its pieces`() {
        val runs = listOf(
            RunoutComponentSpan("b1", RunoutComponentKind.BODY, 0f, 400f),
            RunoutComponentSpan("b1", RunoutComponentKind.BODY, 500f, 400f),
        )
        val broken = stations(4, spans = runs).map { it.stationX }
        val plain = stations(4, gaps = emptyList(), spans = runs).map { it.stationX }
        // Two stations per run; the second run (no gap) is untouched.
        assertEquals(plain.drop(2), broken.drop(2))
        assertEquals(listOf(124f, 276f), broken.take(2).map { Math.round(it).toFloat() })
        assertFalse(broken.any { inGap(it) })
    }

    @Test
    fun `stations keep their AFT to FWD order`() {
        for (n in 1..9) {
            val xs = stations(n).map { it.stationX }
            assertEquals("count $n", xs.sorted(), xs)
            assertFalse("count $n", xs.any { inGap(it) })
        }
    }

    @Test
    fun `a dragged station stays where the user put it, gap or not`() {
        val pinned = RunoutStationPlacements(listOf(RunoutStationPlacement("b1", 1, 200f)))
        val s = stations(3, placements = pinned)
        assertEquals(200f, s[1].stationX, 1e-3f)
        // Its derived siblings keep the drawn-even positions they have without the pin.
        val free = stations(3)
        assertEquals(free[0].stationX, s[0].stationX, 1e-3f)
        assertEquals(free[2].stationX, s[2].stationX, 1e-3f)
        assertTrue(abs(s[2].stationX - s[1].stationX) > 0f)
    }
}
