package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
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
        // Bodies are not obstacles for the start validator — a body runs under/against other
        // components normally, so a thread at the AFT end over a body is fine.
        val sixteenInchesMm = 16f * 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = sixteenInchesMm, diaMm = 80f)),
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
    fun `thread start at 0 is allowed even if a taper spans from 0`() {
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

    @Test
    fun `negative start is rejected for all kinds`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        listOf(ComponentKind.THREAD, ComponentKind.LINER).forEach { kind ->
            val err = startOverlapErrorMm(spec, "new", kind, 100f, -1f)
            assertEquals("Must be ≥ 0", err)
        }
    }

    @Test
    fun `thread sandwiched between bodies is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 300f, diaMm = 80f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 500f, diaMm = 80f),
            )
        )
        val err = startOverlapErrorMm(spec, "new", ComponentKind.THREAD, 200f, 300f)
        assertEquals("Thread must be at a shaft end, not between components", err)
    }

    @Test
    fun `thread at FWD end adjacent to body is allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 900f, diaMm = 80f))
        )
        // Thread starts at body end (900mm), no body ahead of it → not sandwiched
        val err = startOverlapErrorMm(spec, "new", ComponentKind.THREAD, 100f, 900f)
        assertNull(err)
    }

    @Test
    fun `liner overlapping another liner is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f))
        )
        val err = startOverlapErrorMm(spec, "new", ComponentKind.LINER, 200f, 250f)
        assertEquals("Overlaps another component", err)
    }

    @Test
    fun `adjacent liners touching at endpoint are allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f))
        )
        // New liner starts exactly where l1 ends (300mm) — touching but not overlapping
        val err = startOverlapErrorMm(spec, "new", ComponentKind.LINER, 200f, 300f)
        assertNull(err)
    }

    @Test
    fun `bodies and tapers have no collision group and always pass`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 80f))
        )
        assertNull(startOverlapErrorMm(spec, "new", ComponentKind.BODY, 500f, 0f))
        assertNull(startOverlapErrorMm(spec, "new", ComponentKind.TAPER, 500f, 0f))
    }
}
