package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutSetDatum
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The undercut sheet's per-job "Distance to S.E.T." choice (`UndercutRecord.setDatum`) and its
 * "Sections to print" exclusion lists (`excludedLinerIds`, `excludedFreeCutIds`) through the
 * document envelope and the autosave/backup Json posture. Additive and defaulted: a missing field
 * is NEAREST / nothing excluded, an unknown datum name decodes to NEAREST, never a failed document,
 * and an orphan id survives decode untouched.
 */
class UndercutSheetElectionCodecTest {

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
    fun `every datum and both lists round trip through the envelope`() {
        UndercutSetDatum.entries.forEach { d ->
            val doc = ShaftDocCodec.ShaftDocV1(
                spec = ShaftSpec(overallLengthMm = 500f),
                undercutRecord = UndercutRecord(
                    undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
                    setDatum = d,
                    excludedLinerIds = listOf("L2", "gone-liner"),
                    excludedFreeCutIds = listOf("u1", "gone-cut"),
                ),
            )
            val raw = ShaftDocCodec.encodeV1(doc)
            assertTrue("stored by name: $raw", raw.contains("\"setDatum\": \"${d.name}\""))
            val rec = ShaftDocCodec.decode(raw).undercutRecord
            assertEquals(d, rec.setDatum)
            // Orphans ride along verbatim: skipped at render, never pruned at decode.
            assertEquals(listOf("L2", "gone-liner"), rec.excludedLinerIds)
            assertEquals(listOf("u1", "gone-cut"), rec.excludedFreeCutIds)
        }
    }

    @Test
    fun `a document without the fields decodes to nearest and everything printing`() {
        val rec = ShaftDocCodec.decode(envelope("")).undercutRecord
        assertEquals(UndercutSetDatum.NEAREST, rec.setDatum)
        assertEquals(emptyList<String>(), rec.excludedLinerIds)
        assertEquals(emptyList<String>(), rec.excludedFreeCutIds)
        assertEquals(1, rec.undercuts.size)
    }

    @Test
    fun `an unknown datum decodes to nearest with the record intact`() {
        val rec = ShaftDocCodec.decode(
            envelope(",\n    \"setDatum\": \"COUPLING\",\n    \"excludedLinerIds\": [\"L1\"]"),
        ).undercutRecord
        assertEquals(UndercutSetDatum.NEAREST, rec.setDatum)
        assertEquals(listOf("L1"), rec.excludedLinerIds)
        assertEquals("weld", rec.undercuts.single().note)
        assertEquals(0.1f, rec.exaggerationFrac, 0f)
    }

    @Test
    fun `the autosave and backup Json posture decodes them tolerantly too`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val rec = UndercutRecord(
            undercuts = listOf(Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f)),
            setDatum = UndercutSetDatum.FWD,
            excludedLinerIds = listOf("L1"),
            excludedFreeCutIds = listOf("u1"),
        )
        val raw = json.encodeToString(UndercutRecord.serializer(), rec)
        assertEquals(rec, json.decodeFromString(UndercutRecord.serializer(), raw))
        val unknown = raw.replace("\"FWD\"", "\"SIDEWAYS\"")
        assertEquals(UndercutSetDatum.NEAREST, json.decodeFromString(UndercutRecord.serializer(), unknown).setDatum)
    }

    @Test
    fun `fromName falls back to nearest`() {
        assertEquals(UndercutSetDatum.NEAREST, UndercutSetDatum.fromName(null))
        assertEquals(UndercutSetDatum.NEAREST, UndercutSetDatum.fromName(""))
        assertEquals(UndercutSetDatum.NEAREST, UndercutSetDatum.fromName("aft"))
        assertEquals(UndercutSetDatum.AFT, UndercutSetDatum.fromName("AFT"))
    }
}
