package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence for the per-job coupling-face election ([RunoutConfig.showCouplingFace]) and the
 * pilot runout that rides the readings list under [COUPLING_PILOT_COMPONENT_ID] — additive +
 * defaulted on the existing envelope elements, same posture as `heightScale` and the liner pair.
 */
class RunoutConfigCouplingFaceTest {

    @Test
    fun `envelope round trip preserves the election`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 1000f),
            runoutConfig = RunoutConfig(showCouplingFace = true),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertTrue(decoded.runoutConfig.showCouplingFace)
    }

    @Test
    fun `a runout_config json without the field decodes to face-off`() {
        // A file written before the field existed reprints exactly as it did: not every
        // inspection measures the coupling, so the face is elected, never inherited.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": { "overallLengthMm": 500.0 },
              "runout_config": { "componentOverrides": {}, "tirDirection": "AFT" }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertFalse(decoded.runoutConfig.showCouplingFace)
        assertFalse(RunoutConfig().showCouplingFace)
    }

    @Test
    fun `the reserved pilot reading survives decode though it matches no component`() {
        // The pilot id deliberately names no resolved component. Runout readings are never
        // pruned at decode, so it must ride through untouched — losing it would silently
        // erase a typed measurement.
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 1000f),
            runoutConfig = RunoutConfig(showCouplingFace = true),
            runoutReadings = RunoutReadings(
                listOf(
                    RunoutReading(
                        componentId = COUPLING_PILOT_COMPONENT_ID,
                        stationIndex = 0,
                        valueMm = 0.08f,
                        highSpotHalfHours = 6,
                    )
                )
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))
        val pilot = decoded.runoutReadings.find(COUPLING_PILOT_COMPONENT_ID, 0)

        assertNotNull("the reserved pilot reading must not be pruned", pilot)
        assertEquals(0.08f, pilot!!.valueMm!!, 1e-6f)
        assertEquals(6, pilot.highSpotHalfHours)
    }
}
