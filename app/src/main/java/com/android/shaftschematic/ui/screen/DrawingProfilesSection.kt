package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.settings.DRAWING_PROFILE_MAX_COUNT
import com.android.shaftschematic.settings.DRAWING_PROFILE_NAME_MAX_CHARS
import com.android.shaftschematic.settings.DrawingProfile

/**
 * Settings → Drawing → "Profiles": save the current drawing look under a name, apply a saved one,
 * rename or delete it, and restore the whole section to its fresh-install defaults.
 *
 * There is deliberately no "active profile" readout. A profile is a **one-shot copy** into the
 * device's preferences — nothing tracks which one was applied, and no document remembers the
 * profile that drew it, so a highlighted row would start lying the moment any single control is
 * adjusted afterwards.
 *
 * Restoring defaults takes a confirmation because it discards tuning that can represent a long
 * session of adjustment; applying a profile does not, because the look it replaces can be saved
 * as a profile first.
 */
@Composable
internal fun DrawingProfilesSection(
    profiles: Map<String, DrawingProfile>,
    onSave: (String) -> Unit,
    onApply: (DrawingProfile) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameText by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }

    val names = remember(profiles) { profiles.keys.sortedBy { it.lowercase() } }
    val typedName = nameText.trim()
    val replacesExisting = typedName.isNotEmpty() && profiles.containsKey(typedName)
    val atCap = !replacesExisting && profiles.size >= DRAWING_PROFILE_MAX_COUNT

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profiles", style = MaterialTheme.typography.titleSmall)
        Text(
            "A profile stores the whole drawing look: everything in this section, the line " +
                "thickness, and the PDF Export page's shading, component titles and tiering " +
                "reference. It applies to every shaft you draw — a drawing never remembers " +
                "which profile made it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = nameText,
                onValueChange = { raw ->
                    if (raw.length <= DRAWING_PROFILE_NAME_MAX_CHARS) nameText = raw
                },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("drawing_profile_name"),
            )
            Button(
                onClick = {
                    onSave(typedName)
                    nameText = ""
                },
                enabled = typedName.isNotEmpty() && !atCap,
                modifier = Modifier.testTag("drawing_profile_save"),
            ) { Text(if (replacesExisting) "Update" else "Save") }
        }
        if (atCap) {
            Text(
                "Saved profile limit reached ($DRAWING_PROFILE_MAX_COUNT). Delete one to save another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (names.isEmpty()) {
            Text(
                "No saved profiles yet. Set the drawing up the way you want it, then save it " +
                    "under a name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            names.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { profiles[name]?.let(onApply) },
                        modifier = Modifier.testTag("drawing_profile_apply_$name"),
                    ) { Text("Apply") }
                    Box {
                        var menuOpen by remember(name) { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Profile options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename…") },
                                onClick = {
                                    menuOpen = false
                                    pendingRename = name
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete…") },
                                onClick = {
                                    menuOpen = false
                                    pendingDelete = name
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = { confirmRestore = true },
            modifier = Modifier.testTag("drawing_restore_defaults"),
        ) { Text("Restore Drawing defaults") }
        Text(
            "Puts the whole drawing look back to what a new install starts with — the same " +
                "settings a profile stores. Saved profiles, the theme and the preview colors " +
                "are all kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore Drawing defaults?") },
            text = {
                Text(
                    "Every drawing-look setting goes back to its original value — this " +
                        "section, the line thickness, and the PDF Export page's shading, " +
                        "titles and tiering. Your saved profiles are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        onRestoreDefaults()
                    },
                    modifier = Modifier.testTag("drawing_restore_confirm"),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this profile?") },
            text = {
                Text("‘$target’ will be removed. The current drawing settings stay as they are.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    pendingRename?.let { target ->
        var text by remember(target) { mutableStateOf(target) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        if (raw.length <= DRAWING_PROFILE_NAME_MAX_CHARS) text = raw
                    },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        pendingRename = null
                        onRename(target, text)
                    },
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("Cancel") }
            },
        )
    }
}
