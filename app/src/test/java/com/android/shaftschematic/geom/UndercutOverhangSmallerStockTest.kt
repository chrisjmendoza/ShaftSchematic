package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cut typed a little past a liner's end onto SMALLER stock — the on-device case: the Pacific
 * Warrior STBD aft liner (8.999" OD, 18 5/16" → 54 7/16" from the AFT S.E.T.) with the foreman's
 * three cuts, the last 8.920" × 20 5/16" ending at 54 1/2" — 1/16" onto 8 1/2" bare shaft. The cut
 * removes nothing on the bare shaft (its floor stands above it), so it is a cut, the liner's FWD
 * edge still stands, and it counts toward the sheet's depth like any other.
 */
class UndercutOverhangSmallerStockTest {

    private val inch = 25.4f
    private val oalMm = 348.75f * inch
    private val linerStart = 18.3125f * inch
    private val linerEnd = 54.4375f * inch
    private val segs = listOf(
        SurfaceSeg(0f, linerStart, 7.5f * inch, 9.0f * inch),        // AFT taper (stand-in)
        SurfaceSeg(linerStart, linerEnd, 8.999f * inch, 8.999f * inch), // AFT liner
        SurfaceSeg(linerEnd, oalMm, 8.5f * inch, 8.5f * inch),        // bare shaft
    )

    private fun cut(id: String, startIn: Float, lenIn: Float, diaIn: Float) =
        Undercut(id = id, startFromAftMm = startIn * inch, lengthMm = lenIn * inch, diaMm = diaIn * inch)

    private val cut1 = cut("c1", 18.9375f, 10.5625f, 8.915f)
    private val cut2 = cut("c2", 29.5f, 4.6875f, 8.780f)
    private val cut3 = cut("c3", 34.1875f, 20.3125f, 8.920f)   // ends at 54 1/2"
    private val sheet = listOf(cut1, cut2, cut3)

    @Test
    fun `the overhanging cut is checked against the liner it sits on, not the bare shaft`() {
        val floors = resolveUndercutFloors(sheet, segs, oalMm, exaggerationFrac = 0f)
        val f3 = floors.single { it.span.id == "c3" }
        assertEquals(8.999f * inch, f3.highestSurfaceDiaMm, 0.01f)
        assertTrue("8.920 is below the liner it cuts", cut3.diaMm < f3.highestSurfaceDiaMm)
        // The minimum is still the bare shaft — what the old warning wrongly compared against.
        assertEquals(8.5f * inch, f3.surfaceDiaMm, 0.01f)
    }

    @Test
    fun `a cut that removes nothing anywhere still trips the check`() {
        val tooBig = cut("big", 40f, 20f, 9.1f)
        val f = resolveUndercutFloors(listOf(tooBig), segs, oalMm, exaggerationFrac = 0f).single()
        assertTrue(tooBig.diaMm >= f.highestSurfaceDiaMm)
    }

    @Test
    fun `the machined span stops at the liner end where the floor stands above the bare shaft`() {
        val machined = undercutMachinedSpans(sheet, segs, oalMm).associateBy { it.id }
        assertEquals(linerEnd, machined.getValue("c3").endMm, 0.01f)
        assertEquals(34.1875f * inch, machined.getValue("c3").startMm, 0.01f)
        // A cut wholly on the liner is unchanged.
        assertEquals(cut1.startFromAftMm, machined.getValue("c1").startMm, 0.01f)
        assertEquals(cut1.startFromAftMm + cut1.lengthMm, machined.getValue("c1").endMm, 0.01f)
    }

    @Test
    fun `the liner's FWD edge still stands under a cut that only overhangs it on paper`() {
        assertFalse(undercutLinerEdgeBuried(linerEnd, undercutMachinedSpans(sheet, segs, oalMm)))
        // The typed span alone says buried — the bug.
        val typed = sheet.map { UndercutSpanMm(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        assertTrue(undercutLinerEdgeBuried(linerEnd, typed))
    }

    @Test
    fun `a cut that does cut into the bare shaft still buries the edge`() {
        // The mid-section layout: a cut below the bare shaft's Ø running across the liner end.
        val deep = cut("deep", 50f, 7f, 8.445f)
        assertTrue(undercutLinerEdgeBuried(linerEnd, undercutMachinedSpans(listOf(deep), segs, oalMm)))
    }

    @Test
    fun `an unmeasured cut keeps its whole span and a cut that removes nothing is dropped`() {
        val placeholder = cut("p", 50f, 7f, 0f)
        val none = cut("n", 60f, 5f, 8.8f)   // on 8 1/2" bare shaft only
        val machined = undercutMachinedSpans(listOf(placeholder, none), segs, oalMm)
        assertEquals(listOf("p"), machined.map { it.id })
        assertEquals(57f * inch, machined.single().endMm, 0.01f)
    }

    @Test
    fun `the overhanging cut counts toward the sheet's deepest cut`() {
        val alone = deepestUndercutDepthMm(listOf(cut3), segs, oalMm)
        assertEquals((8.999f - 8.920f) * inch, alone, 0.01f)
    }
}
