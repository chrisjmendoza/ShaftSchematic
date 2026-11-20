package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A liner can be entered by where it starts and where it ends, both from the same end of the
 * shaft — the length is End − Start (on-device request). Card and Add dialog alike (parity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class LinerEndEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val inch = 25.4f
    private val oalMm = 200f * inch

    private fun tagged(tag: String): SemanticsNodeInteraction =
        rule.onNode(hasTestTag(tag) and hasSetTextAction(), useUnmergedTree = true)

    private fun labelled(label: String): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(label))

    private fun boxText(node: SemanticsNodeInteraction): String =
        node.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /* ── The pure rule ───────────────────────────────────────────────────────── */

    @Test
    fun `the length is end minus start`() {
        assertEquals(30f, linerSpanFromEnd(10f, "40", 40f).lengthMm!!, 0f)
        assertNull(linerSpanFromEnd(10f, "40", 40f).error)
    }

    @Test
    fun `an end at or before the start makes no length and says why`() {
        assertEquals(LINER_END_BEFORE_START_MSG, linerSpanFromEnd(10f, "10", 10f).error)
        assertEquals(LINER_END_BEFORE_START_MSG, linerSpanFromEnd(10f, "5", 5f).error)
        assertNull(linerSpanFromEnd(10f, "5", 5f).lengthMm)
    }

    @Test
    fun `blank, unreadable or a missing start make nothing and no error`() {
        assertEquals(LinerEndSpan(null, null), linerSpanFromEnd(10f, "", null))
        assertEquals(LinerEndSpan(null, null), linerSpanFromEnd(10f, "abc", null))
        assertEquals(LinerEndSpan(null, null), linerSpanFromEnd(null, "40", 40f))
    }

    /* ── AddLinerDialog ──────────────────────────────────────────────────────── */

    private class Submitted(val startMm: Float, val lengthMm: Float, val ref: LinerAuthoredReference)

    private fun showDialog(onSubmit: (Submitted) -> Unit) {
        rule.setContent {
            MaterialTheme {
                AddLinerDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = oalMm),
                    onSubmit = { start, len, _, ref, _, _ -> onSubmit(Submitted(start, len, ref)) },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun clickAdd() {
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `the dialog takes a start and an end from AFT`() {
        var got: Submitted? = null
        showDialog { got = it }
        labelled("Start from AFT (in)").performTextReplacement("10")
        tagged("add_liner_end").performTextReplacement("40")
        rule.waitForIdle()
        assertEquals("30", boxText(tagged("add_liner_length")))
        clickAdd()
        assertNotNull("Add submitted", got)
        assertEquals(10f * inch, got!!.startMm, 1e-3f)
        assertEquals(30f * inch, got!!.lengthMm, 1e-3f)
    }

    @Test
    fun `the dialog takes a start and an end from FWD`() {
        var got: Submitted? = null
        showDialog { got = it }
        rule.onNode(hasText("FWD") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
        labelled("Start from FWD (in)").performTextReplacement("5")
        tagged("add_liner_end").performTextReplacement("25")
        rule.waitForIdle()
        clickAdd()
        assertNotNull("Add submitted", got)
        assertEquals(LinerAuthoredReference.FWD, got!!.ref)
        assertEquals(20f * inch, got!!.lengthMm, 1e-3f)
        // The FWD edge sits 5" from the FWD face.
        assertEquals(oalMm - 25f * inch, got!!.startMm, 1e-2f)
    }

    @Test
    fun `a typed end holds when the start changes, and a typed length takes over again`() {
        var got: Submitted? = null
        showDialog { got = it }
        labelled("Start from AFT (in)").performTextReplacement("10")
        tagged("add_liner_end").performTextReplacement("40")
        labelled("Start from AFT (in)").performTextReplacement("12")
        rule.waitForIdle()
        assertEquals("28", boxText(tagged("add_liner_length")))
        tagged("add_liner_length").performTextReplacement("6")
        rule.waitForIdle()
        assertEquals("18", boxText(tagged("add_liner_end")))
        clickAdd()
        assertEquals(6f * inch, got!!.lengthMm, 1e-3f)
    }

    /* ── The liner card ──────────────────────────────────────────────────────── */

    private fun showCard(liner: Liner, onUpdate: (Float, Float) -> Unit) {
        val spec = ShaftSpec(overallLengthMm = oalMm, liners = listOf(liner))
        rule.setContent {
            MaterialTheme {
                LinerPagerCard(
                    component = ResolvedLiner(
                        id = liner.id, startMmPhysical = liner.startFromAftMm,
                        endMmPhysical = liner.startFromAftMm + liner.lengthMm, odMm = liner.odMm,
                    ),
                    explicitIndex = 0,
                    spec = spec,
                    unit = UnitSystem.INCHES,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    linerTitleById = mapOf(liner.id to "Liner"),
                    f1 = { "%.1f".format(it) },
                    onUpdateLiner = { _, start, len, _ -> onUpdate(start, len) },
                    onUpdateLinerShowDia = { _, _ -> },
                    onUpdateLinerShowLabel = { _, _ -> },
                    onUpdateLinerShoulder = { _, _, _, _, _ -> },
                    linerShouldersEnabled = false,
                    onUpdateLinerLabel = { _, _ -> },
                    onUpdateLinerReference = { _, _ -> },
                    onRemoveLiner = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the card reads the end back and a typed end keeps the start`() {
        val commits = mutableListOf<Pair<Float, Float>>()
        val liner = Liner(id = "l1", startFromAftMm = 10f * inch, lengthMm = 20f * inch, odMm = 228.6f)
        showCard(liner) { s, l -> commits += s to l }
        assertEquals("30", boxText(tagged("liner_end")))
        tagged("liner_end").performClick()
        tagged("liner_end").performTextReplacement("45 1/2")
        tagged("liner_end").performImeAction()
        rule.waitForIdle()
        assertEquals(1, commits.size)
        assertEquals(10f * inch, commits.single().first, 0f)
        assertEquals(35.5f * inch, commits.single().second, 1e-3f)
    }

    @Test
    fun `under FWD the card's end is the AFT edge and the FWD edge stays put`() {
        val commits = mutableListOf<Pair<Float, Float>>()
        // FWD edge 5" from the FWD face, 20" long: End from FWD reads 25.
        val liner = Liner(
            id = "l1", startFromAftMm = oalMm - 25f * inch, lengthMm = 20f * inch, odMm = 228.6f,
            authoredReference = LinerAuthoredReference.FWD,
        )
        showCard(liner) { s, l -> commits += s to l }
        assertEquals("25", boxText(tagged("liner_end")))
        tagged("liner_end").performClick()
        tagged("liner_end").performTextReplacement("35")
        tagged("liner_end").performImeAction()
        rule.waitForIdle()
        assertEquals(30f * inch, commits.single().second, 1e-3f)
        assertEquals(oalMm - 35f * inch, commits.single().first, 1e-2f)
    }
}
