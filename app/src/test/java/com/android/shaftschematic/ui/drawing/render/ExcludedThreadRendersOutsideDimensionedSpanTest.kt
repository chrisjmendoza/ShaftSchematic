package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.resolvedStartFromAftMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedThreadRendersOutsideDimensionedSpanTest {

    @Test
    fun `excluded aft thread renders outside dimensioned span`() {
        val thread = Threads(
            id = "TH-AFT",
            startFromAftMm = 40f,
            lengthMm = 20f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true,
            endAttachment = ThreadAttachment.AFT
        )
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            tapers = listOf(
                Taper(
                    id = "TAPER-AFT",
                    startFromAftMm = 0f,
                    lengthMm = 50f,
                    startDiaMm = 70f,
                    endDiaMm = 40f
                )
            ),
            threads = listOf(thread)
        )

        val layout = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f,
            topPx = 0f,
            rightPx = 1000f,
            bottomPx = 400f,
            marginPx = 0f
        )

        val renderOriginMm = -layout.minXMm
        assertEquals(thread.lengthMm, renderOriginMm, 1e-3f)
        assertEquals(0f, thread.resolvedStartFromAftMm(spec.overallLengthMm), 1e-3f)

        val taperStartX = layout.xPx(0f)
        val threadStartX = layout.xPx(-thread.lengthMm)
        val threadEndX = layout.xPx(0f)

        assertTrue(threadStartX < taperStartX)
        assertTrue(threadEndX <= taperStartX + 1e-3f)
    }
}
