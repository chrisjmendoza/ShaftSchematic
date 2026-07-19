package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Tests for the shared runout bubble placement engine.
 *
 * The hard invariants under test (see RunoutBubbleLayout.kt):
 *  1. No two bubble circles ever touch or overlap.
 *  2. No leader line ever enters any bubble other than its own.
 *  3. No two leader lines ever properly cross.
 *  4. Bubbles stay within the content bounds.
 *  5. Within a component, stations alternate rows (hand-drawn shop convention).
 */
class RunoutBubbleLayoutTest {

    // PDF-scale geometry: letter landscape content area, 20pt bubbles.
    private val geom = RunoutBubbleGeometry(
        radius = 20f,
        minGap = 5f,
        shortLeader = 18f,
        contentLeft = 36f,
        contentRight = 756f,
    )

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun place(
        stations: List<RunoutStationX>,
        geom: RunoutBubbleGeometry = this.geom,
        anchorY: Float = 300f,
        surfaceYAtMm: (Float) -> Float = { anchorY },
    ): RunoutBubbleResult = planRunoutBubbles(stations, geom).finish(anchorY, surfaceYAtMm)

    private fun assertInvariants(result: RunoutBubbleResult, geom: RunoutBubbleGeometry = this.geom) {
        val bubbles = result.bubbles
        assertEquals("engine reported unresolved collisions", 0, result.unresolvedCollisions)

        // 1. Circles pairwise disjoint
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            val a = bubbles[i]
            val b = bubbles[j]
            val dist = hypot(
                (a.bubbleX - b.bubbleX).toDouble(),
                (a.bubbleCenterY - b.bubbleCenterY).toDouble(),
            )
            assertTrue(
                "bubbles $i and $j overlap (dist=$dist < ${2f * geom.radius})",
                dist >= 2.0 * geom.radius - 1e-3,
            )
        }

        // 2. No leader enters a foreign bubble (at exact radius, no clearance slack)
        for (i in bubbles.indices) {
            val leader = bubbles[i].leader
            for (j in bubbles.indices) {
                if (j == i) continue
                for (s in 0 until leader.size - 1) {
                    assertTrue(
                        "leader of bubble $i enters bubble $j",
                        !segmentIntersectsCircle(
                            leader[s], leader[s + 1],
                            bubbles[j].bubbleX, bubbles[j].bubbleCenterY, geom.radius,
                        ),
                    )
                }
            }
        }

