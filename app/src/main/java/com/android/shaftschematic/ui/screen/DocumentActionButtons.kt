/**
 * The output-action trio every document tab carries: Print, Preview, Export.
 *
 * One construction behind the Runout, Wear, Undercut and Consolidated Output tabs, so the
 * order and the visual weight of the three actions cannot drift between them.
 */
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Test tags for the three actions, stable across every hosting tab. */
const val DOC_ACTION_PRINT_TAG = "doc_action_print"
const val DOC_ACTION_PREVIEW_TAG = "doc_action_preview"
const val DOC_ACTION_EXPORT_TAG = "doc_action_export"

/**
 * Print / Preview / Export for one document, full width and in that order.
 *
 * Print LEADS and is the only filled button: the shop works from paper and prints straight
 * from the device, so paper is the daily route and a PDF file is the backup copy. Preview
 * follows as the inspection step, and Export trails with the secondary outlined treatment.
 * Do not restore the filled Export button — it read as the primary output action.
 *
 * @param documentName The document's display name, e.g. "Runout Sheet". The three labels are
 *                     built from it ("Print <name>", "Preview <name>", "Export <name> PDF"),
 *                     so a tab cannot label one action differently from its siblings.
 * @param enabled      The hosting tab's export gate. All three actions share it — a document
 *                     that cannot be composed cannot be printed either. The gate's disabled
 *                     MESSAGE stays with the route: each tab words its own.
 */
@Composable
internal fun DocumentActionButtons(
    documentName: String,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onPrint,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(DOC_ACTION_PRINT_TAG),
    ) {
        Icon(Icons.Filled.Print, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Print $documentName")
    }

    OutlinedButton(
        onClick = onPreview,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(DOC_ACTION_PREVIEW_TAG),
    ) {
        Icon(Icons.Outlined.Preview, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Preview $documentName")
    }

    OutlinedButton(
        onClick = onExport,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(DOC_ACTION_EXPORT_TAG),
    ) {
        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Export $documentName PDF")
    }
}
