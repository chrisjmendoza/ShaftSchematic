package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Forward-compatibility guard: a document written by a NEWER app version must be refused,
 * not silently decoded with its unknown fields dropped (re-saving such a file would
 * destroy the newer data).
 */
class DocVersionTest {

    private fun envelopeJson(version: Int): String = """
        {
          "version": $version,
          "preferred_unit": "INCHES",
          "unit_locked": true,
          "job_number": "J-100",
          "customer": "Test Customer",
          "vessel": "Test Vessel",
          "shaft_position": "PORT",
          "notes": "",
          "spec": { "overallLengthMm": 1000.0 },
          "some_future_field": { "critical": "data the current build does not understand" }
        }
    """.trimIndent()

    @Test
    fun `current version decodes normally and ignores unknown keys`() {
        val decoded = ShaftDocCodec.decode(envelopeJson(ShaftDocCodec.CURRENT_VERSION))

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals("J-100", decoded.jobNumber)
        assertEquals(1000f, decoded.spec.overallLengthMm, 0.001f)
    }

    @Test
    fun `newer version is refused instead of silently losing data`() {
        val ex = assertThrows(ShaftDocCodec.UnsupportedDocVersionException::class.java) {
            ShaftDocCodec.decode(envelopeJson(ShaftDocCodec.CURRENT_VERSION + 1))
        }
        assertEquals(ShaftDocCodec.CURRENT_VERSION + 1, ex.docVersion)
    }

    @Test
    fun `legacy spec-only file still decodes via fallback`() {
        val legacy = """{ "overallLengthMm": 500.0 }"""

        val decoded = ShaftDocCodec.decode(legacy)

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertEquals(500f, decoded.spec.overallLengthMm, 0.001f)
    }

    @Test
    fun `encode round trip stays at current version`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = ShaftSpec(overallLengthMm = 250f))
        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(250f, decoded.spec.overallLengthMm, 0.001f)
    }
}
