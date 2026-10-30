package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import com.android.shaftschematic.ui.input.FIELD_ACCEPT_TAG
import com.android.shaftschematic.ui.input.FIELD_CANCEL_TAG
import com.android.shaftschematic.ui.input.NumericInputField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card fields' ✓ / ✗ pair — the Overall Length field's own control, shown beside a card
 * field while it holds an edit that has not landed (on-device request: the Save button at the
 * foot of a long card was hard to find).
 *
 * Fields commit on blur and IME Done, but chips, toggles and checkboxes never take focus, so a
 * typed value followed by a chip tap sits uncommitted in a still-focused field (on-device report:
 * a body keyway length that never landed). The pair stays beside that field until it is
 * answered: ✓ lands the value through the field's own commit, ✗ puts the stored value back and
 * commits nothing. These pin that wiring, that the pair shows only while there is an edit, and
 * that neither answer is followed by a second commit from the blur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ComponentCardAcceptCancelTest {

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
                    validator = { raw -> if (raw.trim() == "9") "too long" else null },
                ) { commits += it }
            }
        }
    }

    private fun typeInField(value: String, tag: String = FIELD) {
        rule.onNodeWithTag(tag).requestFocus()
        rule.onNodeWithTag(tag).performTextReplacement(value)
        rule.waitForIdle()
    }

    @Test
    fun `accept commits the still-focused field's typed value`() {
        rule.setContent { Host() }
        typeInField("6.5")
        // No blur, no IME Done — the chip-tap scenario. ✓ must land the value.
        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
    }

    @Test
    fun `accept is followed by no second commit when the field is left again`() {
        rule.setContent { Host() }
        typeInField("6.5")
        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        // Out of touch mode a cleared focus can come straight back to the field; going in and out
        // again must find nothing new to commit.
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
    }

    @Test
    fun `cancel puts the stored value back and commits nothing`() {
        rule.setContent { Host() }
        typeInField("6.5")
        rule.onNodeWithTag(FIELD_CANCEL_TAG).performClick()
        rule.waitForIdle()
        assertTrue("✗ must not commit", commits.isEmpty())
        rule.onNodeWithTag(FIELD).assertTextEquals("KW L", "0")
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    @Test
    fun `the pair is absent while nothing is typed`() {
        rule.setContent { Host() }
        rule.waitForIdle()
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
        rule.onAllNodesWithTag(FIELD_CANCEL_TAG).assertCountEquals(0)
    }

    /** Tapping into a field and leaving it alone is not a pending edit. */
    @Test
    fun `focus alone does not show the pair`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.waitForIdle()
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    @Test
    fun `the pair appears on an edit and goes once the edit lands`() {
        rule.setContent { Host() }
        typeInField("6.5")
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(1)
        rule.onAllNodesWithTag(FIELD_CANCEL_TAG).assertCountEquals(1)
        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    @Test
    fun `accepting a refused value names it, commits nothing, and drops the pair`() {
        rule.setContent { Host() }
        typeInField("9")
        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        assertTrue(commits.isEmpty())
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
        rule.onNodeWithTag(FIELD).assertTextEquals("KW L", "0", "9 not applied — too long")
    }

    /* ── One pair, beside the field being edited ─────────────────────────────── */

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
    fun `only the edited field shows the pair, and moving on lands the first edit`() {
        rule.setContent { TwoFieldHost() }

        typeInField("6.5")
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(1)

        // Moving to B blurs A, which commits it; B then carries the only pair.
        typeInField("3.25", FIELD_B)
        assertEquals("only A has landed so far", listOf("A=6.5"), commits)
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(1)

        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("A=6.5", "B=3.25"), commits)
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    /* ── A field leaving composition still lands its edit ────────────────────── */

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
    fun `a dirty field leaving composition lands its edit and takes its pair with it`() {
        rule.setContent { DisappearingFieldHost() }

        typeInField("3.25", FIELD_B)
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(1)

        showSecondField = false
        rule.waitForIdle()

        assertEquals("the departing field's edit still lands", listOf("B=3.25"), commits)
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    /* ── Instant-commit controls ─────────────────────────────────────────────── */

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
    fun `toggling a checkbox shows no pair`() {
        // Chips, checkboxes and switches commit the instant they are tapped — there is nothing
        // for a ✓ to accept.
        rule.setContent { CheckboxHost() }
        rule.onNodeWithTag(CHECK).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    @Test
    fun `after a chip tap the pair stays beside the value that has not landed`() {
        rule.setContent { CheckboxHost() }
        typeInField("6.5")
        rule.onNodeWithTag(CHECK).performClick()
        rule.waitForIdle()
        assertTrue("a checkbox tap never takes focus, so nothing has landed", commits.isEmpty())
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(1)
        rule.onNodeWithTag(FIELD_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
    }

    /* ── Outside a card the field is unchanged ───────────────────────────────── */

    @Test
    fun `a numeric field outside a card shows no pair`() {
        // Add dialogs commit through their own Add button; the undercut and wear editors keep
        // their own controls.
        rule.setContent {
            MaterialTheme {
                Column {
                    NumericInputField(
                        label = "Length",
                        initialText = "0",
                        modifier = Modifier.testTag(FIELD),
                        parseValid = { it.toFloatOrNull() != null },
                        onCommit = { commits += it },
                    )
                }
            }
        }
        typeInField("6.5")
        rule.onAllNodesWithTag(FIELD_ACCEPT_TAG).assertCountEquals(0)
    }

    private companion object {
        const val FIELD = "kw_l_field"
        const val FIELD_B = "kw_d_field"
        const val CHECK = "card_checkbox"
    }
}
