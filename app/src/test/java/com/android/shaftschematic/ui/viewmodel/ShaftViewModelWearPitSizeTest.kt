package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.geom.PIT_SIZE_FACTOR_DEFAULT
import com.android.shaftschematic.geom.pitSizeFactor
import com.android.shaftschematic.model.PitSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The wear inspection page's X-size slider (on-device request: pick the size, then place; a
 * placed X keeps its size until it is removed): the slider is session state, and each pit stores
 * the size it was placed at.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelWearPitSizeTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `each pit keeps the size it was placed at`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(PIT_SIZE_FACTOR_DEFAULT, vm.wearPitBrushSizeFactor.value, 0f)

        vm.addWearPit("L1", 100f, 0.5f, sizeFactor = 0.75f)
        vm.setWearPitBrushSizeFactor(2.5f)
        vm.addWearPit("L1", 200f, 0.5f, sizeFactor = vm.wearPitBrushSizeFactor.value)
        idle()

        val (first, second) = vm.wearRecord.value.pits
        assertEquals(0.75f, first.sizeFactor!!, 0f)
        assertEquals(PitSize.SMALL, first.size)
        assertEquals(2.5f, second.sizeFactor!!, 0f)
        assertEquals(PitSize.LARGE, second.size)
        // Moving the slider afterwards changes no placed pit.
        vm.setWearPitBrushSizeFactor(1f)
        idle()
        assertEquals(0.75f, pitSizeFactor(vm.wearRecord.value.pits[0]), 0f)
        assertEquals(2.5f, pitSizeFactor(vm.wearRecord.value.pits[1]), 0f)
    }

    @Test
    fun `the slider value snaps to its steps`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setWearPitBrushSizeFactor(1.6f)
        assertEquals(1.5f, vm.wearPitBrushSizeFactor.value, 0f)
    }
}
