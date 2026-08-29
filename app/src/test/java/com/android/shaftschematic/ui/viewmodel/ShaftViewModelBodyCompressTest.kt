package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The authoring default: a body created through [addBodyAt] — the ONE add path, serving both
 * `AddBodyDialog` and the auto-body card's "Explicit body" promotion — is created with
 * compression OFF, so a named section draws at true proportion (on-device report: a freshly
 * added 12" section printed with an S-break). The card's "Compress on drawing" checkbox
 * ([updateBodyCompressOnDrawing]) is the escape hatch, and it is the opposite of the
 * SERIALIZATION default, which keeps every already-saved document compressing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelBodyCompressTest {

    private fun vm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `a newly authored body is created uncompressed`() {
        val vm = vm()
        vm.addBodyAt(startMm = 0f, lengthMm = 304.8f, diaMm = 127f)

        assertFalse(vm.spec.value.bodies.single().compressOnDrawing)
    }

    @Test
    fun `the card toggle turns compression back on and off`() {
        val vm = vm()
        vm.addBodyAt(startMm = 0f, lengthMm = 304.8f, diaMm = 127f)

        vm.updateBodyCompressOnDrawing(0, true)
        assertTrue(vm.spec.value.bodies.single().compressOnDrawing)

        vm.updateBodyCompressOnDrawing(0, false)
        assertFalse(vm.spec.value.bodies.single().compressOnDrawing)
    }

    @Test
    fun `re-setting the same value is a no-op`() {
        // The identity guard in `withItemField` — a recomposition may never mark the
        // document dirty by re-asserting a flag that already matches.
        val vm = vm()
        vm.addBodyAt(startMm = 0f, lengthMm = 304.8f, diaMm = 127f)

        val before = vm.spec.value
        vm.updateBodyCompressOnDrawing(0, false)
        assertTrue("the same value must not produce a new spec", before === vm.spec.value)
    }
}
