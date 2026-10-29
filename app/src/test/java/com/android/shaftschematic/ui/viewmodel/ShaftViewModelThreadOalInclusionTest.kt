package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
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
 * The thread card's OAL inclusion prompt through the REAL [ShaftViewModel]
 * ([setThreadExcludeFromOal] with `adjustOal`): a Shorten that re-anchors a FWD-measured part
 * says so with the OAL field's own snackbar, one Undo takes the flag, the OAL and the moved part
 * back together, and the edit lands only on its [SpecTarget].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelThreadOalInclusionTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val fwdThread = Threads(
        id = "th", startFromAftMm = 1000f, lengthMm = 100f, majorDiaMm = 100f, pitchMm = 6f,
        isAftEnd = false,
    )
    private val fwdTaper = Taper(
        id = "tp", startFromAftMm = 700f, lengthMm = 300f, startDiaMm = 150f, endDiaMm = 120f,
        authoredReference = LinerAuthoredReference.FWD,
    )

    /** A VM holding [spec] as its opened state, history reseeded so the next edit is one step. */
    private fun vmWith(spec: ShaftSpec): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm._spec.value = spec
        idle()
        vm.reseedEditHistory()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        return vm
    }

    private fun collect(vm: ShaftViewModel, block: (MutableList<UiEvent>) -> Unit) {
        val events = mutableListOf<UiEvent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.uiEvents.collect { events += it } }
        try {
            block(events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `exclude and shorten announces the moved FWD part once and one Undo takes it all back`() {
        val start = ShaftSpec(overallLengthMm = 1100f, tapers = listOf(fwdTaper), threads = listOf(fwdThread))
        val vm = vmWith(start)
        collect(vm) { events ->
            vm.setThreadExcludeFromOal("th", excludeFromOAL = true, adjustOal = true)
            idle()

            val s = vm.spec.value
            assertEquals(1000f, s.overallLengthMm, 1e-3f)
            assertTrue(s.threads.single().excludeFromOAL)
            assertEquals("the thread stays where it was", 1000f, s.threads.single().startFromAftMm, 1e-3f)
            assertEquals("the FWD taper keeps its FWD distance", 600f, s.tapers.single().startFromAftMm, 1e-3f)

            val snack = events.filterIsInstance<UiEvent.ShowOalMovedSnack>().single()
            assertTrue(snack.message.startsWith("OAL "))

            vm.undoEdit()
            idle()
            assertEquals("one Undo restores flag, OAL and the moved taper", start, vm.spec.value)
        }
    }

    @Test
    fun `the snackbar's Undo names the whole step`() {
        val start = ShaftSpec(overallLengthMm = 1100f, tapers = listOf(fwdTaper), threads = listOf(fwdThread))
        val vm = vmWith(start)
        collect(vm) { events ->
            vm.setThreadExcludeFromOal("th", excludeFromOAL = true, adjustOal = true)
            idle()
            val snack = events.filterIsInstance<UiEvent.ShowOalMovedSnack>().single()
            assertTrue(vm.undoEditIfStillAt(snack.expected))
            idle()
            assertEquals(start, vm.spec.value)
        }
    }

    @Test
    fun `a shorten that moves nothing says nothing`() {
        val vm = vmWith(ShaftSpec(overallLengthMm = 1100f, threads = listOf(fwdThread)))
        collect(vm) { events ->
            vm.setThreadExcludeFromOal("th", excludeFromOAL = true, adjustOal = true)
            idle()
            assertEquals(1000f, vm.spec.value.overallLengthMm, 1e-3f)
            assertTrue(events.none { it is UiEvent.ShowOalMovedSnack })
        }
    }

    @Test
    fun `keep OAL changes only the flag and says nothing`() {
        val vm = vmWith(ShaftSpec(overallLengthMm = 1100f, tapers = listOf(fwdTaper), threads = listOf(fwdThread)))
        collect(vm) { events ->
            vm.setThreadExcludeFromOal("th", excludeFromOAL = true, adjustOal = false)
            idle()
            val s = vm.spec.value
            assertEquals(1100f, s.overallLengthMm, 0f)
            assertEquals(fwdTaper, s.tapers.single())
            assertTrue(s.threads.single().excludeFromOAL)
            assertEquals(1100f, s.threads.single().startFromAftMm, 1e-3f)
            assertTrue(events.none { it is UiEvent.ShowOalMovedSnack })
        }
    }

    @Test
    fun `a FINAL edit lands on the final drawing only`() {
        val start = ShaftSpec(overallLengthMm = 1100f, tapers = listOf(fwdTaper), threads = listOf(fwdThread))
        val vm = vmWith(start)
        vm.startFinalSpec()
        idle()

        vm.setThreadExcludeFromOal("th", excludeFromOAL = true, adjustOal = true, target = SpecTarget.FINAL)
        idle()

        assertEquals("the original is untouched", start, vm.spec.value)
        val fin = requireNotNull(vm.finalSpec.value)
        assertEquals(1000f, fin.overallLengthMm, 1e-3f)
        assertTrue(fin.threads.single().excludeFromOAL)
        assertFalse(start.threads.single().excludeFromOAL)
    }
}
