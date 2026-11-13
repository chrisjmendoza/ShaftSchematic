package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.pdf.planUndercutStripRails
import com.android.shaftschematic.pdf.undercutRailFallbackRows
import com.android.shaftschematic.pdf.undercutSheetStrips
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Undercut tab's detail canvas draws a crowded section through the printed strip's axial map
 * ([planUndercutDetailXMap] → `planUndercutStripXMap`): on the on-device job (fixture
 * `undercut_crowded_royal_atlantic.shaft`, seven cuts on the AFT liner) the short cuts draw wide
 * enough to read, the rails are planned on that map, and a tap inverts it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutDetailCrowdedMapTest {

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_crowded_royal_atlantic.shaft")).readText(),
    )
    private val spec = doc.spec
    private val strip = undercutSheetStrips(spec, resolveComponents(spec), doc.undercutRecord, blankValues = false)
        .strips.single()
    private val spans = doc.undercutRecord.undercuts.map { u ->
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, spec.overallLengthMm)
        UndercutSpanMm(u.id, c.startMm, c.endMm)
    }
    private val widthOf: (DualLabel) -> Float = { it.inline().length * 11f }

    /** A tablet-wide canvas: room for every floor. */
    private fun inputs(widthPx: Float) = UndercutDetailRailInputs(
        spans = spans,
        chainStartMm = strip.chainStartMm,
        chainEndMm = strip.chainEndMm,
        splitAtMm = emptyList(),
        unit = UnitSystem.INCHES,
        startedStrip = false,
        widthPx = widthPx,
        profileRowHeightPx = 367f,
        edgePadPx = 42f,
        drawStartMm = strip.drawStartMm,
        windowLengthMm = strip.drawEndMm - strip.drawStartMm,
        maxOdMm = 190.5f,
        scale = 2.6f,
        textSizePx = 22f,
        typeface = null,
        fractionStyle = null,
    )

    @Test
    fun `a wide canvas seats every value of the crowded job`() {
        val inputs = inputs(2000f)
        val map = requireNotNull(planUndercutDetailXMap(inputs, widthOf))
        assertFalse(map.isLinear)
        val rails = requireNotNull(planUndercutDetailRails(inputs, widthOf))
        (rails.chain + rails.nested.flatten()).forEach {
            assertTrue("${it.label.inline()} seats in its line", it.seatsInBreak)
        }
        // The rails are planned on the map the canvas draws with.
        assertEquals(map.xAt(spans.minOf { it.startMm }), rails.chain.first().x0Pt, 1e-2f)
    }

    @Test
    fun `a phone canvas widens the short cuts and stacks fewer values`() {
        val inputs = inputs(1080f)
        val lay = computeUndercutWindowLayout(
            widthPx = inputs.widthPx, profileTopPx = 0f, profileRowHeightPx = inputs.profileRowHeightPx,
            windowLengthMm = inputs.windowLengthMm, maxOdMm = inputs.maxOdMm, edgePadPx = inputs.edgePadPx,
        )
        val map = requireNotNull(planUndercutDetailXMap(inputs, widthOf))
        assertFalse(map.isLinear)
        // Same canvas, same ends.
        assertEquals(lay.startPx, map.xAt(inputs.drawStartMm), 1e-2f)
        assertEquals(lay.endPx, map.xAt(inputs.drawStartMm + inputs.windowLengthMm), 0.5f)
        // Every cut under 2" draws at least twice its to-scale width.
        spans.filter { it.endMm - it.startMm < 50.8f }.forEach { s ->
            val drawn = map.xAt(s.endMm) - map.xAt(s.startMm)
            assertTrue(drawn >= 2f * (s.endMm - s.startMm) * lay.pxPerMm)
        }
        val toScale = planUndercutStripRails(
            strip.chainStartMm, strip.chainEndMm, spans, UnitSystem.INCHES, dual = false,
            splitAtMm = emptyList(), startedStrip = false,
            xAt = { mm -> lay.startPx + (mm - inputs.drawStartMm) * lay.pxPerMm },
            labelWidth = widthOf, scale = inputs.scale,
        )
        val fitted = requireNotNull(planUndercutDetailRails(inputs, widthOf))
        fun unseated(r: com.android.shaftschematic.pdf.UndercutStripRails) =
            (r.chain + r.nested.flatten()).count { !it.seatsInBreak }
        assertTrue(unseated(fitted) < unseated(toScale))
        assertTrue(undercutRailFallbackRows(fitted.chain) <= undercutRailFallbackRows(toScale.chain))
    }

    @Test
    fun `a tap reads the cut drawn under it`() {
        val map = requireNotNull(planUndercutDetailXMap(inputs(1080f), widthOf))
        spans.forEach { s ->
            val mid = (s.startMm + s.endMm) / 2f
            assertEquals(mid, map.mmAt(map.xAt(mid)), 0.05f)
        }
    }
}
