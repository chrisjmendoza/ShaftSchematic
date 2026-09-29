package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.autoBlendFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Seal-area length authoring on the REAL [ShaftViewModel]: `updateBodyBlend`, `setAutoBlend`
 * and `addBodyAt` store the seal lengths verbatim (golden rule — 0.001 counts), coerce a
 * negative to 0, and treat a repeat of the stored values as a no-op that emits no new spec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelBodySealLengthTest {

    private fun vm() =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
            .also { it.onSetOverallLengthMm(1000f) }

    private fun vmWithBody() = vm().also { it.addBodyAt(startMm = 0f, lengthMm = 800f, diaMm = 150f) }

    // ── Explicit body ────────────────────────────────────────────────────────

    @Test
    fun `updateBodyBlend stores seal lengths verbatim`() {
        val vm = vmWithBody()

        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 12.7f, profile = BlendProfile.OGEE,
            sealAft = true, sealFwd = false, sealAftLenMm = 123.456f, sealFwdLenMm = 76.201f,
        )

        val b = vm.spec.value.bodies.single()
        assertEquals(123.456f, b.blendAftSealLenMm, 0f)
        assertEquals(76.201f, b.blendFwdSealLenMm, 0f)
        assertTrue(b.blendAftSeal)
        assertEquals(25.4f, b.blendAftMm, 0f)
    }

    @Test
    fun `updateBodyBlend coerces a negative seal length to zero`() {
        val vm = vmWithBody()

        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 0f, profile = BlendProfile.OGEE,
            sealAft = true, sealFwd = false, sealAftLenMm = -5f, sealFwdLenMm = -0.001f,
        )

        val b = vm.spec.value.bodies.single()
        assertEquals(0f, b.blendAftSealLenMm, 0f)
        assertEquals(0f, b.blendFwdSealLenMm, 0f)
    }

    @Test
    fun `repeating the stored blend values emits no new spec`() {
        val vm = vmWithBody()
        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 12.7f, profile = BlendProfile.FILLET,
            sealAft = true, sealFwd = true, sealAftLenMm = 123.456f, sealFwdLenMm = 50.8f,
        )
        val before = vm.spec.value

        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 12.7f, profile = BlendProfile.FILLET,
            sealAft = true, sealFwd = true, sealAftLenMm = 123.456f, sealFwdLenMm = 50.8f,
        )

        assertSame(before, vm.spec.value)
    }

    /** The guard includes the seal lengths: changing ONLY a seal length is a real edit. */
    @Test
    fun `changing only a seal length is not swallowed by the no-op guard`() {
        val vm = vmWithBody()
        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 0f, profile = BlendProfile.OGEE,
            sealAft = true, sealFwd = false, sealAftLenMm = 100f, sealFwdLenMm = 0f,
        )

        vm.updateBodyBlend(
            0, blendAftMm = 25.4f, blendFwdMm = 0f, profile = BlendProfile.OGEE,
            sealAft = true, sealFwd = false, sealAftLenMm = 100.001f, sealFwdLenMm = 0f,
        )

        assertEquals(100.001f, vm.spec.value.bodies.single().blendAftSealLenMm, 0f)
    }

    @Test
    fun `addBodyAt stores seal lengths verbatim`() {
        val vm = vm()

        vm.addBodyAt(
            startMm = 0f, lengthMm = 800f, diaMm = 150f,
            blendAftMm = 25.4f, blendAftSeal = true,
            blendAftSealLenMm = 123.456f, blendFwdSealLenMm = -1f,
        )

        val b = vm.spec.value.bodies.single()
        assertEquals(123.456f, b.blendAftSealLenMm, 0f)
        assertEquals(0f, b.blendFwdSealLenMm, 0f)
    }

    // ── Auto span ────────────────────────────────────────────────────────────

    @Test
    fun `setAutoBlend stores the seal length verbatim`() {
        val vm = vm()

        vm.setAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 25.4f, BlendProfile.OGEE,
            seal = true, sealLenMm = 123.456f)

        val blend = vm.spec.value.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!
        assertEquals(123.456f, blend.sealLenMm, 0f)
        assertTrue(blend.seal)
    }

    @Test
    fun `setAutoBlend coerces a negative seal length to zero`() {
        val vm = vm()

        vm.setAutoBlend(200f, 600f, LinerAuthoredReference.FWD, 25.4f, BlendProfile.OGEE,
            seal = true, sealLenMm = -3f)

        val blend = vm.spec.value.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.FWD)!!
        assertEquals(0f, blend.sealLenMm, 0f)
    }

    @Test
    fun `repeating the stored auto blend emits no new spec`() {
        val vm = vm()
        vm.setAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 25.4f, BlendProfile.OGEE,
            seal = true, sealLenMm = 123.456f)
        val before = vm.spec.value

        vm.setAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 25.4f, BlendProfile.OGEE,
            seal = true, sealLenMm = 123.456f)

        assertSame(before, vm.spec.value)
    }
}
