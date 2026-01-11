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
     */
    data class ShowDeletedSnack(val kind: ComponentKind) : UiEvent
}
