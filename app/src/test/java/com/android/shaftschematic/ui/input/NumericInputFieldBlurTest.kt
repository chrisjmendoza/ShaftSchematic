package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * Compose-level coverage of the field's two wired behaviours: commit-on-blur, and the
 * `onDirtyChange` reporting the card's Save button is enabled off.
 *
 * [BlurCommitPolicyTest] pins the commit decision; this pins that the decision is actually
 * *wired into* the field — that real focus and blur events reach it, and that the captured
 * baseline is taken at focus-gain rather than at composition. A correct predicate wired to
 * nothing would pass the pure test and fail here.
 *
 * The dirty half is **edge-triggered**: it reports the moment the text diverges from what a
 * walk-away would leave behind and again when a commit, a revert, or an external model refresh
 * settles it — never once per keystroke.
 *
 * Runs on the JVM under Robolectric, so it goes in CI with the plain unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class NumericInputFieldBlurTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()
    private val dirtyReports = mutableListOf<Boolean>()

    @Composable
    private fun Host(
        initialText: String = "12.5",
        validator: ((String) -> String?)? = null,
    ) {
        val focusManager = LocalFocusManager.current
        MaterialTheme {
            Column {
                NumericInputField(
                    label = "Length",
                    initialText = initialText,
                    modifier = Modifier.testTag(FIELD),
                    validator = validator,
                    onDirtyChange = { dirtyReports += it },
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

    /* ── onDirtyChange — what the carousel card's Save button is enabled off ──── */

    @Test
    fun `a freshly composed field reports itself clean`() {
        rule.setContent { Host() }
        rule.waitForIdle()

        assertEquals(listOf(false), dirtyReports)
    }

    @Test
    fun `diverging from the committed value reports dirty once`() {
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()

        assertEquals("first divergence reports true", listOf(false, true), dirtyReports)
    }

    @Test
    fun `committing on blur reports clean again`() {
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        blurField()
        rule.waitForIdle()

        assertEquals(listOf("13"), commits)
        assertEquals("last report after a commit must be clean", false, dirtyReports.last())
    }

    @Test
    fun `reverting an invalid edit reports clean again`() {
        // The field reverts to its last valid text instead of committing, so nothing is
        // pending afterwards — Save must grey back out rather than stay lit forever.
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("abc")
        blurField()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), commits)
        assertEquals("last report after a revert must be clean", false, dirtyReports.last())
    }

    @Test
    fun `a validator rejection reverts and reports clean again`() {
        rule.setContent { Host(validator = { raw -> if (raw.toFloatOrNull() == 0f) "Must be > 0" else null }) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("0")
        blurField()
        rule.waitForIdle()

        assertEquals("a rejected value must not reach the model", emptyList<String>(), commits)
        assertEquals(false, dirtyReports.last())
    }

    @Test
    fun `typing the value back to the committed one reports clean`() {
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("99")
        rule.onNodeWithTag(FIELD).performTextReplacement("12.5")
        rule.waitForIdle()

        assertEquals(listOf(false, true, false), dirtyReports)
    }

    @Test
    fun `a run of edits past the first reports nothing further`() {
        // Edge-triggered for real: three divergent values in a row are still ONE report. A
        // level-triggered listener would fire on every keystroke and recompose the card's Save
        // button with it.
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.onNodeWithTag(FIELD).performTextReplacement("14")
        rule.onNodeWithTag(FIELD).performTextReplacement("15")
        rule.waitForIdle()

        assertEquals("one report for the whole run", listOf(false, true), dirtyReports)
    }

    @Test
    fun `an external model refresh settles the field without committing`() {
        // An undo, or an edit made elsewhere, hands the field a new value while it still holds
        // focus. The new value becomes what a walk-away would leave behind, so the field is
        // clean again — and the refresh is not a user edit, so nothing may be written back.
        var initial by mutableStateOf("12.5")
        rule.setContent { Host(initialText = initial) }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()
        assertEquals(listOf(false, true), dirtyReports)

        initial = "20"     // no blur: the field never lost focus
        rule.waitForIdle()

        assertEquals("a refresh must not write back", emptyList<String>(), commits)
        assertEquals("the refreshed value is the settled one", false, dirtyReports.last())
    }

    private companion object {
        const val FIELD = "numeric_field"
        const val AWAY = "away_button"
    }
}
