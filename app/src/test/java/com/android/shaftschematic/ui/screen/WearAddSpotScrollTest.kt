package com.android.shaftschematic.ui.screen

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addWearSpot
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "Add spot" brings the new spot card into view. The canvas at the top of the wear detail page
 * grows a rail row per spot, so an add used to push the button and the new card down the screen
 * while the scroll stayed put — the page read as scrolling away from the section it had just
 * opened (on-device report). Through the REAL ViewModel mutator the Wear tab binds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class WearAddSpotScrollTest {

    @get:Rule
    val rule = createComposeRule()

    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val spec = ShaftSpec(overallLengthMm = 2000f, liners = listOf(liner))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun show(vm: ShaftViewModel) {
        val resolved = resolveComponents(spec)
        rule.setContent {
            val record by vm.wearRecord.collectAsState()
            MaterialTheme {
                ComponentWearDetailOverlay(
                    componentId = "L1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    wearRecord = record,
                    onAddSpot = { vm.addWearSpot(it) },
                    onUpdateSpot = { _, _, _, _, _ -> },
                    onUpdateSpotReference = { _, _ -> },
                    onRemoveSpot = {},
                    onAddPit = { _, _, _, _ -> },
                    onRemovePit = {},
                    onAddDiaReading = { _, _, _ -> },
                    onUpdateDiaReading = { _, _ -> },
                    onRemoveDiaReading = {},
                    onClose = {},
                    canUndo = false,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    onRemovePits = {},
                    onRestoreSpot = { _, _ -> },
                    onRestorePits = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `add spot scrolls the new card into view`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        // Enough spots that the list runs past the fold and the canvas has grown with them.
        repeat(4) { vm.addWearSpot("L1") }
        idle()
        show(vm)

        rule.onNodeWithTag(WEAR_ADD_SPOT_TAG).performScrollTo().performClick()
        rule.waitForIdle()
        idle()
        rule.waitForIdle()

        assertEquals(5, vm.wearRecord.value.spots.size)
        // The fifth card is the column's last item; the page must have scrolled to it.
        rule.onNodeWithText("Spot 5").assertIsDisplayed()
    }
}
