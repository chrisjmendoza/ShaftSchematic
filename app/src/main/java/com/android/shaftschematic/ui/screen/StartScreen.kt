// file: app/src/main/java/com/android/shaftschematic/ui/screen/StartScreen.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.doc.stripShaftDocExtension

/**
 * StartScreen
 *
 * Purpose
 * Simple hub with New, Open (internal), and Settings.
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
    onSendFeedback: () -> Unit,
    hasDraft: Boolean = false,
    onContinueDraft: (() -> Unit)? = null,
    onDiscardDraft: (() -> Unit)? = null,
    recentFiles: List<Pair<String, Long>> = emptyList(),
    onOpenRecent: ((String) -> Unit)? = null,
) {
    val nowMs = System.currentTimeMillis()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ShaftSchematic", style = MaterialTheme.typography.headlineMedium)

        val shownRecent = recentFiles.take(5)
        if (shownRecent.isNotEmpty() && onOpenRecent != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                shownRecent.forEachIndexed { idx, (filename, lastModMs) ->
                    if (idx > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRecent(filename) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stripShaftDocExtension(filename),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = relativeDate(nowMs, lastModMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New Drawing") }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open…") }
        if (hasDraft && onContinueDraft != null && onDiscardDraft != null) {
            Button(onClick = onContinueDraft, modifier = Modifier.fillMaxWidth()) {
                Text("Continue Draft")
            }
            OutlinedButton(onClick = onDiscardDraft, modifier = Modifier.fillMaxWidth()) {
                Text("Discard Draft")
            }
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        OutlinedButton(onClick = onSendFeedback, modifier = Modifier.fillMaxWidth()) { Text("Send Feedback") }
    }
}

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
