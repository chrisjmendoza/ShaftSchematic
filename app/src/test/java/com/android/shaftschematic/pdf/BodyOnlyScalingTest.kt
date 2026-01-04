package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.maxOuterDiaMm
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyOnlyScalingTest {

    @Test
    fun `body-only huge diameter clamps to target height`() {
        // Arrange: a simple body-only shaft where width is not the limiting factor.
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            bodies = listOf(
                Body(
                    startFromAftMm = 0f,
                    lengthMm = 100f,
                    diaMm = 1_000f
                )
            )
        )

        val geomWidthPt = 500f

        // Act
        val ptPerMm = computeBodyOnlyPtPerMm(spec, geomWidthPt)
        val renderedHeightPt = spec.maxOuterDiaMm() * ptPerMm

        // Assert: diameter increases should not exceed the fixed target drawing height.
        val targetHeightPt = 1.25f * 72f
        assertEquals(targetHeightPt, renderedHeightPt, 0.01f)
    }
}
