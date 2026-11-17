package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftEndHoles
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shaft ends' draw-plate holes ([ShaftEndHoles]) print as lines in their end's
 * footer column, built from the parts that are given, and print nothing when nothing is.
 */
class EndHoleFooterTest {

    private val inch = 25.4f

    private val cfg = FooterConfig(
        showAftThread = true, showFwdThread = true, showAftTaper = true, showFwdTaper = true,
    )

    // The hand-marked sheet's FWD end: 4 holes 1 1/4" NC on a 4 1/2" bolt circle.
    private val fwdHoles = ShaftEndHoles(
        drawPlateCount = 4,
        drawPlateSize = "1 1/4\" NC",
        drawPlateBoltCircleMm = 4.5f * inch,
    )

    private fun spec(aft: ShaftEndHoles = ShaftEndHoles(), fwd: ShaftEndHoles = ShaftEndHoles()) = ShaftSpec(
        overallLengthMm = 100f * inch,
        bodies = listOf(Body(id = "B", startFromAftMm = 0f, lengthMm = 100f * inch, diaMm = 8f * inch)),
        aftEndHoles = aft,
        fwdEndHoles = fwd,
    )

    @Test
    fun `a full record prints its line in its own end's column, verbatim size`() {
        val cols = buildFooterEndColumns(spec(fwd = fwdHoles), UnitSystem.INCHES, cfg)

        assertTrue(cols.fwdLines.toString(), "Draw plate: 4 holes 1 1/4\" NC on 4 1/2\" B.C." in cols.fwdLines)
        assertFalse(cols.aftLines.any { it.startsWith(FOOTER_DRAW_PLATE_LABEL) })
    }

    @Test
    fun `an empty record prints nothing`() {
        val cols = buildFooterEndColumns(spec(), UnitSystem.INCHES, cfg)
        assertTrue(cols.aftLines.isEmpty())
        assertTrue(cols.fwdLines.isEmpty())
    }

    @Test
    fun `only the given parts print`() {
        val partial = ShaftEndHoles(drawPlateCount = 1, drawPlateSize = "M30")
        val cols = buildFooterEndColumns(spec(aft = partial), UnitSystem.INCHES, cfg)

        assertEquals(listOf("Draw plate: 1 hole M30"), cols.aftLines)
    }

    @Test
    fun `the holes follow the end's taper block`() {
        val taper = Taper(
            id = "T", startFromAftMm = 85f * inch, lengthMm = 15f * inch,
            startDiaMm = 8f * inch, endDiaMm = 7f * inch,
        )
        val s = spec(fwd = fwdHoles).copy(tapers = listOf(taper))
        val fwd = buildFooterEndColumns(s, UnitSystem.INCHES, cfg).fwdLines

        assertEquals(FOOTER_FWD_TAPER_HEADER, fwd.first())
        val drawPlate = fwd.indexOfFirst { it.startsWith(FOOTER_DRAW_PLATE_LABEL) }
        assertTrue(drawPlate > fwd.indexOfFirst { it.startsWith("Length:") })
    }

    @Test
    fun `a blank draft keeps the labels and rules the values`() {
        val cols = buildFooterEndColumns(spec(fwd = fwdHoles), UnitSystem.INCHES, cfg, blankValues = true)
        assertTrue(FOOTER_DRAW_PLATE_LABEL in cols.fwdLines)
    }

    @Test
    fun `lengths print in the document unit, dual when the document is`() {
        val holes = ShaftEndHoles(drawPlateBoltCircleMm = 120f)
        val metric = buildFooterEndColumns(spec(aft = holes), UnitSystem.MILLIMETERS, cfg).aftLines
        assertEquals(listOf("Draw plate: on ${formatLenWithUnitDual(120.0, UnitSystem.MILLIMETERS, false)} B.C."), metric)

        val dual = buildFooterEndColumns(
            spec(aft = holes), UnitSystem.INCHES, cfg,
            displayUnits = DisplayUnits(UnitSystem.INCHES, dual = true),
        ).aftLines
        assertEquals(listOf("Draw plate: on ${formatLenWithUnitDual(120.0, UnitSystem.INCHES, true)} B.C."), dual)
    }
}
