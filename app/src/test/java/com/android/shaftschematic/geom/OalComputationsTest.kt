package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Test

class OalComputationsTest {

    @Test
    fun `excluded aft thread shifts measure origin so following body starts at 0`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val body = Body(startFromAftMm = 100f, lengthMm = 200f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), bodies = listOf(body))

        val win = computeOalWindow(spec)

        assertEquals(100.0, win.measureStartMm, 1e-9)
        assertEquals(0.0, win.toMeasureX(body.startFromAftMm.toDouble()), 1e-9)
    }

    @Test
    fun `excluded aft thread shifts measure origin so following taper starts at 0`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val taper = Taper(
            startFromAftMm = 100f,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th), tapers = listOf(taper))

        val win = computeOalWindow(spec)

        assertEquals(100.0, win.measureStartMm, 1e-9)
        assertEquals(0.0, win.toMeasureX(taper.startFromAftMm.toDouble()), 1e-9)
    }
}
