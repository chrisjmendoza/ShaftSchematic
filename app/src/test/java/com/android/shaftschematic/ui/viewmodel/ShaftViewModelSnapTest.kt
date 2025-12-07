package com.android.shaftschematic.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShaftViewModelSnapTest {

    @Test
    fun snapChainFrom_snaps_forward_from_anchor() {
        val vm = ShaftViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        // Build a simple chain where components start unaligned
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)),
            tapers = listOf(Taper(id = "t1", startFromAftMm = 110f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 40f))
        )
        // Seed state
        vm.setOverallIsManual(true)
        vm.setUnitLocked(true)
        // set spec via internal flow
        val field = ShaftViewModel::class.java.getDeclaredField("_spec")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<ShaftSpec>
        flow.value = spec
        // UI order determines anchor lookup correctness
        val orderField = ShaftViewModel::class.java.getDeclaredField("_componentOrder")
        orderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val orderFlow = orderField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<List<ComponentKey>>
        orderFlow.value = listOf(
            ComponentKey(id = "b1", kind = ComponentKind.BODY),
            ComponentKey(id = "t1", kind = ComponentKind.TAPER)
        )

        // Act: snap forward from body
        vm.snapChainFrom(ComponentKey("b1", ComponentKind.BODY))

        val snapped = vm.spec.value
        val body = snapped.bodies.single()
        val taper = snapped.tapers.single()
        // Body remains at 0..100
        assertEquals(0f, body.startFromAftMm)
        assertEquals(100f, body.lengthMm)
        // Taper snaps to body end
        assertEquals(100f, taper.startFromAftMm)
    }
}
