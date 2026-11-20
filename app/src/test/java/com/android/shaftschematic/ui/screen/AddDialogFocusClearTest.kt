package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An Add-dialog field the user has not typed into empties on focus, so the first real entry needs
 * no deleting (on-device request). The seed is not lost: Add still submits it, and leaving the
 * field with nothing typed puts it back. A typed value is never cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class AddDialogFocusClearTest {

    @get:Rule
    val rule = createComposeRule()

    private val seedStartMm = 10.5f * 25.4f
    private var submittedStart: Float? = null

    private fun field(label: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(label))

    private fun boxText(label: String): String =
        field(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun showThread() {
        rule.setContent {
            MaterialTheme {
                AddThreadDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = 100f * 25.4f),
                    initialStartMm = seedStartMm,
                    initialLengthMm = 5f * 25.4f,
                    initialMajorDiaMm = 5f * 25.4f,
                    initialPitchMm = 25.4f / 4f,
                    onSubmit = { start, _, _, _, _, _, _, _, _, _ -> submittedStart = start },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("add_thread_include_oal").performClick()
        rule.waitForIdle()
    }

    private fun clickAdd() {
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a new thread opens excluded from the OAL`() {
        rule.setContent {
            MaterialTheme {
                AddThreadDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = 100f * 25.4f),
                    initialStartMm = seedStartMm,
                    initialLengthMm = 5f * 25.4f,
                    initialMajorDiaMm = 5f * 25.4f,
                    initialPitchMm = 25.4f / 4f,
                    onSubmit = { _, _, _, _, _, _, _, _, _, _ -> },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
        assertTrue("Thread end chips shown",
            rule.onAllNodes(hasText("Thread end:"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertFalse("no Start field",
            rule.onAllNodes(hasSetTextAction() and hasText("Start (in)")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `an untyped seed empties on focus and still submits`() {
        showThread()
        assertEquals("10 1/2", boxText("Start (in)"))
        field("Start (in)").performClick()
        rule.waitForIdle()
        assertEquals("", boxText("Start (in)"))
        clickAdd()
        assertEquals(seedStartMm, assertNotNullAndGet(submittedStart), 0f)
    }

    @Test
    fun `leaving an emptied field with nothing typed puts the seed back`() {
        showThread()
        field("Start (in)").performClick()
        rule.waitForIdle()
        field("Length (in)").performClick()
        rule.waitForIdle()
        assertEquals("10 1/2", boxText("Start (in)"))
        clickAdd()
        assertEquals(seedStartMm, assertNotNullAndGet(submittedStart), 0f)
    }

    @Test
    fun `a typed value is not cleared on the next focus`() {
        showThread()
        field("Start (in)").performTextReplacement("12")
        rule.waitForIdle()
        field("Length (in)").performClick()
        rule.waitForIdle()
        field("Start (in)").performClick()
        rule.waitForIdle()
        assertEquals("12", boxText("Start (in)"))
        clickAdd()
        assertEquals(toMmOrNull("12", UnitSystem.INCHES)!!, assertNotNullAndGet(submittedStart), 0f)
    }

    @Test
    fun `the focus-clear rule`() {
        assertTrue(untypedFocusClear(typed = false, text = "4"))
        assertFalse(untypedFocusClear(typed = true, text = "4"))
        assertFalse(untypedFocusClear(typed = false, text = ""))
    }

    private fun assertNotNullAndGet(v: Float?): Float {
        assertNotNull("Add submitted", v)
        return v!!
    }
}
