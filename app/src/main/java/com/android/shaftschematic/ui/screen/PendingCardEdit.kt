package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * A card field holding a typed value that has not landed, with the two answers the card
 * Save / Cancel bar gives: [accept] commits it (the ✓) and returns whether it landed — false when
 * the field refused it, so a card change waiting on Save stays on the card where the notice is —
 * and [cancel] puts the stored value back (the ✗).
 */
internal class PendingCardEdit(
    val cardTitle: String,
    val fieldLabel: String,
    val accept: () -> Boolean,
    val cancel: () -> Unit,
)

/**
 * The editor's record of which card field holds an unsaved edit. The editor screen provides it;
 * the Overall Length row swaps itself for the card Save / Cancel bar off [current], and the
 * carousel and the preview tap ask before changing cards while it is non-null.
 *
 * Fields register themselves ([CommitNum], [CommitDesignationField]) through
 * [LocalPendingCardEdits], keyed by an identity token each field instance owns, and drop the
 * entry when they settle or leave composition. View state only: never written to the document,
 * `EditState`, or undo history.
 */
@Stable
internal class PendingCardEdits {
    private val byField = mutableStateMapOf<Any, PendingCardEdit>()

    /** The pending edit, or null when every card field is settled. A blur commits, so at most
     *  one field is normally dirty at a time. */
    val current: PendingCardEdit? get() = byField.values.firstOrNull()

    fun set(token: Any, edit: PendingCardEdit?) {
        if (edit != null) byField[token] = edit else byField.remove(token)
    }
}

/** Null outside the editor screen: a card field there reports to nobody. */
internal val LocalPendingCardEdits = staticCompositionLocalOf<PendingCardEdits?> { null }

/** The title of the [ComponentCard] a field sits on, for the Save bar and the card-change prompt. */
internal val LocalCardTitle = staticCompositionLocalOf { "" }

/** [label] without its unit suffix — "KW L (in)" → "KW L" — for prose that names a field. */
internal fun fieldLabelWithoutUnit(label: String): String = label.substringBefore(" (").trim()

/** "“Length” on AFT Taper" — how the Save bar and the prompts name a pending edit. */
internal fun pendingEditName(edit: PendingCardEdit): String {
    val field = "“${fieldLabelWithoutUnit(edit.fieldLabel)}”"
    return if (edit.cardTitle.isBlank()) field else "$field on ${edit.cardTitle}"
}

/** testTags of [UnsavedCardEditDialog]. */
internal const val UNSAVED_EDIT_DIALOG_TAG = "unsaved_edit_dialog"
internal const val UNSAVED_EDIT_SAVE_TAG = "unsaved_edit_save"
internal const val UNSAVED_EDIT_DISCARD_TAG = "unsaved_edit_discard"
internal const val UNSAVED_EDIT_KEEP_TAG = "unsaved_edit_keep"

/**
 * "Save changes?" — asked when the user changes cards (a carousel swipe, the edge arrows, a tap on
 * a peeking card or on a component in the drawing) while a card field holds an unsaved value.
 * Save lands it, Discard puts the stored value back, Keep editing stays on the card; a dismissal
 * is Keep editing.
 */
@Composable
internal fun UnsavedCardEditDialog(
    edit: PendingCardEdit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Save changes?") },
        text = { Text("${pendingEditName(edit)} holds a value you have not saved.") },
        confirmButton = {
            TextButton(onClick = onSave, modifier = Modifier.testTag(UNSAVED_EDIT_SAVE_TAG)) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard, modifier = Modifier.testTag(UNSAVED_EDIT_DISCARD_TAG)) {
                    Text("Discard")
                }
                TextButton(onClick = onKeepEditing, modifier = Modifier.testTag(UNSAVED_EDIT_KEEP_TAG)) {
                    Text("Keep editing")
                }
            }
        },
        modifier = Modifier.testTag(UNSAVED_EDIT_DIALOG_TAG),
    )
}
