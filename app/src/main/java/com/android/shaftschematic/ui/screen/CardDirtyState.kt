package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Per-card record of which commit-on-blur fields hold an edit that has not landed yet.
 *
 * [ComponentCard]'s Save button is enabled off [hasPendingEdits] and greys out the moment the
 * blur pipeline commits, so a card with a disabled Save is a solid statement that everything
 * on it is saved. Nothing here commits anything — Save force-clears focus and the existing
 * blur path does the writing.
 *
 * Fields register themselves through [LocalCardDirtyState] rather than being wired up one by
 * one at every call site. A card carries dozens of numeric fields across four kinds, and a
 * hand-maintained per-field key list would be a second place for the button and the fields to
 * drift apart — the first field added without a key would silently stop lighting Save up.
 *
 * View state only: never written to the document, `EditState`, or undo history. Instant-commit
 * controls (chips, checkboxes, switches, sliders) never register — they have no uncommitted
 * state to report.
 */
@Stable
internal class CardDirtyState {
    /** Keyed by an identity token owned by the field instance; values are irrelevant. */
    private val dirtyFields = mutableStateMapOf<Any, Unit>()

    /** True while any registered field's text differs from its last committed value. */
    val hasPendingEdits: Boolean get() = dirtyFields.isNotEmpty()

    fun setDirty(token: Any, dirty: Boolean) {
        if (dirty) dirtyFields[token] = Unit else dirtyFields.remove(token)
    }

    /** A field leaving composition drops its claim — otherwise a swiped-away card's edit
     *  would keep the next card's Save button lit. */
    fun forget(token: Any) {
        dirtyFields.remove(token)
    }
}

/**
 * Null outside a [ComponentCard]: the Add dialogs commit through their own Add button and have
 * no Save affordance to drive, so their fields report to nobody.
 */
internal val LocalCardDirtyState = staticCompositionLocalOf<CardDirtyState?> { null }
