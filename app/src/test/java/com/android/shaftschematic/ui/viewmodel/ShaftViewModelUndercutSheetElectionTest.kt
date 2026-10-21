package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.model.UndercutSetDatum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `setUndercutSetDatum` and `setUndercutSectionPrinted` on the REAL [ShaftViewModel]: each writes
 * the record, marks the document dirty, undoes like every other record edit, and is a no-op when
 * unchanged. A bare-shaft section is keyed by its member cuts — leaving it off lists them all,
 * printing it again unlists them all — and an entry the strip does not name stays put.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUndercutSheetElectionTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun linerStrip(id: String) = UndercutStrip.LinerStrip(
        linerId = id, linerStartMm = 100f, linerEndMm = 400f,
        drawStartMm = 80f, drawEndMm = 420f, chainStartMm = 100f, chainEndMm = 400f,
        undercutIds = listOf("c1"),
    )

    private fun freeStrip(vararg ids: String) =
        UndercutStrip.FreeStrip(UndercutWindow(500f, 600f, ids.toList()))

    private fun savedVm(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        vm.addUndercut(200f, 25.4f)
        idle()
        vm.markDocumentSaved()
        idle()
        assertEquals(false, vm.hasUnsavedChanges.value)
        return vm
    }

    @Test
    fun `the datum setter updates the record and marks the document dirty`() {
        val vm = savedVm()
        vm.setUndercutSetDatum(UndercutSetDatum.AFT)
        idle()
        assertEquals(UndercutSetDatum.AFT, vm.undercutRecord.value.setDatum)
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `the datum setter is a no-op when unchanged`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        val before = vm.undercutRecord.value
        vm.setUndercutSetDatum(UndercutSetDatum.NEAREST)
        assertSame(before, vm.undercutRecord.value)
    }

    @Test
    fun `a liner section toggles its liner id and marks the document dirty`() {
        val vm = savedVm()
        vm.setUndercutSectionPrinted(linerStrip("L1"), printed = false)
        idle()
        assertEquals(listOf("L1"), vm.undercutRecord.value.excludedLinerIds)
        assertTrue(vm.hasUnsavedChanges.value)

        val excluded = vm.undercutRecord.value
        vm.setUndercutSectionPrinted(linerStrip("L1"), printed = false)
        assertSame("already off: no-op", excluded, vm.undercutRecord.value)

        vm.setUndercutSectionPrinted(linerStrip("L1"), printed = true)
        assertEquals(emptyList<String>(), vm.undercutRecord.value.excludedLinerIds)
        val printed = vm.undercutRecord.value
        vm.setUndercutSectionPrinted(linerStrip("L1"), printed = true)
        assertSame("already on: no-op", printed, vm.undercutRecord.value)
    }

    @Test
    fun `a bare-shaft section lists and unlists its member cuts and leaves other entries`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setUndercutSectionPrinted(freeStrip("other"), printed = false)
        vm.setUndercutSectionPrinted(freeStrip("a", "b"), printed = false)
        assertEquals(listOf("other", "a", "b"), vm.undercutRecord.value.excludedFreeCutIds)

        vm.setUndercutSectionPrinted(freeStrip("a", "b", "c"), printed = false)
        assertEquals("only the missing member is added", listOf("other", "a", "b", "c"),
            vm.undercutRecord.value.excludedFreeCutIds)

        vm.setUndercutSectionPrinted(freeStrip("a", "b", "c"), printed = true)
        assertEquals(listOf("other"), vm.undercutRecord.value.excludedFreeCutIds)
    }

    @Test
    fun `undo restores the previous datum and section election`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.addUndercut(200f, 25.4f)
        idle()
        val before = vm.undercutRecord.value
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        vm.setUndercutSetDatum(UndercutSetDatum.FWD)
        idle()
        assertTrue(vm.canUndo.value)
        vm.undoEdit()
        idle()
        assertEquals(before, vm.undercutRecord.value)

        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        vm.setUndercutSectionPrinted(linerStrip("L1"), printed = false)
        idle()
        vm.undoEdit()
        idle()
        assertEquals(before, vm.undercutRecord.value)
    }
}
