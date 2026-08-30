package com.android.shaftschematic.ui.drawing

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [renderSpanSpec] — the OAL-less fallback both on-screen canvases lay out from.
 */
class RenderSpanSpecTest {

    @Test
    fun `with no length the span reaches the last occupied end`() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 100f)),
        )
        assertEquals(400f, spec.renderSpanSpec().overallLengthMm, 0.001f)
    }

    @Test
    fun `with no length and nothing placed the spec is unchanged`() {
        val spec = ShaftSpec(overallLengthMm = 0f)
        assertSame(spec, spec.renderSpanSpec())
    }

    /** The fallback never rewrites an authored length — even one the components run past. */
    @Test
    fun `an authored length is returned untouched`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 100f)),
        )
        assertSame(spec, spec.renderSpanSpec())

        val oversized = spec.copy(
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 1400f, diaMm = 100f)),
        )
        assertSame(oversized, oversized.renderSpanSpec())
    }

    /**
     * Excluded threads count here even though they are outside the measured shaft — unlike
     * `coverageEndMm()`, which drops them. A thread drawn past the end of the shaft still has to
     * fit inside the canvas, so the drawn span has to reach it.
     */
    @Test
    fun `an excluded fwd thread still extends the drawn span`() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 100f)),
            threads = listOf(
                Threads(
                    startFromAftMm = 400f, lengthMm = 114f, majorDiaMm = 95f, pitchMm = 6f,
                    excludeFromOAL = true, isAftEnd = false,
                )
            ),
        )
        assertEquals(514f, spec.renderSpanSpec().overallLengthMm, 0.001f)
    }
}
