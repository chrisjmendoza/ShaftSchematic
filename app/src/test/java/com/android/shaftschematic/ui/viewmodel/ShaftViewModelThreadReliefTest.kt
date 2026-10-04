package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The thread relief through the REAL ViewModel ([addThreadAt] and [setThreadRelief]): values land
 * verbatim, the relief is part of the thread's stored span so NOTHING moves (the thread's own
 * start/length, every other component, the OAL), and an edit lands only on its [SpecTarget].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelThreadReliefTest {

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(1000f)
            it.addThreadAt(startMm = 0f, lengthMm = 150f, majorDiaMm = 114.3f, pitchMm = 6.35f)
            it.addTaperAt(startMm = 150f, lengthMm = 300f, startDiaMm = 120f, endDiaMm = 145f)
            it.addBodyAt(startMm = 450f, lengthMm = 550f, diaMm = 152.4f)
        }

    @Test
    fun `setThreadRelief stores the values verbatim and moves nothing`() {
        val vm = vm()
        val before = vm.spec.value
        val id = before.threads.single().id

        vm.setThreadRelief(id, reliefLenMm = 9.525f, reliefDiaMm = 106.045f, reliefAtAft = false)

        val s = vm.spec.value
        val th = s.threads.single()
        assertEquals(9.525f, th.reliefLenMm, 0f)
        assertEquals(106.045f, th.reliefDiaMm, 0f)
        assertFalse(th.reliefAtAft)
        assertTrue(th.hasRelief)
        assertEquals("the thread's own span never moves", 0f, th.startFromAftMm, 0f)
        assertEquals("the thread's own span never moves", 150f, th.lengthMm, 0f)
        assertEquals(before.tapers, s.tapers)
        assertEquals(before.bodies, s.bodies)
        assertEquals(1000f, s.overallLengthMm, 0f)
    }

    @Test
    fun `setThreadRelief with zeros clears the relief and keeps the end`() {
        val vm = vm()
        val id = vm.spec.value.threads.single().id
        vm.setThreadRelief(id, 9.525f, 106.045f, reliefAtAft = true)

        vm.setThreadRelief(id, 0f, 0f, reliefAtAft = true)

        val th = vm.spec.value.threads.single()
        assertFalse(th.hasRelief)
        assertEquals(0f, th.reliefLenMm, 0f)
        assertEquals(0f, th.reliefDiaMm, 0f)
    }

    @Test
    fun `an unknown id is a no-op`() {
        val vm = vm()
        val before = vm.spec.value
        vm.setThreadRelief("nope", 9f, 100f, reliefAtAft = false)
        assertEquals(before, vm.spec.value)
    }

    @Test
    fun `setThreadRelief on the final lands only on the final`() {
        val vm = vm()
        vm.startFinalSpec()
        val id = vm.spec.value.threads.single().id

        vm.setThreadRelief(id, 9.525f, 106.045f, reliefAtAft = false, target = SpecTarget.FINAL)

        assertFalse("the original is the record of what came in", vm.spec.value.threads.single().hasRelief)
        val f = requireNotNull(vm.finalSpec.value)
        assertTrue(f.threads.single().hasRelief)
    }

    @Test
    fun `addThreadAt carries the relief verbatim`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(1000f)
        vm.addThreadAt(
            startMm = 850f, lengthMm = 150f, majorDiaMm = 114.3f, pitchMm = 6.35f,
            reliefLenMm = 9.6f, reliefDiaMm = 105.9f, reliefAtAft = true,
        )
        val th = vm.spec.value.threads.single()
        assertEquals(9.6f, th.reliefLenMm, 0f)
        assertEquals(105.9f, th.reliefDiaMm, 0f)
        assertTrue(th.reliefAtAft)
        assertEquals(850f, th.startFromAftMm, 0f)
        assertEquals(150f, th.lengthMm, 0f)
    }

    @Test
    fun `updating the thread keeps its relief`() {
        val vm = vm()
        val id = vm.spec.value.threads.single().id
        vm.setThreadRelief(id, 9.525f, 106.045f, reliefAtAft = false)

        vm.updateThread(0, startMm = 0f, lengthMm = 160f, majorDiaMm = 114.3f, pitchMm = 6.35f)

        val th = vm.spec.value.threads.single()
        assertEquals(9.525f, th.reliefLenMm, 0f)
        assertEquals(106.045f, th.reliefDiaMm, 0f)
    }
}
