package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The wear inspection page's X-size slider (on-device request: pick the size, then place; each X
 * keeps its size until it is removed): the slider and its preview are live while adding and greyed
 * out in place under any other tool (on-device report: appearing and disappearing made the page
 * jump), a tap places
 * the pit at the slider's size, and a slider move reports the snapped value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class WearPitSizeSliderTest {

    @get:Rule
    val rule = createComposeRule()

    private val inch = 25.4f
    // The on-device case: a long 7.5" body, which draws as a thin band.
    private val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 120f * inch, diaMm = 7.5f * inch)
    private val spec = ShaftSpec(overallLengthMm = 120f * inch, bodies = listOf(body))

    private val placed = mutableStateListOf<Pair<Float, Float>>()   // axialMm to sizeFactor
    private var factor by mutableFloatStateOf(1f)

    private fun show(record: WearRecord) {
        val resolved = resolveComponents(spec)
        rule.setContent {
            MaterialTheme {
                ComponentWearDetailOverlay(
                    componentId = "B1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    wearRecord = record,
                    onAddSpot = {},
                    onUpdateSpot = { _, _, _, _, _ -> },
                    onUpdateSpotReference = { _, _ -> },
                    onRemoveSpot = {},
                    onAddPit = { _, axial, _, f -> placed += axial to f },
                    onRemovePit = {},
                    onAddDiaReading = { _, _, _ -> },
                    onUpdateDiaReading = { _, _ -> },
                    onRemoveDiaReading = {},
                    onClose = {},
                    pitSizeFactor = factor,
                    onPitSizeFactorChange = { factor = it },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a tap places the pit at the slider's size`() {
        factor = 2.5f
        show(WearRecord())
        rule.onNodeWithTag("wear_pit_size_slider").assertExists()
        rule.onNodeWithTag("wear_pit_size_preview").assertExists()
        rule.onNodeWithText("2.5×").assertExists()

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput { click(center) }
        rule.waitForIdle()

        assertEquals(1, placed.size)
        assertEquals(2.5f, placed.single().second, 0f)
    }

    @Test
    fun `moving the slider reports a snapped size`() {
        show(WearRecord())
        rule.onNodeWithTag("wear_pit_size_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        rule.waitForIdle()
        assertEquals(1.5f, factor, 0f)
        rule.onNodeWithText("1.5×").assertExists()
    }

    @Test
    fun `the slider stays in place but disabled while not adding`() {
        show(WearRecord())
        rule.onNodeWithTag("wear_pit_size_slider").assertIsEnabled()
        val topWhileAdding = rule.onNodeWithTag("wear_pit_size_slider").fetchSemanticsNode().boundsInRoot.top
        val helperTopWhileAdding = rule.onNodeWithText("Add mode — tap the segment to place an X.")
            .fetchSemanticsNode().boundsInRoot.top

        rule.onNodeWithText("Remove X").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wear_pit_size_slider").assertExists().assertIsNotEnabled()
        rule.onNodeWithTag("wear_pit_size_preview").assertExists()
        assertEquals(topWhileAdding, rule.onNodeWithTag("wear_pit_size_slider").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(
            helperTopWhileAdding,
            rule.onNodeWithText("Remove mode — tap an X to delete it.").fetchSemanticsNode().boundsInRoot.top,
            0.5f,
        )

        // A disabled slider keeps the chosen size for when Add X comes back.
        rule.onNodeWithText("Add X").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wear_pit_size_slider").assertIsEnabled()
        rule.onNodeWithText("1×").assertExists()
    }
}
