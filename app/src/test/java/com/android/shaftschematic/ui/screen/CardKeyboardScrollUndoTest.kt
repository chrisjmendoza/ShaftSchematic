package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A carousel card gives back the scroll the keyboard took: the keyboard shrinks the card's
 * viewport and the focused field scrolls into view, pushing the title off the top; Compose never
 * scrolls back when the viewport grows again, so the title stayed cut off — the start card's
 * "Shaft & job" on a tablet after any keyboard use (on-device QA).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class CardKeyboardScrollUndoTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `closing the keyboard scrolls back to where the card was`() {
        val undo = CardKeyboardScrollUndo()
        assertEquals(0, undo.onViewport(800, 0))
        assertEquals(0, undo.onViewport(600, 0)) // the keyboard opens…
        assertEquals(0, undo.onViewport(400, 0))
        // …and the focused field is brought into view: the card now sits 32 px down.
        assertEquals(-32, undo.onViewport(600, 32))
        assertEquals(0, undo.onViewport(800, 0))
    }

    @Test
    fun `a scroll the user had before the keyboard is kept`() {
        val undo = CardKeyboardScrollUndo()
        undo.onViewport(800, 300)
        undo.onViewport(400, 300)
        assertEquals(0, undo.onViewport(800, 300))
    }

    @Test
    fun `a field reached with Next stays in view as the keyboard closes`() {
        val undo = CardKeyboardScrollUndo()
        undo.onViewport(800, 0)
        undo.onViewport(400, 0)
        // Next moved the focus far down the card while the keyboard was up: back up by the
        // 400 px regained, not all the way to 0.
        assertEquals(-400, undo.onViewport(800, 500))
    }

    @Test
    fun `growing without having shrunk, or after the user scrolled up, never moves the card`() {
        val first = CardKeyboardScrollUndo()
        assertEquals(0, first.onViewport(400, 50))
        assertEquals(0, first.onViewport(800, 50))

        val up = CardKeyboardScrollUndo()
        up.onViewport(800, 200)
        up.onViewport(400, 200)
        // The user scrolled above the pre-keyboard offset while the keyboard was up.
        assertEquals(0, up.onViewport(800, 100))
    }

    @Test
    fun `the card title is back in view once the viewport grows again`() {
        var height by mutableStateOf(600)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(height.dp)) {
                    // Taller than the card's viewport at either height, as the start card is.
                    ComponentCard(title = "Shaft & job") {
                        Spacer(Modifier.height(350.dp))
                        Text("Overall Length", Modifier.testTag(FIELD))
                        Spacer(Modifier.height(500.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val cardTop = rule.onNodeWithText("Shaft & job").getBoundsInRoot().top
        // The keyboard shrinks the card and the focused field is scrolled into view.
        height = 300
        rule.waitForIdle()
        rule.onNodeWithTag(FIELD).performScrollTo()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithText("Shaft & job").getBoundsInRoot().top < cardTop)
        // The keyboard closes: the title is back where it was.
        height = 600
        rule.waitForIdle()
        assertEquals(cardTop, rule.onNodeWithText("Shaft & job").getBoundsInRoot().top)
    }

    private companion object {
        const val FIELD = "field"
    }
}
