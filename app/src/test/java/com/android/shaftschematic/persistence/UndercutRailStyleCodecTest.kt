package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRailStyle
import com.android.shaftschematic.model.UndercutRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The undercut sheet's per-job "Dimensions" choice (`UndercutRecord.railStyle`) through the document
 * envelope and the autosave/backup Json posture. Additive and defaulted: a missing field is CHAIN, an
 * unknown name decodes to CHAIN, never a failed document.
 */
class UndercutRailStyleCodecTest {

    private fun envelope(extra: String): String = """
        {
          "version": 1,
          "preferred_unit": "INCHES",
          "unit_locked": true,
          "job_number": "",
          "customer": "",
          "vessel": "",
          "shaft_position": "OTHER",
          "notes": "",
          "spec": { "overallLengthMm": 500.0 },
          "undercut_record": {
            "undercuts": [
              { "id": "u1", "startFromAftMm": 10.0, "lengthMm": 5.0, "diaMm": 140.25,
                "authoredReference": "AFT_SET", "note": "weld" }
            ],
            "exaggerationFrac": 0.1$extra
          }
        }
    """.trimIndent()

    @Test
    fun `every rail style round trips through the envelope by name`() {
        UndercutRailStyle.entries.forEach { style ->
            val doc = ShaftDocCodec.ShaftDocV1(
                spec = ShaftSpec(overallLengthMm = 500f),
                undercutRecord = UndercutRecord(
                    undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
                    railStyle = style,
                ),
            )
            val raw = ShaftDocCodec.encodeV1(doc)
            assertTrue("stored by name: $raw", raw.contains("\"railStyle\": \"${style.name}\""))
            assertEquals(style, ShaftDocCodec.decode(raw).undercutRecord.railStyle)
        }
    }

    @Test
    fun `a document without the field decodes to chain`() {
        val rec = ShaftDocCodec.decode(envelope("")).undercutRecord
        assertEquals(UndercutRailStyle.CHAIN, rec.railStyle)
        assertEquals(1, rec.undercuts.size)
    }

    @Test
    fun `an unknown rail style decodes to chain with the record intact`() {
        val rec = ShaftDocCodec.decode(envelope(",\n    \"railStyle\": \"ORDINATE\"")).undercutRecord
        assertEquals(UndercutRailStyle.CHAIN, rec.railStyle)
        assertEquals("weld", rec.undercuts.single().note)
        assertEquals(0.1f, rec.exaggerationFrac, 0f)
    }

    @Test
    fun `the autosave and backup Json posture decodes it tolerantly too`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val rec = UndercutRecord(
            undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
            railStyle = UndercutRailStyle.FROM_SET,
        )
        val raw = json.encodeToString(UndercutRecord.serializer(), rec)
        assertEquals(rec, json.decodeFromString(UndercutRecord.serializer(), raw))
        val unknown = raw.replace("\"FROM_SET\"", "\"SIDEWAYS\"")
        assertEquals(UndercutRailStyle.CHAIN, json.decodeFromString(UndercutRecord.serializer(), unknown).railStyle)
    }

    @Test
    fun `fromName falls back to chain`() {
        assertEquals(UndercutRailStyle.CHAIN, UndercutRailStyle.fromName(null))
        assertEquals(UndercutRailStyle.CHAIN, UndercutRailStyle.fromName(""))
        assertEquals(UndercutRailStyle.CHAIN, UndercutRailStyle.fromName("from_set"))
        assertEquals(UndercutRailStyle.FROM_SET, UndercutRailStyle.fromName("FROM_SET"))
    }
}
