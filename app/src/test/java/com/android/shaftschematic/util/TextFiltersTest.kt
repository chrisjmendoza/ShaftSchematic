package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextFiltersTest {

    @Test
    fun `filterNumericInput strips colon by default`() {
        assertEquals(
            "112",
            filterNumericInput(raw = "1:12", allowNegative = false, allowFraction = true)
        )
    }

    @Test
    fun `filterNumericInput keeps colon when enabled`() {
        assertEquals(
            "1:12",
            filterNumericInput(
                raw = "1:12",
                allowNegative = false,
                allowFraction = true,
                allowColon = true
            )
        )
    }

    @Test
    fun `filterNumericInput keeps only first colon when enabled`() {
        assertEquals(
            "1:1234",
            filterNumericInput(
                raw = "1:12:34",
                allowNegative = false,
                allowFraction = true,
                allowColon = true
            )
        )
    }

    @Test
    fun `filterNumericInput keeps existing fraction and decimal behavior`() {
        assertEquals(
            "1 1/2",
            filterNumericInput(raw = "1 1/2", allowNegative = false, allowFraction = true)
        )
        assertEquals(
            "12.34",
            filterNumericInput(raw = "12.3.4", allowNegative = false, allowFraction = false)
        )
    }

    @Test
    fun `filterTaperRateInput lets every advertised rate spelling through to the parser`() {
        // The card and AddTaperDialog label both advertise "1:12, 3/4\"/ft, or decimal".
        for (typed in listOf("3/4\"/ft", "3/4in/ft", "3/4 in/ft", "3/4″/ft", "1:12", ".0625", "0.0833", "1 1/4\"/ft")) {
            val filtered = filterTaperRateInput(typed)
            assertEquals(typed, filtered)
            assertNotNull("parser must read $typed", parseTaperRateText(filtered, allowAmbiguousBareOne = false))
        }
        assertEquals(1f / 16f, parseTaperRateText(filterTaperRateInput("3/4\"/ft"), allowAmbiguousBareOne = false)!!, 1e-6f)
        assertEquals(1f / 16f, parseTaperRateText(filterTaperRateInput("3/4in/ft"), allowAmbiguousBareOne = false)!!, 1e-6f)
        assertEquals(1f / 12f, parseTaperRateText(filterTaperRateInput("1:12"), allowAmbiguousBareOne = false)!!, 1e-6f)
        assertEquals(0.0625f, parseTaperRateText(filterTaperRateInput(".0625"), allowAmbiguousBareOne = false)!!, 1e-6f)
    }

    @Test
    fun `the numeric filter is what blocked the per-foot form`() {
        // The rate field ran this filter before: it drops the inch mark and the second slash,
        // which leaves a shape the rate parser refuses.
        val numeric = filterNumericInput("3/4\"/ft", allowNegative = false, allowFraction = true, allowColon = true)
        assertEquals("3/4ft", numeric)
        assertNull(parseTaperRateText(numeric, allowAmbiguousBareOne = false))
    }

    @Test
    fun `filterTaperRateInput drops characters no rate spelling uses`() {
        assertEquals("1:12", filterTaperRateInput("1:12x"))
        assertEquals("3/4\"/ft", filterTaperRateInput("3/4\"/ft!"))
        assertEquals("0.0625", filterTaperRateInput("-0.0625"))
    }

    // ── Hyphenated mixed fractions ────────────────────────────────────────────

    private fun card(raw: String, allowNegative: Boolean = false) =
        filterNumericInput(raw = raw, allowNegative = allowNegative, allowFraction = true)

    @Test
    fun `the hyphen of a mixed fraction survives instead of joining the numbers`() {
        // Dropping it turned 10-1/4 into 101/4 = 25.25 (emulator QA).
        assertEquals("10-1/4", card("10-1/4"))
        assertEquals("368-1/2", card("368-1/2"))
        assertEquals(10.25, parseFractionOrDecimal(card("10-1/4"))!!, 1e-9)
        // Typed one key at a time, every intermediate keeps it.
        assertEquals("10-", card("10-"))
        assertEquals("10-1", card("10-1"))
    }

    @Test
    fun `only one hyphen, only after a digit, only before the slash`() {
        assertEquals("10-1/4", card("10--1/4"))
        assertEquals("1/4", card("-1/4"))           // a sign, and this field takes no negatives
        assertEquals("1/24", card("1/2-4"))         // after the slash: dropped
        assertEquals("10 -1/4", card("10 -1/4"))    // a space between digit and hyphen is fine
        assertEquals("1012", filterNumericInput("10-12", allowNegative = false, allowFraction = false))
    }

    @Test
    fun `a leading sign and the separator hyphen coexist where negatives are allowed`() {
        assertEquals("-10-1/4", card("-10-1/4", allowNegative = true))
        assertEquals(-10.25, parseFractionOrDecimal(card("-10-1/4", allowNegative = true))!!, 1e-9)
        assertEquals("-1/4", card("-1/4", allowNegative = true))
    }

    // ── Commas ────────────────────────────────────────────────────────────────

    @Test
    fun `commas are kept so the parser can read or refuse them`() {
        assertEquals("1,5", card("1,5"))           // was deleted to 15 (emulator QA)
        assertEquals("12,75", card("12,75"))
        assertEquals("1,500", card("1,500"))       // kept, then refused by the parser
        assertNull(parseFractionOrDecimal(card("1,500")))
        assertEquals("1,234.5", card("1,234.5"))
        assertEquals("1.5", card("1.5,"))          // a comma after the dot is never valid
    }

    // ── Unit suffixes ─────────────────────────────────────────────────────────

    @Test
    fun `a trailing unit suffix is kept for the parser`() {
        assertEquals("260mm", card("260mm"))
        assertEquals("260 mm", card("260 mm"))
        assertEquals("11\"", card("11\""))
        assertEquals("11″", card("11″"))
        assertEquals("11''", card("11''"))
        assertEquals("10.25in", card("10.25in"))
        assertEquals("10-1/4\"", card("10-1/4\""))
        assertEquals(260f, toMmOrNull(card("260mm"), UnitSystem.INCHES)!!, 1e-4f)
        // Typed one key at a time.
        assertEquals("260m", card("260m"))
        assertEquals("260mm", filterNumericInput("260mm", allowNegative = false, allowFraction = false))
    }

    @Test
    fun `letters away from the end are still dropped`() {
        assertEquals("260", card("2m60"))
        assertEquals("12", card("1a2"))
        assertEquals("4tpi", card("4tpi"))         // kept as typed at the end, then refused
        assertNull(parseFractionOrDecimal(card("4tpi")))
    }

    @Test
    fun `a trailing word reaches the parser whole and is refused`() {
        // Dropping the letters the suffix set does not name turned 12ft into an accepted 12.
        for (typed in listOf("12ft", "10 inches", "6 1/2 inch", "12FT")) {
            assertEquals(typed, card(typed))
            assertNull("$typed must not read as a length", toMmOrNull(card(typed), UnitSystem.INCHES))
        }
        // The suffixes the parser reads are unchanged.
        assertEquals("12in", card("12in"))
        assertEquals("260mm", card("260mm"))
        assertEquals("11\"", card("11\""))
        assertEquals(12f * 25.4f, toMmOrNull(card("12in"), UnitSystem.MILLIMETERS)!!, 1e-3f)
    }

    @Test
    fun `the sign path is unchanged by the trailing-word rule`() {
        assertEquals("-", card("-", allowNegative = true))
        assertEquals("", card("-"))
        assertEquals("-5", card("-5", allowNegative = true))
    }
}
