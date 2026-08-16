package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for the two wear-strip window rules that decide how a strip READS:
 *
 * - [wearStripEndStyle] / [wearStripEndThreadDiaMm] — an end stub's S-break claims "the shaft
 *   continues past here", so it may only be drawn where it does; a threaded shaft end shows the
 *   whole remainder (flat + hatched) and an end with nothing beyond it gets no stub at all.
 * - [collectWearStripWindows] — the join threshold decides window MEMBERSHIP: a taper farther than
 *   it from its nearest liner gets its own strip, so a window the builder produces never carries a
 *   compressed gap.
 * - [wearStripClusters] / [wearStripClusterShowsAnchor] — a compressed gap means the components
 *   either side are NOT adjacent, so each side titles itself, and a cluster holding a taper needs
 *   no from-SET dimension. The split rule is pinned on directly-constructed windows, since it is
 *   the general model's behavior rather than something the current builder emits.
 */
class WearStripEndsAndClustersTest {

    // ── Window end style ──────────────────────────────────────────────────────

    /** Body 0…500 (Ø100) with a threaded end 500…560 (Ø60) — the classic aft-thread shaft. */
    private fun threadedEndSpec() = ShaftSpec(
        overallLengthMm = 560f,
        bodies = listOf(Body(id = "b", startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)),
        threads = listOf(Threads(id = "th", startFromAftMm = 500f, lengthMm = 60f, majorDiaMm = 60f)),
    )

