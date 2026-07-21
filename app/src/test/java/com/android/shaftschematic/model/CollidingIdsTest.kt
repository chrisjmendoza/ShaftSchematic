package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ShaftSpec.collidingIds].
 *
 * collidingIds() returns the IDs of every component that participates in at least
 * one illegal overlap *within the current spec*.  This is distinct from
 * collectAddWarnings(), which checks whether a *new* proposed component would collide.
 *
 * Bodies are intentionally NOT checked (a body legitimately runs under a liner or up against
 * a taper; the resolve layer trims the drawn body around those, so a stored body span across
 * them is not a real conflict). Excluded threads are always skipped.
 */
class CollidingIdsTest {

    // ── Clean specs ───────────────────────────────────────────────────────

    @Test
    fun `empty spec returns empty set`() {
        assertTrue(ShaftSpec(overallLengthMm = 1000f).collidingIds().isEmpty())
    }

    @Test
    fun `non-overlapping components return empty set`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers  = listOf(Taper( startFromAftMm = 0f,   lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)),
            threads = listOf(Threads(startFromAftMm = 200f, lengthMm = 50f,  majorDiaMm = 50f, pitchMm = 2f)),
            liners  = listOf(Liner(  startFromAftMm = 400f, lengthMm = 100f, odMm = 55f)),
        )
        assertTrue(spec.collidingIds().isEmpty())
    }

    @Test
    fun `touching endpoints are not collisions`() {
        val taper  = Taper( id = "t1", startFromAftMm = 0f,   lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        val liner  = Liner(  id = "l1", startFromAftMm = 100f, lengthMm = 100f, odMm = 55f)
        val thread = Threads(id = "th1",startFromAftMm = 200f, lengthMm = 50f,  majorDiaMm = 50f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f,
            tapers = listOf(taper), liners = listOf(liner), threads = listOf(thread))
        assertTrue(spec.collidingIds().isEmpty())
    }

    // ── Taper–Taper ───────────────────────────────────────────────────────

    @Test
    fun `two overlapping tapers both appear in result`() {
        val t1 = Taper(id = "t1", startFromAftMm = 0f,  lengthMm = 150f, startDiaMm = 60f, endDiaMm = 50f)
        val t2 = Taper(id = "t2", startFromAftMm = 100f,lengthMm = 150f, startDiaMm = 50f, endDiaMm = 40f)
        val ids = ShaftSpec(overallLengthMm = 500f, tapers = listOf(t1, t2)).collidingIds()
        assertTrue(ids.contains("t1"))
        assertTrue(ids.contains("t2"))
    }

    @Test
    fun `non-overlapping tapers return empty set`() {
        val t1 = Taper(id = "t1", startFromAftMm = 0f,   lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        val t2 = Taper(id = "t2", startFromAftMm = 200f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f)
        assertTrue(ShaftSpec(overallLengthMm = 500f, tapers = listOf(t1, t2)).collidingIds().isEmpty())
    }

    // ── Taper–Thread ──────────────────────────────────────────────────────

    @Test
    fun `overlapping taper and included thread both appear in result`() {
        val t  = Taper(  id = "t1",  startFromAftMm = 0f,   lengthMm = 200f, startDiaMm = 60f, endDiaMm = 50f)
        val th = Threads(id = "th1", startFromAftMm = 100f, lengthMm = 50f,  majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = false)
        val ids = ShaftSpec(overallLengthMm = 500f, tapers = listOf(t), threads = listOf(th)).collidingIds()
        assertTrue(ids.contains("t1"))
        assertTrue(ids.contains("th1"))
    }

    @Test
    fun `excluded thread overlapping a taper is not flagged`() {
        val t  = Taper(  id = "t1",  startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 60f, endDiaMm = 50f)
        val th = Threads(id = "th1", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f,
                         excludeFromOAL = true, isAftEnd = true)
        val ids = ShaftSpec(overallLengthMm = 500f, tapers = listOf(t), threads = listOf(th)).collidingIds()
        assertFalse("excluded thread must not be flagged", ids.contains("th1"))
        assertFalse("taper must not be flagged for excluded-thread overlap", ids.contains("t1"))
    }

    // ── Taper–Liner ───────────────────────────────────────────────────────

    @Test
    fun `overlapping taper and liner both appear in result`() {
        val t  = Taper(id = "t1",  startFromAftMm = 50f,  lengthMm = 200f, startDiaMm = 60f, endDiaMm = 50f)
        val ln = Liner(id = "ln1", startFromAftMm = 200f, lengthMm = 100f, odMm = 55f)
        val ids = ShaftSpec(overallLengthMm = 500f, tapers = listOf(t), liners = listOf(ln)).collidingIds()
        assertTrue(ids.contains("t1"))
        assertTrue(ids.contains("ln1"))
    }

    // ── Thread–Thread ─────────────────────────────────────────────────────

    @Test
    fun `two overlapping included threads both appear in result`() {
        val th1 = Threads(id = "th1", startFromAftMm = 0f,  lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f)
        val th2 = Threads(id = "th2", startFromAftMm = 50f, lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f)
        val ids = ShaftSpec(overallLengthMm = 500f, threads = listOf(th1, th2)).collidingIds()
        assertTrue(ids.contains("th1"))
        assertTrue(ids.contains("th2"))
    }

    @Test
    fun `one excluded and one included thread overlapping - only included appears`() {
        val included = Threads(id = "th1", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = false)
        val excluded = Threads(id = "th2", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f, excludeFromOAL = true)
        val ids = ShaftSpec(overallLengthMm = 500f, threads = listOf(included, excluded)).collidingIds()
        assertFalse("excluded thread must not be flagged", ids.contains("th2"))
        assertFalse("included thread is not flagged for overlap with excluded", ids.contains("th1"))
    }

    // ── Thread–Liner ──────────────────────────────────────────────────────

    @Test
    fun `overlapping included thread and liner both appear in result`() {
        val th = Threads(id = "th1", startFromAftMm = 100f, lengthMm = 100f, majorDiaMm = 50f, pitchMm = 2f)
        val ln = Liner(  id = "ln1", startFromAftMm = 150f, lengthMm = 100f, odMm = 55f)
        val ids = ShaftSpec(overallLengthMm = 500f, threads = listOf(th), liners = listOf(ln)).collidingIds()
        assertTrue(ids.contains("th1"))
        assertTrue(ids.contains("ln1"))
    }

    // ── Liner–Liner ───────────────────────────────────────────────────────

    @Test
    fun `two overlapping liners both appear in result`() {
        val ln1 = Liner(id = "ln1", startFromAftMm = 0f,   lengthMm = 200f, odMm = 55f)
        val ln2 = Liner(id = "ln2", startFromAftMm = 150f, lengthMm = 100f, odMm = 55f)
        val ids = ShaftSpec(overallLengthMm = 500f, liners = listOf(ln1, ln2)).collidingIds()
        assertTrue(ids.contains("ln1"))
        assertTrue(ids.contains("ln2"))
    }

    @Test
    fun `non-overlapping liners return empty set`() {
        val ln1 = Liner(id = "ln1", startFromAftMm = 0f,   lengthMm = 100f, odMm = 55f)
        val ln2 = Liner(id = "ln2", startFromAftMm = 200f, lengthMm = 100f, odMm = 55f)
        assertTrue(ShaftSpec(overallLengthMm = 500f, liners = listOf(ln1, ln2)).collidingIds().isEmpty())
    }

    // ── Bodies are not flagged (they run under liners / against tapers) ────

    @Test
    fun `body overlapping a taper is not flagged`() {
        val body  = Body( id = "b1",  startFromAftMm = 0f,  lengthMm = 300f, diaMm = 60f)
        val taper = Taper(id = "t1",  startFromAftMm = 100f,lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        val ids = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body), tapers = listOf(taper)).collidingIds()
        assertFalse(ids.contains("b1"))
    }

    @Test
    fun `body overlapping a liner is not flagged`() {
        // A liner is a sleeve over the shaft — the body legitimately runs under it.
        val body  = Body(id = "b1",  startFromAftMm = 0f,   lengthMm = 300f, diaMm = 60f)
        val liner = Liner(id = "ln1", startFromAftMm = 100f, lengthMm = 100f, odMm = 55f)
        val ids = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body), liners = listOf(liner)).collidingIds()
        assertFalse(ids.contains("b1"))
        assertFalse(ids.contains("ln1"))
    }

    // ── Multi-collision ───────────────────────────────────────────────────

    @Test
    fun `multiple simultaneous collisions all appear in result`() {
        val t   = Taper(  id = "t1",  startFromAftMm = 0f,   lengthMm = 300f, startDiaMm = 60f, endDiaMm = 50f)
        val th  = Threads(id = "th1", startFromAftMm = 100f, lengthMm = 50f,  majorDiaMm = 50f, pitchMm = 2f)
        val ln  = Liner(  id = "ln1", startFromAftMm = 200f, lengthMm = 50f,  odMm = 55f)
        val ids = ShaftSpec(overallLengthMm = 500f,
            tapers = listOf(t), threads = listOf(th), liners = listOf(ln)).collidingIds()
        assertEquals("all three participants must be in result", setOf("t1", "th1", "ln1"), ids)
    }

    @Test
    fun `non-colliding component in multi-component spec is not included`() {
        val t1  = Taper(id = "t1",  startFromAftMm = 0f,   lengthMm = 150f, startDiaMm = 60f, endDiaMm = 50f)
        val t2  = Taper(id = "t2",  startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f)
        val t3  = Taper(id = "t3",  startFromAftMm = 400f, lengthMm = 50f,  startDiaMm = 40f, endDiaMm = 30f)
        val ids = ShaftSpec(overallLengthMm = 500f, tapers = listOf(t1, t2, t3)).collidingIds()
        assertTrue("t1 collides with t2", ids.contains("t1"))
        assertTrue("t2 collides with t1", ids.contains("t2"))
        assertFalse("t3 is clear and must not be flagged", ids.contains("t3"))
    }
}
