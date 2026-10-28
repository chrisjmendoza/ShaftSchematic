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
        // The rate field ran this filter before: it leaves the bare fraction the parser refuses.
        val numeric = filterNumericInput("3/4\"/ft", allowNegative = false, allowFraction = true, allowColon = true)
        assertEquals("3/4", numeric)
        assertNull(parseTaperRateText(numeric, allowAmbiguousBareOne = false))
    }

    @Test
    fun `filterTaperRateInput drops characters no rate spelling uses`() {
        assertEquals("1:12", filterTaperRateInput("1:12x"))
        assertEquals("3/4\"/ft", filterTaperRateInput("3/4\"/ft!"))
        assertEquals("0.0625", filterTaperRateInput("-0.0625"))
    }
}