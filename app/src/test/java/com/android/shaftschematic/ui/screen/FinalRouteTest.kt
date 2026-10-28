package com.android.shaftschematic.ui.screen

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.adaptive.LocalSidebarPermanent
import com.android.shaftschematic.ui.viewmodel.SessionHistory
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.discardFinalSpec
import com.android.shaftschematic.ui.viewmodel.startFinalSpec
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Final Schematic tab has two states and no third: with no final drawing it offers exactly
 * one action, and with one it becomes the ordinary editor under a standing banner. What these
 * pin is the switch itself — an empty state that kept its Start button after the drawing exists
 * would offer to overwrite work with no warning, and a tab that showed the editor with no final
 * would be editing a geometry the document does not carry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class FinalRouteTest {

    @get:Rule
    val rule = createComposeRule()

    /** A built shaft: one body, one liner, an authored OAL. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        }

    private fun host(vm: ShaftViewModel) {
        rule.setContent {
            MaterialTheme {
                FinalRoute(vm = vm)
            }
        }
    }

    @Test
    fun `with no final drawing the tab offers only the start action`() {
        val vm = vm()
        assertNull("the document starts with no final drawing", vm.finalSpec.value)

        host(vm)

        rule.onNodeWithTag("final_start_button").assertIsDisplayed()
        rule.onAllNodesWithTag("final_banner").assertCountEquals(0)
    }

    @Test
    fun `starting the final drawing copies the original`() {
        val vm = vm()
        host(vm)

        rule.onNodeWithTag("final_start_button").performClick()
        rule.waitForIdle()

        assertNotNull("the tap started a final drawing", vm.finalSpec.value)
    }

    @Test
    fun `with a final drawing the tab is the editor under its banner`() {
        val vm = vm().also { it.startFinalSpec() }

        host(vm)

        rule.onNodeWithTag("final_banner").assertIsDisplayed()
        rule.onAllNodesWithTag("final_start_button").assertCountEquals(0)
    }

    /** A Discard lands on the empty state; its Undo has to be reachable right there. */
    @Test
    fun `after a discard the empty state offers undo and it brings the final back`() {
        val vm = vm()
        vm.startFinalSpec()
        // Past the coalescing window, so the discard is its own history step.
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        vm.discardFinalSpec()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(vm.finalSpec.value)

        host(vm)

        rule.onNodeWithTag("final_start_button").assertIsDisplayed()
        rule.onNodeWithTag("toolbar_history").performClick()
        rule.onNodeWithTag("history_undo_delete").performClick()
        rule.waitForIdle()

        assertNotNull("undo restored the discarded final drawing", vm.finalSpec.value)
        rule.onNodeWithTag("final_banner").assertIsDisplayed()
    }

    /** Beside the permanent sidebar the empty state has nothing to open — like every other tab. */
    @Test
    fun `the empty state drops its hamburger beside the permanent sidebar`() {
        val vm = vm()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSidebarPermanent provides true) {
                    FinalRoute(vm = vm)
                }
            }
        }

        rule.onNodeWithTag("final_start_button").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Open navigation").assertCountEquals(0)
    }

    @Test
    fun `the empty state keeps its hamburger with the overlay sidebar`() {
        host(vm())

        rule.onNodeWithContentDescription("Open navigation").assertIsDisplayed()
    }
}
