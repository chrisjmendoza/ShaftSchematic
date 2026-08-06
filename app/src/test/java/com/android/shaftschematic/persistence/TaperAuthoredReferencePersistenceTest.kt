package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Taper measuring-frame persistence.
 *
 * `Taper.authoredReference` records which end the Start was measured from. The Add dialog now
 * passes it through (a taper added "measure from FWD" reopens in that frame instead of falling
 * back to AFT with a converted Start). The field is additive + defaulted, so files written
 * before it existed decode as AFT and no envelope version bump is involved.
 *
 * Stored diameters are NOT repaired on the way in: a document written with a reversed pair
 * decodes exactly as it was saved (golden rule — nothing rewrites stored user data).
 */
class TaperAuthoredReferencePersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `a FWD-measured taper round-trips its authored reference`() {
        val spec = ShaftSpec(
            overallLengthMm = 2540f,
            tapers = listOf(
                Taper(
                    id = "t1",
                    startFromAftMm = 2032f,
                    lengthMm = 381f,
                    startDiaMm = 177.8f,
                    endDiaMm = 152.4f,
                    authoredReference = LinerAuthoredReference.FWD,
                )
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec)))

        val t = decoded.spec.tapers.single()
        assertEquals(LinerAuthoredReference.FWD, t.authoredReference)
        assertEquals(2032f, t.startFromAftMm, 0f)
        assertEquals("LET stays at the AFT face", 177.8f, t.startDiaMm, 0f)
        assertEquals("SET stays at the FWD face", 152.4f, t.endDiaMm, 0f)
    }

    @Test
    fun `an AFT-measured taper round-trips its authored reference`() {
        val spec = ShaftSpec(
            overallLengthMm = 2540f,
            tapers = listOf(
                Taper(
                    id = "t1", startFromAftMm = 127f, lengthMm = 381f,
                    startDiaMm = 152.4f, endDiaMm = 177.8f,
                    authoredReference = LinerAuthoredReference.AFT,
                )
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec)))

        assertEquals(LinerAuthoredReference.AFT, decoded.spec.tapers.single().authoredReference)
    }

    @Test
    fun `taper JSON saved before the field decodes as AFT`() {
        val legacySpecJson = """
            {
              "overallLengthMm": 2540.0,
              "tapers": [
                { "id": "t1", "startFromAftMm": 127.0, "lengthMm": 381.0,
                  "startDiaMm": 152.4, "endDiaMm": 177.8 }
              ]
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecJson)

        assertEquals(LinerAuthoredReference.AFT, decoded.spec.tapers.single().authoredReference)
    }

    @Test
    fun `a reversed stored pair decodes exactly as written`() {
        // A FWD-half taper saved by an older build with SET at its AFT face. Decoding must not
        // "correct" it: existing documents keep loading as stored.
        val legacySpecJson = """
            {
              "overallLengthMm": 2540.0,
              "tapers": [
                { "id": "t1", "startFromAftMm": 2032.0, "lengthMm": 381.0,
                  "startDiaMm": 152.4, "endDiaMm": 177.8 }
              ]
            }
        """.trimIndent()

        val t = ShaftDocCodec.decode(legacySpecJson).spec.tapers.single()

        assertEquals(152.4f, t.startDiaMm, 0f)
        assertEquals(177.8f, t.endDiaMm, 0f)
    }
}
