package com.android.shaftschematic.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * InlineAddChooserDialog.md.md (v1.0)
 *
 * Purpose: Small, stable chooser for adding a component to the current shaft spec.
 * Stable Material3 only. No experimental APIs. No business logic.
 *
 * API (lambda-based)
 * - onDismiss(): close dialog without action
 * - onAddBody(), onAddLiner(), onAddThread(), onAddTaper(): fire-and-close actions
 */
@Composable
fun InlineAddChooserDialog(
    onDismiss: () -> Unit,
    onAddBody: () -> Unit,
    onAddLiner: () -> Unit,
    onAddThread: () -> Unit,
    onAddTaper: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Component") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAddBody() },  modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { Text("Body") }
                Button(onClick = { onAddLiner() }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { Text("Liner") }
                Button(onClick = { onAddThread() },modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { Text("Thread") }
                Button(onClick = { onAddTaper() }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { Text("Taper") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
