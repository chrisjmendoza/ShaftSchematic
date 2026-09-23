package com.android.shaftschematic.ui.screen

import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.junit.Assert.assertFalse
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

    /* ── The state is an AGGREGATE over every field on the card ──────────────── */

    @Composable
    private fun TwoFieldHost() {
        MaterialTheme {
            ComponentCard(title = "Body") {
                CommitNum(label = "KW W", initialDisplay = "0", modifier = Modifier.testTag(FIELD)) {
                    commits += "A=$it"
                }
                CommitNum(label = "KW D", initialDisplay = "0", modifier = Modifier.testTag(FIELD_B)) {
                    commits += "B=$it"
                }
            }
        }
    }

    @Test
    fun `save tracks every field, not just the last one to report`() {
        rule.setContent { TwoFieldHost() }

        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(FIELD).performTextReplacement("6.5")
        rule.waitForIdle()
        rule.onNodeWithTag(SAVE).assertIsEnabled()

        // Moving to B blurs A, which commits it — but B is now the one holding an edit, so the
        // button must stay lit. A registry that tracked one flag instead of one per field would
        // have gone dark on A's clean report.
        rule.onNodeWithTag(FIELD_B).requestFocus()
        rule.onNodeWithTag(FIELD_B).performTextReplacement("3.25")
        rule.waitForIdle()
        assertEquals("only A has landed so far", listOf("A=6.5"), commits)
        rule.onNodeWithTag(SAVE).assertIsEnabled()

        rule.onNodeWithTag(SAVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf("A=6.5", "B=3.25"), commits)
        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    /* ── A field leaving composition drops its claim ─────────────────────────── */

    /** Hoisted so the field can be removed WITHOUT a tap that would blur and commit it first. */
    private var showSecondField by mutableStateOf(true)

    @Composable
    private fun DisappearingFieldHost() {
        MaterialTheme {
            ComponentCard(title = "Body") {
                CommitNum(label = "KW W", initialDisplay = "0", modifier = Modifier.testTag(FIELD)) {
                    commits += "A=$it"
                }
                if (showSecondField) {
                    CommitNum(label = "KW D", initialDisplay = "0", modifier = Modifier.testTag(FIELD_B)) {
                        commits += "B=$it"
                    }
                }
            }
        }
    }

    @Test
    fun `a dirty field leaving composition lands its edit and greys save back out`() {
        // Nothing may be left holding a claim on behalf of a field that is gone — the regression
        // the registry's `forget` guards, where a departed field kept Save lit forever. Losing
        // focus on the way out is also what lands the value, so the edit is not dropped either.
        rule.setContent { DisappearingFieldHost() }

        rule.onNodeWithTag(FIELD_B).requestFocus()
        rule.onNodeWithTag(FIELD_B).performTextReplacement("3.25")
        rule.waitForIdle()
        rule.onNodeWithTag(SAVE).assertIsEnabled()

        showSecondField = false
        rule.waitForIdle()

        assertEquals("the departing field's edit still lands", listOf("B=3.25"), commits)
        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    /* ── Instant-commit controls never register ──────────────────────────────── */

    @Composable
    private fun CheckboxHost() {
        var checked by remember { mutableStateOf(false) }
        MaterialTheme {
            ComponentCard(title = "Body") {
                CommitNum(label = "KW L", initialDisplay = "0", modifier = Modifier.testTag(FIELD)) {
                    commits += it
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.testTag(CHECK),
                )
            }
        }
    }

    @Test
    fun `toggling a checkbox leaves save disabled`() {
        // Chips, checkboxes and switches commit the instant they are tapped — they have no
        // uncommitted state to report, so they must not light the button they are not waiting on.
        rule.setContent { CheckboxHost() }

        rule.onNodeWithTag(CHECK).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    /* ── The registry itself ─────────────────────────────────────────────────── */

    @Test
    fun `CardDirtyState is pending while any token is dirty`() {
        val state = CardDirtyState()
        val a = Any()
        val b = Any()

        assertFalse("a fresh card has nothing pending", state.hasPendingEdits)

        state.setDirty(a, true)
        assertTrue(state.hasPendingEdits)

        state.setDirty(b, true)
        state.setDirty(a, false)
        assertTrue("b is still pending", state.hasPendingEdits)

        state.setDirty(b, false)
        assertFalse(state.hasPendingEdits)
    }

    @Test
    fun `CardDirtyState forgets a departed field's claim`() {
        val state = CardDirtyState()
        val token = Any()

        state.setDirty(token, true)
        state.forget(token)

        assertFalse("a field that left composition holds nothing", state.hasPendingEdits)
    }

    private companion object {
        const val FIELD = "kw_l_field"
        const val FIELD_B = "kw_d_field"
        const val CHECK = "card_checkbox"
        const val SAVE = "card_save_button"
    }
}
