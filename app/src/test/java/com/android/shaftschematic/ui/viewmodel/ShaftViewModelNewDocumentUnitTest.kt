package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.encodeTemplateJson
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A new drawing starts in the Settings → Units default and is unit-locked from birth: the
 * document owns its unit, so the setting changing later never flips an open or saved drawing.
 * `newDocument` used to hard-code inches (on-device report: a metric shop's new drawings came
 * up in inches). The same seed serves the Settings "Dual-unit display" default.
 *
 * These drive the REAL [ShaftViewModel] on Robolectric. The default is the value the
 * DataStore collector last delivered ([ShaftViewModel._defaultUnit]); the tests set that
 * mirror directly rather than race the asynchronous collector, which is exactly the seam
 * `newDocument` reads — a synchronous DataStore read is what the design rules out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelNewDocumentUnitTest {

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `a metric default makes a new drawing metric and locked`() {
        val vm = vm()
        vm._defaultUnit.value = UnitSystem.MILLIMETERS

        vm.newDocument()

        assertEquals(UnitSystem.MILLIMETERS, vm.unit.value)
        assertTrue("the document owns its unit from birth", vm.unitLocked.value)
    }

    @Test
    fun `an inch default makes a new drawing inches and locked`() {
        val vm = vm()
        vm._defaultUnit.value = UnitSystem.INCHES

        vm.newDocument()

        assertEquals(UnitSystem.INCHES, vm.unit.value)
        assertTrue(vm.unitLocked.value)
    }

    @Test
    fun `the default changing later does not flip the open drawing`() {
        val vm = vm()
        vm._defaultUnit.value = UnitSystem.MILLIMETERS
        vm.newDocument()

        // What the collector does on a later Settings change: remember the default, and
        // apply it to the live unit only while the document is not locked.
        vm._defaultUnit.value = UnitSystem.INCHES
        if (!vm.unitLocked.value) vm.setUnit(UnitSystem.INCHES, persist = false)

        assertEquals("a locked document keeps its unit", UnitSystem.MILLIMETERS, vm.unit.value)
        assertEquals("the next new drawing takes the new default", UnitSystem.INCHES,
            vm.also { it.newDocument() }.unit.value)
    }

    @Test
    fun `dual-unit display seeds from its Settings default`() {
        val vm = vm()
        vm._dualUnitsDefault.value = true
        vm.newDocument()
        assertTrue(vm.dualUnits.value)

        vm._dualUnitsDefault.value = false
        vm.newDocument()
        assertFalse(vm.dualUnits.value)
    }

    @Test
    fun `a template keeps its own authored unit but takes the dual default`() {
        // The unit travels with the template (the geometry was authored in it); dual display
        // is per-job, so it seeds the way a blank drawing's does.
        val vm = vm()
        vm._defaultUnit.value = UnitSystem.MILLIMETERS
        vm._dualUnitsDefault.value = true

        vm.applyTemplate(
            encodeTemplateJson(
                spec = ShaftSpec(overallLengthMm = 3000f),
                preferredUnit = UnitSystem.INCHES,
                unitLocked = true,
            )
        )

        assertEquals(UnitSystem.INCHES, vm.unit.value)
        assertTrue(vm.unitLocked.value)
        assertTrue(vm.dualUnits.value)
    }
}
