package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyNamingTest {

    @Test
    fun `empty bodies yields empty map`() {
        val spec = ShaftSpec(overallLengthMm = 100f, bodies = emptyList())
        assertEquals(emptyMap<String, String>(), buildBodyTitleById(spec))
    }

    @Test
    fun `bodies numbered by physical aft to fwd order regardless of list order`() {
        val b2 = Body(id = "B2", startFromAftMm = 50f, lengthMm = 10f, diaMm = 1f)
        val b1 = Body(id = "B1", startFromAftMm = 0f, lengthMm = 10f, diaMm = 1f)
        val spec = ShaftSpec(overallLengthMm = 100f, bodies = listOf(b2, b1))

        assertEquals(
            mapOf(
                "B1" to "Body #1",
                "B2" to "Body #2",
            ),
            buildBodyTitleById(spec),
        )
    }

    @Test
    fun `tie-break uses id when start positions equal`() {
        val a = Body(id = "A", startFromAftMm = 0f, lengthMm = 10f, diaMm = 1f)
        val b = Body(id = "B", startFromAftMm = 0f, lengthMm = 10f, diaMm = 1f)
        val spec = ShaftSpec(overallLengthMm = 100f, bodies = listOf(b, a))

        assertEquals(
            mapOf(
                "A" to "Body #1",
                "B" to "Body #2",
            ),
            buildBodyTitleById(spec),
        )
    }
}
