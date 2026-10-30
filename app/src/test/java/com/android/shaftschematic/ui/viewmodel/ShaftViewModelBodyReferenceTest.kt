package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.LinerAuthoredReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An explicit body's "Measure From: AFT | FWD" reference through the REAL [ShaftViewModel]:
 * a switch writes only the reference (never the stored start), an unchanged switch is no edit at
 * all, the FINAL target is honoured, `addBodyAt` stores the dialog's choice, and an OAL commit
 * carries a FWD body with the FWD end and names it in the snackbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelBodyReferenceTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(1000f)
            it.addBodyAt(startMm = 700f, lengthMm = 300f, diaMm = 150f)
            idle()
        }

    @Test
    fun `a switch writes the reference and never the start`() {
        val vm = vm()
        val before = vm.spec.value.bodies.single()

        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD)
        idle()

        val after = vm.spec.value.bodies.single()
        assertEquals(LinerAuthoredReference.FWD, after.authoredReference)
        assertEquals(before.copy(authoredReference = LinerAuthoredReference.FWD), after)
        assertEquals(700f, after.startFromAftMm, 0f)
    }

    @Test
    fun `an unchanged reference is no edit and no undo step`() {
        val vm = vm()
        vm.reseedEditHistory()
        idle()
        assertFalse(vm.canUndo.value)
        val spec = vm.spec.value

        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.AFT)
        idle()

        assertSame("the same spec instance — nothing was written", spec, vm.spec.value)
        assertFalse("no undo step for a no-op", vm.canUndo.value)

        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD)
        idle()
        assertTrue("a real switch is an undo step", vm.canUndo.value)
    }

    @Test
    fun `the FINAL target edits only the final drawing`() {
        val vm = vm()
        vm.startFinalSpec()

        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD, SpecTarget.FINAL)
        idle()

        assertEquals(LinerAuthoredReference.FWD, vm.finalSpec.value!!.bodies.single().authoredReference)
        assertEquals(LinerAuthoredReference.AFT, vm.spec.value.bodies.single().authoredReference)
    }

    @Test
    fun `addBodyAt stores the reference`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(1000f)
        vm.addBodyAt(startMm = 600f, lengthMm = 400f, diaMm = 150f, reference = LinerAuthoredReference.FWD)
        idle()

        val b = vm.spec.value.bodies.single()
        assertEquals(LinerAuthoredReference.FWD, b.authoredReference)
        assertEquals("the canonical start is stored as given", 600f, b.startFromAftMm, 0f)
    }

    @Test
    fun `an OAL commit carries a FWD body with the FWD end and names it`() {
        val vm = vm()
        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD)
        idle()

        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            vm.onSetOverallLengthMm(1100f)
            idle()

            val b = vm.spec.value.bodies.single()
            assertEquals("still flush with the FWD end", 800f, b.startFromAftMm, 1e-3f)
            assertEquals(300f, b.lengthMm, 0f)
            val snack = events.filterIsInstance<UiEvent.ShowOalMovedSnack>().single()
            assertTrue(snack.message, snack.message.contains("Body #1"))
            assertTrue(snack.message, snack.message.contains("fwd (measured from FWD)"))
        } finally {
            scope.cancel()
        }
    }
}
