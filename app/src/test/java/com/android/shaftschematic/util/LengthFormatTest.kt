package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LengthFormatTest {

    @Test
    fun `formatInchesSmart uses mixed fractions to 1_16`() {
        assertEquals("1 3/4", LengthFormat.formatInchesSmart(1.75))
        assertEquals("1/16", LengthFormat.formatInchesSmart(0.0625))
        assertEquals("2", LengthFormat.formatInchesSmart(2.0))
    }

    @Test
    fun `formatInchesSmart falls back to 3 decimals when not on grid`() {
        assertEquals("1.333", LengthFormat.formatInchesSmart(1.3333))
    }
}
