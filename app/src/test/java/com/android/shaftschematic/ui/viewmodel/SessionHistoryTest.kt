package com.android.shaftschematic.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the observable semantics of [SessionHistory] — time-based coalescing, cap eviction,
 * redo invalidation, identical-state no-op, undo/redo round-trip, and clear(). These are the
 * fixed contract the ShaftViewModel session undo/redo relies on.
 */
class SessionHistoryTest {

    /** How many times undo() can succeed from the current head, draining the undo stack. */
    private fun <T> drainUndoCount(h: SessionHistory<T>, from: T): Int {
        var count = 0
        var cur = from
        while (true) {
            val prior = h.undo(cur) ?: break
            cur = prior
            count++
        }
        return count
    }

    @Test
    fun `two records within the window collapse into one undo step`() {
        val h = SessionHistory<String>()
        h.record("base", 0)        // seed head
        h.record("a", 1000)        // first edit → new step (pushes base)
        h.record("ab", 1100)       // 100ms later → coalesces into the same step

        assertTrue(h.canUndo)
        assertEquals("undo returns pre-burst state", "base", h.undo("ab"))
        assertFalse("only one step existed", h.canUndo)
    }

    @Test
    fun `records outside the window are separate undo steps`() {
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("a", 1000)        // step 1 (pushes base)
        h.record("ab", 1700)       // 700ms > 600 window → step 2 (pushes a)

        assertEquals("a", h.undo("ab"))
        assertEquals("base", h.undo("a"))
        assertFalse(h.canUndo)
    }

    @Test
    fun `first edit after seed always starts a step even within the window`() {
        // The seed has no "previous record time", so the very next edit can never coalesce
        // away — a single deliberate edit is never lost.
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("a", 1)           // 1ms after seed, still a real step
        assertTrue(h.canUndo)
        assertEquals("base", h.undo("a"))
    }

    @Test
    fun `undo stack is capped and evicts the oldest`() {
        val h = SessionHistory<Int>(maxHistory = SessionHistory.MAX_HISTORY)  // 50
        h.record(0, 0)             // seed
        // 60 well-separated edits → 60 candidate steps, capped to 50.
        for (i in 1..60) h.record(i, i.toLong() * 1000L)
        assertEquals(50, drainUndoCount(h, from = 60))
    }

    @Test
    fun `a new state clears the redo stack`() {
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("a", 1000)
        h.record("b", 2000)
        assertEquals("a", h.undo("b"))   // redo now holds b
        assertTrue(h.canRedo)

        h.record("c", 3000)              // genuine new state invalidates redo
        assertFalse(h.canRedo)
    }

    @Test
    fun `identical consecutive state is a no-op`() {
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("base", 1000)           // equal to head → no step
        assertFalse(h.canUndo)

        h.record("a", 2000)              // real step
        h.record("a", 3000)              // equal to head again → no new step
        assertEquals("base", h.undo("a"))
        assertFalse(h.canUndo)
    }

    @Test
    fun `undo and redo round-trip restores states in order`() {
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("a", 1000)
        h.record("b", 2000)

        assertEquals("a", h.undo("b"))
        assertEquals("base", h.undo("a"))
        assertFalse(h.canUndo)
        assertTrue(h.canRedo)

        assertEquals("a", h.redo("base"))
        assertEquals("b", h.redo("a"))
        assertFalse(h.canRedo)
        assertTrue(h.canUndo)
    }

    @Test
    fun `clear drops all history and the head`() {
        val h = SessionHistory<String>()
        h.record("base", 0)
        h.record("a", 1000)
        assertTrue(h.canUndo)

        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.currentOrNull())
        // After clear the next record re-seeds; the following edit is a fresh step.
        h.record("x", 5000)
        assertFalse(h.canUndo)
    }
}
