package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence tests for the liner wear-inspection record (Phase 1 of
 * `docs/LinerWearAreas_Proposal.md`): envelope round-trip, legacy-file default,
 * and the orphan-filtering policy (§3, §7 rule 6).
 */
class WearRecordPersistenceTest {

    private fun linerSpec(linerId: String, overallLengthMm: Float = 500f): ShaftSpec =
        ShaftSpec(
            overallLengthMm = overallLengthMm,
            liners = listOf(
                Liner(id = linerId, startFromAftMm = 0f, lengthMm = 200f, odMm = 50f, endMmPhysical = 200f)
            ),
        )

    @Test
    fun `envelope round trip preserves wear record`() {
        val spot = WearSpot(
            id = "spot-1",
            linerId = "ln1",
            startMm = 25f,
            lengthMm = 40f,
            minDiaMm = 138.5f,
            note = "scored, 6 o'clock",
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(spots = listOf(spot)),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected wear_record key in JSON", raw.contains("\"wear_record\""))

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(1, decoded.wearRecord.spots.size)
        val decodedSpot = decoded.wearRecord.spots.single()
        assertEquals("spot-1", decodedSpot.id)
        assertEquals("ln1", decodedSpot.linerId)
        assertEquals(25f, decodedSpot.startMm, 0.001f)
        assertEquals(40f, decodedSpot.lengthMm, 0.001f)
        assertEquals(138.5f, decodedSpot.minDiaMm, 0.001f)
        assertEquals("scored, 6 o'clock", decodedSpot.note)
    }

    @Test
    fun `authoredReference round trips through the envelope without a version bump`() {
        // Chris's 2026-07-18 post-review spec: WearSpot gains an additive, defaulted
        // `authoredReference` field — no envelope version bump, so the round trip must still
        // land on ENVELOPE_V1 and preserve the non-default reference exactly.
        val spot = WearSpot(
            id = "spot-1", linerId = "ln1", startMm = 25f, lengthMm = 40f,
            authoredReference = WearSpotReference.FWD_SET,
        )
        val doc = ShaftDocCodec.ShaftDocV1(spec = linerSpec("ln1"), wearRecord = WearRecord(spots = listOf(spot)))

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(WearSpotReference.FWD_SET, decoded.wearRecord.spots.single().authoredReference)
    }

    @Test
    fun `a wear_record json without authoredReference decodes to the LINER_AFT default`() {
        // Simulates a file written before this field existed: the spot object has no
        // "authoredReference" key at all.
        val raw = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "unit_locked": true,
              "job_number": "",
              "customer": "",
              "vessel": "",
              "shaft_position": "OTHER",
              "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": {
                "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(WearSpotReference.LINER_AFT, decoded.wearRecord.spots.single().authoredReference)
    }

    @Test
    fun `envelope without wear_record field decodes to empty record`() {
        // Simulates a file written before this field existed: no "wear_record" key at all.
        val raw = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "unit_locked": true,
              "job_number": "",
              "customer": "",
              "vessel": "",
              "shaft_position": "OTHER",
              "notes": "",
              "spec": { "overallLengthMm": 500.0 }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertTrue(decoded.wearRecord.spots.isEmpty())
    }

    @Test
    fun `legacy bare-spec file decodes to empty wear record`() {
        val legacy = """{ "overallLengthMm": 500.0 }"""

        val decoded = ShaftDocCodec.decode(legacy)

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertTrue(decoded.wearRecord.spots.isEmpty())
    }

    @Test
    fun `orphan spots for a deleted liner are dropped on decode, surviving spots kept`() {
        val keptSpot = WearSpot(id = "kept", linerId = "ln1", startMm = 10f, lengthMm = 20f)
        val orphanSpot = WearSpot(id = "orphan", linerId = "ln-deleted", startMm = 5f, lengthMm = 15f)

        val doc = ShaftDocCodec.ShaftDocV1(
            // Only "ln1" exists in the spec; "ln-deleted" does not.
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(spots = listOf(keptSpot, orphanSpot)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(1, decoded.wearRecord.spots.size)
        assertEquals("kept", decoded.wearRecord.spots.single().id)
    }

    @Test
    fun `orphan filtering with no liners at all drops every spot`() {
        val spot = WearSpot(id = "s1", linerId = "ln1", startMm = 0f, lengthMm = 10f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 500f), // no liners
            wearRecord = WearRecord(spots = listOf(spot)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertTrue(decoded.wearRecord.spots.isEmpty())
    }
}
