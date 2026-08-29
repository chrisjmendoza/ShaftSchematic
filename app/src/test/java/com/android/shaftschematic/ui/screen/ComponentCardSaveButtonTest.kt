package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card's Save button — the explicit commit affordance for a still-focused field.
 *
 * Fields commit on blur and IME Done, but chips/toggles/checkboxes never take focus, so a
 * typed value followed by a chip tap sat uncommitted with nothing visibly wrong (on-device
 * report: a body keyway length that never landed). Save force-clears focus and rides the
 * ONE existing commit path; these pin that the wiring holds — Save after typing commits the
 * value, and Save with no edit stays a no-op (`shouldCommitOnBlur`'s no-change rule).
 *
 * They also pin the button's ENABLED state, which is the visible half of the same statement
 * (on-device request): greyed out means every field on this card is committed, filled means
 * something is waiting. The state comes from the fields themselves through `CardDirtyState`,
 * with no per-call-site wiring, so these cover the registration path too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ComponentCardSaveButtonTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()

    @Composable
    private fun Host() {
        MaterialTheme {
            ComponentCard(title = "Body") {
                CommitNum(
                    label = "KW L",
                    initialDisplay = "0",
                    modifier = Modifier.testTag(FIELD),
                ) { commits += it }
            }
        }
    }

    @Test
    fun `save commits the still-focused field's typed value`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(FIELD).performTextReplacement("6.5")
        // No blur, no IME Done — the chip-tap scenario. Save must land the value.
        rule.onNodeWithTag(SAVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
    }

    @Test
    fun `save with no edit is a no-op`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(SAVE).performClick()
        rule.waitForIdle()
        assertTrue("tap-and-save with no edit must not commit", commits.isEmpty())
    }

    @Test
    fun `save with nothing focused is a no-op`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(SAVE).performClick()
        rule.waitForIdle()
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `save is disabled while every field is committed`() {
        rule.setContent { Host() }
        rule.waitForIdle()
        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    @Test
    fun `save enables as soon as a field holds an uncommitted edit`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(FIELD).performTextReplacement("6.5")
        rule.waitForIdle()
        rule.onNodeWithTag(SAVE).assertIsEnabled()
    }

    @Test
    fun `save greys back out once the edit lands`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(FIELD).performTextReplacement("6.5")
        rule.onNodeWithTag(SAVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    /** Tapping into a field and leaving it alone is not a pending edit. */
    @Test
    fun `focus alone does not enable save`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    private companion object {
        const val FIELD = "kw_l_field"
        const val SAVE = "card_save_button"
    }
}
