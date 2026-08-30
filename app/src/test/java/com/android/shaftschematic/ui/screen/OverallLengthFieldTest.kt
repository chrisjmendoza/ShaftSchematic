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
 * not-yet-typed length is not an error while a component past a real one is, and a parseable
 * keystroke commits immediately — the deliberate exception to commit-on-blur that keeps the
 * preview growing as the user types (`docs/contracts/ShaftScreen.md`).
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

    /* ── The documented per-keystroke exception ──────────────────────────────── */

    @Test
    fun `a parseable keystroke commits immediately`() {
        rule.setContent { Host(measured) }

        rule.onNodeWithTag(OAL_FIELD_TAG).performClick()
        rule.onNodeWithTag(OAL_FIELD_TAG).performTextReplacement("1234")
        rule.waitForIdle()

        // No blur, no Done: the preview has to follow the typing.
        assertEquals(listOf(1234f), mmCommits)
    }

    private companion object {
        const val AWAY = "away_button"
    }
}
