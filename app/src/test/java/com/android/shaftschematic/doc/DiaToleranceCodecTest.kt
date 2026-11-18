package com.android.shaftschematic.doc

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasDiaTolerance
import com.android.shaftschematic.model.mergeBodiesAround
import com.android.shaftschematic.model.splitBodiesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ø tolerance fields on liners and bodies are additive and defaulted: a document saved before
 * them decodes with no tolerance, and a toleranced liner or body round-trips through the REAL codec
 * verbatim. A body split around a new component keeps the tolerance on both fragments; a merge takes
 * the AFT fragment's.
 */
class DiaToleranceCodecTest {

    private fun envelope(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `a document saved before the tolerance fields decodes with none`() {
        val raw = """
            {"version":1,"preferred_unit":"INCHES","spec":{"overallLengthMm":1000.0,
             "bodies":[{"id":"b1","startFromAftMm":0.0,"lengthMm":400.0,"diaMm":200.0}],
             "liners":[{"id":"l1","startMmPhysical":500.0,"lengthMm":300.0,"odMm":228.6}]}}
        """.trimIndent()
        val spec = ShaftDocCodec.decodeEnvelope(raw).spec
        val b = spec.bodies.single()
        val l = spec.liners.single()
        assertEquals(0f, b.diaTolPlusMm, 0f)
        assertEquals(0f, b.diaTolMinusMm, 0f)
        assertEquals(0f, l.diaTolPlusMm, 0f)
        assertEquals(0f, l.diaTolMinusMm, 0f)
        assertFalse(b.hasDiaTolerance)
        assertFalse(l.hasDiaTolerance)
    }

    @Test
    fun `a toleranced liner and body round-trip verbatim`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 200f,
                diaTolPlusMm = 0.0254f, diaTolMinusMm = 0.0127f)),
            liners = listOf(Liner(id = "l1", startFromAftMm = 500f, lengthMm = 300f, odMm = 228.6f,
                endMmPhysical = 800f, diaTolPlusMm = 0.0508f)),
        )
        val decoded = ShaftDocCodec.decodeEnvelope(ShaftDocCodec.encodeV1(envelope(spec))).spec
        val b = decoded.bodies.single()
        val l = decoded.liners.single()
        assertEquals(0.0254f, b.diaTolPlusMm, 0f)
        assertEquals(0.0127f, b.diaTolMinusMm, 0f)
        assertEquals(0.0508f, l.diaTolPlusMm, 0f)
        assertEquals(0f, l.diaTolMinusMm, 0f)
        assertTrue(l.hasDiaTolerance)
    }

    @Test
    fun `a split keeps the tolerance on both fragments and a merge keeps it`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 200f,
            diaTolPlusMm = 0.0508f, diaTolMinusMm = 0.0254f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body))
        var n = 0
        val split = spec.splitBodiesAround(400f, 600f) { "f${n++}" }.spec
        assertEquals(2, split.bodies.size)
        split.bodies.forEach {
            assertEquals(0.0508f, it.diaTolPlusMm, 0f)
            assertEquals(0.0254f, it.diaTolMinusMm, 0f)
        }
        val merged = split.mergeBodiesAround(400f, 600f) { "m${n++}" }.spec
        val single = merged.bodies.single()
        assertEquals(0.0508f, single.diaTolPlusMm, 0f)
        assertEquals(0.0254f, single.diaTolMinusMm, 0f)
    }
}
