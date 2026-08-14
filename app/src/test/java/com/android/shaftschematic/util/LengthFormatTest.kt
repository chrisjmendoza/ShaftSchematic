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

    /**
     * Plain `n/d` for EVERY denominator — the typography is the renderer's job
     * (`FractionText` / `FractionTextRenderer`). Emitting `¾` here and `11/16` there is what
     * made one sheet mix two different-looking fractions.
     */
    @Test
    fun `formatInchesSmart never emits a Unicode vulgar fraction`() {
        val samples = listOf(0.5, 0.25, 0.75, 0.125, 0.375, 0.625, 0.875, 1.0625, 2.1875)
        samples.forEach { v ->
            val s = LengthFormat.formatInchesSmart(v)
            assertEquals("$v -> $s", -1, s.indexOfFirst { it in "¼½¾⅛⅜⅝⅞" })
        }
        assertEquals("5/8", LengthFormat.formatInchesSmart(0.625))
        assertEquals("2 3/16", LengthFormat.formatInchesSmart(2.1875))
    }

    @Test
    fun `formatInchesSmart falls back to 3 decimals when not on grid`() {
        assertEquals("1.333", LengthFormat.formatInchesSmart(1.3333))
    }
}
