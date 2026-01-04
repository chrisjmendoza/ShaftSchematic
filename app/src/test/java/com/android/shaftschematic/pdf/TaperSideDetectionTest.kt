package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TaperSideDetectionTest {

    @Test
    fun `single taper near start defaults to AFT`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(
                    startFromAftMm = 0f,
                    lengthMm = 200f,
                    startDiaMm = 50f,
                    endDiaMm = 40f
                )
            )
        )

        val sel = selectFooterTapers(spec)
        assertNotNull(sel.aft)
        assertNull(sel.fwd)
    }

    @Test
    fun `single taper near end is FWD`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(
                    startFromAftMm = 800f,
                    lengthMm = 200f,
                    startDiaMm = 50f,
                    endDiaMm = 40f
                )
            )
        )

        val sel = selectFooterTapers(spec)
        assertNull(sel.aft)
        assertNotNull(sel.fwd)
    }

    @Test
    fun `two tapers choose leftmost AFT and rightmost FWD`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(
                    startFromAftMm = 0f,
                    lengthMm = 150f,
                    startDiaMm = 60f,
                    endDiaMm = 50f
                ),
                Taper(
                    startFromAftMm = 850f,
                    lengthMm = 150f,
                    startDiaMm = 50f,
                    endDiaMm = 40f
                )
            )
        )

        val sel = selectFooterTapers(spec)
        assertNotNull(sel.aft)
        assertNotNull(sel.fwd)
    }
}
