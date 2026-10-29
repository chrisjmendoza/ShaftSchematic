// file: app/src/main/java/com/android/shaftschematic/ui/viewmodel/UiEvent.kt
package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.ui.order.ComponentKind

/**
 * One-shot UI events emitted by [ShaftViewModel] and observed by routes/screens.
 *
 * These are for transient notifications (snackbars, dialogs), not for persistent state.
 */
sealed interface UiEvent {

    /** Request to show a simple snackbar message (no action). */
    data class ShowSnackbarMessage(val message: String) : UiEvent

    /**
     * Request to show a deletion snackbar with “Undo”.
     *
     * @property kind The kind of component that was deleted.
     * @property expected The editor state the delete produced. The snackbar's Undo goes through
     *   `ShaftViewModel.undoEditIfStillAt(expected)`, so it undoes the delete or nothing — never
     *   an edit made while the snackbar was up.
     */
    data class ShowDeletedSnack(val kind: ComponentKind, val expected: EditState) : UiEvent

    /**
     * Request to show what an OAL commit moved ([message], `oalMoveSummary`), with "Undo" — the
     * commit is one history step, so the action undoes the OAL change and the move together.
     * [expected] is the state the commit produced; as with [ShowDeletedSnack], Undo acts only
     * while the drawing is still in it.
     */
    data class ShowOalMovedSnack(val message: String, val expected: EditState) : UiEvent
}
