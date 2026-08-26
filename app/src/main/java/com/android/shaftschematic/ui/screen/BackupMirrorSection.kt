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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.io.BackupMirror
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Data → "Mirror saves to folder": pick a folder once, and every saved shaft is
 * copied there as well — plus the "Mirror all now" catch-up row underneath it.
 *
 * These two rows are the feature's only status surface. Mirroring is deliberately silent while it
 * works and stays silent when it fails — a save must never be interrupted by a backup problem —
 * so the last attempt's result is reported here as quiet supporting text and nowhere else.
 *
 * The catch-up keeps its own status line rather than sharing the folder row's: it reports a whole
 * run ("Mirrored 7 of 8"), while that line reports what the last single save did.
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
    val catchUp by BackupMirror.catchUp.collectAsState()
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
                                BackupMirror.Status.RENAMED ->
                                    append(
                                        "\nRenamed in the folder: " +
                                            "${outcome.previousName ?: "the old name"} → " +
                                            outcome.documentName
                                    )
                                BackupMirror.Status.REMOVED ->
                                    append("\nRemoved from the folder: ${outcome.documentName}")
                                BackupMirror.Status.FAILED ->
                                    append(
                                        "\nThe folder copy of ${outcome.documentName} did not go " +
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

    // The catch-up. Only reachable once a folder is picked — there is nothing to catch up to
    // otherwise — and it follows the Data section's own idiom: a clickable row with its result in
    // the supporting line, no dialog.
    if (folderUri != null) {
        val running = catchUp?.running == true
        val failedInLastRun = catchUp?.takeIf { !it.running }?.failed ?: 0
        ListItem(
            headlineContent = {
                Text(
                    "Mirror all now",
                    color = if (running) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
            },
            supportingContent = {
                Text(
                    mirrorAllStatusText(catchUp),
                    color =
                        if (failedInLastRun > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("backup_mirror_all")
                .clickable(enabled = !running) { BackupMirror.mirrorAllSavedDocuments(ctx) },
        )
    }
}

/**
 * The catch-up row's supporting line: what it is for before a run, live counts during one, and
 * the totals after. Failures are counted, never detailed — the per-document reasons are on the
 * IO log, and the point of this line is that the saved documents themselves are unaffected.
 */
private fun mirrorAllStatusText(catchUp: BackupMirror.CatchUp?): String = when {
    catchUp == null ->
        "Copy every saved shaft to the folder now — catches up anything saved before you picked it"
    catchUp.running ->
        "Mirroring… ${catchUp.mirrored + catchUp.failed} of ${catchUp.total}"
    catchUp.failed > 0 ->
        "Mirrored ${catchUp.mirrored} of ${catchUp.total} — ${catchUp.failed} did not go through. " +
            "Your saved shafts are fine."
    else -> "Mirrored ${catchUp.mirrored} of ${catchUp.total}"
}
