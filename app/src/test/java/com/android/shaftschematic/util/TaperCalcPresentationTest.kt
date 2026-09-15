package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The taper calculator's display rules — what each field and the message line show for a
 * given set of entries, before and after Calculate. The dialog cannot be hosted under the
 * Robolectric harness, so these are the calculator's UI tests.
 */
class TaperCalcPresentationTest {

    private val inch = TaperCalcEntries(unit = UnitSystem.INCHES)

    private fun TaperCalcPresentation.red(): Set<TaperCalcField> =
        TaperCalcField.entries.filter { field(it).isError }.toSet()

    // ── Before Calculate ───────────────────────────────────────────────────────

    @Test
    fun `an empty form says nothing and marks nothing`() {
        val p = presentTaperCalc(inch, calculated = false)
        assertTrue(p.red().isEmpty())
        assertNull(p.message)
        assertFalse(p.isSolved)
    }

    @Test
    fun `two values typed but not calculated show no derived value and no errors`() {
        val p = presentTaperCalc(inch.copy(largeDia = "9.875", length = "16.5"), calculated = false)
        assertTrue(p.red().isEmpty())
        assertNull(p.message)
        TaperCalcField.entries.forEach { assertNull(p.field(it).derivedText) }
    }

    @Test
    fun `unreadable text is red immediately, without Calculate`() {
        val p = presentTaperCalc(inch.copy(largeDia = "abc", rate = "1:"), calculated = false)
        assertEquals(setOf(TaperCalcField.LARGE_DIA, TaperCalcField.RATE), p.red())
        assertNull("parse errors are at the field, the message waits for Calculate", p.message)
    }

    // ── Too few values ─────────────────────────────────────────────────────────

    @Test
    fun `two values calculated turn the two missing fields red and name them`() {
        val p = presentTaperCalc(inch.copy(largeDia = "9.875", length = "16.5"), calculated = true)
        assertEquals(setOf(TaperCalcField.SMALL_DIA, TaperCalcField.RATE), p.red())
        assertEquals("Enter one more value — Small end Ø or Taper rate.", p.message)
        assertTrue(p.messageIsError)
    }

    @Test
    fun `one value calculated asks for any three`() {
        val p = presentTaperCalc(inch.copy(length = "16.5"), calculated = true)
        assertEquals(
            setOf(TaperCalcField.LARGE_DIA, TaperCalcField.SMALL_DIA, TaperCalcField.RATE),
            p.red(),
        )
        assertEquals("Enter any three values to calculate the fourth.", p.message)
    }

