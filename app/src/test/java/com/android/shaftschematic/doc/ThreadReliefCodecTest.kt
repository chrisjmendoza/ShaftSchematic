package com.android.shaftschematic.doc

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.geom.SurfaceSeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thread relief fields are additive and defaulted: a document saved before them decodes with
 * no relief, and a relieved thread round-trips through the REAL codec verbatim. The resolve layer
 * copies them verbatim, and the surface envelope sees the groove at its own Ø.
 */
class ThreadReliefCodecTest {

    private fun envelope(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    @Test
    fun `a thread saved before the relief fields decodes with no relief`() {
        val raw = """
            {"version":1,"preferred_unit":"INCHES","spec":{"overallLengthMm":1000.0,
             "threads":[{"id":"t1","startFromAftMm":0.0,"majorDiaMm":114.3,"pitchMm":6.35,"lengthMm":150.0}]}}
        """.trimIndent()
        val th = ShaftDocCodec.decodeEnvelope(raw).spec.threads.single()
        assertEquals(0f, th.reliefLenMm, 0f)
        assertEquals(0f, th.reliefDiaMm, 0f)
        assertFalse(th.reliefAtAft)
        assertFalse(th.hasRelief)
    }

    @Test
    fun `a relieved thread round-trips verbatim`() {
        val th = Threads(
            id = "t1", startFromAftMm = 0f, lengthMm = 150f, majorDiaMm = 114.3f, pitchMm = 6.35f,
            reliefLenMm = 9.525f, reliefDiaMm = 106.045f, reliefAtAft = true,
        )
        val decoded = ShaftDocCodec.decodeEnvelope(
            ShaftDocCodec.encodeV1(envelope(ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))))
        ).spec.threads.single()
        assertEquals(9.525f, decoded.reliefLenMm, 0f)
        assertEquals(106.045f, decoded.reliefDiaMm, 0f)
        assertTrue(decoded.reliefAtAft)
        assertEquals(0f, decoded.startFromAftMm, 0f)
        assertEquals(150f, decoded.lengthMm, 0f)
    }

    @Test
    fun `resolve copies the relief verbatim and the envelope sees the groove`() {
        val th = Threads(
            id = "t1", startFromAftMm = 0f, lengthMm = 150f, majorDiaMm = 114.3f, pitchMm = 6.35f,
            reliefLenMm = 10f, reliefDiaMm = 105f, reliefAtAft = false,
        )
        val resolved = resolveComponents(ShaftSpec(overallLengthMm = 150f, threads = listOf(th)))
        val rt = resolved.filterIsInstance<ResolvedThread>().single()
        assertEquals(10f, rt.reliefLenMm, 0f)
        assertEquals(105f, rt.reliefDiaMm, 0f)
        assertFalse(rt.reliefAtAft)

        val segs = surfaceSegsFrom(listOf(rt))
        assertEquals(
            listOf(SurfaceSeg(0f, 140f, 114.3f, 114.3f), SurfaceSeg(140f, 150f, 105f, 105f)),
            segs,
        )
    }
}
