package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.autoBlendFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Angle-mode blend entry on the REAL [ShaftViewModel]: the angle and corner radius store verbatim,
 * a switch between Length and Angle never touches the typed blend length (so switching back
 * restores it), and every other blend field rides through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelBlendAngleTest {

    private fun vm() =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
            .also { it.onSetOverallLengthMm(1000f) }

    @Test
    fun `an angle and radius store verbatim and leave the typed length alone`() {
        val vm = vm().also { it.addBodyAt(startMm = 0f, lengthMm = 800f, diaMm = 150f) }
        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 0f, profile = BlendProfile.FILLET,
            sealAft = true, sealAftLenMm = 60f, toLinerOdAft = true,
        )
        val before = vm.spec.value.bodies.single()

        vm.updateBodyBlendAngle(0, LinerAuthoredReference.AFT, angleDeg = 37.125f, radiusMm = 9.525f)
        val angled = vm.spec.value.bodies.single()
        assertEquals(37.125f, angled.blendAftAngleDeg!!, 0f)
        assertEquals(9.525f, angled.blendAftRadiusMm, 0f)
        assertEquals(before.copy(blendAftAngleDeg = 37.125f, blendAftRadiusMm = 9.525f), angled)

        // Back to Length: the angle clears, the typed length is exactly what it was.
        vm.updateBodyBlendAngle(0, LinerAuthoredReference.AFT, angleDeg = null, radiusMm = 9.525f)
        val length = vm.spec.value.bodies.single()
        assertNull(length.blendAftAngleDeg)
        assertEquals(25.4f, length.blendAftMm, 0f)
        assertEquals(9.525f, length.blendAftRadiusMm, 0f)
    }

    @Test
    fun `a repeat of the stored entry is a no-op`() {
        val vm = vm().also { it.addBodyAt(startMm = 0f, lengthMm = 800f, diaMm = 150f) }
        vm.updateBodyBlendAngle(0, LinerAuthoredReference.FWD, 45f, 0f)
        val spec = vm.spec.value
        vm.updateBodyBlendAngle(0, LinerAuthoredReference.FWD, 45f, 0f)
        assertSame(spec, vm.spec.value)
    }

    @Test
    fun `addBodyAt carries the angle draft`() {
        val vm = vm()
        vm.addBodyAt(
            startMm = 0f, lengthMm = 800f, diaMm = 150f, blendAftMm = 50.8f,
            blendAftAngleDeg = 45f, blendAftRadiusMm = 9.525f, blendFwdMm = 25.4f,
        )
        val b = vm.spec.value.bodies.single()
        assertEquals(45f, b.blendAftAngleDeg!!, 0f)
        assertEquals(9.525f, b.blendAftRadiusMm, 0f)
        assertEquals(50.8f, b.blendAftMm, 0f)
        assertNull(b.blendFwdAngleDeg)
    }

    @Test
    fun `an auto span's entry rides its anchor through a length edit`() {
        val vm = vm()
        vm.setAutoBlend(0f, 1000f, LinerAuthoredReference.AFT, 25.4f, BlendProfile.OGEE)
        vm.setAutoBlendAngle(0f, 1000f, LinerAuthoredReference.AFT, 30f, 6.35f)
        var a = vm.spec.value.autoBlends.autoBlendFor(0f, 1000f, LinerAuthoredReference.AFT)!!
        assertEquals(30f, a.angleDeg!!, 0f)
        assertEquals(6.35f, a.radiusMm, 0f)
        assertEquals(25.4f, a.lengthMm, 0f)

        // A length, seal or liner-target commit replaces the anchor and keeps the entry.
        vm.setAutoBlend(0f, 1000f, LinerAuthoredReference.AFT, 40f, BlendProfile.OGEE, seal = true, toLinerOd = true)
        a = vm.spec.value.autoBlends.autoBlendFor(0f, 1000f, LinerAuthoredReference.AFT)!!
        assertEquals(30f, a.angleDeg!!, 0f)
        assertEquals(6.35f, a.radiusMm, 0f)
        assertEquals(40f, a.lengthMm, 0f)

        // Back to Length keeps the length.
        vm.setAutoBlendAngle(0f, 1000f, LinerAuthoredReference.AFT, null, 6.35f)
        a = vm.spec.value.autoBlends.autoBlendFor(0f, 1000f, LinerAuthoredReference.AFT)!!
        assertNull(a.angleDeg)
        assertEquals(40f, a.lengthMm, 0f)
    }

    @Test
    fun `an auto entry with no live anchor sets nothing`() {
        val vm = vm()
        val spec = vm.spec.value
        vm.setAutoBlendAngle(0f, 1000f, LinerAuthoredReference.AFT, 45f, 0f)
        assertSame(spec, vm.spec.value)
    }
}
