package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.autoBodyRuns
import com.android.shaftschematic.ui.resolved.resolveComponents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Readings on a bare-shaft body survive a neighbour's length correction, through the REAL
 * [ShaftViewModel] seam and recorder (emulator QA: a 7/16" AFT-liner correction took the auto
 * body's two typed TIRs, its "−" and its drag pin off the Runout tab). The job is the device
 * file the report was made on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelAutoBodyKeysTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val oldId = "auto_body_1793.875_5435.600"
    private val aftLinerId = "26323d09-7e38-4a69-85e7-b8067936d81b"

    private fun openJob(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        val raw = requireNotNull(
            javaClass.classLoader?.getResource("fixtures/runout_auto_body_polar_ranger.shaft")
        ).readText()
        vm.importJson(raw)
        idle()
        return vm
    }

    private fun ShaftViewModel.lengthenAftLinerTo(lengthMm: Float) {
        val index = spec.value.liners.indexOfFirst { it.id == aftLinerId }
        val liner = spec.value.liners[index]
        updateLiner(index, liner.startFromAftMm, lengthMm, liner.odMm)
        idle()
    }

    private fun ShaftViewModel.autoIdAt(mm: Float): String =
        autoBodyRuns(resolveComponents(spec.value)).single { mm >= it.startMm && mm < it.endMm }.id

    @Test
    fun `a liner length correction keeps the auto body's readings, and ONE undo restores both`() {
        val vm = openJob()
        assertEquals(oldId, vm.autoIdAt(3000f))
        assertFalse(vm.canUndo.value)

        vm.lengthenAftLinerTo(1219.2f) // 47 9/16" → 48"
        val newId = vm.autoIdAt(3000f)
        assertNotEquals(oldId, newId)

        val readings = vm.runoutReadings.value
        assertEquals(0.0381f, readings.find(newId, 1)!!.valueMm!!, 0f)
        assertEquals(12, readings.find(newId, 1)!!.highSpotHalfHours)
        assertEquals(0.396875f, readings.find(newId, 3)!!.valueMm!!, 0f)
        assertNull(readings.find(oldId, 1))
        assertEquals(4, vm.runoutConfig.value.componentOverrides[newId])
        assertFalse(oldId in vm.runoutConfig.value.componentOverrides)

        vm.undoEdit()
        idle()
        assertEquals(1208.0875f, vm.spec.value.liners.single { it.id == aftLinerId }.lengthMm, 1e-3f)
        assertEquals(0.0381f, vm.runoutReadings.value.find(oldId, 1)!!.valueMm!!, 0f)
        assertEquals(0.396875f, vm.runoutReadings.value.find(oldId, 3)!!.valueMm!!, 0f)
        assertNull(vm.runoutReadings.value.find(newId, 1))
        assertEquals(4, vm.runoutConfig.value.componentOverrides[oldId])
        assertFalse(newId in vm.runoutConfig.value.componentOverrides)
        assertFalse("one undo took the edit and the re-key together", vm.canUndo.value)

        vm.redoEdit()
        idle()
        assertEquals(0.0381f, vm.runoutReadings.value.find(newId, 1)!!.valueMm!!, 0f)
    }

    /** Ticks the auto card's "Explicit body" box: the card hands addBodyAt the run's span and Ø. */
    private fun ShaftViewModel.promoteAutoAt(mm: Float): String {
        val run = resolveComponents(spec.value).filterIsInstance<ResolvedBody>().single { it.id == autoIdAt(mm) }
        addBodyAt(run.startMmPhysical, run.endMmPhysical - run.startMmPhysical, run.diaMm)
        idle()
        return spec.value.bodies.single { it.startFromAftMm == run.startMmPhysical }.id
    }

    @Test
    fun `promoting the auto body keeps its readings, and ONE undo restores both`() {
        val vm = openJob()
        val bodyId = vm.promoteAutoAt(3000f)

        val readings = vm.runoutReadings.value
        assertEquals(0.0381f, readings.find(bodyId, 1)!!.valueMm!!, 0f)
        assertEquals(0.396875f, readings.find(bodyId, 3)!!.valueMm!!, 0f)
        assertNull(readings.find(oldId, 1))
        assertEquals(4, vm.runoutConfig.value.componentOverrides[bodyId])

        vm.undoEdit()
        idle()
        assertTrue(vm.spec.value.bodies.none { it.id == bodyId })
        assertEquals(0.0381f, vm.runoutReadings.value.find(oldId, 1)!!.valueMm!!, 0f)
        assertNull(vm.runoutReadings.value.find(bodyId, 1))
        assertFalse("one undo took the promotion and the re-key together", vm.canUndo.value)
    }

    @Test
    fun `demoting the promoted body hands its readings back to the auto run`() {
        val vm = openJob()
        val bodyId = vm.promoteAutoAt(3000f)
        vm.reseedEditHistory() // the demotion is its own step, not coalesced with the promotion
        vm.removeBody(bodyId)
        idle()

        assertEquals(oldId, vm.autoIdAt(3000f))
        val readings = vm.runoutReadings.value
        assertEquals(0.0381f, readings.find(oldId, 1)!!.valueMm!!, 0f)
        assertEquals(0.396875f, readings.find(oldId, 3)!!.valueMm!!, 0f)
        assertNull(readings.find(bodyId, 1))
        assertEquals(4, vm.runoutConfig.value.componentOverrides[oldId])

        vm.undoEdit()
        idle()
        assertEquals(0.0381f, vm.runoutReadings.value.find(bodyId, 1)!!.valueMm!!, 0f)
    }

    @Test
    fun `a drag pin and a pit on the auto body keep their shaft position`() {
        val vm = openJob()
        vm.setRunoutStationPosition(oldId, 0, 500f)
        vm.addWearPit(oldId, axialMm = 800f, acrossFrac = 0.5f, size = PitSize.SMALL)
        idle()
        vm.reseedEditHistory()

        vm.lengthenAftLinerTo(1219.2f)
        val newId = vm.autoIdAt(3000f)
        val shift = 1793.875f - (585.7875f + 1219.2f)

        val pin = vm.runoutStationPlacements.value.placements.single { it.componentId == newId }
        assertEquals(500f + shift, pin.axialMm, 1e-3f)
        val pit = vm.wearRecord.value.pits.single()
        assertEquals(newId, pit.componentId)
        assertEquals(800f + shift, pit.axialMm, 1e-3f)
        assertTrue(vm.runoutStationPlacements.value.placements.none { it.componentId == oldId })
    }
}
