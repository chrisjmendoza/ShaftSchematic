package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Persistence tests for [RunoutConfig.heightScale] — the "Shaft height" slider multiplier,
 * additive + defaulted on the existing `runout_config` envelope element (no codec version
 * bump). See `geom/ProfileCompression.kt` (`exaggeratedProfileScale`).
 */
class RunoutConfigHeightScaleTest {

    @Test
    fun `envelope round trip preserves heightScale`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 1000f),
            runoutConfig = RunoutConfig(heightScale = 1.75f),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(1.75f, decoded.runoutConfig.heightScale, 0.0001f)
    }

    @Test
    fun `a runout_config json without heightScale decodes to the 1_0 default`() {
        // Simulates a file written before this field existed: runout_config carries only
        // the fields that predate the "Shaft height" slider.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": { "overallLengthMm": 500.0 },
              "runout_config": { "componentOverrides": {}, "tirDirection": "AFT" }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(1.0f, decoded.runoutConfig.heightScale, 0.0001f)
    }
}
