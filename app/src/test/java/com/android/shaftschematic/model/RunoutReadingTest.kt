package com.android.shaftschematic.model

import com.android.shaftschematic.doc.ShaftDocCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RunoutReading]/[RunoutReadings] and their round-trip through [ShaftDocCodec].
 * See `docs/RunoutBubbleEditor_PLAN.md`.
 */
class RunoutReadingTest {

    // ── RunoutReadings helpers ──────────────────────────────────────────────────

    @Test fun withReading_inserts() {
        val r = RunoutReadings().withReading(RunoutReading("body1", 0, 0.05f, 3))
        assertEquals(1, r.readings.size)
        assertEquals(0.05f, r.find("body1", 0)!!.valueMm)
        assertEquals(3, r.find("body1", 0)!!.highSpotHalfHours)
    }

    @Test fun withReading_replaces_same_key() {
        val r = RunoutReadings()
            .withReading(RunoutReading("body1", 0, 0.05f, 3))
            .withReading(RunoutReading("body1", 0, 0.10f, null))
        assertEquals(1, r.readings.size)
        assertEquals(0.10f, r.find("body1", 0)!!.valueMm)
        assertNull(r.find("body1", 0)!!.highSpotHalfHours)
    }

    @Test fun withReading_distinct_keys_coexist() {
        val r = RunoutReadings()
            .withReading(RunoutReading("body1", 0, 0.05f, null))
            .withReading(RunoutReading("body1", 1, 0.06f, null))
            .withReading(RunoutReading("taper1", 0, 0.07f, null))
        assertEquals(3, r.readings.size)
    }

    @Test fun withReading_empty_is_not_stored() {
        val r = RunoutReadings().withReading(RunoutReading("body1", 0, null, null))
        assertTrue(r.readings.isEmpty())
    }

    @Test fun withReading_empty_removes_existing() {
        val r = RunoutReadings()
            .withReading(RunoutReading("body1", 0, 0.05f, 3))
            .withReading(RunoutReading("body1", 0, null, null))
        assertNull(r.find("body1", 0))
        assertTrue(r.readings.isEmpty())
    }

    @Test fun without_removes() {
        val r = RunoutReadings()
            .withReading(RunoutReading("body1", 0, 0.05f, 3))
            .without("body1", 0)
        assertNull(r.find("body1", 0))
    }

    @Test fun isEmpty_semantics() {
        assertTrue(RunoutReading("x", 0, null, null).isEmpty)
        assertTrue(!RunoutReading("x", 0, 0.01f, null).isEmpty)
        assertTrue(!RunoutReading("x", 0, null, 5).isEmpty)
    }

    // ── Codec round-trip ────────────────────────────────────────────────────────

    @Test fun envelope_roundtrips_readings() {
        val readings = RunoutReadings(
            listOf(
                RunoutReading("body1", 0, 0.0508f, 3),
                RunoutReading("taper1", 1, null, 18),
                RunoutReading("liner1", 0, 0.12f, null),
            )
        )
        val doc = ShaftDocCodec.ShaftDocV1(spec = ShaftSpec(), runoutReadings = readings)
        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))
        assertEquals(readings, decoded.runoutReadings)
    }

    @Test fun readings_default_empty_when_absent() {
        // A v1 envelope JSON with no runout_readings key must decode to an empty set.
        val doc = ShaftDocCodec.ShaftDocV1(spec = ShaftSpec())
        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))
        assertTrue(decoded.runoutReadings.readings.isEmpty())
    }

    @Test fun legacy_spec_decodes_to_empty_readings() {
        // Legacy files were the bare spec JSON — no envelope, no readings.
        val legacy = kotlinx.serialization.json.Json { encodeDefaults = true }
            .encodeToString(ShaftSpec.serializer(), ShaftSpec())
        val decoded = ShaftDocCodec.decode(legacy)
        assertTrue(decoded.runoutReadings.readings.isEmpty())
    }

    @Test fun orphan_readings_survive_decode() {
        // Readings whose componentId isn't in the spec pass through decode untouched — orphan
        // pruning is a render-layer concern (see model/RunoutReading.kt).
        val readings = RunoutReadings(listOf(RunoutReading("ghost", 9, 0.01f, 1)))
        val doc = ShaftDocCodec.ShaftDocV1(spec = ShaftSpec(), runoutReadings = readings)
        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))
        assertEquals(readings, decoded.runoutReadings)
    }
}
