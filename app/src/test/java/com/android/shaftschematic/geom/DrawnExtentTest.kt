package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Test

/** [drawnExtentMm]: the span a sheet must map so every drawn component lands on the page. */
class DrawnExtentTest {

    @Test
    fun `a shaft with no excluded thread spans exactly its overall length`() {
        val spec = ShaftSpec(overallLengthMm = 4000f)
        assertEquals(DrawnExtentMm(0f, 4000f), spec.drawnExtentMm())
    }

    @Test
    fun `an excluded AFT thread widens the start before the datum`() {
        // On-device report: a 6" thread excluded from the OAL at the AFT end drew off the left
        // edge of the consolidated page, because the x map started at the AFT S.E.T.
        val spec = ShaftSpec(
            overallLengthMm = 4000f,
            threads = listOf(
                Threads(startFromAftMm = -152.4f, lengthMm = 152.4f, majorDiaMm = 200f, pitchMm = 6.35f, excludeFromOAL = true),
            ),
        )
        val extent = spec.drawnExtentMm()
        assertEquals(-152.4f, extent.startMm, 1e-3f)
        assertEquals(4000f, extent.endMm, 1e-3f)
        assertEquals(4152.4f, extent.spanMm, 1e-3f)
    }

    @Test
    fun `an excluded FWD thread widens the end past the overall length`() {
        val spec = ShaftSpec(
            overallLengthMm = 4000f,
            threads = listOf(
                Threads(startFromAftMm = 4000f, lengthMm = 100f, majorDiaMm = 200f, pitchMm = 6.35f, excludeFromOAL = true, isAftEnd = false),
            ),
        )
        assertEquals(DrawnExtentMm(0f, 4100f), spec.drawnExtentMm())
    }

    @Test
    fun `a thread counted in the overall length changes nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 4000f,
            threads = listOf(
                Threads(startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 200f, pitchMm = 6.35f, excludeFromOAL = false),
            ),
        )
        assertEquals(DrawnExtentMm(0f, 4000f), spec.drawnExtentMm())
    }
}
