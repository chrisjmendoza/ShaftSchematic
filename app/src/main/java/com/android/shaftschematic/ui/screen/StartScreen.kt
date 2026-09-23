// file: app/src/main/java/com/android/shaftschematic/ui/screen/StartScreen.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.ui.adaptive.readableWidth

/**
 * StartScreen
 *
 * Purpose
 * Simple hub with New, Open (internal), Settings, and Help.
 *
 * Contract
 * - Emits navigation intents only.
 * - Does not touch ViewModel or storage directly.
 */
@Composable
fun StartScreen(
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    /** Open the Help & FAQ screen — a top-level entry, never only behind Settings. */
    onHelp: () -> Unit = {},
    onSendFeedback: () -> Unit,
    onOpenTemplates: () -> Unit = {},
    drafts: List<AutosaveManager.DraftEntry> = emptyList(),
    onContinueDraft: ((String) -> Unit)? = null,
    onDiscardDraft: ((String) -> Unit)? = null,
    recentFiles: List<Pair<String, Long>> = emptyList(),
    onOpenRecent: ((String) -> Unit)? = null,
) {
    val nowMs = System.currentTimeMillis()

    // Pending discard confirmation (destructive → confirm before removing).
    var pendingDiscardId by remember { mutableStateOf<String?>(null) }
    if (pendingDiscardId != null && onDiscardDraft != null) {
        val id = pendingDiscardId!!
        AlertDialog(
            onDismissRequest = { pendingDiscardId = null },
            title = { Text("Discard this draft?") },
            text = { Text("This unsaved draft will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscardId = null
                    onDiscardDraft(id)
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscardId = null }) { Text("Cancel") }
            }
        )
    }

    // Scrollable: with drafts + recents showing, the title and the last buttons overrun a
    // small phone's height, and a centered non-scrolling column would clip BOTH ends with no
    // way to reach Settings. CenterVertically still centers when the content fits.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ShaftSchematic", style = MaterialTheme.typography.headlineMedium)

        val shownDrafts = drafts.take(3)
        if (shownDrafts.isNotEmpty() && onContinueDraft != null && onDiscardDraft != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Unsaved drafts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    shownDrafts.forEachIndexed { idx, entry ->
                        if (idx > 0) HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        val title = draftRowTitle(entry)
                        // A row titled from the draft's own job info looks exactly like a saved
                        // file's row, so it says outright that it is not one — and carries its
                        // age on that same line, since the trailing age column would then
                        // print the time twice.
                        val titleIsDerived = savedDraftBaseName(entry) == null &&
                            title != UNTITLED_DRAFT_TITLE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContinueDraft(entry.draftId) }
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (titleIsDerived) {
                                    Text(
                                        text = "Unsaved draft · " +
                                            relativeAge(nowMs, entry.updatedAtEpochMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (!titleIsDerived) {
                                Text(
                                    text = relativeAge(nowMs, entry.updatedAtEpochMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDiscardId = entry.draftId }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Discard draft",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        val shownRecent = recentFiles.take(3)
        if (shownRecent.isNotEmpty() && onOpenRecent != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    shownRecent.forEachIndexed { idx, (filename, lastModMs) ->
                        if (idx > 0) HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecent(filename) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stripShaftDocExtension(filename),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = relativeDate(nowMs, lastModMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New Drawing") }
        Button(
            onClick = onOpenTemplates,
            modifier = Modifier.fillMaxWidth().testTag("start_templates_button"),
        ) { Text("Start from Template") }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open…") }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        OutlinedButton(
            onClick = onHelp,
            modifier = Modifier.fillMaxWidth().testTag("start_help_button"),
        ) { Text("Help & FAQ") }
        OutlinedButton(onClick = onSendFeedback, modifier = Modifier.fillMaxWidth()) { Text("Send Feedback") }
    }
}

/** Primary text for a draft that carries neither a saved name nor any project information. */
const val UNTITLED_DRAFT_TITLE = "Untitled draft"

/**
 * Primary text for one "Unsaved drafts" row.
 *
 * A draft the user never saved still holds the job number / customer / vessel they typed, so the
 * row is named the way the save screen would name the file — three rows reading "Untitled draft"
 * can only be told apart by their age, which is not how anyone remembers a shaft. The saved name
 * wins when there is one (the draft was opened from a file), and the literal placeholder is the
 * last resort for a draft with nothing to say about itself.
 *
 * Pure, so the branches are asserted without a Compose harness. This NAMES a row; it never
 * writes a name anywhere — the draft stays unsaved and unnamed until the user saves it.
 */
fun draftRowTitle(entry: AutosaveManager.DraftEntry): String {
    savedDraftBaseName(entry)?.let { return it }

    val snapshot = entry.snapshot
    return DocumentNaming.suggestedBaseName(
        jobNumber = snapshot.jobNumber,
        customer = snapshot.customer,
        vessel = snapshot.vessel,
    ) ?: UNTITLED_DRAFT_TITLE
}

/** The file name this draft was opened from, extension stripped, or null for a new drawing. */
private fun savedDraftBaseName(entry: AutosaveManager.DraftEntry): String? =
    entry.documentName?.let(::stripShaftDocExtension)?.takeIf { it.isNotBlank() }

/** Coarse "time since" for saved files (day granularity is enough for these). */
private fun relativeDate(nowMs: Long, lastModifiedMs: Long): String {
    val days = ((nowMs - lastModifiedMs) / (1000L * 60 * 60 * 24)).toInt()
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

/** Finer "time since" for drafts (minutes/hours matter for recent autosaves). */
private fun relativeAge(nowMs: Long, thenMs: Long): String {
    val diffMs = (nowMs - thenMs).coerceAtLeast(0L)
    val minutes = diffMs / (1000L * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days < 7 -> "$days d ago"
        days < 30 -> "${days / 7} w ago"
        else -> "${days / 30} mo ago"
    }
}
