package com.android.shaftschematic.ui.viewmodel

/**
 * File: SessionHistory.kt
 * Layer: ViewModel (pure — no Android / coroutine dependencies)
 *
 * Purpose
 * A generic, unit-testable undo/redo history for a single kind of snapshot [T].
 * Records are fed in as full snapshots; the class decides which ones become undo
 * steps using **time-based coalescing** so a typing burst collapses into one step.
 *
 * Observable semantics (pinned by SessionHistoryTest — do not weaken):
 * - [record] snapshots a new state. Changes arriving within [coalesceWindowMs] of the
 *   previous record collapse into the same undo step (the intermediate states are
 *   discarded; undo returns to the pre-burst state). A record whose gap since the last
 *   record exceeds the window starts a fresh undo step (the previous head is pushed).
 * - The FIRST record after construction / [clear] / [undo] / [redo] always starts a new
 *   step (there is no "previous record time" to coalesce against), so a single deliberate
 *   edit is never lost to coalescing.
 * - A record equal to the current head is a no-op (no step, no redo clear).
 * - Any genuine new state clears the redo stack.
 * - [undo] / [redo] are standard stack operations; [undo] pushes the supplied current
 *   onto redo and returns the prior state, [redo] mirrors it.
 * - The undo (and redo) stack is capped at [maxHistory]; the oldest entry is dropped on
 *   overflow.
 */
class SessionHistory<T>(
    private val coalesceWindowMs: Long = COALESCE_WINDOW_MS,
    private val maxHistory: Int = MAX_HISTORY,
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    // The latest recorded state (the "committed" head). Null until the first record seeds it.
    private var current: T? = null

    // Timestamp of the record that produced/updated `current`. Null right after a seed /
    // undo / redo, which forces the next genuine edit to start a fresh (non-coalesced) step.
    private var lastRecordMs: Long? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** The current committed head, or null before the first record. Exposed for inspection. */
    fun currentOrNull(): T? = current

    /**
     * Record [state] observed at [atMs] (caller supplies the clock — `System.currentTimeMillis()`
     * in production, a controlled value in tests). See the class doc for the coalescing rules.
     */
    fun record(state: T, atMs: Long) {
        val head = current
        if (head == null) {
            // Seed: first known state establishes the head without creating an undo step.
            current = state
            lastRecordMs = null
            return
        }
        if (state == head) return  // identical consecutive state → no-op

        val last = lastRecordMs
        val startsNewStep = last == null || (atMs - last) > coalesceWindowMs
        if (startsNewStep) {
            undoStack.addLast(head)
            while (undoStack.size > maxHistory) undoStack.removeFirst()
        }
        // A genuine new state always invalidates the redo branch.
        redoStack.clear()
        current = state
        lastRecordMs = atMs
    }

    /** Pop the previous state, pushing [current] onto redo. Returns null when nothing to undo. */
    fun undo(current: T): T? {
        val prior = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        while (redoStack.size > maxHistory) redoStack.removeFirst()
        this.current = prior
        lastRecordMs = null
        return prior
    }

    /** Re-apply the most recently undone state, pushing [current] onto undo. Null when none. */
    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        this.current = next
        lastRecordMs = null
        return next
    }

    /** Drop all history (both stacks and the head). Used at document/session boundaries. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        current = null
        lastRecordMs = null
    }

    companion object {
        /** Records within this many ms of the previous record collapse into one undo step. */
        const val COALESCE_WINDOW_MS = 600L

        /** Maximum undo (and redo) depth; the oldest step is evicted on overflow. */
        const val MAX_HISTORY = 50
    }
}
