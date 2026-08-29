package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Component-name label visibility persistence: envelope round-trip, and decode of documents
 * saved before the flags existed. All four flags are additive (no envelope version bump) and
 * TRI-STATE: `null` — the default, and what every pre-flag document decodes to — follows the
 * global Settings titles switch at draw time, so such a document prints exactly as its setting
 * says; an explicit `true`/`false` is an authored per-component override and must round-trip
 * verbatim (a stored `true` from a build where the flag was a plain Boolean decodes as the
 * explicit override it was).
 */
class LabelVisibilityPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `explicit flags round-trip and unset stays unset`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showLabelOnDrawing = false),
                Body(id = "b2", startFromAftMm = 1500f, lengthMm = 200f, diaMm = 120f, showLabelOnDrawing = true),
                Body(id = "b3", startFromAftMm = 1000f, lengthMm = 200f, diaMm = 120f),
            ),
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 400f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 120f, showLabelOnDrawing = false),
            ),
            threads = listOf(
                Threads(id = "th1", startFromAftMm = 1700f, lengthMm = 100f, majorDiaMm = 90f, showLabelOnDrawing = true),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 800f, lengthMm = 200f, odMm = 150f, showLabelOnDrawing = false),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertEquals(false, decoded.bodies.first { it.id == "b1" }.showLabelOnDrawing)
        assertEquals(true, decoded.bodies.first { it.id == "b2" }.showLabelOnDrawing)
        assertNull(decoded.bodies.first { it.id == "b3" }.showLabelOnDrawing)
        assertEquals(false, decoded.tapers.single().showLabelOnDrawing)
        assertEquals(true, decoded.threads.single().showLabelOnDrawing)
        assertEquals(false, decoded.liners.single().showLabelOnDrawing)
    }

    @Test
    fun `a document with no flags decodes every flag unset`() {
        // Envelope JSON with no showLabelOnDrawing key anywhere — such a document follows the
        // device's global titles switch, exactly as it printed before the flags existed.
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

        assertNull(decoded.bodies.single().showLabelOnDrawing)
        assertNull(decoded.tapers.single().showLabelOnDrawing)
        assertNull(decoded.threads.single().showLabelOnDrawing)
        assertNull(decoded.liners.single().showLabelOnDrawing)
    }

    @Test
    fun `a stored explicit true survives as the override it was`() {
        // A document saved by the build where the flag was a plain Boolean wrote "true"
        // explicitly; it decodes as the explicit always-show override.
        val stored = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0,
                            "showLabelOnDrawing": true } ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(stored).spec

        assertEquals(true, decoded.bodies.single().showLabelOnDrawing)
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

        assertNull(decoded.bodies.single().showLabelOnDrawing)
    }

    @Test
    fun `the flag is unset on every kind by default`() {
        assertNull(Body().showLabelOnDrawing)
        assertNull(Taper().showLabelOnDrawing)
        assertNull(Threads().showLabelOnDrawing)
        assertNull(Liner().showLabelOnDrawing)
    }
}
