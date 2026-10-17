package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
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
 * The wear-spot card's Start and Length fields never revert a typed value (on-device ruling):
 * an out-of-span value is committed to the record verbatim, the field keeps showing it, and the
 * card states why the span does not fit — under the fields and beside the card title. Composed
 * against a live [WearRecord] that the update callback writes back, the way the ViewModel does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LinerWearSpotFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val spec = ShaftSpec(overallLengthMm = 2000f, liners = listOf(liner))

    private var record by mutableStateOf(WearRecord())
    private val updates = mutableListOf<Pair<Float, Float>>()

    private fun show(spot: WearSpot) {
        record = WearRecord(spots = listOf(spot))
        val resolved = resolveComponents(spec)
        rule.setContent {
            MaterialTheme {
                ComponentWearDetailOverlay(
                    componentId = "L1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    wearRecord = record,
                    onAddSpot = {},
                    onUpdateSpot = { id, s, l, d, n ->
                        updates += s to l
                        record = record.copy(
                            spots = record.spots.map {
                                if (it.id != id) it
                                else it.copy(startMm = s, lengthMm = l, minDiaMm = d, note = n)
                            }
                        )
                    },
                    onUpdateSpotReference = { _, _ -> },
                    onRemoveSpot = {},
                    onAddPit = { _, _, _, _ -> },
                    onRemovePit = {},
                    onAddDiaReading = { _, _, _ -> },
                    onUpdateDiaReading = { _, _ -> },
                    onRemoveDiaReading = {},
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun spot(start: Float, len: Float, ref: WearSpotReference = WearSpotReference.LINER_AFT) =
        WearSpot(id = "s1", linerId = "L1", startMm = start, lengthMm = len, authoredReference = ref)

    private fun inCard(label: String) = hasText(label) and hasAnyAncestor(hasTestTag("wear_spot_card_s1"))

    private fun editableStartsWith(prefix: String) = SemanticsMatcher("editable text starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text?.startsWith(prefix) == true
    }

    private fun type(label: String, value: String) {
        rule.onNode(inCard(label)).performScrollTo().performTextReplacement(value)
        rule.onNode(inCard(label)).performImeAction()
        rule.waitForIdle()
    }

    private fun assertKeptAndExplained(label: String, typed: String, message: String, shortNote: String) {
        rule.onNode(inCard(label)).assert(editableStartsWith(typed))
        assertTrue(
            "no field may report the value as not applied",
            rule.onAllNodes(hasText("not applied", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        rule.onNodeWithTag("wear_spot_issue_s1", useUnmergedTree = true).assert(hasText(message))
        rule.onNodeWithTag("wear_spot_note_s1", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(shortNote)))
    }

    @Test
    fun `a length past the liner's FWD edge is stored verbatim and the card says why`() {
        show(spot(start = 100f, len = 50f))
        type("Length (in)", "100")

        assertEquals(listOf(100f to 2540f), updates)
        assertKeptAndExplained(
            "Length (in)", "100",
            "Wear spot extends past the liner's FWD edge", "Extends past liner end",
        )
    }

    @Test
    fun `a start past the liner's FWD edge is stored verbatim and the card says why`() {
        show(spot(start = 100f, len = 50f))
        type("Start from liner AFT edge (in)", "60")

        assertEquals(1, updates.size)
        assertEquals(1524f, updates.single().first, 0.01f)
        assertEquals(50f, updates.single().second, 0.0f)
        assertKeptAndExplained(
            "Start from liner AFT edge (in)", "60",
            "Wear spot extends past the liner's FWD edge", "Extends past liner end",
        )
    }

    @Test
    fun `a start that lands AFT of the liner is stored verbatim, negative`() {
        // AFT SET sits at the shaft's AFT end (0) on this spec, so 10 in from it is 254 mm in
        // shaft space — 246 mm AFT of the liner's AFT edge at 500.
        show(spot(start = 100f, len = 50f, ref = WearSpotReference.AFT_SET))
        type("Start from AFT SET (in)", "10")

        assertEquals(1, updates.size)
        assertTrue("a negative canonical start was floored", updates.single().first < 0f)
        assertKeptAndExplained(
            "Start from AFT SET (in)", "10",
            "Wear spot extends past the liner's AFT edge", "Extends past liner end",
        )
    }

    @Test
    fun `a zero length is stored and named`() {
        show(spot(start = 100f, len = 50f))
        type("Length (in)", "0")

        assertEquals(listOf(100f to 0f), updates)
        assertKeptAndExplained("Length (in)", "0", "Length must be greater than zero", "No length")
    }

    @Test
    fun `a spot inside the liner shows no issue`() {
        show(spot(start = 100f, len = 50f))
        type("Length (in)", "2")

        assertEquals(1, updates.size)
        assertTrue(
            rule.onAllNodes(hasTestTag("wear_spot_issue_s1"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            rule.onAllNodes(hasTestTag("wear_spot_note_s1"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
