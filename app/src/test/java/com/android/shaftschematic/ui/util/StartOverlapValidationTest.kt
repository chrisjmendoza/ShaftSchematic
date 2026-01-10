package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.order.ComponentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartOverlapValidationTest {

    @Test
    fun `thread start at 0 is allowed even if body spans from 0`() {
        val sixteenInchesMm = 16f * 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = sixteenInchesMm, diaMm = 80f)),
            threads = listOf(Threads(id = "th1", startFromAftMm = 100f, lengthMm = 50f, majorDiaMm = 60f, pitchMm = 2f)),
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "thNew",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 50f,
            startMm = 0f,
        )

        assertNull(err)
    }

    @Test
    fun `thread start at 0 is allowed even if taper spans from 0`() {
        val sixteenInchesMm = 16f * 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(id = "t1", startFromAftMm = 0f, lengthMm = sixteenInchesMm, startDiaMm = 80f, endDiaMm = 60f)),
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "thNew",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 50f,
            startMm = 0f,
        )

        assertNull(err)
    }

    @Test
    fun `thread overlapping another thread is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(id = "a", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
                Threads(id = "b", startFromAftMm = 50f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
            )
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "b",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 100f,
            startMm = 50f,
        )

        assertEquals("Overlaps another component", err)
    }
}
