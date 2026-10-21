package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.UndercutPageOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `setUndercutPageOrientation` on the REAL [ShaftViewModel]: it writes the record, marks the
 * document dirty, undoes like every other record edit, and is a no-op when unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUndercutPageOrientationTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `the setter updates the record and marks the document dirty`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        vm.addUndercut(200f, 25.4f)
        idle()
        vm.markDocumentSaved()
        idle()
        assertEquals(false, vm.hasUnsavedChanges.value)

        vm.setUndercutPageOrientation(UndercutPageOrientation.PORTRAIT)
        idle()

        assertEquals(UndercutPageOrientation.PORTRAIT, vm.undercutRecord.value.pageOrientation)
        assertTrue("a page change is unsaved work", vm.hasUnsavedChanges.value)
    }

    @Test
    fun `the setter is a no-op when unchanged`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        val before = vm.undercutRecord.value
        vm.setUndercutPageOrientation(UndercutPageOrientation.AUTO)
        assertSame(before, vm.undercutRecord.value)
    }

    @Test
    fun `undo restores the previous page choice`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.addUndercut(200f, 25.4f)
        idle()
        val before = vm.undercutRecord.value
        // Past the coalescing window, so the page change is its own history step.
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        vm.setUndercutPageOrientation(UndercutPageOrientation.LANDSCAPE)
        idle()
        assertTrue(vm.canUndo.value)

        vm.undoEdit()
        idle()

        assertEquals(before, vm.undercutRecord.value)
        assertEquals(UndercutPageOrientation.AUTO, vm.undercutRecord.value.pageOrientation)
    }
}
