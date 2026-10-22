package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cut numbers count within each section (on-device ruling: "the numbers need to match within
 * view"), so the detail page's drawing, its card titles and the tab list's rows under that
 * section's heading all read 1..N.
 */
class UndercutNumberingTest {

    private fun cut(id: String, startMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = 10f, diaMm = 200f)

    @Test
    fun `each section counts from 1 — the on-device job, 3 cuts aft and 6 in the FWD liner`() {
        val aft = listOf(cut("a1", 100f), cut("a2", 140f), cut("a3", 180f))
        val fwd = listOf(cut("f1", 900f), cut("f2", 930f), cut("f3", 960f),
            cut("f4", 970f), cut("f5", 1100f), cut("f6", 1200f))
        val sections = undercutSections(aft + fwd, listOf(aft.map { it.id }, fwd.map { it.id }))

        assertEquals(listOf(0, 1), sections.map { it.sectionIndex })
        assertEquals(mapOf("a1" to 1, "a2" to 2, "a3" to 3), sections[0].numbers)
        assertEquals((1..6).toList(), fwd.map { sections[1].numbers.getValue(it.id) })
    }

    @Test
    fun `a section numbers AFT to FWD by stored start, whatever the record or member order`() {
        val cuts = listOf(cut("c", 300f), cut("a", 100f), cut("b", 200f))
        val section = undercutSections(cuts, listOf(listOf("c", "b", "a"))).single()

        assertEquals(listOf("a", "b", "c"), section.cuts.map { it.id })
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), section.numbers)
    }

    @Test
    fun `ties keep the section's member order — the detail page's page order`() {
        val cuts = listOf(cut("x", 100f), cut("y", 100f))
        assertEquals(listOf("y", "x"), undercutSectionCuts(cuts, listOf("y", "x")).map { it.id })
    }

    @Test
    fun `a cut no section holds is still numbered, in a trailing group`() {
        val cuts = listOf(cut("in", 100f), cut("off2", 5000f), cut("off1", 4000f))
        val sections = undercutSections(cuts, listOf(listOf("in")))

        assertEquals(2, sections.size)
        assertNull(sections[1].sectionIndex)
        assertEquals(mapOf("off1" to 1, "off2" to 2), sections[1].numbers)
    }

    @Test
    fun `ids the record no longer holds drop out, and nothing loose means no trailing group`() {
        val sections = undercutSections(listOf(cut("a", 100f)), listOf(listOf("a", "gone", "a")))

        assertEquals(1, sections.size)
        assertEquals(mapOf("a" to 1), sections.single().numbers)
    }
}
