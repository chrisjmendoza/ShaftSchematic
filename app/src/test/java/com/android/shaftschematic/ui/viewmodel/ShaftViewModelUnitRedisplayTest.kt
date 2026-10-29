package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings → Units re-displays the open document in the chosen unit WITHOUT making it unsaved
 * work (on-device ruling: a user may switch to millimeters just to read the values). The switch
 * is not dirty, is not an undo step, and writes no draft; the first value edit after it dirties
 * the document as usual, and the save after that stores the new unit. Drives the REAL
 * [ShaftViewModel], idling the main looper after each write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUnitRedisplayTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private var vmUnderTest: ShaftViewModel? = null

    /**
     * The chips persist the Settings default, and the settings DataStore is a process-wide
     * singleton: leave it at the store's own fallback so no other test inherits this one's unit.
     *
     * The restore is QUEUED through the ViewModel's own write path, never awaited: the store's
     * writes land in order, so the fallback is the last value, and nothing here blocks the main
     * thread on the store — a `runBlocking` teardown behind the ViewModel's own queued write is a
     * deadlock that would hang the whole suite without a timeout.
     */
    @After
    fun restoreDefaultUnit() {
        vmUnderTest?.setUnit(UnitSystem.MILLIMETERS)
        idle()
    }

    /** An inch job, opened from its file: clean, nothing to undo. */
    private fun openedInchJob(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also { vmUnderTest = it }
        vm._defaultUnit.value = UnitSystem.INCHES
        vm.newDocument()
        vm.onSetOverallLengthMm(3000f)
        idle()
        val raw = vm.exportJson()
        vm.importJson(raw)
        idle()
        assertEquals(UnitSystem.INCHES, vm.unit.value)
        assertFalse("an opened document is clean", vm.hasUnsavedChanges.value)
        assertFalse(vm.canUndo.value)
        return vm
    }

    @Test
    fun `the Settings unit switch re-displays the document without dirtying it`() {
        val vm = openedInchJob()

        vm.setUnit(UnitSystem.MILLIMETERS) // the Settings → Units chip
        idle()

        assertEquals("the open document re-displays in the new unit", UnitSystem.MILLIMETERS, vm.unit.value)
        assertFalse("the switch alone is not unsaved work", vm.hasUnsavedChanges.value)
        assertFalse("…by the autosave gate's comparison too (no draft)", vm.hasUnsavedWork())
        assertFalse("the switch is not an undo step", vm.canUndo.value)
    }

    @Test
    fun `the first value edit after the switch dirties the document and the save stores the new unit`() {
        val vm = openedInchJob()
        vm.setUnit(UnitSystem.MILLIMETERS)
        idle()

        vm.onSetOverallLengthMm(2000f)
        idle()

        assertTrue(vm.hasUnsavedChanges.value)
        assertTrue(vm.canUndo.value)
        assertEquals(UnitSystem.MILLIMETERS, ShaftDocCodec.decode(vm.exportJson()).preferredUnit)
        vm.markDocumentSaved()
        idle()
        assertFalse(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `a document with edits stays dirty across the switch`() {
        val vm = openedInchJob()
        vm.onSetOverallLengthMm(2500f)
        idle()
        assertTrue(vm.hasUnsavedChanges.value)

        vm.setUnit(UnitSystem.MILLIMETERS)
        idle()

        assertTrue("the switch never hides an edit", vm.hasUnsavedChanges.value)
        assertTrue(vm.hasUnsavedWork())
    }

    @Test
    fun `switching back and forth leaves a clean document clean`() {
        val vm = openedInchJob()
        vm.setUnit(UnitSystem.MILLIMETERS)
        idle()
        vm.setUnit(UnitSystem.INCHES)
        idle()
        assertEquals(UnitSystem.INCHES, vm.unit.value)
        assertFalse(vm.hasUnsavedChanges.value)
        assertFalse(vm.canUndo.value)
    }
}
