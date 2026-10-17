package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undercut detail overlay's selection ↔ pager binding, composed against a live
 * [UndercutRecord] that every record callback writes back, the way the ViewModel does: the trash
 * deletes the SELECTED card and nothing else, the overlay opens on the requested card, and "Add
 * undercut" whose pending page sorts to the open card's index shows the pending card without
 * recording anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutDetailOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val oal = 2000f
    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val linerSpan = UndercutLinerSpan("L1", 500f, 1500f)
    private val spec = ShaftSpec(overallLengthMm = oal, liners = listOf(liner))

    private var record by mutableStateOf(UndercutRecord())
    private val removed = mutableListOf<String>()
    private val added = mutableListOf<Float>()

    private fun cut(id: String, start: Float, len: Float = 50f) = Undercut(
        id = id, startFromAftMm = start, lengthMm = len,
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L1",
    )

    private fun show(cuts: List<Undercut>, initialSelectedId: String? = null) {
        record = UndercutRecord(cuts)
        val resolved = resolveComponents(spec)
        rule.setContent {
            MaterialTheme {
                val spans = record.undercuts.map {
                    UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm)
                }
                UndercutWindowDetailOverlay(
                    strip = linerStripFor(linerSpan, spans, oal),
                    stripKey = "liner:L1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    undercutRecord = record,
                    onAddUndercut = { start, len, ref, refLinerId ->
                        added += start
                        val u = Undercut(
                            id = "new", startFromAftMm = start, lengthMm = len,
                            authoredReference = ref, referenceLinerId = refLinerId,
                        )
                        record = record.copy(undercuts = record.undercuts + u)
                        u.id
                    },
                    onUpdateUndercut = { _, _, _, _, _ -> },
                    onUpdateReference = { _, _, _ -> },
                    onRemoveUndercut = { id ->
                        removed += id
                        record = record.copy(undercuts = record.undercuts.filterNot { it.id == id })
                    },
                    onClose = {},
                    initialSelectedId = initialSelectedId,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `trash on the selected card deletes that cut and no other`() {
        show(
            listOf(cut("u1", 600f), cut("u2", 800f), cut("u3", 1000f)),
            initialSelectedId = "u2",
        )
        // A neighbour's trash (its card peeks beside the open one) is not the selected card:
        // it brings the selected card back to the front and deletes nothing.
        rule.onNodeWithContentDescription("Delete ${undercutCardTitle(1, 3)}")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("a neighbour's trash deleted $removed", removed.isEmpty())

        // The overlay opened on u2 (the second card), so its trash is the live one.
        rule.onNodeWithContentDescription("Delete ${undercutCardTitle(2, 3)}").performClick()
        rule.waitForIdle()
        assertEquals(listOf("u2"), removed)
        assertEquals(listOf("u1", "u3"), record.undercuts.map { it.id })
    }

    @Test
    fun `add in this liner shows the pending card and records nothing until confirm`() {
        show(listOf(cut("u1", 900f)))
        rule.onNodeWithTag("undercut_add_in_liner").performClick()
        rule.waitForIdle()
        // Let any settle/heal/leave pass run to completion before judging.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithText("New undercut").assertIsDisplayed()
        assertTrue("the pending cut was recorded at $added", added.isEmpty())
        assertEquals(listOf("u1"), record.undercuts.map { it.id })
    }
}
