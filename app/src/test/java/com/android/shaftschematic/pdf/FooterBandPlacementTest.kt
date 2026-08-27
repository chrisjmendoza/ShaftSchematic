package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [footerBandTop] — the schematic footer sits ON the bottom margin and grows upward from it, so
 * the page's slack is the air between drawing and footer rather than a dead strip below the
 * footer. Anchoring the band's TOP to the shaft instead left it floating mid-page with the waste
 * underneath (on-device report).
 */
class FooterBandPlacementTest {

    private val pageH = 612f      // US Letter, landscape height
    private val margin = 36f
    private val eps = 1e-3f

    /** Bottom of the band is always the margin, so this is what the band's height comes out as. */
    private fun bandHeight(footerBlockPt: Float, shaftBottomY: Float): Float =
        (pageH - margin) - footerBandTop(pageH, margin, footerBlockPt, shaftBottomY)

    @Test
    fun `the band sits on the bottom margin`() {
        // Shaft well clear above: the block gets exactly its own height, ending on the margin.
        assertEquals(pageH - margin - 96f, footerBandTop(pageH, margin, 96f, shaftBottomY = 300f), eps)
        assertEquals(96f, bandHeight(96f, shaftBottomY = 300f), eps)
    }

    @Test
    fun `a taller block grows UP toward the shaft, never down past the margin`() {
        val printed = footerBandTop(pageH, margin, 96f, shaftBottomY = 300f)
        val blank = footerBandTop(pageH, margin, 200f, shaftBottomY = 300f)
        assertTrue("a blank draft's taller block starts higher", blank < printed)
        // Both still end on the margin — the page's bottom edge does not move.
        assertEquals(96f, bandHeight(96f, shaftBottomY = 300f), eps)
        assertEquals(200f, bandHeight(200f, shaftBottomY = 300f), eps)
    }

    @Test
    fun `the footer moves DOWN relative to anchoring it to the shaft`() {
        // The old rule: top pinned one INFO_GAP below the shaft, bottom left floating.
        val shaftBottomY = 390f
        val oldTop = shaftBottomY + 72f
        assertTrue(
            "bottom-anchoring must not raise the footer on an ordinary sheet",
            footerBandTop(pageH, margin, 96f, shaftBottomY) > oldTop,
        )
    }

    /**
     * The shaft placement reserves the full info gap measured up from the margin, so the floor
     * is reachable only by a shaft too tall for that reservation to have held.
     */
    @Test
    fun `a shaft crowding the band still keeps clearance for a fully grown footer`() {
        val shaftBottomY = 500f      // far lower than the budget would ever place it
        val top = footerBandTop(pageH, margin, 96f, shaftBottomY)
        assertTrue("the band never overlaps the drawing", top > shaftBottomY)
        // drawFooter may grow the band up to FOOTER_GROWTH_MAX_PT above rect.top; that must
        // still land below the shaft.
        assertTrue("even a fully grown band clears the shaft", top - 48f > shaftBottomY)
    }
}
