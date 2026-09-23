package com.android.shaftschematic.util

import com.android.shaftschematic.model.MM_PER_IN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The standalone taper calculator's solve core. Every case is expressed in inches converted to
 * canonical mm at the call, mirroring what the dialog does at its entry-unit edge.
 */
class TaperCalcMathTest {

    private fun inches(v: Double) = v * MM_PER_IN

    private fun solved(result: TaperCalcResult): TaperCalcResult.Solved {
        assertTrue("expected a solve, got $result", result is TaperCalcResult.Solved)
        return result as TaperCalcResult.Solved
    }

    private fun issue(result: TaperCalcResult): TaperCalcIssue {
        assertTrue("expected an invalid result, got $result", result is TaperCalcResult.Invalid)
        return (result as TaperCalcResult.Invalid).issue
    }

    // ── Solve for the rate (the headline case) ─────────────────────────────────

    @Test
    fun `three geometry values give the rate`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(12.0),
                slope = null,
            )
        )

        assertEquals(TaperCalcUnknown.RATE, r.unknown)
        assertEquals(12.0, r.rate.exactOneToN, 1e-3)
        assertEquals("1:12", r.rate.exactText)
        assertNull(r.solvedValueMm)
        assertNull(r.typedSlopeAgrees)
    }

    @Test
    fun `a rate near a common one snaps and names it`() {
        // 1" of taper over 15.6" is 1:15.6 — 2.5% off 1:16, inside the tolerance.
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(15.6),
                slope = null,
            )
        )

        assertEquals(15.6, r.rate.exactOneToN, 1e-3)
        assertEquals("1:15.6", r.rate.exactText)
        assertEquals("1:16", r.rate.commonText)
    }

    @Test
    fun `a rate well outside every common one names none`() {
        // 1:30 is nowhere near 1:20, the nearest common rate.
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(30.0),
                slope = null,
            )
        )

        assertEquals(30.0, r.rate.exactOneToN, 1e-3)
        assertNull(r.rate.commonOneToN)
        assertNull(r.rate.commonText)
    }

    // ── The three inverse solves ───────────────────────────────────────────────

    @Test
    fun `rate plus length plus small end gives the large end`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = null,
                smallDiaMm = inches(3.0),
                lengthMm = inches(12.0),
                slope = 1.0 / 12.0,
            )
        )

        assertEquals(TaperCalcUnknown.LARGE_DIA, r.unknown)
        assertEquals(inches(4.0), r.largeDiaMm, 1e-6)
        assertEquals(inches(4.0), r.solvedValueMm!!, 1e-6)
    }

    @Test
    fun `rate plus length plus large end gives the small end`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = null,
                lengthMm = inches(12.0),
                slope = 1.0 / 12.0,
            )
        )

        assertEquals(TaperCalcUnknown.SMALL_DIA, r.unknown)
        assertEquals(inches(3.0), r.smallDiaMm, 1e-6)
    }

    @Test
    fun `rate plus both diameters gives the length`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = null,
                slope = 1.0 / 12.0,
            )
        )

        assertEquals(TaperCalcUnknown.LENGTH, r.unknown)
        assertEquals(inches(12.0), r.lengthMm, 1e-6)
    }

    @Test
    fun `an inverse solve still reports the rate it implies`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = null,
                lengthMm = inches(12.0),
                slope = 1.0 / 12.0,
            )
        )

        assertEquals(12.0, r.rate.exactOneToN, 1e-3)
        assertEquals("1:12", r.rate.exactText)
    }

    // ── All four: consistency check ────────────────────────────────────────────

    @Test
    fun `all four agreeing reports agreement and derives nothing`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(12.0),
                slope = 1.0 / 12.0,
            )
        )

        assertNull(r.unknown)
        assertEquals(true, r.typedSlopeAgrees)
        assertNull(r.solvedValueMm)
    }

    @Test
    fun `all four within the tolerance still agree`() {
        // Geometry is 1:12; a typed 1:12.3 is 2.4% off — inside the 3% common-rate tolerance.
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(12.0),
                slope = 1.0 / 12.3,
            )
        )

        assertEquals(true, r.typedSlopeAgrees)
    }

    @Test
    fun `all four outside the tolerance disagree without erroring`() {
        val r = solved(
            solveTaperCalc(
                largeDiaMm = inches(4.0),
                smallDiaMm = inches(3.0),
                lengthMm = inches(12.0),
                slope = 1.0 / 16.0,
            )
        )

        assertEquals(false, r.typedSlopeAgrees)
        // The geometry, not the typed rate, is what the result reports.
        assertEquals(12.0, r.rate.exactOneToN, 1e-3)
    }

    @Test
    fun `the tolerance the check uses is the app's common-rate tolerance`() {
        val geometrySlope = 1.0 / 12.0
        val justInside = geometrySlope * (1.0 - DEFAULT_SLOPE_ERROR_TOLERANCE.toDouble() + 1e-4)
        val justOutside = geometrySlope * (1.0 - DEFAULT_SLOPE_ERROR_TOLERANCE.toDouble() - 1e-4)

        fun agreesWith(slope: Double) = solved(
            solveTaperCalc(inches(4.0), inches(3.0), inches(12.0), slope)
        ).typedSlopeAgrees

        assertEquals(true, agreesWith(justInside))
        assertEquals(false, agreesWith(justOutside))
    }

    // ── Not enough to solve ────────────────────────────────────────────────────

    @Test
    fun `nothing entered is incomplete, not an error`() {
        assertEquals(
            TaperCalcResult.Incomplete,
            solveTaperCalc(null, null, null, null),
        )
    }

    @Test
    fun `two values are incomplete`() {
        assertEquals(
            TaperCalcResult.Incomplete,
            solveTaperCalc(inches(4.0), inches(3.0), null, null),
        )
    }

    @Test
    fun `a blank field wins over a bad value in the other blanks`() {
        // Only two readable values, one of them nonsense: still Incomplete, so the results
        // block stays quiet and the field's own error state does the talking.
        assertEquals(
            TaperCalcResult.Incomplete,
            solveTaperCalc(inches(-4.0), null, null, 1.0 / 12.0),
        )
    }

    // ── Invalid geometry ───────────────────────────────────────────────────────

    @Test
    fun `equal ends are not a taper`() {
        assertEquals(
            TaperCalcIssue.SET_NOT_SMALLER,
            issue(solveTaperCalc(inches(4.0), inches(4.0), inches(12.0), null)),
        )
    }

    @Test
    fun `a small end larger than the large end is rejected`() {
        assertEquals(
            TaperCalcIssue.SET_NOT_SMALLER,
            issue(solveTaperCalc(inches(3.0), inches(4.0), inches(12.0), null)),
        )
    }

    @Test
    fun `a nonpositive length is rejected`() {
        assertEquals(
            TaperCalcIssue.NON_POSITIVE_LENGTH,
            issue(solveTaperCalc(inches(4.0), inches(3.0), 0.0, null)),
        )
    }

    @Test
    fun `a nonpositive diameter is rejected`() {
        assertEquals(
            TaperCalcIssue.NON_POSITIVE_DIA,
            issue(solveTaperCalc(inches(4.0), 0.0, inches(12.0), null)),
        )
    }

    @Test
    fun `a nonpositive rate is rejected`() {
        assertEquals(
            TaperCalcIssue.NON_POSITIVE_RATE,
            issue(solveTaperCalc(inches(4.0), null, inches(12.0), -1.0 / 12.0)),
        )
    }

    @Test
    fun `a rate that eats the whole large end is rejected`() {
        // 1:2 over 12" removes 6" of diameter from a 4" end.
        assertEquals(
            TaperCalcIssue.RATE_CONSUMES_DIA,
            issue(solveTaperCalc(inches(4.0), null, inches(12.0), 0.5)),
        )
    }

    // ── Inches per foot ────────────────────────────────────────────────────────

    @Test
    fun `1 to 12 is one inch per foot`() {
        val rate = taperCalcRate(inches(12.0), inches(3.0), inches(4.0))!!
        assertEquals(1.0, rate.inchesPerFoot, 1e-6)
        assertEquals("1", rate.inchesPerFootText)
    }

    @Test
    fun `1 to 16 is three quarters of an inch per foot`() {
        val rate = taperCalcRate(inches(16.0), inches(3.0), inches(4.0))!!
        assertEquals(0.75, rate.inchesPerFoot, 1e-6)
        assertEquals("3/4", rate.inchesPerFootText)
    }

    @Test
    fun `a per-foot value off the scale prints as a decimal`() {
        // 1:20 is 0.6"/ft — not a sixteenth, so the fraction-smart formatter keeps decimals.
        val rate = taperCalcRate(inches(20.0), inches(3.0), inches(4.0))!!
        assertEquals(0.6, rate.inchesPerFoot, 1e-6)
        assertEquals("0.600", rate.inchesPerFootText)
    }

    @Test
    fun `a straight run has no rate at all`() {
        assertNull(taperCalcRate(inches(12.0), inches(4.0), inches(4.0)))
        assertNull(taperCalcRate(0.0, inches(3.0), inches(4.0)))
    }

    // ── Display formatting ─────────────────────────────────────────────────────

    @Test
    fun `inch results are fraction-smart with a unit suffix`() {
        assertEquals("2 3/4 in", taperCalcValueText(inches(2.75), UnitSystem.INCHES))
        assertEquals("3 in", taperCalcValueText(inches(3.0), UnitSystem.INCHES))
    }

    @Test
    fun `millimeter results take the three-decimal print convention`() {
        assertEquals("101.600 mm", taperCalcValueText(101.6, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `a metric entry solves the same identity`() {
        // 100 mm down to 80 mm over 240 mm is 1:12, whatever unit it was typed in.
        val r = solved(solveTaperCalc(100.0, 80.0, 240.0, null))

        assertEquals(12.0, r.rate.exactOneToN, 1e-6)
        assertEquals("1:12", r.rate.exactText)
    }

    @Test
    fun `the common-rate decision is delegated, never restated`() {
        // Canonical mm reach autoTaperRate unchanged, so its snap and its bore tie-break
        // decide what the calculator names — the calculator holds no rate list of its own.
        listOf(
            Triple(inches(12.0), inches(3.0), inches(4.0)),
            Triple(inches(15.6), inches(3.0), inches(4.0)),
            Triple(inches(30.0), inches(3.0), inches(4.0)),
            Triple(3000.0, 200.0, 415.0),
        ).forEach { (len, set, let) ->
            val expected = autoTaperRate(
                lengthMm = len.toFloat(),
                setDiaMm = set.toFloat(),
                letDiaMm = let.toFloat(),
            )!!.matchedCommonOneToN?.toDouble()

            assertEquals(expected, taperCalcRate(len, set, let)!!.commonOneToN)
        }
    }
}
