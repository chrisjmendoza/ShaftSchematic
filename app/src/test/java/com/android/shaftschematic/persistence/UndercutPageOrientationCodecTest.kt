package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UndercutRecord.pageOrientation` through the document envelope and the other Json instances
 * that carry the record (autosave drafts, backups — the same `@Serializable` class under the same
 * `ignoreUnknownKeys` + `encodeDefaults` posture). Additive and defaulted, and decoded by name
 * tolerantly: a missing field or an unknown name (a newer build's value) is AUTO, never a failed
 * document.
 */
class UndercutPageOrientationCodecTest {

    private fun envelope(orientationJson: String?): String = """
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
            "exaggerationFrac": 0.1${if (orientationJson != null) ",\n            \"pageOrientation\": $orientationJson" else ""}
          }
        }
    """.trimIndent()

    @Test
    fun `every value round trips through the envelope`() {
        UndercutPageOrientation.entries.forEach { o ->
            val doc = ShaftDocCodec.ShaftDocV1(
                spec = ShaftSpec(overallLengthMm = 500f),
                undercutRecord = UndercutRecord(
                    undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
                    pageOrientation = o,
                ),
            )
            val raw = ShaftDocCodec.encodeV1(doc)
            assertTrue("stored by name: $raw", raw.contains("\"pageOrientation\": \"${o.name}\""))
            assertEquals(o, ShaftDocCodec.decode(raw).undercutRecord.pageOrientation)
        }
    }

    @Test
    fun `a document without the field decodes to AUTO`() {
        val decoded = ShaftDocCodec.decode(envelope(null))
        assertEquals(UndercutPageOrientation.AUTO, decoded.undercutRecord.pageOrientation)
        assertEquals(1, decoded.undercutRecord.undercuts.size)
    }

    @Test
    fun `an unknown value decodes to AUTO with the record's cuts intact`() {
        val decoded = ShaftDocCodec.decode(envelope("\"SIDEWAYS\""))
        val rec = decoded.undercutRecord
        assertEquals(UndercutPageOrientation.AUTO, rec.pageOrientation)
        val cut = rec.undercuts.single()
        assertEquals("u1", cut.id)
        assertEquals(140.25f, cut.diaMm, 0f)
        assertEquals("weld", cut.note)
        assertEquals(0.1f, rec.exaggerationFrac, 0f)
    }

    @Test
    fun `a known value in a hand-written file decodes`() {
        assertEquals(
            UndercutPageOrientation.PORTRAIT,
            ShaftDocCodec.decode(envelope("\"PORTRAIT\"")).undercutRecord.pageOrientation,
        )
    }

    @Test
    fun `the autosave and backup Json posture decodes the record tolerantly too`() {
        // AutosaveManager and ShaftBackup both run `ignoreUnknownKeys = true; encodeDefaults = true`
        // over the same serializable class.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val rec = UndercutRecord(
            undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
            pageOrientation = UndercutPageOrientation.LANDSCAPE,
        )
        val raw = json.encodeToString(UndercutRecord.serializer(), rec)
        assertEquals(rec, json.decodeFromString(UndercutRecord.serializer(), raw))

        val unknown = raw.replace("\"LANDSCAPE\"", "\"SIDEWAYS\"")
        val decoded = json.decodeFromString(UndercutRecord.serializer(), unknown)
        assertEquals(UndercutPageOrientation.AUTO, decoded.pageOrientation)
        assertEquals(rec.undercuts, decoded.undercuts)
    }

    @Test
    fun `fromName falls back to AUTO`() {
        assertEquals(UndercutPageOrientation.AUTO, UndercutPageOrientation.fromName(null))
        assertEquals(UndercutPageOrientation.AUTO, UndercutPageOrientation.fromName(""))
        assertEquals(UndercutPageOrientation.AUTO, UndercutPageOrientation.fromName("portrait"))
        assertEquals(UndercutPageOrientation.PORTRAIT, UndercutPageOrientation.fromName("PORTRAIT"))
    }
}
