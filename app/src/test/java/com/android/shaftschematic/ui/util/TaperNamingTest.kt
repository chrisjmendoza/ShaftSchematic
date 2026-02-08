package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class TaperNamingTest {

    @Test
    fun `empty tapers yields empty map`() {
        val spec = ShaftSpec(overallLengthMm = 100f, tapers = emptyList())
        assertEquals(emptyMap<String, String>(), buildTaperTitleById(spec))
    }

    @Test
    fun `single taper increasing dia is AFT Taper with no number`() {
        val t = Taper(
            id = "T1",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 6f,
            endDiaMm = 7f,
            orientation = TaperOrientation.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 100f, tapers = listOf(t))

        assertEquals(mapOf("T1" to "AFT Taper"), buildTaperTitleById(spec))
    }

    @Test
    fun `single taper decreasing dia is FWD Taper with no number`() {
        val t = Taper(
            id = "T1",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 7f,
            endDiaMm = 6f,
            orientation = TaperOrientation.FWD
        )
        val spec = ShaftSpec(overallLengthMm = 100f, tapers = listOf(t))

        assertEquals(mapOf("T1" to "FWD Taper"), buildTaperTitleById(spec))
    }

    @Test
    fun `multiple AFT tapers get numbered by physical order`() {
        val t2 = Taper(
            id = "T2",
            startFromAftMm = 100f,
            lengthMm = 10f,
            startDiaMm = 7f,
            endDiaMm = 8f,
            orientation = TaperOrientation.AFT
        )
        val t1 = Taper(
            id = "T1",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 6f,
            endDiaMm = 7f,
            orientation = TaperOrientation.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 200f, tapers = listOf(t2, t1))

        assertEquals(
            mapOf(
                "T1" to "AFT Taper #1",
                "T2" to "AFT Taper #2",
            ),
            buildTaperTitleById(spec),
        )
    }

    @Test
    fun `AFT and FWD tapers are numbered independently and only when needed`() {
        val aft = Taper(
            id = "A",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 6f,
            endDiaMm = 7f,
            orientation = TaperOrientation.AFT
        )
        val fwd = Taper(
            id = "F",
            startFromAftMm = 50f,
            lengthMm = 10f,
            startDiaMm = 7f,
            endDiaMm = 6f,
            orientation = TaperOrientation.FWD
        )
        val spec = ShaftSpec(overallLengthMm = 100f, tapers = listOf(fwd, aft))

        assertEquals(
            mapOf(
                "A" to "AFT Taper",
                "F" to "FWD Taper",
            ),
            buildTaperTitleById(spec),
        )
    }

    @Test
    fun `tie-break uses id when start positions equal`() {
        val a = Taper(
            id = "A",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 6f,
            endDiaMm = 7f,
            orientation = TaperOrientation.AFT
        )
        val b = Taper(
            id = "B",
            startFromAftMm = 0f,
            lengthMm = 10f,
            startDiaMm = 7f,
            endDiaMm = 6f,
            orientation = TaperOrientation.FWD
        )
        val spec = ShaftSpec(overallLengthMm = 100f, tapers = listOf(b, a))

        // Each direction count = 1, so no numbering.
        assertEquals(
            mapOf(
                "A" to "AFT Taper",
                "B" to "FWD Taper",
            ),
            buildTaperTitleById(spec),
        )
    }
}
