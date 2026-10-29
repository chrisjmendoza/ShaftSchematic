package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
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
 * An Add dialog's seeded field keeps its EXACT mm until the user edits it (emulator QA: a Start
 * seeded flush against a liner ending at 35.42422" showed 35.424, was parsed back from that text,
 * and opened on a false "Overlaps AFT Liner"; a liner seeded against a 25.1875" taper started
 * 0.0005" past it). The display text is unchanged — only the value behind it is exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class AddDialogSeedExactTest {

    @get:Rule
    val rule = createComposeRule()

    private val taperEndMm = 25.1875f * 25.4f
    // The repro's liner: started at the old rounded seed 25.188" and typed `260mm`, so it ends at
    // 35.42422" — which displays as 35.424, a hair INSIDE the liner when parsed back.
    private val linerStartMm = 25.188f * 25.4f
    private val linerEndMm = linerStartMm + 260f
    private val taper = Taper(id = "t1", startFromAftMm = 0f, lengthMm = taperEndMm,
        startDiaMm = 200f, endDiaMm = 220f)
    private val liner = Liner(id = "l1", startFromAftMm = linerStartMm, lengthMm = 260f, odMm = 260f,
        endMmPhysical = linerEndMm)
    private val spec = ShaftSpec(overallLengthMm = 300.5f * 25.4f, tapers = listOf(taper), liners = listOf(liner))

    private fun field(label: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(label))

    private fun overlapShown(): Boolean =
        rule.onAllNodes(hasText("Overlaps", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private var threadStart: Float? = null

    private fun showThread() {
        rule.setContent {
            MaterialTheme {
                AddThreadDialog(
                    unit = UnitSystem.INCHES,
                    spec = spec,
                    initialStartMm = linerEndMm,
                    initialLengthMm = 5f * 25.4f,
                    initialMajorDiaMm = 5f * 25.4f,
                    initialPitchMm = 25.4f / 4f,
                    onSubmit = { start, _, _, _, _, _, _, _, _, _ -> threadStart = start },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a Start seeded flush against a liner shows no overlap and submits the liner's exact end`() {
        showThread()
        // The display is still the 3-decimal rounding the dialog always showed.
        rule.onNodeWithText("35.424", useUnmergedTree = true).assertExists()
        assertFalse("no false overlap line on the flush seed", overlapShown())
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
        assertEquals(linerEndMm, assertNotNullAndGet(threadStart), 0f)
    }

    @Test
    fun `an edited Start is parsed as typed`() {
        showThread()
        field("Start (in)").performTextReplacement("36.5")
        rule.waitForIdle()
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
        assertEquals(toMmOrNull("36.5", UnitSystem.INCHES)!!, assertNotNullAndGet(threadStart), 0f)
    }

    @Test
    fun `an edited Start retyped onto the liner shows the overlap the typed value makes`() {
        showThread()
        field("Start (in)").performTextReplacement("35.42")
        field("Start (in)").performTextReplacement("35.424")
        rule.waitForIdle()
        assertTrue("35.424 typed is inside the liner", overlapShown())
    }

    @Test
    fun `a liner seeded against the taper submits the taper's exact end`() {
        var linerStart: Float? = null
        val onlyTaper = spec.copy(liners = emptyList())
        rule.setContent {
            MaterialTheme {
                AddLinerDialog(
                    unit = UnitSystem.INCHES,
                    spec = onlyTaper,
                    initialLengthMm = 260f,
                    onSubmit = { start, _, _, _, _ -> linerStart = start },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("25.188", useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
        assertEquals(taperEndMm, assertNotNullAndGet(linerStart), 0f)
    }

    private fun assertNotNullAndGet(v: Float?): Float {
        assertNotNull("Add submitted", v)
        return v!!
    }
}
