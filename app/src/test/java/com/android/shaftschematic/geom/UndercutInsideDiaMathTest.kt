package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fit rule for a Ø value written inside the shaft on the printed undercut sheet
 * (`PdfPrefs.undercutDiaInside`): across the piece it needs its text height plus a side pad each
 * side; up the floor it needs its text width, the break pad each end, an arrowhead on each floor
 * line and a minimum stub past each head.
 */
class UndercutInsideDiaMathTest {

    private val textW = 30f
    private val textH = 10f
    private val arrow = 4f
    private val eps = 1e-4f

    /** 30 + 2·3 + 2·4 + 2·2 = 48. */
    private val needH = textW + 2f * UNDERCUT_INSIDE_DIA_TEXT_PAD_PT + 2f * arrow + 2f * UNDERCUT_INSIDE_DIA_MIN_STUB_PT

    /** 10 + 2·2 = 14. */
    private val needW = textH + 2f * UNDERCUT_INSIDE_DIA_SIDE_PAD_PT

    @Test
    fun `the needed floor height is the value, its break pads, two heads and two stubs`() {
        assertEquals(needH, undercutInsideDiaNeedHeightPt(textW, arrow), eps)
    }

    @Test
    fun `a value fits a piece and floor with room on both axes`() {
        assertTrue(undercutInsideDiaFits(pieceWidthPt = 40f, floorHeightPt = 80f, textW, textH, arrow))
        assertEquals(20f + 20f, undercutInsideDiaCentreXPt(listOf(20f to 60f), 80f, textW, textH, arrow)!!, eps)
    }

    @Test
    fun `exactly the needed room fits`() {
        assertTrue(undercutInsideDiaFits(needW, needH, textW, textH, arrow))
    }

    @Test
    fun `a piece too narrow for the rotated text does not fit`() {
        assertFalse(undercutInsideDiaFitsWidth(needW - 0.5f, textH))
        assertFalse(undercutInsideDiaFits(needW - 0.5f, 200f, textW, textH, arrow))
        assertNull(undercutInsideDiaCentreXPt(listOf(0f to needW - 0.5f), 200f, textW, textH, arrow))
    }

    @Test
    fun `a floor too short for the text, pads, heads and stubs does not fit`() {
        assertFalse(undercutInsideDiaFitsHeight(needH - 0.5f, textW, arrow))
        assertFalse(undercutInsideDiaFits(200f, needH - 0.5f, textW, textH, arrow))
        assertNull(undercutInsideDiaCentreXPt(listOf(0f to 200f), needH - 0.5f, textW, textH, arrow))
    }

    @Test
    fun `a larger arrowhead needs a taller floor`() {
        assertTrue(undercutInsideDiaFitsHeight(needH, textW, arrow))
        assertFalse(undercutInsideDiaFitsHeight(needH, textW, arrow + 1f))
    }

    @Test
    fun `the value sits in the widest free piece`() {
        // A parent split by a nested cut: 8 pt, 30 pt and 12 pt pieces.
        val pieces = listOf(0f to 8f, 20f to 50f, 60f to 72f)
        assertEquals(35f, undercutInsideDiaCentreXPt(pieces, 80f, textW, textH, arrow)!!, eps)
    }

    @Test
    fun `when even the widest piece is too narrow nothing fits`() {
        val pieces = listOf(0f to 8f, 20f to 30f, 60f to 72f)
        assertNull(undercutInsideDiaCentreXPt(pieces, 80f, textW, textH, arrow))
        assertNull(undercutInsideDiaCentreXPt(emptyList(), 80f, textW, textH, arrow))
    }

    @Test
    fun `the drawn floor height follows the strip's radius map`() {
        // A 96 pt shaft at a 10" largest Ø: a 7.5" floor draws 72 pt between its lines.
        assertEquals(72f, undercutInsideDiaFloorHeightPt(96f, 190.5f, 254f), eps)
        assertEquals(96f, undercutInsideDiaFloorHeightPt(96f, 300f, 254f), eps)
        assertEquals(0f, undercutInsideDiaFloorHeightPt(96f, 100f, 0f), eps)
    }
}
