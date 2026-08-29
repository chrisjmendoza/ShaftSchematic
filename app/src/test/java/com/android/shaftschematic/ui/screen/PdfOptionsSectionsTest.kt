package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.shaftschematic.settings.PdfTieringMode
import org.junit.Assert.assertEquals
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class PdfOptionsSectionsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun hostShade(shadedBodies: Boolean) {
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
}
