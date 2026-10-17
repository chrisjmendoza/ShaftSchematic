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
 * Getting a deleted undercut back. The detail page's delete snackbar calls
 * `ShaftViewModel.restoreUndercut` with the exact cut it removed and that cut's prior record
 * index; the top-bar Undo walks the document history instead. These drive the REAL
 * [ShaftViewModel] — a mirror of the record transform would pass whatever the extension did.
 *
 * The undo case runs the ViewModel's own recorder (an async combine over the undoable flows,
 * with wall-clock coalescing in [SessionHistory]), so it idles the main looper after each write
 * and waits past the coalescing window between the add and the delete — otherwise the two would
 * fold into one step and the undo would go back past the add.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUndercutRestoreTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A ViewModel with three recorded cuts, aft → fwd, ids returned in record order. */
    private fun vmWithThreeCuts(): Pair<ShaftViewModel, List<String>> {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        val ids = listOf(
            vm.addUndercut(100f, 20f),
            vm.addUndercut(500f, 30f, UndercutReference.FWD_SET),
            vm.addUndercut(900f, 40f),
        )
        vm.updateUndercut(ids[1], startFromAftMm = 500f, lengthMm = 30f, diaMm = 151.13f, note = "pitted")
        return vm to ids
    }

    @Test
    fun `restoreUndercut puts the same cut back at its original index`() {
        val (vm, ids) = vmWithThreeCuts()
        val before = vm.undercutRecord.value.undercuts
        val removed = before[1]

        vm.removeUndercut(removed.id)
        vm.restoreUndercut(removed, index = 1)

        val after = vm.undercutRecord.value.undercuts
        assertEquals("the record is exactly what it was before the delete", before, after)
        assertSame("the very object is reinstated — id and every typed value", removed, after[1])
        assertEquals(ids, after.map { it.id })
    }

    @Test
    fun `restoreUndercut appends when the captured index is out of range`() {
        val (vm, ids) = vmWithThreeCuts()
        val removed = vm.undercutRecord.value.undercuts[2]
        vm.removeUndercut(removed.id)
        vm.removeUndercut(ids[1])

        vm.restoreUndercut(removed, index = 2)

        assertEquals(listOf(ids[0], ids[2]), vm.undercutRecord.value.undercuts.map { it.id })
        assertSame(removed, vm.undercutRecord.value.undercuts.last())
    }

    @Test
    fun `restoreUndercut of an id already present is a no-op`() {
        val (vm, _) = vmWithThreeCuts()
        val before = vm.undercutRecord.value
        val present = before.undercuts[0]

        // A snackbar Undo landing after a history Undo already brought the cut back.
        vm.restoreUndercut(present.copy(lengthMm = 99f), index = 2)

        assertSame("nothing duplicated, nothing overwritten", before, vm.undercutRecord.value)
    }

    @Test
    fun `undoEdit after removeUndercut brings the cut back`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        val id = vm.addUndercut(200f, 25.4f)
        vm.updateUndercut(id, startFromAftMm = 200f, lengthMm = 25.4f, diaMm = 140.2f, note = "")
        idle()
        val withCut = vm.undercutRecord.value
        // Past the coalescing window, so the delete is its own history step.
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        vm.removeUndercut(id)
        idle()
        assertTrue("the delete is undoable", vm.canUndo.value)

        vm.undoEdit()
        idle()

        assertEquals("the undo restores the record as it stood before the delete", withCut, vm.undercutRecord.value)
        assertTrue("and the delete can be redone", vm.canRedo.value)
    }
}
