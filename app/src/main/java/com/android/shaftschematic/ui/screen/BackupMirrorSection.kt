package com.android.shaftschematic.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.io.BackupMirror
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Data → "Mirror saves to folder": pick a folder once, and every saved shaft is
 * copied there as well.
 *
 * The row is the feature's only status surface. Mirroring is deliberately silent while it works
 * and stays silent when it fails — a save must never be interrupted by a backup problem — so the
 * last attempt's result is reported here as quiet supporting text and nowhere else.
 *
 * "Stop mirroring" is the only thing that clears the stored folder. A revoked grant leaves the
 * choice standing so re-granting the same folder resumes it.
 */
@Composable
internal fun BackupMirrorSection(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val folderUri by SettingsStore.backupMirrorFolderUriFlow(ctx).collectAsState(initial = null)
    val lastOutcome by BackupMirror.lastOutcome.collectAsState()
    var folderLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(folderUri) {
        folderLabel = folderUri?.let { uri ->
            withContext(Dispatchers.IO) { BackupMirror.folderLabel(ctx, uri) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { BackupMirror.selectFolder(ctx, uri) }
    }

    ListItem(
        headlineContent = { Text("Mirror saves to folder") },
        supportingContent = {
            val label = folderLabel
            when {
                folderUri == null -> Text(
                    "Off — pick a folder and every shaft you save is copied there too, " +
                        "so the off-device backup is always current"
                )
                else -> Text(
                    buildString {
                        append("Copying every save to ‘${label ?: "the selected folder"}’")
                        lastOutcome?.let { outcome ->
                            when (outcome.status) {
                                BackupMirror.Status.WROTE ->
                                    append("\nLast copy: ${outcome.documentName}")
                                BackupMirror.Status.FAILED ->
                                    append(
                                        "\nLast copy of ${outcome.documentName} did not go " +
                                            "through (${outcome.detail ?: "write failed"}). " +
                                            "Your saved shaft is fine."
                                    )
                            }
                        }
                    },
                    color =
                        if (lastOutcome?.status == BackupMirror.Status.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
        trailingContent = {
            if (folderUri != null) {
                TextButton(
                    onClick = { scope.launch { BackupMirror.clearFolder(ctx) } },
                    modifier = Modifier.testTag("backup_mirror_clear"),
                ) { Text("Stop") }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_mirror_row")
            .clickable { picker.launch(null) },
    )
}
