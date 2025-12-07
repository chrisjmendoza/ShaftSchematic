package com.android.shaftschematic.model

import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ShaftSpecSnapExtensionsTest {

    @Test
    fun `snapForwardFrom snaps mixed chain body-taper-thread-liner`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val taper = Taper(id = "t1", startFromAftMm = 120f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 40f)
        val thread = Threads(id = "th1", startFromAftMm = 190f, lengthMm = 10f, majorDiaMm = 40f, pitchMm = 5f)
        val liner = Liner(id = "ln1", startFromAftMm = 210f, lengthMm = 20f, odMm = 60f)

        val spec = ShaftSpec(
            overallLengthMm = 500f,
            bodies = listOf(body),
            tapers = listOf(taper),
            threads = listOf(thread),
            liners = listOf(liner),
        )

        val snapped = spec.snapForwardFrom(ComponentKey("b1", ComponentKind.BODY))

        val b = snapped.bodies.single()
        val t = snapped.tapers.single()
        val th = snapped.threads.single()
        val ln = snapped.liners.single()

        // Body unchanged
        assertEquals(0f, b.startFromAftMm)
        assertEquals(100f, b.lengthMm)

        // Taper snapped to body end
        assertEquals(100f, t.startFromAftMm)

        // Thread snapped to taper end (100 + 50)
        assertEquals(150f, th.startFromAftMm)

        // Liner snapped to thread end (150 + 10)
        assertEquals(160f, ln.startFromAftMm)
    }

    @Test
    fun `snapForwardFrom no-op when anchor is last`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 200f, bodies = listOf(body))

        val snapped = spec.snapForwardFrom(ComponentKey("b1", ComponentKind.BODY))

        assertEquals(0f, snapped.bodies.single().startFromAftMm)
        assertEquals(100f, snapped.bodies.single().lengthMm)
    }
}

