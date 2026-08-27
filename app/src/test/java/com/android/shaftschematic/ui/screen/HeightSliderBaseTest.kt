package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN
import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.PROFILE_MIN_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.drawnShaftHeightPt
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The base solves behind the "Shaft height" slider. The slider states its track in paper
 * INCHES, so every surface that hosts it has to start from the same base for the same job —
 * two surfaces disagreeing would offer one shaft two different heights.
 *
 * Two bases, one per composer: the schematic takes the sizing curve alone (it never widens
 * the shaft to fill the page), the runout family takes max(width-fit, curve).
 */
class HeightSliderBaseTest {

    // Ø 8" (203.2 mm) — the sizing curve's high anchor, so the standard height is exactly
    // the anchor value and the arithmetic is readable.
    private val dia = 203.2f
    private val loIn = 0.5f
    private val hiIn = 1.0f

    /** Long shaft: at the curve scale it overflows the page, so width-fit is the smaller term. */
    private val longSpec = ShaftSpec(
        overallLengthMm = 4000f,
        liners = listOf(Liner(startFromAftMm = 800f, lengthMm = 700f, odMm = dia)),
    )

    /** Short shaft: it fits the page at far more than the curve scale. */
    private val shortSpec = ShaftSpec(
        overallLengthMm = 300f,
        liners = listOf(Liner(startFromAftMm = 50f, lengthMm = 100f, odMm = dia)),
    )

    @Test
    fun `the slider sizes against the true largest OD`() {
        assertEquals(dia, heightSliderMaxDiaFor(longSpec), 1e-3f)
    }

    @Test
    fun `a spec with no diameters still yields a usable track`() {
        assertEquals(10f, heightSliderMaxDiaFor(ShaftSpec(overallLengthMm = 1000f)), 1e-3f)
    }

    @Test
    fun `the schematic base draws the standard height at 100 percent`() {
        val base = schematicHeightSliderBase(dia, loIn, hiIn)
        // 8" → 1.00" on the standard curve — the hand-sheet rule.
        assertEquals(72f, drawnShaftHeightPt(base, 1f, dia), 0.01f)
    }

    @Test
    fun `the ceiling caps the top of the track`() {
        val base = schematicHeightSliderBase(dia, loIn, hiIn)
        assertEquals(
            PROFILE_MAX_SHAFT_HEIGHT_PT,
            drawnShaftHeightPt(base, PROFILE_HEIGHT_SCALE_MAX, dia),
            0.01f,
        )
    }

    /**
     * The track is stated in PAPER inches, so both its ends are paper measures — the near end is
     * the 1/2" floor, not a fraction of this shaft's own curve height. That is what lets a large
     * shaft (usually a long one, which cramps the schematic) be shrunk out of the way.
     */
    @Test
    fun `the track spans the full paper band on an ordinary shaft`() {
        listOf(6f, 8f, 11f, 14f).forEach { diaIn ->
            val d = diaIn * 25.4f
            val base = schematicHeightSliderBase(d, loIn, hiIn)
            assertEquals(
                "Ø${diaIn}in track floor",
                PROFILE_MIN_SHAFT_HEIGHT_PT, drawnShaftHeightPt(base, PROFILE_HEIGHT_SCALE_MIN, d), 0.01f,
            )
            assertEquals(
                "Ø${diaIn}in track ceiling",
                PROFILE_MAX_SHAFT_HEIGHT_PT, drawnShaftHeightPt(base, PROFILE_HEIGHT_SCALE_MAX, d), 0.01f,
            )
        }
    }

    @Test
    fun `a long shaft's runout base is the sizing curve`() {
        val schematic = schematicHeightSliderBase(dia, loIn, hiIn)
        val runout = runoutHeightSliderBase(longSpec, dia, loIn, hiIn)
        assertEquals(schematic, runout, 1e-6f)
    }

    @Test
    fun `a short shaft's runout base is the width fit`() {
        val schematic = schematicHeightSliderBase(dia, loIn, hiIn)
        val runout = runoutHeightSliderBase(shortSpec, dia, loIn, hiIn)
        assertTrue("width-fit ($runout) must exceed the curve ($schematic)", runout > schematic)
    }

    @Test
    fun `the width-fit term never lowers the runout base`() {
        val schematic = schematicHeightSliderBase(dia, loIn, hiIn)
        for (spec in listOf(longSpec, shortSpec)) {
            assertTrue(runoutHeightSliderBase(spec, dia, loIn, hiIn) >= schematic - 1e-6f)
        }
    }

    @Test
    fun `taller anchors raise both bases`() {
        val tallSchematic = schematicHeightSliderBase(dia, 0.75f, 1.25f)
        assertTrue(tallSchematic > schematicHeightSliderBase(dia, loIn, hiIn))
        assertTrue(
            runoutHeightSliderBase(longSpec, dia, 0.75f, 1.25f) >
                runoutHeightSliderBase(longSpec, dia, loIn, hiIn)
        )
    }
}
