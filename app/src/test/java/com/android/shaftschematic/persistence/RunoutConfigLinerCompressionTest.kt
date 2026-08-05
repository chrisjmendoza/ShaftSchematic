package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Persistence tests for the per-job liner-proportionality pair
 * ([RunoutConfig.linersProportional] + [RunoutConfig.linerCompression]) — additive +
 * defaulted on the existing `runout_config` envelope element, same posture as
 * `heightScale`. Also pins the derived [RunoutConfig.linerMinFracOfTrue] mapping the
 * composers consume.
 */
class RunoutConfigLinerCompressionTest {

    @Test
    fun `envelope round trip preserves the liner pair`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 1000f),
            runoutConfig = RunoutConfig(linersProportional = true, linerCompression = 0.4f),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(true, decoded.runoutConfig.linersProportional)
        assertEquals(0.4f, decoded.runoutConfig.linerCompression, 0.0001f)
    }

    @Test
    fun `a runout_config json without the pair decodes to the historical defaults`() {
        // A file written before these fields existed: liners compress freely.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": { "overallLengthMm": 500.0 },
              "runout_config": { "componentOverrides": {}, "tirDirection": "AFT" }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertFalse(decoded.runoutConfig.linersProportional)
        assertEquals(1.0f, decoded.runoutConfig.linerCompression, 0.0001f)
        assertEquals(0f, decoded.runoutConfig.linerMinFracOfTrue, 0.0001f)
    }

    @Test
    fun `derived frac - checkbox pins, slider maps inverted, checkbox wins`() {
        assertEquals(0f, RunoutConfig(linerCompression = 1.0f).linerMinFracOfTrue, 1e-6f)
        assertEquals(0.7f, RunoutConfig(linerCompression = 0.3f).linerMinFracOfTrue, 1e-6f)
        assertEquals(1f, RunoutConfig(linerCompression = 0f).linerMinFracOfTrue, 1e-6f)
        assertEquals(
            1f,
            RunoutConfig(linersProportional = true, linerCompression = 1.0f).linerMinFracOfTrue,
            1e-6f,
        )
    }
}
