package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The first edit after a session boundary is an undo step. Resuming a draft applies the draft's
 * state and THEN drops the history; when the recorder has already seen that state, a history
 * cleared to an empty head took the user's first edit as its seed, and that edit could not be
 * undone (emulator QA). Drives the REAL [ShaftViewModel] recorder, idling the main looper after
 * each write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelHistoryBoundaryTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `an edit right after a reseeded boundary can be undone`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        idle()
        // The boundary's state has been recorded; now the boundary drops the history.
        vm.reseedEditHistory()
        assertFalse("nothing to undo at the boundary itself", vm.canUndo.value)

        vm.onSetOverallLengthMm(2000f)
        idle()

        assertTrue("the first edit after the boundary is an undo step", vm.canUndo.value)
        vm.undoEdit()
        idle()
        assertEquals(3000f, vm.spec.value.overallLengthMm, 0.001f)
        assertFalse("undo never crosses back over the boundary", vm.canUndo.value)
    }

    @Test
    fun `opening a document starts a history whose first edit undoes back to the opened state`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(1234f)
        idle()
        val doc = vm.exportJson()
        vm.onSetOverallLengthMm(999f)
        idle()

        vm.importJson(doc)
        idle()
        assertFalse(vm.canUndo.value)

        vm.onSetOverallLengthMm(500f)
        idle()
        assertTrue(vm.canUndo.value)
        vm.undoEdit()
        idle()
        assertEquals(1234f, vm.spec.value.overallLengthMm, 0.001f)
        assertFalse(vm.canUndo.value)
    }

    /** Two documents: one carrying an undercut, one plain. */
    private fun docs(vm: ShaftViewModel): Pair<String, String> {
        vm.onSetOverallLengthMm(3000f)
        vm.addUndercut(100f, 20f)
        idle()
        val withCut = vm.exportJson()
        vm.newDocument()
        idle()
        vm.onSetOverallLengthMm(2000f)
        idle()
        return withCut to vm.exportJson()
    }

    @Test
    fun `opening a job records no step that carries the previous job's measurements`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        val (withCut, plain) = docs(vm)
        vm.importJson(withCut)
        idle()

        vm.importJson(plain)
        idle()

        assertFalse("a freshly opened job has nothing to undo", vm.canUndo.value)
        assertTrue(vm.undercutRecord.value.undercuts.isEmpty())
        assertEquals(2000f, vm.spec.value.overallLengthMm, 0.001f)
    }

    @Test
    fun `starting from a template records no step that carries the previous job's measurements`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        val (withCut, plain) = docs(vm)
        vm.importJson(withCut)
        idle()

        vm.applyTemplate(plain)
        idle()

        assertFalse(vm.canUndo.value)
        assertTrue(vm.undercutRecord.value.undercuts.isEmpty())
    }
}
