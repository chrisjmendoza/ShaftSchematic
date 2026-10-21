package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.util.PdfPageOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The undercut sheet's page shape: the AUTO rule (one section landscape, two or more portrait),
 * the forced shapes, the portrait per-page cap, and the strip cells each shape lays out — portrait
 * one full-width column, landscape exactly the grid / single-strip banding it always printed.
 */
class UndercutPageOrientationTest {

    // Page geometry of the two US Letter shapes at the composer's 36 pt margin: the strip band
    // runs from under the direction row to above the notes row.
    private val margin = 36f
    private val portraitLeft = margin
    private val portraitRight = 612f - margin
    private val portraitBandTop = 110f
    private val portraitBandBottom = 704f
    private val landscapeLeft = margin
    private val landscapeRight = 792f - margin
    private val landscapeBandTop = 110f
    private val landscapeBandBottom = 524f

    @Test
    fun `AUTO resolves by section count`() {
        assertEquals(PdfPageOrientation.LANDSCAPE, resolveUndercutPageOrientation(UndercutPageOrientation.AUTO, 0))
        assertEquals(PdfPageOrientation.LANDSCAPE, resolveUndercutPageOrientation(UndercutPageOrientation.AUTO, 1))
        assertEquals(PdfPageOrientation.PORTRAIT, resolveUndercutPageOrientation(UndercutPageOrientation.AUTO, 2))
        assertEquals(PdfPageOrientation.PORTRAIT, resolveUndercutPageOrientation(UndercutPageOrientation.AUTO, 5))
    }

    @Test
    fun `a forced shape wins outright`() {
        assertEquals(PdfPageOrientation.LANDSCAPE, resolveUndercutPageOrientation(UndercutPageOrientation.LANDSCAPE, 3))
        assertEquals(PdfPageOrientation.PORTRAIT, resolveUndercutPageOrientation(UndercutPageOrientation.PORTRAIT, 1))
        assertEquals(PdfPageOrientation.PORTRAIT, resolveUndercutPageOrientation(UndercutPageOrientation.PORTRAIT, 0))
    }

    @Test
    fun `page sizes are US Letter in both shapes`() {
        assertEquals(792, PdfPageOrientation.LANDSCAPE.widthPt)
        assertEquals(612, PdfPageOrientation.LANDSCAPE.heightPt)
        assertEquals(612, PdfPageOrientation.PORTRAIT.widthPt)
        assertEquals(792, PdfPageOrientation.PORTRAIT.heightPt)
    }

    @Test
    fun `portrait caps at the landscape grid's count`() {
        assertEquals(WEAR_STRIP_GRID_MAX_PER_PAGE, UNDERCUT_PORTRAIT_MAX_PER_PAGE)
        for (n in 1..7) {
            val mode = determineUndercutPdfMode(n)
            assertTrue(
                "portrait never prints fewer strips than landscape (n=$n)",
                undercutStripsPerPage(mode, portrait = true) >= minOf(n, undercutStripsPerPage(mode)),
            )
        }
        assertEquals(4, undercutStripsPerPage(determineUndercutPdfMode(5), portrait = true))
        // Landscape caps are untouched.
        assertEquals(WEAR_STRIP_MAX_PER_PAGE, undercutStripsPerPage(WearPdfMode.COMBINED))
        assertEquals(WEAR_STRIP_GRID_MAX_PER_PAGE, undercutStripsPerPage(WearPdfMode.GRID))
    }

    @Test
    fun `portrait stacks every strip full width, top to bottom, inside the band`() {
        for (n in 1..4) {
            val cells = undercutStripCells(
                portrait = true, stripCount = n,
                bandTop = portraitBandTop, bandBottom = portraitBandBottom,
                contentLeft = portraitLeft, contentRight = portraitRight,
            )
            assertEquals(n, cells.size)
            cells.forEach { c ->
                assertEquals("full content width (n=$n)", portraitLeft, c.left, 0f)
                assertEquals("full content width (n=$n)", portraitRight, c.right, 0f)
                assertTrue("inside the band (n=$n)", c.top >= portraitBandTop - 1e-3f && c.bottom <= portraitBandBottom + 1e-3f)
                assertTrue("a real strip (n=$n)", c.bottom > c.top)
            }
            cells.zipWithNext().forEach { (a, b) ->
                assertTrue("strictly top to bottom (n=$n)", b.top > a.bottom - 1e-3f && b.top > a.top)
            }
        }
    }

