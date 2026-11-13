package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
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
 * A PRINTED undercut strip is always to scale — the sheet shows the work done at its true
 * proportions, however crowded its values are (fixture `undercut_crowded_royal_atlantic.shaft`,
 * seven cuts on one liner). Only the detail canvas widens short stretches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutSheetToScaleTest {
    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_crowded_royal_atlantic.shaft")).readText(),
    )
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    private val diaText = Paint(text).apply { textSize = 8f }

    private fun planWith(strip: com.android.shaftschematic.geom.UndercutStrip, frac: Float): UndercutStripPlan {
        val record = doc.undercutRecord
        val sh = undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        return requireNotNull(
            planUndercutDetailStrip(
                sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                36f, 756f, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                leadSide = UndercutAnchorSide.AFT_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                longCompressFrac = frac,
            ),
        )
    }

    @Test
    fun `compressing long sections keeps the strip's ends and the short cuts' proportions`() {
        // 0..1000 mm chain: a 10 mm and a 30 mm cut either side of a 960 mm one, 50 mm stock each end.
        val edges = listOf(0f, 10f, 970f, 1000f)
        fun map(f: Float) = compressLongUndercutStripXMap(-50f, 1050f, 0f, 0.5f, 0f, 1000f, edges, f)
        assertTrue(map(0f).isLinear)
        val m = map(0.75f)
        // Ends and end stock to scale.
        assertEquals(25f, m.xAt(0f), 1e-3f)
        assertEquals(525f, m.xAt(1000f), 1e-2f)
        assertEquals(550f, m.xAt(1050f), 1e-2f)
        // The two short cuts stay 1 : 3, both drawn larger; the long one shorter but still longest.
        val a = m.xAt(10f) - m.xAt(0f)
        val b = m.xAt(1000f) - m.xAt(970f)
        val long = m.xAt(970f) - m.xAt(10f)
        assertEquals(3f, b / a, 1e-3f)
        assertTrue(a > 5f * 1.5f)
        assertTrue(long < 480f && long > b)
        // More compression, larger short cuts.
        assertTrue(map(0.9f).let { it.xAt(10f) - it.xAt(0f) } > a)
        // Equal stretches have nothing to trade: still to scale.
        assertTrue(compressLongUndercutStripXMap(0f, 100f, 0f, 1f, 0f, 100f, listOf(0f, 50f, 100f), 0.9f).isLinear)
    }

    @Test
    fun `every strip of the crowded job prints to scale`() {
        val record = doc.undercutRecord
        val sh = undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        assertTrue(sh.strips.isNotEmpty())
        for (fromSet in listOf(false, true)) sh.strips.forEach { strip ->
            val plan = requireNotNull(
                planUndercutDetailStrip(
                    sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                    36f, 756f, UnitSystem.INCHES, text, diaText, sh.shaftExtentMm,
                    startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                    arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                    leadSide = UndercutAnchorSide.AFT_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                    fromSet = fromSet,
                ),
            )
            assertTrue(plan.xMap.isLinear)
            assertTrue(planWith(strip, 0.9f).xMap.let { !it.isLinear || strip.undercutIds.size < 2 })
            // Two cuts draw in the ratio of their lengths.
            val cuts = record.undercuts.filter { it.id in strip.undercutIds }.mapNotNull { sh.clampedById[it.id] }
            if (cuts.size >= 2) {
                val (a, b) = cuts.sortedBy { it.endMm - it.startMm }.let { it.first() to it.last() }
                assertEquals(
                    (b.endMm - b.startMm) / (a.endMm - a.startMm),
                    (plan.xAt(b.endMm) - plan.xAt(b.startMm)) / (plan.xAt(a.endMm) - plan.xAt(a.startMm)),
                    1e-2f,
                )
            }
        }
    }
}