    @Test
    fun `nothing beyond the edge means the shaft ends there`() {
        val spec = threadedEndSpec()
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 0f, aftSide = true))
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 560f, aftSide = false))
    }

    @Test
    fun `an end whose remainder is only thread draws as a thread end`() {
        val spec = threadedEndSpec()
        assertEquals(WearStripEndStyle.THREAD_END, wearStripEndStyle(spec, edgeMm = 500f, aftSide = false))
    }

    @Test
    fun `a thread with real shaft beyond it still breaks`() {
        // At 400 mm the thread AND the body both continue forward, so the stub genuinely
        // truncates the shaft.
        val spec = threadedEndSpec()
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(spec, edgeMm = 400f, aftSide = false))
    }

    @Test
    fun `a body beyond the edge breaks, on either side`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            bodies = listOf(Body(id = "b", startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)),
        )
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(spec, edgeMm = 400f, aftSide = false))
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(spec, edgeMm = 400f, aftSide = true))
    }

    @Test
    fun `a component ending exactly at the edge is not beyond it`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            bodies = listOf(Body(id = "b", startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)),
        )
        // Within the eps tolerance either way — the body's FWD face IS the window edge.
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 500f, aftSide = false))
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 500.4f, aftSide = false))
        // …and its AFT face likewise, looking the other way.
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 0f, aftSide = true))
    }

    @Test
    fun `an aft threaded end reads the same rule looking aft`() {
        val spec = ShaftSpec(
            overallLengthMm = 260f,
            threads = listOf(Threads(id = "th", startFromAftMm = 0f, lengthMm = 60f, majorDiaMm = 60f)),
            liners = listOf(Liner(id = "l", startFromAftMm = 60f, lengthMm = 200f, odMm = 90f)),
        )
        assertEquals(WearStripEndStyle.THREAD_END, wearStripEndStyle(spec, edgeMm = 60f, aftSide = true))
        assertEquals(WearStripEndStyle.FLAT, wearStripEndStyle(spec, edgeMm = 260f, aftSide = false))
    }

    @Test
    fun `a taper or liner beyond the edge breaks too`() {
        val spec = ShaftSpec(
            overallLengthMm = 600f,
            tapers = listOf(Taper(id = "t", startFromAftMm = 400f, lengthMm = 200f, startDiaMm = 90f, endDiaMm = 70f)),
            liners = listOf(Liner(id = "l", startFromAftMm = 0f, lengthMm = 200f, odMm = 100f)),
        )
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(spec, edgeMm = 300f, aftSide = false))
        assertEquals(WearStripEndStyle.BREAK, wearStripEndStyle(spec, edgeMm = 300f, aftSide = true))
    }

    @Test
    fun `the thread end stub takes the thread's own major diameter`() {
        val spec = threadedEndSpec()
        assertEquals(60f, wearStripEndThreadDiaMm(spec, edgeMm = 500f, aftSide = false), 1e-4f)
        // Nothing beyond the shaft's FWD face, so there is no thread to size a stub from.
        assertEquals(0f, wearStripEndThreadDiaMm(spec, edgeMm = 560f, aftSide = false), 1e-4f)
        assertEquals(0f, wearStripEndThreadDiaMm(spec, edgeMm = 0f, aftSide = true), 1e-4f)
    }

    @Test
    fun `two threads beyond the edge report the largest`() {
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            threads = listOf(
                Threads(id = "t1", startFromAftMm = 200f, lengthMm = 60f, majorDiaMm = 50f),
                Threads(id = "t2", startFromAftMm = 300f, lengthMm = 60f, majorDiaMm = 70f),
            ),
        )
        assertEquals(70f, wearStripEndThreadDiaMm(spec, edgeMm = 100f, aftSide = false), 1e-4f)
    }

    // ── Clusters ──────────────────────────────────────────────────────────────

    private fun liner(id: String, startMm: Float, lengthMm: Float, odMm: Float = 80f) =
        WearStripComponent(id, WearStripComponentKind.LINER, startMm, startMm + lengthMm, odMm, odMm)

    private fun taper(id: String, startMm: Float, lengthMm: Float, d0: Float = 70f, d1: Float = 60f) =
        WearStripComponent(id, WearStripComponentKind.TAPER, startMm, startMm + lengthMm, d0, d1)

    private fun body(id: String, startMm: Float, lengthMm: Float, diaMm: Float = 75f) =
        WearStripComponent(id, WearStripComponentKind.BODY, startMm, startMm + lengthMm, diaMm, diaMm)

    private fun idsOf(c: WearStripCluster) = c.components.map { it.id }

    @Test
    fun `a single-component window is one cluster`() {
        val w = collectWearStripWindows(listOf(liner("l", 100f, 200f)), listOf("l")).single()
        val clusters = wearStripClusters(w)
        assertEquals(listOf(listOf("l")), clusters.map { idsOf(it) })
        assertEquals(100f, clusters.single().startMm, 1e-4f)
        assertEquals(300f, clusters.single().endMm, 1e-4f)
    }

    @Test
    fun `a taper and liner joined by a true gap stay in one cluster`() {
        val w = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 150f, 200f)), listOf("t", "l"),
        ).single()
        assertTrue(w.segments.filterIsInstance<WearStripGapSeg>().single().trueScale)
        val clusters = wearStripClusters(w)
        assertEquals(listOf(listOf("t", "l")), clusters.map { idsOf(it) })
        assertEquals(0f, clusters.single().startMm, 1e-4f)
        assertEquals(350f, clusters.single().endMm, 1e-4f)
    }

    @Test
    fun `touching components stay in one cluster`() {
        val w = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 100f, 200f)), listOf("t", "l"),
        ).single()
        assertEquals(listOf(listOf("t", "l")), wearStripClusters(w).map { idsOf(it) })
    }

    @Test
    fun `a taper past the join threshold gets its own window instead of sharing one`() {
        // 300 mm apart, far past the default threshold: the taper does not attach at all, so the
        // pair prints as two single-component strips (AFT→FWD) rather than one shared window
        // whose total width pushed the liner off-center.
        val windows = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 400f, 200f)), listOf("t", "l"),
        )
        assertEquals(
            listOf(listOf("t"), listOf("l")),
            windows.map { w -> w.components.map { it.id } },
        )
        assertTrue(
            "a separated window holds no gap segment at all",
            windows.all { w -> w.segments.none { it is WearStripGapSeg } },
        )
        assertEquals(0f, windows[0].startMm, 1e-4f)
        assertEquals(100f, windows[0].endMm, 1e-4f)
        assertEquals(400f, windows[1].startMm, 1e-4f)
        assertEquals(600f, windows[1].endMm, 1e-4f)
    }

    @Test
    fun `a compressed gap splits a window into two clusters, aft to fwd`() {
        // The current builder never emits a compressed gap (membership follows the same
        // threshold), so the split rule is pinned on a directly-constructed window — the model is
        // general, and any window carrying a break must still title each side separately.
        val w = WearStripWindow(
            listOf(
                WearStripComponentSeg(taper("t", 0f, 100f)),
                WearStripGapSeg(100f, 400f, trueScale = false),
                WearStripComponentSeg(liner("l", 400f, 200f)),
            ),
        )
        val clusters = wearStripClusters(w)
        assertEquals(listOf(listOf("t"), listOf("l")), clusters.map { idsOf(it) })
        assertEquals(0f, clusters[0].startMm, 1e-4f)
        assertEquals(100f, clusters[0].endMm, 1e-4f)
        assertEquals(400f, clusters[1].startMm, 1e-4f)
        assertEquals(600f, clusters[1].endMm, 1e-4f)
    }

    @Test
    fun `only the taper within the threshold joins the liner`() {
        // tA touches within the threshold and joins; tF sits 300 mm off the liner's FWD edge, so
        // it separates into its own window rather than riding a compressed gap.
        val windows = collectWearStripWindows(
            listOf(taper("tA", 0f, 100f), liner("l", 150f, 200f), taper("tF", 650f, 100f)),
            listOf("tA", "l", "tF"),
        )
        assertEquals(
            listOf(listOf("tA", "l"), listOf("tF")),
            windows.map { w -> w.components.map { it.id } },
        )
        assertTrue(windows[0].segments.filterIsInstance<WearStripGapSeg>().single().trueScale)
        assertTrue(windows[1].segments.none { it is WearStripGapSeg })
    }

    @Test
    fun `a window with one true and one compressed gap splits only at the compressed one`() {
        val w = WearStripWindow(
            listOf(
                WearStripComponentSeg(taper("tA", 0f, 100f)),
                WearStripGapSeg(100f, 150f, trueScale = true),
                WearStripComponentSeg(liner("l", 150f, 200f)),
                WearStripGapSeg(350f, 650f, trueScale = false),
                WearStripComponentSeg(taper("tF", 650f, 100f)),
            ),
        )
        assertEquals(listOf(listOf("tA", "l"), listOf("tF")), wearStripClusters(w).map { idsOf(it) })
    }

    // ── Where the threshold cuts ──────────────────────────────────────────────

    @Test
    fun `a gap exactly at the threshold still joins, one epsilon past it separates`() {
        val threshold = 100f
        val atThreshold = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 200f, 200f)),
            listOf("t", "l"),
            trueGapMaxMm = threshold,
        )
        assertEquals(listOf(listOf("t", "l")), atThreshold.map { w -> w.components.map { it.id } })
        assertTrue(atThreshold.single().segments.filterIsInstance<WearStripGapSeg>().single().trueScale)

        val past = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 200.01f, 200f)),
            listOf("t", "l"),
            trueGapMaxMm = threshold,
        )
        assertEquals(listOf(listOf("t"), listOf("l")), past.map { w -> w.components.map { it.id } })
    }

    @Test
    fun `at a zero threshold only a touching taper joins`() {
        val touching = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 100f, 200f)), listOf("t", "l"), trueGapMaxMm = 0f,
        )
        assertEquals(listOf(listOf("t", "l")), touching.map { w -> w.components.map { it.id } })

        val apart = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 101f, 200f)), listOf("t", "l"), trueGapMaxMm = 0f,
        )
        assertEquals(listOf(listOf("t"), listOf("l")), apart.map { w -> w.components.map { it.id } })
    }

    // ── Which clusters print an anchor dimension ──────────────────────────────

    @Test
    fun `a lone liner or lone body prints its anchor dimension`() {
        assertTrue(wearStripClusterShowsAnchor(WearStripCluster(listOf(liner("l", 0f, 100f)))))
        assertTrue(wearStripClusterShowsAnchor(WearStripCluster(listOf(body("b", 0f, 100f)))))
    }

    @Test
    fun `any cluster holding a taper prints none`() {
        assertFalse(wearStripClusterShowsAnchor(WearStripCluster(listOf(taper("t", 0f, 100f)))))
        assertFalse(
            wearStripClusterShowsAnchor(
                WearStripCluster(listOf(taper("t", 0f, 100f), liner("l", 100f, 200f))),
            ),
        )
        assertFalse(
            wearStripClusterShowsAnchor(
                WearStripCluster(listOf(body("b", 0f, 100f), taper("t", 100f, 100f))),
            ),
        )
    }

    // ── Compressed-gap lead-in ────────────────────────────────────────────────

    @Test
    fun `the break lead-ins always leave open space between the pair`() {
        assertTrue(
            "two lead-ins must not consume the compressed run",
            2f * WEAR_STRIP_BREAK_LEAD_PT < WEAR_STRIP_BREAK_GAP_PT,
        )
    }
}
