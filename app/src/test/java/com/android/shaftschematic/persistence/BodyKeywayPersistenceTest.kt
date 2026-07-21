package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body keyway + keyways-180°-apart persistence: envelope round-trip, and back-compat
 * decode of documents saved before these fields existed (all additive + defaulted, so
 * no envelope version bump — same contract as couplerBoltSlots/wearRecord).
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
}
