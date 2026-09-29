package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Overall Length field's edges, wired rather than in principle.
 *
 * Three behaviours the field owns alone and that nothing else pins: an empty field commits
 * NOTHING and restores the stored length (clearing to retype must never zero the shaft), a
 * not-yet-typed length is not an error while a component past a real one is, and nothing
 * commits until the value is accepted — ✓, IME Done, or a blur after a change, each exactly
 * once — while ✗ restores the stored length (`docs/contracts/ShaftScreen.md`).
 *
 * Runs on the JVM under Robolectric, like `NumericInputFieldBlurTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class OverallLengthFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val mmCommits = mutableListOf<Float>()
    private val rawCommits = mutableListOf<String>()

    @Composable
    private fun Host(spec: ShaftSpec) {
        val focusManager = LocalFocusManager.current
        MaterialTheme {
            Column {
                OverallLengthField(
                    spec = spec,
                    unit = UnitSystem.MILLIMETERS,
                    onSetOverallLengthMm = { mmCommits += it },
                    onSetOverallLengthRaw = { rawCommits += it },
                )
                Button(
                    onClick = { focusManager.clearFocus() },
                    modifier = Modifier.testTag(AWAY),
                ) { Text("away") }
            }
        }
    }

    private fun fieldText(): String =
        rule.onNodeWithTag(OAL_FIELD_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    private val measured = ShaftSpec(overallLengthMm = 2540f)
    private val storedText = formatDisplay(2540f, UnitSystem.MILLIMETERS)

    /* ── An empty field commits nothing ──────────────────────────────────────── */

    @Test
    fun `clearing the field and pressing Done restores the stored length`() {
        rule.setContent { Host(measured) }

        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.onNodeWithTag(OAL_FIELD_TAG).performTextClearance()
        rule.onNodeWithTag(OAL_FIELD_TAG).performImeAction()
        rule.waitForIdle()

        assertEquals("an empty field must not commit", emptyList<Float>(), mmCommits)
        assertEquals("an empty field must not commit", emptyList<String>(), rawCommits)
        assertEquals("the text reverts to the stored length", storedText, fieldText())
    }

    @Test
    fun `clearing the field and walking away restores the stored length`() {
        rule.setContent { Host(measured) }

        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.onNodeWithTag(OAL_FIELD_TAG).performTextClearance()
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()

        assertEquals("an empty field must not commit", emptyList<Float>(), mmCommits)
        assertEquals("the text reverts to the stored length", storedText, fieldText())
    }

    /* ── Oversize is a STYLE, and a not-yet-typed length is not oversize ─────── */

    @Test
    fun `a not-yet-typed length is not an error`() {
        rule.setContent { Host(ShaftSpec(overallLengthMm = 0f)) }
        rule.waitForIdle()

        rule.onNodeWithTag(OAL_FIELD_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun `a component running past the authored length tints the field`() {
        rule.setContent {
            Host(
                ShaftSpec(
                    overallLengthMm = 1000f,
                    bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 1400f, diaMm = 100f)),
                )
            )
        }
        rule.waitForIdle()

        rule.onNodeWithTag(OAL_FIELD_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    /* ── Commit on accept, never per keystroke ───────────────────────────────── */

    private fun typeInField(text: String) {
        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.onNodeWithTag(OAL_FIELD_TAG).performTextReplacement(text)
        rule.waitForIdle()
    }

    @Test
    fun `a keystroke commits nothing until the value is accepted`() {
        rule.setContent { Host(measured) }

        typeInField("1234")

        // No blur, no Done, no check: an intermediate value must never reach the drawing.
        assertEquals(emptyList<Float>(), mmCommits)
        assertEquals(emptyList<String>(), rawCommits)
    }

    @Test
    fun `the check commits the typed value once`() {
        rule.setContent { Host(measured) }

        typeInField("1234")
        rule.onNodeWithTag(OAL_ACCEPT_TAG).performClick()
        rule.waitForIdle()

        assertEquals(listOf(1234f), mmCommits)
        assertEquals(listOf("1234"), rawCommits)

        // The blur that follows the accept must not commit a second time.
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()
        assertEquals(listOf(1234f), mmCommits)
        assertEquals(listOf("1234"), rawCommits)
    }

    @Test
    fun `the cross reverts the text and commits nothing`() {
        rule.setContent { Host(measured) }

        typeInField("1234")
        rule.onNodeWithTag(OAL_CANCEL_TAG).performClick()
        rule.waitForIdle()

        assertEquals(emptyList<Float>(), mmCommits)
        assertEquals(emptyList<String>(), rawCommits)
        assertEquals("the text reverts to the stored length", storedText, fieldText())

        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()
        assertEquals("the blur after a cancel commits nothing", emptyList<Float>(), mmCommits)
    }

    @Test
    fun `Done commits once`() {
        rule.setContent { Host(measured) }

        typeInField("1234")
        rule.onNodeWithTag(OAL_FIELD_TAG).performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()

        assertEquals(listOf(1234f), mmCommits)
        assertEquals(listOf("1234"), rawCommits)
    }

    @Test
    fun `walking away after a change commits once`() {
        rule.setContent { Host(measured) }

        typeInField("1234")
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()

        assertEquals(listOf(1234f), mmCommits)
        assertEquals(listOf("1234"), rawCommits)
    }

    @Test
    fun `walking away without a change commits nothing`() {
        rule.setContent { Host(measured) }

        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()

        assertEquals(emptyList<Float>(), mmCommits)
        assertEquals(emptyList<String>(), rawCommits)
    }

    @Test
    fun `the accept and cancel buttons only appear while the value is modified`() {
        rule.setContent { Host(measured) }

        rule.onNodeWithTag(OAL_ACCEPT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(OAL_CANCEL_TAG).assertDoesNotExist()

        // Focused but untouched is not modified.
        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(OAL_ACCEPT_TAG).assertDoesNotExist()

        rule.onNodeWithTag(OAL_FIELD_TAG).performTextReplacement("1234")
        rule.waitForIdle()
        rule.onNodeWithTag(OAL_ACCEPT_TAG).assertIsDisplayed()
        rule.onNodeWithTag(OAL_CANCEL_TAG).assertIsDisplayed()

        rule.onNodeWithTag(OAL_ACCEPT_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(OAL_ACCEPT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(OAL_CANCEL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the cleared zero ghost is not a modification`() {
        rule.setContent { Host(ShaftSpec(overallLengthMm = 0f)) }

        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.waitForIdle()

        assertEquals("the ghost clears on focus", "", fieldText())
        rule.onNodeWithTag(OAL_ACCEPT_TAG).assertDoesNotExist()
    }

    private companion object {
        const val AWAY = "away_button"
    }
}
