package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.startFinalSpec
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
