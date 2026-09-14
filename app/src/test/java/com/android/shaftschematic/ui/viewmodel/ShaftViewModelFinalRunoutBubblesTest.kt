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
 * "Runout bubbles" on the FINAL drawing's schematic sheet.
 *
 * The default is what these are mostly about: the final drawing is first of all the welding
 * and machining copy the shop marks liner placements up on, so a sheet asked for without
 * touching anything must come out without stations. The flag also carries the blank-draft
 * posture — never persisted, and moving in lockstep with it at every document boundary, so a
 * future reset that catches one catches the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelFinalRunoutBubblesTest {

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `bubbles are off until asked for`() {
        assertFalse(
            "the machining copy is the default sheet",
            vm().finalRunoutBubbles.value,
        )
    }

    @Test
    fun `the setter elects them on and back off`() {
        val vm = vm()

        vm.setFinalRunoutBubbles(true)
        assertTrue(vm.finalRunoutBubbles.value)

        vm.setFinalRunoutBubbles(false)
        assertFalse(vm.finalRunoutBubbles.value)
    }

    @Test
    fun `it keeps the blank-draft posture across a document boundary`() {
        val vm = vm()
        vm.setPdfBlankDraft(true)
        vm.setFinalRunoutBubbles(true)

        vm.newDocument()

        // Both are session flags, not document state: a document boundary resets neither.
        // Asserted as concrete values, not as equality between the two — `false == false`
        // would pass a change that wrongly reset both.
        assertTrue("blank draft survives newDocument()", vm.pdfBlankDraft.value)
        assertTrue("the bubble election survives newDocument()", vm.finalRunoutBubbles.value)
    }
}
