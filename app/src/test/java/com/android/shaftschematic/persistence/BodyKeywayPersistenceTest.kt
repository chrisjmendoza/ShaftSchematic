package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.keywayClocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body keyway + keyway-clocking (180° / 90° CW / 90° CCW) persistence: envelope round-trip,
 * and back-compat decode of documents saved before these fields existed (all additive +
 * defaulted, so no envelope version bump — same contract as couplerBoltSlots/wearRecord).
 */
class BodyKeywayPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `body keyway and 180 flag round-trip through the envelope`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(
                    startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f,
                    keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 200f,
                    keywayOffsetFromEndMm = 25f,
                    keywayEnd = LinerAuthoredReference.FWD,
                    keywaySpooned = false,
                )
            ),
            keyways180Apart = true,
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec)))

        val b = decoded.spec.bodies.single()
        assertTrue(b.hasKeyway)
        assertEquals(30f, b.keywayWidthMm, 0f)
        assertEquals(15f, b.keywayDepthMm, 0f)
        assertEquals(200f, b.keywayLengthMm, 0f)
        assertEquals(25f, b.keywayOffsetFromEndMm, 0f)
        assertEquals(LinerAuthoredReference.FWD, b.keywayEnd)
        assertTrue(decoded.spec.keyways180Apart)
    }

    @Test
    fun `pre-keyway body JSON decodes with no keyway and flag false`() {
        // A body serialized before the keyway fields existed.
        val legacySpecJson = """
            {
              "overallLengthMm": 500.0,
              "bodies": [
                { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 500.0, "diaMm": 100.0 }
              ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecJson)

        val b = decoded.spec.bodies.single()
        assertFalse(b.hasKeyway)
        assertEquals(0f, b.keywayOffsetFromEndMm, 0f)
        assertEquals(LinerAuthoredReference.AFT, b.keywayEnd)
        assertFalse(decoded.spec.keyways180Apart)
    }

    @Test
    fun `90 apart flag and direction round-trip through the envelope`() {
        val ccw = ShaftSpec(overallLengthMm = 1000f, keyways90Apart = true, keyways90Cw = false)
        val decodedCcw = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(ccw)))
        assertTrue(decodedCcw.spec.keyways90Apart)
        assertFalse(decodedCcw.spec.keyways90Cw)
        assertFalse(decodedCcw.spec.keyways180Apart)
        assertEquals(KeywayClocking.DEG_90_CCW, decodedCcw.spec.keywayClocking())

        val cw = ShaftSpec(overallLengthMm = 1000f, keyways90Apart = true, keyways90Cw = true)
        val decodedCw = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(cw)))
        assertTrue(decodedCw.spec.keyways90Apart)
        assertTrue(decodedCw.spec.keyways90Cw)
        assertEquals(KeywayClocking.DEG_90_CW, decodedCw.spec.keywayClocking())
    }

    @Test
    fun `pre-90-clocking JSON decodes to the defaults`() {
        // A spec serialized before keyways90Apart/keyways90Cw existed: additive + defaulted,
        // so the note is off and the direction falls back to clockwise.
        val legacySpecJson = """
            {
              "overallLengthMm": 500.0,
              "keyways180Apart": true,
              "bodies": [
                { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 500.0, "diaMm": 100.0 }
              ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecJson)

        assertTrue(decoded.spec.keyways180Apart)
        assertFalse(decoded.spec.keyways90Apart)
        assertTrue(decoded.spec.keyways90Cw)
        assertEquals(KeywayClocking.DEG_180, decoded.spec.keywayClocking())
    }
}
