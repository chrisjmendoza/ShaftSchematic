package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit bodies as non-negotiable, first-class components (2026-07-21): they participate
 * in collision detection, block overlapping adds/moves, and negotiate a shared boundary
 * with a liner whose length changes (the "filling" of an auto-body, but confirmed).
 */
class BodyCollisionTest {

    private fun body(id: String, start: Float, len: Float, label: String? = null) =
        Body(id = id, startFromAftMm = start, lengthMm = len, diaMm = 100f, label = label)

    private fun liner(id: String, start: Float, len: Float) =
        Liner(id = id, startFromAftMm = start, lengthMm = len, odMm = 120f, endMmPhysical = start + len)

    private fun taper(id: String, start: Float, len: Float) =
        Taper(id = id, startFromAftMm = start, lengthMm = len, startDiaMm = 100f, endDiaMm = 90f)

    // ── collidingIds ─────────────────────────────────────────────────────────

    @Test fun `body overlapping a liner is flagged`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(body("b", 0f, 1000f)),
            liners = listOf(liner("l", 800f, 400f)),
        )
        assertEquals(setOf("b", "l"), spec.collidingIds())
    }

    @Test fun `body overlapping a taper is flagged`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(body("b", 0f, 1000f)),
            tapers = listOf(taper("t", 900f, 300f)),
        )
        assertEquals(setOf("b", "t"), spec.collidingIds())
    }

    @Test fun `two overlapping bodies are flagged`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(body("a", 0f, 1000f), body("b", 900f, 500f)),
        )
        assertEquals(setOf("a", "b"), spec.collidingIds())
    }

    @Test fun `abutting body and liner are not flagged`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(body("b", 0f, 1000f)),
            liners = listOf(liner("l", 1000f, 400f)),
        )
        assertTrue(spec.collidingIds().isEmpty())
    }

    // ── bodyOverlapErrorMm ───────────────────────────────────────────────────

    @Test fun `overlap error reports the body label`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(body("b", 500f, 500f, label = "Coupling End")))
        assertEquals("Overlaps Coupling End", spec.bodyOverlapErrorMm(selfId = null, startMm = 900f, lengthMm = 200f))
    }

    @Test fun `overlap error falls back to positional label`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(body("b", 500f, 500f)))
        assertEquals("Overlaps Body 1", spec.bodyOverlapErrorMm(selfId = null, startMm = 900f, lengthMm = 200f))
    }

    @Test fun `abutting a body is allowed`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(body("b", 500f, 500f)))
        assertNull(spec.bodyOverlapErrorMm(selfId = null, startMm = 1000f, lengthMm = 200f))
    }

    @Test fun `a body does not collide with itself`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, bodies = listOf(body("b", 500f, 500f)))
        assertNull(spec.bodyOverlapErrorMm(selfId = "b", startMm = 600f, lengthMm = 200f))
    }

    // ── linerBodyBoundaryAdjust ──────────────────────────────────────────────

    @Test fun `extending a liner into the abutting fwd body offers to shorten it`() {
        // liner 200..600 abuts body 600..1000. Extend liner length 400 -> 500 (end 600 -> 700).
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(body("b", 600f, 400f)),
            liners = listOf(liner("l", 200f, 400f)),
        )
        val adj = spec.linerBodyBoundaryAdjust("l", oldStartMm = 200f, oldLengthMm = 400f, newStartMm = 200f, newLengthMm = 500f)!!
        assertTrue(adj.shorten)
        assertEquals("b", adj.bodyId)
        assertEquals(100f, adj.deltaMm, 1e-3f)
        assertEquals(700f, adj.newBodyStartMm, 1e-3f)  // body start follows liner end
        assertEquals(300f, adj.newBodyLengthMm, 1e-3f) // 1000 - 700
    }

    @Test fun `shortening a liner offers to grow the abutting fwd body to fill`() {
        // liner 200..600 abuts body 600..1000. Shorten liner length 400 -> 300 (end 600 -> 500).
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(body("b", 600f, 400f)),
            liners = listOf(liner("l", 200f, 400f)),
        )
        val adj = spec.linerBodyBoundaryAdjust("l", oldStartMm = 200f, oldLengthMm = 400f, newStartMm = 200f, newLengthMm = 300f)!!
        assertFalse(adj.shorten)
        assertEquals(100f, adj.deltaMm, 1e-3f)
        assertEquals(500f, adj.newBodyStartMm, 1e-3f)
        assertEquals(500f, adj.newBodyLengthMm, 1e-3f) // 1000 - 500
    }

    @Test fun `aft-edge move negotiates the aft-adjacent body`() {
        // body 0..400 abuts liner 400..800 (liner AFT edge at 400). Move liner start 400 -> 500.
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(body("b", 0f, 400f)),
            liners = listOf(liner("l", 400f, 400f)),
        )
        // start 400 -> 500 (AFT edge retracts FWD), length kept 400 so end 800 -> 900.
        val adj = spec.linerBodyBoundaryAdjust("l", oldStartMm = 400f, oldLengthMm = 400f, newStartMm = 500f, newLengthMm = 400f)!!
        assertFalse(adj.shorten)  // liner pulled away from the aft body -> grow it
        assertEquals("b", adj.bodyId)
        assertEquals(0f, adj.newBodyStartMm, 1e-3f)
        assertEquals(500f, adj.newBodyLengthMm, 1e-3f)  // body 0..500
    }

    @Test fun `no adjacent body yields no adjustment`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(body("b", 1500f, 400f)),
            liners = listOf(liner("l", 200f, 400f)),
        )
        assertNull(spec.linerBodyBoundaryAdjust("l", 200f, 400f, 200f, 500f))
    }

    @Test fun `liner swallowing the whole body is not a boundary nudge`() {
        // liner 200..600 abuts body 600..800; extend end to 900 (past body end 800).
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(body("b", 600f, 200f)),
            liners = listOf(liner("l", 200f, 400f)),
        )
        assertNull(spec.linerBodyBoundaryAdjust("l", 200f, 400f, 200f, 700f))
    }
}
