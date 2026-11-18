package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.autoSectionFiberglass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Explicit body" on an auto card carries EVERY face choice of the section onto the new body —
 * blends, Angle entries, liner targets, seal areas and their lengths (on-device ruling: "all
 * checkboxes must carry over") — and unticking it hands the body's faces back to the section.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelPromotionFacesTest {

    private val aft = LinerAuthoredReference.AFT
    private val fwd = LinerAuthoredReference.FWD

    /** Liners at 0–200 and 600–800 on a 1000 mm shaft: bare shaft 200–600 and 800–1000. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(1000f)
            it.addLinerAt(0f, 200f, 120f)
            it.addLinerAt(600f, 200f, 120f)
        }

    @Test
    fun `promotion carries seal areas, blends, liner targets, angles and the wrap`() {
        val vm = vm()
        vm.setAutoBlend(200f, 600f, aft, 0f, BlendProfile.OGEE, true, 76.2f, false)
        vm.setAutoBlend(200f, 600f, fwd, 25.4f, BlendProfile.OGEE, true, 50.8f, true)
        vm.setAutoBlendAngle(200f, 600f, fwd, 45f, 9.525f)
        vm.setAutoSectionFiberglass(200f, 600f, true, 76.2f, 50.8f)

        vm.addBodyAt(200f, 400f, 100f, carryAutoSectionFaces = true)

        val b = vm.spec.value.bodies.single()
        assertTrue(b.blendAftSeal)
        assertEquals(76.2f, b.blendAftSealLenMm)
        assertEquals("a square AFT face stays square", 0f, b.blendAftMm)
        assertTrue(b.blendFwdSeal)
        assertEquals(50.8f, b.blendFwdSealLenMm)
        assertEquals(25.4f, b.blendFwdMm)
        assertTrue(b.blendFwdToLinerOd)
        assertEquals(45f, b.blendFwdAngleDeg)
        assertEquals(9.525f, b.blendFwdRadiusMm)
        assertTrue(b.fiberglass)
    }

    @Test
    fun `the Add Body dialog's own faces stand on a span an auto run fills`() {
        val vm = vm()
        vm.setAutoBlend(200f, 600f, aft, 0f, BlendProfile.OGEE, true, 76.2f, false)

        vm.addBodyAt(200f, 400f, 100f)  // the dialog path: no carry

        assertFalse(vm.spec.value.bodies.single().blendAftSeal)
    }

    @Test
    fun `demotion hands the body's faces back to the section, not the stale anchors`() {
        val vm = vm()
        vm.setAutoBlend(200f, 600f, aft, 0f, BlendProfile.OGEE, true, 76.2f, false)
        vm.addBodyAt(200f, 400f, 100f, carryAutoSectionFaces = true)
        // Edited on the explicit card: the AFT seal off, a FWD seal on.
        vm.updateBodyBlend(0, 0f, 0f, BlendProfile.OGEE, false, true, 76.2f, 63.5f, false, false)

        vm.removeBody(vm.spec.value.bodies.single().id)

        val s = vm.spec.value
        assertNull("the AFT seal the body dropped stays dropped", s.autoBlends.autoBlendFor(200f, 600f, aft))
        val f = s.autoBlends.autoBlendFor(200f, 600f, fwd)!!
        assertTrue(f.seal)
        assertEquals(63.5f, f.sealLenMm)
        assertFalse(s.autoSectionFiberglass(200f, 600f))
    }
}
