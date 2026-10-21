package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.geom.undercutSetPositions
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.addUndercut
import com.android.shaftschematic.util.DisplayUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Undercut tab's add entry point records nothing by itself: "Add undercut" asks where the cut
 * is, every row opens a strip on a PENDING "New undercut" card, and only that card's confirm puts
 * a cut in the record. A bare-shaft add opens on a window that holds no cut yet and, once
 * confirmed, carries on over the real strip holding the new cut. The drawing settings sit
 * collapsed under the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutRouteTest {

    @get:Rule
    val rule = createComposeRule()

    /** A built shaft: one body, two liners, an authored OAL. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 3000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 400f, lengthMm = 400f, odMm = 170f)
            it.addLinerAt(startMm = 1800f, lengthMm = 500f, odMm = 160f)
        }

    private fun host(vm: ShaftViewModel) {
        rule.setContent {
            MaterialTheme {
                UndercutRoute(vm = vm)
            }
        }
    }

    private fun openChooser() {
        rule.onNodeWithTag("undercut_add").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun `add asks where with one row per liner and both bare-shaft rows`() {
        val vm = vm()
        host(vm)
        openChooser()

        rule.onNodeWithTag("undercut_add_where").assertIsDisplayed()
        rule.onNodeWithText("Where is the cut?").assertIsDisplayed()
        val spec = vm.spec.value
        val titles = undercutAddLinerTitles(spec, DisplayUnits.single(vm.unit.value))
        assertEquals(2, spec.liners.size)
        spec.liners.forEach { ln ->
            rule.onNodeWithTag("undercut_add_where_liner_${ln.id}").assertIsDisplayed()
            rule.onNodeWithText(titles.getValue(ln.id)).assertIsDisplayed()
        }
        rule.onNodeWithTag("undercut_add_where_aft").assertIsDisplayed()
        rule.onNodeWithTag("undercut_add_where_fwd").assertIsDisplayed()
        assertTrue("opening the chooser records nothing", vm.undercutRecord.value.undercuts.isEmpty())
    }

    @Test
    fun `cancel closes the chooser and records nothing`() {
        val vm = vm()
        host(vm)
        openChooser()

        rule.onNodeWithTag("undercut_add_where_cancel").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("undercut_add_where").assertCountEquals(0)
        rule.onAllNodesWithText("New undercut").assertCountEquals(0)
        assertTrue(vm.undercutRecord.value.undercuts.isEmpty())
    }

    @Test
    fun `a liner row opens that liner on a pending card without recording`() {
        val vm = vm()
        host(vm)
        openChooser()

        val liner = vm.spec.value.liners.minBy { it.startFromAftMm }
        rule.onNodeWithTag("undercut_add_where_liner_${liner.id}").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("undercut_add_where").assertCountEquals(0)
        rule.onNodeWithText("New undercut").assertIsDisplayed()
        // The liner strip's own add button names its liner — the overlay opened on the liner.
        rule.onNodeWithTag("undercut_add_in_liner").assertExists()
        assertTrue("a pending card is not a recorded cut", vm.undercutRecord.value.undercuts.isEmpty())
    }

    @Test
    fun `a bare-shaft add records only on confirm and stays open on the new cut`() {
        val vm = vm()
        host(vm)
        openChooser()

        rule.onNodeWithTag("undercut_add_where_aft").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("New undercut").assertIsDisplayed()
        rule.onNodeWithTag("undercut_add_in_strip").assertExists()
        assertTrue(vm.undercutRecord.value.undercuts.isEmpty())

        rule.onNodeWithTag("undercut_confirm", useUnmergedTree = true).performClick()
        rule.waitForIdle()

        val cuts = vm.undercutRecord.value.undercuts
        assertEquals("the confirm records exactly the pending cut", 1, cuts.size)
        val (aftSet, _) = undercutSetPositions(vm.spec.value)
        assertEquals("the seed sits on the AFT S.E.T.", aftSet, cuts.single().startFromAftMm, 1e-3f)
        // The overlay moved onto the real strip holding the cut instead of closing.
        rule.onNodeWithTag("undercut_card_pager").assertExists()
        rule.onAllNodesWithText("New undercut").assertCountEquals(0)
    }

    @Test
    fun `drawing settings sit collapsed under the list and expand`() {
        val vm = vm().also { it.addUndercut(1000f, 25.4f) }
        host(vm)

        rule.onNodeWithTag("undercut_drawing_settings_toggle").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("undercut_exaggeration_slider").assertCountEquals(0)
        rule.onAllNodesWithTag("undercut_draw_to_set_switch").assertCountEquals(0)
        rule.onAllNodesWithTag("undercut_split_at_liner_edge_switch").assertCountEquals(0)

        rule.onNodeWithTag("undercut_drawing_settings_toggle").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Drawn depth").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("undercut_exaggeration_slider").assertExists()
        rule.onNodeWithTag("undercut_draw_to_set_switch").assertExists()
        rule.onNodeWithTag("undercut_split_at_liner_edge_switch").assertExists()
    }

    @Test
    fun `no recorded cuts means no drawing settings`() {
        val vm = vm()
        host(vm)
        rule.onAllNodesWithTag("undercut_drawing_settings_toggle").assertCountEquals(0)
    }
}
