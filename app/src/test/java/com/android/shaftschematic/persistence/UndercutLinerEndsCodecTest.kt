package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The undercut sheet's per-job "Liner ends from S.E.T." choice (`UndercutRecord.linerEndsFromSet`)
 * through the document envelope and the autosave/backup Json posture. Additive and defaulted: a
 * missing field is off.
 */
class UndercutLinerEndsCodecTest {

    private val legacy = """
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
            "exaggerationFrac": 0.1,
            "railStyle": "CHAIN"
          }
        }
    """.trimIndent()

    @Test
    fun `both values round trip through the envelope`() {
        listOf(true, false).forEach { on ->
            val doc = ShaftDocCodec.ShaftDocV1(
                spec = ShaftSpec(overallLengthMm = 500f),
                undercutRecord = UndercutRecord(
                    undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
                    linerEndsFromSet = on,
                    longCompressFrac = if (on) 0.5f else 0f,
                ),
            )
            val raw = ShaftDocCodec.encodeV1(doc)
            assertTrue("stored: $raw", raw.contains("\"linerEndsFromSet\": $on"))
            assertEquals(on, ShaftDocCodec.decode(raw).undercutRecord.linerEndsFromSet)
            assertEquals(if (on) 0.5f else 0f, ShaftDocCodec.decode(raw).undercutRecord.longCompressFrac, 0f)
        }
    }

    @Test
    fun `a document without the field decodes off with the record intact`() {
        val rec = ShaftDocCodec.decode(legacy).undercutRecord
        assertFalse(rec.linerEndsFromSet)
        assertEquals("weld", rec.undercuts.single().note)
        assertEquals(0.1f, rec.exaggerationFrac, 0f)
    }

    @Test
    fun `the autosave and backup Json posture round trips it`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val rec = UndercutRecord(
            undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
            linerEndsFromSet = true,
        )
        assertEquals(rec, json.decodeFromString(UndercutRecord.serializer(), json.encodeToString(UndercutRecord.serializer(), rec)))
    }
}
