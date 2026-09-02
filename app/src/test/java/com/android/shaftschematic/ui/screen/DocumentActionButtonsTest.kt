package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared output-action trio.
 *
 * Print LEADS and is the only primary-weight button — the shop prints from the device and a
 * PDF file is the backup copy. Order is the part worth pinning: a build that puts Export
 * first, or gives it the filled treatment, has silently inverted the product ruling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class DocumentActionButtonsTest {

    @get:Rule
    val rule = createComposeRule()

    private var printed = 0
    private var previewed = 0
    private var exported = 0

    private fun host(name: String = "Runout Sheet", enabled: Boolean = true) {
        rule.setContent {
            MaterialTheme {
                Column {
                    DocumentActionButtons(
                        documentName = name,
                        onPrint = { printed++ },
                        onPreview = { previewed++ },
                        onExport = { exported++ },
                        enabled = enabled,
                    )
                }
            }
        }
    }

    @Test
    fun `all three actions render labelled with the document name`() {
        host(name = "Wear Document")

        rule.onNodeWithText("Print Wear Document").assertIsDisplayed()
        rule.onNodeWithText("Preview Wear Document").assertIsDisplayed()
        rule.onNodeWithText("Export Wear Document PDF").assertIsDisplayed()
    }

    @Test
    fun `Print leads, then Preview, then Export`() {
        host()

        val tops = listOf(
            DOC_ACTION_PRINT_TAG,
            DOC_ACTION_PREVIEW_TAG,
            DOC_ACTION_EXPORT_TAG,
        ).map { rule.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.y }

        assertTrue("Print must sit above Preview", tops[0] < tops[1])
        assertTrue("Preview must sit above Export", tops[1] < tops[2])
    }

    @Test
    fun `each button fires its own callback`() {
        host()

        rule.onNodeWithTag(DOC_ACTION_PRINT_TAG).performClick()
        rule.onNodeWithTag(DOC_ACTION_PREVIEW_TAG).performClick()
        rule.onNodeWithTag(DOC_ACTION_EXPORT_TAG).performClick()

        assertEquals(1, printed)
        assertEquals(1, previewed)
        assertEquals(1, exported)
    }

    @Test
    fun `enabled by default`() {
        host()

        rule.onNodeWithTag(DOC_ACTION_PRINT_TAG).assertIsEnabled()
        rule.onNodeWithTag(DOC_ACTION_PREVIEW_TAG).assertIsEnabled()
        rule.onNodeWithTag(DOC_ACTION_EXPORT_TAG).assertIsEnabled()
    }

    @Test
    fun `a closed export gate disables all three`() {
        host(enabled = false)

        rule.onNodeWithTag(DOC_ACTION_PRINT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(DOC_ACTION_PREVIEW_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(DOC_ACTION_EXPORT_TAG).assertIsNotEnabled()

        rule.onNode(hasTestTag(DOC_ACTION_PRINT_TAG)).performClick()
        assertEquals(0, printed)
    }
}
