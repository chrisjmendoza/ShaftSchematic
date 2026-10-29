package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import com.android.shaftschematic.util.isLengthText
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
        hint: String? = null,
        placeholder: String? = null,
        externalIssueText: String? = null,
        landRestoredEdit: Boolean = false,
        parseValid: (String) -> Boolean = { it.toFloatOrNull() != null },
    ) {
        val focusManager = LocalFocusManager.current
        MaterialTheme {
            Column {
                NumericInputField(
                    label = "Length",
                    initialText = initialText,
                    modifier = Modifier.testTag(FIELD),
                    validator = validator,
                    hint = hint,
                    placeholder = placeholder,
                    externalIssueText = externalIssueText,
                    landRestoredEdit = landRestoredEdit,
                    onDirtyChange = { dirtyReports += it },
                    parseValid = parseValid,
                    onCommit = { commits += it }
                )
                Button(
                    onClick = { focusManager.clearFocus() },
                    modifier = Modifier.testTag(AWAY)
                ) { Text("away") }
                // A second focus target: a blur that stays a blur. Under Robolectric a cleared
                // focus is handed straight back to the first focusable (non-touch mode), so the
                // "away" button's blur is immediately followed by a re-focus of the field.
                var other by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = other,
                    onValueChange = { other = it },
                    modifier = Modifier.testTag(OTHER)
                )
            }
        }
    }

    private fun focusField() = rule.onNodeWithTag(FIELD).performClick()
    private fun blurField() = rule.onNodeWithTag(AWAY).performClick()
    private fun focusElsewhere() = rule.onNodeWithTag(OTHER).performClick()

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

    /* ── A refused value is reported, never swapped out silently ───────────────── */

    private fun fieldHolds(value: String) = rule.onNodeWithTag(FIELD).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
    )

    private fun notice(text: String) = rule.onNodeWithText(text, useUnmergedTree = true)

    @Test
    fun `a validator rejection names the refused value and the rule, and the box reverts`() {
        rule.setContent { Host(validator = { raw -> if (raw.toFloatOrNull() == 0f) "Must be > 0" else null }) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("0")
        focusElsewhere()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), commits)
        fieldHolds("12.5")
        notice("0 not applied — Must be > 0").assertIsDisplayed()
    }

    @Test
    fun `an unparseable value is named as not a number`() {
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("1/")
        rule.onNodeWithTag(FIELD).performImeAction()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), commits)
        fieldHolds("12.5")
        notice("1/ not applied — not a number").assertIsDisplayed()
    }

    @Test
    fun `a cleared field is named as a blank entry`() {
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("")
        focusElsewhere()
        rule.waitForIdle()

        fieldHolds("12.5")
        notice("Blank entry not applied — not a number").assertIsDisplayed()
    }

    @Test
    fun `the revert goes to the value the caller holds, not a half-typed intermediate`() {
        // "3" parses on the way to "3/" — but it was never committed, so putting it in the box
        // would show a value the caller does not hold.
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("3")
        rule.onNodeWithTag(FIELD).performTextReplacement("3/")
        focusElsewhere()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), commits)
        fieldHolds("12.5")
    }

    @Test
    fun `focusing the field again keeps the notice and typing clears it`() {
        rule.setContent { Host(validator = { raw -> if (raw.toFloatOrNull() == 0f) "Must be > 0" else null }) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("0")
        focusElsewhere()
        rule.waitForIdle()
        notice("0 not applied — Must be > 0").assertIsDisplayed()

        focusField()
        rule.waitForIdle()
        notice("0 not applied — Must be > 0").assertIsDisplayed()

        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()
        notice("0 not applied — Must be > 0").assertDoesNotExist()
    }

    @Test
    fun `a focus change nobody asked for does not wipe the notice`() {
        // The "away" button's clearFocus is handed straight back to the field under Robolectric
        // (non-touch mode, as with a hardware keyboard): the field is re-focused without the
        // machinist touching it, and the notice must still be there to read.
        rule.setContent { Host(validator = { raw -> if (raw.toFloatOrNull() == 0f) "Must be > 0" else null }) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("0")
        blurField()
        rule.waitForIdle()

        fieldHolds("12.5")
        notice("0 not applied — Must be > 0").assertIsDisplayed()
    }

    @Test
    fun `an edit after an IME Done rejection clears the notice`() {
        // Done keeps focus; the next real edit retires the report.
        rule.setContent { Host() }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("1/")
        rule.onNodeWithTag(FIELD).performImeAction()
        rule.waitForIdle()
        notice("1/ not applied — not a number").assertIsDisplayed()

        rule.onNodeWithTag(FIELD).performTextReplacement("14")
        rule.waitForIdle()

        notice("1/ not applied — not a number").assertDoesNotExist()
    }

    /* ── hint and placeholder — advertised entry, never in an error's place ──── */

    @Test
    fun `a clean field shows its hint, and a refused commit takes the line until the next edit`() {
        rule.setContent { Host(hint = HINT) }
        rule.waitForIdle()
        notice(HINT).assertIsDisplayed()

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("1/")
        rule.onNodeWithTag(FIELD).performImeAction()
        rule.waitForIdle()
        notice("1/ not applied — not a number").assertIsDisplayed()
        notice(HINT).assertDoesNotExist()

        rule.onNodeWithTag(FIELD).performTextReplacement("14")
        rule.waitForIdle()
        notice("1/ not applied — not a number").assertDoesNotExist()
        notice(HINT).assertIsDisplayed()
    }

    @Test
    fun `an external issue outranks the hint`() {
        rule.setContent { Host(hint = HINT, externalIssueText = "Overlaps cut 1") }
        rule.waitForIdle()
        notice("Overlaps cut 1").assertIsDisplayed()
        notice(HINT).assertDoesNotExist()
    }

    @Test
    fun `an empty focused field shows its placeholder`() {
        rule.setContent { Host(initialText = "", placeholder = "e.g. 5 5/8") }
        focusField()
        rule.waitForIdle()
        notice("e.g. 5 5/8").assertIsDisplayed()
        assertEquals("a placeholder is never a value", emptyList<String>(), commits)
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

    /* ── A typed value survives an activity recreation (a tablet rotation) ──────── */

    // The state is saved BEFORE the teardown, and the teardown takes focus off the field, so its
    // own blur commits once on the way out exactly as any blur does. Where that commit lands in
    // state the recreation discards (the undercut card's draft), the caller's value comes back
    // unchanged and the typed text is the only copy left — the case these pin.

    @Test
    fun `a typed value that was never committed survives a recreation without committing`() {
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()

        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        fieldHolds("13")
        assertEquals("the teardown's blur is the only commit; the restore adds none", listOf("13"), commits)
        assertEquals("the restored edit still reads as uncommitted", true, dirtyReports.last())
    }

    @Test
    fun `a restored edit lands on the next blur even with no further typing`() {
        // On screen and unapplied is the trap: the box shows the typed value, so leaving the
        // field must hand it over exactly as a blur before the rotation would have.
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Host() }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()
        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        focusField()
        focusElsewhere()

        assertEquals("the teardown's blur, then the restored edit landing", listOf("13", "13"), commits)
    }

    @Test
    fun `a restore over a model value that moved meanwhile shows the new value clean`() {
        // Restoration does not compare the saved state's inputs: a model value that changed
        // between the save and the restore (a teardown blur committed the edit) must win over
        // the saved text, or the field would report an edit nobody is making.
        var initial = "12.5"
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Host(initialText = initial) }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()

        initial = "20"
        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        fieldHolds("20")
        assertEquals("only the teardown's blur committed", listOf("13"), commits)
        assertEquals(false, dirtyReports.last())
    }

    @Test
    fun `landRestoredEdit commits a restored edit once, with no focus and no further typing`() {
        // The undercut card's case: the teardown's blur committed into state the recreation threw
        // away, so the restored field must hand its edit over itself.
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Host(landRestoredEdit = true) }

        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("13")
        rule.waitForIdle()
        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        fieldHolds("13")
        assertEquals("the teardown's blur, then the restored edit landing", listOf("13", "13"), commits)
        assertEquals("the landed edit is settled", false, dirtyReports.last())
    }

    @Test
    fun `landRestoredEdit commits nothing on an ordinary composition or a clean restore`() {
        val restorer = StateRestorationTester(rule)
        restorer.setContent { Host(landRestoredEdit = true) }
        rule.waitForIdle()
        assertEquals("composition alone must not commit", emptyList<String>(), commits)

        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("a restore with nothing typed must not commit", emptyList<String>(), commits)
    }

    private companion object {
        const val FIELD = "numeric_field"
        const val AWAY = "away_button"
        const val OTHER = "other_field"
        const val HINT = "Decimals or fractions"
    }

    /* ── The card fields' spellings reach the commit as typed ─────────────────── */

    // The keystroke filter once dropped the hyphen (10-1/4 became 101/4 = 25.25), the comma (1,5
    // became 15) and the suffix (260mm became 260 in the document unit) — emulator QA.
    private fun commitsAsTyped(typed: String) {
        rule.setContent { Host(parseValid = ::isLengthText) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement(typed)
        rule.onNodeWithTag(FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(listOf(typed), commits)
    }

    @Test
    fun `a hyphenated mixed fraction commits as typed`() = commitsAsTyped("10-1/4")

    @Test
    fun `a decimal comma commits as typed`() = commitsAsTyped("1,5")

    @Test
    fun `a mm suffix commits as typed`() = commitsAsTyped("260mm")

    @Test
    fun `an inch mark commits as typed`() = commitsAsTyped("11\"")

    @Test
    fun `an ambiguous comma is refused and named, never guessed`() {
        rule.setContent { Host(parseValid = ::isLengthText) }
        focusField()
        rule.onNodeWithTag(FIELD).performTextReplacement("1,500")
        rule.onNodeWithTag(FIELD).performImeAction()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), commits)
        fieldHolds("12.5")
        notice("1,500 not applied — not a number").assertIsDisplayed()
    }
}
