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
    fun `pinned spans force the scale down until they fit - height yields, never the pinned span`() {
        // Three pinned spans (keyway-bearing bodies) totaling 2100mm on a 700pt page: at the
        // desired 0.4 the pinned spans alone need 840pt → the solve must come back below
        // 700/2100 ≈ 0.333.
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
        // At the solved scale the map keeps every pinned span at TRUE width.
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
    fun `liners compress in size above their floor - never pinned, never below the writable minimum`() {
        // on-device clarification: liners must never get body-style S-break cutouts (that
        // glyph is body-only) — the finite PROFILE_MIN_LINER_PT floor gives them SIZE
        // compression only, same posture as any other floored span.
        val features = listOf(feature(500f, 1500f, PROFILE_MIN_LINER_PT)) // 1000mm liner
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = features,
            contentLeft = 0f, contentRight = 400f, // heavy squeeze vs. 3000pt true total
            diaPtPerMm = 1f,
        )
        val liner = map.segments.first { it.startMm == 500f }
        val drawnWidth = liner.x1 - liner.x0
        assertTrue("liner compresses under heavy squeeze", drawnWidth < 1000f)
        assertTrue("liner never draws below its writable floor", drawnWidth >= PROFILE_MIN_LINER_PT - 1e-2f)
        assertTrue("liner segment is marked compressed", liner.compressed)
    }

    // ── exaggeratedProfileScale (the "Shaft height" slider) ────────────────────

    @Test
    fun `heightFrac of 1 with a generous budget returns baseScale unchanged`() {
        val scale = exaggeratedProfileScale(
            baseScale = 0.4f, heightFrac = 1.0f, budgetCapPt = 1000f, maxDiaMm = 50f,
        )
        assertEquals(0.4f, scale, 1e-6f)
    }

    @Test
    fun `heightFrac of 2 doubles the scale when both caps allow`() {
        val scale = exaggeratedProfileScale(
            baseScale = 0.4f, heightFrac = 2.0f, budgetCapPt = 1000f, maxDiaMm = 50f,
        )
        assertEquals(0.8f, scale, 1e-6f)
    }

    @Test
    fun `the ceiling is absolute - drawn height never exceeds 108pt at any frac`() {
        // base x frac x dia = 0_4 x 3_0 x 200 = 240pt, way past the 108pt (1_5in) ceiling —
        // capped no matter how generous the budget is.
        val scale = exaggeratedProfileScale(
            baseScale = 0.4f, heightFrac = 3.0f, budgetCapPt = 1000f, maxDiaMm = 200f,
        )
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT / 200f, scale, 1e-6f)
    }

    @Test
    fun `a short shaft's width-fit base is capped too - proportion kept, page width unspanned`() {
        // base = 1_0 x 200mm = 200pt: a stub shaft whose width-fit would draw far taller
        // than the ceiling. On-device direction: cap it even at 100% — the drawing then
        // simply doesn't span the page, leaving room for the dimension rails.
        val at100 = exaggeratedProfileScale(
            baseScale = 1.0f, heightFrac = 1.0f, budgetCapPt = 10000f, maxDiaMm = 200f,
        )
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT / 200f, at100, 1e-6f)
        // And no slider position pushes past the ceiling either.
        val at200 = exaggeratedProfileScale(
            baseScale = 1.0f, heightFrac = 2.0f, budgetCapPt = 10000f, maxDiaMm = 200f,
        )
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT / 200f, at200, 1e-6f)
    }

    // ── drawnShaftHeightPt / heightFracForDrawnHeight (value-based slider) ────

    @Test
    fun `slider track far end is the 1_5 inch cap when the shaft can reach it`() {
        // 8in shaft at the 0_4 visual scale: 300% would draw 244pt — capped at 108pt.
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            drawnShaftHeightPt(baseScale = 0.4f, heightFrac = PROFILE_HEIGHT_SCALE_MAX, maxDiaMm = 203.2f),
            1e-3f,
        )
    }

    @Test
    fun `small shafts top out below the cap`() {
        // 2in shaft: even 300% draws only 60pt — the track's far end shows that value.
        assertEquals(
            50f * 0.4f * 3f,
            drawnShaftHeightPt(baseScale = 0.4f, heightFrac = PROFILE_HEIGHT_SCALE_MAX, maxDiaMm = 50f),
            1e-3f,
        )
    }

    @Test
    fun `picked height round-trips to the stored multiplier and back`() {
        // The slider selects by VALUE (on-device request): picking 108pt on an 8in shaft
        // stores frac 108/(203_2 x 0_4) ≈ 1_329, which draws exactly 108pt again.
        val frac = heightFracForDrawnHeight(baseScale = 0.4f, targetHeightPt = 108f, maxDiaMm = 203.2f)
        assertEquals(108f / (203.2f * 0.4f), frac, 1e-4f)
        assertEquals(108f, drawnShaftHeightPt(0.4f, frac, 203.2f), 1e-2f)
    }

    @Test
    fun `inverse clamps to the slider bounds and guards degenerate input`() {
        // A target below the 50% height clamps to the bound; an absurd target to 300%.
        assertEquals(
            PROFILE_HEIGHT_SCALE_MIN,
            heightFracForDrawnHeight(baseScale = 0.4f, targetHeightPt = 10f, maxDiaMm = 203.2f),
            1e-6f,
        )
        assertEquals(
            PROFILE_HEIGHT_SCALE_MAX,
            heightFracForDrawnHeight(baseScale = 0.4f, targetHeightPt = 9999f, maxDiaMm = 203.2f),
            1e-6f,
        )
        assertEquals(1f, heightFracForDrawnHeight(0.4f, 100f, maxDiaMm = 0f), 1e-6f)
    }

    @Test
    fun `a budget cap below 108pt dominates the absolute ceiling`() {
        val scale = exaggeratedProfileScale(
            baseScale = 1.0f, heightFrac = 1.0f, budgetCapPt = 20f, maxDiaMm = 50f,
        )
        assertEquals(20f / 50f, scale, 1e-6f)
        assertTrue(scale < PROFILE_MAX_SHAFT_HEIGHT_PT / 50f)
    }

    @Test
    fun `out-of-range fracs coerce to the slider bounds`() {
        val base = 0.4f
        val maxDia = 50f
        val budget = 1000f
        val low = exaggeratedProfileScale(base, heightFrac = 0.1f, budgetCapPt = budget, maxDiaMm = maxDia)
        val lowClamped =
            exaggeratedProfileScale(base, heightFrac = PROFILE_HEIGHT_SCALE_MIN, budgetCapPt = budget, maxDiaMm = maxDia)
        assertEquals(lowClamped, low, 1e-6f)

        val high = exaggeratedProfileScale(base, heightFrac = 10f, budgetCapPt = budget, maxDiaMm = maxDia)
        val highClamped =
            exaggeratedProfileScale(base, heightFrac = PROFILE_HEIGHT_SCALE_MAX, budgetCapPt = budget, maxDiaMm = maxDia)
        assertEquals(highClamped, high, 1e-6f)
    }

    @Test
    fun `maxDiaMm zero returns baseScale unchanged`() {
        val scale = exaggeratedProfileScale(baseScale = 0.7f, heightFrac = 2.0f, budgetCapPt = 500f, maxDiaMm = 0f)
        assertEquals(0.7f, scale, 1e-6f)
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

    // ── defaultShaftHeightPt / defaultVisualScale (default sizing curve) ──────

    @Test
    fun `sizing curve anchors - 8in draws 1_25in, 4in draws 0_75in`() {
        assertEquals(90f, defaultShaftHeightPt(203.2f), 1e-3f)
        assertEquals(54f, defaultShaftHeightPt(101.6f), 1e-3f)
    }

    @Test
    fun `sizing curve interpolates linearly between the anchors`() {
        // 6in sits midway → midway height: 72pt (1.0in on paper).
        assertEquals(72f, defaultShaftHeightPt(152.4f), 1e-3f)
    }

    @Test
    fun `sizing curve continues past both anchors so sizes differentiate`() {
        // 3in keeps shrinking below the low anchor; 9in keeps growing above the high one.
        assertEquals(45f, defaultShaftHeightPt(76.2f), 1e-2f)
        assertEquals(99f, defaultShaftHeightPt(228.6f), 1e-2f)
        assertTrue(defaultShaftHeightPt(76.2f) < defaultShaftHeightPt(101.6f))
        assertTrue(defaultShaftHeightPt(228.6f) > defaultShaftHeightPt(203.2f))
    }

    @Test
    fun `sizing curve meets the absolute ceiling at 10in and stays capped above`() {
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(254f), 1e-2f)
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(400f), 1e-3f)
    }

    @Test
    fun `default visual scale is the curve height over the diameter`() {
        assertEquals(90f / 203.2f, defaultVisualScale(203.2f), 1e-5f)
        assertEquals(54f / 101.6f, defaultVisualScale(101.6f), 1e-5f)
        // Degenerate diameter falls back to the legacy flat scale.
        assertEquals(VISUAL_DIA_SCALE_PT_PER_MM, defaultVisualScale(0f), 1e-6f)
    }

    @Test
    fun `sizing curve anchor heights are adjustable`() {
        // On-device request: the anchor heights are settings (e.g. 4" shafts default to
        // 0.5"). The line re-derives: 4"→36pt, 8"→90pt, 6" midway →63pt.
        assertEquals(36f, defaultShaftHeightPt(101.6f, loHeightPt = 36f, hiHeightPt = 90f), 1e-3f)
        assertEquals(90f, defaultShaftHeightPt(203.2f, loHeightPt = 36f, hiHeightPt = 90f), 1e-3f)
        assertEquals(63f, defaultShaftHeightPt(152.4f, 36f, 90f), 1e-3f)
        assertEquals(63f / 152.4f, defaultVisualScale(152.4f, 36f, 90f), 1e-5f)
    }

    @Test
    fun `inverted anchor pair flattens at the low anchor - larger never draws smaller`() {
        assertEquals(72f, defaultShaftHeightPt(101.6f, loHeightPt = 72f, hiHeightPt = 36f), 1e-3f)
        assertEquals(72f, defaultShaftHeightPt(203.2f, 72f, 36f), 1e-3f)
        assertEquals(72f, defaultShaftHeightPt(300f, 72f, 36f), 1e-3f)
    }

    @Test
    fun `custom anchors still cap at the absolute ceiling`() {
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(203.2f, 54f, 200f), 1e-3f)
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(500f, 54f, 90f), 1e-3f)
    }

    // ── minWidthFracOfTrue (the "Liner compression" control) ──────────────────

    @Test
    fun `frac-of-true floor holds - the span keeps its fraction under squeeze`() {
        // 2000mm window at 1 pt/mm true into 700pt. The 600mm liner (600pt true) with a
        // 50% frac floor must draw ≥ 300pt even though its flat floor is only 100pt.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0.5f)),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
        )
        val w = map.xAt(1300f) - map.xAt(700f)
        assertTrue("liner drew $w, needs >= 300", w >= 300f - 0.5f)
        assertEquals(700f, map.x1, 0.1f)
    }

    @Test
    fun `frac 1 pins the span at true scale`() {
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 1f)),
            contentLeft = 0f, contentRight = 800f,
            diaPtPerMm = 1f,
        )
        assertEquals(600f, map.xAt(1300f) - map.xAt(700f), 0.5f)
    }

    @Test
    fun `frac demand lowers the max profile scale like a pin`() {
        val free = solveMaxProfileScale(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0f)),
            contentWidth = 700f, scaleHi = 1f,
        )
        val pinned = solveMaxProfileScale(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 1f)),
            contentWidth = 700f, scaleHi = 1f,
        )
        assertEquals(1f, free, 1e-4f)
        assertTrue("pinned scale $pinned must drop below free $free", pinned < free - 1e-3f)
    }

    @Test
    fun `frac zero is bit-for-bit the flat-floor behavior`() {
        val flat = buildCompressedProfileXMap(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f)),
            0f, 700f, 1f,
        )
        val zeroFrac = buildCompressedProfileXMap(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0f)),
            0f, 700f, 1f,
        )
        flat.segments.zip(zeroFrac.segments).forEach { (a, b) ->
            assertEquals(a.x0, b.x0, 1e-4f)
            assertEquals(a.x1, b.x1, 1e-4f)
        }
    }
}
