package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
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
    }

    @Test
    fun `undercut line art is absent on every other sheet`() {
        hostShade(shadedBodies = false, showUndercutLineArt = false)
        rule.onNodeWithTag("options_shade_expander").performClick()

        rule.onNodeWithText("Liners").assertExists()
        rule.onNodeWithTag("pdf_undercut_line_art").assertDoesNotExist()
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
}
