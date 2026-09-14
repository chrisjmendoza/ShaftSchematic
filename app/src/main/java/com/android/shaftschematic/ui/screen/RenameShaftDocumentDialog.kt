// file: app/src/main/java/com/android/shaftschematic/ui/screen/RenameShaftDocumentDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.io.InternalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Test tag on the rename dialog's name field. */
const val RENAME_DOC_FIELD_TAG = "rename_doc_field"

/** Test tag on the rename dialog's confirm button. */
const val RENAME_DOC_CONFIRM_TAG = "rename_doc_confirm"

/** Reported through `onError` when the typed name holds no usable filename. */
const val RENAME_BLANK_NAME_MESSAGE = "Name cannot be blank."

/**
 * What confirming the rename dialog means, decided from the typed text ALONE — before any
 * storage is touched. Two of the three outcomes never reach the disk at all, which is why this
 * is separated out: the rules that decide them are the ones shared between the Open screen and
 * the editor's title strip, and they are asserted without a Compose harness.
 */
sealed interface RenameOutcome {
    /** Nothing usable was typed — refuse, and leave the dialog open over the name to fix. */
    data object Blank : RenameOutcome

    /** The typed name normalizes back to the current one; there is nothing to rename. */
    data object Unchanged : RenameOutcome

    /** [toName] is a different, valid document filename — the rename may be attempted. */
    data class Proceed(val toName: String) : RenameOutcome
}

/**
 * Decides [RenameOutcome] for a name typed over [fromName].
 *
 * The comparison is case-insensitive because the document store treats "Job 12" and "job 12"
 * as one file: a rename that differed only in case would be a rename onto itself, which the
 * store refuses, so it is reported as unchanged rather than as a failure.
 */
fun renameOutcomeFor(fromName: String, typed: String): RenameOutcome {
    val toName = InternalStorage.normalizeShaftDocName(sanitizeUserBaseName(typed))
        ?: return RenameOutcome.Blank

    if (toName.equals(fromName, ignoreCase = true)) return RenameOutcome.Unchanged

    return RenameOutcome.Proceed(toName)
}

/**
 * Renames a saved shaft document in app storage.
 *
 * Shared by the Open screen's per-file "Rename" menu item and the editor's title-strip tap, so
 * the two surfaces cannot drift on the rules that matter: a blank name is refused, a name that
 * normalizes to the current one is a no-op, and an existing file is **never overwritten** — the
 * same posture as the post-save rename offer.
 *
 * The dialog owns the typed name and the storage work; the caller owns the consequences, which
 * differ per surface (the Open screen refreshes its list, the editor updates the session's
 * document name so the title strip follows). Each outcome is terminal for its path:
 * [onRenamed] and [onDismiss] mean the dialog is finished and the caller closes it, while
 * [onError] leaves it open over the name the user still has to fix.
 */
@Composable
fun RenameShaftDocumentDialog(
    fromName: String,
    onDismiss: () -> Unit,
    onRenamed: (toName: String) -> Unit,
    onError: (message: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Seeded selected so the first keystroke replaces the old name rather than appending to it.
    var value by remember(fromName) {
        val base = stripShaftDocExtension(fromName)
        mutableStateOf(TextFieldValue(base, selection = TextRange(0, base.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename saved shaft") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a new name. The file will be saved as $SHAFT_DOT_EXT.")
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RENAME_DOC_FIELD_TAG),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val outcome = renameOutcomeFor(fromName, value.text)) {
                        RenameOutcome.Blank -> onError(RENAME_BLANK_NAME_MESSAGE)

                        RenameOutcome.Unchanged -> onDismiss()

                        is RenameOutcome.Proceed -> {
                            val toName = outcome.toName
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    // A taken name reports failure rather than replacing the
                                    // file behind it: a rename that silently destroyed another
                                    // saved shaft would be unrecoverable.
                                    if (InternalStorage.exists(ctx, toName)) {
                                        return@withContext false
                                    }
                                    InternalStorage.rename(ctx, fromName, toName)
                                }
                                if (ok) {
                                    onRenamed(toName)
                                } else {
                                    onError(
                                        "Could not rename to ‘${stripShaftDocExtension(toName)}’."
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.testTag(RENAME_DOC_CONFIRM_TAG),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Strips what a filename cannot carry — path separators, the Windows-reserved punctuation, and
 * control characters — and collapses runs of whitespace. Returns "" for a name with nothing
 * usable left, which `InternalStorage.normalizeShaftDocName` then refuses.
 */
private fun sanitizeUserBaseName(raw: String): String {
    val collapsed = raw.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isEmpty()) return ""

    return collapsed
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\u0000-\\u001F]"), "")
        .trim()
}
