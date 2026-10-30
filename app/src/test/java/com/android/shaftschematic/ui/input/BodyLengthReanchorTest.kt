package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [bodyPhysStartForNewLength] — the re-anchor behind the explicit body card's Length field
 * (`BodyPagerCard.kt`): an AFT-measured body keeps its start and lets the FWD end move; a
 * FWD-measured body keeps its FWD end (its authored distance from the FWD face) and slides the
 * canonical start by the length change.
 */
class BodyLengthReanchorTest {

    private fun body(start: Float, len: Float, ref: LinerAuthoredReference) =
        Body(startFromAftMm = start, lengthMm = len, diaMm = 100f, authoredReference = ref)

    @Test
    fun `aft ref - start unchanged on grow and shrink`() {
        val b = body(50f, 100f, LinerAuthoredReference.AFT)
        assertEquals(50f, bodyPhysStartForNewLength(b, 150f, 300f), 0.001f)
        assertEquals(50f, bodyPhysStartForNewLength(b, 40f, 300f), 0.001f)
    }

    @Test
    fun `fwd ref - fwd end pinned on grow`() {
        // start 150, len 100 → FWD end at 250, 50 from the FWD face of a 300 shaft
        val b = body(150f, 100f, LinerAuthoredReference.FWD)
        val newStart = bodyPhysStartForNewLength(b, 150f, 300f)
        assertEquals(100f, newStart, 0.001f)
        assertEquals(250f, newStart + 150f, 0.001f)
    }

    @Test
    fun `fwd ref - fwd end pinned on shrink`() {
        val b = body(150f, 100f, LinerAuthoredReference.FWD)
        val newStart = bodyPhysStartForNewLength(b, 50f, 300f)
        assertEquals(200f, newStart, 0.001f)
        assertEquals(250f, newStart + 50f, 0.001f)
    }

    @Test
    fun `fwd ref - flush body stays flush`() {
        val b = body(200f, 100f, LinerAuthoredReference.FWD)
        val newStart = bodyPhysStartForNewLength(b, 120f, 300f)
        assertEquals(300f, newStart + 120f, 0.001f)
    }
}
