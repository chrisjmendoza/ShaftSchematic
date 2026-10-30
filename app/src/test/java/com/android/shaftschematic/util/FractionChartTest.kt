package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fraction chart's rows: reduced fractions, exact decimals, every interval up to 1". */
class FractionChartTest {

    @Test
    fun `each interval runs from its first step up to one inch`() {
        for (i in FRACTION_CHART_INTERVALS) {
            val rows = fractionChartRows(i)
            assertEquals(i, rows.size)
            assertEquals("1/$i", rows.first().fraction)
            assertEquals("1", rows.last().fraction)
        }
    }

    @Test
    fun `fractions are reduced`() {
        val rows = fractionChartRows(32).associateBy { it.fraction }
        assertTrue("16/32 reads 1/2", "1/2" in rows)
        assertTrue("11/32 stays", "11/32" in rows)
        assertEquals(32, rows.size)
    }

    @Test
    fun `decimals are exact and carry the interval's places`() {
        assertEquals("0.125", fractionChartRows(8).first().decimal)
        assertEquals("0.0625", fractionChartRows(16).first().decimal)
        assertEquals("0.03125", fractionChartRows(32).first().decimal)
        assertEquals("0.015625", fractionChartRows(64).first().decimal)
        val r64 = fractionChartRows(64)
        assertEquals("0.500000", r64.single { it.fraction == "1/2" }.decimal)
        assertEquals("0.984375", r64.single { it.fraction == "63/64" }.decimal)
        assertEquals("1.000000", r64.last().decimal)
        assertTrue("one width per column", r64.map { it.decimal.length }.toSet().size == 1)
    }

    @Test
    fun `millimetres convert to three places`() {
        assertEquals("0.397", fractionChartRows(64).first().millimeters)
        assertEquals("25.400", fractionChartRows(64).last().millimeters)
        assertEquals("8.731", fractionChartRows(32).single { it.fraction == "11/32" }.millimeters)
    }

    @Test
    fun `eighths are marked on the finer intervals only`() {
        val r32 = fractionChartRows(32)
        assertTrue(r32.single { it.fraction == "3/8" }.isMajor)
        assertTrue(r32.single { it.fraction == "1" }.isMajor)
        assertFalse(r32.single { it.fraction == "3/16" }.isMajor)
        assertFalse(r32.single { it.fraction == "11/32" }.isMajor)
        assertTrue(fractionChartRows(8).none { it.isMajor })
    }

    @Test
    fun `the chart opens on 32nds`() {
        assertEquals(32, FRACTION_CHART_DEFAULT_INTERVAL)
        assertEquals(listOf(8, 16, 32, 64), FRACTION_CHART_INTERVALS)
    }
}
