package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thread card's "Include thread in OAL" switch ASKS whether the OAL follows the thread: the
 * prompt names the new OAL in the document unit, Keep OAL and Shorten/Lengthen report their
 * choice, Cancel reports nothing and leaves the switch on its stored state, and with no OAL typed
 * there is nothing to ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ThreadOalPromptCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val inch = 25.4f

    /** FWD thread, 4" long, inside a 100" shaft at `[96", 100"]`. */
    private fun fwdIncluded() = Threads(
        id = "t1", startFromAftMm = 96f * inch, lengthMm = 4f * inch,
        majorDiaMm = 4.5f * inch, pitchMm = inch / 4f, isAftEnd = false,
    )

    private val calls = mutableListOf<Triple<String, Boolean, Boolean>>()

    private fun show(th: Threads, oalMm: Float = 100f * inch) {
        rule.setContent {
            MaterialTheme {
                ThreadPagerCard(
                    component = ResolvedThread(
                        id = th.id, startMmPhysical = th.startFromAftMm,
                        endMmPhysical = th.startFromAftMm + th.lengthMm,
                        majorDiaMm = th.majorDiaMm, pitchMm = th.pitchMm,
                    ),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oalMm, threads = listOf(th)),
                    unit = UnitSystem.INCHES,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    threadTitleById = mapOf(th.id to "Thread"),
                    f1 = { "%.1f".format(it) },
                    startValidator = { _, _, _ -> { null } },
                    onUpdateThread = { _, _, _, _, _, _ -> },
                    onUpdateThreadLabel = { _, _ -> },
                    onUpdateThreadShowLabel = { _, _ -> },
                    onSetThreadExcludeFromOal = { id, ex, adjust -> calls += Triple(id, ex, adjust) },
                    onSetThreadEndPosition = { _, _ -> },
                    onSetThreadRelief = { _, _, _, _ -> },
                    onRemoveThread = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                )
            }
        }
    }

    private fun tapSwitch() {
        rule.onNodeWithTag("thread_include_in_oal").performClick()
        rule.waitForIdle()
    }

    @Test
    fun `the prompt names the shortened OAL in inches`() {
        show(fwdIncluded())
        tapSwitch()
        rule.onNodeWithText("Exclude thread from OAL").assertExists()
        assertTrue(
            rule.onAllNodesWithText("OAL 100\" → 96\"", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNodeWithText("Shorten OAL").assertExists()
        assertTrue("nothing applied while the prompt is up", calls.isEmpty())
    }

    @Test
    fun `Keep OAL excludes without adjusting`() {
        show(fwdIncluded())
        tapSwitch()
        rule.onNodeWithTag("thread_oal_keep").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Triple("t1", true, false)), calls)
        rule.onNodeWithTag("thread_oal_keep").assertDoesNotExist()
    }

    @Test
    fun `Shorten OAL excludes and adjusts`() {
        show(fwdIncluded())
        tapSwitch()
        rule.onNodeWithTag("thread_oal_adjust").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Triple("t1", true, true)), calls)
    }

    @Test
    fun `Cancel applies nothing and the switch still reads on`() {
        show(fwdIncluded())
        tapSwitch()
        rule.onNodeWithTag("thread_oal_cancel").performClick()
        rule.waitForIdle()
        assertTrue(calls.isEmpty())
        rule.onNodeWithTag("thread_oal_cancel").assertDoesNotExist()
        rule.onNodeWithTag("thread_include_in_oal").assertIsOn()
    }

    @Test
    fun `with no OAL typed the switch flips at once`() {
        show(fwdIncluded(), oalMm = 0f)
        tapSwitch()
        rule.onNodeWithTag("thread_oal_cancel").assertDoesNotExist()
        assertEquals(listOf(Triple("t1", true, false)), calls)
    }

    @Test
    fun `an excluded thread's prompt offers to lengthen`() {
        show(fwdIncluded().copy(startFromAftMm = 100f * inch, excludeFromOAL = true))
        tapSwitch()
        assertEquals(
            "the card row and the prompt's title",
            2,
            rule.onAllNodesWithText("Include thread in OAL", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        rule.onNodeWithText("Lengthen OAL").assertExists()
        assertTrue(
            rule.onAllNodesWithText("OAL 100\" → 104\"", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNodeWithTag("thread_oal_adjust").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Triple("t1", false, true)), calls)
    }
}
