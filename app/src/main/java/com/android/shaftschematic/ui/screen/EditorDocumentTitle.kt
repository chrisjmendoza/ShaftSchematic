// file: app/src/main/java/com/android/shaftschematic/ui/screen/EditorDocumentTitle.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.doc.stripShaftDocExtension

/** Test tag on the document title strip, identical on every editor tab. */
const val EDITOR_DOCUMENT_TITLE_TAG = "editor_document_title"

/**
 * Formats the editor's document title: the saved file name (extension stripped) or
 * "Untitled draft", with a trailing asterisk while the session differs from the last
 * saved/loaded baseline.
 *
 * Pure so the string can be asserted without a Compose harness.
 */
fun editorDocumentTitleText(documentName: String?, hasUnsavedChanges: Boolean): String =
    buildString {
        append(documentName?.let(::stripShaftDocExtension) ?: "Untitled draft")
        if (hasUnsavedChanges) append(" *")
    }

/**
 * EditorDocumentTitle — desktop-editor style document title strip.
 *
 * Sits above each editor tab's action bar so the saved-vs-draft state is visible from
 * every document view, not just the Schematic. The dirty flag behind [hasUnsavedChanges]
 * (`ShaftViewModel.hasUnsavedChanges`) compares a full session snapshot, so runout
 * readings/station counts, wear records, and undercuts raise the asterisk exactly like a
 * spec edit does.
 *
 * The strip applies no window insets of its own — the caller owns them. `ShaftScreen`
 * passes a status-bar inset modifier (its `TopAppBar` then zeroes its own insets); the
 * other tabs already sit inside a `systemBarsPadding()` column and pass nothing.
 */
@Composable
fun EditorDocumentTitle(
    documentName: String?,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = editorDocumentTitleText(documentName, hasUnsavedChanges),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .testTag(EDITOR_DOCUMENT_TITLE_TAG),
    )
}
