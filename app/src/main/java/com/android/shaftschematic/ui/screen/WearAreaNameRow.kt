package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.WearAreaName
import com.android.shaftschematic.ui.input.nameKeyboard

/**
 * The wear-area card's "Area:" row — what runs on the area (on-device request: wear where there
 * is no liner, such as packing or a grounding strap). Packing | Grounding strap | Seal write their
 * name; Other opens a field for a typed one. Tapping the selected chip again clears the name, so
 * an area can always go back to unnamed. The name is stored verbatim ([onCommit] hands it on).
 */
@Composable
internal fun WearAreaNameRow(
    spotId: String,
    label: String,
    onCommit: (String) -> Unit,
) {
    val preset = WearAreaName.presetFor(label)
    // Other is open for a typed name, or once tapped while the name is still blank.
    var otherTapped by remember(spotId) { mutableStateOf(false) }
    val otherOpen = (label.isNotBlank() && preset == null) || (otherTapped && label.isBlank())
    // A chip clears focus before it commits: the typed-name field's blur commit then lands FIRST,
    // and the chip's choice is the last word — never overwritten by a stale draft.
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Area:", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipFlowRow(Modifier.fillMaxWidth()) {
            WearAreaName.entries.forEach { name ->
                WearChip(
                    name.text, preset == name,
                    modifier = Modifier.testTag("wear_area_name_${name.name}_$spotId"),
                ) {
                    focusManager.clearFocus()
                    otherTapped = false
                    onCommit(if (preset == name) "" else name.text)
                }
            }
            WearChip(
                "Other", otherOpen,
                modifier = Modifier.testTag("wear_area_name_OTHER_$spotId"),
            ) {
                focusManager.clearFocus()
                if (otherOpen) {
                    otherTapped = false
                    if (label.isNotBlank()) onCommit("")
                } else {
                    otherTapped = true
                    if (preset != null) onCommit("")
                }
            }
        }
        if (otherOpen) {
            // Commit on blur only when changed — the card's Notes discipline.
            var draft by remember(spotId, label) { mutableStateOf(label) }
            var focusText by remember(spotId, label) { mutableStateOf<String?>(null) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Area name") },
                keyboardOptions = nameKeyboard(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wear_area_name_text_$spotId")
                    .onFocusChanged { f ->
                        if (f.isFocused) {
                            focusText = draft
                        } else {
                            val captured = focusText
                            focusText = null
                            if (captured != null && draft != captured) onCommit(draft)
                        }
                    },
            )
        }
    }
}
