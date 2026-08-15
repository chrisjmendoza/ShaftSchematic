package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.WearRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for the wear sheet's strip WINDOWS — the grouping rule that puts a taper in
 * its nearest liner's strip, the true-vs-compressed gap threshold, the window's single piecewise
 * mm→pt mapping, and the shared page scale once fixed-width break gaps are in play.
 *
 * The equivalence tests at the end are the load-bearing ones: a liner-only sheet — the default
 * election, and every document authored before strips became elective — must group and lay out
 * exactly as the legacy per-liner path did.
 */
class WearStripWindowTest {

    private fun liner(id: String, startMm: Float, lengthMm: Float, odMm: Float = 80f) =
        WearStripComponent(id, WearStripComponentKind.LINER, startMm, startMm + lengthMm, odMm, odMm)

    private fun taper(id: String, startMm: Float, lengthMm: Float, d0: Float = 70f, d1: Float = 60f) =
        WearStripComponent(id, WearStripComponentKind.TAPER, startMm, startMm + lengthMm, d0, d1)

    private fun body(id: String, startMm: Float, lengthMm: Float, diaMm: Float = 75f, auto: Boolean = false) =
        WearStripComponent(id, WearStripComponentKind.BODY, startMm, startMm + lengthMm, diaMm, diaMm, auto)

    private fun idsOf(w: WearStripWindow) = w.components.map { it.id }

    // ── Grouping ──────────────────────────────────────────────────────────────

    @Test
    fun `the default election makes one single-component window per liner`() {
        val comps = listOf(body("b", 0f, 500f), liner("l1", 100f, 200f), taper("t", 600f, 100f), liner("l2", 900f, 150f))
        val windows = collectWearStripWindows(comps, stripComponentIds = null)
        assertEquals(listOf(listOf("l1"), listOf("l2")), windows.map { idsOf(it) })
        assertTrue(windows.all { it.segments.size == 1 })
    }

    @Test
    fun `an elected taper joins its nearest elected liner`() {
        // The taper sits 20 mm from l2 and 380 mm from l1 — it belongs to l2's strip.
        val l1 = liner("l1", 0f, 200f)
        val t = taper("t", 580f, 100f)
        val l2 = liner("l2", 700f, 200f)
        val windows = collectWearStripWindows(listOf(l1, t, l2), listOf("l1", "t", "l2"))
        assertEquals(listOf(listOf("l1"), listOf("t", "l2")), windows.map { idsOf(it) })
    }

    @Test
    fun `an equidistant taper goes to the AFT liner`() {
        val aft = liner("aft", 50f, 40f)     // ends at 90, gap 10 to the taper
        val t = taper("t", 100f, 50f)        // ends at 150, gap 10 to the fwd liner
        val fwd = liner("fwd", 160f, 40f)
        val windows = collectWearStripWindows(listOf(aft, t, fwd), listOf("aft", "t", "fwd"))
        assertEquals(listOf(listOf("aft", "t"), listOf("fwd")), windows.map { idsOf(it) })
    }

    @Test
    fun `a liner can take one taper from each side`() {
        val tA = taper("tA", 0f, 100f)
        val ln = liner("l", 120f, 300f)
        val tF = taper("tF", 440f, 100f)
        val windows = collectWearStripWindows(listOf(tA, ln, tF), listOf("tA", "l", "tF"))
        assertEquals(1, windows.size)
        assertEquals(listOf("tA", "l", "tF"), idsOf(windows[0]))
    }

    @Test
    fun `two tapers on the same side of one liner - only the nearest joins it`() {
        val far = taper("far", 0f, 50f)      // ends at 50, gap 250
        val near = taper("near", 250f, 40f)  // ends at 290, gap 10
        val ln = liner("l", 300f, 200f)
        val windows = collectWearStripWindows(listOf(far, near, ln), listOf("far", "near", "l"))
        assertEquals(listOf(listOf("far"), listOf("near", "l")), windows.map { idsOf(it) })
    }

    @Test
    fun `an elected taper with no elected liner gets its own strip`() {
        val comps = listOf(taper("t", 100f, 80f), liner("l", 400f, 200f))
        val windows = collectWearStripWindows(comps, listOf("t"))
        assertEquals(listOf(listOf("t")), windows.map { idsOf(it) })
        assertNull(windows[0].liner)
    }

    @Test
    fun `bodies always get their own window, never a liner's`() {
        val b = body("b", 0f, 100f)
        val ln = liner("l", 100f, 200f)      // touching — still separate strips
        val windows = collectWearStripWindows(listOf(b, ln), listOf("b", "l"))
        assertEquals(listOf(listOf("b"), listOf("l")), windows.map { idsOf(it) })
    }

