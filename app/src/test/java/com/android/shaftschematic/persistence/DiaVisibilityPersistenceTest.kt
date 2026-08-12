package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ø-callout visibility persistence: envelope round-trip, and back-compat decode of documents
 * saved before the flags existed. All three are additive + defaulted `true`, so no envelope
 * version bump — a pre-feature document decodes to today's behavior (every Ø printed).
 */
class DiaVisibilityPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `hidden body and liner flags round-trip through the envelope`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showDiaOnDrawing = false),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 200f, diaMm = 120f),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 420f, lengthMm = 60f, odMm = 150f, showDiaOnDrawing = false),
            ),
            showAutoBodyDia = false,
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertFalse(decoded.bodies.first { it.id == "b1" }.showDiaOnDrawing)
        assertTrue(decoded.bodies.first { it.id == "b2" }.showDiaOnDrawing)
        assertFalse(decoded.liners.single().showDiaOnDrawing)
        assertFalse(decoded.showAutoBodyDia)
    }

    @Test
    fun `a document saved before the flags existed decodes as fully visible`() {
        // Envelope JSON with no showDiaOnDrawing / showAutoBodyDia keys anywhere.
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 120.0 }
                ],
                "liners": [
                  { "id": "l1", "startMmPhysical": 500.0, "lengthMm": 200.0, "odMm": 150.0 }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec

        assertTrue(decoded.bodies.single().showDiaOnDrawing)
        assertTrue(decoded.liners.single().showDiaOnDrawing)
        assertTrue(decoded.showAutoBodyDia)
    }

    @Test
    fun `a legacy spec-only document decodes as fully visible`() {
        val legacySpecOnly = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0 } ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecOnly).spec

        assertTrue(decoded.bodies.single().showDiaOnDrawing)
        assertTrue(decoded.showAutoBodyDia)
    }
}
