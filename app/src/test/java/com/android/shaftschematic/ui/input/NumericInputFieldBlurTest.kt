package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose-level coverage of the commit-on-blur contract (TODO §5.2).
 *
 * [BlurCommitPolicyTest] pins the decision; this pins that the decision is actually *wired
 * into* the field — that real focus and blur events reach it, and that the captured
 * baseline is taken at focus-gain rather than at composition. A correct predicate wired to
 * nothing would pass the pure test and fail here.
 *
 * Runs on the JVM under Robolectric, so it goes in CI with the plain unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class NumericInputFieldBlurTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()

    @Composable
    private fun Host(initialText: String = "12.5") {
        val focusManager = LocalFocusManager.current
        MaterialTheme {
            Column {
                NumericInputField(
                    label = "Length",
                    initialText = initialText,
                    modifier = Modifier.testTag(FIELD),
                    parseValid = { it.toFloatOrNull() != null },
                    onCommit = { commits += it }
                )
                Button(
                    onClick = { focusManager.clearFocus() },
                    modifier = Modifier.testTag(AWAY)
                ) { Text("away") }
            }
        }
    }

    private fun focusField() = rule.onNodeWithTag(FIELD).performClick()
    private fun blurField() = rule.onNodeWithTag(AWAY).performClick()

    @Test
    fun `merely composing the field does not commit`() {
        rule.setContent { Host() }
        rule.waitForIdle()

        assertEquals("composition alone must not commit", emptyList<String>(), commits)
    }

    @Test
    fun `tap into the field and leave without editing does not commit`() {
        rule.setContent { Host() }

        focusField()
        blurField()

        assertEquals("tap-and-leave must be a no-op", emptyList<String>(), commits)
    }

    @Test
    fun `editing then leaving commits the new value`() {
        rule.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        blurField()

        assertEquals(listOf("13"), commits)
    }

    @Test
    fun `typing the same value back does not commit`() {
        // The baseline is the text at focus-gain, so an edit that lands back on the
        // original value is correctly seen as no change at all.
        rule.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("99")
        rule.onNodeWithTag(FIELD).performTextReplacement("12.5")
        blurField()

        assertEquals(emptyList<String>(), commits)
    }

    @Test
    fun `a second visit with no edit does not re-commit`() {
        // Guards the baseline being re-captured on each focus-gain rather than once at
        // composition — otherwise the second visit compares against stale text and fires.
        rule.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        blurField()
        assertEquals(listOf("13"), commits)

        focusField()
        blurField()

        assertEquals("second visit made no edit", listOf("13"), commits)
    }

    @Test
    fun `an invalid edit reverts and does not commit`() {
        rule.setContent { Host() }

        focusField()
        // The numeric filter drops the letters, leaving an empty field, which fails
        // parseValid — so this reverts to the last valid text instead of committing.
        rule.onNodeWithTag(FIELD).performTextReplacement("abc")
        blurField()

        assertEquals(emptyList<String>(), commits)
    }

    @Test
    fun `the IME Done action commits even without an edit`() {
        // Pins a deliberate asymmetry: Done is an explicit "I mean this" gesture and
        // commits unconditionally, while blur is passive and does not. If this ever needs
        // to change, it should change on purpose.
        rule.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performImeAction()

        assertEquals(listOf("12.5"), commits)
    }

    private companion object {
        const val FIELD = "numeric_field"
        const val AWAY = "away_button"
    }
}
