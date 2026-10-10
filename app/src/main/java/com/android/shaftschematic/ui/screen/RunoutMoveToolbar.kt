package com.android.shaftschematic.ui.screen

/**
 * RunoutMoveToolbar — the strip under the Runout tab's canvas while Move mode is on.
 *
 * Names what is selected and offers −/+ on the selected component, so a station can be added
 * or removed while looking at the bubble it affects rather than scrolling to the station rows
 * at the foot of the page. The buttons call the station editor's own actions with the editor's
 * own count (`stationCountFor`) and − rule (`canRemoveStation`), so the two surfaces cannot act
 * differently. A stateless view — the host owns the selection and the ViewModel calls.
 */

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * @param selectionLabel What is selected — see [moveSelectionLabel].
 * @param hasSelection False disables both buttons: −/+ act on a component, and none is chosen.
 * @param canRemove The station editor's − rule for the selected component's current count.
 */
@Composable
internal fun RunoutMoveToolbar(
    selectionLabel: String,
    hasSelection: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = selectionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasSelection) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .testTag("runout_move_selection"),
        )
        IconButton(
            onClick = onRemove,
            enabled = hasSelection && canRemove,
            modifier = Modifier.testTag("runout_move_remove"),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Remove station")
        }
        IconButton(
            onClick = onAdd,
            enabled = hasSelection,
            modifier = Modifier.testTag("runout_move_add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add station")
        }
    }
}
