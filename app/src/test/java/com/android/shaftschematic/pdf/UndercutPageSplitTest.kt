package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [splitUndercutStripsForPage] — the ONE answer to which printed strips are on the undercut page
 * and which overflow into its "+N more" note, read by the composer's own cut and by the Undercut
 * tab's caption, list rows and pre-print question.
 */
class UndercutPageSplitTest {

    private fun strips(n: Int): List<UndercutStrip> = (0 until n).map { i ->
        UndercutStrip.FreeStrip(UndercutWindow(i * 1000f, i * 1000f + 100f, listOf("cut$i")))
    }

    @Test
    fun `no strips put nothing on the page and nothing past it`() {
        val split = splitUndercutStripsForPage(emptyList(), portrait = false)
        assertEquals(emptyList<UndercutStrip>(), split.onPage)
        assertEquals(emptyList<UndercutStrip>(), split.overflow)
    }

    @Test
    fun `up to the cap every strip is on the page, either shape`() {
        for (n in 1..UNDERCUT_PORTRAIT_MAX_PER_PAGE) {
            for (portrait in listOf(false, true)) {
                val all = strips(n)
                val split = splitUndercutStripsForPage(all, portrait)
                assertEquals("n=$n portrait=$portrait", all, split.onPage)
                assertEquals("n=$n portrait=$portrait", emptyList<UndercutStrip>(), split.overflow)
            }
        }
    }

    @Test
    fun `past the cap the FWD-most strips overflow, in order`() {
        val all = strips(6)
        for (portrait in listOf(false, true)) {
            val cap = undercutStripsPerPage(determineUndercutPdfMode(all.size), portrait)
            val split = splitUndercutStripsForPage(all, portrait)
            assertEquals(all.take(cap), split.onPage)
            assertEquals(all.drop(cap), split.overflow)
            assertEquals(all, split.onPage + split.overflow)
        }
    }

    @Test
    fun `five strips leave exactly one off the page`() {
        val all = strips(5)
        val split = splitUndercutStripsForPage(all, portrait = true)
        assertEquals(4, split.onPage.size)
        assertEquals(listOf(all.last()), split.overflow)
    }
}
