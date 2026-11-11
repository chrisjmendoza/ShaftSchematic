package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Line Thickness's percent box commits on accept (IME Done, or a blur after an edit),
 * never per keystroke: typing "150" passes through "1", which a per-keystroke commit applied as
 * 50% on the way. An empty entry commits nothing and the stored value comes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LineThicknessFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<Float>()
    private var stored by mutableFloatStateOf(1f)

    private fun host() {
        rule.setContent {
            MaterialTheme {
                LineThicknessControl(
                    scale = stored,
                    onScaleChange = { commits += it; stored = it },
                )
            }
        }
        rule.waitForIdle()
    }

    private val field get() = rule.onNodeWithTag(LINE_THICKNESS_FIELD_TAG)

    @Test
    fun `typing commits nothing until Done`() {
        host()

        field.performTextReplacement("1")
        field.performTextReplacement("15")
        field.performTextReplacement("150")
        rule.waitForIdle()
        assertTrue("no keystroke commits ($commits)", commits.isEmpty())

        field.performImeAction()
        rule.waitForIdle()
        assertEquals(listOf(1.5f), commits)
        field.assertTextContains("150")
    }

    @Test
    fun `an empty entry commits nothing and restores the stored value`() {
        stored = 1.25f
        host()

        field.performTextReplacement("")
        field.performImeAction()
        rule.waitForIdle()

        assertTrue(commits.isEmpty())
        field.assertTextContains("125")
    }

    @Test
    fun `the entry parser clamps to the slider range and refuses unreadable text`() {
        assertEquals(150, lineThicknessFieldPercent("150"))
        assertEquals(150, lineThicknessFieldPercent(" 150% "))
        assertEquals(50, lineThicknessFieldPercent("1"))
        assertEquals(200, lineThicknessFieldPercent("900"))
        assertNull(lineThicknessFieldPercent(""))
        assertNull(lineThicknessFieldPercent("abc"))
    }
}
