package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence tests for the undercut drawing record (Phase 1 of
 * `docs/UndercutDrawing_PLAN.md` §7): envelope round-trip, legacy/older-file defaults, and
 * the no-orphan-pruning rule — undercuts have no component key, so nothing is dropped at
 * decode (unlike wear spots).
 */
class UndercutRecordPersistenceTest {

    private fun spec(overallLengthMm: Float = 500f): ShaftSpec =
        ShaftSpec(overallLengthMm = overallLengthMm)

    @Test
    fun `envelope round trip preserves undercuts verbatim`() {
        val undercut = Undercut(
            id = "u1",
            startFromAftMm = 120f,
            lengthMm = 25.4f,
            diaMm = 247.777f,
            authoredReference = UndercutReference.AFT_SET,
            note = "weld undercut",
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = spec(),
            undercutRecord = UndercutRecord(undercuts = listOf(undercut)),
        )

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected undercut_record key in JSON", raw.contains("\"undercut_record\""))

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(1, decoded.undercutRecord.undercuts.size)
        val d = decoded.undercutRecord.undercuts.single()
        assertEquals("u1", d.id)
        assertEquals(120f, d.startFromAftMm, 0.001f)
        assertEquals(25.4f, d.lengthMm, 0.001f)
        // Golden rule: the typed measurement round-trips exactly, no rounding.
        assertEquals(247.777f, d.diaMm, 0f)
        assertEquals(UndercutReference.AFT_SET, d.authoredReference)
        assertEquals("weld undercut", d.note)
    }

    @Test
    fun `envelope round trip preserves diaMm of exactly zero (placed but unmeasured)`() {
        val undercut = Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 5f, diaMm = 0f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = spec(),
            undercutRecord = UndercutRecord(undercuts = listOf(undercut)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(0f, decoded.undercutRecord.undercuts.single().diaMm, 0f)
    }

    @Test
    fun `FWD_SET reference round trips through the envelope`() {
        val undercut = Undercut(
            id = "u1", startFromAftMm = 10f, lengthMm = 5f,
            authoredReference = UndercutReference.FWD_SET,
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = spec(),
            undercutRecord = UndercutRecord(undercuts = listOf(undercut)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(UndercutReference.FWD_SET, decoded.undercutRecord.undercuts.single().authoredReference)
    }

    @Test
    fun `envelope without undercut_record field decodes to empty record`() {
        // Simulates a file written before this field existed: no "undercut_record" key at all.
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
        assertTrue(decoded.undercutRecord.undercuts.isEmpty())
    }

    @Test
    fun `legacy bare-spec file decodes to empty undercut record`() {
        val legacy = """{ "overallLengthMm": 500.0 }"""

        val decoded = ShaftDocCodec.decode(legacy)

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertTrue(decoded.undercutRecord.undercuts.isEmpty())
    }

    @Test
    fun `undercuts survive decode with no pruning regardless of shaft spec content`() {
        // Undercuts have no component key at all — unlike wear spots (keyed by linerId and
        // pruned as orphans at decode), nothing here can be "orphaned", so every undercut
        // must survive decode no matter what the spec contains (even an empty spec).
        val u1 = Undercut(id = "u1", startFromAftMm = 10f, lengthMm = 20f)
        val u2 = Undercut(id = "u2", startFromAftMm = 300f, lengthMm = 15f)
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 500f), // no bodies/tapers/liners at all
            undercutRecord = UndercutRecord(undercuts = listOf(u1, u2)),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(2, decoded.undercutRecord.undercuts.size)
        assertEquals(listOf("u1", "u2"), decoded.undercutRecord.undercuts.map { it.id })
    }
}
