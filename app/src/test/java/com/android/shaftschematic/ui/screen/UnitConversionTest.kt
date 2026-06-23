package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConversionTest {

    // ── toMmOrNull ────────────────────────────────────────────────────────────

    @Test
    fun `toMmOrNull in mm passthrough`() {
        assertEquals(127f, toMmOrNull("127", UnitSystem.MILLIMETERS))
    }

    @Test
    fun `toMmOrNull exact inch to mm — whole number`() {
        // 6" = 152.4 mm exactly; Float should round-trip cleanly via Double arithmetic
        val result = toMmOrNull("6", UnitSystem.INCHES)!!
        assertEquals(152.4f, result, 0.0001f)
    }

    @Test
    fun `toMmOrNull exact inch to mm — common fraction`() {
        // 5 15/16" = 5.9375" = 150.8125 mm
        val result = toMmOrNull("5 15/16", UnitSystem.INCHES)!!
        assertEquals(150.8125f, result, 0.0001f)
    }

    @Test
    fun `toMmOrNull exact inch to mm — small fraction`() {
        // 1/8" = 0.125" = 3.175 mm
        val result = toMmOrNull("1/8", UnitSystem.INCHES)!!
        assertEquals(3.175f, result, 0.0001f)
    }

    @Test
    fun `toMmOrNull blank returns null`() {
        assertNull(toMmOrNull("", UnitSystem.INCHES))
    }

    @Test
    fun `toMmOrNull invalid returns null`() {
        assertNull(toMmOrNull("abc", UnitSystem.MILLIMETERS))
    }

    // ── tpiToPitchMm ─────────────────────────────────────────────────────────

    @Test
    fun `tpiToPitchMm 16 tpi gives 1_5875 mm`() {
        // 25.4 / 16 = 1.5875 exactly
        assertEquals(1.5875f, tpiToPitchMm(16f), 0.0001f)
    }

    @Test
    fun `tpiToPitchMm 20 tpi gives 1_27 mm`() {
        // 25.4 / 20 = 1.27 exactly
        assertEquals(1.27f, tpiToPitchMm(20f), 0.0001f)
    }

    @Test
    fun `tpiToPitchMm zero returns zero`() {
        assertEquals(0f, tpiToPitchMm(0f), 0f)
    }

    // ── formatDisplay (mm → inch) ─────────────────────────────────────────────

    @Test
    fun `formatDisplay mm passthrough`() {
        assertEquals("127", formatDisplay(127f, UnitSystem.MILLIMETERS, 0))
    }

    @Test
    fun `formatDisplay inch round trip for common shaft diameter`() {
        // 6" stored as 152.4 mm — display should show 6.0000 (4 decimal min for inches)
        val s = formatDisplay(152.4f, UnitSystem.INCHES, 4)
        assertEquals("6", s)
    }

    @Test
    fun `formatDisplay inch precision for 5 15 16`() {
        // 5.9375" → 150.8125 mm → back to 5.9375"
        val mm = (5.9375 * 25.4).toFloat()
        val s = formatDisplay(mm, UnitSystem.INCHES, 4)
        assertEquals("5.9375", s)
    }
}
