package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.pdf.dim.buildLinerSpans
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildBodyLengthSpans` — an explicit body's own length rail. ONE LOCAL span over the STORED
 * span of every body whose effective flag is on (default: on at a shaft end, off mid-shaft),
 * labelled with the stored length in the body's own display unit. A span the sheet already
 * prints — another builder's identical span, or the OAL rail for a face-to-face body — is not
 * printed twice. Shared by the schematic and the consolidated sheet.
 */
class BodyLengthDimSpanTest {

    private val inch = 25.4f
    private val oal = 120f * inch

    private fun body(id: String, startIn: Float, lenIn: Float, rail: Boolean? = null) = Body(
        id = id, startFromAftMm = startIn * inch, lengthMm = lenIn * inch, diaMm = 6f * inch,
        showDimensionRail = rail,
    )

    private fun spans(spec: ShaftSpec, units: DisplayUnits = DisplayUnits.single(UnitSystem.INCHES)) =
        buildBodyLengthSpans(spec, computeOalWindow(spec), UnitSystem.INCHES, units)

    @Test
    fun `an end body prints its length by default`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("fwd", 105.25f, 14.75f)))
        val span = spans(spec).single()
        assertEquals(SpanKind.LOCAL, span.kind)
        assertEquals((105.25f * inch).toDouble(), minOf(span.x1Mm, span.x2Mm), 1e-2)
        assertEquals(oal.toDouble(), maxOf(span.x1Mm, span.x2Mm), 1e-2)
        assertEquals(formatLenDim((14.75f * inch).toDouble(), UnitSystem.INCHES), span.label.inline())
    }

    @Test
    fun `an AFT end body prints too`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("aft", 0f, 20f)))
        assertEquals(1, spans(spec).size)
    }

    @Test
    fun `a mid-shaft body prints nothing by default`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("mid", 40f, 20f)))
        assertTrue(spans(spec).isEmpty())
    }

    @Test
    fun `an explicit ON prints a mid-shaft body`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("mid", 40f, 20f, rail = true)))
        val span = spans(spec).single()
        assertEquals((40f * inch).toDouble(), minOf(span.x1Mm, span.x2Mm), 1e-2)
        assertEquals((60f * inch).toDouble(), maxOf(span.x1Mm, span.x2Mm), 1e-2)
    }

    @Test
    fun `an explicit OFF hides an end body`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("fwd", 100f, 20f, rail = false)))
        assertTrue(spans(spec).isEmpty())
    }

    @Test
    fun `the label takes the body's own display unit, dual aware`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("fwd", 100f, 20f)))
        val metric = DisplayUnits(UnitSystem.INCHES, overrides = mapOf("fwd" to UnitSystem.MILLIMETERS))
        assertEquals(
            formatLenDim((20f * inch).toDouble(), UnitSystem.MILLIMETERS),
            spans(spec, metric).single().label.inline(),
        )
        val dual = DisplayUnits(UnitSystem.INCHES, dual = true)
        val label = spans(spec, dual).single().label
        assertEquals(formatLenDim((20f * inch).toDouble(), UnitSystem.INCHES), label.primary)
        assertNotNull("dual sheets carry the secondary term", label.secondary)
    }

    @Test
    fun `a span another builder already prints is not printed twice`() {
        // The on-device layout: a liner butting the FWD-end body. The liner's datum rail from the
        // FWD SET to its FWD edge IS the body's span, so the body adds nothing.
        val spec = ShaftSpec(
            overallLengthMm = oal,
            bodies = listOf(body("fwd", 105.25f, 14.75f)),
            liners = listOf(Liner(id = "l", startFromAftMm = 70f * inch, lengthMm = 35.25f * inch, odMm = 7f * inch)),
        )
        val win = computeOalWindow(spec)
        val linerSpans = buildLinerSpans(
            liners = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO),
            sets = computeSetPositionsInMeasureSpace(win, spec),
            unit = UnitSystem.INCHES,
            measureFrom = PdfTieringMode.AUTO,
        )
        assertEquals("alone, the body prints", 1, spans(spec).size)
        assertTrue(
            buildBodyLengthSpans(spec, win, UnitSystem.INCHES, existing = linerSpans).isEmpty(),
        )
    }

    @Test
    fun `a face-to-face body is the OAL rail and prints nothing of its own`() {
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body("all", 0f, 120f, rail = true)))
        assertTrue(spans(spec).isEmpty())
    }
}
