package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import com.android.shaftschematic.model.LinerAuthoredReference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DraftCommitViewModelTest {

    @Test
    fun liner_first_add_preserves_explicit_start() {
        val app = RuntimeEnvironment.getApplication() as Application
        val vm = ShaftViewModel(app)

        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 50f)
        vm.beginDraftLiner(startMm = 100f, lengthMm = 10f, odMm = 20f)
        vm.updateDraftLiner(
            startInputMm = 24f,
            lengthMm = 10f,
            odMm = 20f,
            measureFrom = LinerAuthoredReference.AFT
        )
        vm.commitDraftComponent()

        val liner = vm.spec.value.liners.first()
        assertEquals(24f, liner.startFromAftMm, 1e-4f)
    }
}
