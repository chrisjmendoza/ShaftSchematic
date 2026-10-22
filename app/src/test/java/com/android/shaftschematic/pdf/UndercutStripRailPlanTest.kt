package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UNDERCUT_SET_REACH_MM
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.undercutNestingForest
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_MEDIUM_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_SMALL_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [planUndercutStripRails] — the ONE rail plan behind the printed strip and the Undercut tab's
 * detail canvas — on the on-device job (fixture `undercut_polar_ranger_stbd.shaft`: the FWD
 * liner's five cuts, a 2 3/8" cut nested inside the 12 5/8" relief). The AFT-most cut overhangs the
 * liner's AFT edge, so the chain datum is that cut's shoulder and the chain opens on the cut itself.
 */
class UndercutStripRailPlanTest {

    private val unit = UnitSystem.INCHES

    private val doc by lazy {
        ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
        )
    }

    private val spans: List<UndercutSpanMm> by lazy {
        val oal = doc.spec.overallLengthMm
        doc.undercutRecord.undercuts.mapNotNull { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
    }

    private fun strip(reachToSetMm: Float = 0f): UndercutStrip {
        val spec = doc.spec
        val segs = surfaceSegsFrom(resolveComponents(spec))
        val liners = spec.liners
            .filter { it.lengthMm > 0f && it.odMm > 0f }
            .map { UndercutLinerSpan(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }
        val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
        val strips = buildUndercutStrips(
            spans, liners, spec.overallLengthMm, segs = segs,
            reachToSetMm = reachToSetMm,
            aftSetMm = sets.aftSETxMm.toFloat(), fwdSetMm = sets.fwdSETxMm.toFloat(),
        )
        assertEquals("the job's cuts read as one strip", 1, strips.size)
        return strips.single()
    }

    private val xAt: (Float) -> Float = { mm -> (mm - 9900f) * 0.8f }
    private val labelWidth: (DualLabel) -> Float = { it.inline().length * 4.64f }

    private fun plan(s: UndercutStrip, scale: Float = 1f, x: (Float) -> Float = xAt) = planUndercutStripRails(
        s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
        xAt = x, labelWidth = labelWidth, scale = scale,
    )

    @Test
    fun `the chain carries the top-level cuts, the nested cut gets its own row against its parent`() {
        val rails = plan(strip())
        assertEquals(
            listOf("1 1/4\"", "12 5/8\"", "3 11/16\"", "9 1/4\""),
            rails.chainSpans.map { it.label.inline() },
        )
        assertEquals("one nesting level", 1, rails.nestedSpans.size)
        assertEquals(
            listOf("5.080\"", "2 3/8\"", "5.170\""),
            rails.nestedSpans.single().map { it.label.inline() },
        )
        // The nested row is located against the parent's shoulders: it spans the parent exactly.
        val parent = spans.single { kotlin.math.abs(it.endMm - it.startMm - 320.675f) < 1e-2f }
        val row = rails.nestedSpans.single()
        assertEquals(parent.startMm, row.first().startMm, 1e-3f)
        assertEquals(parent.endMm, row.last().endMm, 1e-3f)
        assertNotNull("five cuts carry a total", rails.totalSpan)
        assertEquals(1, rails.total.size)
        assertEquals("a total rides above the chain, so fallbacks tuck below", 0, rails.rows.aboveRows)
        assertTrue("the nested row claims reserved rows", rails.rows.nestedRows >= 1)
    }

    @Test
    fun `with the total switched off the plan carries no total and reserves no total band`() {
        val s = strip()
        val on = plan(s)
        val off = planUndercutStripRails(
            s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = xAt, labelWidth = labelWidth, showTotal = false,
        )
        assertNull("no total span", off.totalSpan)
        assertTrue("no total layout", off.total.isEmpty())
        assertFalse("nothing to lift under a missing total", off.canLiftFallbacks)
        // Everything else on the strip is the same plan: the switch removes the total only.
        assertEquals(on.chainSpans, off.chainSpans)
        assertEquals(on.nestedSpans, off.nestedSpans)
        assertEquals(on.chain, off.chain)
        assertEquals(on.nested, off.nested)
        assertEquals(on.lead, off.lead)
        assertEquals(
            "with no total, the chain's fallbacks go above its line — the single-cut layout",
            planUndercutRailRows(
                on.chain, startedStrip = false, hasTotalRail = false, nestedLayouts = on.nested,
            ),
            off.rows,
        )
    }

    @Test
    fun `with the draw-to-S E T reach the chain closes on the run to the S E T`() {
        val rails = plan(strip(UNDERCUT_SET_REACH_MM))
        assertEquals(
            listOf("1 1/4\"", "12 5/8\"", "3 11/16\"", "9 1/4\"", "12 3/4\""),
            rails.chainSpans.map { it.label.inline() },
        )
    }

    @Test
    fun `at scale 1 and the former fixed 4 pt head the plan is the composer's former inline sequence exactly`() {
        val s = strip()
        val rails = planUndercutStripRails(
            s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = xAt, labelWidth = labelWidth, arrowSizePt = PDF_ARROW_SIZE_MEDIUM_PT,
        )
        // The composer's sequence before the planner existed, verbatim.
        val sorted = spans.sortedBy { it.startMm }
        val nestingById = undercutNestingForest(sorted).associateBy { it.id }
        val topSpans = sorted.filter { (nestingById[it.id]?.level ?: 0) == 0 }
        val railSpans = buildUndercutRailSpans(s.chainStartMm, s.chainEndMm, topSpans, unit, false, splitAtMm = emptyList())
        val totalSpan = buildUndercutTotalSpan(topSpans, unit, false)
        val nestedRows = buildNestedUndercutRailRows(sorted, unit, false)
        val chain = layoutWearStripRail(railSpans, xAtStripMm = xAt, labelWidthPt = labelWidth)
        val nested = nestedRows.map { layoutWearStripRail(it, xAtStripMm = xAt, labelWidthPt = labelWidth) }
        val total = layoutWearStripRail(listOf(totalSpan!!), xAtStripMm = xAt, labelWidthPt = labelWidth)
        val rows = planUndercutRailRows(chain, false, hasTotalRail = true, nestedLayouts = nested)

        assertEquals(railSpans, rails.chainSpans)
        assertEquals(nestedRows, rails.nestedSpans)
        assertEquals(totalSpan, rails.totalSpan)
        assertEquals(chain, rails.chain)
        assertEquals(nested, rails.nested)
        assertEquals(total, rails.total)
        assertEquals(rows, rails.rows)
    }

    // The rails follow the app-wide "Dimension arrows" pref: a value seats in its break only with
    // an arrowhead's room on each side, so the head size is a layout input, not just ink.

    /** One 40 mm cut in a 100 mm chain at 1 pt/mm, every value 26 pt wide: the cut's value has
     *  exactly 3 pt each side of its break pads. */
    private fun borderlinePlan(arrowSizePt: Float?): UndercutStripRails {
        val cut = listOf(UndercutSpanMm("c", 30f, 70f))
        return if (arrowSizePt == null) planUndercutStripRails(
            0f, 100f, cut, unit, dual = false, splitAtMm = emptyList(),
            startedStrip = false, xAt = { it }, labelWidth = { 26f },
        ) else planUndercutStripRails(
            0f, 100f, cut, unit, dual = false, splitAtMm = emptyList(),
            startedStrip = false, xAt = { it }, labelWidth = { 26f }, arrowSizePt = arrowSizePt,
        )
    }

    @Test
    fun `a larger arrowhead flips a borderline span from seated to fallback`() {
        val cut = { a: Float -> borderlinePlan(a).chain.single { it.x0Pt == 30f } }
        assertTrue("3 pt heads fit the 3 pt either side", cut(PDF_ARROW_SIZE_SMALL_PT).seatsInBreak)
        assertFalse("4 pt heads do not", cut(PDF_ARROW_SIZE_MEDIUM_PT).seatsInBreak)
        assertFalse(cut(PDF_ARROW_SIZE_LARGE_PT).seatsInBreak)
        // The fallback leaves the whole 40 pt span to the heads, so they still point inward.
        assertTrue(cut(PDF_ARROW_SIZE_LARGE_PT).arrowInward)
        // The fallback claims a row the seated value did not.
        assertTrue(
            undercutRailFallbackRows(borderlinePlan(PDF_ARROW_SIZE_LARGE_PT).chain) >=
                undercutRailFallbackRows(borderlinePlan(PDF_ARROW_SIZE_SMALL_PT).chain),
        )
    }

    @Test
    fun `with no head given the plan uses the pref's default`() {
        assertEquals(borderlinePlan(PDF_ARROW_SIZE_DEFAULT_PT), borderlinePlan(null))
        val s = strip()
        assertEquals(
            plan(s),
            planUndercutStripRails(
                s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
                xAt = xAt, labelWidth = labelWidth, arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
            ),
        )
    }

    @Test
    fun `on the job the head size changes no decision a value had room for either way`() {
        // Only borderline spans may flip: every value that seats at the largest head seats at the
        // smallest too, and nothing seated at the smallest is new room the plan could not have had.
        val s = strip()
        fun at(a: Float) = planUndercutStripRails(
            s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = xAt, labelWidth = labelWidth, arrowSizePt = a,
        )
        val small = at(PDF_ARROW_SIZE_SMALL_PT)
        val large = at(PDF_ARROW_SIZE_LARGE_PT)
        (large.chain + large.nested.flatten() + large.total)
            .zip(small.chain + small.nested.flatten() + small.total)
            .forEach { (l, sm) ->
                assertEquals(l.x0Pt, sm.x0Pt, 1e-3f)
                assertEquals(l.labelCxPt, sm.labelCxPt, 1e-3f)
                if (l.seatsInBreak) assertTrue("${l.label.inline()} seats at 5 pt but not at 3 pt", sm.seatsInBreak)
            }
    }

    @Test
    fun `a scaled plan makes the page's seating and arrow decisions at its own size`() {
        val k = 3.3f
        val page = plan(strip())
        // Everything — the x map and every label — k times larger: the same decisions, k times the x.
        val canvas = planUndercutStripRails(
            strip().chainStartMm, strip().chainEndMm, spans, unit, false, emptyList(), false,
            xAt = { mm -> xAt(mm) * k }, labelWidth = { labelWidth(it) * k }, scale = k,
        )
        assertEquals(page.rows, canvas.rows)
        (page.chain + page.nested.flatten() + page.total).zip(canvas.chain + canvas.nested.flatten() + canvas.total)
            .forEach { (p, c) ->
                assertEquals(p.seatsInBreak, c.seatsInBreak)
                assertEquals(p.arrowInward, c.arrowInward)
                assertEquals(p.labelRow, c.labelRow)
                assertEquals(p.labelCxPt * k, c.labelCxPt, 1e-2f)
            }
    }

    @Test
    fun `the nested row's fallback value sits above its own line, under the chain's labels`() {
        // A tighter x map than [xAt] — the on-device width, where the parent's 2 3/8" middle span
        // is too short to seat its value in a break.
        val rails = plan(strip(), x = { mm -> (mm - 9900f) * 0.6f })
        val nested = rails.nested.single()
        val short = nested.single { it.label.inline() == "2 3/8\"" }
        assertFalse("the 2 3/8\" span falls back off its line", short.seatsInBreak)

        // The band is the plan's, unchanged: the chain's own rows, then the nested level's
        // fallback rows and its line.
        val chainRows = undercutRailFallbackRows(rails.chain)
        assertTrue("the chain carries fallback rows below it", chainRows >= 1)
        val nestedFallbackRows = undercutRailFallbackRows(nested)
        assertEquals(1 + nestedFallbackRows, rails.rows.nestedRows)
        assertEquals(chainRows + rails.rows.nestedRows, rails.rows.belowRows)

        // The detail canvas's vertical split: chain line, belowRows, headroom, cylinder top.
        val step = UNDERCUT_RAIL_ROW_HEIGHT_PT
        val chainY = 100f
        val cylTop = chainY + rails.rows.belowRows * step + 6f
        val witnessBottom = cylTop - UNDERCUT_RAIL_WITNESS_GAP_PT
        val lineY = undercutNestedRailLineYs(rails, chainY, rails.rows.belowRows, step, witnessBottom).single()
        assertEquals("the nested line takes the band's last row", chainY + rails.rows.belowRows * step, lineY, 1e-3f)
        assertTrue("the line stays above the witness start", lineY <= witnessBottom)

        // An 8 pt label face: ascent 7.4, descent 2.
        val textSize = 8f
        val ascent = 7.4f
        val descent = 2f
        val baseline = undercutRailFallbackBaselineY(lineY, short.labelRow, above = true, step, textSize, descent)
        assertTrue(
            "the value's descent clears the largest arrowheads above the line ($baseline vs $lineY)",
            baseline + descent < lineY - PDF_ARROW_SIZE_LARGE_PT * 0.5f,
        )
        val chainLowest = undercutRailFallbackBaselineY(chainY, chainRows - 1, above = false, step, textSize, descent)
        assertTrue(
            "the value sits below the chain's lowest label row ($baseline vs $chainLowest)",
            baseline - ascent > chainLowest + descent,
        )
    }

    @Test
    fun `a started strip dimensions nothing`() {
        val s = strip()
        val rails = planUndercutStripRails(
            s.chainStartMm, s.chainEndMm, spans, unit, false, emptyList(), startedStrip = true,
            xAt = xAt, labelWidth = labelWidth,
        )
        assertTrue(rails.chainSpans.isEmpty())
        assertTrue(rails.nested.isEmpty())
        assertNull(rails.totalSpan)
        assertTrue(rails.total.isEmpty())
        assertEquals(WEAR_RAIL_MAX_LABEL_ROWS, rails.rows.belowRows)
        assertFalse(rails.rows.aboveRows > 0)
    }
}
