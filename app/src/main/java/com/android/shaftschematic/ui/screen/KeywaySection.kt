// file: app/src/main/java/com/android/shaftschematic/ui/screen/KeywaySection.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The "Keyway" header row that gates the whole keyway section — ONE composable behind the Body
 * and Taper carousel cards and `AddBodyDialog` / `AddTaperDialog`, so the four surfaces cannot
 * drift (add-dialog-parity rule). Everything keyway-related on those surfaces — the keyway unit
 * chip, W × D, the standard-size picker, KW L, captured + inset, spooned, and the clocking
 * section — renders only while [checked] is true.
 *
 * It is a Checkbox, deliberately not a Switch: in this app a checkbox means "this thing exists on
 * the component" (Explicit body, Keyway, Seal AFT/FWD), and a Switch means a mode of something
 * that already exists (Captured, Spooned, 180°).
 *
 * The row carries no policy: what an untick does (confirm before clearing stored values on a
 * card, simply hide in a dialog) is the caller's [onCheckedChange].
 */
@Composable
internal fun KeywayGateRow(
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ).padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Keyway", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** What a card does when its Keyway checkbox is toggled — see [keywayGateAction]. */
internal enum class KeywayGateAction {
    /** Show the fields. The model is untouched until a value commits. */
    REVEAL,
    /** Typed values exist: open [RemoveKeywayConfirmDialog]; nothing changes until it confirms. */
    CONFIRM_REMOVE,
    /** Nothing typed: hide the fields; there is nothing to clear. */
    HIDE,
}

/**
 * The ONE decision behind both cards' Keyway checkbox. Ticking only reveals; unticking a
 * component with ANY typed W/D/L ([hasAnyKeywayValue], the model's `hasAnyKeywayValue`) asks
 * first, because one tap would otherwise erase up to four typed values; unticking an empty
 * section just hides it. Dialogs do not use this — nothing is stored until they submit.
 */
internal fun keywayGateAction(checked: Boolean, hasAnyKeywayValue: Boolean): KeywayGateAction = when {
    checked -> KeywayGateAction.REVEAL
    hasAnyKeywayValue -> KeywayGateAction.CONFIRM_REMOVE
    else -> KeywayGateAction.HIDE
}

/**
 * Confirmation before a card's Keyway checkbox clears a stored keyway. One tap would otherwise
 * erase up to four typed values (W, D, L, inset); the explicit-body demote checkbox confirms for
 * the same reason. Dialogs never show this — nothing is stored until the dialog submits.
 */
@Composable
internal fun RemoveKeywayConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("kw_remove_confirm"),
        title = { Text("Remove keyway?") },
        text = { Text("This clears the keyway's width, depth, length and inset from this component.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
