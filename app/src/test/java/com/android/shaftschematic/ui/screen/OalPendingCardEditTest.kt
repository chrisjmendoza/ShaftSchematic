package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
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
 * A tap on the Overall Length field while a card field holds a typed value that has not landed
 * asks first (on-device request): moving focus to the OAL would blur-commit the card value with
 * its ✓ / ✗ never answered. Keep answers ✓, Discard answers ✗ — each then hands focus to the
 * OAL — and Cancel leaves the card field exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class OalPendingCardEditTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()
    private val oalCommits = mutableListOf<Float>()

    @Composable
    private fun Host() {
        val pending = remember { PendingCardEdits() }
        MaterialTheme {
            CompositionLocalProvider(LocalPendingCardEdits provides pending) {
                Column {
                    OverallLengthField(
                        spec = ShaftSpec(overallLengthMm = 2540f),
                        unit = UnitSystem.MILLIMETERS,
                        onSetOverallLengthMm = { oalCommits += it },
                        onSetOverallLengthRaw = {},
                    )
                    ComponentCard(title = "Body 1") {
                        CommitNum(
                            label = "KW L",
                            initialDisplay = "0",
                            modifier = Modifier.testTag(FIELD),
                        ) { commits += it }
                    }
                }
            }
        }
    }

    private fun typeInCard(value: String) {
        rule.onNodeWithTag(FIELD).requestFocus()
        rule.onNodeWithTag(FIELD).performTextReplacement(value)
        rule.waitForIdle()
    }

    private fun tapOal() {
        rule.onNodeWithTag(OAL_PENDING_GUARD_TAG).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `with nothing pending the OAL takes the tap itself`() {
        rule.setContent { Host() }
        rule.waitForIdle()
        rule.onAllNodesWithTag(OAL_PENDING_GUARD_TAG).assertCountEquals(0)
        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsFocused()
        rule.onAllNodesWithTag(OAL_PENDING_DIALOG_TAG).assertCountEquals(0)
    }

    @Test
    fun `a tap with a card value pending asks first and commits nothing`() {
        rule.setContent { Host() }
        typeInCard("6.5")
        tapOal()
        rule.onAllNodesWithTag(OAL_PENDING_DIALOG_TAG).assertCountEquals(1)
        assertTrue("the card value must not land behind the prompt", commits.isEmpty())
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsNotFocused()
    }

    @Test
    fun `keep lands the card value once and moves to the OAL`() {
        rule.setContent { Host() }
        typeInCard("6.5")
        tapOal()
        rule.onNodeWithTag(OAL_PENDING_KEEP_TAG).performClick()
        rule.waitForIdle()
        assertEquals(listOf("6.5"), commits)
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsFocused()
        rule.onAllNodesWithTag(OAL_PENDING_GUARD_TAG).assertCountEquals(0)
    }

    @Test
    fun `discard puts the card value back and moves to the OAL`() {
        rule.setContent { Host() }
        typeInCard("6.5")
        tapOal()
        rule.onNodeWithTag(OAL_PENDING_DISCARD_TAG).performClick()
        rule.waitForIdle()
        assertTrue("discard must not commit", commits.isEmpty())
        rule.onNodeWithTag(FIELD).assertTextEquals("KW L", "0")
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun `cancel leaves the card value pending and the OAL untouched`() {
        rule.setContent { Host() }
        typeInCard("6.5")
        tapOal()
        rule.onNodeWithTag(OAL_PENDING_CANCEL_TAG).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(OAL_PENDING_DIALOG_TAG).assertCountEquals(0)
        assertTrue(commits.isEmpty())
        val cardText = rule.onNodeWithTag(FIELD).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("the typed value stays in the card field", "6.5", cardText)
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsNotFocused()
        assertTrue(oalCommits.isEmpty())
    }

    @Test
    fun `the prompt names the field and its card`() {
        val msg = pendingCardEditMessage(PendingCardEdit("Body 1", "KW L", {}, {}))
        assertEquals(
            "“KW L” on Body 1 holds a value you have not saved. " +
                "Keep it before you change the overall length?",
            msg,
        )
    }

    private companion object {
        const val FIELD = "kw_l_field"
    }
}
