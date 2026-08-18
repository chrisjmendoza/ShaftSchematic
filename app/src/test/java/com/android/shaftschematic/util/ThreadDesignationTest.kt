package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadDesignationTest {

    @Test
    fun `parses canonical designation`() {
        val d = ThreadDesignation.parse("M20×2.5")!!
        assertEquals(20f, d.majorDiaMm, 1e-4f)
        assertEquals(2.5f, d.pitchMm!!, 1e-4f)
    }

    @Test
    fun `accepts ascii x and star separators`() {
        assertEquals(2.5f, ThreadDesignation.parse("M20x2.5")!!.pitchMm!!, 1e-4f)
        assertEquals(2.5f, ThreadDesignation.parse("M20X2.5")!!.pitchMm!!, 1e-4f)
        assertEquals(2.5f, ThreadDesignation.parse("M20*2.5")!!.pitchMm!!, 1e-4f)
    }

    @Test
    fun `tolerates whitespace and lowercase m`() {
        val d = ThreadDesignation.parse("  m20 × 2.5 ")!!
        assertEquals(20f, d.majorDiaMm, 1e-4f)
        assertEquals(2.5f, d.pitchMm!!, 1e-4f)
    }

    @Test
    fun `coarse designation without pitch parses with null pitch`() {
        val d = ThreadDesignation.parse("M20")!!
        assertEquals(20f, d.majorDiaMm, 1e-4f)
        assertNull(d.pitchMm)
    }

    @Test
    fun `rejects non-metric and malformed input`() {
        assertNull(ThreadDesignation.parse(null))
        assertNull(ThreadDesignation.parse(""))
        assertNull(ThreadDesignation.parse("20×2.5"))   // missing M
        assertNull(ThreadDesignation.parse("M"))          // no diameter
        assertNull(ThreadDesignation.parse("Mabc"))       // non-numeric diameter
        assertNull(ThreadDesignation.parse("M0×2.5"))     // non-positive diameter
        assertNull(ThreadDesignation.parse("M20×0"))      // non-positive pitch
        assertNull(ThreadDesignation.parse("M20×abc"))    // non-numeric pitch
    }

    @Test
    fun `format trims trailing zeros and round-trips`() {
        assertEquals("M20×2.5", ThreadDesignation.format(20f, 2.5f))
        assertEquals("M20×1", ThreadDesignation.format(20f, 1f))
        assertEquals("M20", ThreadDesignation.format(20f, null))
        val round = ThreadDesignation.parse("M12×1.75")!!
        assertEquals("M12×1.75", round.format())
    }
}
