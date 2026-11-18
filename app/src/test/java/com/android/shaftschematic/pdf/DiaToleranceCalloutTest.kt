package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.pdf.notes.DiaToleranceText
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A liner OD or body Ø tolerance (on-device request: a 9" liner carrying +.002 / −.000 on the Final
 * drawing) prints as two stacked limits after the Ø callout value. This pins the ONE formatter and
 * what the callout builders do with it — grouping, and the liner/body join.
 */
class DiaToleranceCalloutTest {

    private val inch = 25.4

    // ── formatDiaTolerance ────────────────────────────────────────────────────

    @Test
    fun `no tolerance formats to null`() {
        assertNull(formatDiaTolerance(0.0, 0.0, UnitSystem.INCHES))
        assertNull(formatDiaTolerance(0.0, 0.0, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `inches print sign and three decimals with no leading zero`() {
        assertEquals(
            DiaToleranceText("+.002", "-.000"),
            formatDiaTolerance(0.002 * inch, 0.0, UnitSystem.INCHES),
        )
        assertEquals(
            DiaToleranceText("+.000", "-.005"),
            formatDiaTolerance(0.0, 0.005 * inch, UnitSystem.INCHES),
        )
    }

    @Test
    fun `an inch limit of one or more keeps its integer part`() {
        assertEquals(
            DiaToleranceText("+1.250", "-.010"),
            formatDiaTolerance(1.25 * inch, 0.010 * inch, UnitSystem.INCHES),
        )
    }

    @Test
    fun `an inch limit that needs a fourth place takes it on both limits`() {
        assertEquals(
            DiaToleranceText("+.0005", "-.0010"),
            formatDiaTolerance(0.0005 * inch, 0.001 * inch, UnitSystem.INCHES),
        )
    }

    @Test
    fun `stored float values format exactly`() {
        // What the field stores for a typed .002": the Float of 0.0508 mm.
        assertEquals(
            DiaToleranceText("+.002", "-.001"),
            formatDiaTolerance((0.002f * 25.4f).toDouble(), (0.001f * 25.4f).toDouble(), UnitSystem.INCHES),
        )
    }

    @Test
    fun `millimetres print compact decimals shared by both limits and a bare zero side`() {
        assertEquals(DiaToleranceText("+0.05", "0"), formatDiaTolerance(0.05, 0.0, UnitSystem.MILLIMETERS))
        assertEquals(DiaToleranceText("+0.25", "-0.10"), formatDiaTolerance(0.25, 0.1, UnitSystem.MILLIMETERS))
        assertEquals(DiaToleranceText("0", "-0.2"), formatDiaTolerance(0.0, 0.2, UnitSystem.MILLIMETERS))
        assertEquals(DiaToleranceText("+1.5", "-0.5"), formatDiaTolerance(1.5, 0.5, UnitSystem.MILLIMETERS))
        assertEquals(DiaToleranceText("+0.025", "0"), formatDiaTolerance(0.025f.toDouble(), 0.0, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `a whole inch value takes its limits' places and others print as always`() {
        val tol3 = DiaToleranceText("+.002", "-.000")
        val tol4 = DiaToleranceText("+.0005", "-.0000")
        assertEquals("9.000\"", formatTolerancedDiaWithUnit(9.0 * inch, UnitSystem.INCHES, tol3))
        assertEquals("9.0000\"", formatTolerancedDiaWithUnit(9.0 * inch, UnitSystem.INCHES, tol4))
        assertEquals("8.750\"", formatTolerancedDiaWithUnit(8.75 * inch, UnitSystem.INCHES, tol3))
        assertEquals("228.6 mm", formatTolerancedDiaWithUnit(228.6, UnitSystem.MILLIMETERS, DiaToleranceText("+0.05", "0")))
    }

    // ── Callout builders ──────────────────────────────────────────────────────

    @Test
    fun `a toleranced liner callout carries its limits`() {
        val liner = Liner(id = "l1", startFromAftMm = 0f, lengthMm = 300f, odMm = 228.6f, diaTolPlusMm = 0.0508f)
        val call = buildLinerOdCallouts(listOf(liner)).single()
        assertEquals(DiaToleranceText("+.002", "-.000"), call.tolerance)
    }

    @Test
    fun `an untoleranced liner callout carries none`() {
        val liner = Liner(id = "l1", startFromAftMm = 0f, lengthMm = 300f, odMm = 228.6f)
        assertNull(buildLinerOdCallouts(listOf(liner)).single().tolerance)
    }

    @Test
    fun `the limits follow the callout's own unit`() {
        val liner = Liner(id = "l1", startFromAftMm = 0f, lengthMm = 300f, odMm = 228.6f, diaTolPlusMm = 0.05f)
        val units = DisplayUnits(UnitSystem.INCHES, overrides = mapOf("l1" to UnitSystem.MILLIMETERS))
        assertEquals(DiaToleranceText("+0.05", "0"), buildLinerOdCallouts(listOf(liner), units).single().tolerance)
    }

    @Test
    fun `liners of one OD with different tolerances print separately`() {
        val a = Liner(id = "a", startFromAftMm = 0f, lengthMm = 300f, odMm = 228.6f, diaTolPlusMm = 0.0508f)
        val b = Liner(id = "b", startFromAftMm = 1000f, lengthMm = 300f, odMm = 228.6f)
        val c = Liner(id = "c", startFromAftMm = 2000f, lengthMm = 400f, odMm = 228.6f, diaTolPlusMm = 0.0508f)
        val calls = buildLinerOdCallouts(listOf(a, b, c))
        assertEquals(2, calls.size)
        // The two toleranced liners still group, anchored on the longer of them.
        val tol = calls.single { it.tolerance != null }
        assertEquals(2200.0, tol.xMm, 1e-6)
    }

    @Test
    fun `bodies of one Ø with different tolerances print separately`() {
        val a = Body(id = "a", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f, showDiaOnDrawing = true,
            diaTolMinusMm = 0.0254f)
        val b = Body(id = "b", startFromAftMm = 1000f, lengthMm = 300f, diaMm = 200f, showDiaOnDrawing = true)
        val calls = buildBodyOdCallouts(listOf(a, b))
        assertEquals(2, calls.size)
        assertEquals(DiaToleranceText("+.000", "-.001"), calls.single { it.tolerance != null }.tolerance)
    }

    @Test
    fun `a liner and a touching body join only when their tolerances agree`() {
        val liner = Liner(id = "l1", startFromAftMm = 300f, lengthMm = 300f, odMm = 228.6f, diaTolPlusMm = 0.0508f)
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 300f, diaMm = 228.6f, showDiaOnDrawing = true)

        // Different tolerances: two values.
        assertEquals(2, joinSharedOdCallouts(listOf(body), listOf(liner)).size)

        // The same tolerance: one value with a leader to each.
        val joined = joinSharedOdCallouts(listOf(body.copy(diaTolPlusMm = 0.0508f)), listOf(liner))
        assertEquals(1, joined.size)
        assertEquals(DiaToleranceText("+.002", "-.000"), joined.single().tolerance)
        assertEquals(150.0, joined.single().secondXMm!!, 1e-6)
    }
}
