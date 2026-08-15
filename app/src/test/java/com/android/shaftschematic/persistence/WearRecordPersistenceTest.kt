package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.model.WornSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `dyePenResult round trips through the envelope without a version bump`() {
        // Additive, defaulted field on WearRecord — no envelope version bump; the selected
        // outcome must come back exactly.
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(dyePenResult = DyePenResult.FAIL),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(DyePenResult.FAIL, decoded.wearRecord.dyePenResult)
    }

    @Test
    fun `wear record without dyePenResult decodes to no selection`() {
        // A file saved before the field existed keeps both printed checkboxes blank.
        val doc = ShaftDocCodec.ShaftDocV1(spec = linerSpec("ln1"), wearRecord = WearRecord())
        val raw = ShaftDocCodec.encodeV1(doc).replace(Regex(""""dyePenResult"\s*:\s*[^,}]+,?"""), "")

        val decoded = ShaftDocCodec.decode(raw)

        assertNull(decoded.wearRecord.dyePenResult)
    }

    @Test
    fun `authoredReference round trips through the envelope without a version bump`() {
        // Post-review spec: WearSpot gains an additive, defaulted
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

    // ── Pits (the "X" markers) ────────────────────────────────────────────────

    @Test
    fun `envelope round trip preserves wear pits`() {
        val pit = WearPit(
            id = "pit-1", componentId = "ln1", axialMm = 30f, acrossFrac = 0.25f, size = PitSize.LARGE,
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(pits = listOf(pit)),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected pits key in JSON", raw.contains("\"pits\""))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        val decodedPit = decoded.wearRecord.pits.single()
        assertEquals("pit-1", decodedPit.id)
        assertEquals("ln1", decodedPit.componentId)
        assertEquals(30f, decodedPit.axialMm, 0.001f)
        assertEquals(0.25f, decodedPit.acrossFrac, 0.001f)
        assertEquals(PitSize.LARGE, decodedPit.size)
    }

    @Test
    fun `a wear_record json without pits decodes to an empty pit list`() {
        // Simulates a file written before pits existed: "wear_record" has only "spots".
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": { "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ] }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(1, decoded.wearRecord.spots.size)
        assertTrue(decoded.wearRecord.pits.isEmpty())
    }

    @Test
    fun `pits on non-liner or missing components survive decode (unlike orphan spots)`() {
        // Pits attach to bodies/tapers/auto-bodies whose ids the codec can't know, so — unlike
        // wear spots — they are NOT pruned at decode. Orphan handling is at the render layer
        // (same posture as runout readings). Here the pit's componentId matches no liner, yet
        // it must be preserved while the orphan spot beside it is dropped.
        val keptPit = WearPit(id = "p1", componentId = "auto_body_0.000_100.000", axialMm = 10f)
        val orphanSpot = WearSpot(id = "orphan", linerId = "ln-deleted", startMm = 5f, lengthMm = 15f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(spots = listOf(orphanSpot), pits = listOf(keptPit)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertTrue("orphan spot should be dropped", decoded.wearRecord.spots.isEmpty())
        assertEquals("pit should survive", 1, decoded.wearRecord.pits.size)
        assertEquals("auto_body_0.000_100.000", decoded.wearRecord.pits.single().componentId)
    }

    // ── Measured-Ø readings ───────────────────────────────────────────────────

    @Test
    fun `envelope round trip preserves dia readings verbatim`() {
        val reading = WearDiaReading(
            id = "dia-1", componentId = "ln1", axialMm = 42.5f, diaMm = 247.777f,
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(diaReadings = listOf(reading)),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected diaReadings key in JSON", raw.contains("\"diaReadings\""))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        val d = decoded.wearRecord.diaReadings.single()
        assertEquals("dia-1", d.id)
        assertEquals("ln1", d.componentId)
        assertEquals(42.5f, d.axialMm, 0.0001f)
        // Golden rule: the typed measurement round-trips exactly, no rounding.
        assertEquals(247.777f, d.diaMm, 0f)
    }

    @Test
    fun `a wear_record json without diaReadings decodes to an empty list`() {
        // Simulates a file written before dia readings existed.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": { "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ] }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(1, decoded.wearRecord.spots.size)
        assertTrue(decoded.wearRecord.diaReadings.isEmpty())
    }

    @Test
    fun `dia readings on non-liner or missing components survive decode`() {
        // Like pits (and unlike orphan spots), readings key on resolved component ids the
        // codec can't know — orphan handling is at the render layer, never decode.
        val kept = WearDiaReading(id = "d1", componentId = "auto_body_0.000_100.000", axialMm = 10f, diaMm = 99f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(diaReadings = listOf(kept)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(1, decoded.wearRecord.diaReadings.size)
        assertEquals("auto_body_0.000_100.000", decoded.wearRecord.diaReadings.single().componentId)
    }

    // ── Worn sections (consolidated runout/wear sheet) ────────────────────────

    @Test
    fun `envelope round trip preserves worn sections verbatim`() {
        val section = WornSection(
            id = "worn-1",
            startFromAftMm = 320f,
            lengthMm = 85f,
            diaMm = listOf(162.9156f, 162.941f, 162.9156f),
            authoredReference = UndercutReference.FWD_SET,
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(wornSections = listOf(section)),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected wornSections key in JSON", raw.contains("\"wornSections\""))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        val w = decoded.wearRecord.wornSections.single()
        assertEquals("worn-1", w.id)
        assertEquals(320f, w.startFromAftMm, 0.0001f)
        assertEquals(85f, w.lengthMm, 0.0001f)
        assertEquals(UndercutReference.FWD_SET, w.authoredReference)
        // Golden rule: every typed measurement round-trips exactly, order preserved.
        assertEquals(listOf(162.9156f, 162.941f, 162.9156f), w.diaMm)
    }

    @Test
    fun `a wear_record json without wornSections decodes to an empty list`() {
        // Simulates a file written before worn sections existed.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": { "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ] }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(1, decoded.wearRecord.spots.size)
        assertTrue(decoded.wearRecord.wornSections.isEmpty())
    }

    // ── Trace depth exaggeration (per-job override of the Settings default) ───

    @Test
    fun `envelope round trip preserves a pinned trace depth`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(traceDepthFrac = 0.12f),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected traceDepthFrac key in JSON", raw.contains("\"traceDepthFrac\""))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(0.12f, decoded.wearRecord.traceDepthFrac!!, 1e-6f)
    }

    @Test
    fun `a wear_record json without traceDepthFrac decodes to null (follow the default)`() {
        // Simulates a file written before the slider existed: null is what makes such a
        // document track the Settings → Drawing default instead of pinning the old cap.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": { "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ] }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(1, decoded.wearRecord.spots.size)
        assertNull(decoded.wearRecord.traceDepthFrac)
    }

    // ── Strip election + shaft-profile toggle (per-job wear sheet shape) ──────

    @Test
    fun `envelope round trip preserves the strip election and the profile toggle`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(
                // Taper/body ids ride along exactly like a pit's: never pruned at decode.
                stripComponentIds = listOf("ln1", "auto_body_0.000_100.000", "taper-9"),
                showShaftProfile = false,
            ),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected stripComponentIds key in JSON", raw.contains("\"stripComponentIds\""))
        assertTrue("expected showShaftProfile key in JSON", raw.contains("\"showShaftProfile\""))

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(
            listOf("ln1", "auto_body_0.000_100.000", "taper-9"),
            decoded.wearRecord.stripComponentIds,
        )
        assertEquals(false, decoded.wearRecord.showShaftProfile)
    }

    @Test
    fun `an empty strip election round trips as empty, not as the default`() {
        // Empty means "no detail strips"; null means "every drawable liner" — the two must
        // never collapse into each other across a save/load.
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = linerSpec("ln1"),
            wearRecord = WearRecord(stripComponentIds = emptyList()),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(emptyList<String>(), decoded.wearRecord.stripComponentIds)
    }

    @Test
    fun `a wear_record json without the strip fields decodes to the default sheet`() {
        // A file written before the election existed: null election (every drawable liner) and
        // the whole-shaft profile drawn — exactly the historical sheet.
        val raw = """
            {
              "version": 1, "preferred_unit": "INCHES", "unit_locked": true,
              "job_number": "", "customer": "", "vessel": "", "shaft_position": "OTHER", "notes": "",
              "spec": {
                "overallLengthMm": 500.0,
                "liners": [ { "id": "ln1", "startMmPhysical": 0.0, "lengthMm": 200.0, "odMm": 50.0, "endMmPhysical": 200.0 } ]
              },
              "wear_record": { "spots": [ { "id": "spot-1", "linerId": "ln1", "startMm": 25.0, "lengthMm": 40.0 } ] }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertNull(decoded.wearRecord.stripComponentIds)
        assertTrue(decoded.wearRecord.showShaftProfile)
    }

    @Test
    fun `worn sections are never pruned at decode`() {
        // Shaft-space, no component key — even a span past the current OAL survives decode
        // (render-layer clamp only; the stored record is never mutated).
        val overrun = WornSection(id = "w1", startFromAftMm = 900f, lengthMm = 300f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 500f), // no liners, span past OAL
            wearRecord = WearRecord(wornSections = listOf(overrun)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(1, decoded.wearRecord.wornSections.size)
        assertEquals(900f, decoded.wearRecord.wornSections.single().startFromAftMm, 0.0001f)
    }
}
