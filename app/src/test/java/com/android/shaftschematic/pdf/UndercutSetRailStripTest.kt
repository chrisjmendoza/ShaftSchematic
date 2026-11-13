package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A From-S.E.T. strip through the printed sheet's own plan ([planUndercutDetailStrip] `fromSet`,
 * [UndercutStripPlan]): the sheet's strip, S.E.T. and drawn range give the hand sketch's values; the
 * vertical overhead grows by exactly the baseline tiers less the total band it gives back; and a
 * strip short of room closes its tiers up before its shaft gives more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutSetRailStripTest {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val diaText = Paint(text).apply { textSize = 8f }

    private fun fixture(name: String) = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText(),
    )

    private fun planOf(
        spec: ShaftSpec, record: UndercutRecord, side: UndercutAnchorSide, fromSet: Boolean,
        left: Float = 36f, right: Float = 756f,
    ): UndercutStripPlan {
        val sh = undercutSheetStrips(spec, resolveComponents(spec), record, blankValues = false)
        val strip = sh.strips.first()
        return requireNotNull(
            planUndercutDetailStrip(
                sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                left, right, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                leadSide = side, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                fromSet = fromSet,
            ),
        )
    }

    @Test
    fun `the printed strip measures the Royal Atlantic cuts as the hand sketch does`() {
        val doc = fixture("undercut_crowded_royal_atlantic.shaft")
        val plan = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, fromSet = true)
        val expected = listOf(15.5, 16.0625, 18.8125, 50.75, 51.3125, 51.875, 53.375, 54.0)
            .map { formatLenDimDualLabel(it * 25.4, UnitSystem.INCHES, false) }
        assertEquals(expected, plan.rails.setRails.map { it.label })
        plan.rails.setRails.forEach { assertTrue("${it.label.primary} seats in its line", it.seatsInBreak) }
        // The tier-0 value has the room the lead had: it opens off the AFT drawn edge.
        assertTrue(plan.rails.setRailSpans.first().openAtSet)
        assertEquals(plan.drawStartMm, plan.rails.setRailSpans.first().span.startMm, 0f)
    }

    @Test
    fun `the overhead grows by the tiers less the total band it gives back`() {
        val doc = fixture("undercut_polar_ranger_stbd.shaft")
        val chain = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.FWD_SET, fromSet = false)
        val set = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.FWD_SET, fromSet = true)
        val rowH = undercutRailRowHeightPt(set.dimText, false)
        val c = chain.rails.rows
        val s = set.rails.rows
        assertTrue(chain.rails.totalSpan != null)
        assertTrue(s.setTiers > 0)
        val chainClear = if (c.chainBelowRows > 0 && c.nestedRows == 0) undercutRailBelowClearPt(chain.dimText, false) else 0f
        val chainBands = UNDERCUT_TOTAL_RAIL_BAND_PT + (c.aboveRows + c.belowRows) * rowH + chainClear + chain.diaBandPt
        val setBands = (s.setRows + s.aboveRows + s.belowRows) * rowH + set.diaBandPt
        assertEquals(setBands - chainBands, set.verticalOverheadPt(false) - chain.verticalOverheadPt(false), 1e-2f)
        // Nothing lifts on a From-S.E.T. strip.
        assertEquals(set.verticalOverheadPt(false), set.verticalOverheadPt(false, lifted = true), 0f)
    }

    @Test
    fun `a roomy strip stacks its tiers at the row metric`() {
        val doc = fixture("undercut_crowded_royal_atlantic.shaft")
        val plan = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, fromSet = true)
        val (rails, inner) = plan.resolveRails(112f, 560f, dualStacked = false)
        assertEquals(undercutRailRowHeightPt(plan.dimText, false), inner.setTierPitchPt, 0f)
        assertTrue(inner.cylBottom - inner.cylTop >= UNDERCUT_CYL_MAX_FLOOR_PT)
        val ys = undercutSetRailLineYs(rails, inner.chainRailY, undercutRailRowHeightPt(plan.dimText, false), inner.setTierPitchPt)
        assertEquals(rails.setRails.size, ys.size)
        assertTrue("the top tier stays inside the strip", ys.last() >= 112f - 1e-3f)
    }

    @Test
    fun `a short strip closes its tiers up before its shaft gives more`() {
        val doc = fixture("undercut_crowded_royal_atlantic.shaft")
        val plan = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, fromSet = true)
        val rowH = undercutRailRowHeightPt(plan.dimText, false)
        val full = plan.innerLayout(0f, 1f, false)
        assertEquals(rowH, full.setTierPitchPt, 0f)
        // A strip that holds the floor shaft with the tiers 2 pt tighter than the row metric.
        val need = plan.verticalOverheadPt(false) + UNDERCUT_CYL_MAX_FLOOR_PT - plan.rails.rows.setRows * 2f
        val (_, inner) = plan.resolveRails(100f, 100f + need, dualStacked = false)
        assertEquals(rowH - 2f, inner.setTierPitchPt, 1e-2f)
        assertEquals(UNDERCUT_CYL_MAX_FLOOR_PT, inner.cylBottom - inner.cylTop, 1e-2f)
        // Far shorter: the tiers stop at their tightest pitch and the shaft takes the rest.
        val tightOverhead = plan.verticalOverheadPt(false, tightSetTiers = true)
        assertTrue(tightOverhead < plan.verticalOverheadPt(false))
        val (_, squeezed) = plan.resolveRails(100f, 100f + tightOverhead + 40f, dualStacked = false)
        val minPitch = rowH - (plan.verticalOverheadPt(false) - tightOverhead) / plan.rails.rows.setRows
        assertEquals(minPitch, squeezed.setTierPitchPt, 1e-2f)
        assertEquals(40f, squeezed.cylBottom - squeezed.cylTop, 1e-2f)
    }
}
