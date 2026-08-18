package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.android.shaftschematic.util.DualLabel
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Invariant tests for the measured-Ø callout placement engine
 * (`geom/WearDiaCalloutLayout.kt`) — the label-width-aware sibling of
 * `RunoutBubbleLayout`. Same posture as `RunoutBubbleLayoutTest`: deterministic cases for
 * each rule plus randomized stress configs asserting the collision guarantees.
 */
class WearDiaCalloutLayoutTest {

    private val minGap = 5f
    private val textH = 8f
    private val rowGap = 3f

    private fun station(key: String, x: Float, w: Float) = DiaCalloutStation(key, x, DualLabel.single(key), w)

    // ── Basic shapes ─────────────────────────────────────────────────────────

    @Test
    fun `empty input yields an empty plan`() {
        val p = planDiaCallouts(emptyList(), 0f, 400f, minGap)
        assertEquals(0, p.rowCount)
        assertEquals(0f, p.bandHeightPt(textH, rowGap, 10f), 0f)
        assertTrue(p.finish(100f, textH, rowGap, { 0f }).isEmpty())
    }

    @Test
    fun `single station sits directly under its station on row 0`() {
        val p = planDiaCallouts(listOf(station("a", 123f, 30f)), 0f, 400f, minGap)
        assertEquals(1, p.rowCount)
        assertEquals(0, p.rows[0])
        assertEquals(123f, p.labelCx[0], 0.01f)
        assertFalse(p.compressed)
    }

    @Test
    fun `well-spaced stations stay on one row directly under their stations`() {
        val s = listOf(station("a", 50f, 30f), station("b", 150f, 30f), station("c", 300f, 30f))
        val p = planDiaCallouts(s, 0f, 400f, minGap)
        assertEquals(1, p.rowCount)
        assertFalse(p.compressed)
        s.forEachIndexed { i, st -> assertEquals(st.stationX, p.labelCx[i], 0.01f) }
    }

    @Test
    fun `crowded stations fall back to two alternating rows`() {
        // 6 stations 10pt apart with 40pt labels: one row needs 5×45 + 40 = 265pt of band,
        // two rows only 5×25 + 40 = 165pt — so a 250pt band forces the stagger.
        val s = (0 until 6).map { station("s$it", 100f + it * 10f, 40f) }
        val p = planDiaCallouts(s, 0f, 250f, minGap)
        assertEquals(2, p.rowCount)
        assertFalse(p.compressed)
        p.rows.forEachIndexed { i, r -> assertEquals(i % 2, r) }
    }

    @Test
    fun `compressed flag set only when even two rows cannot fit`() {
        val s = (0 until 12).map { station("s$it", 50f, 40f) }
        val p = planDiaCallouts(s, 0f, 120f, minGap)
        assertTrue(p.compressed)
    }

    @Test
    fun `band height scales with row count`() {
        val one = planDiaCallouts(listOf(station("a", 50f, 30f)), 0f, 400f, minGap)
        val two = planDiaCallouts((0 until 6).map { station("s$it", 100f + it * 10f, 40f) }, 0f, 250f, minGap)
        assertEquals(10f + textH, one.bandHeightPt(textH, rowGap, 10f), 0.01f)
        assertEquals(10f + 2 * textH + rowGap, two.bandHeightPt(textH, rowGap, 10f), 0.01f)
        assertEquals(textH, one.labelsHeightPt(textH, rowGap), 0.01f)
        assertEquals(2 * textH + rowGap, two.labelsHeightPt(textH, rowGap), 0.01f)
    }

    // ── Randomized invariants ────────────────────────────────────────────────

    private fun randomPlan(rnd: Random): DiaCalloutPlan {
        val n = rnd.nextInt(1, 10)
        val stations = (0 until n).map {
            station("s$it", rnd.nextFloat() * 400f, 24f + rnd.nextFloat() * 24f)
        }
        return planDiaCallouts(stations, 0f, 400f, minGap)
    }

    @Test
    fun `label x-order always equals station x-order`() {
        val rnd = Random(42)
        repeat(200) {
            val p = randomPlan(rnd)
            for (i in 1 until p.stations.size) {
                assertTrue(
                    "labels out of order at $i",
                    p.labelCx[i] >= p.labelCx[i - 1] - 0.01f,
                )
            }
        }
    }