    @Test
    fun `a five-strip portrait sheet places four and reserves the overflow note`() {
        val n = 5
        val perPage = undercutStripsPerPage(determineUndercutPdfMode(n), portrait = true)
        assertEquals(4, perPage)
        assertEquals(1, n - perPage)
        val reserved = 16f
        val cells = undercutStripCells(
            portrait = true, stripCount = perPage,
            bandTop = portraitBandTop, bandBottom = portraitBandBottom,
            contentLeft = portraitLeft, contentRight = portraitRight,
            reservedBottomPt = reserved,
        )
        assertEquals(4, cells.size)
        assertTrue("the note's band stays free", cells.last().bottom <= portraitBandBottom - reserved + 1e-3f)
    }

    @Test
    fun `landscape cells are exactly the banding the sheet always printed`() {
        // One strip: the single full-width strip.
        val v = computeWearVerticalLayout(
            landscapeBandTop, landscapeBandBottom, 1,
            reservedBottomPt = 0f,
            minProfileHeightPt = 0f, preferredProfileHeightPt = 0f, profileToStripsGapPt = 0f,
        )
        assertEquals(
            listOf(WearStripCell(v.stripTops[0], v.stripBottoms[0], landscapeLeft, landscapeRight)),
            undercutStripCells(false, 1, landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight),
        )
        // Two or more: the 2-column grid, with and without an overflow reservation.
        for (n in 2..4) {
            for (reserved in listOf(0f, 16f)) {
                val grid = computeWearStripGridLayout(
                    landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight, n,
                    reservedBottomPt = reserved,
                    minProfileHeightPt = 0f, preferredProfileHeightPt = 0f, profileToStripsGapPt = 0f,
                )
                assertEquals(
                    "n=$n reserved=$reserved",
                    grid.cells,
                    undercutStripCells(
                        false, n, landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight,
                        reservedBottomPt = reserved,
                    ),
                )
            }
        }
        assertTrue(undercutStripCells(false, 0, landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight).isEmpty())
        assertTrue(undercutStripCells(true, 0, portraitBandTop, portraitBandBottom, portraitLeft, portraitRight).isEmpty())
        // Landscape ignores the portrait overheads.
        assertEquals(
            undercutStripCells(false, 3, landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight),
            undercutStripCells(
                false, 3, landscapeBandTop, landscapeBandBottom, landscapeLeft, landscapeRight,
                overheadsPt = listOf(120f, 40f, 120f),
            ),
        )
    }

    @Test
    fun `a roomy portrait page sizes each strip to what it draws and spreads the rest between`() {
        val overheads = listOf(60f, 120f)
        val cells = undercutStripCells(
            true, 2, portraitBandTop, portraitBandBottom, portraitLeft, portraitRight,
            overheadsPt = overheads,
        )
        assertEquals(2, cells.size)
        // ONE shaft height down the page — the smaller strip's natural cylinder — and no strip
        // taller than overhead + that, so nothing is left to spend between a figure and its title.
        val cyl = undercutNaturalCylinderPt(60f)
        cells.zip(overheads).forEach { (c, o) -> assertEquals(o + cyl, c.bottom - c.top, 1e-3f) }
        // The leftover is air spread evenly: above the first strip, between them (on top of the
        // normal gap) and below the last, all equal.
        val above = cells[0].top - portraitBandTop
        val between = cells[1].top - cells[0].bottom - WEAR_STRIP_GAP_PT
        val below = portraitBandBottom - cells[1].bottom
        assertTrue("a roomy page has air to spread", above > 0f)
        assertEquals(above, between, 1e-3f)
        assertEquals(above, below, 1e-3f)
        cells.forEach { assertEquals(portraitLeft, it.left, 0f); assertEquals(portraitRight, it.right, 0f) }
    }

