package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
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
}