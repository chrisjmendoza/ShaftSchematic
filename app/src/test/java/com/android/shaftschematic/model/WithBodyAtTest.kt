package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

private const val EPS = 0.001f

/**
 * Tests for [ShaftSpec.withBodyAt].
 *
 * Contract:
 *  - Out-of-range index returns `this` unchanged (same instance).
 *  - Length and Ø are clamped to ≥ 0.
 *  - Start is kept verbatim — never clamped or snapped (golden rule).
 *  - Keyway fields, id, and label of the edited body survive; other bodies are untouched.
 */
class WithBodyAtTest {

    private fun spec() = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(id = "b0", startFromAftMm = 0f, lengthMm = 200f, diaMm = 50f),
            Body(
                id = "b1", startFromAftMm = 200f, lengthMm = 300f, diaMm = 60f,
                keywayWidthMm = 12f, keywayDepthMm = 6f, keywayLengthMm = 80f,
                keywayOffsetFromEndMm = 10f, keywayEnd = LinerAuthoredReference.FWD,
                keywaySpooned = true, label = "coupling seat",
            ),
        ),
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `updates start length dia at index`() {
        val result = spec().withBodyAt(1, startMm = 250f, lengthMm = 350f, diaMm = 65f)
        val b = result.bodies[1]
        assertEquals(250f, b.startFromAftMm, EPS)
        assertEquals(350f, b.lengthMm, EPS)
        assertEquals(65f, b.diaMm, EPS)
    }

    @Test
    fun `other bodies are untouched`() {
        val before = spec()
        val result = before.withBodyAt(1, startMm = 250f, lengthMm = 350f, diaMm = 65f)
        assertSame(before.bodies[0], result.bodies[0])
    }

    @Test
    fun `keyway fields id and label survive the edit`() {
        val result = spec().withBodyAt(1, startMm = 250f, lengthMm = 350f, diaMm = 65f)
        val b = result.bodies[1]
        assertEquals("b1", b.id)
        assertEquals("coupling seat", b.label)
        assertEquals(12f, b.keywayWidthMm, EPS)
        assertEquals(6f, b.keywayDepthMm, EPS)
        assertEquals(80f, b.keywayLengthMm, EPS)
        assertEquals(10f, b.keywayOffsetFromEndMm, EPS)
        assertEquals(LinerAuthoredReference.FWD, b.keywayEnd)
        assertEquals(true, b.keywaySpooned)
    }

    // ── Out-of-range index ────────────────────────────────────────────────────

    @Test
    fun `index past end returns same instance`() {
        val before = spec()
        assertSame(before, before.withBodyAt(2, 0f, 100f, 50f))
    }

    @Test
    fun `negative index returns same instance`() {
        val before = spec()
        assertSame(before, before.withBodyAt(-1, 0f, 100f, 50f))
    }

    @Test
    fun `empty bodies list returns same instance`() {
        val before = ShaftSpec(overallLengthMm = 500f)
        assertSame(before, before.withBodyAt(0, 0f, 100f, 50f))
    }

    // ── Clamping ──────────────────────────────────────────────────────────────

    @Test
    fun `negative length clamps to zero`() {
        val result = spec().withBodyAt(0, startMm = 0f, lengthMm = -5f, diaMm = 50f)
        assertEquals(0f, result.bodies[0].lengthMm, EPS)
    }

    @Test
    fun `negative dia clamps to zero`() {
        val result = spec().withBodyAt(0, startMm = 0f, lengthMm = 200f, diaMm = -1f)
        assertEquals(0f, result.bodies[0].diaMm, EPS)
    }

    @Test
    fun `start is kept verbatim even when negative`() {
        val result = spec().withBodyAt(0, startMm = -3f, lengthMm = 200f, diaMm = 50f)
        assertEquals(-3f, result.bodies[0].startFromAftMm, EPS)
    }
}
