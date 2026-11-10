package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.AutoBlend
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.mergeBodiesAround
import com.android.shaftschematic.model.splitBodiesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Angle-mode blend fields (`Body.blendAftAngleDeg`/`blendFwdAngleDeg`/`…RadiusMm`,
 * `AutoBlend.angleDeg`/`radiusMm`) are additive and defaulted (no envelope version bump): they
 * round-trip verbatim, a document saved before they existed decodes to Length mode with every other
 * blend field as stored, and a split or merge hands each face's entry to the fragment that keeps it.
 */
class BlendAngleCodecTest {

    private fun roundTrip(spec: ShaftSpec): ShaftSpec =
        ShaftDocCodec.decode(ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))).spec

    @Test
    fun `a body's angle entries round-trip per face`() {
        val body = Body(
            id = "b1", startFromAftMm = 0f, lengthMm = 800f, diaMm = 150f,
            blendAftMm = 12.7f, blendAftAngleDeg = 37.125f, blendAftRadiusMm = 9.525f,
            blendFwdMm = 25.4f,
        )
        val decoded = roundTrip(ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body))).bodies.single()
        assertEquals(body, decoded)
        assertNull(decoded.blendFwdAngleDeg)
    }

    @Test
    fun `an auto blend's angle entry round-trips`() {
        val blend = AutoBlend(
            anchorMm = 400f, end = LinerAuthoredReference.FWD, lengthMm = 25.4f,
            angleDeg = 45f, radiusMm = 3.175f,
        )
        val decoded = roundTrip(ShaftSpec(overallLengthMm = 1000f, autoBlends = listOf(blend))).autoBlends.single()
        assertEquals(blend, decoded)
    }

    @Test
    fun `a document saved before the angle existed decodes to Length mode`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 150.0,
                    "blendAftMm": 50.8, "blendFwdMm": 25.4, "blendProfile": "FILLET" }
                ],
                "autoBlends": [
                  { "anchorMm": 700.0, "end": "FWD", "lengthMm": 50.8, "seal": true }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec
        assertEquals(
            Body(
                id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f,
                blendAftMm = 50.8f, blendFwdMm = 25.4f, blendProfile = BlendProfile.FILLET,
            ),
            decoded.bodies.single(),
        )
        val auto = decoded.autoBlends.single()
        assertNull(auto.angleDeg)
        assertEquals(0f, auto.radiusMm, 0f)
        assertEquals(50.8f, auto.lengthMm, 0f)
    }

    private var counter = 0
    private fun nextId() = "gen-${++counter}"

    @Test
    fun `a split hands each face's angle entry to the fragment that keeps the face`() {
        val body = Body(
            id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f,
            blendAftMm = 40f, blendAftAngleDeg = 30f, blendAftRadiusMm = 3f,
            blendFwdMm = 25f, blendFwdAngleDeg = 45f, blendFwdRadiusMm = 6f,
        )
        val split = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))
            .splitBodiesAround(200f, 350f, ::nextId).spec.bodies
        val left = split.first { it.startFromAftMm == 0f }
        val right = split.first { it.startFromAftMm == 350f }
        assertEquals(30f, left.blendAftAngleDeg!!, 0f)
        assertEquals(3f, left.blendAftRadiusMm, 0f)
        assertNull("the cut face is square, in Length mode", left.blendFwdAngleDeg)
        assertEquals(0f, left.blendFwdRadiusMm, 0f)
        assertEquals(45f, right.blendFwdAngleDeg!!, 0f)
        assertEquals(6f, right.blendFwdRadiusMm, 0f)
        assertNull(right.blendAftAngleDeg)
    }

    @Test
    fun `a merge takes the AFT entry from A and the FWD entry from B`() {
        val a = Body(
            id = "a", startFromAftMm = 0f, lengthMm = 200f, diaMm = 60f,
            blendAftMm = 40f, blendAftAngleDeg = 30f, blendAftRadiusMm = 3f, blendFwdAngleDeg = 10f,
        )
        val b = Body(
            id = "b", startFromAftMm = 350f, lengthMm = 150f, diaMm = 60f,
            blendAftAngleDeg = 20f, blendFwdMm = 25f, blendFwdAngleDeg = 45f, blendFwdRadiusMm = 6f,
        )
        val merged = ShaftSpec(overallLengthMm = 500f, bodies = listOf(a, b))
            .mergeBodiesAround(200f, 350f, ::nextId).spec.bodies.single()
        assertEquals(30f, merged.blendAftAngleDeg!!, 0f)
        assertEquals(3f, merged.blendAftRadiusMm, 0f)
        assertEquals(45f, merged.blendFwdAngleDeg!!, 0f)
        assertEquals(6f, merged.blendFwdRadiusMm, 0f)
    }
}
