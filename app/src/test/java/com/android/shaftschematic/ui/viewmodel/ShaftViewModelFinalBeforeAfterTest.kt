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
 * "Original + Final" on the FINAL drawing's schematic sheet.
 *
 * The default is the point: the before/after comparison is the Final tab's primary output, so a
 * sheet asked for without touching anything must come out with both drawings on it. Like the
 * runout-bubbles election it is a session flag — never persisted, and not reset at a document
 * boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelFinalBeforeAfterTest {

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `the before-after sheet is on until turned off`() {
        assertTrue(
            "the comparison is the default final sheet",
            vm().finalBeforeAfter.value,
        )
    }

    @Test
    fun `the setter elects it off and back on`() {
        val vm = vm()

        vm.setFinalBeforeAfter(false)
        assertFalse(vm.finalBeforeAfter.value)

        vm.setFinalBeforeAfter(true)
        assertTrue(vm.finalBeforeAfter.value)
    }

    @Test
    fun `the election survives a document boundary`() {
        val vm = vm()
        vm.setFinalBeforeAfter(false)
        vm.setFinalRunoutBubbles(true)

        vm.newDocument()

        // Session flags, not document state: a document boundary resets neither. Asserted as
        // concrete values — OFF here is the non-default, so a reset to default would show.
        assertFalse("the before/after election survives newDocument()", vm.finalBeforeAfter.value)
        assertTrue("the bubble election survives newDocument()", vm.finalRunoutBubbles.value)
    }
}
