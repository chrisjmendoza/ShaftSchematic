package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.dualStackMetrics
import com.android.shaftschematic.util.setsStacked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where a strip's nested rail rows sit inside the band [planUndercutRailRows] reserved below the
 * chain (on-device report: on a lifted strip the nested lengths printed crammed against the
 * shaft with empty air above them). The rows stack directly under the chain's OWN below-line
 * rows ([UndercutRailRowPlan.chainBelowRows]); the plan's clear-air row is the LOWEST of the band,
 * between the last nested line and the shaft ([undercutNestedRailLineYs]) — the compressed layout
 * (total rail, chain fallbacks below the line) included, so on a crowded page no nested line lands
 * on the liner's outline (on-device report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutNestedRailAirTest {

    private val step = UNDERCUT_RAIL_ROW_HEIGHT_PT
    private val chainY = 100f

    private fun span(row: Int, seated: Boolean) =
        WearRailSpanLayout(0f, 10f, DualLabel.single("x"), 5f, row, seatsInBreak = seated, arrowInward = true)

    private fun rails(chain: List<WearRailSpanLayout>, nested: List<List<WearRailSpanLayout>>, rows: UndercutRailRowPlan) =
        UndercutStripRails(
            chainSpans = emptyList(), nestedSpans = emptyList(), totalSpan = null,
            chain = chain, nested = nested, total = emptyList(), rows = rows,
        )

    /** The stepping before the clear row moved to the bottom: nested rows started under it. */
    private fun preFixLineY(r: UndercutStripRails, railLabelRows: Int, level0Fallbacks: Int): Float =
        chainY + (railLabelRows - r.rows.nestedRows).coerceAtLeast(0) * step + (1 + level0Fallbacks) * step

    // ── Row plan ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the row plan names the chain's own below-line rows in every branch`() {
        val nested = listOf(listOf(span(0, false)))
        val nestedRows = 2
        fun clear(p: UndercutRailRowPlan) = p.belowRows - p.chainBelowRows - p.nestedRows

        val lifted = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested, lifted = true)
        assertEquals(0, lifted.chainBelowRows)
        assertEquals(1 + nestedRows, lifted.belowRows)
        assertEquals("lifted: one clear row", 1, clear(lifted))

        val noTotal = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = false, nestedLayouts = nested)
        assertEquals(0, noTotal.chainBelowRows)
        assertEquals("no total: one clear row", 1, clear(noTotal))

        val compressed = planUndercutRailRows(
            listOf(span(0, false), span(1, false)), false, hasTotalRail = true, nestedLayouts = nested,
        )
        assertEquals(2, compressed.chainBelowRows)
        assertEquals(2 + nestedRows + 1, compressed.belowRows)
        assertEquals("compressed with nested rows: one clear row under them", 1, clear(compressed))

        val chainOnly = planUndercutRailRows(listOf(span(0, false), span(1, false)), false, hasTotalRail = true)
        assertEquals(2, chainOnly.belowRows)
        assertEquals("compressed, chain only: the chain's rows fill the place", 0, clear(chainOnly))

        val seated = planUndercutRailRows(listOf(span(0, true)), false, hasTotalRail = true, nestedLayouts = nested)
        assertEquals(0, seated.chainBelowRows)
        assertEquals(1 + nestedRows, seated.belowRows)
        assertEquals("total with no fallbacks: one clear row", 1, clear(seated))

        val started = planUndercutRailRows(emptyList(), startedStrip = true, hasTotalRail = true)
        assertEquals(0, started.chainBelowRows)
        assertEquals(WEAR_RAIL_MAX_LABEL_ROWS, started.belowRows)
    }

    // ── Stepping ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a lifted strip's nested line sits under the chain with the clear row below it`() {
        val nested = listOf(listOf(span(0, false)))
        val plan = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested, lifted = true)
        val r = rails(listOf(span(0, false)), nested, plan)
        val bandBottom = chainY + plan.belowRows * step
        val witnessBottom = bandBottom + 6f - UNDERCUT_RAIL_WITNESS_GAP_PT
        val lineY = undercutNestedRailLineYs(r, chainY, plan.belowRows, step, witnessBottom).single()
        assertEquals(chainY + 2 * step, lineY, 1e-3f)
        assertEquals("one full clear row between the nested line and the band bottom", step, bandBottom - lineY, 1e-3f)
        assertTrue(witnessBottom - lineY >= step)
        assertEquals("exactly one row higher than before", preFixLineY(r, plan.belowRows, 1) - step, lineY, 1e-3f)
    }

    @Test
    fun `a no-total strip's nested line sits under the chain with the clear row below it`() {
        val nested = listOf(listOf(span(0, false)))
        val plan = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = false, nestedLayouts = nested)
        val r = rails(listOf(span(0, false)), nested, plan)
        val bandBottom = chainY + plan.belowRows * step
        val witnessBottom = bandBottom + 6f - UNDERCUT_RAIL_WITNESS_GAP_PT
        val lineY = undercutNestedRailLineYs(r, chainY, plan.belowRows, step, witnessBottom).single()
        assertEquals(chainY + 2 * step, lineY, 1e-3f)
        assertTrue(witnessBottom - lineY >= step)
    }

    @Test
    fun `the compressed layout keeps a clear row under its nested lines`() {
        // On-device report: on a crowded page the last nested line landed on the band's bottom
        // edge — the liner's outline.
        val nested = listOf(listOf(span(0, false)), listOf(span(0, true)))
        val plan = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested)
        assertEquals(1, plan.chainBelowRows)
        // nestedRows = (1 + 1) + (1 + 0) = 3, plus the chain's own row and the clear row.
        assertEquals(1 + 3 + 1, plan.belowRows)
        val r = rails(listOf(span(0, false)), nested, plan)
        val bandBottom = chainY + plan.belowRows * step
        val witnessBottom = bandBottom - UNDERCUT_RAIL_WITNESS_GAP_PT
        val lineYs = undercutNestedRailLineYs(r, chainY, plan.belowRows, step, witnessBottom)
        // Under the chain's own row: the first level's fallback row, then its line; then the next.
        assertEquals(chainY + (1 + 2) * step, lineYs[0], 1e-3f)
        assertEquals(lineYs[0] + step, lineYs[1], 1e-3f)
        assertEquals("one full clear row under the last nested line", step, bandBottom - lineYs.last(), 1e-3f)
    }

    @Test
    fun `a short band still stops every nested line at the witness start`() {
        val nested = listOf(listOf(span(0, false)))
        // Lifted: plan wants 3 rows below, the band granted 1.
        val lifted = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested, lifted = true)
        val witnessBottom = chainY + 1 * step + 3f
        val lineY = undercutNestedRailLineYs(rails(listOf(span(0, false)), nested, lifted), chainY, 1, step, witnessBottom).single()
        assertEquals(witnessBottom, lineY, 0f)
        // Compressed: plan wants 2 + 2 + 1 rows, the band granted 3 — the chain's rows shrink to
        // what is left after the nested rows.
        val compressed = planUndercutRailRows(
            listOf(span(0, false), span(1, false)), false, hasTotalRail = true, nestedLayouts = nested,
        )
        val r = rails(listOf(span(0, false), span(1, false)), nested, compressed)
        val bottom = chainY + 3 * step + 3f
        val y = undercutNestedRailLineYs(r, chainY, 3, step, bottom).single()
        assertEquals(minOf(preFixLineY(r, 3, 1), bottom), y, 0f)
        assertTrue(y <= bottom)
    }

    // ── The job's strip, as recorded ─────────────────────────────────────────────────────

    @Test
    fun `the recorded job's nested row sits up under the chain, a clear row above the shaft`() {
        val doc = ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
        )
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val diaText = Paint(text).apply { textSize = 8f }
        val record = doc.undercutRecord
        val sh = undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        val strip = sh.strips.filterIsInstance<UndercutStrip.LinerStrip>().maxBy { it.linerStartMm }
        assertTrue("the FWD liner", strip.linerId.startsWith("02d0b768"))

        var liftedWithNestedFallback = 0
        // The full landscape width (content 36..756 pt) and narrower cells, over the strip band
        // of the page: narrower x maps push the nested row's short values off their line.
        listOf(756f, 576f, 456f, 396f).forEach { right ->
            val plan = requireNotNull(
                planUndercutDetailStrip(
                    sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                    36f, right, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                    startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                    arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                    leadSide = UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                ),
            )
            val (rails, inner) = plan.resolveRails(112f, 520f, dualStacked = false)
            assertTrue("the job's strip lifts at width ${right - 36f}", rails.rows.aboveRows >= 1)
            val nested = rails.nested.single()
            val rowStep = undercutRailRowHeightPt(plan.dimText, dualStacked = false)
            val witnessBottom = inner.cylTop - UNDERCUT_RAIL_WITNESS_GAP_PT
            val lineY = undercutNestedRailLineYs(rails, inner.chainRailY, inner.railLabelRows, rowStep, witnessBottom).single()
            val nestedFallbacks = undercutRailFallbackRows(nested)
            val before = (inner.chainRailY +
                (inner.railLabelRows - rails.rows.nestedRows).coerceAtLeast(0) * rowStep +
                (1 + nestedFallbacks) * rowStep).coerceAtMost(witnessBottom)
            println(
                "fixture FWD-liner strip, width ${right - 36f}: chainRailY=${inner.chainRailY} cylTop=${inner.cylTop} " +
                    "railLabelRows=${inner.railLabelRows} rows=${rails.rows} nestedFallbacks=$nestedFallbacks " +
                    "nested line before=$before after=$lineY " +
                    "(line to cylTop before=${inner.cylTop - before} after=${inner.cylTop - lineY})",
            )

            assertEquals(
                "the nested row starts right under the chain line",
                inner.chainRailY + (1 + nestedFallbacks) * rowStep, lineY, 1e-3f,
            )
            assertTrue(
                "a clear row between the nested line and the shaft ($lineY vs $witnessBottom)",
                witnessBottom - lineY >= rowStep,
            )

            // The nested row's own fallback values (above its line) stay clear of the chain's heads.
            val fm = plan.dimText.fontMetrics
            val headReach = maxOf(UNDERCUT_RAIL_LABEL_CLEAR_ARROW_PT, PDF_ARROW_SIZE_LARGE_PT) * 0.5f
            val fallbacks = nested.filter { !it.seatsInBreak }
            if (fallbacks.isNotEmpty()) liftedWithNestedFallback++
            fallbacks.forEach { s ->
                val baseline = undercutRailFallbackBaselineY(
                    lineY, s.labelRow, above = true, rowStep, plan.dimText.textSize, fm.descent,
                )
                assertTrue(
                    "${s.label.inline()} clears the chain's arrowheads " +
                        "(${baseline + fm.ascent} vs ${inner.chainRailY + headReach})",
                    baseline + fm.ascent > inner.chainRailY + headReach,
                )
                assertTrue("${s.label.inline()} sits above its own line", baseline + fm.descent < lineY)
            }
        }
        assertTrue("some width puts a nested value off its line", liftedWithNestedFallback >= 1)
    }

    // ── Crowded bands: the compressed layout ─────────────────────────────────────────────

    private val crowdText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val crowdDiaText = Paint(crowdText).apply { textSize = 8f }

    /** A band too short to lift: the lifted layout would draw only a 60 pt shaft. */
    private fun crowdedBand(plan: UndercutStripPlan, stacked: Boolean): Pair<Float, Float> =
        112f to 112f + plan.verticalOverheadPt(stacked, lifted = true) + 60f

    /** Every below-line chain value's lowest ink (descent, and a stack's second line) clears [limitY]. */
    private fun assertChainValuesClear(
        plan: UndercutStripPlan,
        rails: UndercutStripRails,
        inner: UndercutStripInnerLayout,
        stacked: Boolean,
        limitY: Float,
        what: String,
    ) {
        val rowStep = undercutRailRowHeightPt(plan.dimText, stacked)
        val fm = plan.dimText.fontMetrics
        val chainRows = (inner.railLabelRows - rails.rows.nestedRows).coerceAtLeast(1)
        val below = rails.chainWithLead.filter { !it.seatsInBreak }
        assertTrue("$what: some chain value falls below its line", below.isNotEmpty())
        below.forEach { s ->
            val baseline = undercutRailFallbackBaselineY(
                inner.chainRailY, s.labelRow.coerceAtMost(chainRows - 1), above = false, rowStep,
                plan.dimText.textSize, fm.descent,
            )
            val stackH = if (s.label.setsStacked(stacked)) plan.dimText.dualStackMetrics().advance else 0f
            val inkBottom = baseline + fm.descent + stackH
            assertTrue("$what: ${s.label.inline()} ink bottom $inkBottom clears $limitY", inkBottom <= limitY)
        }
    }

    @Test
    fun `on a crowded band the job's nested row keeps a clear row above the outline`() {
        val doc = ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
        )
        val record = doc.undercutRecord
        val sh = undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        val strip = sh.strips.filterIsInstance<UndercutStrip.LinerStrip>().maxBy { it.linerStartMm }
        // Single-unit, and stacked dual — whose taller rows the band must be reserved at too.
        listOf(false, true).forEach { stacked ->
            val plan = requireNotNull(
                planUndercutDetailStrip(
                    sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                    36f, 576f, UnitSystem.INCHES, crowdText, crowdDiaText, sh.shaftExtentMm,
                    startedStrip = false, dual = stacked, dualStacked = stacked, railSplitAtMm = emptyList(),
                    arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                    leadSide = UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                ),
            )
            val (top, bottom) = crowdedBand(plan, stacked)
            val (rails, inner) = plan.resolveRails(top, bottom, stacked)
            val what = if (stacked) "stacked dual" else "single unit"
            assertEquals("$what: crowded, so compressed", 0, rails.rows.aboveRows)
            assertTrue("$what: nested rows", rails.rows.nestedRows > 0)
            assertEquals("$what: the band grants the whole plan", rails.rows.belowRows, inner.railLabelRows)

            val rowStep = undercutRailRowHeightPt(plan.dimText, stacked)
            val witnessBottom = inner.cylTop - UNDERCUT_RAIL_WITNESS_GAP_PT
            val lineYs = undercutNestedRailLineYs(rails, inner.chainRailY, inner.railLabelRows, rowStep, witnessBottom)
            println("crowded $what: chainRailY=${inner.chainRailY} cylTop=${inner.cylTop} rows=${rails.rows} lines=$lineYs")
            lineYs.forEach { y ->
                assertTrue("$what: nested line $y sits a row above the outline ${inner.cylTop}", y <= inner.cylTop - rowStep + 1e-3f)
            }
            assertChainValuesClear(plan, rails, inner, stacked, inner.cylTop - 1f, what)
        }
    }

    @Test
    fun `a crowded chain-only strip keeps its below-line value off the outline`() {
        val inch = 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 120f * inch,
            autoBodyDiaMm = 5f * inch,
            liners = listOf(Liner(id = "L", startFromAftMm = 50f * inch, lengthMm = 18f * inch, odMm = 5.75f * inch)),
        )
        fun cut(id: String, at: Float, len: Float) = Undercut(
            id = id, startFromAftMm = at * inch, lengthMm = len * inch, diaMm = 5.625f * inch,
            authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L",
        )
        val record = UndercutRecord(undercuts = listOf(cut("a", 51f, 1f), cut("b", 53f, 7f)))
        val sh = undercutSheetStrips(spec, resolveComponents(spec), record, blankValues = false)
        val strip = sh.strips.single()
        listOf(false, true).forEach { stacked ->
            // A narrow cell: the 1" spans cannot seat their values.
            val plan = requireNotNull(
                planUndercutDetailStrip(
                    sh.surfaceSegs, strip, record.undercuts, sh.clampedById,
                    36f, 256f, UnitSystem.INCHES, crowdText, crowdDiaText, sh.shaftExtentMm,
                    startedStrip = false, dual = stacked, dualStacked = stacked, railSplitAtMm = emptyList(),
                    arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                    leadSide = UndercutAnchorSide.AFT_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                ),
            )
            val what = if (stacked) "stacked dual" else "single unit"
            assertTrue("$what: the short values cannot seat", plan.rails.canLiftFallbacks)
            val (top, bottom) = crowdedBand(plan, stacked)
            val (rails, inner) = plan.resolveRails(top, bottom, stacked)
            assertEquals("$what: crowded, so compressed", 0, rails.rows.aboveRows)
            assertEquals("$what: no nested rows", 0, rails.rows.nestedRows)
            assertTrue("$what: the chain's values sit below its line", rails.rows.chainBelowRows > 0)
            println("crowded chain-only $what: chainRailY=${inner.chainRailY} cylTop=${inner.cylTop} rows=${rails.rows}")
            assertChainValuesClear(plan, rails, inner, stacked, inner.cylTop - 1f, what)
        }
    }
}
