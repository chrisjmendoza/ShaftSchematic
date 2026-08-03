package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `geom/UndercutMath.kt` — reference conversion, span validation, cluster
 * windows, hit-testing, and the per-sheet drawn-depth exaggeration for the undercut
 * drawing feature.
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
    fun `conversion pair round-trips exactly for all references`() {
        for (ref in UndercutReference.entries) {
            for (entered in listOf(0f, 12.7f, 101.6001f, 333.333f)) {
                val canonical = undercutStartToCanonicalMm(
                    ref, entered, 55.5f, 42.42f, 1234.56f,
                    linerStartMm = 600f, linerEndMm = 1000f,
                )
                val redisplayed = canonicalToUndercutStartMm(
                    ref, canonical, 55.5f, 42.42f, 1234.56f,
                    linerStartMm = 600f, linerEndMm = 1000f,
                )
                assertEquals("ref=$ref entered=$entered", entered, redisplayed, 1e-3f)
            }
        }
    }

    @Test
    fun `LINER_AFT entry is offset by the liner AFT edge`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.LINER_AFT,
            enteredMm = 50f, lengthMm = 40f, aftSetXMm = 0f, fwdSetXMm = 0f,
            linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(650f, canonical, 1e-4f)
    }

    @Test
    fun `LINER_FWD entry locates the FWD edge measured aft from the liner FWD edge`() {
        val canonical = undercutStartToCanonicalMm(
            reference = UndercutReference.LINER_FWD,
            enteredMm = 50f, lengthMm = 40f, aftSetXMm = 0f, fwdSetXMm = 0f,
            linerStartMm = 600f, linerEndMm = 1000f,
        )
        // FWD edge at 1000 - 50 = 950, AFT edge (canonical) = 950 - 40.
        assertEquals(910f, canonical, 1e-4f)
    }

    // ── Length edits keep the authored Distance fixed ──

    @Test
    fun `length edit keeps the displayed distance unchanged for all references`() {
        for (ref in UndercutReference.entries) {
            val canonical = undercutStartToCanonicalMm(
                ref, enteredMm = 127f, lengthMm = 304.8f, aftSetXMm = 50f, fwdSetXMm = 1900f,
                linerStartMm = 600f, linerEndMm = 1000f,
            )
            val newCanonical = undercutCanonicalForNewLength(
                ref, canonical, oldLengthMm = 304.8f, newLengthMm = 254f,
                aftSetXMm = 50f, fwdSetXMm = 1900f, linerStartMm = 600f, linerEndMm = 1000f,
            )
            val redisplayed = canonicalToUndercutStartMm(
                ref, newCanonical, lengthMm = 254f, aftSetXMm = 50f, fwdSetXMm = 1900f,
                linerStartMm = 600f, linerEndMm = 1000f,
            )
            assertEquals("ref=$ref", 127f, redisplayed, 1e-3f)
        }
    }

    @Test
    fun `length edit under an AFT reference leaves canonical untouched`() {
        for (ref in listOf(UndercutReference.AFT_SET, UndercutReference.LINER_AFT)) {
            val newCanonical = undercutCanonicalForNewLength(
                ref, canonicalStartMm = 700f, oldLengthMm = 60f, newLengthMm = 90f,
                aftSetXMm = 50f, fwdSetXMm = 1900f, linerStartMm = 600f, linerEndMm = 1000f,
            )
            assertEquals("ref=$ref", 700f, newCanonical, 1e-4f)
        }
    }

    @Test
    fun `length edit under a FWD reference pins the cut's FWD end`() {
        // The on-device report's shape: liner-FWD reference, Distance 5 units, Length 12 -> 10.
        // The FWD end (linerEnd - distance) must stay put; the AFT start absorbs the delta.
        val canonical = undercutStartToCanonicalMm(
            UndercutReference.LINER_FWD, enteredMm = 127f, lengthMm = 304.8f,
            aftSetXMm = 0f, fwdSetXMm = 0f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        val newCanonical = undercutCanonicalForNewLength(
            UndercutReference.LINER_FWD, canonical, oldLengthMm = 304.8f, newLengthMm = 254f,
            aftSetXMm = 0f, fwdSetXMm = 0f, linerStartMm = 600f, linerEndMm = 1000f,
        )
        assertEquals(canonical + 304.8f, newCanonical + 254f, 1e-3f)
    }

    // ── Nearest-SET default for bare-shaft cuts ──

    @Test
    fun `bare-shaft cut in the AFT half defaults to the AFT SET`() {
        assertEquals(
            UndercutReference.AFT_SET,
            nearestSetReference(startMm = 300f, endMm = 360f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    @Test
    fun `bare-shaft cut in the FWD half defaults to the FWD SET`() {
        assertEquals(
            UndercutReference.FWD_SET,
            nearestSetReference(startMm = 1500f, endMm = 1560f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    @Test
    fun `bare-shaft cut straddling the midpoint breaks the tie to the AFT SET`() {
        assertEquals(
            UndercutReference.AFT_SET,
            nearestSetReference(startMm = 970f, endMm = 1030f, aftSetXMm = 100f, fwdSetXMm = 1900f),
        )
    }

    // ── Preview draw range (overlay window follows the previewed spans) ──

    @Test
    fun `preview range is the strip range while spans stay inside it`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val stored = listOf(UndercutSpanMm("u1", 700f, 760f))
        val strip = linerStripFor(liner, stored, oalMm = 2000f)
        val (start, end) = undercutPreviewDrawRange(strip, stored, oalMm = 2000f)
        assertEquals(strip.drawStartMm, start, 1e-4f)
        assertEquals(strip.drawEndMm, end, 1e-4f)
    }

    @Test
    fun `draft overhanging the liner edge widens the preview range with the standard pad`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val strip = linerStripFor(liner, listOf(UndercutSpanMm("u1", 700f, 760f)), oalMm = 2000f)
        // The draft slid the cut past the liner's AFT edge; the stored strip hasn't rebuilt.
        val preview = listOf(UndercutSpanMm("u1", 575f, 635f))
        val (start, end) = undercutPreviewDrawRange(strip, preview, oalMm = 2000f)
        assertEquals(575f - UNDERCUT_WINDOW_PAD_MM, start, 1e-4f)
        assertEquals(strip.drawEndMm, end, 1e-4f)
    }

    @Test
    fun `preview range clamps to the shaft and never narrows`() {
        val liner = UndercutLinerSpan("ln", 600f, 1000f)
        val strip = linerStripFor(liner, listOf(UndercutSpanMm("u1", 700f, 760f)), oalMm = 2000f)
        // Overhang near the shaft's AFT end: pad would go negative — clamp to 0.
        val (start, _) = undercutPreviewDrawRange(
            strip, listOf(UndercutSpanMm("u1", 10f, 50f)), oalMm = 2000f,
        )
        assertEquals(0f, start, 1e-4f)
        // No spans at all (every preview span clamped away): the strip range, unchanged.
        val (s2, e2) = undercutPreviewDrawRange(strip, emptyList(), oalMm = 2000f)
        assertEquals(strip.drawStartMm, s2, 1e-4f)
        assertEquals(strip.drawEndMm, e2, 1e-4f)
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
    fun `overlap check blocks intrusion into an adjacent cut and allows touching edges`() {
        val others = listOf(UndercutSpanMm("other", 200f, 260f))
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 250f, lengthMm = 40f, otherSpans = others))
        assertNotNull(undercutOverlapIssue(canonicalStartMm = 180f, lengthMm = 100f, otherSpans = others))
        // Edge-to-edge is legal, as is clear separation and an empty sheet.
        assertNull(undercutOverlapIssue(canonicalStartMm = 260f, lengthMm = 40f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 100f, lengthMm = 100f, otherSpans = others))
        assertNull(undercutOverlapIssue(canonicalStartMm = 250f, lengthMm = 40f, otherSpans = emptyList()))
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

    // ── Strips: liner-anchored vs free ──

    private val liners = listOf(UndercutLinerSpan("liner1", 600f, 1000f))

    @Test
    fun `cut is assigned to the liner with the largest overlap`() {
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("a", 700f, 760f), liners))
        // Crossing the FWD edge but mostly inside.
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("b", 960f, 1030f), liners))
        // Mostly outside: 990-1000 inside (10) vs 1000-1090 outside (90) — still liner1's
        // (it is the only overlapping liner); no overlap at all → null.
        assertEquals("liner1", assignUndercutLiner(UndercutSpanMm("c", 990f, 1090f), liners))
        assertNull(assignUndercutLiner(UndercutSpanMm("d", 100f, 160f), liners))
    }

    @Test
    fun `cut crossing two liners belongs to the one holding most of it`() {
        val two = listOf(
            UndercutLinerSpan("aftLiner", 0f, 500f),
            UndercutLinerSpan("fwdLiner", 500f, 1200f),
        )
        assertEquals("fwdLiner", assignUndercutLiner(UndercutSpanMm("x", 480f, 580f), two))
        assertEquals("aftLiner", assignUndercutLiner(UndercutSpanMm("y", 420f, 520f), two))
    }

    @Test
    fun `liner strip covers the whole liner and chains at its edges`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 700f, 760f)), liners, oalMm = 2000f,
        )
        assertEquals(1, strips.size)
        val s = strips[0] as UndercutStrip.LinerStrip
        assertEquals(600f - UNDERCUT_WINDOW_PAD_MM, s.drawStartMm, 1e-3f)
        assertEquals(1000f + UNDERCUT_WINDOW_PAD_MM, s.drawEndMm, 1e-3f)
        assertEquals(600f, s.chainStartMm, 1e-3f)
        assertEquals(1000f, s.chainEndMm, 1e-3f)
        assertEquals(listOf("a"), s.undercutIds)
    }

    @Test
    fun `cut overhanging the liner edge extends both draw and chain ranges`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 960f, 1030f)), liners, oalMm = 2000f,
        )
        val s = strips[0] as UndercutStrip.LinerStrip
        assertEquals(1030f, s.chainEndMm, 1e-3f)
        assertEquals(1030f + UNDERCUT_WINDOW_PAD_MM, s.drawEndMm, 1e-3f)
        assertEquals(600f, s.chainStartMm, 1e-3f)
    }

    @Test
    fun `bare-shaft cuts still cluster into free strips beside liner strips`() {
        val strips = buildUndercutStrips(
            listOf(
                UndercutSpanMm("bare", 100f, 160f),
                UndercutSpanMm("inLiner", 700f, 760f),
            ),
            liners, oalMm = 2000f,
        )
        assertEquals(2, strips.size)
        assertTrue(strips[0] is UndercutStrip.FreeStrip)
        assertTrue(strips[1] is UndercutStrip.LinerStrip)
        assertEquals(listOf("bare"), strips[0].undercutIds)
        // Free strip keeps the original window chain behavior: chain == draw range.
        assertEquals(strips[0].drawStartMm, strips[0].chainStartMm, 1e-4f)
    }

    @Test
    fun `liner with no cuts builds an empty authoring strip on demand`() {
        val s = linerStripFor(liners[0], emptyList(), oalMm = 2000f)
        assertTrue(s.undercutIds.isEmpty())
        assertEquals(600f, s.chainStartMm, 1e-3f)
        assertEquals(1000f, s.chainEndMm, 1e-3f)
    }

    @Test
    fun `strip pick finds the containing strip aft-first`() {
        val strips = buildUndercutStrips(
            listOf(UndercutSpanMm("a", 700f, 760f)), liners, oalMm = 2000f,
        )
        assertNotNull(pickUndercutStripAt(600f, strips))
        assertNull(pickUndercutStripAt(200f, strips))
    }

    // ── Drawn depth exaggeration (normalized to the sheet's deepest cut) ──

    /** Plain round stock of [diaMm] over the whole shaft — the exaggeration scenarios' surface. */
    private fun stockSegs(diaMm: Float, oalMm: Float = EX_OAL_MM): List<SurfaceSeg> =
        listOf(SurfaceSeg(0f, oalMm, diaMm, diaMm))

    /** A cut of [depthMm] Ø-reduction below [surfaceDiaMm], placed at [startMm]. */
    private fun cutOfDepth(id: String, depthMm: Float, surfaceDiaMm: Float, startMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = 50f, diaMm = surfaceDiaMm - depthMm)

    /** Drawn depth as a fraction of the local surface Ø — what the eye reads on the sheet. */
    private fun drawnFrac(diaMm: Float, surfaceDiaMm: Float, deepestMm: Float, ex: Float): Float =
        (surfaceDiaMm - normalizedNotchFloorDiaMm(diaMm, surfaceDiaMm, deepestMm, ex)) / surfaceDiaMm

    @Test
    fun `the sheet's deepest measured cut draws at exactly the exaggeration fraction`() {
        val surface = 10f * 25.4f
        val deep = cutOfDepth("deep", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(deep), stockSegs(surface), EX_OAL_MM)
        assertEquals(12.7f, deepest, 1e-3f)

        assertEquals(0.25f, drawnFrac(deep.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `a half-as-deep cut draws at the square root of its depth ratio`() {
        // Ordering WITHIN a sheet stays honest but the ratio is sqrt-compressed, so a
        // shallow cut never reads as a hairline next to a deep one.
        val surface = 10f * 25.4f
        val deep = cutOfDepth("deep", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val half = cutOfDepth("half", depthMm = 6.35f, surfaceDiaMm = surface, startMm = 600f)
        val deepest = deepestUndercutDepthMm(listOf(deep, half), stockSegs(surface), EX_OAL_MM)

        val deepFrac = drawnFrac(deep.diaMm, surface, deepest, 0.25f)
        val halfFrac = drawnFrac(half.diaMm, surface, deepest, 0.25f)
        assertEquals(0.25f, deepFrac, 1e-4f)
        assertEquals(deepFrac * kotlin.math.sqrt(0.5f), halfFrac, 1e-4f)
        assertTrue(halfFrac < deepFrac)
    }

    @Test
    fun `shallow cuts in one liner stay readable when a deeper cut lives in another`() {
        // On-device report: two ~0.005"-deep cuts in the MID liner drew as hairlines
        // because the FWD liner's 0.05"-deep cut owned the sheet reference. Every
        // measured cut draws at no less than the minimum share of the exaggeration.
        val surface = 7f * 25.4f
        val shallow = cutOfDepth("mid1", depthMm = 0.005f * 25.4f, surfaceDiaMm = surface, startMm = 200f)
        val deeper = cutOfDepth("fwd", depthMm = 0.05f * 25.4f, surfaceDiaMm = surface, startMm = 800f)
        val deepest = deepestUndercutDepthMm(listOf(shallow, deeper), stockSegs(surface), EX_OAL_MM)

        val shallowFrac = drawnFrac(shallow.diaMm, surface, deepest, 0.25f)
        assertTrue(
            "shallow frac $shallowFrac must be >= min share of the exaggeration",
            shallowFrac >= 0.25f * UNDERCUT_MIN_SHARE_OF_EXAGGERATION - 1e-4f,
        )
        assertEquals(0.25f, drawnFrac(deeper.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `sheets with very different absolute depths read alike`() {
        // The point of normalizing: a sheet whose worst cut is 1" deep and one whose worst is
        // 1/4" deep must print their deepest cut at the SAME drawn depth (on-device request).
        val surface = 10f * 25.4f
        val inchSheet = listOf(cutOfDepth("a", 25.4f, surface, 200f))
        val quarterSheet = listOf(cutOfDepth("b", 6.35f, surface, 200f))

        val inchDeepest = deepestUndercutDepthMm(inchSheet, stockSegs(surface), EX_OAL_MM)
        val quarterDeepest = deepestUndercutDepthMm(quarterSheet, stockSegs(surface), EX_OAL_MM)

        assertEquals(
            drawnFrac(inchSheet[0].diaMm, surface, inchDeepest, 0.25f),
            drawnFrac(quarterSheet[0].diaMm, surface, quarterDeepest, 0.25f),
            1e-4f,
        )
    }

    @Test
    fun `a cut deeper than the cap is never drawn shallower than its true depth`() {
        // True depth frac 0.5 exceeds the 0.25 cap — the drawn floor stays the true floor.
        val surface = 114.3f
        assertEquals(
            57.15f,
            normalizedNotchFloorDiaMm(57.15f, surface, deepestDepthMm = 57.15f, exaggerationFrac = 0.25f),
            1e-3f,
        )
    }

    @Test
    fun `zero exaggeration draws measured cuts at true scale`() {
        val surface = 10f * 25.4f
        val cut = cutOfDepth("c", depthMm = 12.7f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(cut), stockSegs(surface), EX_OAL_MM)

        assertEquals(
            cut.diaMm,
            normalizedNotchFloorDiaMm(cut.diaMm, surface, deepest, exaggerationFrac = 0f),
            1e-3f,
        )
    }

    @Test
    fun `a placeholder cut draws at half the exaggeration and never below the visibility floor`() {
        val surface = 10f * 25.4f
        assertEquals(
            0.25f * UNDERCUT_PLACEHOLDER_OF_EXAGGERATION,
            drawnFrac(0f, surface, deepestMm = 12.7f, ex = 0.25f),
            1e-4f,
        )
        // At true scale a placeholder would vanish, so it keeps the visibility floor.
        assertEquals(
            UNDERCUT_PLACEHOLDER_MIN_DRAWN_FRAC,
            drawnFrac(0f, surface, deepestMm = 12.7f, ex = 0f),
            1e-4f,
        )
    }

    @Test
    fun `placeholder cuts are excluded from the deepest-depth reference`() {
        // An unmeasured cut must not be able to squash the real ones: only measured cuts
        // (and only where material was actually removed) set the normalization reference.
        val surface = 10f * 25.4f
        val cuts = listOf(
            Undercut(id = "placeholder", startFromAftMm = 200f, lengthMm = 50f, diaMm = 0f),
            cutOfDepth("measured", depthMm = 6.35f, surfaceDiaMm = surface, startMm = 600f),
            // Ø at the surface: nothing removed, so it contributes no depth either.
            Undercut(id = "no-cut", startFromAftMm = 900f, lengthMm = 50f, diaMm = surface),
        )
        assertEquals(6.35f, deepestUndercutDepthMm(cuts, stockSegs(surface), EX_OAL_MM), 1e-3f)
    }

    @Test
    fun `a hairline cut on a ten inch shaft draws at the full exaggeration when it is the only cut`() {
        // Sizing context: shafts run up to ~10" Ø, where a 1/16" Ø-reduction is 0.6% of the
        // diameter — invisible at true scale. As the sheet's deepest cut it sets the
        // reference, so it draws at the full chosen exaggeration.
        val surface = 10f * 25.4f
        val cut = cutOfDepth("hairline", depthMm = (1f / 16f) * 25.4f, surfaceDiaMm = surface, startMm = 200f)
        val deepest = deepestUndercutDepthMm(listOf(cut), stockSegs(surface), EX_OAL_MM)

        assertEquals(0.25f, drawnFrac(cut.diaMm, surface, deepest, 0.25f), 1e-4f)
    }

    @Test
    fun `exaggeration degrades safely on degenerate inputs`() {
        // No surface to reference: the Ø passes straight through.
        assertEquals(50f, normalizedNotchFloorDiaMm(50f, 0f, 10f, 0.25f), 1e-4f)
        // A Ø at or above the surface removed no material (the card's warning case), so there
        // is no depth to exaggerate and the notch degenerates to the surface itself.
        assertEquals(114.3f, normalizedNotchFloorDiaMm(120f, 114.3f, 10f, 0.25f), 1e-4f)
        // An empty sheet has no reference depth at all.
        assertEquals(0f, deepestUndercutDepthMm(emptyList(), stockSegs(114.3f), EX_OAL_MM), 1e-4f)
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

    private companion object {
        /** Shaft extent used by the exaggeration scenarios (long enough for several cuts). */
        const val EX_OAL_MM = 2000f
    }
}
