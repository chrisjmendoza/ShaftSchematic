package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutStrip
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
 * "Liner ends from S.E.T." through the printed sheet's own plan ([planUndercutDetailStrip]
 * `linerEnds`, [UndercutStripPlan]): the near-end rail states the strip title's figure, the tiers
 * stack above the total's band, and the overhead grows by exactly what is reserved for them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutLinerEndRailStripTest {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val diaText = Paint(text).apply { textSize = 8f }

    private fun fixture(name: String) = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText(),
    )

    private class Planned(val plan: UndercutStripPlan, val sheet: UndercutSheetStrips, val strip: UndercutStrip.LinerStrip)

    private fun planOf(spec: ShaftSpec, record: UndercutRecord, side: UndercutAnchorSide, on: Boolean): Planned {
        val sh = undercutSheetStrips(spec, resolveComponents(spec), record, blankValues = false)
        val strip = sh.strips.first() as UndercutStrip.LinerStrip
        val plan = requireNotNull(
            planUndercutDetailStrip(
                sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                36f, 756f, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                leadSide = side, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                linerEnds = if (on) UndercutLinerSpanMm(strip.linerStartMm, strip.linerEndMm) else null,
            ),
        )
        return Planned(plan, sh, strip)
    }

    @Test
    fun `the near-end rail states the strip title's figure`() {
        listOf("undercut_crowded_royal_atlantic.shaft", "undercut_polar_ranger_stbd.shaft").forEach { name ->
            val doc = fixture(name)
            listOf(UndercutAnchorSide.AFT_SET, UndercutAnchorSide.FWD_SET).forEach { side ->
                val p = planOf(doc.spec, doc.undercutRecord, side, on = true)
                val liner = doc.spec.liners.single { it.id == p.strip.linerId }
                val title = buildLinerAnchorLabel(
                    doc.spec, liner, p.sheet.setPositions, UnitSystem.INCHES, false, forced = side.toLinerAnchor(),
                )
                val near = p.plan.rails.setRails.first().label.primary
                assertTrue("$name $side: \"$title\" starts with $near", title.startsWith("$near "))
            }
        }
    }

    @Test
    fun `the overhead grows by the two tiers and the lead it may give back`() {
        listOf("undercut_crowded_royal_atlantic.shaft", "undercut_polar_ranger_stbd.shaft").forEach { name ->
            val doc = fixture(name)
            val off = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, on = false).plan
            val on = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, on = true).plan
            val rowH = undercutRailRowHeightPt(on.dimText, false)
            fun bands(p: UndercutStripPlan): Float {
                val r = p.rails.rows
                val clear = if (r.chainBelowRows > 0 && r.nestedRows == 0) undercutRailBelowClearPt(p.dimText, false) else 0f
                val total = if (p.rails.totalSpan != null) UNDERCUT_TOTAL_RAIL_BAND_PT else 0f
                return total + (r.setRows + r.aboveRows + r.belowRows) * rowH + clear + p.diaBandPt
            }
            assertEquals(2, on.rails.rows.setTiers)
            assertEquals(name, bands(on) - bands(off), on.verticalOverheadPt(false) - off.verticalOverheadPt(false), 1e-2f)
        }
    }

    @Test
    fun `the tiers sit above the total's band at the pitch the layout carries`() {
        val doc = fixture("undercut_crowded_royal_atlantic.shaft")
        val plan = planOf(doc.spec, doc.undercutRecord, UndercutAnchorSide.AFT_SET, on = true).plan
        val (rails, inner) = plan.resolveRails(112f, 560f, dualStacked = false)
        assertTrue(rails.totalSpan != null)
        val rowH = undercutRailRowHeightPt(plan.dimText, false)
        assertEquals(rowH, inner.setTierPitchPt, 0f)
        val ys = undercutSetRailLineYs(rails, inner.chainRailY, rowH, inner.setTierPitchPt, totalRailY = inner.totalRailY)
        assertTrue("tier 0 above the total's value", ys[0] <= inner.totalRailY - UNDERCUT_TOTAL_RAIL_ABOVE_PT - rowH + 1e-3f)
        assertTrue("the top tier stays inside the strip", ys.last() >= 112f - 1e-3f)
        assertTrue(inner.cylBottom - inner.cylTop >= UNDERCUT_CYL_MAX_FLOOR_PT)
    }
}
