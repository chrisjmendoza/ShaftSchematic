package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card Save / Cancel bar: while a carousel card field holds a typed value that has not
 * landed, the Overall Length row shows the bar — the OAL's own ✓ / ✗ in the OAL's own slot,
 * naming the waiting field — in the field's place, and the OAL field returns once the card is
 * clean (on-device ruling: a Save button at the end of a long card was hidden; the OAL row is
 * always on screen).
 *
 * Fields commit on blur and IME Done, but chips, toggles and checkboxes never take focus, so a
 * typed value followed by a chip tap sits uncommitted in a still-focused field (on-device report:
 * a body keyway length that never landed). The bar stays until it is answered: ✓ lands the value
 * through the field's own commit, ✗ puts the stored value back and commits nothing. These pin
 * the swap, that the bar shows only while there is an edit, and that neither answer is followed
 * by a second commit from the blur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class CardSaveBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()

    @Composable
    private fun Editor(card: @Composable () -> Unit) {
        MaterialTheme {
            CompositionLocalProvider(LocalPendingCardEdits provides remember { PendingCardEdits() }) {
                Column {
                    OverallLengthRow(
                        spec = ShaftSpec(overallLengthMm = 2540f),
                        unit = UnitSystem.MILLIMETERS,
                        onSetOverallLengthMm = {},
                        onSetOverallLengthRaw = {},
                    )
                    card()
                }
            }
        }
    }

    @Composable
    private fun Host() {
        Editor {
            ComponentCard(title = "Body 1") {
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

    private fun assertOalShowing() {
        rule.onAllNodesWithTag(OAL_FIELD_TAG).assertCountEquals(1)
        rule.onAllNodesWithTag(CARD_SAVE_BAR_TAG).assertCountEquals(0)
    }

    private fun assertBarShowing() {
        rule.onAllNodesWithTag(OAL_FIELD_TAG).assertCountEquals(0)
        rule.onAllNodesWithTag(CARD_SAVE_BAR_TAG).assertCountEquals(1)
    }

    /* ── The swap ────────────────────────────────────────────────────────────── */

    @Test
    fun `at rest the row is the OAL field`() {
        rule.setContent { Host() }
        rule.waitForIdle()
        assertOalShowing()
    }

    /** Tapping into a field and leaving it alone is not a pending edit. */
    @Test
    fun `focus alone does not bring up the bar`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.waitForIdle()
        assertOalShowing()
    }

    @Test
    fun `an edit swaps the OAL field for the bar, naming the field and its card`() {
        rule.setContent { Host() }
        typeInField("6.5")
        assertBarShowing()
        val inBar = hasAnyAncestor(hasTestTag(CARD_SAVE_BAR_TAG))
        rule.onAllNodes(inBar and hasText("KW L", substring = true)).assertCountEquals(1)
        rule.onAllNodes(inBar and hasText("Body 1", substring = true)).assertCountEquals(1)
        assertTrue("an edit alone commits nothing", commits.isEmpty())
    }

    @Test
    fun `save commits the still-focused value once and brings the OAL back`() {
        rule.setContent { Host() }
        typeInField("6.5")
        // No blur, no IME Done — the chip-tap scenario. ✓ must land the value.
        rule.onNodeWithTag(CARD_SAVE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
        assertOalShowing()
        // Out of touch mode a cleared focus can come straight back to the field; going in and out
        // again must find nothing new to commit.
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag(OAL_FIELD_TAG).requestFocus()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
    }

    @Test
    fun `cancel puts the stored value back, commits nothing, and brings the OAL back`() {
        rule.setContent { Host() }
        typeInField("6.5")
        rule.onNodeWithTag(CARD_CANCEL_TAG).performClick()
        rule.waitForIdle()
        assertTrue("✗ must not commit", commits.isEmpty())
        rule.onNodeWithTag(FIELD).assertTextEquals("KW L", "0")
        assertOalShowing()
    }

    @Test
    fun `saving a refused value names it, commits nothing, and drops the bar`() {
        rule.setContent { Host() }
        typeInField("9")
        rule.onNodeWithTag(CARD_SAVE_TAG).performClick()
        rule.waitForIdle()
        assertTrue(commits.isEmpty())
        assertOalShowing()
        rule.onNodeWithTag(FIELD).assertTextEquals("KW L", "0", "9 not applied — too long")
    }

    /* ── One bar for the field being edited ──────────────────────────────────── */

    @Test
    fun `two fields share one bar, and moving on lands the first edit`() {
        rule.setContent {
            Editor {
                ComponentCard(title = "Body 1") {
                    CommitNum(label = "KW W", initialDisplay = "0", modifier = Modifier.testTag(FIELD)) {
                        commits += "A=$it"
                    }
                    CommitNum(label = "KW D", initialDisplay = "0", modifier = Modifier.testTag(FIELD_B)) {
                        commits += "B=$it"
                    }
                }
            }
        }

        typeInField("6.5")
        assertBarShowing()

        // Moving to B blurs A, which commits it; B then holds the only pending edit.
        typeInField("3.25", FIELD_B)
        assertEquals("only A has landed so far", listOf("A=6.5"), commits)
        assertBarShowing()
        rule.onAllNodes(hasAnyAncestor(hasTestTag(CARD_SAVE_BAR_TAG)) and hasText("KW D", substring = true))
            .assertCountEquals(1)

        rule.onNodeWithTag(CARD_SAVE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("A=6.5", "B=3.25"), commits)
        assertOalShowing()
    }

    /* ── A field leaving composition still lands its edit ────────────────────── */

    /** Hoisted so the field can be removed WITHOUT a tap that would blur and commit it first. */
    private var showSecondField by mutableStateOf(true)

    @Test
    fun `a dirty field leaving composition lands its edit and the bar goes`() {
        rule.setContent {
            Editor {
                ComponentCard(title = "Body 1") {
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

        typeInField("3.25", FIELD_B)
        assertBarShowing()

        showSecondField = false
        rule.waitForIdle()

        assertEquals("the departing field's edit still lands", listOf("B=3.25"), commits)
        assertOalShowing()
    }

    /* ── Instant-commit controls ─────────────────────────────────────────────── */

    @Composable
    private fun CheckboxHost() {
        var checked by remember { mutableStateOf(false) }
        Editor {
            ComponentCard(title = "Body 1") {
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
    fun `toggling a checkbox brings up no bar`() {
        // Chips, checkboxes and switches commit the instant they are tapped — there is nothing
        // for a ✓ to save.
        rule.setContent { CheckboxHost() }
        rule.onNodeWithTag(CHECK).performClick()
        rule.waitForIdle()
        assertOalShowing()
    }

    @Test
    fun `after a chip tap the bar stays for the value that has not landed`() {
        rule.setContent { CheckboxHost() }
        typeInField("6.5")
        rule.onNodeWithTag(CHECK).performClick()
        rule.waitForIdle()
        assertTrue("a checkbox tap never takes focus, so nothing has landed", commits.isEmpty())
        assertBarShowing()
        rule.onNodeWithTag(CARD_SAVE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
        assertOalShowing()
    }

    /* ── Naming ──────────────────────────────────────────────────────────────── */

    @Test
    fun `field labels drop their unit suffix in prose`() {
        assertEquals("Length", fieldLabelWithoutUnit("Length (in)"))
        assertEquals("Count", fieldLabelWithoutUnit("Count"))
        assertEquals("Start from AFT", fieldLabelWithoutUnit("Start from AFT (in)"))
    }

    @Test
    fun `a pending edit is named by its field and its card`() {
        assertEquals(
            "“Length” on AFT Taper",
            pendingEditName(PendingCardEdit("AFT Taper", "Length (in)", { true }, {})),
        )
        assertEquals("“KW L”", pendingEditName(PendingCardEdit("", "KW L", { true }, {})))
    }

    private companion object {
        const val FIELD = "kw_l_field"
        const val FIELD_B = "kw_d_field"
        const val CHECK = "card_checkbox"
    }
}
