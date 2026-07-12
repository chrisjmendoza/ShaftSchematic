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
    fun `autoTaperRateText formats one to ten and one to twenty without corruption`() {
        // Regression: trimEnd('0') on "%.0f" output turned "10" into "1" and "20" into "2".
        assertEquals("1:10", autoTaperRateText(lengthMm = 10f, setDiaMm = 4f, letDiaMm = 5f))
        assertEquals("1:20", autoTaperRateText(lengthMm = 20f, setDiaMm = 4f, letDiaMm = 5f))
    }

    @Test
    fun `autoTaperRateText returns null when delta or length invalid`() {
        assertNull(autoTaperRateText(lengthMm = 0f, setDiaMm = 4f, letDiaMm = 5f))
        assertNull(autoTaperRateText(lengthMm = 16f, setDiaMm = 5f, letDiaMm = 5f))
    }

    @Test
    fun `autoTaperRateText returns null when a diameter is a missing sentinel`() {
        // UI layers use 0 (model default) and -1 (dialog "not provided") as sentinels;
        // a rate must never be fabricated from them.
        assertNull(autoTaperRateText(lengthMm = 300f, setDiaMm = 100f, letDiaMm = 0f))
        assertNull(autoTaperRateText(lengthMm = 300f, setDiaMm = 100f, letDiaMm = -1f))
        assertNull(autoTaperRateText(lengthMm = 300f, setDiaMm = -1f, letDiaMm = 100f))
    }

    @Test
    fun `bore preference decides between comparably close candidates`() {
        // Exact N = 13.7 sits between 1:12 and 1:16 with nearly equal errors
        // (~14.2% vs ~14.4%); a widened tolerance admits both, so the bore
        // preference decides: 1:16 at or under the 6 in break, 1:12 above.
        val small = autoTaperRateText(
            lengthMm = 13.7f, setDiaMm = 4f, letDiaMm = 5f,
            referenceDiaMm = 100f,
            commonOneToN = listOf(16f, 12f),
            maxRelativeSlopeError = 0.2f,
        )
        assertEquals("1:16", small)

        val large = autoTaperRateText(
            lengthMm = 13.7f, setDiaMm = 4f, letDiaMm = 5f,
            referenceDiaMm = 200f,
            commonOneToN = listOf(16f, 12f),
            maxRelativeSlopeError = 0.2f,
        )
        assertEquals("1:12", large)
    }

    @Test
    fun `clearly closer candidate wins over bore preference`() {
        // Exact N = 14.5: 1:16 error ~9.4%, 1:12 error ~20.8% — both within a
        // widened tolerance, but the gap exceeds the comparably-close margin,
        // so geometry wins even though the large bore prefers 1:12.
        val rate = autoTaperRateText(
            lengthMm = 14.5f, setDiaMm = 4f, letDiaMm = 5f,
            referenceDiaMm = 200f,
            commonOneToN = listOf(16f, 12f),
            maxRelativeSlopeError = 0.25f,
        )
        assertEquals("1:16", rate)
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
