package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.undercutSections
import com.android.shaftschematic.model.Undercut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Undercut tab's pre-print question ([undercutPrePrintIssues]), the overlap flags on its list
 * ([undercutOverlapPartners], [undercutOverlapRowText]) and the "Sections to print" caption when the
 * page has no room for every ticked section ([undercutPrintSectionsCaption]) — pure, no Compose.
 */
class UndercutPrePrintIssuesTest {

    private val oal = 1000f

    /** A plain Ø100 shaft, 0 → [oal]. */
    private val segs = listOf(SurfaceSeg(0f, oal, 100f, 100f))

    private fun cut(id: String, start: Float, len: Float, dia: Float) =
        Undercut(id = id, startFromAftMm = start, lengthMm = len, diaMm = dia)

    @Test
    fun `a clean sheet asks nothing`() {
        val cuts = listOf(cut("a", 100f, 50f, 90f), cut("b", 150f, 50f, 92f), cut("c", 400f, 20f, 95f))
        assertEquals(
            emptyList<UndercutPrintIssue>(),
            undercutPrePrintIssues(cuts, oal, segs, emptySet(), emptyList(), blankDraft = false),
        )
    }

    @Test
    fun `every kind of problem is one counted line, in order`() {
        val cuts = listOf(
            cut("a", 100f, 50f, 0f), // no Ø
            cut("b", 120f, 50f, 90f), // overlaps a
            cut("c", 950f, 100f, 90f), // runs past the end
            cut("d", 1200f, 20f, 90f), // wholly off the shaft
            cut("e", 500f, 20f, 100f), // Ø at the surface
        )
        val issues = undercutPrePrintIssues(
            cuts, oal, segs, looseCutIds = setOf("d"),
            overflowSectionLabels = listOf("FWD Liner Ø10.000\""), blankDraft = false,
        )
        assertEquals(
            listOf(
                UndercutPrintIssueKind.NO_DIA,
                UndercutPrintIssueKind.OVERLAP,
                UndercutPrintIssueKind.PAST_END,
                UndercutPrintIssueKind.OFF_SHAFT,
                UndercutPrintIssueKind.DIA_AT_SURFACE,
                UndercutPrintIssueKind.OVERFLOW,
            ),
            issues.map { it.kind },
        )
        assertEquals(
            listOf(
                "1 cut has no diameter and will print without one.",
                "2 cuts overlap another cut — a printed length will be short.",
                "1 cut runs past the end of the shaft.",
                "1 cut is not on the shaft and will not print.",
                "1 cut's Ø is not below the surface it is cut into.",
                "FWD Liner Ø10.000\" will not fit on the page.",
            ),
            issues.map { it.text },
        )
        assertEquals("undercut_print_issue_overlap", issues[1].testTag)
        assertEquals("Print with these problems?", undercutPrintAskTitle(issues, export = false))
        assertEquals("Export with these problems?", undercutPrintAskTitle(issues, export = true))
    }

    @Test
    fun `counts read in the plural`() {
        val cuts = listOf(
            cut("a", 100f, 50f, 0f),
            cut("b", 300f, 50f, 0f),
            cut("c", 950f, 100f, 101f),
            cut("d", 500f, 10f, 100f),
        )
        val issues = undercutPrePrintIssues(
            cuts, oal, segs, looseCutIds = emptySet(),
            overflowSectionLabels = listOf("A", "B", "C"), blankDraft = false,
        ).associate { it.kind to it.text }
        assertEquals("2 cuts have no diameter and will print without one.", issues[UndercutPrintIssueKind.NO_DIA])
        assertEquals("1 cut runs past the end of the shaft.", issues[UndercutPrintIssueKind.PAST_END])
        assertEquals(
            "2 cuts have a Ø that is not below the surface they are cut into.",
            issues[UndercutPrintIssueKind.DIA_AT_SURFACE],
        )
        assertEquals("A, B and C will not fit on the page.", issues[UndercutPrintIssueKind.OVERFLOW])
    }

    @Test
    fun `a blank draft is never asked about`() {
        val cuts = listOf(cut("a", 100f, 50f, 0f), cut("b", 120f, 50f, 0f))
        assertEquals(
            emptyList<UndercutPrintIssue>(),
            undercutPrePrintIssues(cuts, oal, segs, setOf("a"), listOf("FWD Liner"), blankDraft = true),
        )
    }

    @Test
    fun `missing diameters alone keep the old question's title`() {
        val issues = undercutPrePrintIssues(
            listOf(cut("a", 100f, 50f, 0f)), oal, segs, emptySet(), emptyList(), blankDraft = false,
        )
        assertEquals(listOf(UndercutPrintIssueKind.NO_DIA), issues.map { it.kind })
        assertEquals("Print without diameters?", undercutPrintAskTitle(issues, export = false))
        assertEquals("Print without diameters?", undercutPrintAskTitle(issues, export = true))
    }

    @Test
    fun `a partial overlap pairs both cuts, nested and butted cuts are not overlaps`() {
        val pair = listOf(cut("a", 100f, 50f, 90f), cut("b", 120f, 50f, 90f))
        assertEquals(mapOf("a" to "b", "b" to "a"), undercutOverlapPartners(pair, oal))

        val duplicate = listOf(cut("a", 100f, 50f, 90f), cut("b", 100f, 50f, 80f))
        assertEquals(setOf("a", "b"), undercutOverlapPartners(duplicate, oal).keys)

        val nested = listOf(cut("p", 100f, 200f, 90f), cut("c", 150f, 50f, 80f))
        assertTrue(undercutOverlapPartners(nested, oal).isEmpty())

        val butted = listOf(cut("a", 100f, 50f, 90f), cut("b", 150f, 50f, 90f))
        assertTrue(undercutOverlapPartners(butted, oal).isEmpty())
    }

    @Test
    fun `the row names the partner by its number in the section, or by the other section`() {
        val a = cut("a", 120f, 50f, 90f)
        val b = cut("b", 100f, 50f, 90f)
        val c = cut("c", 600f, 50f, 90f)
        val sections = undercutSections(listOf(a, b, c), listOf(listOf("a", "b"), listOf("c")))
        val titles = listOf("AFT Liner", "FWD Liner")
        // AFT → FWD within the section: b is cut 1, a is cut 2.
        assertEquals("Overlaps cut 1", undercutOverlapRowText("a", "b", sections, titles))
        assertEquals("Overlaps cut 2", undercutOverlapRowText("b", "a", sections, titles))
        assertEquals("Overlaps a cut in FWD Liner", undercutOverlapRowText("a", "c", sections, titles))
    }

    @Test
    fun `the sections caption names what will not fit on the page`() {
        assertEquals("2 of 3 sections print. Saved with this job.", undercutPrintSectionsCaption(2, 3))
        assertEquals(
            "4 of 5 sections fit on the page — FWD Liner will not print. Saved with this job.",
            undercutPrintSectionsCaption(5, 5, listOf("FWD Liner")),
        )
        assertEquals(
            "6 of 7 sections ticked, 4 fit on the page — Mid Liner and FWD Liner will not print. Saved with this job.",
            undercutPrintSectionsCaption(6, 7, listOf("Mid Liner", "FWD Liner")),
        )
    }
}
