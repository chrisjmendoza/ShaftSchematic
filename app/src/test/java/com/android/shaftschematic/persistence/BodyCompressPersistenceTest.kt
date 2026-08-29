package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Body.compressOnDrawing` persistence. The flag is additive (no envelope version bump) and
 * its SERIALIZATION default is `true`, deliberately opposite the default the authoring path
 * creates a body with: a document saved before the flag existed must keep compressing exactly
 * as it does today, because re-pinning a long saved shaft at true width could leave it
 * unrenderable. Turning a body off is therefore always something a person did.
 */
class BodyCompressPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `the flag round-trips through the envelope in both states`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, compressOnDrawing = false),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 200f, diaMm = 120f, compressOnDrawing = true),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertFalse(decoded.bodies.first { it.id == "b1" }.compressOnDrawing)
        assertTrue(decoded.bodies.first { it.id == "b2" }.compressOnDrawing)
    }

    @Test
    fun `a document saved before the flag existed keeps compressing`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 6000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 6000.0, "diaMm": 120.0 }
                ]
              }
            }
        """.trimIndent()

        assertTrue(ShaftDocCodec.decode(legacy).spec.bodies.single().compressOnDrawing)
    }

    @Test
    fun `a legacy spec-only document follows the same default`() {
        val legacySpecOnly = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0 } ]
            }
        """.trimIndent()

        assertTrue(ShaftDocCodec.decode(legacySpecOnly).spec.bodies.single().compressOnDrawing)
    }

    @Test
    fun `the model default is the decode default`() {
        assertTrue(Body().compressOnDrawing)
    }
}
