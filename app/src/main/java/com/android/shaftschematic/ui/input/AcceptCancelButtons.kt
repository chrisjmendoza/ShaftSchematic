package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/**
 * File: AcceptCancelButtons.kt
 * Layer: UI → Input
 *
 * The green ✓ / red ✗ pair an edited value shows while it holds an edit that has not landed:
 * ✓ accepts it, ✗ puts the stored value back. ONE construction behind the Overall Length field
 * and every carousel card field, so the two read as the same control (on-device request: the
 * card's Save button at the foot of a long card was hard to find; the OAL's pair sits where the
 * edit is).
 *
 * Neither button can take focus: a click that pulled focus off the field would blur-commit the
 * field before the button's own handler ran, and ✗ would then cancel an edit that had already
 * landed.
 */

/** Semantic "accept" status colour for the ✓; legible on both themes. */
val ACCEPT_GREEN = Color(0xFF2E7D32)

@Composable
fun AcceptCancelButtons(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    acceptDescription: String,
    cancelDescription: String,
    acceptTag: String,
    cancelTag: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onAccept,
            modifier = Modifier
                .focusProperties { canFocus = false }
                .testTag(acceptTag),
        ) {
            Icon(Icons.Filled.Check, contentDescription = acceptDescription, tint = ACCEPT_GREEN)
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .focusProperties { canFocus = false }
                .testTag(cancelTag),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = cancelDescription,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
