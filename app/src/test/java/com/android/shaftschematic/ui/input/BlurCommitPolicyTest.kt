package com.android.shaftschematic.ui.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the commit-on-blur contract from `docs/NumberField.md`.
 *
 * The invariant that matters: a tap-and-leave with no edit is a no-op. `onCommit` has
 * side effects downstream (auto-body promotion, ViewModel writes), so a spurious commit
 * is not harmless.
 */
class BlurCommitPolicyTest {

    @Test
    fun `unchanged value does not commit`() {
        assertFalse(shouldCommitOnBlur(capturedOnFocus = "12.5", currentText = "12.5"))
    }

    @Test
    fun `changed value commits`() {
        assertTrue(shouldCommitOnBlur(capturedOnFocus = "12.5", currentText = "13.0"))
    }

    @Test
    fun `edit and revert by hand does not commit`() {
        // The user typed something, then undid it back to the original before leaving.
        // Same text in means same text out — no reason to fire side effects.
        assertFalse(shouldCommitOnBlur(capturedOnFocus = "7", currentText = "7"))
    }

    @Test
    fun `clearing the field commits`() {
        // Emptying a field is a real edit; the commit path validates and reverts it.
        assertTrue(shouldCommitOnBlur(capturedOnFocus = "7", currentText = ""))
    }

    @Test
    fun `typing into an empty field commits`() {
        assertTrue(shouldCommitOnBlur(capturedOnFocus = "", currentText = "7"))
    }

    @Test
    fun `an unfocused callback with no recorded focus does not commit`() {
        // Compose delivers an initial onFocusChanged with isFocused = false when the
        // modifier attaches. With no baseline there is no edit to commit — committing here
        // would fire onCommit on every composition.
        assertFalse(shouldCommitOnBlur(capturedOnFocus = null, currentText = "12.5"))
        assertFalse(shouldCommitOnBlur(capturedOnFocus = null, currentText = ""))
    }

    @Test
    fun `whitespace-only difference still counts as a change`() {
        // Comparison is exact, not trimmed — the commit path is what normalizes.
        assertTrue(shouldCommitOnBlur(capturedOnFocus = "7", currentText = "7 "))
    }

    @Test
    fun `trailing zero difference counts as a change`() {
        // "7" and "7.0" parse to the same number but are different text. Committing is
        // correct: the field's displayed text is itself user-visible state.
        assertTrue(shouldCommitOnBlur(capturedOnFocus = "7", currentText = "7.0"))
    }
}
