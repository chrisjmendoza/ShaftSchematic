package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

        // 2. No leader enters a foreign bubble (at exact radius, no clearance slack) — and a
        //    STRAIGHT leader (2 vertices) additionally keeps the wider visual clearance that
        //    makes its landing readable (rule 5); one that can't is rerouted as a dogleg.
        val straightClearance =
            maxOf(0.5f * geom.minGap, STRAIGHT_LEADER_CLEARANCE_RADIUS_FRAC * geom.radius)
        for (i in bubbles.indices) {
            val leader = bubbles[i].leader
            val clearance = if (leader.size == 2) straightClearance else 0f
            for (j in bubbles.indices) {
                if (j == i) continue
                for (s in 0 until leader.size - 1) {
                    assertTrue(
                        "leader of bubble $i enters bubble $j",
                        !segmentIntersectsCircle(
                            leader[s], leader[s + 1],
                            bubbles[j].bubbleX, bubbles[j].bubbleCenterY,
                            geom.radius + clearance - 1e-3f,
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
        // Two stations 10pt apart: must spread past crossRowPitch (25) but stay centred at
        // 400. Content span here (36..756) has ample slack, so the even-spread waterfill
        // (rule 7) takes the gap all the way to the spreadPitch cap.
        val stations = listOf(
            RunoutStationX("t1", 100f, 395f),
            RunoutStationX("t1", 110f, 405f),
        )
        val plan = planRunoutBubbles(stations, geom)
        val mid = (plan.bubbleX[0] + plan.bubbleX[1]) / 2f
        assertEquals(400f, mid, 1e-2f)
        assertEquals(geom.spreadPitch, plan.bubbleX[1] - plan.bubbleX[0], 1e-2f)
    }

    @Test
    fun `ample slack spreads bubbles apart but never past the fidelity bound`() {
        // Two liners' worth of stations bunched mid-shaft on a wide-open page. The spread
        // pulls the bubbles well past the bare crossRowPitch minimum (room to hand-write a
        // reading beside the circle) — but stops where a bubble would stray more than
        // spreadMaxOffset from its own station, so every pointer stays traceably its
        // station's (rule 7's brake; unbraked, this cluster would fan to the spreadPitch
        // cap and the outer pointers would lean twice as far).
        val stations = listOf(
            RunoutStationX("linerA", 400f, 400f),
            RunoutStationX("linerA", 410f, 410f),
            RunoutStationX("linerB", 420f, 420f),
            RunoutStationX("linerB", 430f, 430f),
        )
        val plan = planRunoutBubbles(stations, geom)
        for (i in 0 until plan.bubbleX.size - 1) {
            val dx = plan.bubbleX[i + 1] - plan.bubbleX[i]
            assertTrue(
                "gap $i..${i + 1} = $dx wants well above crossRowPitch ${geom.crossRowPitch}",
                dx >= geom.crossRowPitch + 10f,
            )
        }
        plan.stations.forEachIndexed { i, st ->
            val off = abs(plan.bubbleX[i] - st.stationX)
            assertTrue(
                "bubble $i strayed $off past the fidelity bound ${geom.spreadMaxOffset}",
                off <= geom.spreadMaxOffset + 1e-2f,
            )
        }
        assertInvariants(place(stations))
    }

    @Test
    fun `tight page spreads to one level inside the fidelity bound, zero collisions`() {
        // A narrow content window (160pt available) with 6 alternating-row stations needing
        // 5 * crossRowPitch(25) = 125pt minimum. The waterfill raises every gap toward the
        // width-consuming level (32pt) but the fidelity brake stops it where the end
        // bubbles would stray past spreadMaxOffset (rule 7) — one common level, never
        // compressed, never colliding, every pointer still traceably its station's.
        val tight = geom.copy(contentLeft = 0f, contentRight = 200f)
        val stations = List(6) { i -> RunoutStationX("c0", i * 20f, i * 20f + 10f) }
        val plan = planRunoutBubbles(stations, tight)
        assertTrue("should not need to compress", !plan.compressed)
        val gaps = (1 until plan.bubbleX.size).map { plan.bubbleX[it] - plan.bubbleX[it - 1] }
        for (g in gaps) assertEquals("one common level", gaps[0], g, 1e-1f)
        assertTrue("level ${gaps[0]} should exceed the crossRowPitch floor", gaps[0] > tight.crossRowPitch + 1f)
        plan.stations.forEachIndexed { i, st ->
            assertTrue(
                "bubble $i strayed past the fidelity bound",
                abs(plan.bubbleX[i] - st.stationX) <= tight.spreadMaxOffset + 1e-2f,
            )
        }
        val result = plan.finish(300f) { 300f }
        assertEquals(0, result.unresolvedCollisions)
        assertInvariants(result, tight)
    }

    @Test
    fun `bunch already past the fidelity bound takes no widening at all`() {
        // 12 stations bunched into 132pt of page: even at the bare minimum pitches the
        // cluster's ends already sit further off-station than spreadMaxOffset, so the
        // waterfill adds nothing — widening could only make the pointers flatter (rule 7's
        // brake; the unbraked fill spread this bunch across the whole page and the outer
        // pointers went near-horizontal, on-device report).
        val stations = List(12) { i -> RunoutStationX("c${i / 3}", i * 10f, 300f + i * 12f) }
        val plan = planRunoutBubbles(stations, geom)
        val gaps = (1 until plan.bubbleX.size).map { plan.bubbleX[it] - plan.bubbleX[it - 1] }
        // Every adjacent pair alternates rows here, so the geometric floor is crossRowPitch —
        // and every gap must sit exactly on it.
        for ((i, g) in gaps.withIndex()) {
            assertEquals("gap $i widened past the geometric floor", geom.crossRowPitch, g, 1e-1f)
        }
        assertInvariants(place(stations))
    }

    @Test
    fun `straight leaders aim at their circle's center`() {
        // Well-separated stations with a deliberate offset between station and bubble: the
        // straight leader must stop on the rim, collinear with the circle center (rule 5) —
        // the arrival direction is what tells the reader which circle the pointer lands in.
        val stations = listOf(
            RunoutStationX("a", 100f, 150f),
            RunoutStationX("a", 300f, 400f),
            RunoutStationX("b", 500f, 650f),
        )
        val result = place(stations)
        for (b in result.bubbles) {
            if (b.leader.size != 2) continue
            val end = b.leader.last()
            val start = b.leader.first()
            val toCenter = hypot((b.bubbleX - end.x).toDouble(), (b.bubbleCenterY - end.y).toDouble())
            assertEquals("leader end not on the rim", geom.radius.toDouble(), toCenter, 1e-2)
            // Collinear: cross product of (start→end) × (start→center) ≈ 0.
            val cross = (end.x - start.x) * (b.bubbleCenterY - start.y) -
                (end.y - start.y) * (b.bubbleX - start.x)
            assertEquals("leader does not aim at the center", 0f, cross, 1e-1f)
        }
    }

    @Test
    fun `body stations place evenly across the DRAWN span under a compressed mapping`() {
        // Piecewise map compressing 0..1000mm into 100pt and stretching 1000..2000mm into
        // 500pt. Physical midpoints would bunch the first body's stations into the squeezed
        // 100pt; drawn-space placement spreads them evenly across it and inverts to mm.
        fun xAt(mm: Float) = if (mm <= 1000f) mm * 0.1f else 100f + (mm - 1000f) * 0.5f
        fun mmAt(x: Float) = if (x <= 100f) x / 0.1f else 1000f + (x - 100f) / 0.5f
        val spans = listOf(RunoutComponentSpan("b1", RunoutComponentKind.BODY, 0f, 1000f))
        val stations = collectRunoutStations(spans, mapOf("b1" to 4), ::xAt, ::mmAt)
        assertEquals(4, stations.size)
        // Evenly spaced in DRAWN x: 12.5, 37.5, 62.5, 87.5 across the 100pt span.
        stations.forEachIndexed { i, st ->
            assertEquals(12.5f + i * 25f, st.stationX, 1e-3f)
            // And the mm is the true inverse of that drawn position.
            assertEquals(mmAt(st.stationX), st.stationMm, 1e-3f)
        }
        // Without an inverse, bodies fall back to physical cell midpoints.
        val fallback = collectRunoutStations(spans, mapOf("b1" to 4), ::xAt)
        assertEquals(125f, fallback[0].stationMm, 1e-3f)
    }

    @Test
    fun `taper and liner stations keep their physical edge-inset placement`() {
        // The drawn-space rule is bodies-only: liners/tapers measure near their physical
        // edges (best runout spots — worn areas rarely reach the very edge).
        fun xAt(mm: Float) = mm * 0.3f
        fun mmAt(x: Float) = x / 0.3f
        val spans = listOf(RunoutComponentSpan("ln", RunoutComponentKind.LINER, 100f, 500f))
        val stations = collectRunoutStations(spans, emptyMap(), ::xAt, ::mmAt)
        assertEquals(125.4f, stations[0].stationMm, 1e-3f)   // 100 + 1in inset
        assertEquals(574.6f, stations[1].stationMm, 1e-3f)   // 600 − 1in inset
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
        // a tight configuration where a leader could otherwise cross through a bubble.
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
