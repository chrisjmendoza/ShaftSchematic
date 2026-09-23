package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sidebar's tools group and its order: Keyway calculator · Taper calculator ·
 * Unit converter · Help & FAQ · Settings.
 *
 * The two calculators are adjacent (a shop calculation is reached for as one kind of thing),
 * and Help is reachable from the main menu rather than only from inside Settings, sitting
 * directly above it — reference content is reached for mid-job, so burying it behind the page
 * for changing things is the failure this pins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class EditorSidebarHelpTest {

    @get:Rule
    val rule = createComposeRule()

    private var helpTaps = 0
    private var taperCalcTaps = 0
    private var closes = 0

    /** The tools group, top to bottom. */
    private val toolOrder = listOf(
        "Keyway calculator",
        "Taper calculator",
        "Unit converter",
        "Help & FAQ",
        "Settings",
    )

    private fun hostSidebar() {
        rule.setContent {
            MaterialTheme {
                EditorSidebarOverlay(
                    open = true,
                    selectedTab = EditorTab.SCHEMATIC,
                    runoutEnabled = true,
                    onOpen = {},
                    onClose = { closes++ },
                    onTabSelected = {},
                    onHome = {},
                    onSettings = {},
                    onTaperCalculator = { taperCalcTaps++ },
                    onHelp = { helpTaps++ },
                )
            }
        }
    }

    @Test
    fun `tools group shows every utility`() {
        hostSidebar()

        toolOrder.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `the utilities keep their order, calculators adjacent and Help above Settings`() {
        hostSidebar()

        val tops = toolOrder.map { it to rule.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        tops.zipWithNext().forEach { (above, below) ->
            assertTrue("${below.first} must sit below ${above.first}", below.second > above.second)
        }
    }

    @Test
    fun `tapping Help opens help and closes the sidebar`() {
        hostSidebar()

        rule.onNodeWithText("Help & FAQ").performClick()

        assertEquals(1, helpTaps)
        assertEquals(1, closes)
    }

    @Test
    fun `tapping the taper calculator opens it and closes the sidebar`() {
        hostSidebar()

        rule.onNodeWithText("Taper calculator").performClick()

        assertEquals(1, taperCalcTaps)
        assertEquals(1, closes)
    }
}
