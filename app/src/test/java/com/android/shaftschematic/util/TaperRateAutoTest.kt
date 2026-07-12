package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaperRateAutoTest {

    @Test
    fun `autoTaperRateText snaps exact one to sixteen`() {
        val rate = autoTaperRateText(lengthMm = 16f, setDiaMm = 4f, letDiaMm = 5f)
        assertEquals("1:16", rate)
    }

    @Test
    fun `autoTaperRateText snaps near one to sixteen within tolerance`() {
        val rate = autoTaperRateText(lengthMm = 16.125f, setDiaMm = 4f, letDiaMm = 5f)
        assertEquals("1:16", rate)
    }

    @Test
    fun `autoTaperRateText returns exact when not near common tapers`() {
        val rate = autoTaperRateText(lengthMm = 15f, setDiaMm = 4f, letDiaMm = 5f)
        assertEquals("1:15.000", rate)
    }

    @Test
    fun `autoTaperRateText keeps three decimals for exact non-integer result`() {
        val rate = autoTaperRateText(lengthMm = 15.3f, setDiaMm = 4f, letDiaMm = 5f)
        assertEquals("1:15.300", rate)
    }

    @Test
    fun `autoTaperRateText keeps trailing zeros on exact values`() {
        val rate = autoTaperRateText(lengthMm = 15.5f, setDiaMm = 4f, letDiaMm = 5f)
        assertEquals("1:15.500", rate)
    }

    @Test
    fun `autoTaperRateText allows alternate common set`() {
        val rate = autoTaperRateText(
            lengthMm = 14f,
            setDiaMm = 4f,
            letDiaMm = 5f,
            commonOneToN = listOf(14f, 12f, 10f)
        )
        assertEquals("1:14", rate)
    }

    @Test
    fun `autoTaperRateText returns null when delta or length invalid`() {
        assertNull(autoTaperRateText(lengthMm = 0f, setDiaMm = 4f, letDiaMm = 5f))
        assertNull(autoTaperRateText(lengthMm = 16f, setDiaMm = 5f, letDiaMm = 5f))
    }

    @Test
    fun `parseTaperRateText rejects bare one in strict mode`() {
        assertNull(parseTaperRateText("1", allowAmbiguousBareOne = false))
        assertEquals(1f / 12f, parseTaperRateText("12", allowAmbiguousBareOne = false))
        assertEquals(1f, parseTaperRateText("1/1", allowAmbiguousBareOne = false))
    }

    @Test
    fun `manualTaperRateBlockingMessage rejects bare one`() {
        assertEquals(
            "Use a full ratio or fraction; `1` is ambiguous",
            manualTaperRateBlockingMessage(rateText = "1", lengthMm = 100f, setDiaMm = 100f, letDiaMm = 120f)
        )
    }

    @Test
    fun `manualTaperRateBlockingMessage requires rate when one end missing`() {
        assertEquals(
            "Enter a taper rate to derive the missing end",
            manualTaperRateBlockingMessage(rateText = "", lengthMm = 100f, setDiaMm = 100f, letDiaMm = 0f)
        )
    }

    @Test
    fun `manualTaperRateWarning flags mismatch when all geometry is present`() {
        assertEquals(
            "Rate does not match Length + SET + LET",
            manualTaperRateWarning(rateText = "1:12", lengthMm = 16f, setDiaMm = 4f, letDiaMm = 5f)
        )
    }

    @Test
    fun `manualTaperRateWarning does not flag when one end is missing`() {
        assertNull(
            manualTaperRateWarning(rateText = "1:12", lengthMm = 16f, setDiaMm = 4f, letDiaMm = 0f)
        )
    }
}