    @Test
    fun `windows come out aft to fwd whatever order the election was authored in`() {
        val comps = listOf(liner("fwd", 900f, 100f), body("mid", 400f, 100f), liner("aft", 0f, 100f))
        val windows = collectWearStripWindows(comps, listOf("fwd", "aft", "mid"))
        assertEquals(listOf("aft", "mid", "fwd"), windows.map { idsOf(it).single() })
    }

    @Test
    fun `degenerate and unresolved elections are skipped, never fatal`() {
        val comps = listOf(
            liner("ok", 0f, 200f),
            liner("no-len", 300f, 0f),
            liner("no-od", 400f, 100f, odMm = 0f),
        )
        val windows = collectWearStripWindows(comps, listOf("ok", "no-len", "no-od", "ghost"))
        assertEquals(listOf(listOf("ok")), windows.map { idsOf(it) })
        assertTrue(collectWearStripWindows(comps, emptyList()).isEmpty())
    }

    // ── Gap mode — decided from mm alone, so the scale solve stays non-circular ─

    @Test
    fun `a gap at or under the threshold draws true, past it compresses`() {
        assertTrue(wearStripGapDrawsTrue(0f))
        assertTrue(wearStripGapDrawsTrue(WEAR_STRIP_TRUE_GAP_MAX_MM))
        assertFalse(wearStripGapDrawsTrue(WEAR_STRIP_TRUE_GAP_MAX_MM + 0.1f))
    }

