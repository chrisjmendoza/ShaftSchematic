package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.AutoBlend
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "At a liner" target (`Body.blendAftToLinerOd`/`blendFwdToLinerOd`, `AutoBlend.toLinerOd`).
 * The fields are additive and defaulted (no envelope version bump): a set flag round-trips, and a
 * document saved before the fields existed decodes them `false` — the derived half-way seat it
 * always drew — with every other blend field exactly as stored.
 */
class BlendLinerTargetCodecTest {

    private fun roundTrip(spec: ShaftSpec): ShaftSpec =
        ShaftDocCodec.decode(ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))).spec

    @Test
    fun `a body's liner targets round-trip per face`() {
        val body = Body(
            id = "b1", startFromAftMm = 0f, lengthMm = 800f, diaMm = 150f,
            blendAftMm = 12.7f, blendFwdMm = 25.4f, blendFwdToLinerOd = true,
        )
        val decoded = roundTrip(ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body))).bodies.single()

        assertFalse(decoded.blendAftToLinerOd)
        assertTrue(decoded.blendFwdToLinerOd)
        assertEquals(body, decoded)
    }

    @Test
    fun `an auto blend's liner target round-trips`() {
        val blend = AutoBlend(
            anchorMm = 400f, end = LinerAuthoredReference.AFT, lengthMm = 25.4f,
            profile = BlendProfile.EASED_CONE, toLinerOd = true,
        )
        val decoded = roundTrip(ShaftSpec(overallLengthMm = 1000f, autoBlends = listOf(blend))).autoBlends.single()

        assertEquals(blend, decoded)
    }

    @Test
    fun `a document saved before the liner target existed decodes to the seat on both`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 150.0,
                    "blendAftMm": 50.8, "blendFwdMm": 25.4, "blendFwdSeal": true }
                ],
                "autoBlends": [
                  { "anchorMm": 700.0, "end": "FWD", "lengthMm": 50.8, "seal": true }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec
        val body = decoded.bodies.single()
        val auto = decoded.autoBlends.single()

        assertEquals(
            Body(
                id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f,
                blendAftMm = 50.8f, blendFwdMm = 25.4f, blendFwdSeal = true,
            ),
            body,
        )
        assertFalse(body.blendAftToLinerOd)
        assertFalse(body.blendFwdToLinerOd)
        assertEquals(
            AutoBlend(anchorMm = 700f, end = LinerAuthoredReference.FWD, lengthMm = 50.8f, seal = true),
            auto,
        )
        assertFalse(auto.toLinerOd)
    }

    /** The codec ignores unknown keys, so a document from a later build still opens here. */
    @Test
    fun `an unknown blend key from a later build is ignored`() {
        val later = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 150.0,
                    "blendFwdMm": 25.4, "blendFwdToLinerOd": true, "blendFwdSomethingNew": 3 }
                ],
                "autoBlends": [
                  { "anchorMm": 700.0, "end": "FWD", "lengthMm": 50.8, "toLinerOd": true, "later": "x" }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(later).spec
        assertTrue(decoded.bodies.single().blendFwdToLinerOd)
        assertTrue(decoded.autoBlends.single().toLinerOd)
    }
}
