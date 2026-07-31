package com.android.shaftschematic.geom

import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `geom/UndercutMath.kt` — reference conversion, span validation, cluster
 * windows, and hit-testing for the undercut drawing feature.
 */
class UndercutMathTest {

    // ── Reference conversion ──

    @Test
    fun `AFT_SET entry is offset by the aft SET position`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.AFT_SET,
            enteredMm = 100f, lengthMm = 40f, aftSetXMm = 50f, fwdSetXMm = 950f,
        )
        assertEquals(150f, canonical, 1e-4f)
    }

    @Test
    fun `FWD_SET entry locates the FWD edge measured aft from the fwd SET`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.FWD_SET,
            enteredMm = 100f, lengthMm = 40f, aftSetXMm = 50f, fwdSetXMm = 950f,
        )
        // FWD edge at 950 - 100 = 850, AFT edge (canonical) = 850 - 40.
        assertEquals(810f, canonical, 1e-4f)
    }

    @Test
    fun `conversion pair round-trips exactly for both references`() {
        for (ref in UndercutReference.entries) {
            for (entered in listOf(0f, 12.7f, 101.6001f, 333.333f)) {
                val canonical = undercutStartToCanonicalMm(ref, entered, 55.5f, 42.42f, 1234.56f)
                val redisplayed = canonicalToUndercutStartMm(ref, canonical, 55.5f, 42.42f, 1234.56f)
                assertEquals("ref=$ref entered=$entered", entered, redisplayed, 1e-3f)
            }
        }
    }

    // ── Validation ──

    @Test
    fun `in-bounds span passes validation`() {
        assertNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `boundary-exact span is accepted`() {
        assertNull(undercutSpanIssue(canonicalStartMm = 0f, lengthMm = 1000f, oalMm = 1000f))
    }

    @Test
    fun `zero or negative length is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = 0f, oalMm = 1000f))
        assertNotNull(undercutSpanIssue(canonicalStartMm = 100f, lengthMm = -5f, oalMm = 1000f))
    }

    @Test
    fun `span before the AFT end is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = -10f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `span past the FWD end is rejected`() {
        assertNotNull(undercutSpanIssue(canonicalStartMm = 980f, lengthMm = 50f, oalMm = 1000f))
    }

    @Test
    fun `stale classifier mirrors the span issue check`() {
        assertFalse(isUndercutStaleOverrun(100f, 50f, 1000f))
        assertTrue(isUndercutStaleOverrun(980f, 50f, 1000f))
    }

    @Test
    fun `clamp trims a span into the shaft extent without mutating length semantics`() {
        val clamped = clampUndercutSpan(startFromAftMm = 980f, lengthMm = 50f, oalMm = 1000f)
        assertEquals(980f, clamped.startMm, 1e-4f)
        assertEquals(1000f, clamped.endMm, 1e-4f)
        assertFalse(clamped.isEmpty)
    }

    @Test
    fun `clamp of a fully outside span is empty`() {
        assertTrue(clampUndercutSpan(1100f, 50f, 1000f).isEmpty)
    }

    // ── Cluster windows ──

    @Test
    fun `single undercut gets one padded window`() {
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 500f, 540f)), oalMm = 1000f,
        )
        assertEquals(1, windows.size)
        assertEquals(500f - UNDERCUT_WINDOW_PAD_MM, windows[0].startMm, 1e-4f)
        assertEquals(540f + UNDERCUT_WINDOW_PAD_MM, windows[0].endMm, 1e-4f)
        assertEquals(listOf("a"), windows[0].undercutIds)
    }

    @Test
    fun `window padding clamps to the shaft extent`() {
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 5f, 990f)), oalMm = 1000f,
        )
        assertEquals(0f, windows[0].startMm, 1e-4f)
        assertEquals(1000f, windows[0].endMm, 1e-4f)
    }

    @Test
    fun `nearby undercuts share one window in aft-fwd order`() {
        val windows = clusterUndercuts(
            listOf(
                UndercutSpanMm("mid", 300f, 320f),
                UndercutSpanMm("aft", 200f, 240f),
                UndercutSpanMm("fwd", 320f + UNDERCUT_CLUSTER_GAP_MM, 500f + UNDERCUT_CLUSTER_GAP_MM),
            ),
            oalMm = 2000f,
        )
        assertEquals(1, windows.size)
        assertEquals(listOf("aft", "mid", "fwd"), windows[0].undercutIds)
    }

    @Test
    fun `far apart undercuts get separate windows`() {
        val windows = clusterUndercuts(
            listOf(
                UndercutSpanMm("a", 100f, 140f),
                UndercutSpanMm("b", 140f + UNDERCUT_CLUSTER_GAP_MM + 1f, 900f),
            ),
            oalMm = 2000f,
        )
        assertEquals(2, windows.size)
        assertEquals(listOf("a"), windows[0].undercutIds)
        assertEquals(listOf("b"), windows[1].undercutIds)
        assertTrue(windows[0].endMm < windows[1].startMm)
    }

    @Test
    fun `padded windows that touch are defensively merged`() {
        // Retuned constants where pad overwhelms the gap: spans 10mm apart, gap
        // threshold 5mm (separate clusters), pad 20mm (padded windows overlap).
        val windows = clusterUndercuts(
            listOf(UndercutSpanMm("a", 100f, 120f), UndercutSpanMm("b", 130f, 150f)),
            oalMm = 1000f, gapMm = 5f, padMm = 20f,
        )
        assertEquals(1, windows.size)
        assertEquals(listOf("a", "b"), windows[0].undercutIds)
        assertEquals(80f, windows[0].startMm, 1e-4f)
        assertEquals(170f, windows[0].endMm, 1e-4f)
    }

    @Test
    fun `empty and fully-outside spans yield no windows`() {
        assertTrue(clusterUndercuts(emptyList(), 1000f).isEmpty())
        assertTrue(
            clusterUndercuts(listOf(UndercutSpanMm("a", 500f, 500f)), 1000f).isEmpty(),
        )
    }

    // ── Hit-testing ──

    @Test
    fun `window pick finds the containing window`() {
        val windows = clusterUndercuts(listOf(UndercutSpanMm("a", 500f, 540f)), 1000f)
        assertNotNull(pickUndercutWindowAt(520f, windows))
        assertNull(pickUndercutWindowAt(700f, windows))
    }

    @Test
    fun `undercut pick prefers a span containing the tap over a nearby pad hit`() {
        val spans = listOf(
            UndercutSpanMm("inside", 100f, 200f),
            UndercutSpanMm("padded", 205f, 300f),
        )
        assertEquals("inside", pickUndercutAt(199f, spans, padPx(10f)))
    }

    @Test
    fun `undercut pick falls back to the pad zone and breaks ties by nearer edge`() {
        val spans = listOf(
            UndercutSpanMm("left", 100f, 200f),
            UndercutSpanMm("right", 210f, 300f),
        )
        assertEquals("right", pickUndercutAt(207f, spans, padPx(10f)))
        assertNull(pickUndercutAt(500f, spans, padPx(10f)))
    }

    // ── Placeholder Ø ──

    @Test
    fun `entered dia is used verbatim and zero dia gets the symbolic floor`() {
        assertEquals(101.6f, effectiveNotchDiaMm(101.6f, 114.3f), 1e-4f)
        assertEquals(
            114.3f * UNDERCUT_PLACEHOLDER_DEPTH_FRAC,
            effectiveNotchDiaMm(0f, 114.3f),
            1e-4f,
        )
    }

    /** The pad parameter is mm in shaft space; named helper only for test readability. */
    private fun padPx(mm: Float): Float = mm
}
