package com.android.shaftschematic.doc

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round-trip coverage for the mixed-units / dual-display envelope fields. */
class UnitOverridesCodecTest {

    private fun docWith(spec: ShaftSpec, overrides: Map<String, UnitSystem>, dual: Boolean) =
        ShaftDocCodec.ShaftDocV1(
            preferredUnit = UnitSystem.INCHES,
            spec = spec,
            unitOverrides = overrides,
            dualUnits = dual,
        )

    @Test
    fun `unit overrides and dual flag round-trip`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val overrides = mapOf("comp-a" to UnitSystem.MILLIMETERS, "comp-b" to UnitSystem.INCHES)
        val raw = ShaftDocCodec.encodeV1(docWith(spec, overrides, dual = true))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(overrides, decoded.unitOverrides)
        assertTrue(decoded.dualUnits)
    }

    @Test
    fun `thread metric designation round-trips`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            threads = listOf(
                Threads(id = "t1", majorDiaMm = 20f, pitchMm = 2.5f, lengthMm = 40f, metricDesignation = "M20×2.5"),
            ),
        )
        val raw = ShaftDocCodec.encodeV1(docWith(spec, emptyMap(), dual = false))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals("M20×2.5", decoded.spec.threads.single().metricDesignation)
    }

    @Test
    fun `older files without the fields decode to empty defaults`() {
        // An envelope written before these fields existed simply omits the keys.
        val legacy = """
            { "version": 1, "preferred_unit": "INCHES", "spec": { "overallLengthMm": 100.0 } }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy)
        assertTrue(decoded.unitOverrides.isEmpty())
        assertFalse(decoded.dualUnits)
    }
}
