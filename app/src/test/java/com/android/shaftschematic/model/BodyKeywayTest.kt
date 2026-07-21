package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body-hosted keyways (intermediate shafts with fitted couplings): field invariants,
 * absolute-span resolution for the AFT/FWD end reference, keyway carry across body
 * split/merge, and the spec-level keyway count that gates the 180°-apart note.
 * Mirrors [TaperKeywayTest] for the shared invariants.
 */
class BodyKeywayTest {

    private fun body(
        startMm: Float = 0f,
        lengthMm: Float = 400f,
        diaMm: Float = 100f,
        kwWidth: Float = 0f,
        kwDepth: Float = 0f,
        kwLength: Float = 0f,
        kwOffset: Float = 0f,
        kwEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
        spooned: Boolean = false,
    ) = Body(
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = diaMm,
        keywayWidthMm = kwWidth,
        keywayDepthMm = kwDepth,
        keywayLengthMm = kwLength,
        keywayOffsetFromEndMm = kwOffset,
        keywayEnd = kwEnd,
        keywaySpooned = spooned,
    )

    private fun keyedBody(
        startMm: Float = 0f,
        lengthMm: Float = 400f,
        kwLength: Float = 200f,
        kwOffset: Float = 0f,
        kwEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
    ) = body(
        startMm = startMm, lengthMm = lengthMm,
        kwWidth = 30f, kwDepth = 15f, kwLength = kwLength, kwOffset = kwOffset, kwEnd = kwEnd,
    )

    // ── hasKeyway ────────────────────────────────────────────────────────────

    @Test fun `hasKeyway is false when all dims are zero`() {
        assertFalse(body().hasKeyway)
    }

    @Test fun `hasKeyway is false when only width is set`() {
        assertFalse(body(kwWidth = 30f).hasKeyway)
    }

    @Test fun `hasKeyway is true when width depth and length are all non-zero`() {
        assertTrue(keyedBody().hasKeyway)
    }

    // ── isValid ──────────────────────────────────────────────────────────────

    @Test fun `isValid passes when open keyway fits within body`() {
        assertTrue(keyedBody(kwOffset = 0f).isValid(1000f))
    }

    @Test fun `isValid passes when floating keyway fits within body`() {
        assertTrue(keyedBody(kwLength = 200f, kwOffset = 50f).isValid(1000f))
    }

    @Test fun `isValid fails when floating keyway overruns body length`() {
        assertFalse(keyedBody(kwLength = 200f, kwOffset = 300f).isValid(1000f))
    }

    @Test fun `isValid fails when keyway length alone exceeds body length`() {
        assertFalse(keyedBody(kwLength = 500f).isValid(1000f))
    }

    @Test fun `isValid fails when negative offset`() {
        assertFalse(keyedBody(kwOffset = -1f).isValid(1000f))
    }

    // ── keywayAbsSpanMm ──────────────────────────────────────────────────────

    @Test fun `abs span from AFT face`() {
        val b = keyedBody(startMm = 100f, lengthMm = 400f, kwLength = 200f, kwOffset = 50f)
        val (lo, hi) = b.keywayAbsSpanMm()!!
        assertEquals(150f, lo, 1e-3f)
        assertEquals(350f, hi, 1e-3f)
    }

    @Test fun `abs span from FWD face`() {
        val b = keyedBody(
            startMm = 100f, lengthMm = 400f, kwLength = 200f, kwOffset = 50f,
            kwEnd = LinerAuthoredReference.FWD,
        )
        // FWD face at 500; near edge at 450; keyway spans 250..450
        val (lo, hi) = b.keywayAbsSpanMm()!!
        assertEquals(250f, lo, 1e-3f)
        assertEquals(450f, hi, 1e-3f)
    }

    @Test fun `abs span is null without a keyway`() {
        assertEquals(null, body().keywayAbsSpanMm())
    }

    // ── keywayCount ──────────────────────────────────────────────────────────

