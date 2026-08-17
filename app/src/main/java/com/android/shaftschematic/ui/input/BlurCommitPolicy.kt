package com.android.shaftschematic.ui.input

/**
 * File: BlurCommitPolicy.kt
 * Layer: UI → Input
 *
 * The commit-on-blur decision for [NumericInputField], extracted so it can be unit-tested
 * without a Compose harness. See `docs/contracts/NumberField.md`.
 */

/**
 * Whether an unfocused-state callback should trigger a commit.
 *
 * A tap-and-leave with no edit must be a no-op: `onCommit` has side effects downstream
 * (ViewModel writes, `ensureOverall`, `rememberBodyDefaults`), so re-committing an unchanged
 * value is not merely wasteful.
 *
 * Commits require a focus baseline to compare against. A null baseline means focus was never
 * gained, so there is no edit to commit — most importantly this covers the initial
 * `onFocusChanged` callback that Compose delivers with `isFocused = false` when the modifier
 * first attaches. Treating that as a commit fired `onCommit` on every composition, which
 * silently rewrote the add-defaults just from scrolling the carousel.
 *
 * @param capturedOnFocus text as it stood when focus was gained; null if focus was never
 *   gained, or if a second unfocused callback arrives after the baseline was cleared.
 * @param currentText text at the moment of blur.
 */
fun shouldCommitOnBlur(capturedOnFocus: String?, currentText: String): Boolean =
    capturedOnFocus != null && currentText != capturedOnFocus
