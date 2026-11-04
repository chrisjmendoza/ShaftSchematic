package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.input.TaperCalcValues
import com.android.shaftschematic.ui.input.TaperSide
import com.android.shaftschematic.ui.screen.addTaperFromCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The taper calculator's Apply on the real [ShaftViewModel], since its promises live where the
 * history recorder and the two-drawing seam run: one Undo per Apply, and it lands on the drawing
 * it was told to and no other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelTaperCalcApplyTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun inch(v: Float) = v * MM_PER_IN.toFloat()
    private val values = TaperCalcValues(inch(8f), inch(7f), inch(16f), typedRateText = null)
    private val oal get() = inch(300f)

    private fun settledVm(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(oal)
        vm.addTaperAt(0f, inch(12f), inch(6f), inch(7f), "1:12", rateManual = false)
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        return vm
    }

    private val ShaftViewModel.onlyTaper: Taper get() = spec.value.tapers.single()

    @Test
    fun `one Undo takes the whole Apply back`() {
        val vm = settledVm()
        val before = vm.spec.value
        val id = vm.onlyTaper.id

        vm.applyTaperCalcValues(id, values)
        idle()

        assertEquals(inch(16f), vm.onlyTaper.lengthMm, 0f)
        assertEquals("1:16", vm.onlyTaper.taperRateText)
        assertEquals("the OAL never moves", oal, vm.spec.value.overallLengthMm, 0f)
        assertEquals("its card shows the result", id, vm.selectedComponentId.value)

        val expected = vm.currentEditState()
        assertTrue(vm.undoEditIfStillAt(expected))
        idle()
        assertEquals(before, vm.spec.value)
    }

    @Test
    fun `an Apply on the Final drawing leaves the original untouched, and the reverse`() {
        val vm = settledVm()
        vm.startFinalSpec()
        idle()
        val id = vm.onlyTaper.id
        val original = vm.spec.value

        vm.applyTaperCalcValues(id, values, SpecTarget.FINAL)
        idle()
        assertEquals(original, vm.spec.value)
        assertEquals(inch(16f), vm.finalSpec.value!!.tapers.single().lengthMm, 0f)

        val final = vm.finalSpec.value
        vm.applyTaperCalcValues(id, values.copy(lengthMm = inch(20f)), SpecTarget.ORIGINAL)
        idle()
        assertEquals(final, vm.finalSpec.value)
        assertEquals(inch(20f), vm.onlyTaper.lengthMm, 0f)
    }

    @Test
    fun `an Apply naming a taper that is gone writes nothing`() {
        val vm = settledVm()
        val before = vm.spec.value
        val selected = vm.selectedComponentId.value
        vm.applyTaperCalcValues("gone", values)
        idle()
        assertEquals(before, vm.spec.value)
        assertEquals(selected, vm.selectedComponentId.value)
    }

    @Test
    fun `a new taper at the free end goes through the add path and Undo removes it`() {
        val vm = settledVm()
        val before = vm.spec.value

        vm.addTaperFromCalc(values, TaperSide.FWD)
        idle()

        val added = vm.spec.value.tapers.first { it.id != before.tapers.single().id }
        assertEquals(oal - inch(16f), added.startFromAftMm, 0.01f)
        assertEquals("large end at its AFT face", inch(8f), added.startDiaMm, 0f)
        assertEquals(inch(7f), added.endDiaMm, 0f)
        assertEquals(false, added.taperRateManual)
        assertEquals(added.id, vm.selectedComponentId.value)

        assertTrue(vm.undoEditIfStillAt(vm.currentEditState()))
        idle()
        assertEquals(before, vm.spec.value)
    }

    @Test
    fun `a final write with no final drawing is the seam's no-op`() {
        val vm = settledVm()
        assertNull(vm.finalSpec.value)
        val before = vm.spec.value
        vm.applyTaperCalcValues(vm.onlyTaper.id, values, SpecTarget.FINAL)
        idle()
        assertEquals(before, vm.spec.value)
        assertNull(vm.finalSpec.value)
    }
}
