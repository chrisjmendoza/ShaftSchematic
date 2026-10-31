package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared PDF options-sheet sections: the collapsed-by-default expanders, the shade
 * group's "Explicit bodies only" sub-checkbox, and a Content chip whose election is a
 * sub-option of another.
 *
 * The expanders exist to keep the sliders above the fold, so "collapsed until tapped" is the
 * behaviour worth pinning; a sub-option that stayed tappable while its parent was off would
 * commit a preference that draws nothing.
 *
 * The scope caption is pinned here too: a sheet's rows are remote controls for the one
 * app-wide pref, and the caption is the only thing on the sheet that says so — a sheet with
 * no per-job control must not point at captions it does not carry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class PdfOptionsSectionsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun hostShade(shadedBodies: Boolean, showUndercutLineArt: Boolean = false) {
        rule.setContent {
            MaterialTheme {
                ShadeInPdfChecks(
                    pdfShadedBodies = shadedBodies,
                    pdfShadedTapers = false,
                    pdfShadedLiners = false,
                    shadeExplicitBodiesOnly = false,
                    onSetShadedBodies = {},
                    onSetShadedTapers = {},
                    onSetShadedLiners = {},
                    onSetShadeExplicitBodiesOnly = {},
                    showUndercutLineArt = showUndercutLineArt,
                )
            }
        }
    }

    @Test
    fun `shade section is collapsed until its header is tapped`() {
        hostShade(shadedBodies = true)

        rule.onNodeWithText("Shade in Components").assertExists()
        rule.onNodeWithText("Bodies").assertDoesNotExist()

        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithText("Bodies").assertExists()
        rule.onNodeWithText("Tapers").assertExists()
        rule.onNodeWithText("Liners").assertExists()
    }

    @Test
    fun `explicit-bodies-only follows the Bodies checkbox`() {
        hostShade(shadedBodies = true)
        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithTag("shade_explicit_bodies_only").assertIsEnabled()
    }

    @Test
    fun `explicit-bodies-only is untappable with body shading off`() {
        hostShade(shadedBodies = false)
        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithTag("shade_explicit_bodies_only").assertIsNotEnabled()
    }

    // `PdfPrefs.undercutLineArt` reaches ONE composer, so the row is offered on the undercut
    // sheet and hidden everywhere else rather than shown as a checkbox the page ignores.

    @Test
    fun `undercut line art is offered on the sheet that draws it`() {
        hostShade(shadedBodies = false, showUndercutLineArt = true)
        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithTag("pdf_undercut_line_art").assertExists()
        rule.onNodeWithTag("pdf_undercut_liner_outline").assertExists()
        // Cut numbers print on no sheet, so the sheet offers no row for them.
        rule.onNodeWithTag("pdf_undercut_cut_numbers").assertDoesNotExist()
        rule.onNodeWithText("Cut numbers").assertDoesNotExist()
        // "Split cuts at liner edges" is a DIMENSIONING choice, not a shading one: it lives in
        // the undercut Content block under "Distance to S.E.T." (`UndercutSplitAtLinerEdgeRow`,
        // hosted by `UndercutOptionsSheet`), never among the shading rows — under "Shade in
        // Components" it could not be found (on-device report).
        rule.onNodeWithTag("pdf_undercut_split_at_liner_edge").assertDoesNotExist()
        rule.onNodeWithText("Split cuts at liner edges").assertDoesNotExist()
    }

    @Test
    fun `undercut line art is absent on every other sheet`() {
        hostShade(shadedBodies = false, showUndercutLineArt = false)
        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithText("Liners").assertExists()
        rule.onNodeWithTag("pdf_undercut_line_art").assertDoesNotExist()
        rule.onNodeWithTag("pdf_undercut_liner_outline").assertDoesNotExist()
        rule.onNodeWithTag("pdf_undercut_cut_numbers").assertDoesNotExist()
        rule.onNodeWithTag("pdf_undercut_split_at_liner_edge").assertDoesNotExist()
    }

    @Test
    fun `measurement reference is collapsed until its header is tapped`() {
        rule.setContent {
            MaterialTheme {
                MeasurementReferenceSection(
                    pdfTieringMode = PdfTieringMode.AUTO,
                    onCommit = {},
                )
            }
        }

        rule.onNodeWithText("Measurement reference").assertExists()
        rule.onNodeWithText("AFT").assertDoesNotExist()

        rule.onNodeWithTag("options_measure_ref_expander").performClick()

        rule.onNodeWithText("Auto (closest end)").assertExists()
        rule.onNodeWithText("AFT").assertExists()
        rule.onNodeWithText("FWD").assertExists()
    }

    private fun hostScopeNote(hasJobControls: Boolean) {
        rule.setContent {
            MaterialTheme { OptionsScopeNote(hasJobControls = hasJobControls) }
        }
    }

    @Test
    fun `the scope note names the app-wide scope and points at the per-job captions`() {
        hostScopeNote(hasJobControls = true)

        rule.onNodeWithTag(OPTIONS_SCOPE_NOTE_TAG)
            .assertTextContains("app-wide", substring = true, ignoreCase = true)
        rule.onNodeWithTag(OPTIONS_SCOPE_NOTE_TAG)
            .assertTextContains("saved with this job", substring = true, ignoreCase = true)
    }

    @Test
    fun `a sheet with no per-job control promises no per-job caption`() {
        hostScopeNote(hasJobControls = false)

        rule.onNodeWithTag(OPTIONS_SCOPE_NOTE_TAG)
            .assertTextContains("app-wide", substring = true, ignoreCase = true)
        rule.onNodeWithTag(OPTIONS_SCOPE_NOTE_TAG)
            .assert(!hasText("saved with this job", substring = true, ignoreCase = true))
    }

    @Test
    fun `a Content sub-election is untappable while its parent is off`() {
        var callouts = 0
        rule.setContent {
            MaterialTheme {
                ContentChipRow {
                    ContentChip(
                        label = "Ø callouts",
                        selected = false,
                        enabled = false,
                        onClick = { callouts++ },
                        modifier = Modifier.testTag("pdf_blank_dia_callouts_toggle"),
                    )
                }
            }
        }

        rule.onNodeWithText("Ø callouts").assertIsNotEnabled()
        assertEquals(0, callouts)
    }

    // ── The sheets' section layout ──────────────────────────────────────────────
    // Both sheets build their sections from ONE ordered list (`OptionsSection`): most-used on
    // top, the shaft fit open, everything from Runout bubbles down folded, Units last. These
    // host the real sheets so a section moved, renamed, or opened by default on one of them
    // fails here rather than reading as a different sheet on device.

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    private val bareSpec = ShaftSpec(overallLengthMm = 3000f)

    private val threadedSpec = ShaftSpec(
        overallLengthMm = 3000f,
        threads = listOf(Threads(id = "t1", majorDiaMm = 50f, pitchMm = 2.5f, lengthMm = 60f)),
    )

    private fun hostSchematicSheet(
        spec: ShaftSpec,
        vm: ShaftViewModel = vm(),
        showFinalElections: Boolean = false,
        finalRunoutBubbles: Boolean = false,
        finalBeforeAfter: Boolean = false,
    ) {
        rule.setContent {
            MaterialTheme {
                PdfOptionsSheet(
                    vm = vm,
                    spec = spec,
                    pdfShowComponentTitles = true,
                    pdfTieringMode = PdfTieringMode.AUTO,
                    lineThicknessScale = 1f,
                    sBreakThresholdFrac = 0.5f,
                    arrowSizePt = 4f,
                    threadSlant = 3f,
                    threadDensity = 0.5f,
                    fractionStyle = FractionStyle.Default,
                    dualUnitLayout = DualUnitLayout.Default,
                    dualUnits = false,
                    heightScale = 1f,
                    linersProportional = false,
                    linerCompression = 1f,
                    curveLoHeightIn = 0.5f,
                    curveHiHeightIn = 1f,
                    pdfShadedBodies = false,
                    pdfShadedTapers = false,
                    pdfShadedLiners = false,
                    pdfShadeExplicitBodiesOnly = false,
                    pdfBlankDraft = false,
                    pdfBlankDiaCallouts = false,
                    showFinalRunoutBubbles = showFinalElections,
                    finalRunoutBubbles = finalRunoutBubbles,
                    finalBeforeAfter = finalBeforeAfter,
                    tuning = PreviewTuning(),
                )
            }
        }
    }

    // ── The final drawing's "Original + Final" election ─────────────────────────

    @Test
    fun `the original schematic's sheet offers no before-after election`() {
        hostSchematicSheet(bareSpec)

        rule.onNodeWithTag("final_before_after_toggle").assertDoesNotExist()
    }

    @Test
    fun `the final sheet offers Original + Final beside Runout bubbles, and a tap elects it off`() {
        val vm = vm()
        hostSchematicSheet(bareSpec, vm, showFinalElections = true, finalBeforeAfter = true)

        rule.onNodeWithTag("final_runout_bubbles_toggle").assertExists()
        rule.onNodeWithTag("final_before_after_toggle").assertIsEnabled().assertIsSelected()
        rule.onNodeWithTag("final_before_after_toggle").performClick()
        rule.waitForIdle()
        assertEquals(false, vm.finalBeforeAfter.value)
    }

    @Test
    fun `Original + Final is untappable while runout bubbles are elected`() {
        val vm = vm()
        hostSchematicSheet(bareSpec, vm, showFinalElections = true, finalRunoutBubbles = true, finalBeforeAfter = true)

        rule.onNodeWithTag("final_before_after_toggle").assertIsNotEnabled()
        assertEquals(true, vm.finalBeforeAfter.value)
    }

    /** The shared sheet as the Consolidated Output preview configures it. */
    private fun hostConsolidatedSheet(hasThreads: Boolean) {
        val vm = vm()
        rule.setContent {
            MaterialTheme {
                RunoutWearOptionsSheet(
                    lineThicknessScale = 1f,
                    pdfShadedBodies = false,
                    pdfShadedTapers = false,
                    pdfShadedLiners = false,
                    vm = vm,
                    showCouplingFaceRow = true,
                    showSBreak = true,
                    showDimensionArrows = true,
                    hasThreads = hasThreads,
                    onDualUnitsChange = {},
                    onSetBlankDraft = {},
                    showHeightSlider = true,
                    showLinerCompression = true,
                    showBubbleControls = true,
                    showMeasurementReference = true,
                )
            }
        }
    }

    // The layout position, not `getBoundsInRoot`: the sheet scrolls, and a header below the
    // test window's fold reports clipped bounds that say nothing about the order.
    private fun headerTop(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun assertSectionOrder(vararg tags: String) {
        val tops = tags.map { headerTop(it) }
        for (i in 1 until tops.size) {
            assertTrue(
                "${tags[i - 1]} (${tops[i - 1]}) sits above ${tags[i]} (${tops[i]})",
                tops[i - 1] < tops[i],
            )
        }
    }

    private val allSectionTags = OptionsSection.entries.map { it.testTag }

    @Test
    fun `the schematic sheet opens on the Shaft section holding the height slider`() {
        hostSchematicSheet(bareSpec)

        rule.onNodeWithTag("options_shaft_expander").assertExists()
        rule.onNodeWithText("Shaft height", substring = true).assertExists()
        rule.onNodeWithText("Body S-break", substring = true).assertExists()
    }

    @Test
    fun `the consolidated sheet opens on the Shaft section holding the height slider`() {
        hostConsolidatedSheet(hasThreads = false)

        rule.onNodeWithText("Shaft height", substring = true).assertExists()
    }

    @Test
    fun `the schematic sheet offers the Thread section only on a threaded shaft`() {
        hostSchematicSheet(bareSpec)

        rule.onNodeWithTag("options_thread_expander").assertDoesNotExist()
        rule.onNodeWithText("Thread slant", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the schematic sheet's Thread section is collapsed until tapped`() {
        hostSchematicSheet(threadedSpec)

        rule.onNodeWithTag("options_thread_expander").assertExists()
        rule.onNodeWithText("Thread slant", substring = true).assertDoesNotExist()

        rule.onNodeWithTag("options_thread_expander").performScrollTo().performClick()

        rule.onNodeWithTag("thread_slant_slider").assertExists()
        rule.onNodeWithTag("thread_density_slider").assertExists()
    }

    @Test
    fun `the shared sheet offers the Thread section only when told there are threads`() {
        hostConsolidatedSheet(hasThreads = false)

        rule.onNodeWithTag("options_thread_expander").assertDoesNotExist()
    }

    @Test
    fun `the shared sheet's Thread section is collapsed until tapped`() {
        hostConsolidatedSheet(hasThreads = true)

        rule.onNodeWithTag("thread_slant_slider").assertDoesNotExist()

        rule.onNodeWithTag("options_thread_expander").performScrollTo().performClick()

        rule.onNodeWithTag("thread_slant_slider").assertExists()
    }

    @Test
    fun `Lines and text is collapsed until tapped, then shows Line thickness`() {
        hostSchematicSheet(bareSpec)

        rule.onNodeWithText("Line thickness", substring = true).assertDoesNotExist()

        rule.onNodeWithTag("options_lines_expander").performScrollTo().performClick()

        rule.onNodeWithText("Line thickness", substring = true).assertExists()
        rule.onNodeWithText("Dimension arrows").assertExists()
        rule.onNodeWithText("Fractions").assertExists()
    }

    @Test
    fun `the schematic sheet lays its sections out in the documented order`() {
        hostSchematicSheet(threadedSpec)

        assertSectionOrder(
            "options_shaft_expander",
            "options_thread_expander",
            "options_lines_expander",
            "options_shade_expander",
            "options_measure_ref_expander",
            "options_units_expander",
        )
    }

    @Test
    fun `the consolidated sheet lays its sections out in the documented order`() {
        hostConsolidatedSheet(hasThreads = true)

        assertSectionOrder(
            "options_shaft_expander",
            "options_bubbles_expander",
            "options_thread_expander",
            "options_lines_expander",
            "options_shade_expander",
            "options_measure_ref_expander",
            "options_units_expander",
        )
    }

    @Test
    fun `Units is the last section on both sheets`() {
        hostConsolidatedSheet(hasThreads = true)

        val unitsTop = headerTop("options_units_expander")
        allSectionTags.filter { it != "options_units_expander" }.forEach { tag ->
            val nodes = rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                assertTrue("$tag sits above Units", headerTop(tag) < unitsTop)
            }
        }
    }

    @Test
    fun `Units is the last section on the schematic sheet`() {
        hostSchematicSheet(threadedSpec)

        val unitsTop = headerTop("options_units_expander")
        allSectionTags.filter { it != "options_units_expander" }.forEach { tag ->
            val nodes = rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                assertTrue("$tag sits above Units", headerTop(tag) < unitsTop)
            }
        }
    }

    @Test
    fun `the wear sheet opens its Wear section and omits the runout-only sections`() {
        val vm = vm()
        rule.setContent {
            MaterialTheme {
                RunoutWearOptionsSheet(
                    lineThicknessScale = 1f,
                    pdfShadedBodies = false,
                    pdfShadedTapers = false,
                    pdfShadedLiners = false,
                    vm = vm,
                    showWearControls = true,
                    showHeightSlider = true,
                    showShadeExplicitBodiesOnly = false,
                    onDualUnitsChange = {},
                    onSetBlankDraft = {},
                )
            }
        }

        rule.onNodeWithTag("wear_strip_size_slider").assertExists()
        rule.onNodeWithTag("options_bubbles_expander").assertDoesNotExist()
        rule.onNodeWithTag("options_measure_ref_expander").assertDoesNotExist()
        assertSectionOrder(
            "options_shaft_expander",
            "options_wear_expander",
            "options_lines_expander",
            "options_shade_expander",
            "options_units_expander",
        )
    }

    @Test
    fun `the undercut sheet has no Shaft section`() {
        val vm = vm()
        rule.setContent {
            MaterialTheme {
                RunoutWearOptionsSheet(
                    lineThicknessScale = 1f,
                    pdfShadedBodies = false,
                    pdfShadedTapers = false,
                    pdfShadedLiners = false,
                    vm = vm,
                    showShadeExplicitBodiesOnly = false,
                    showUndercutLineArt = true,
                    onDualUnitsChange = {},
                    onSetBlankDraft = {},
                )
            }
        }

        rule.onNodeWithTag("options_shaft_expander").assertDoesNotExist()
        assertSectionOrder(
            "options_lines_expander",
            "options_shade_expander",
            "options_units_expander",
        )
    }

    @Test
    fun `the undercut sheet offers the dimension arrow sizes its strip rails follow`() {
        val vm = vm()
        rule.setContent {
            MaterialTheme {
                UndercutOptionsSheet(vm = vm, blankDraft = false, onSetBlankDraft = {}, showThreadControls = false)
            }
        }

        rule.onNodeWithTag("options_shaft_expander").assertDoesNotExist()
        rule.onNodeWithTag("options_lines_expander").performScrollTo().performClick()
        rule.onNodeWithText("Dimension arrows").assertExists()
        rule.onNodeWithText("Line thickness", substring = true).assertExists()
        rule.onNodeWithText("Fractions").assertExists()
    }

    @Test
    fun `the undercut sheet shows its Thread section only when it draws a thread`() {
        val vm = vm()
        var draws by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                UndercutOptionsSheet(vm = vm, blankDraft = false, onSetBlankDraft = {}, showThreadControls = draws)
            }
        }
        rule.onNodeWithTag("options_thread_expander").assertDoesNotExist()

        draws = true
        rule.waitForIdle()
        rule.onNodeWithTag("options_thread_expander").assertExists()
    }
}