    @Test fun `keywayCount sums taper and body keyways`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(keyedBody(), body()),
            tapers = listOf(
                Taper(lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f,
                    keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 100f),
                Taper(lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f),
            ),
        )
        assertEquals(2, spec.keywayCount())
    }

    // ── carry across splitBodiesAround ───────────────────────────────────────

    @Test fun `split keeps AFT keyway on the left fragment`() {
        // Body 0..400, open AFT keyway 0..100. Taper inserted at 200..300.
        val spec = ShaftSpec(overallLengthMm = 400f, bodies = listOf(keyedBody(kwLength = 100f)))
        var n = 0
        val result = spec.splitBodiesAround(200f, 300f) { "id${n++}" }

        val left = result.spec.bodies.first { it.startFromAftMm == 0f }
        val right = result.spec.bodies.first { it.startFromAftMm == 300f }
        assertTrue(left.hasKeyway)
        assertEquals(0f, left.keywayOffsetFromEndMm, 1e-3f)
        assertFalse(right.hasKeyway)
    }

    @Test fun `split keeps FWD keyway on the right fragment with re-anchored offset`() {
        // Body 0..400, keyway referenced from FWD face, offset 20, length 60 → abs 320..380.
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(keyedBody(kwLength = 60f, kwOffset = 20f, kwEnd = LinerAuthoredReference.FWD)),
        )
        var n = 0
        val result = spec.splitBodiesAround(100f, 200f) { "id${n++}" }

        val right = result.spec.bodies.first { it.startFromAftMm == 200f }
        assertTrue(right.hasKeyway)
        // Right fragment keeps the original FWD face, so the offset is unchanged.
        assertEquals(20f, right.keywayOffsetFromEndMm, 1e-3f)
        assertEquals(LinerAuthoredReference.FWD, right.keywayEnd)
        val left = result.spec.bodies.first { it.startFromAftMm == 0f }
        assertFalse(left.hasKeyway)
    }

    @Test fun `split drops keyway when the cut passes through it`() {
        // Open AFT keyway 0..300; taper inserted at 100..200 cuts through it.
        val spec = ShaftSpec(overallLengthMm = 400f, bodies = listOf(keyedBody(kwLength = 300f)))
        var n = 0
        val result = spec.splitBodiesAround(100f, 200f) { "id${n++}" }
        assertTrue(result.spec.bodies.none { it.hasKeyway })
    }

    // ── carry across mergeBodiesAround ───────────────────────────────────────

    @Test fun `merge carries the surviving keyway into the merged body`() {
        // Fragments 0..100 (open AFT keyway 0..80) and 200..400; component 100..200 removed.
        val a = keyedBody(startMm = 0f, lengthMm = 100f, kwLength = 80f)
        val b = body(startMm = 200f, lengthMm = 200f)
        val spec = ShaftSpec(overallLengthMm = 400f, bodies = listOf(a, b))
        var n = 0
        val result = spec.mergeBodiesAround(100f, 200f) { "id${n++}" }

        val merged = result.spec.bodies.single()
        assertEquals(400f, merged.lengthMm, 1e-3f)
        assertTrue(merged.hasKeyway)
        assertEquals(0f, merged.keywayOffsetFromEndMm, 1e-3f)
        assertEquals(LinerAuthoredReference.AFT, merged.keywayEnd)
    }

    @Test fun `single-side expansion re-anchors a FWD-referenced keyway`() {
        // Body 0..100 with FWD-referenced open keyway (abs 40..100). Component 100..200
        // removed at the FWD end → body expands to 0..200; keyway stays at abs 40..100,
        // so its offset from the NEW FWD face becomes 100.
        val a = keyedBody(startMm = 0f, lengthMm = 100f, kwLength = 60f, kwOffset = 0f, kwEnd = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 200f, bodies = listOf(a))
        var n = 0
        val result = spec.mergeBodiesAround(100f, 200f) { "id${n++}" }

        val expanded = result.spec.bodies.single()
        assertEquals(200f, expanded.lengthMm, 1e-3f)
        assertTrue(expanded.hasKeyway)
        assertEquals(100f, expanded.keywayOffsetFromEndMm, 1e-3f)
        val (lo, hi) = expanded.keywayAbsSpanMm()!!
        assertEquals(40f, lo, 1e-3f)
        assertEquals(100f, hi, 1e-3f)
    }

    // ── backward-compat defaults ─────────────────────────────────────────────

    @Test fun `body keyway defaults are none, AFT reference`() {
        val b = Body()
        assertFalse(b.hasKeyway)
        assertEquals(0f, b.keywayOffsetFromEndMm, 0f)
        assertEquals(LinerAuthoredReference.AFT, b.keywayEnd)
        assertFalse(b.keywaySpooned)
    }

    @Test fun `spec keyways180Apart defaults false`() {
        assertFalse(ShaftSpec().keyways180Apart)
    }
}