    @Test
    fun `a lone forced-portrait strip is centred at its natural height`() {
        val cells = undercutStripCells(
            true, 1, portraitBandTop, portraitBandBottom, portraitLeft, portraitRight,
            overheadsPt = listOf(100f),
        )
        val c = cells.single()
        assertEquals(100f + undercutNaturalCylinderPt(100f), c.bottom - c.top, 1e-3f)
        assertEquals(c.top - portraitBandTop, portraitBandBottom - c.bottom, 1e-3f)
    }

    @Test
    fun `the natural cylinder satisfies the strip layout's own cap`() {
        // Floor, fraction and absolute branches: a strip given overhead + natural cylinder must
        // come out of computeUndercutStripInnerLayout's cap with no surplus to spend.
        listOf(20f, 100f, 200f, 400f).forEach { o ->
            val c = undercutNaturalCylinderPt(o)
            val h = o + c
            val cap = maxOf(
                UNDERCUT_CYL_MAX_FLOOR_PT,
                minOf(h * UNDERCUT_CYL_MAX_HEIGHT_FRAC, UNDERCUT_CYL_MAX_ABS_PT),
            )
            assertTrue("overhead $o: cylinder $c over its cap $cap", c <= cap + 1e-3f)
        }
        assertEquals(UNDERCUT_CYL_MAX_FLOOR_PT, undercutNaturalCylinderPt(20f), 1e-3f)
        assertEquals(UNDERCUT_CYL_MAX_ABS_PT, undercutNaturalCylinderPt(400f), 1e-3f)
    }

    @Test
    fun `a crowded portrait page falls through to the shared-height split`() {
        // Natural heights (216 + 146 + 211 + 146 + gaps) overflow the band, so no natural cells.
        val usable = portraitBandBottom - portraitBandTop
        assertEquals(null, undercutPortraitNaturalCells(usable, listOf(120f, 50f, 115f, 50f)))
    }

    @Test
    fun `a squeezed portrait page gives every strip the same shaft height`() {
        val overheads = listOf(120f, 50f, 115f, 50f)
        val cells = undercutStripCells(
            true, 4, portraitBandTop, portraitBandBottom, portraitLeft, portraitRight,
            overheadsPt = overheads,
        )
        assertEquals(4, cells.size)
        val cyl = cells.zip(overheads).map { (c, o) -> (c.bottom - c.top) - o }
        cyl.forEach { assertEquals(cyl[0], it, 1e-3f) }
        // Never below the most-squeezed strip of the even split.
        val even = undercutStripCells(true, 4, portraitBandTop, portraitBandBottom, portraitLeft, portraitRight)
        val evenMinCyl = even.zip(overheads).minOf { (c, o) -> (c.bottom - c.top) - o }
        assertTrue(cyl[0] > evenMinCyl)
        // Same band, same gaps, same order, full width.
        assertEquals(portraitBandTop, cells.first().top, 1e-3f)
        assertEquals(portraitBandBottom, cells.last().bottom, 1e-3f)
        cells.zipWithNext().forEach { (a, b) -> assertEquals(WEAR_STRIP_GAP_PT, b.top - a.bottom, 1e-3f) }
        cells.forEach { assertEquals(portraitLeft, it.left, 0f); assertEquals(portraitRight, it.right, 0f) }
    }

    @Test
    fun `equal-shaft heights fall back when there is nothing to share`() {
        assertEquals(null, undercutPortraitStripHeights(500f, listOf(100f)))
        assertEquals(null, undercutPortraitStripHeights(200f, listOf(150f, 150f)))
        assertEquals(null, undercutPortraitStripHeights(600f, listOf(50f, 60f)))
        assertEquals(listOf(130f, 70f), undercutPortraitStripHeights(200f, listOf(90f, 30f)))
    }
}