    @Test
    fun `an unreadable field counts as missing and the message points at it`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "9.875", length = "16.5", rate = "sixteen"),
            calculated = true,
        )
        assertTrue(TaperCalcField.RATE in p.red())
        assertEquals("Fix the highlighted value first.", p.message)
    }

    // ── Solved ─────────────────────────────────────────────────────────────────

    @Test
    fun `the small end appears in its own field, in the entry unit without a suffix`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "9.875", length = "16.5", rate = "1:16"),
            calculated = true,
        )
        assertTrue(p.isSolved)
        assertTrue(p.red().isEmpty())
        // 9.875 − 16.5/16 = 8.84375 → the shop-fraction formatter's nearest reading.
        val set = p.field(TaperCalcField.SMALL_DIA).derivedText
        assertEquals(taperCalcNumberText(8.84375 * 25.4, UnitSystem.INCHES), set)
        assertFalse("no unit suffix — the field shows one", set!!.endsWith("in"))
        listOf(TaperCalcField.LARGE_DIA, TaperCalcField.LENGTH, TaperCalcField.RATE).forEach {
            assertNull("typed fields carry no derived value", p.field(it).derivedText)
        }
        assertNull(p.message)
    }

    @Test
    fun `the rate appears in the rate field with its per-foot reading underneath`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "4", smallDia = "3", length = "12"),
            calculated = true,
        )
        assertEquals("1:12", p.field(TaperCalcField.RATE).derivedText)
        assertEquals("1\"/ft", p.field(TaperCalcField.RATE).supportingText)
    }

    @Test
    fun `a near-common rate names the common one beside the exact`() {
        // 16.5 / (9.875 − 8.844) = 16.004 → exact 1:16.004, common 1:16.
        val p = presentTaperCalc(
            inch.copy(largeDia = "9.875", smallDia = "8.844", length = "16.5"),
            calculated = true,
        )
        val rate = p.field(TaperCalcField.RATE)
        assertEquals("1:16.004", rate.derivedText)
        // The per-foot reading is stated from the EXACT N (12 / 16.004), so it is the
        // formatter's nearest spelling of 0.7498 — not the rounded "3/4" the common rate implies.
        val text = rate.supportingText!!
        assertTrue(text, text.contains("≈ 1:16 (within 3%)"))
        assertTrue(text, text.contains("\"/ft"))
        assertEquals("${rate.supportingText}", "${rateSupportingText(
            taperCalcRate(16.5 * 25.4, 8.844 * 25.4, 9.875 * 25.4)!!, UnitSystem.INCHES)}")
    }

    @Test
    fun `a metric entry has no per-foot line`() {
        val p = presentTaperCalc(
            TaperCalcEntries(largeDia = "100", smallDia = "75", length = "300", unit = UnitSystem.MILLIMETERS),
            calculated = true,
        )
        assertEquals("1:12", p.field(TaperCalcField.RATE).derivedText)
        assertNull(p.field(TaperCalcField.RATE).supportingText)
    }

    @Test
    fun `the length appears with the per-foot line under the typed rate`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "4", smallDia = "3", rate = "1:12"),
            calculated = true,
        )
        assertEquals("12", p.field(TaperCalcField.LENGTH).derivedText)
        assertEquals("1\"/ft", p.field(TaperCalcField.RATE).supportingText)
    }

    // ── All four typed ─────────────────────────────────────────────────────────

    @Test
    fun `four agreeing values say so and derive nothing`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "4", smallDia = "3", length = "12", rate = "1:12"),
            calculated = true,
        )
        assertTrue(p.isSolved)
        assertEquals("All four values agree.", p.message)
        assertFalse(p.messageIsError)
        TaperCalcField.entries.forEach { assertNull(p.field(it).derivedText) }
    }

    @Test
    fun `a typed rate that disagrees turns the rate red and quotes the geometry's own`() {
        val p = presentTaperCalc(
            inch.copy(largeDia = "4", smallDia = "3", length = "12", rate = "1:16"),
            calculated = true,
        )
        assertEquals(setOf(TaperCalcField.RATE), p.red())
        assertTrue(p.messageIsError)
        assertEquals("The typed rate does not match these three values — they give 1:12.", p.message)
    }

    // ── Solve-level issues ─────────────────────────────────────────────────────

    @Test
    fun `a small end at or above the large end reds both diameters after Calculate only`() {
        val e = inch.copy(largeDia = "3", smallDia = "4", length = "12")
        assertTrue(presentTaperCalc(e, calculated = false).red().isEmpty())
        val p = presentTaperCalc(e, calculated = true)
        assertEquals(setOf(TaperCalcField.LARGE_DIA, TaperCalcField.SMALL_DIA), p.red())
        assertTrue(p.messageIsError)
    }

    @Test
    fun `a rate that consumes the diameter reds the small end and the rate`() {
        val p = presentTaperCalc(inch.copy(largeDia = "1", length = "24", rate = "1:12"), calculated = true)
        assertEquals(setOf(TaperCalcField.SMALL_DIA, TaperCalcField.RATE), p.red())
    }

    // ── Entries helpers ────────────────────────────────────────────────────────

    @Test
    fun `with and text address the same field`() {
        val e = TaperCalcEntries().with(TaperCalcField.LENGTH, "16.5")
        assertEquals("16.5", e.text(TaperCalcField.LENGTH))
        assertEquals("", e.text(TaperCalcField.RATE))
    }
}
