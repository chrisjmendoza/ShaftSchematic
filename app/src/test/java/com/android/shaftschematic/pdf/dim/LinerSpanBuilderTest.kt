package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinerSpanBuilderTest {

    private val sets = SetPositions(aftSETxMm = 0.0, fwdSETxMm = 3810.0)

    private fun spans(liner: LinerDim, mode: PdfTieringMode = PdfTieringMode.AUTO) =
        buildLinerSpans(listOf(liner), sets, UnitSystem.INCHES, mode)

    @Test
    fun `an offset liner gets a datum span and a length span`() {
        val s = spans(LinerDim("l1", LinerAnchor.AFT_SET, offsetFromSetMm = 254.0, lengthMm = 304.8))
        assertEquals(2, s.size)
        assertEquals(SpanKind.DATUM, s[0].kind)
        assertEquals(SpanKind.LOCAL, s[1].kind)
    }

    @Test
    fun `a liner sitting on its aft SET emits no zero-length datum span`() {
        // A zero-offset liner printed a floating "0.000" beside the SET (on-device report):
        // the datum span had nothing to dimension, so it must not exist at all.
        val s = spans(LinerDim("l1", LinerAnchor.AFT_SET, offsetFromSetMm = 0.0, lengthMm = 304.8))
        assertEquals(1, s.size)
        assertEquals(SpanKind.LOCAL, s.single().kind)
        assertEquals(0.0, s.single().x1Mm, 1e-6)
        assertEquals(304.8, s.single().x2Mm, 1e-6)
    }

    @Test
    fun `a liner sitting on its fwd SET emits no zero-length datum span`() {
        val s = spans(LinerDim("l1", LinerAnchor.FWD_SET, offsetFromSetMm = 0.0, lengthMm = 304.8))
        assertEquals(1, s.size)
        assertEquals(SpanKind.LOCAL, s.single().kind)
    }

    @Test
    fun `a forced reference still skips the zero-length datum span`() {
        val s = spans(
            LinerDim("l1", LinerAnchor.AFT_SET, offsetFromSetMm = 0.0, lengthMm = 304.8),
            mode = PdfTieringMode.AFT,
        )
        assertTrue(s.none { it.kind == SpanKind.DATUM })
    }
}
