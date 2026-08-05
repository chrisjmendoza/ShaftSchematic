package com.android.shaftschematic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sizing-curve anchor prefs — the user-adjustable "Default drawing size" pair. Pins the
 * standard defaults (the hand-sheet convention: 4" → 0.75", 8" → 1.25"), the pt
 * conversion the composers consume, and the clamp to the settable range.
 */
class PdfPrefsCurveTest {

    @Test
    fun `defaults are the standard hand-sheet anchors`() {
        val p = PdfPrefs()
        assertEquals(0.75f, p.curveLoHeightIn, 1e-6f)
        assertEquals(1.25f, p.curveHiHeightIn, 1e-6f)
        assertEquals(54f, p.curveLoHeightPt, 1e-4f)
        assertEquals(90f, p.curveHiHeightPt, 1e-4f)
    }

    @Test
    fun `clamped coerces anchors into the settable range`() {
        val p = PdfPrefs(curveLoHeightIn = 0.1f, curveHiHeightIn = 9f).clamped()
        assertEquals(PDF_CURVE_HEIGHT_MIN_IN, p.curveLoHeightIn, 1e-6f)
        assertEquals(PDF_CURVE_HEIGHT_MAX_IN, p.curveHiHeightIn, 1e-6f)
    }

    @Test
    fun `in-range anchors pass through clamped verbatim`() {
        val p = PdfPrefs(curveLoHeightIn = 0.5f, curveHiHeightIn = 1.0f).clamped()
        assertEquals(0.5f, p.curveLoHeightIn, 1e-6f)
        assertEquals(1.0f, p.curveHiHeightIn, 1e-6f)
    }
}
