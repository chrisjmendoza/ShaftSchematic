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
    fun `even a small shaft can reach the ceiling`() {
        // The track is a PAPER measure, so the whole 1/2in..1 1/2in band has to be offered on
        // any shaft — which is what the deliberately wide multiplier bounds buy. A 2in shaft's
        // standard height is a sliver, and it still reaches the cap.
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            drawnShaftHeightPt(baseScale = 0.4f, heightFrac = PROFILE_HEIGHT_SCALE_MAX, maxDiaMm = 50f),
            1e-3f,
        )
    }

    @Test
    fun `a large shaft shrinks all the way to the floor`() {
        // On-device report: a O11in shaft bottomed out at 0_69in — half its sizing-curve height
        // — because the track was a multiple of the curve rather than a paper measure. A big
        // shaft is usually a long one, and shrinking it is how the schematic stops cramping.
        val dia = 11f * 25.4f
        val base = defaultVisualScale(dia)
        assertEquals(
            PROFILE_MIN_SHAFT_HEIGHT_PT,
            drawnShaftHeightPt(base, PROFILE_HEIGHT_SCALE_MIN, dia),
            1e-3f,
        )
    }

    @Test
    fun `the floor never raises a shaft above its own sizing-curve height`() {
        // A 2in shaft's standard height (18pt) is UNDER the floor. It keeps that standard at
        // 100% — flooring it would fatten a small shaft off the proportional hand-sheet rule,
        // and the floor exists for the opposite case (shrinking a large shaft).
        val dia = 2f * 25.4f
        val base = defaultVisualScale(dia)
        val standard = base * dia
        assertTrue("premise: standard height is under the floor", standard < PROFILE_MIN_SHAFT_HEIGHT_PT)
        assertEquals(standard, drawnShaftHeightPt(base, 1f, dia), 1e-3f)
        // …and it cannot be dragged below that standard either.
        assertEquals(standard, drawnShaftHeightPt(base, PROFILE_HEIGHT_SCALE_MIN, dia), 1e-3f)
    }

    @Test
    fun `the floor yields to a page budget that cannot afford it`() {
        // Budget below the floor: the budget wins — the drawing must fit the paper first.
        val scale = exaggeratedProfileScale(
            baseScale = 1.0f, heightFrac = PROFILE_HEIGHT_SCALE_MIN, budgetCapPt = 20f, maxDiaMm = 50f,
        )
        assertEquals(20f / 50f, scale, 1e-6f)
    }

    @Test
    fun `picked height round-trips to the stored multiplier and back`() {
        // The slider selects by VALUE (on-device request): picking the top of the track on an
        // 8in shaft stores frac cap/(203_2 x 0_4), which draws exactly that height again. Read
        // off the constant — the cap has moved once and a spelled figure would drift with it.
        val target = PROFILE_MAX_SHAFT_HEIGHT_PT
        val frac = heightFracForDrawnHeight(baseScale = 0.4f, targetHeightPt = target, maxDiaMm = 203.2f)
        assertEquals(target / (203.2f * 0.4f), frac, 1e-4f)
        assertEquals(target, drawnShaftHeightPt(0.4f, frac, 203.2f), 1e-2f)
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
    fun `standard anchors are proportional - 8in draws 1in, 6in draws 3-4in, 4in draws 1-2in`() {
        // The standard line passes through the origin — drawn height stays strictly
        // proportional to true diameter (the hand-sheet rule from the original rulered
        // sketches; taller defaults read "chubby" on-device).
        assertEquals(72f, defaultShaftHeightPt(203.2f), 1e-3f)
        assertEquals(54f, defaultShaftHeightPt(152.4f), 1e-3f)
        assertEquals(36f, defaultShaftHeightPt(101.6f), 1e-3f)
        // Through-origin: half the diameter draws half the height.
        assertEquals(defaultShaftHeightPt(203.2f) / 2f, defaultShaftHeightPt(101.6f), 1e-3f)
    }

    @Test
    fun `sizing curve continues past both anchors so sizes differentiate`() {
        assertTrue(defaultShaftHeightPt(76.2f) < defaultShaftHeightPt(101.6f))
        assertTrue(defaultShaftHeightPt(228.6f) > defaultShaftHeightPt(203.2f))
    }

    @Test
    fun `sizing curve stays capped at the absolute ceiling`() {
        // The standard line reaches the 108pt cap at 12" of true diameter (304.8mm).
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(304.8f), 1e-2f)
        assertEquals(PROFILE_MAX_SHAFT_HEIGHT_PT, defaultShaftHeightPt(400f), 1e-3f)
    }

    @Test
    fun `default visual scale is the curve height over the diameter`() {
        assertEquals(72f / 203.2f, defaultVisualScale(203.2f), 1e-5f)
        assertEquals(36f / 101.6f, defaultVisualScale(101.6f), 1e-5f)
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
        // (Gap fracs off — this test isolates the liner mechanism.)
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0.5f)),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
            gapMinFracOfTrue = 0f,
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
            gapMinFracOfTrue = 0f,
        )
        assertEquals(600f, map.xAt(1300f) - map.xAt(700f), 0.5f)
    }

    @Test
    fun `frac demand never lowers the max profile scale - height precedence`() {
        // The drawing height takes precedence (on-device direction): a full-true liner
        // request must NOT drag the scale down the way a keyway pin does.
        val free = solveMaxProfileScale(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0f)),
            contentWidth = 700f, scaleHi = 1f,
        )
        val requested = solveMaxProfileScale(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 1f)),
            contentWidth = 700f, scaleHi = 1f,
        )
        assertEquals(1f, free, 1e-4f)
        assertEquals("frac must be scale-blind", free, requested, 1e-4f)
        // A true pin (MAX_VALUE) still yields the height — the documented keyway posture.
        val pinned = solveMaxProfileScale(
            0f, 2000f,
            listOf(ProfileFeatureSpan(700f, 1300f, Float.MAX_VALUE)),
            contentWidth = 700f, scaleHi = 1f,
        )
        assertTrue("keyway pin still yields ($pinned)", pinned < free - 1e-3f)
    }

    @Test
    fun `overflowing frac raises lambda-fit the page - other floors intact`() {
        // (Gap fracs off — isolates the liner raise.) Liner frac 1 wants 600pt but flat
        // floors (64 + 600 + 64) overflow the 700pt page: the raise shrinks to fit
        // (liner ≈ 572pt), the gap floors keep their 64, and the page is consumed
        // exactly — the scale was never touched.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 1f)),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
            gapMinFracOfTrue = 0f,
        )
        val liner = map.xAt(1300f) - map.xAt(700f)
        val gapA = map.xAt(700f) - map.xAt(0f)
        val gapB = map.xAt(2000f) - map.xAt(1300f)
        assertEquals(572f, liner, 1.0f)
        assertTrue("gap floors hold ($gapA / $gapB)", gapA >= 64f - 0.5f && gapB >= 64f - 0.5f)
        assertEquals(700f, map.x1, 0.1f)
    }

    @Test
    fun `fracFitFactor reports the applied lambda`() {
        val overflowing = fracFitFactor(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 1f)),
            contentWidthPt = 700f, diaPtPerMm = 1f,
            gapMinFracOfTrue = 0f,
        )
        assertEquals(572f / 600f, overflowing, 5e-3f)
        val fitting = fracFitFactor(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(ProfileFeatureSpan(700f, 1300f, 100f, minWidthFracOfTrue = 0.4f)),
            contentWidthPt = 700f, diaPtPerMm = 1f,
            gapMinFracOfTrue = 0f,
        )
        assertEquals(1f, fitting, 1e-4f)
    }

    @Test
    fun `body gaps keep their relative lengths under liner pressure`() {
        // The on-device report: with proportional liners, body runs collapsed to their
        // flat floors — equalized, "I can't tell that the span between the aft and mid
        // liner is longer." Gaps now join the λ pool (default gap frac): a 900mm and a
        // 500mm body run keep their true 1.8 ratio, and both draw ABOVE the flat floor.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2600f,
            features = listOf(
                ProfileFeatureSpan(500f, 1200f, 100f, minWidthFracOfTrue = 1f),
                ProfileFeatureSpan(2100f, 2400f, 100f, minWidthFracOfTrue = 1f),
            ),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
        )
        val gapAft = map.xAt(500f) - map.xAt(0f)      // 500mm run
        val gapMid = map.xAt(2100f) - map.xAt(1200f)  // 900mm run
        assertEquals("body runs keep true ratio", 900f / 500f, gapMid / gapAft, 0.02f)
        assertTrue("runs draw above the flat floor ($gapAft)", gapAft > PROFILE_MIN_BODY_RUN_PT + 1f)
        // Liners also keep their own ratio (700mm vs 300mm) under the shared λ.
        val linerA = map.xAt(1200f) - map.xAt(500f)
        val linerB = map.xAt(2400f) - map.xAt(2100f)
        assertEquals(700f / 300f, linerA / linerB, 0.02f)
        assertEquals(700f, map.x1, 0.1f)
    }

    @Test
    fun `taper frac floors preserve relative widths under squeeze`() {
        // Two frac-floored spans with NO flat floor (the taper posture) keep their TRUE
        // ratio at any squeeze — a 495mm and a 292mm taper must never draw equal
        // (on-device report: unequal tapers drew identical under the old flat floor).
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 2000f,
            features = listOf(
                ProfileFeatureSpan(0f, 495f, 0f, minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE),
                ProfileFeatureSpan(1700f, 1992f, 0f, minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE),
            ),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
            gapMinFracOfTrue = 0f,
        )
        val wAft = map.xAt(495f) - map.xAt(0f)
        val wFwd = map.xAt(1992f) - map.xAt(1700f)
        assertEquals("true ratio must survive the squeeze", 495f / 292f, wAft / wFwd, 1e-2f)
        assertTrue("frac floor holds", wAft >= 495f * PROFILE_TAPER_MIN_FRAC_OF_TRUE - 0.5f)
    }

    @Test
    fun `tapers out-prioritize body runs in the shared lambda pool`() {
        // On-device request: "sacrifice a little more of the body compression to make the
        // tapers more proportional." Under a hard squeeze both floors λ-fit, so neither is
        // absolute — the guarantee is PRIORITY: each span's kept fraction of true width
        // scales with its pool fraction (same λ), so the taper's kept fraction exceeds the
        // body run's by exactly the ratio of the two constants.
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = listOf(
                ProfileFeatureSpan(0f, 400f, 0f, minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE),
            ),
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
        )
        val taperKept = (map.xAt(400f) - map.xAt(0f)) / 400f
        val bodyKept = (map.xAt(3000f) - map.xAt(400f)) / 2600f
        assertEquals(
            "kept fractions must scale with the pool fractions",
            PROFILE_TAPER_MIN_FRAC_OF_TRUE / PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE,
            taperKept / bodyKept,
            0.02f,
        )
        assertTrue(
            "taper fraction must stay the pool's largest for the priority to hold",
            PROFILE_TAPER_MIN_FRAC_OF_TRUE > PROFILE_BODY_RUN_MIN_FRAC_OF_TRUE,
        )
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
