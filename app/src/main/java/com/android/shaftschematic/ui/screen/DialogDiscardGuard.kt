// file: app/src/main/java/com/android/shaftschematic/ui/screen/DialogDiscardGuard.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Whether the user has changed anything in an entry dialog since it opened — a keystroke in any
 * field (committed or still being typed), a chip moved to another option, a switch or checkbox
 * flipped. A dialog that is [edited] asks before Back or a tap outside throws the entry away
 * ([DiscardGuardedDialog]); an untouched one closes as it always did.
 *
 * The flag is RAISED BY THE CONTROLS, not listed by each dialog: the Add dialogs' shared building
 * blocks ([LocalDialogEdits] — the number field, the AFT/FWD-style chip, [DialogSwitch], the
 * captured-keyway row, and the shared sections' leaf controls: [KeywayGateRow],
 * [ThreadReliefSwitchRow], [ChipRow], the seal-area checkboxes, the shoulder radius picker) mark it
 * themselves, so a field or chip added to a dialog from those pieces is covered without the dialog
 * having to remember it. A raw Material control placed straight into a guarded dialog is the one
 * way to bypass it — build from the shared pieces, or call [mark] in its callback.
 *
 * A control marks only on a CHANGE: re-tapping the selected chip or a blur that hands the text
 * back unchanged is not an edit. Seeding a field when a section opens is part of the control
 * change that opened it and adds nothing of its own.
 */
@Stable
internal class DialogEdits(edited: Boolean = false) {
    var edited: Boolean by mutableStateOf(edited)
        private set

    fun mark() {
        edited = true
    }

    companion object {
        val Saver: Saver<DialogEdits, Boolean> = Saver(save = { it.edited }, restore = { DialogEdits(it) })
    }
}

/**
 * The [DialogEdits] of the guarded dialog a control sits in; null everywhere else — the carousel
 * cards share several of the same controls, and there marking is a no-op.
 */
internal val LocalDialogEdits = staticCompositionLocalOf<DialogEdits?> { null }

/** A [DialogEdits] for a dialog whose fields reset with it on a configuration change. */
@Composable
internal fun rememberDialogEdits(vararg keys: Any?): DialogEdits = remember(*keys) { DialogEdits() }

/** A [DialogEdits] for a dialog whose fields survive a configuration change (`rememberSaveable`). */
@Composable
internal fun rememberSaveableDialogEdits(): DialogEdits =
    rememberSaveable(saver = DialogEdits.Saver) { DialogEdits() }

/**
 * [ImeResizingAlertDialog] with a discard guard on its dismiss: Back or a tap outside closes an
 * untouched dialog at once ([onDismiss]), and on an [edits]-marked one opens
 * [DiscardEntryConfirmDialog] instead — "Keep editing" returns to the dialog with everything
 * intact, "Discard" closes it through [onDismiss]. An explicit Cancel button the host puts in
 * [dismissButton] stays a direct discard: it is a deliberate choice, a stray swipe or tap is not
 * (on-device ruling: a filled Add dialog lost to Back "should ask to be safe").
 *
 * The guard rides the dialog's `onDismissRequest` only, so the IME padding and window sizing of
 * [ImeResizingAlertDialog] are untouched. [edits] is provided to the dialog's content through
 * [LocalDialogEdits], which is how the shared controls mark it.
 */
@Composable
internal fun DiscardGuardedDialog(
    discardTitle: String,
    onDismiss: () -> Unit,
    edits: DialogEdits = rememberDialogEdits(),
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalDialogEdits provides edits) {
        ImeResizingAlertDialog(
            onDismissRequest = { if (edits.edited) confirmOpen = true else onDismiss() },
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
    if (confirmOpen) {
        DiscardEntryConfirmDialog(
            title = discardTitle,
            onKeepEditing = { confirmOpen = false },
            onDiscard = {
                confirmOpen = false
                onDismiss()
            },
        )
    }
}

/**
 * "Discard this …?" — the question a dismissed, filled entry dialog asks. Laid out like the
 * undercut screens' leave prompt: "Keep editing" is the confirm and also what dismissing this
 * prompt means (Back or a tap outside never discards), "Discard" is the only way to lose the entry.
 */
@Composable
internal fun DiscardEntryConfirmDialog(
    title: String,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.testTag("add_dialog_discard_confirm"),
        title = { Text(title) },
        text = { Text("What you typed will be lost.") },
        confirmButton = {
            TextButton(onClick = onKeepEditing, modifier = Modifier.testTag("add_dialog_keep_editing")) {
                Text("Keep editing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("add_dialog_discard")) {
                Text("Discard")
            }
        },
    )
}

/** A [Switch] that marks the enclosing guarded dialog edited when the user flips it. */
@Composable
internal fun DialogSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val edits = LocalDialogEdits.current
    Switch(
        checked = checked,
        onCheckedChange = { on ->
            if (on != checked) edits?.mark()
            onCheckedChange(on)
        },
        modifier = modifier,
        enabled = enabled,
    )
}
