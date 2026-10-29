package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
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
 * The deletion snackbar names the deleted component, and its Undo selects the restored component
 * so the carousel returns to that card (on-device ruling: after "Thread deleted — Undo" the
 * carousel stayed on the neighbouring page). A stale Undo selects nothing. Drives the REAL
 * [ShaftViewModel] — the history recorder and the event flow live there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelDeleteUndoSelectTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets the history close the current step, so the next edit is a step of its own. */
    private fun settle() {
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
    }

    private fun shaft(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        vm.addThreadAt(startMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 2f)
        vm.addLinerAt(startMm = 500f, lengthMm = 300f, odMm = 120f)
        vm.addBodyAt(startMm = 1000f, lengthMm = 400f, diaMm = 100f)
        settle()
        return vm
    }

    private fun <T> withEvents(vm: ShaftViewModel, block: (MutableList<UiEvent>) -> T): T {
        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            return block(events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Undo on a thread delete restores the thread and selects it`() {
        val vm = shaft()
        val thread = vm.spec.value.threads.single()
        val liner = vm.spec.value.liners.single()
        withEvents(vm) { events ->
            vm.removeThread(thread.id)
            idle()
            val snack = events.filterIsInstance<UiEvent.ShowDeletedSnack>().single()
            assertEquals(ComponentKind.THREAD, snack.kind)
            assertEquals("the snackbar names the card it deleted", thread.id, snack.componentId)

            // The carousel slid to the neighbour when the card went away.
            vm.selectComponentById(liner.id)

            assertTrue(vm.undoDeletion(snack))
            idle()
            assertEquals(listOf(thread), vm.spec.value.threads)
            assertEquals("the carousel returns to the restored thread", thread.id, vm.selectedComponentId.value)
        }
    }

    @Test
    fun `Undo on a body demotion selects the restored explicit body by its card id`() {
        val vm = shaft()
        val body = vm.spec.value.bodies.single()
        withEvents(vm) { events ->
            // Unticking "Explicit body" demotes through the same remove pipeline.
            vm.removeBody(body.id)
            idle()
            val snack = events.filterIsInstance<UiEvent.ShowDeletedSnack>().single()
            assertEquals(ComponentKind.BODY, snack.kind)
            assertEquals(body.id, snack.componentId)
            vm.selectComponentById(null)

            assertTrue(vm.undoDeletion(snack))
            idle()
            assertEquals(listOf(body), vm.spec.value.bodies)
            val selected = vm.selectedComponentId.value
            assertEquals(body.id, selected)
            assertTrue(
                "the selected id is a live card",
                vm.resolvedComponents.value.any { resolvedBodyBaseId(it.id) == selected },
            )
        }
    }

    @Test
    fun `a stale Undo restores nothing and selects nothing`() {
        val vm = shaft()
        val thread = vm.spec.value.threads.single()
        val liner = vm.spec.value.liners.single()
        withEvents(vm) { events ->
            vm.removeThread(thread.id)
            settle()
            val snack = events.filterIsInstance<UiEvent.ShowDeletedSnack>().single()

            // An edit made while the snackbar was up — the Undo no longer names this drawing.
            vm.onSetOverallLengthMm(2900f)
            idle()
            vm.selectComponentById(liner.id)

            assertFalse(vm.undoDeletion(snack))
            idle()
            assertTrue(vm.spec.value.threads.isEmpty())
            assertEquals(liner.id, vm.selectedComponentId.value)
        }
    }
}
