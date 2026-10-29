package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An OAL commit that re-anchors FWD-measured parts says what it moved, and the snackbar's Undo
 * takes the whole commit back in ONE step — the real [ShaftViewModel], since both promises live
 * where the history recorder and the event flow run. Drives the editor's commit exactly: the mm
 * callback, then the raw-text one (`ShaftRoute`), the second of which moves nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelOalMoveSnackTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText()

    @Test
    fun `an OAL correction announces the move once and Undo restores the length and the liner`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.importJson(fixture())
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        assertEquals(UnitSystem.INCHES, vm.unit.value)
        val before = vm.spec.value

        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            val newOalMm = 234.1875f * 25.4f
            vm.onSetOverallLengthMm(newOalMm)
            vm.setOverallLength("234 3/16")
            idle()

            val snack = events.single() as UiEvent.ShowOalMovedSnack
            assertEquals("OAL 234 3/16\": moved FWD Liner 4 13/16\" aft (measured from FWD)", snack.message)
            assertTrue(vm.spec.value.overallLengthMm < before.overallLengthMm)

            assertTrue("the snackbar's Undo acts on the drawing it announced", vm.undoEditIfStillAt(snack.expected))
            idle()

            assertEquals("one Undo takes back the OAL and the move", before, vm.spec.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the OAL snackbar's Undo does nothing once a later edit has changed the drawing`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.importJson(fixture())
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            vm.onSetOverallLengthMm(234.1875f * 25.4f)
            vm.setOverallLength("234 3/16")
            idle()
            val snack = events.filterIsInstance<UiEvent.ShowOalMovedSnack>().single()
            Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

            // A card edit while the snackbar is still up.
            val liner = vm.spec.value.liners.first()
            vm.updateLiner(0, liner.startFromAftMm, liner.lengthMm, liner.odMm + 2f)
            idle()
            val edited = vm.spec.value

            assertFalse(vm.undoEditIfStillAt(snack.expected))
            idle()
            assertEquals("nothing undone — not the card edit, not the OAL", edited, vm.spec.value)
            assertEquals(
                UiEvent.ShowSnackbarMessage(ShaftViewModel.SNACK_UNDO_STALE_MESSAGE),
                events.last(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a delete made under the OAL snackbar keeps its own Undo and the OAL's undoes nothing`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.importJson(fixture())
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            vm.onSetOverallLengthMm(234.1875f * 25.4f)
            vm.setOverallLength("234 3/16")
            idle()
            val oalSnack = events.filterIsInstance<UiEvent.ShowOalMovedSnack>().single()
            val afterOal = vm.spec.value
            Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)

            val removed = afterOal.liners.first()
            vm.removeLiner(removed.id)
            idle()
            val deleteSnack = events.filterIsInstance<UiEvent.ShowDeletedSnack>().single()
            val afterDelete = vm.spec.value

            assertFalse("the OAL's Undo would take back the delete", vm.undoEditIfStillAt(oalSnack.expected))
            assertEquals(afterDelete, vm.spec.value)

            assertTrue(vm.undoEditIfStillAt(deleteSnack.expected))
            idle()
            assertEquals("the delete's Undo restores the liner and keeps the OAL", afterOal, vm.spec.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an OAL change that moves nothing says nothing`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            vm.onSetOverallLengthMm(1000f)
            vm.addLinerAt(startMm = 100f, lengthMm = 200f, odMm = 200f)
            vm.onSetOverallLengthMm(1200f)
            idle()
            assertTrue(events.none { it is UiEvent.ShowOalMovedSnack })
        } finally {
            scope.cancel()
        }
    }
}
