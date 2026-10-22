package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
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
 * between the last nested line and the shaft ([undercutNestedRailLineYs]). The compressed layout
 * (total rail, chain fallbacks below the line) has no separate clear row and keeps its stepping.
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
        assertEquals(2 + nestedRows, compressed.belowRows)
        assertEquals("compressed: the chain's rows fill the place, no separate clear row", 0, clear(compressed))

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
    fun `the compressed layout keeps its stepping`() {
        val nested = listOf(listOf(span(0, false)), listOf(span(0, true)))
        val plan = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested)
        assertEquals(1, plan.chainBelowRows)
        val r = rails(listOf(span(0, false)), nested, plan)
        val witnessBottom = chainY + plan.belowRows * step + 6f - UNDERCUT_RAIL_WITNESS_GAP_PT
        val lineYs = undercutNestedRailLineYs(r, chainY, plan.belowRows, step, witnessBottom)
        val first = preFixLineY(r, plan.belowRows, 1)
        assertEquals(first, lineYs[0], 0f)
        assertEquals(first + (1 + 0) * step, lineYs[1], 0f)
        assertEquals("the last line takes the band's last row", chainY + plan.belowRows * step, lineYs[1], 1e-3f)
    }

    @Test
    fun `a short band still stops every nested line at the witness start`() {
        val nested = listOf(listOf(span(0, false)))
        // Lifted: plan wants 3 rows below, the band granted 1.
        val lifted = planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested, lifted = true)
        val witnessBottom = chainY + 1 * step + 3f
        val lineY = undercutNestedRailLineYs(rails(listOf(span(0, false)), nested, lifted), chainY, 1, step, witnessBottom).single()
        assertEquals(witnessBottom, lineY, 0f)
        // Compressed: plan wants 2 + 2 rows, the band granted 3 — the chain's rows shrink exactly
        // as they always did.
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
}
