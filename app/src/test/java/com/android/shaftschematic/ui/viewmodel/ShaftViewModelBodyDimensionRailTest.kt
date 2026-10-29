package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [updateBodyShowDimensionRail] — the body card's "Show dimension rail". A new body is created
 * unset (it follows its position); a tap stores an authored value; a write of the value
 * already stored produces no new spec, so a recomposition can never mark the document dirty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelBodyDimensionRailTest {

    private fun vm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `a new body follows its position and the toggle stores an authored value`() {
        val vm = vm()
        vm.addBodyAt(startMm = 0f, lengthMm = 304.8f, diaMm = 127f)
        assertNull(vm.spec.value.bodies.single().showDimensionRail)

        vm.updateBodyShowDimensionRail(0, false)
        assertEquals(false, vm.spec.value.bodies.single().showDimensionRail)

        vm.updateBodyShowDimensionRail(0, true)
        assertEquals(true, vm.spec.value.bodies.single().showDimensionRail)
    }

    @Test
    fun `re-setting the same value is a no-op`() {
        val vm = vm()
        vm.addBodyAt(startMm = 0f, lengthMm = 304.8f, diaMm = 127f)
        vm.updateBodyShowDimensionRail(0, true)

        val before = vm.spec.value
        vm.updateBodyShowDimensionRail(0, true)
        assertTrue("the same value must not produce a new spec", before === vm.spec.value)
    }
}
