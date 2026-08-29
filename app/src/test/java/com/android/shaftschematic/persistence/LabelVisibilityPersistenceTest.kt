package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Component-name label visibility persistence: envelope round-trip, and decode of documents
 * saved before the flags existed. All four flags are additive (no envelope version bump) and
 * default TRUE, so a document with no flags prints every label exactly as it did before the
 * per-component switches existed — the mirror of the Ø-callout posture, where bodies are opt-in.
 */
class LabelVisibilityPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `all four flags round-trip through the envelope`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showLabelOnDrawing = false),
                Body(id = "b2", startFromAftMm = 1500f, lengthMm = 200f, diaMm = 120f, showLabelOnDrawing = true),
            ),
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 400f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 120f, showLabelOnDrawing = false),
            ),
            threads = listOf(
                Threads(id = "th1", startFromAftMm = 1700f, lengthMm = 100f, majorDiaMm = 90f, showLabelOnDrawing = false),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 800f, lengthMm = 200f, odMm = 150f, showLabelOnDrawing = false),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertFalse(decoded.bodies.first { it.id == "b1" }.showLabelOnDrawing)
        assertTrue(decoded.bodies.first { it.id == "b2" }.showLabelOnDrawing)
        assertFalse(decoded.tapers.single().showLabelOnDrawing)
        assertFalse(decoded.threads.single().showLabelOnDrawing)
        assertFalse(decoded.liners.single().showLabelOnDrawing)
    }

    @Test
    fun `a document with no flags decodes every label visible`() {
        // Envelope JSON with no showLabelOnDrawing key anywhere.
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 2000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 400.0, "diaMm": 120.0 }
                ],
                "tapers": [
                  { "id": "t1", "startFromAftMm": 400.0, "lengthMm": 300.0, "startDiaMm": 100.0, "endDiaMm": 120.0 }
                ],
                "threads": [
                  { "id": "th1", "startFromAftMm": 1700.0, "lengthMm": 100.0, "majorDiaMm": 90.0 }
                ],
                "liners": [
                  { "id": "l1", "startMmPhysical": 800.0, "lengthMm": 200.0, "odMm": 150.0 }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec

        assertTrue(decoded.bodies.single().showLabelOnDrawing)
        assertTrue(decoded.tapers.single().showLabelOnDrawing)
        assertTrue(decoded.threads.single().showLabelOnDrawing)
        assertTrue(decoded.liners.single().showLabelOnDrawing)
    }

    @Test
    fun `a legacy spec-only document follows the same default`() {
        val legacySpecOnly = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0 } ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecOnly).spec

        assertTrue(decoded.bodies.single().showLabelOnDrawing)
    }

    @Test
    fun `the defaults are visible on every kind`() {
        assertTrue(Body().showLabelOnDrawing)
        assertTrue(Taper().showLabelOnDrawing)
        assertTrue(Threads().showLabelOnDrawing)
        assertTrue(Liner().showLabelOnDrawing)
    }
}
