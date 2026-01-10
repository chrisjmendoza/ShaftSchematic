package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class LinerNamingTest {

    @Test
    fun `empty liners yields empty map`() {
        val spec = ShaftSpec(overallLengthMm = 100f, liners = emptyList())
        assertEquals(emptyMap<String, String>(), buildLinerTitleById(spec))
    }

    @Test
    fun `single liner no label uses positional name with no number`() {
        val ln = Liner(id = "L1", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 100f, liners = listOf(ln))

        assertEquals(mapOf("L1" to "AFT Liner"), buildLinerTitleById(spec))
    }

    @Test
    fun `custom label overrides default and is trimmed`() {
        val ln = Liner(id = "L1", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f, label = "  Cutless  ")
        val spec = ShaftSpec(overallLengthMm = 100f, liners = listOf(ln))

        assertEquals(mapOf("L1" to "Cutless"), buildLinerTitleById(spec))
    }

    @Test
    fun `multiple liners are numbered by aft to fwd position regardless of list order`() {
        val aft = Liner(id = "A", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f)
        val mid = Liner(id = "M", startFromAftMm = 100f, lengthMm = 10f, odMm = 50f)
        val fwd = Liner(id = "F", startFromAftMm = 200f, lengthMm = 10f, odMm = 50f)

        // Intentionally shuffled input order.
        val spec = ShaftSpec(overallLengthMm = 300f, liners = listOf(fwd, aft, mid))

        assertEquals(
            mapOf(
                "A" to "AFT Liner",
                "M" to "MID Liner",
                "F" to "FWD Liner",
            ),
            buildLinerTitleById(spec),
        )
    }

    @Test
    fun `two liners are always categorized as aft and fwd (no mid)`() {
        // If we used pure region-by-ratio naming, the second liner's center would be ~50% of OAL
        // and could incorrectly land in MID. For a two-liner setup we want AFT + FWD.
        val aft = Liner(id = "A", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f)
        val forwardButMidByRatio = Liner(id = "B", startFromAftMm = 45f, lengthMm = 10f, odMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 100f, liners = listOf(forwardButMidByRatio, aft))

        assertEquals(
            mapOf(
                "A" to "AFT Liner",
                "B" to "FWD Liner",
            ),
            buildLinerTitleById(spec),
        )
    }

    @Test
    fun `tie-break uses id when start positions equal`() {
        val a = Liner(id = "A", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f)
        val b = Liner(id = "B", startFromAftMm = 0f, lengthMm = 10f, odMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 100f, liners = listOf(b, a))

        assertEquals(
            mapOf(
                "A" to "AFT Liner",
                "B" to "FWD Liner",
            ),
            buildLinerTitleById(spec),
        )
    }
}
