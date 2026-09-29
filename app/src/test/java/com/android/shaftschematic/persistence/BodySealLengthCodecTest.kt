package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.AutoBlend
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seal-area length persistence (`Body.blendAftSealLenMm`/`blendFwdSealLenMm`,
 * `AutoBlend.sealLenMm`). The fields are additive and defaulted (no envelope version bump):
 * a typed length round-trips verbatim, and a document saved before the fields existed decodes
 * to `0` — "follow the blend length" — so its seal areas keep their grooves.
 */
class BodySealLengthCodecTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    private fun roundTrip(spec: ShaftSpec): ShaftSpec =
        ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

    @Test
    fun `a body's seal length round-trips verbatim`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(
                    id = "b1", startFromAftMm = 0f, lengthMm = 800f, diaMm = 150f,
                    blendFwdMm = 25.4f, blendFwdSeal = true, blendFwdSealLenMm = 152.4f,
                ),
            ),
        )

        val body = roundTrip(spec).bodies.single()

        assertTrue(body.blendFwdSeal)
        assertEquals(152.4f, body.blendFwdSealLenMm, 0f)
        assertEquals(25.4f, body.blendFwdMm, 0f)
        assertEquals(0f, body.blendAftSealLenMm, 0f)
        assertEquals(spec.bodies.single(), body)
    }

    @Test
    fun `an auto blend's seal length round-trips verbatim`() {
        val blend = AutoBlend(
            anchorMm = 400f, end = LinerAuthoredReference.FWD, lengthMm = 25.4f,
            profile = BlendProfile.FILLET, seal = true, sealLenMm = 101.6f,
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, autoBlends = listOf(blend))

        val decoded = roundTrip(spec).autoBlends.single()

        assertEquals(101.6f, decoded.sealLenMm, 0f)
        assertEquals(blend, decoded)
    }

    @Test
    fun `a document saved before the seal length existed decodes to zero on both`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 150.0,
                    "blendAftMm": 50.8, "blendAftSeal": true }
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

        assertTrue(body.blendAftSeal)
        assertEquals(0f, body.blendAftSealLenMm, 0f)
        assertEquals(0f, body.blendFwdSealLenMm, 0f)
        assertTrue(auto.seal)
        assertEquals(0f, auto.sealLenMm, 0f)
    }
}