    @Test
    fun `adjacent labels keep their pitch invariants when not compressed`() {
        val rnd = Random(7)
        repeat(200) {
            val p = randomPlan(rnd)
            if (p.compressed) return@repeat
            for (i in 1 until p.stations.size) {
                val wA = p.stations[i - 1].labelWidth
                val wB = p.stations[i].labelWidth
                val need =
                    if (p.rows[i] == p.rows[i - 1]) (wA + wB) * 0.5f + minGap
                    else max(wA, wB) * 0.5f + minGap
                assertTrue(
                    "pitch violated at $i: ${p.labelCx[i] - p.labelCx[i - 1]} < $need",
                    p.labelCx[i] - p.labelCx[i - 1] >= need - 0.5f,
                )
            }
        }
    }

    @Test
    fun `leaders never properly cross each other and never enter a foreign label box`() {
        val rnd = Random(1234)
        repeat(200) {
            val p = randomPlan(rnd)
            if (p.compressed) return@repeat
            // Varied surface depths (a taper-like profile) exercise the departure stub.
            val placed = p.finish(
                row0Top = 130f,
                labelTextHeight = textH,
                rowGap = rowGap,
                surfaceYAt = { i -> 100f + 10f * (i % 3) },
                leaderStartGap = 2f,
            )
            // Leader-leader: no proper crossings.
            for (i in placed.indices) for (j in i + 1 until placed.size) {
                val a = placed[i].leader
                val b = placed[j].leader
                for (s in 0 until a.size - 1) for (q in 0 until b.size - 1) {
                    assertFalse(
                        "leaders $i and $j cross",
                        segmentsProperlyIntersect(a[s], a[s + 1], b[q], b[q + 1]),
                    )
                }
            }
            // Leader vs foreign label boxes (inflated by a small clearance).
            for (i in placed.indices) for (j in placed.indices) {
                if (i == j) continue
                val box = placed[j]
                val l = box.labelCx - box.labelWidth * 0.5f - 1f
                val r = box.labelCx + box.labelWidth * 0.5f + 1f
                val t = box.labelTopY + 0.5f
                val bo = box.labelTopY + textH - 0.5f
                val lead = placed[i].leader
                for (s in 0 until lead.size - 1) {
                    assertFalse(
                        "leader $i enters label $j",
                        segmentIntersectsRect(lead[s], lead[s + 1], l, t, r, bo),
                    )
                }
            }
        }
    }

    @Test
    fun `labels never leave the content bounds when feasible`() {
        val rnd = Random(99)
        repeat(200) {
            val p = randomPlan(rnd)
            if (p.compressed) return@repeat
            for (i in p.stations.indices) {
                val half = p.stations[i].labelWidth * 0.5f
                assertTrue("label $i past left edge", p.labelCx[i] - half >= -0.5f)
                assertTrue("label $i past right edge", p.labelCx[i] + half <= 400.5f)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Segment-vs-axis-aligned-rect intersection (endpoints inside count). */
    private fun segmentIntersectsRect(
        a: LeaderVertex,
        b: LeaderVertex,
        l: Float,
        t: Float,
        r: Float,
        bo: Float,
    ): Boolean {
        fun inside(p: LeaderVertex) = p.x > l && p.x < r && p.y > t && p.y < bo
        if (inside(a) || inside(b)) return true
        val c0 = LeaderVertex(l, t)
        val c1 = LeaderVertex(r, t)
        val c2 = LeaderVertex(r, bo)
        val c3 = LeaderVertex(l, bo)
        return segmentsProperlyIntersect(a, b, c0, c1) ||
            segmentsProperlyIntersect(a, b, c1, c2) ||
            segmentsProperlyIntersect(a, b, c2, c3) ||
            segmentsProperlyIntersect(a, b, c3, c0)
    }

    @Suppress("unused")
    private fun dump(p: DiaCalloutPlan): String =
        p.stations.indices.joinToString { i ->
            "(${p.stations[i].key} x=${p.stations[i].stationX} cx=${p.labelCx[i]} row=${p.rows[i]} w=${p.stations[i].labelWidth})"
        } + " compressed=${p.compressed} diff=${abs(p.labelCx.last() - p.labelCx.first())}"
}
