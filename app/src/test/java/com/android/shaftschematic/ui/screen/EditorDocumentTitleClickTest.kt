package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The title strip's tap affordance. The formatting lives in [EditorDocumentTitleTest] (pure);
 * what needs a Compose harness is that the strip is clickable only when a handler is given —
 * an inert strip must not announce a click action a screen reader would offer and nothing
 * would answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class EditorDocumentTitleClickTest {

    @get:Rule
    val rule = createComposeRule()

    private var taps = 0

    @Test
    fun `a strip with a handler is clickable and reports the tap`() {
        rule.setContent {
            MaterialTheme {
                EditorDocumentTitle(
                    documentName = "Job 4471 Shaft.shaft",
                    hasUnsavedChanges = false,
                    onClick = { taps++ },
                )
            }
        }

        rule.onNodeWithTag(EDITOR_DOCUMENT_TITLE_TAG).assertHasClickAction()
        rule.onNodeWithTag(EDITOR_DOCUMENT_TITLE_TAG).performClick()
        rule.waitForIdle()

        assertEquals(1, taps)
    }

    @Test
    fun `an untitled draft strip is tappable too`() {
        // Naming a document that has never been saved is exactly the case the tap exists for,
        // so the strip must not go inert just because there is no name to show.
        rule.setContent {
            MaterialTheme {
                EditorDocumentTitle(
                    documentName = null,
                    hasUnsavedChanges = true,
                    onClick = { taps++ },
                )
            }
        }

        rule.onNodeWithTag(EDITOR_DOCUMENT_TITLE_TAG).performClick()
        rule.waitForIdle()

        assertEquals(1, taps)
    }

    @Test
    fun `a strip with no handler carries no click action`() {
        rule.setContent {
            MaterialTheme {
                EditorDocumentTitle(
                    documentName = "Job 4471 Shaft.shaft",
                    hasUnsavedChanges = false,
                    onClick = null,
                )
            }
        }

        rule.onNodeWithTag(EDITOR_DOCUMENT_TITLE_TAG).assertHasNoClickAction()
    }

    @Test
    fun `the handler defaults to absent`() {
        // The parameter is optional, so a call site that has not been wired must stay inert
        // rather than pick up a click action it never asked for.
        rule.setContent {
            MaterialTheme {
                EditorDocumentTitle(
                    documentName = "Job 4471 Shaft.shaft",
                    hasUnsavedChanges = false,
                )
            }
        }

        rule.onNodeWithTag(EDITOR_DOCUMENT_TITLE_TAG).assertHasNoClickAction()
    }
}
