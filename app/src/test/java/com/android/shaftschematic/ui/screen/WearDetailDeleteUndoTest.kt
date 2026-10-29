package com.android.shaftschematic.ui.screen

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addWearPit
import com.android.shaftschematic.ui.viewmodel.addWearSpot
import com.android.shaftschematic.ui.viewmodel.removeWearPit
import com.android.shaftschematic.ui.viewmodel.removeWearPits
import com.android.shaftschematic.ui.viewmodel.removeWearSpot
import com.android.shaftschematic.ui.viewmodel.restoreWearPits
import com.android.shaftschematic.ui.viewmodel.restoreWearSpot
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The wear detail page's confirm-free deletes — "Clear all pits" and a wear spot's trash — offer a
 * snackbar Undo that puts back exactly what went (emulator QA: both deleted at once with no way
 * back from the page), and the page's top bar carries the document Undo / Redo. Through the REAL
 * ViewModel mutators the Wear tab binds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class WearDetailDeleteUndoTest {

    @get:Rule
    val rule = createComposeRule()

    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val spec = ShaftSpec(overallLengthMm = 2000f, liners = listOf(liner))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun vmWithWear(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.addWearSpot("L1")
        vm.addWearPit("L1", 100f, 0.5f, PitSize.SMALL)
        vm.addWearPit("other", 50f, 0.5f, PitSize.LARGE) // another component's pit sits between
        vm.addWearPit("L1", 300f, 0.4f, PitSize.LARGE)
        idle()
        return vm
    }

    private fun show(vm: ShaftViewModel) {
        val resolved = resolveComponents(spec)
        rule.setContent {
            val record by vm.wearRecord.collectAsState()
            val canUndo by vm.canUndo.collectAsState()
            val canRedo by vm.canRedo.collectAsState()
            MaterialTheme {
                ComponentWearDetailOverlay(
                    componentId = "L1",
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    wearRecord = record,
                    onAddSpot = {},
                    onUpdateSpot = { _, _, _, _, _ -> },
                    onUpdateSpotReference = { _, _ -> },
                    onRemoveSpot = { vm.removeWearSpot(it) },
                    onAddPit = { _, _, _, _ -> },
                    onRemovePit = { vm.removeWearPit(it) },
                    onAddDiaReading = { _, _, _ -> },
                    onUpdateDiaReading = { _, _ -> },
                    onRemoveDiaReading = {},
                    onClose = {},
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = vm::undoEdit,
                    onRedo = vm::redoEdit,
                    onRemovePits = { vm.removeWearPits(it) },
                    onRestoreSpot = { s, i -> vm.restoreWearSpot(s, i) },
                    onRestorePits = { vm.restoreWearPits(it) },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `clear all pits offers an Undo that puts the exact pits back in order`() {
        val vm = vmWithWear()
        val before = vm.wearRecord.value.pits
        show(vm)

        rule.onNodeWithText("Clear all pits").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("other"), vm.wearRecord.value.pits.map { it.componentId })

        rule.onNodeWithText("2 pits cleared").assertExists()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(before, vm.wearRecord.value.pits)
    }

    @Test
    fun `a deleted wear spot comes back verbatim from the snackbar`() {
        val vm = vmWithWear()
        val spot = vm.wearRecord.value.spots.single()
        show(vm)

        rule.onNodeWithContentDescription("Delete spot 1").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(vm.wearRecord.value.spots.isEmpty())

        rule.onNodeWithText("Wear spot deleted").assertExists()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(listOf(spot), vm.wearRecord.value.spots)
    }

    @Test
    fun `a restore after a history undo already put the spot back is a no-op`() {
        val vm = vmWithWear()
        val spot = vm.wearRecord.value.spots.single()
        vm.restoreWearSpot(spot, 0)
        assertEquals(listOf(spot), vm.wearRecord.value.spots)
    }

    @Test
    fun `the page's top bar carries the document undo`() {
        val vm = vmWithWear()
        show(vm)
        rule.onNodeWithTag("wear_detail_undo").assertExists()
        rule.onNodeWithTag("wear_detail_redo").assertExists()
    }
}
