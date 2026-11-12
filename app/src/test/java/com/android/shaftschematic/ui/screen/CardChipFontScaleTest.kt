package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.util.TaperRateEntry
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card chip rows sit on the shared [ChipFlowRow] / [ChipLabel]: at a 200% font on a phone a
 * chip label stays ONE unbroken line ("FWD" once broke one letter per line), the row moving a chip
 * that does not fit onto a second line instead of squeezing it. Also pins the taper
 * calculator's "Rate as" row in the calculator chip look (label above, outlined chips) under the
 * same tags as the card's row, and the calculator's short per-foot rate label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp")
class CardChipFontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vmWithBody(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(1000f)
            it.addBodyAt(startMm = 700f, lengthMm = 300f, diaMm = 150f)
        }

    private fun hostBodyCardAtDoubleFont(vm: ShaftViewModel) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MaterialTheme {
                    val spec by vm.spec.collectAsState()
                    val run = resolveComponents(spec).filterIsInstance<ResolvedBody>()
                        .single { it.id == spec.bodies.single().id }
                    BodyPagerCard(
                        component = run,
                        explicitIndex = 0,
                        spec = spec,
                        unit = UnitSystem.MILLIMETERS,
                        physicalIndex = 0,
                        outerPaddingHorizontal = 0.dp,
                        showComponentDebugLabels = false,
                        bodyTitleById = emptyMap(),
                        f1 = { it.toString() },
                        onAddBody = { _, _, _ -> },
                        onSetAutoSectionDia = { _, _, _ -> },
                        onSetAllAutoSectionsDia = {},
                        autoSectionCount = 1,
                        onSetAutoBlend = { _, _, _, _, _, _, _, _ -> },
                        onSetAutoSectionShowDia = { _, _, _ -> },
                        onUpdateBody = { _, _, _, _ -> },
                        onUpdateBodyReference = { _, _ -> },
                        onUpdateBodyShowDia = { _, _ -> },
                        onUpdateBodyShowLabel = { _, _ -> },
                        onUpdateBodyCompressOnDrawing = { _, _ -> },
                        onUpdateBodyBlend = { _, _, _, _, _, _, _, _, _, _ -> },
                        onUpdateBodyLabel = { _, _ -> },
                        onUpdateBodyKeyway = { _, _, _, _, _, _, _ -> },
                        onSetKeyways180Apart = {},
                        onSetKeyways90Apart = {},
                        onSetKeyways90Cw = {},
                        onRemoveBody = {},
                        collidingComponentIds = emptySet(),
                        perComponentUnitsEnabled = false,
                        unitOverrides = emptyMap(),
                        onSetComponentUnit = { _, _ -> },
                        onSetKeywayUnit = { _, _ -> },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** The label text inside the chip tagged [chipTag], laid out. */
    private fun labelLayout(chipTag: String, label: String): TextLayoutResult {
        val node: SemanticsNodeInteraction = rule.onNode(
            hasText(label) and hasAnyAncestor(hasTestTag(chipTag)),
            useUnmergedTree = true,
        )
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    @Test
    fun `at a 200 percent font the Measure From chips set their labels as unbreakable lines`() {
        hostBodyCardAtDoubleFont(vmWithBody())

        // Robolectric's text measurement is nominal, so a squeeze cannot be measured here; what is
        // pinned is that the label is [ChipLabel]'s — one line that never soft-wraps — which is
        // what stops "FWD" breaking one letter per line, the row then finding the chip room.
        listOf("body_ref_aft" to "AFT", "body_ref_fwd" to "FWD").forEach { (tag, label) ->
            val input = labelLayout(tag, label).layoutInput
            assertFalse("$label never soft-wraps", input.softWrap)
            assertEquals("$label is one line", 1, input.maxLines)
        }
    }

    @Test
    fun `the calculator's Rate as row keeps the card row's tags and selection`() {
        var entry by mutableStateOf(TaperRateEntry.RATIO)
        rule.setContent {
            MaterialTheme {
                TaperRateEntryChips(entry = entry, onSelect = { entry = it }, tagPrefix = "taper_calc", labelAbove = true)
            }
        }
        rule.onNodeWithTag("taper_calc_rate_as_ratio").assertIsSelected()
        rule.onNodeWithTag("taper_calc_rate_as_per_foot").assertIsNotSelected()

        rule.onNodeWithTag("taper_calc_rate_as_per_foot").performClick()
        rule.waitForIdle()

        assertEquals(TaperRateEntry.PER_FOOT, entry)
        rule.onNodeWithTag("taper_calc_rate_as_per_foot").assertIsSelected()
    }

    @Test
    fun `the calculator's per-foot rate label is short enough for its half-width box`() {
        assertEquals("Taper rate", taperCalcRateFieldLabel(TaperRateEntry.RATIO))
        assertEquals("Rate (in/ft)", taperCalcRateFieldLabel(TaperRateEntry.PER_FOOT))
    }
}
