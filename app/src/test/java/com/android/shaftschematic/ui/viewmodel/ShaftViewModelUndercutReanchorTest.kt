package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An undercut always keeps its reference position (on-device ruling), driven through the REAL
 * [ShaftViewModel]: an ORIGINAL edit that moves a cut's datum carries the cut with it in the same
 * undo step, and nothing else moves it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUndercutReanchorTest {

    private val eps = 1e-3f

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A 1000 mm shaft (no tapers, so the S.E.T.s are the ends) with a liner at 200–400. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(1000f)
            it.addLinerAt(200f, 200f, 120f)
        }

    private fun ShaftViewModel.cut(id: String) = undercutRecord.value.undercuts.single { it.id == id }

    @Test
    fun `a FWD S E T cut keeps its distance from the FWD S E T through an OAL change`() {
        val vm = vm()
        val id = vm.addUndercut(800f, 50f, UndercutReference.FWD_SET)  // Distance 150

        vm.onSetOverallLengthMm(1200f)

        assertEquals(1000f, vm.cut(id).startFromAftMm, eps)
        assertEquals(50f, vm.cut(id).lengthMm)
    }

    @Test
    fun `an AFT S E T cut does not move on an OAL change`() {
        val vm = vm()
        val id = vm.addUndercut(100f, 50f, UndercutReference.AFT_SET)
        val before = vm.cut(id)

        vm.onSetOverallLengthMm(1200f)

        assertSame(before, vm.cut(id))
    }

    @Test
    fun `a liner AFT cut follows its liner when the liner moves`() {
        val vm = vm()
        val linerId = vm.spec.value.liners.single().id
        val id = vm.addUndercut(250f, 20f, UndercutReference.LINER_AFT, referenceLinerId = linerId)

        vm.updateLiner(0, startMm = 300f, lengthMm = 200f, odMm = 120f)

        assertEquals("still 50 from the liner's AFT edge", 350f, vm.cut(id).startFromAftMm, eps)
    }

    @Test
    fun `an edit that moves no datum leaves the record instance alone`() {
        val vm = vm()
        vm.addUndercut(100f, 50f, UndercutReference.AFT_SET)
        val record = vm.undercutRecord.value

        vm.updateLiner(0, startMm = 200f, lengthMm = 200f, odMm = 130f)  // OD only

        assertSame(record, vm.undercutRecord.value)
    }

    @Test
    fun `the FINAL drawing's edits never move a cut`() {
        val vm = vm()
        val id = vm.addUndercut(800f, 50f, UndercutReference.FWD_SET)
        vm.startFinalSpec()
        val before = vm.cut(id)

        vm.onSetOverallLengthMm(1200f, SpecTarget.FINAL)

        assertSame(before, vm.cut(id))
    }

    @Test
    fun `one undo restores both the OAL and the cut`() {
        val vm = vm()
        idle()
        val id = vm.addUndercut(800f, 50f, UndercutReference.FWD_SET)
        idle()
        val specBefore = vm.spec.value
        val cutBefore = vm.cut(id)
        // Past the coalescing window, so the OAL change is its own history step.
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        vm.onSetOverallLengthMm(1200f)
        idle()
        assertEquals(1000f, vm.cut(id).startFromAftMm, eps)
        assertTrue(vm.canUndo.value)

        vm.undoEdit()
        idle()

        assertEquals(specBefore, vm.spec.value)
        assertEquals(cutBefore, vm.cut(id))
    }
}
