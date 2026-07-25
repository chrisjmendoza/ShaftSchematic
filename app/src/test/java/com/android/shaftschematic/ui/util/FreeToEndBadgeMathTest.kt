package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Free-to-End badge's value contract ([freeToEndSignedMm]):
 * effective OAL is [ShaftSpec.overallLengthMm] except when that is exactly 0, in which case
 * the last occupied end stands in (mirroring the preview's safeSpec) so the badge shows 0
 * rather than a phantom negative. Otherwise the signed value is not clamped — a genuinely
 * oversized shaft still goes negative.
 */
class FreeToEndBadgeMathTest {

    private fun body(startMm: Float, lengthMm: Float) =
        Body(id = "b", startFromAftMm = startMm, lengthMm = lengthMm, diaMm = 50f)

    @Test
    fun `oal zero with components yields zero not phantom negative`() {
        val spec = ShaftSpec(overallLengthMm = 0f, bodies = listOf(body(0f, 100f)))
        assertEquals(0f, freeToEndSignedMm(spec), 1e-4f)
    }

    @Test
    fun `oal zero with no components yields zero`() {
        val spec = ShaftSpec(overallLengthMm = 0f)
        assertEquals(0f, freeToEndSignedMm(spec), 1e-4f)
    }

    @Test
    fun `oal greater than fill yields positive free room`() {
        val spec = ShaftSpec(overallLengthMm = 250f, bodies = listOf(body(0f, 100f)))
        assertEquals(150f, freeToEndSignedMm(spec), 1e-4f)
    }

    @Test
    fun `oversized shaft yields negative value`() {
        val spec = ShaftSpec(overallLengthMm = 250f, bodies = listOf(body(0f, 300f)))
        assertEquals(-50f, freeToEndSignedMm(spec), 1e-4f)
    }

    @Test
    fun `exactly filled shaft yields zero`() {
        val spec = ShaftSpec(overallLengthMm = 250f, bodies = listOf(body(0f, 250f)))
        assertEquals(0f, freeToEndSignedMm(spec), 1e-4f)
    }
}
