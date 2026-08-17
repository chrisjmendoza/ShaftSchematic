package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.model.RunoutStationPlacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reset affordances of the measurement-station editor: the per-row Reset and the
 * "Reset all bubble positions" button exist only while a component is authored (dragged) —
 * on a fully derived document neither renders, because a reset with nothing to reset would
 * imply every document carries hidden position state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class RunoutStationEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private val resets = mutableListOf<String>()
    private var resetAll = 0

    private val entries = listOf(
        RunoutComponentEntry(
            id = "l1", label = "Liner 1", kind = RunoutComponentKind.LINER,
            lengthMm = 1000f, startMm = 100f,
        ),
        RunoutComponentEntry(
            id = "b1", label = "Body 1", kind = RunoutComponentKind.BODY,
            lengthMm = 800f, startMm = 1100f,
        ),
    )

    private fun host(placements: RunoutStationPlacements) {
        rule.setContent {
            MaterialTheme {
                RunoutStationCountEditor(
                    entries = entries,
                    overrides = emptyMap(),
                    placements = placements,
                    onIncrement = { _, _ -> },
                    onDecrement = { _, _ -> },
                    onResetPositions = { resets += it },
                    onResetAllPositions = { resetAll++ },
                )
            }
        }
    }

    @Test
    fun `a derived document shows no reset affordances`() {
        host(RunoutStationPlacements())

        rule.onAllNodesWithTag("runout_reset_positions").assertCountEquals(0)
        rule.onAllNodesWithTag("runout_reset_all_positions").assertCountEquals(0)
    }

    @Test
    fun `an authored component gets a row reset, and the editor gets reset-all`() {
        host(RunoutStationPlacements().withComponent("l1", listOf(100f, 600f)))

        // One authored component of two → exactly one row-level Reset.
        rule.onAllNodesWithTag("runout_reset_positions").assertCountEquals(1)
        rule.onAllNodesWithTag("runout_reset_all_positions").assertCountEquals(1)
    }

    @Test
    fun `the row reset targets its own component`() {
        host(RunoutStationPlacements().withComponent("l1", listOf(100f, 600f)))

        rule.onNodeWithTag("runout_reset_positions").performClick()

        assertEquals(listOf("l1"), resets)
        assertEquals(0, resetAll)
    }

    @Test
    fun `reset-all fires its own callback, not the per-row one`() {
        host(
            RunoutStationPlacements()
                .withComponent("l1", listOf(100f, 600f))
                .withComponent("b1", listOf(50f, 400f)),
        )

        rule.onNodeWithTag("runout_reset_all_positions").performClick()

        assertEquals(1, resetAll)
        assertTrue(resets.isEmpty())
    }
}