    @Test
    fun `a close taper draws contiguously and a distant one gets a break connector`() {
        val close = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 150f, 200f)), listOf("t", "l"),
        ).single()
        val closeGap = close.segments.filterIsInstance<WearStripGapSeg>().single()
        assertTrue(closeGap.trueScale)

        val far = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 400f, 200f)), listOf("t", "l"),
        ).single()
        val farGap = far.segments.filterIsInstance<WearStripGapSeg>().single()
        assertFalse(farGap.trueScale)
    }

    @Test
    fun `touching components produce no gap segment at all`() {
        val w = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 100f, 200f)), listOf("t", "l"),
        ).single()
        assertTrue(w.segments.none { it is WearStripGapSeg })
        assertEquals(2, w.segments.size)
    }

    // ── The window's one piecewise mm→pt mapping ──────────────────────────────

    private fun brokenWindow() = collectWearStripWindows(
        listOf(taper("t", 0f, 100f), liner("l", 400f, 200f)), listOf("t", "l"),
    ).single()

    @Test
    fun `drawn width is spans plus true gaps at scale plus each break gap's fixed run`() {
        val w = brokenWindow()
        val s = 0.5f
        // 100 mm taper + a compressed 300 mm gap + 200 mm liner.
        assertEquals(300f * s + WEAR_STRIP_BREAK_GAP_PT, w.drawnWidthPt(s), 1e-3f)

        val trueGap = collectWearStripWindows(
            listOf(taper("t", 0f, 100f), liner("l", 150f, 200f)), listOf("t", "l"),
        ).single()
        assertEquals(350f * s, trueGap.drawnWidthPt(s), 1e-3f)
    }

    @Test
    fun `xAt is exact at every segment boundary`() {
        val w = brokenWindow()
        val left = 100f; val s = 0.5f
        assertEquals(left, w.xAt(0f, left, s), 1e-3f)                       // window AFT edge
        assertEquals(left + 50f, w.xAt(100f, left, s), 1e-3f)               // taper FWD edge
        assertEquals(left + 50f + WEAR_STRIP_BREAK_GAP_PT, w.xAt(400f, left, s), 1e-3f)  // liner AFT edge
        assertEquals(left + w.drawnWidthPt(s), w.xAt(600f, left, s), 1e-3f) // window FWD edge
    }

    @Test
    fun `xAt is monotone across the whole window, breaks included`() {
        val w = brokenWindow()
        var prev = Float.NEGATIVE_INFINITY
        var mm = -50f
        while (mm <= 650f) {
            val x = w.xAt(mm, 100f, 0.5f)
            assertTrue("xAt must never run backwards (at $mm mm)", x >= prev - 1e-4f)
            prev = x
            mm += 5f
        }
    }

    @Test
    fun `positions outside the window extrapolate at the sheet scale - the stubs' own space`() {
        val w = brokenWindow()
        assertEquals(100f - 10f, w.xAt(-20f, 100f, 0.5f), 1e-3f)
        assertEquals(100f + w.drawnWidthPt(0.5f) + 10f, w.xAt(620f, 100f, 0.5f), 1e-3f)
    }

    // ── Shared page scale, with fixed break runs taken off first ───────────────

    private fun innerWidth(left: Float, right: Float) = right - left - 2f * WEAR_STRIP_STUB_WIDTH_PT

    @Test
    fun `a window's break gaps come off its cell before the scale is solved`() {
        val left = 36f; val right = 396f
        val inner = innerWidth(left, right)
        val plain = collectWearStripWindows(listOf(liner("a", 0f, 400f)), listOf("a")).single()
        val broken = brokenWindow()
        val shared = sharedWearStripWindowPtPerMm(listOf(plain, broken), listOf(inner, inner))
        // The plain 400 mm window is the binding constraint here (400 mm in `inner` points).
        assertEquals(inner / 400f, shared, 1e-4f)
        listOf(plain, broken).forEach { w ->
            assertTrue("every window must still fit its cell", w.drawnWidthPt(shared) <= inner + 1e-3f)
        }
    }

    @Test
    fun `a window whose breaks alone fill its cell still solves to a positive scale`() {
        val broken = brokenWindow()
        val shared = sharedWearStripWindowPtPerMm(listOf(broken), listOf(WEAR_STRIP_BREAK_GAP_PT))
        assertTrue(shared > 0f)
    }

    @Test
    fun `an empty page yields the cap`() {
        assertEquals(WEAR_STRIP_MAX_PT_PER_MM, sharedWearStripWindowPtPerMm(emptyList(), emptyList()), 1e-6f)
    }

    // ── Liner-only equivalence with the legacy per-liner path ─────────────────

    @Test
    fun `a liner-only election groups exactly as collectWearLinerGroups did`() {
        val liners = listOf(
            Liner(id = "fwd", startFromAftMm = 700f, lengthMm = 100f, odMm = 80f),
            Liner(id = "aft", startFromAftMm = 50f, lengthMm = 300f, odMm = 90f),
            Liner(id = "dead", startFromAftMm = 900f, lengthMm = 0f, odMm = 80f),
        )
        val comps = liners.map { liner(it.id, it.startFromAftMm, it.lengthMm, it.odMm) }

        listOf<List<String>?>(null, listOf("aft", "fwd"), listOf("aft"), emptyList()).forEach { election ->
            val legacy = collectWearLinerGroups(liners, WearRecord(), election).map { it.liner.id }
            val windows = collectWearStripWindows(comps, election).map { idsOf(it).single() }
            assertEquals("election $election", legacy, windows)
        }
    }

    @Test
    fun `a single-component window lays out exactly where the legacy strip did`() {
        val left = 36f; val right = 756f
        listOf(1f, 150f, 400f, 4000f).forEach { len ->
            val w = collectWearStripWindows(listOf(liner("a", 0f, len)), listOf("a")).single()
            val shared = sharedWearStripPtPerMm(listOf(len), listOf(innerWidth(left, right)))
            val legacy = computeWearStripHorizontalLayout(left, right, len, ptPerMmOverride = shared)
            val now = computeWearStripWindowLayout(left, right, w.drawnWidthPt(shared), shared)
            assertEquals(legacy.linerLeftPt, now.linerLeftPt, 1e-3f)
            assertEquals(legacy.linerRightPt, now.linerRightPt, 1e-3f)
            assertEquals(legacy.stubWidthPt, now.stubWidthPt, 0f)
            assertEquals(legacy.ptPerMm, now.ptPerMm, 0f)
            // …and the window's mapping is the legacy strip's linear one.
            assertEquals(legacy.linerLeftPt, w.xAt(0f, now.linerLeftPt, shared), 1e-3f)
            assertEquals(legacy.linerRightPt, w.xAt(len, now.linerLeftPt, shared), 1e-3f)
        }
    }

    @Test
    fun `a liner-only page solves to the same shared scale as the legacy lengths did`() {
        val inner = innerWidth(36f, 396f)
        val lengths = listOf(120f, 900f, 55f)
        val windows = lengths.mapIndexed { i, len ->
            collectWearStripWindows(listOf(liner("l$i", 0f, len)), listOf("l$i")).single()
        }
        assertEquals(
            sharedWearStripPtPerMm(lengths, List(lengths.size) { inner }),
            sharedWearStripWindowPtPerMm(windows, List(windows.size) { inner }),
            1e-6f,
        )
    }
}
