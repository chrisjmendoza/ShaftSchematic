package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thread card's relief block ([ThreadReliefCardSection]): the switch seeds and reveals,
 * switching off clears and hides, stored values hold it open, the chips commit the end, and an
 * off/on round trip restores the typed values. The block is rendered against a live [Threads]
 * that every commit writes back, the way the ViewModel does. One case renders the whole
 * [ThreadPagerCard] to pin that the block is wired into the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ThreadReliefCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val oal = 2000f
    private val pitch = 25.4f / 4f
    private val major = 4.5f * 25.4f

    /** An AFT-end thread: start 0, so the seeded relief end is FWD (against the S.E.T.). */
    private fun aftThread(reliefLen: Float = 0f, reliefDia: Float = 0f, reliefAtAft: Boolean = false) =
        Threads(id = "t1", startFromAftMm = 0f, lengthMm = 150f, majorDiaMm = major, pitchMm = pitch,
            reliefLenMm = reliefLen, reliefDiaMm = reliefDia, reliefAtAft = reliefAtAft)

    private lateinit var current: Threads

    private fun show(initial: Threads) {
        current = initial
        rule.setContent {
            MaterialTheme {
                var th by remember { mutableStateOf(initial) }
                // The card hosts the block in its content Column; without one the rows overlay.
                Column {
                    ThreadReliefCardSection(
                        th = th,
                        unit = UnitSystem.INCHES,
                        overallLengthMm = oal,
                    ) { id, len, dia, atAft ->
                        assertEquals("t1", id)
                        th = th.copy(reliefLenMm = len, reliefDiaMm = dia, reliefAtAft = atAft)
                        current = th
                    }
                }
            }
        }
    }

    @Test
    fun `switching on seeds the values and the end, and reveals the fields`() {
        show(aftThread())
        rule.onNodeWithTag("thread_relief").assertIsOff()
        rule.onNodeWithTag("thread_relief_end").assertDoesNotExist()

        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("thread_relief").assertIsOn()
        assertEquals(defaultThreadReliefLenMm(pitch), current.reliefLenMm, 1e-4f)
        assertEquals(defaultThreadReliefDiaMm(major, pitch), current.reliefDiaMm, 1e-4f)
        assertFalse("an aft-end thread's relief seeds at its FWD end", current.reliefAtAft)
        assertTrue(current.hasRelief)
        rule.onNodeWithTag("thread_relief_end").assertExists()
        rule.onNodeWithText("Relief length (in)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Relief Ø (in)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a fwd-end thread seeds its relief at the aft end`() {
        show(aftThread().copy(startFromAftMm = oal - 150f))
        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()
        assertTrue(current.reliefAtAft)
    }

    @Test
    fun `switching off clears both values and hides the fields`() {
        show(aftThread(reliefLen = 12f, reliefDia = 100f))
        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("thread_relief").assertIsOff()
        assertEquals(0f, current.reliefLenMm, 0f)
        assertEquals(0f, current.reliefDiaMm, 0f)
        rule.onNodeWithTag("thread_relief_end").assertDoesNotExist()
        rule.onNodeWithText("Relief length (in)", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `stored values hold the section open`() {
        show(aftThread(reliefLen = 12f, reliefDia = 100f))
        rule.onNodeWithTag("thread_relief").assertIsOn()
        rule.onNodeWithTag("thread_relief_end").assertExists()
    }

    @Test
    fun `off then on restores the typed values and their end`() {
        show(aftThread(reliefLen = 12.345f, reliefDia = 101.5f, reliefAtAft = true))
        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()

        assertEquals(12.345f, current.reliefLenMm, 0f)
        assertEquals(101.5f, current.reliefDiaMm, 0f)
        assertTrue("a restored relief keeps the end it was authored at", current.reliefAtAft)
    }

    @Test
    fun `the chips commit the end and nothing else`() {
        show(aftThread(reliefLen = 12f, reliefDia = 100f, reliefAtAft = false))
        rule.onNodeWithTag("thread_relief_end_aft").performClick()
        rule.waitForIdle()
        assertTrue(current.reliefAtAft)
        assertEquals(12f, current.reliefLenMm, 0f)
        assertEquals(100f, current.reliefDiaMm, 0f)

        rule.onNodeWithTag("thread_relief_end_fwd").performClick()
        rule.waitForIdle()
        assertFalse(current.reliefAtAft)
    }

    /**
     * Turning the relief off is the switch's job, so a typed 0 is refused AND named; dropping it in
     * the commit lambda would leave the 0 on screen over the stored value (emulator QA).
     */
    @Test
    fun `a zero relief value is refused and named, and the stored relief is kept`() {
        show(aftThread(reliefLen = 12f, reliefDia = 100f))
        for (label in listOf("Relief Ø (in)", "Relief length (in)")) {
            val f = rule.onNode(hasSetTextAction() and hasText(label))
            f.performClick()
            f.performTextReplacement("0")
            f.performImeAction()
            rule.waitForIdle()
        }
        assertEquals(12f, current.reliefLenMm, 0f)
        assertEquals(100f, current.reliefDiaMm, 0f)
        assertTrue(rule.onAllNodesWithText("0 not applied — Must be > 0", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the thread card carries the relief block`() {
        val th = aftThread()
        val reported = mutableListOf<Triple<Float, Float, Boolean>>()
        rule.setContent {
            MaterialTheme {
                ThreadPagerCard(
                    component = ResolvedThread(
                        id = th.id, startMmPhysical = th.startFromAftMm,
                        endMmPhysical = th.startFromAftMm + th.lengthMm,
                        majorDiaMm = th.majorDiaMm, pitchMm = th.pitchMm,
                    ),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oal, threads = listOf(th)),
                    unit = UnitSystem.INCHES,
                    physicalIndex = 0,
                    outerPaddingHorizontal = androidx.compose.ui.unit.Dp(16f),
                    showComponentDebugLabels = false,
                    threadTitleById = mapOf(th.id to "Thread"),
                    f1 = { "%.1f".format(it) },
                    startValidator = { _, _, _ -> { null } },
                    onUpdateThread = { _, _, _, _, _, _ -> },
                    onUpdateThreadLabel = { _, _ -> },
                    onUpdateThreadShowLabel = { _, _ -> },
                    onSetThreadExcludeFromOal = { _, _, _ -> },
                    onSetThreadEndPosition = { _, _ -> },
                    onSetThreadRelief = { _, len, dia, atAft -> reported += Triple(len, dia, atAft) },
                    onRemoveThread = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("thread_relief").assertIsOff()
        rule.onNodeWithTag("thread_relief").performClick()
        rule.waitForIdle()
        assertEquals(1, reported.size)
        assertTrue(reported.single().first > 0f)
    }
}
