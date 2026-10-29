package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Body.showDimensionRail` persistence. Additive (no envelope version bump) and TRI-STATE:
 * `null` — the default, and what every document saved before the flag decodes to — follows the
 * body's position at draw time; an explicit `true`/`false` is an authored override and must
 * round-trip verbatim.
 */
class BodyDimensionRailPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `explicit values round-trip and unset stays unset`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showDimensionRail = false),
                Body(id = "b2", startFromAftMm = 800f, lengthMm = 200f, diaMm = 120f, showDimensionRail = true),
                Body(id = "b3", startFromAftMm = 1500f, lengthMm = 500f, diaMm = 120f),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertEquals(false, decoded.bodies.first { it.id == "b1" }.showDimensionRail)
        assertEquals(true, decoded.bodies.first { it.id == "b2" }.showDimensionRail)
        assertNull(decoded.bodies.first { it.id == "b3" }.showDimensionRail)
    }

    @Test
    fun `a document saved before the flag decodes it unset`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 2000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 1600.0, "lengthMm": 400.0, "diaMm": 120.0 }
                ]
              }
            }
        """.trimIndent()

        assertNull(ShaftDocCodec.decode(legacy).spec.bodies.single().showDimensionRail)
    }

    @Test
    fun `the model default is unset`() {
        assertNull(Body().showDimensionRail)
    }
}
