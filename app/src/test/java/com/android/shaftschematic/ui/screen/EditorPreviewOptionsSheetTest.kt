package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.PreviewDimTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor's Preview options entry and its sheet: the preview box's corner button opens the sheet on the
 * editor, every control drives the setter it names, the grid greys while the preview draws like
 * the sheet, and the dual-unit layout shows only on a dual-unit document.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class EditorPreviewOptionsSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun state(
        sheetLayout: Boolean = true,
        dualUnits: Boolean = false,
        showDimensions: Boolean = true,
    ) = EditorPreviewOptionsState(
        showDimensions = showDimensions,
        sheetLayout = sheetLayout,
        showGrid = false,
        highlightSelection = true,
        fractionStyle = FractionStyle.DIAGONAL,
        arrowSizePt = 4f,
        dualUnits = dualUnits,
        dualUnitLayout = DualUnitLayout.INLINE,
        sBreakThresholdFrac = 0.5f,
        heightScale = 1f,
        heightSliderBase = 0.12f,
        heightSliderMaxDiaMm = 203.2f,
    )

    private val calls = mutableListOf<String>()

    private val actions = EditorPreviewOptionsActions(
        onShowDimensions = { calls += "dimensions=$it" },
        onSheetLayout = { calls += "sheet=$it" },
        onShowGrid = { calls += "grid=$it" },
        onHighlightSelection = { calls += "highlight=$it" },
        onFractionStyle = { calls += "fraction=$it" },
        onArrowSizePt = { calls += "arrow=$it" },
        onDualUnitLayout = { calls += "dual=$it" },
        onSBreakThresholdFrac = { calls += "sbreak=$it" },
        onHeightScale = { calls += "height=$it" },
        onLinersProportional = { calls += "proportional=$it" },
        onLinerCompression = { calls += "compression=$it" },
        onDimTextSize = { calls += "dimText=$it" },
    )

    private fun show(state: EditorPreviewOptionsState) {
        rule.setContent { MaterialTheme { EditorPreviewOptionsContent(state, actions) } }
        rule.waitForIdle()
    }

    @Test
    fun `each preview switch drives its own setter`() {
        show(state(sheetLayout = false))
        rule.onNodeWithTag(PREVIEW_OPT_DIMENSIONS_TAG).assertIsOn().performClick()
        rule.onNodeWithTag(PREVIEW_OPT_SHEET_LAYOUT_TAG).assertIsOff().performClick()
        rule.onNodeWithTag(PREVIEW_OPT_GRID_TAG).assertIsEnabled().performClick()
        rule.onNodeWithTag(PREVIEW_OPT_HIGHLIGHT_TAG).assertIsOn().performClick()
        assertEquals(listOf("dimensions=false", "sheet=true", "grid=true", "highlight=false"), calls)
    }

    @Test
    fun `the dimension text size chips drive their setter`() {
        show(state())
        PreviewDimTextSize.entries.forEach { rule.onNodeWithTag(previewDimTextChipTag(it)).assertExists() }
        rule.onNodeWithTag("preview_dim_text_MEDIUM").assertIsSelected()
        rule.onNodeWithTag("preview_dim_text_LARGE").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("preview_dim_text_SMALL").performScrollTo().performClick()
        assertEquals(listOf("dimText=${PreviewDimTextSize.LARGE}", "dimText=${PreviewDimTextSize.SMALL}"), calls)
    }

    @Test
    fun `the dimension text size chips grey while the dimension lines are off`() {
        show(state(showDimensions = false))
        rule.onNodeWithTag("preview_dim_text_LARGE").performScrollTo().assertIsNotEnabled().performClick()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `the grid stands down while the preview draws like the sheet`() {
        show(state(sheetLayout = true))
        rule.onNodeWithTag(PREVIEW_OPT_GRID_TAG).assertIsNotEnabled().performClick()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `the shared look controls are the sheets' own and commit through their setters`() {
        show(state())
        rule.onNodeWithTag(OPTIONS_SCOPE_NOTE_TAG).performScrollTo()
        rule.onNodeWithTag("fraction_style_stacked").performScrollTo().performClick()
        assertEquals(listOf("fraction=${FractionStyle.STACKED}"), calls)
        // A single-unit document has no dual-unit layout to set.
        rule.onAllNodesWithTag("dual_unit_layout_stacked").assertCountEquals(0)
    }

    @Test
    fun `the liner and taper compression control is the panels' own and commits through its setter`() {
        show(state())
        rule.onNodeWithTag(PREVIEW_OPT_LINER_COMPRESSION_TAG).performScrollTo()
        rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(PREVIEW_OPT_LINER_COMPRESSION_TAG)))
            .assertIsOff()
            .performClick()
        assertEquals(listOf("proportional=true"), calls)
    }

    @Test
    fun `a dual-unit document shows the dual-unit layout`() {
        show(state(dualUnits = true))
        rule.onNodeWithTag("dual_unit_layout_stacked").performScrollTo().performClick()
        assertEquals(listOf("dual=${DualUnitLayout.STACKED}"), calls)
    }

    @Test
    fun `the preview's corner button opens the sheet, and its switch is the Settings preference`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
        }
        rule.setContent {
            MaterialTheme {
                ShaftEditorRoute(
                    vm = vm,
                    onNavigateHome = {},
                    onNew = {},
                    onOpen = {},
                    onSave = {},
                    onOpenSettings = {},
                    onOpenDeveloperOptions = {},
                    onExportPdf = {},
                )
            }
        }
        rule.waitForIdle()
        val before = vm.previewSheetLayout.value
        rule.onNodeWithTag(PREVIEW_OPTIONS_BUTTON_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PREVIEW_OPTIONS_SHEET_TAG).assertExists()
        rule.onNodeWithTag(PREVIEW_OPT_SHEET_LAYOUT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(!before, vm.previewSheetLayout.value)
        rule.onNodeWithTag(PREVIEW_OPT_SHEET_LAYOUT_TAG).performClick()
        rule.waitForIdle()
        assertEquals(before, vm.previewSheetLayout.value)
    }
}
