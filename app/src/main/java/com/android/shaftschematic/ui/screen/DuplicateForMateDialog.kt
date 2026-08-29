package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.doc.matePosition
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.util.DocumentNaming

/**
 * The name a mate is offered under: the source's project information re-run through the
 * document namer with the OPPOSITE side as its suffix, so the twin of "J-1 - Acme - PORT"
 * proposes "J-1 - Acme - STBD" with nothing typed. A document with no project information to
 * build from falls back to its own name — the suffix alone is never a filename
 * (`DocumentNaming.suggestedBaseName`), and two files may not share a name, so the result is
 * always numbered clear of [existingBaseNames].
 *
 * [matePosition] is the ALREADY-flipped side, so the seed and the dialog's side dropdown
 * cannot disagree about which shaft this copy is.
 */
internal fun mateNameSeed(
    sourceBaseName: String,
    jobNumber: String,
    customer: String,
    vessel: String,
    matePosition: ShaftPosition,
    existingBaseNames: Collection<String>,
): String {
    val seed = DocumentNaming.suggestedBaseName(
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        suffix = matePosition.printableLabelOrNull(),
    ) ?: "$sourceBaseName (mate)"
    return DocumentNaming.uniqueBaseName(existingBaseNames, seed)
}

/**
 * The `.shaft` filename [typed] would be saved as, or null when the mate may not be created:
 * a name that normalizes to nothing, or one already taken in [existingBaseNames].
 *
 * Null is what disables Create. The name goes through the save path's own
 * [InternalStorage.normalizeShaftDocName], so what the button allows is exactly what can be
 * written; the collision check is case-insensitive because the store is — a duplicate must
 * never overwrite the document it was copied from.
 */
internal fun mateFileNameOrNull(typed: String, existingBaseNames: Collection<String>): String? {
    val fileName = InternalStorage.normalizeShaftDocName(typed) ?: return null
    val base = stripShaftDocExtension(fileName)
    if (existingBaseNames.any { it.equals(base, ignoreCase = true) }) return null
    return fileName
}

/**
 * "Duplicate for mate" — names the copy and adjusts its identity before it is created.
 *
 * Shared by the Open screen's row menu and the editor's overflow menu, so a mate is named the
 * same way whichever surface starts it. The dialog owns only its draft fields; the caller owns
 * the document it duplicates and the write.
 *
 * Every field is seeded from the source, since a mate differs from its twin in very little:
 * the shaft side flips ([matePosition]) and the name follows it, which is usually the whole
 * edit. Create is refused when the name would not make a file, or would land on one that
 * already exists — a duplicate must never overwrite the document it was copied from.
 */
@Composable
internal fun DuplicateForMateDialog(
    /** Base name (no extension) of the document being duplicated — the name-seed fallback. */
    sourceBaseName: String,
    jobNumber: String,
    customer: String,
    vessel: String,
    position: ShaftPosition,
    /** Base names (no extension) already in the document store; drives the uniqueness check. */
    existingBaseNames: Collection<String>,
    onDismiss: () -> Unit,
    /**
     * Create the mate. [fileName] is the normalized `.shaft` filename the dialog validated;
     * the remaining arguments are the identity the copy takes.
     */
    onCreate: (
        fileName: String,
        jobNumber: String,
        customer: String,
        vessel: String,
        position: ShaftPosition,
    ) -> Unit,
) {
    val seededPosition = remember(position) { matePosition(position) }

    var job by remember { mutableStateOf(TextFieldValue(jobNumber)) }
    var cust by remember { mutableStateOf(TextFieldValue(customer)) }
    var ves by remember { mutableStateOf(TextFieldValue(vessel)) }
    var side by remember { mutableStateOf(seededPosition) }

    // Seeded once, then the user's to edit: re-deriving it under a later side or job change
    // would overwrite a name they had already typed.
    var name by remember {
        val seed = mateNameSeed(
            sourceBaseName = sourceBaseName,
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            matePosition = seededPosition,
            existingBaseNames = existingBaseNames,
        )
        mutableStateOf(TextFieldValue(seed, TextRange(seed.length)))
    }

    val fileName = mateFileNameOrNull(name.text, existingBaseNames)
    val blankName = InternalStorage.normalizeShaftDocName(name.text) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("mate_dialog"),
        title = { Text("Duplicate for mate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = fileName == null,
                    supportingText = {
                        when {
                            blankName -> Text("Enter a name.")
                            fileName == null -> Text("A drawing with that name already exists.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("mate_name"),
                )
                OutlinedTextField(
                    value = job,
                    onValueChange = { job = it },
                    label = { Text("Job Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mate_job_number"),
                )
                OutlinedTextField(
                    value = cust,
                    onValueChange = { cust = it },
                    label = { Text("Customer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mate_customer"),
                )
                OutlinedTextField(
                    value = ves,
                    onValueChange = { ves = it },
                    label = { Text("Vessel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mate_vessel"),
                )
                ShaftPositionDropdown(
                    selected = side,
                    onSelected = { side = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The copy keeps the shaft's geometry and drawing settings. Runout, wear " +
                        "and undercut measurements stay with the shaft they were taken on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(fileName ?: return@TextButton, job.text, cust.text, ves.text, side) },
                enabled = fileName != null,
                modifier = Modifier.testTag("mate_create"),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
