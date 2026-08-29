package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Per-component shade persistence (`shadeOnDrawing` on `Body`/`Taper`/`Liner`): envelope
 * round-trip, and decode of documents saved before the flag existed. Additive (no envelope
 * version bump) and TRI-STATE: `null` — the default, and what every pre-flag document decodes
 * to — follows the kind's Settings checkbox at draw time, so such a document prints exactly as
 * its settings say; an explicit `true`/`false` is an authored override and must round-trip
 * verbatim.
 *
 * Nullable from the first build on purpose: a non-null default serializes an
 * authored-looking stamp onto every component of every saved document, which is what forced
 * the name-label flag's key rename.
 */
class ShadeVisibilityPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `explicit flags round-trip and unset stays unset`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, shadeOnDrawing = true),
                Body(id = "b2", startFromAftMm = 1500f, lengthMm = 200f, diaMm = 120f, shadeOnDrawing = false),
                Body(id = "b3", startFromAftMm = 1000f, lengthMm = 200f, diaMm = 120f),
            ),
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 400f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 120f, shadeOnDrawing = true),
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 800f, lengthMm = 200f, odMm = 150f, shadeOnDrawing = false),
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertEquals(true, decoded.bodies.first { it.id == "b1" }.shadeOnDrawing)
        assertEquals(false, decoded.bodies.first { it.id == "b2" }.shadeOnDrawing)
        assertNull(decoded.bodies.first { it.id == "b3" }.shadeOnDrawing)
        assertEquals(true, decoded.tapers.single().shadeOnDrawing)
        assertEquals(false, decoded.liners.single().shadeOnDrawing)
    }

    @Test
    fun `a document with no flags decodes every flag unset`() {
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
                "liners": [
                  { "id": "l1", "startMmPhysical": 800.0, "lengthMm": 200.0, "odMm": 150.0 }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec

        assertNull(decoded.bodies.single().shadeOnDrawing)
        assertNull(decoded.tapers.single().shadeOnDrawing)
        assertNull(decoded.liners.single().shadeOnDrawing)
    }

    @Test
    fun `a stored explicit value round-trips under the current key`() {
        val stored = """
            {
              "overallLengthMm": 800.0,
              "bodies": [ { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 800.0, "diaMm": 100.0,
                            "shadeOnDrawing": true } ]
            }
        """.trimIndent()

        assertEquals(true, ShaftDocCodec.decode(stored).spec.bodies.single().shadeOnDrawing)
    }

    @Test
    fun `the flag is unset on every kind by default`() {
        assertNull(Body().shadeOnDrawing)
        assertNull(Taper().shadeOnDrawing)
        assertNull(Liner().shadeOnDrawing)
    }
}
