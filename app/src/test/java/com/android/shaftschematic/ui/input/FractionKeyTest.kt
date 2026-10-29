package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.android.shaftschematic.util.isLengthText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The in-field "/" key: number fields ask for the number pad, which has no fraction bar, so the
 * key is the only way to type one there. Pins the cursor arithmetic, the `String`-field bridge,
 * and that the key is wired into [NumericInputField] — shown only while a fraction-capable field
 * is focused in an inch document, and typing at the cursor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class FractionKeyTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<String>()

    @Test
    fun `insertAtCursor types at the cursor and leaves the cursor after the bar`() {
        val v = TextFieldValue("10 14", TextRange(4)).insertAtCursor("/")
        assertEquals("10 1/4", v.text)
        assertEquals(TextRange(5), v.selection)
    }

    @Test
    fun `insertAtCursor replaces a selection the way a typed key does`() {
        val v = TextFieldValue("12.5", TextRange(0, 4)).insertAtCursor("/")
        assertEquals("/", v.text)
        assertEquals(TextRange(1), v.selection)
    }

    @Test
    fun `FieldCursor keeps the cursor of the last edit and inserts there`() {
        val c = FieldCursor()
        c.track(TextFieldValue("3 4", TextRange(2)))
        assertEquals("3 /4", c.insertBar("3 4").text)
    }

    @Test
    fun `FieldCursor puts the cursor at the end of text that moved under it`() {
        val c = FieldCursor()
        c.track(TextFieldValue("3", TextRange(1)))
        val shown = c.valueFor("10 1")
        assertEquals(TextRange(4), shown.selection)
        assertEquals("10 1/", c.insertBar("10 1").text)
    }

    @Composable
    private fun Host(allowFraction: Boolean = true, inches: Boolean = true) {
        val focusManager = LocalFocusManager.current
        MaterialTheme {
            CompositionLocalProvider(LocalFractionKeyEnabled provides inches) {
                Column {
                    NumericInputField(
                        label = "Length",
                        initialText = "",
                        modifier = Modifier.testTag(FIELD),
                        allowFraction = allowFraction,
                        parseValid = ::isLengthText,
                        onCommit = { commits += it },
                    )
                    Button(
                        onClick = { focusManager.clearFocus() },
                        modifier = Modifier.testTag(AWAY),
                    ) { Text("away") }
                }
            }
        }
    }

    @Test
    fun `the key shows only while the field is focused`() {
        rule.setContent { Host() }
        rule.onAllNodesWithTag(FRACTION_KEY_TAG).assertCountEquals(0)
        rule.onNodeWithTag(FIELD).performClick()
        rule.onAllNodesWithTag(FRACTION_KEY_TAG).assertCountEquals(1)
    }

    @Test
    fun `the key types a bar at the cursor and the field commits the fraction`() {
        rule.setContent { Host() }
        rule.onNodeWithTag(FIELD).performClick()
        rule.onNodeWithTag(FIELD).performTextReplacement("10 1")
        rule.onNodeWithTag(FRACTION_KEY_TAG).performClick()
        rule.onNodeWithTag(FIELD).performTextInput("4")
        rule.onNodeWithTag(AWAY).performClick()
        rule.waitForIdle()
        assertEquals(listOf("10 1/4"), commits)
    }

    @Test
    fun `no key on a field that takes no fraction`() {
        rule.setContent { Host(allowFraction = false) }
        rule.onNodeWithTag(FIELD).performClick()
        rule.onAllNodesWithTag(FRACTION_KEY_TAG).assertCountEquals(0)
    }

    @Test
    fun `no key in a millimetre document`() {
        rule.setContent { Host(inches = false) }
        rule.onNodeWithTag(FIELD).performClick()
        rule.onAllNodesWithTag(FRACTION_KEY_TAG).assertCountEquals(0)
    }

    private companion object {
        const val FIELD = "field"
        const val AWAY = "away"
    }
}