        // 3. No two leaders properly cross
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            val a = bubbles[i].leader
            val b = bubbles[j].leader
            for (s in 0 until a.size - 1) for (q in 0 until b.size - 1) {
                assertTrue(
                    "leaders of bubbles $i and $j cross",
                    !segmentsProperlyIntersect(a[s], a[s + 1], b[q], b[q + 1]),
                )
            }
        }

        // 4. Bubbles inside content bounds
        for ((i, b) in bubbles.withIndex()) {
            assertTrue("bubble $i past left bound", b.bubbleX - geom.radius >= geom.contentLeft - 1e-3)
            assertTrue("bubble $i past right bound", b.bubbleX + geom.radius <= geom.contentRight + 1e-3)
        }
    }

    // ── Station position math (mm domain) ────────────────────────────────────

    @Test
    fun `body stations are cell midpoints across full length`() {
        val positions = runoutStationPositionsMm(100f, 300f, 3, useEdgeInset = false)
        assertEquals(listOf(150f, 250f, 350f), positions)
    }

    @Test
    fun `taper stations inset one inch from each edge`() {
        val positions = runoutStationPositionsMm(0f, 500f, 2, useEdgeInset = true)
        assertEquals(25.4f, positions[0], 1e-3f)
        assertEquals(474.6f, positions[1], 1e-3f)
    }

    @Test
    fun `short taper inset capped at 20 percent of length`() {
        val positions = runoutStationPositionsMm(0f, 50f, 2, useEdgeInset = true)
        assertEquals(10f, positions[0], 1e-3f)   // 20% of 50, not 25.4
        assertEquals(40f, positions[1], 1e-3f)
    }

    @Test
    fun `single station sits at component midpoint`() {
        assertEquals(listOf(250f), runoutStationPositionsMm(200f, 100f, 1, useEdgeInset = true))
    }

    @Test
    fun `zero count or zero length produces no stations`() {
        assertTrue(runoutStationPositionsMm(0f, 100f, 0, false).isEmpty())
        assertTrue(runoutStationPositionsMm(0f, 0f, 3, false).isEmpty())
    }

    // ── Row assignment ───────────────────────────────────────────────────────

    @Test
    fun `stations alternate rows within a component`() {
        val stations = listOf(
            RunoutStationX("body1", 100f, 200f),
            RunoutStationX("body1", 300f, 400f),
            RunoutStationX("body1", 500f, 600f),
        )
        val plan = planRunoutBubbles(stations, geom)
        assertEquals(listOf(0, 1, 0), plan.rows.toList())
        assertEquals(2, plan.rowCount)
    }

    @Test
    fun `single-station component sits on row 0`() {
        val plan = planRunoutBubbles(listOf(RunoutStationX("t1", 50f, 300f)), geom)
        assertEquals(listOf(0), plan.rows.toList())
        assertEquals(1, plan.rowCount)
    }

    @Test
    fun `adjacent component starts on row 1 when previous ended on row 0 nearby`() {
        // comp A: 3 stations ending on row 0 at x=300; comp B starts 20pt away (< sameRowPitch)
        val stations = listOf(
            RunoutStationX("a", 10f, 100f),
            RunoutStationX("a", 20f, 200f),
            RunoutStationX("a", 30f, 300f),
            RunoutStationX("b", 40f, 320f),
            RunoutStationX("b", 50f, 420f),
        )
        val plan = planRunoutBubbles(stations, geom)
        assertEquals(listOf(0, 1, 0, 1, 0), plan.rows.toList())
    }

    @Test
    fun `empty station list produces empty plan`() {
        val plan = planRunoutBubbles(emptyList(), geom)
        assertEquals(0, plan.rowCount)
        assertEquals(0f, plan.sectionHeight(8f))
        assertTrue(plan.finish(300f) { 300f }.bubbles.isEmpty())
    }

    // ── Bubble x solve ───────────────────────────────────────────────────────

    @Test
    fun `sparse stations keep bubbles directly under their stations`() {
        val stations = listOf(
            RunoutStationX("body1", 100f, 150f),
            RunoutStationX("body1", 300f, 400f),
            RunoutStationX("body1", 500f, 650f),
        )
        val plan = planRunoutBubbles(stations, geom)
        assertEquals(150f, plan.bubbleX[0], 1e-2f)
        assertEquals(400f, plan.bubbleX[1], 1e-2f)
        assertEquals(650f, plan.bubbleX[2], 1e-2f)
    }

    @Test
    fun `dense cluster stays centred over its stations`() {
        // Two stations 10pt apart: must spread past crossRowPitch (25) but stay centred at 400.
        // Content span here (36..756) has ample slack, so the leader-clearance spread (rule 7)
        // takes the full gap to crossRowPitch + leaderClearance, not the bare crossRowPitch.
        val stations = listOf(
            RunoutStationX("t1", 100f, 395f),
            RunoutStationX("t1", 110f, 405f),
        )
        val plan = planRunoutBubbles(stations, geom)
        val mid = (plan.bubbleX[0] + plan.bubbleX[1]) / 2f
        assertEquals(400f, mid, 1e-2f)
        assertEquals(
            geom.crossRowPitch + geom.leaderClearance,
            plan.bubbleX[1] - plan.bubbleX[0],
            1e-2f,
        )
    }

    @Test
    fun `ample slack spreads a bubble clear of a foreign leader's leg`() {
        // Mirrors the reported field defect: two liners' worth of stations mid-shaft, packed
        // just tight enough to alternate rows, but with a wide-open page around them. With
        // room to spare, every cross-row adjacent pair should land leaderClearance beyond the
        // bare crossRowPitch minimum — enough room for a hand-written reading beside the bubble
        // without the pen crossing the neighbour's leader.
        val stations = listOf(
            RunoutStationX("linerA", 400f, 400f),
            RunoutStationX("linerA", 410f, 410f),
            RunoutStationX("linerB", 420f, 420f),
            RunoutStationX("linerB", 430f, 430f),
        )
        val plan = planRunoutBubbles(stations, geom)
        for (i in 0 until plan.bubbleX.size - 1) {
            if (plan.rows[i + 1] != plan.rows[i]) {
                val dx = plan.bubbleX[i + 1] - plan.bubbleX[i]
                assertTrue(
                    "cross-row gap $i..${i + 1} = $dx wants >= ${geom.crossRowPitch + geom.leaderClearance}",
                    dx >= geom.crossRowPitch + geom.leaderClearance - 1e-2f,
                )
            }
        }
        assertInvariants(place(stations))
    }

    @Test
    fun `tight page barely spreads and still resolves with zero unresolved collisions`() {
        // A narrow content window (160pt available) with 6 alternating-row stations needing
        // 5 * crossRowPitch(25) = 125pt minimum — only 35pt of slack, not the 5 * 8 = 40pt
        // the full leaderClearance spread would want. The widening must degrade to exactly
        // what fits (7pt/gap, not 8), never compress, never collide — the same
        // no-new-collisions guarantee the layout had before rule 7 existed.
        val tight = geom.copy(contentLeft = 0f, contentRight = 200f)
        val stations = List(6) { i -> RunoutStationX("c0", i * 20f, i * 20f + 10f) }
        val plan = planRunoutBubbles(stations, tight)
        assertTrue("should not need to compress", !plan.compressed)
        val expectedGap = tight.crossRowPitch + 7f // 35pt slack / 5 gaps = 7pt each, capped below leaderClearance(8)
        for (i in 1 until plan.bubbleX.size) {
            assertEquals(expectedGap, plan.bubbleX[i] - plan.bubbleX[i - 1], 1e-2f)
        }
        val result = plan.finish(300f) { 300f }
        assertEquals(0, result.unresolvedCollisions)
        assertInvariants(result, tight)
    }

    @Test
    fun `bubble order always matches station order`() {
        val rnd = Random(7)
        repeat(20) {
            val stations = buildList {
                var x = 50f
                var comp = 0
                while (x < 700f) {
                    val id = "c${comp++}"
                    val n = rnd.nextInt(1, 5)
                    repeat(n) { k -> add(RunoutStationX(id, x + k, x + k * rnd.nextFloat() * 30f)) }
                    x += rnd.nextFloat() * 120f + 10f
                }
            }
            val plan = planRunoutBubbles(stations, geom)
            for (i in 1 until plan.bubbleX.size) {
                assertTrue("bubbleX not monotonic", plan.bubbleX[i] >= plan.bubbleX[i - 1] - 1e-3f)
            }
        }
    }

    // ── Full-placement invariants ────────────────────────────────────────────

    @Test
    fun `typical shaft layout holds all invariants`() {
        // taper(2) + body(3) + liner(2) + body(3) + taper(2), spread over the page
        val stations = listOf(
            RunoutStationX("taperA", 25f, 60f),
            RunoutStationX("taperA", 175f, 105f),
            RunoutStationX("body1", 300f, 180f),
            RunoutStationX("body1", 500f, 260f),
            RunoutStationX("body1", 700f, 340f),
            RunoutStationX("liner1", 800f, 380f),
            RunoutStationX("liner1", 900f, 420f),
            RunoutStationX("body2", 1000f, 470f),
            RunoutStationX("body2", 1200f, 550f),
            RunoutStationX("body2", 1400f, 630f),
            RunoutStationX("taperF", 1500f, 680f),
            RunoutStationX("taperF", 1650f, 730f),
        )
        assertInvariants(place(stations))
    }

    @Test
    fun `dense boundary between components holds all invariants`() {
        // Components meeting at a shared boundary with stations only ~15pt apart on page —
        // the configuration that broke the old greedy algorithm (leader through bubble).
        val stations = listOf(
            RunoutStationX("body1", 100f, 300f),
            RunoutStationX("body1", 120f, 315f),
            RunoutStationX("liner1", 140f, 330f),
            RunoutStationX("liner1", 160f, 345f),
            RunoutStationX("body2", 180f, 360f),
            RunoutStationX("body2", 200f, 375f),
        )
        assertInvariants(place(stations))
    }

    @Test
    fun `stepped shaft surface holds all invariants`() {
        // Large coupler (deep surface) next to thin shaft (shallow surface): surface y
        // varies per station, the case where straight leaders can slice through circles.
        val stations = listOf(
            RunoutStationX("coupler", 50f, 80f),
            RunoutStationX("coupler", 100f, 110f),
            RunoutStationX("shaft", 150f, 140f),
            RunoutStationX("shaft", 400f, 300f),
            RunoutStationX("shaft", 650f, 460f),
        )
        val result = place(stations, anchorY = 340f) { mm -> if (mm <= 100f) 340f else 290f }
        assertInvariants(result)
    }

    @Test
    fun `high station count on short component holds all invariants`() {
        // User cranks one component to 8 stations over a 120pt page span.
        val stations = List(8) { i -> RunoutStationX("body1", 100f + i * 10f, 300f + i * 15f) }
        assertInvariants(place(stations))
    }

    @Test
    fun `randomized stress configurations hold all invariants`() {
        val rnd = Random(42)
        var feasibleTrials = 0
        repeat(60) { trial ->
            val stations = buildList {
                var pageX = 60f
                var comp = 0
                while (pageX < 680f && comp < 8) {
                    val id = "c${comp}"
                    val n = rnd.nextInt(1, 6)
                    val spanW = rnd.nextFloat() * 120f + 30f
                    repeat(n) { k ->
                        val x = pageX + spanW * (k + 0.5f) / n
                        add(RunoutStationX(id, x, x))
                    }
                    pageX += spanW + rnd.nextFloat() * 40f
                    comp++
                }
            }
            val plan = planRunoutBubbles(stations, geom)
            // Randomly stepped shaft surface, 260..340
            val steps = List(4) { 260f + rnd.nextFloat() * 80f }
            val result = plan.finish(340f) { mm -> steps[(mm / 200f).toInt().coerceIn(0, 3)] }

            // Order must survive even physically impossible densities…
            for (i in 1 until plan.bubbleX.size) {
                assertTrue("trial $trial: bubbleX not monotonic", plan.bubbleX[i] >= plan.bubbleX[i - 1] - 1e-3f)
            }
            // …and every feasible (uncompressed) config must be fully collision-free.
            if (!plan.compressed) {
                feasibleTrials++
                try {
                    assertInvariants(result)
                } catch (e: AssertionError) {
                    throw AssertionError("trial $trial: ${e.message}", e)
                }
            }
        }
        assertTrue("stress test lost its teeth: only $feasibleTrials feasible trials", feasibleTrials >= 40)
    }

    @Test
    fun `degenerate overload compresses but stays monotonic and does not crash`() {
        // 40 stations crammed into a 200pt-wide content area — impossible to honour
        // clearances; engine must flag compression, not crash or scramble order.
        val tight = geom.copy(contentLeft = 0f, contentRight = 200f)
        val stations = List(40) { i -> RunoutStationX("c${i / 5}", i * 10f, 5f + i * 4.8f) }
        val plan = planRunoutBubbles(stations, tight)
        assertTrue("expected compressed plan", plan.compressed)
        for (i in 1 until plan.bubbleX.size) {
            assertTrue(plan.bubbleX[i] >= plan.bubbleX[i - 1] - 1e-3f)
        }
        plan.finish(300f) { 300f }  // must not crash
    }

    // ── Section height ───────────────────────────────────────────────────────

    @Test
    fun `section height covers all rows plus tail gap`() {
        val stations = listOf(
            RunoutStationX("b", 100f, 200f),
            RunoutStationX("b", 200f, 300f),
        )
        val plan = planRunoutBubbles(stations, geom)
        assertEquals(2, plan.rowCount)
        // shortLeader + 2r + rowStep + tail = 18 + 40 + 45 + 8
        assertEquals(111f, plan.sectionHeight(8f), 1e-3f)
    }

    @Test
    fun `bubble rows are globally aligned per row`() {
        val stations = listOf(
            RunoutStationX("a", 100f, 100f),
            RunoutStationX("a", 300f, 250f),
            RunoutStationX("b", 600f, 450f),
            RunoutStationX("b", 800f, 600f),
        )
        // Varying surface depth must not change row alignment
        val result = place(stations, anchorY = 320f) { mm -> if (mm < 400f) 320f else 280f }
        val rows = result.bubbles.groupBy { it.row }
        for ((_, members) in rows) {
            val ys = members.map { it.bubbleCenterY }.distinct()
            assertEquals("row not aligned: $ys", 1, ys.size)
        }
    }
}
