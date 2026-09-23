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
 * Component-name label visibility persistence (`showNameOnDrawing`): envelope round-trip, and
 * decode of documents saved before the flag existed. All four flags are additive (no envelope
 * version bump) and TRI-STATE: `null` — the default, and what every pre-flag document decodes
 * to — follows the global Settings titles switch at draw time, so such a document prints
 * exactly as its setting says; an explicit `true`/`false` is an authored per-component
 * override and must round-trip verbatim. Load-bearing: the RETIRED `showLabelOnDrawing` key
 * is ignored at decode — the first build blanket-serialized `true` under it on every
 * component, so honoring it would resurrect a default as an authored choice.
 */
class LabelVisibilityPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `explicit flags round-trip and unset stays unset`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showNameOnDrawing = false),
                Body(id = "b2", startFromAftMm = 1500f, lengthMm = 200f, diaMm = 120f, showNameOnDrawing = true),
                Body(id = "b3", startFromAftMm = 1000f, lengthMm = 200f, diaMm = 120f),
            ),
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 400f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 120f, showNameOnDrawing = false),
            ),
            threads = listOf(
                Threads(id = "th1", startFromAftMm = 1700f, lengthMm = 100f, majorDiaMm = 90f, showNameOnDrawing = true),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 800f, lengthMm = 200f, odMm = 150f, showNameOnDrawing = false),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertEquals(false, decoded.bodies.first { it.id == "b1" }.showNameOnDrawing)
        assertEquals(true, decoded.bodies.first { it.id == "b2" }.showNameOnDrawing)
        assertNull(decoded.bodies.first { it.id == "b3" }.showNameOnDrawing)
        assertEquals(false, decoded.tapers.single().showNameOnDrawing)
        assertEquals(true, decoded.threads.single().showNameOnDrawing)
        assertEquals(false, decoded.liners.single().showNameOnDrawing)
    }

    @Test
    fun `a document with no flags decodes every flag unset`() {
        // Envelope JSON with no showNameOnDrawing key anywhere — such a document follows the
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

        assertNull(decoded.bodies.single().showNameOnDrawing)
        assertNull(decoded.tapers.single().showNameOnDrawing)
        assertNull(decoded.threads.single().showNameOnDrawing)
        assertNull(decoded.liners.single().showNameOnDrawing)
    }

    @Test
    fun `a stored explicit value round-trips under the current key`() {
        val stored = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0,
                            "showNameOnDrawing": true } ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(stored).spec

        assertEquals(true, decoded.bodies.single().showNameOnDrawing)
    }

    @Test
    fun `the retired showLabelOnDrawing key is ignored at decode`() {
        // The first build of the feature blanket-serialized `showLabelOnDrawing: true` onto
        // every component of every saved document (a plain default, not an authored choice) —
        // honoring it as an explicit override made one checked toggle appear to turn every
        // label on (on-device report). The fresh key is what guarantees a stored value is
        // always an authored choice; the old key must decode as unset, never as shown.
        val stored = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0,
                            "showLabelOnDrawing": true } ],
              "tapers": [ { "id": "t1", "startFromAftMm": 100.0, "lengthMm": 300.0,
                            "startDiaMm": 100.0, "endDiaMm": 120.0, "showLabelOnDrawing": true } ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(stored).spec

        assertNull(decoded.bodies.single().showNameOnDrawing)
        assertNull(decoded.tapers.single().showNameOnDrawing)
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

        assertNull(decoded.bodies.single().showNameOnDrawing)
    }

    @Test
    fun `the flag is unset on every kind by default`() {
        assertNull(Body().showNameOnDrawing)
        assertNull(Taper().showNameOnDrawing)
        assertNull(Threads().showNameOnDrawing)
        assertNull(Liner().showNameOnDrawing)
    }
}
