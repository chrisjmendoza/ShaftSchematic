package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fraction chart opens on 32nds and its interval chips re-list the column. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class FractionChartDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private var dismissals = 0

    private fun show() {
        rule.setContent { MaterialTheme { FractionChartDialog(onDismiss = { dismissals++ }) } }
    }

    private fun rowCount(numerator: Int, denominator: Int) =
        rule.onAllNodes(hasTestTag("fraction_chart_row_${numerator}_$denominator"), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test
    fun `opens on 32nds`() {
        show()
        rule.onNodeWithTag("fraction_chart_interval_32", useUnmergedTree = true).assertIsSelected()
        assertEquals(1, rowCount(1, 32))
        assertEquals(0, rowCount(1, 64))
    }

    @Test
    fun `the interval chips re-list the column`() {
        show()
        rule.onNodeWithTag("fraction_chart_interval_64").performClick()
        rule.waitForIdle()
        assertEquals(1, rowCount(1, 64))

        rule.onNodeWithTag("fraction_chart_interval_8").performClick()
        rule.waitForIdle()
        assertEquals(1, rowCount(1, 8))
        assertEquals(0, rowCount(1, 16))
        assertEquals(0, rowCount(1, 32))
    }

    @Test
    fun `Close dismisses`() {
        show()
        rule.onNodeWithTag("fraction_chart_close").performClick()
        assertEquals(1, dismissals)
    }
}
