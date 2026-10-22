package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The undercut rail's LIFTED layout (on-device request: use the room above the rails for the
 * short values a chain cannot seat, compress them under the line when the sheet has no room):
 * the row plan ([planUndercutRailRows] `lifted`), the inner layout with a total band AND the
 * chain's above rows ([computeUndercutStripInnerLayout]), the ONE height-driven decision
 * ([undercutRailsLift]) and the strip plan's resolution of it ([UndercutStripPlan.resolveRails]),
 * on the on-device job (fixture `undercut_polar_ranger_stbd.shaft`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutRailLiftTest {

    private val row = UNDERCUT_RAIL_ROW_HEIGHT_PT

    private fun span(row: Int, seated: Boolean) =
        WearRailSpanLayout(0f, 10f, DualLabel.single("x"), 5f, row, seatsInBreak = seated, arrowInward = true)

    // ── Row plan ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a lifted plan puts the used rows above and keeps one clear row below`() {
        val nested = listOf(listOf(span(0, true)), listOf(span(0, false)))
        // nestedRows = (1 + 0) + (1 + 1) = 3
        assertEquals(
            UndercutRailRowPlan(belowRows = 1 + 3, aboveRows = 1, chainBelowRows = 0, nestedRows = 3),
            planUndercutRailRows(listOf(span(0, false)), false, hasTotalRail = true, nestedLayouts = nested, lifted = true),
        )
        assertEquals(
            UndercutRailRowPlan(belowRows = 1, aboveRows = 2, chainBelowRows = 0),
            planUndercutRailRows(listOf(span(0, false), span(1, false)), false, hasTotalRail = true, lifted = true),
        )
        // Net cost of lifting: exactly one row.
        val chain = listOf(span(0, false), span(1, false), span(2, true))
        val below = planUndercutRailRows(chain, false, hasTotalRail = true)
        val lifted = planUndercutRailRows(chain, false, hasTotalRail = true, lifted = true)
        assertEquals(1, (lifted.aboveRows + lifted.belowRows) - (below.aboveRows + below.belowRows))
    }

    @Test
    fun `lifted changes nothing without fallbacks, without a total, or on a started strip`() {
        val seated = listOf(span(0, true), span(1, true))
        assertEquals(
            planUndercutRailRows(seated, false, hasTotalRail = true),
            planUndercutRailRows(seated, false, hasTotalRail = true, lifted = true),
        )
        listOf(listOf(span(0, false)), listOf(span(3, false)), emptyList()).forEach { chain ->
            assertEquals(
                planUndercutRailRows(chain, false, hasTotalRail = false),
                planUndercutRailRows(chain, false, hasTotalRail = false, lifted = true),
            )
        }
        assertEquals(
            UndercutRailRowPlan(belowRows = WEAR_RAIL_MAX_LABEL_ROWS, aboveRows = 0, chainBelowRows = 0),
            planUndercutRailRows(emptyList(), startedStrip = true, hasTotalRail = true, lifted = true),
        )
    }

    @Test
    fun `lifting re-plans the rows only and leaves the horizontal layout alone`() {
        val spans = listOf(
            com.android.shaftschematic.geom.UndercutSpanMm("a", 30f, 34f),
            com.android.shaftschematic.geom.UndercutSpanMm("b", 40f, 80f),
        )
        val rails = planUndercutStripRails(
            0f, 100f, spans, UnitSystem.INCHES, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = { it * 4f }, labelWidth = { 24f },
        )
        assertNotNull(rails.totalSpan)
        assertTrue(rails.canLiftFallbacks)
        val lifted = rails.withFallbacksLifted()
        assertEquals(rails.copy(rows = lifted.rows), lifted)
        assertEquals(undercutRailFallbackRows(rails.chainWithLead), lifted.rows.aboveRows)
        assertFalse("already lifted", lifted.canLiftFallbacks)
        assertSame(lifted, lifted.withFallbacksLifted())
    }

    // ── Inner layout ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the total band and the lifted rows stack total, gap, rows, chain`() {
        // No-surplus band: every point is spent, so the arithmetic is exact.
        val base = computeUndercutStripInnerLayout(400f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f, maxLabelRows = 1)
        val lifted = computeUndercutStripInnerLayout(
            400f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f, maxLabelRows = 1, chainAboveBandPt = row,
        )
        assertEquals("the total line stays at the band top", 400f + UNDERCUT_TOTAL_RAIL_ABOVE_PT, lifted.totalRailY, 1e-3f)
        assertEquals(base.totalRailY, lifted.totalRailY, 1e-3f)
        assertEquals(
            "total line → gap → lifted rows → chain line",
            UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT + row,
            lifted.chainRailY - lifted.totalRailY, 1e-3f,
        )
        assertEquals(base.chainRailY + row, lifted.chainRailY, 1e-3f)
        assertEquals("the cylinder pays for the band on a full strip", base.cylTop + row, lifted.cylTop, 1e-3f)
        assertEquals(base.cylBottom, lifted.cylBottom, 1e-3f)
        assertTrue(400f <= lifted.totalRailY && lifted.totalRailY <= lifted.chainRailY)
        assertTrue(lifted.chainRailY <= lifted.cylTop && lifted.cylTop <= lifted.cylBottom && lifted.cylBottom <= 540f)
    }

    @Test
    fun `a roomy strip spends surplus on the lifted rows and keeps its capped cylinder`() {
        val base = computeUndercutStripInnerLayout(100f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f, maxLabelRows = 1)
        val lifted = computeUndercutStripInnerLayout(
            100f, 540f, 9f, hasTotalRail = true, diaBandPt = 12f, maxLabelRows = 1, chainAboveBandPt = row,
        )
        assertEquals(base.cylBottom - base.cylTop, lifted.cylBottom - lifted.cylTop, 1e-3f)
        assertEquals(
            UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT + row,
            lifted.chainRailY - lifted.totalRailY, 1e-3f,
        )
        assertTrue(undercutRailsLift(lifted.cylBottom - lifted.cylTop))
    }

    @Test
    fun `a zero above band leaves the total-only layout exactly as it was`() {
        // The existing inputs this file family pins (no-surplus, full-page, short, pathological).
        listOf(
            floatArrayOf(400f, 540f, 9f, 12f), floatArrayOf(100f, 540f, 9f, 0f), floatArrayOf(60f, 560f, 9f, 24f),
            floatArrayOf(400f, 460f, 9f, 12f), floatArrayOf(100f, 104f, 9f, 0f),
        ).forEach { (top, bottom, title, dia) ->
            listOf(true, false).forEach { total ->
                val a = computeUndercutStripInnerLayout(top, bottom, title, hasTotalRail = total, diaBandPt = dia)
                val b = computeUndercutStripInnerLayout(top, bottom, title, hasTotalRail = total, diaBandPt = dia, chainAboveBandPt = 0f)
                assertEquals(a, b)
                if (total) assertEquals(
                    (a.chainRailY - (UNDERCUT_TOTAL_RAIL_BAND_PT - UNDERCUT_TOTAL_RAIL_ABOVE_PT)).coerceIn(top, a.chainRailY),
                    a.totalRailY, 0f,
                )
            }
        }
    }

    @Test
    fun `a pathologically short strip keeps its order with both bands`() {
        val inner = computeUndercutStripInnerLayout(100f, 130f, 9f, hasTotalRail = true, chainAboveBandPt = 2 * row)
        assertTrue(100f <= inner.totalRailY)
        assertTrue(inner.totalRailY <= inner.chainRailY)
        assertTrue(inner.chainRailY <= inner.cylTop)
        assertTrue(inner.cylTop <= inner.cylBottom)
        assertTrue(inner.cylBottom <= 130f)
    }

    // ── The decision ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the lift predicate is the cylinder floor`() {
        assertTrue(undercutRailsLift(UNDERCUT_CYL_MAX_ABS_PT))
        assertTrue("exactly at the floor lifts", undercutRailsLift(UNDERCUT_CYL_MAX_FLOOR_PT))
        assertFalse(undercutRailsLift(UNDERCUT_CYL_MAX_FLOOR_PT - 0.01f))
        assertFalse(undercutRailsLift(0f))
        assertTrue(undercutRailsLift(50f, cylFloorPt = 50f))
    }

    // ── The job's strip, as recorded ─────────────────────────────────────────────────────

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val diaText = Paint(text).apply { textSize = 8f }

    private fun planOf(spec: ShaftSpec, record: UndercutRecord, left: Float, right: Float, pick: Int = 0): UndercutStripPlan {
        val sh = undercutSheetStrips(spec, resolveComponents(spec), record, blankValues = false)
        val strip = sh.strips[pick]
        return requireNotNull(
            planUndercutDetailStrip(
                sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                left, right, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                leadSide = UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
            ),
        )
    }

    @Test
    fun `the recorded job's landscape strip lifts its short chain value above the line`() {
        // One full-width landscape strip (content 36..756 pt) over the strip band of the page.
        val plan = planOf(doc.spec, doc.undercutRecord, 36f, 756f)
        assertTrue("the 1 1/4\" cannot seat", plan.rails.canLiftFallbacks)
        val (top, bottom) = 112f to 520f
        val (rails, inner) = plan.resolveRails(top, bottom, dualStacked = false)
        assertTrue("lifted", rails.rows.aboveRows >= 1)
        assertEquals(undercutRailFallbackRows(rails.chainWithLead), rails.rows.aboveRows)
        assertEquals(1 + rails.rows.nestedRows, rails.rows.belowRows)
        assertEquals(plan.innerLayout(top, bottom, false, lifted = true), inner)
        // A roomy strip: the lift came out of surplus, so the drawn shaft did not shrink.
        val unlifted = plan.innerLayout(top, bottom, false)
        assertEquals(unlifted.cylBottom - unlifted.cylTop, inner.cylBottom - inner.cylTop, 1e-3f)
        assertTrue(inner.totalRailY < inner.chainRailY - rails.rows.aboveRows * row)
    }

    @Test
    fun `the same strip squeezed into a crowded cell keeps its values below the line`() {
        val plan = planOf(doc.spec, doc.undercutRecord, 36f, 576f)
        val (rails, inner) = plan.resolveRails(300f, 440f, dualStacked = false)
        assertSame(plan.rails, rails)
        assertEquals(0, rails.rows.aboveRows)
        assertEquals(plan.innerLayout(300f, 440f, false), inner)
        val lifted = plan.innerLayout(300f, 440f, false, lifted = true)
        assertFalse(undercutRailsLift(lifted.cylBottom - lifted.cylTop))
    }

    @Test
    fun `a strip whose values all seat resolves to exactly today's layout`() {
        // One 18" liner, two 4" cuts: on a full-width strip every chain value seats.
        val inch = 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 120f * inch,
            autoBodyDiaMm = 5f * inch,
            liners = listOf(Liner(id = "L", startFromAftMm = 50f * inch, lengthMm = 18f * inch, odMm = 5.75f * inch)),
        )
        fun cut(id: String, at: Float) = Undercut(
            id = id, startFromAftMm = at * inch, lengthMm = 4f * inch, diaMm = 5.625f * inch,
            authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L",
        )
        val record = UndercutRecord(undercuts = listOf(cut("a", 53f), cut("b", 61f)))
        val plan = planOf(spec, record, 36f, 756f)
        assertNotNull(plan.rails.totalSpan)
        assertFalse(plan.rails.canLiftFallbacks)
        listOf(112f to 520f, 300f to 440f).forEach { (top, bottom) ->
            val (rails, inner) = plan.resolveRails(top, bottom, dualStacked = false)
            assertSame(plan.rails, rails)
            assertEquals(plan.innerLayout(top, bottom, false), inner)
        }
        assertEquals(plan.verticalOverheadPt(false), plan.verticalOverheadPt(false, lifted = true), 0f)
    }

    @Test
    fun `the lifted overhead is exactly one row more`() {
        val plan = planOf(doc.spec, doc.undercutRecord, 36f, 576f)
        assertEquals(row, plan.verticalOverheadPt(false, lifted = true) - plan.verticalOverheadPt(false), 1e-3f)
    }

    // ── Page banding ─────────────────────────────────────────────────────────────────────

    @Test
    fun `natural portrait cells size from the lifted overheads, crowded cells from the compressed ones`() {
        val overheads = listOf(150f, 120f)
        val lifted = listOf(167f, 120f)
        // Roomy: the natural cells take the lifted overheads.
        val roomy = undercutStripCells(true, 2, 0f, 700f, 36f, 576f, overheadsPt = overheads, liftedOverheadsPt = lifted)
        val expect = undercutPortraitNaturalCells(700f, lifted)!!
        assertEquals(expect.map { it.second }, roomy.map { it.bottom - it.top })
        // Without the lifted list the natural cells read the compressed overheads, as before.
        val plain = undercutStripCells(true, 2, 0f, 700f, 36f, 576f, overheadsPt = overheads)
        assertEquals(undercutPortraitNaturalCells(700f, overheads)!!.map { it.second }, plain.map { it.bottom - it.top })
        // Crowded: the even / equal-shaft split reads the compressed overheads only.
        val crowdedLifted = undercutStripCells(true, 2, 0f, 380f, 36f, 576f, overheadsPt = overheads, liftedOverheadsPt = lifted)
        val crowdedPlain = undercutStripCells(true, 2, 0f, 380f, 36f, 576f, overheadsPt = overheads)
        assertEquals(crowdedPlain, crowdedLifted)
    }

    // ── Draw: a lifted value's halo clears the total's witness ───────────────────────────

    @Test
    fun `a halo edge inside a witness stroke widens over the whole stroke`() {
        val reach = 0.75f
        assertEquals(10f to 30f, undercutRailHaloSpan(10f, 30f, emptyList(), reach))
        assertEquals("a line well inside changes nothing", 10f to 30f, undercutRailHaloSpan(10f, 30f, listOf(20f), reach))
        assertEquals("a line well clear changes nothing", 10f to 30f, undercutRailHaloSpan(10f, 30f, listOf(5f, 40f), reach))
        val (l, r) = undercutRailHaloSpan(10f, 30f, listOf(10.2f, 30.5f), reach)
        assertEquals(10.2f - reach, l, 1e-4f)
        assertEquals(30.5f + reach, r, 1e-4f)
        val (l2, _) = undercutRailHaloSpan(10f, 30f, listOf(9.5f), reach)
        assertEquals(9.5f - reach, l2, 1e-4f)
    }
}
