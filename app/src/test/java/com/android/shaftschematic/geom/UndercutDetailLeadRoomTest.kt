package com.android.shaftschematic.geom

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.pdf.UndercutAnchorSide
import com.android.shaftschematic.pdf.formatLenDimDualLabel
import com.android.shaftschematic.pdf.planUndercutDetailStrip
import com.android.shaftschematic.pdf.undercutSetLeadRoomPt
import com.android.shaftschematic.pdf.undercutSetLeadValueMm
import com.android.shaftschematic.pdf.undercutSheetStrips
import com.android.shaftschematic.settings.PDF_ARROW_SIZES_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.screen.UNDERCUT_DETAIL_DP_PER_PT
import com.android.shaftschematic.ui.screen.UNDERCUT_DETAIL_RAIL_TEXT_PT
import com.android.shaftschematic.ui.screen.UNDERCUT_EDGE_PAD_DP
import com.android.shaftschematic.ui.screen.UndercutDetailRailInputs
import com.android.shaftschematic.ui.screen.computeUndercutWindowLayout
import com.android.shaftschematic.ui.screen.linerSpansOf
import com.android.shaftschematic.ui.screen.planUndercutDetailRails
import com.android.shaftschematic.ui.screen.undercutDetailLeadDrawRange
import com.android.shaftschematic.ui.screen.undercutDetailSetLead
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.measureDualLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Undercut tab's detail canvas draws the SAME stock past each chain datum — the S.E.T. lead's
 * room on its side and that very room on the other — so the cuts sit centred between the two
 * breaks (on-device request: symmetric edges, centred undercuts), while the lead's value still
 * seats in its own line and the window-end breaks fit inside the canvas's edge inset. Measured on
 * the MID liner of the on-device job (fixture `undercut_polar_ranger_stbd.shaft`) with the cuts
 * [UndercutBreakRoomSymmetryTest] sets on it, through the overlay's own call sequence
 * ([undercutPreviewDrawRange] → [undercutDetailLeadDrawRange] → [planUndercutDetailRails]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutDetailLeadRoomTest {

    private val inch = 25.4f
    private val midLinerId = "213e92ef-be16-410e-9ba6-a651f382db60"

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )

    private fun cut(id: String, startIn: Float, lenIn: Float, diaIn: Float) = Undercut(
        id = id, startFromAftMm = startIn * inch, lengthMm = lenIn * inch, diaMm = diaIn * inch,
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = midLinerId,
    )

    private val midCuts = listOf(
        cut("m1", 215.0625f, 2.0625f, 9.875f),
        cut("m2", 217.125f, 35.875f, 9.5f),
        cut("m3", 253.0f, 1.25f, 9.3f),
    )

    /** The overlay's canvas at [widthDp] × [density]: the drawn range, its layout, and its rails. */
    private class Canvas(
        val widthPx: Float,
        val density: Float,
        val strip: UndercutStrip.LinerStrip,
        val segs: List<SurfaceSeg>,
        val startMm: Float,
        val endMm: Float,
        val previewStartMm: Float,
        val previewEndMm: Float,
        val inputs: UndercutDetailRailInputs,
        val paint: Paint,
    ) {
        val aftStockMm get() = strip.chainStartMm - startMm
        val fwdStockMm get() = endMm - strip.chainEndMm
    }

    private fun canvas(widthDp: Float, density: Float, arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT): Canvas {
        val spec = doc.spec
        val oal = spec.overallLengthMm
        val rc = resolveComponents(spec)
        val segs = surfaceSegsFrom(rc, bodyBlends(spec, rc))
        val liner = linerSpansOf(rc).single { it.id == midLinerId }
        val spans = midCuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        val strip = linerStripFor(liner, spans, oal, segs = segs, cutSpans = spans)
        val (ps, pe) = undercutPreviewDrawRange(strip, spans, oal, segs = segs, cutSpans = spans)
        val widthPx = widthDp * density
        val scale = density * UNDERCUT_DETAIL_DP_PER_PT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = UNDERCUT_DETAIL_RAIL_TEXT_PT * scale }
        val edgePadPx = UNDERCUT_EDGE_PAD_DP * density
        val rowPx = 140f * density
        val range = undercutDetailLeadDrawRange(
            strip, spec, doc.undercutRecord.setDatum, spans, ps, pe, oal,
            widthPx = widthPx, edgePadPx = edgePadPx, profileRowHeightPx = rowPx,
            maxOdMm = maxOuterDiaOver(segs, ps, pe).takeIf { it > 0f } ?: 1f, scale = scale,
            arrowSizePt = arrowSizePt, unit = UnitSystem.INCHES,
        ) { paint.measureDualLabel(it, false) }
        val lead = undercutDetailSetLead(strip, spec, doc.undercutRecord.setDatum, spans, range.startMm, range.endMm)
        val inputs = UndercutDetailRailInputs(
            spans = spans, chainStartMm = strip.chainStartMm, chainEndMm = strip.chainEndMm,
            splitAtMm = emptyList(), unit = UnitSystem.INCHES, startedStrip = false,
            widthPx = widthPx, profileRowHeightPx = rowPx, edgePadPx = edgePadPx,
            drawStartMm = range.startMm, windowLengthMm = range.endMm - range.startMm,
            maxOdMm = maxOuterDiaOver(segs, range.startMm, range.endMm).takeIf { it > 0f } ?: 1f,
            scale = scale, textSizePx = paint.textSize, typeface = null, fractionStyle = null,
            arrowSizePt = arrowSizePt, setLead = lead,
        )
        return Canvas(widthPx, density, strip, segs, range.startMm, range.endMm, ps, pe, inputs, paint)
    }

    /** The lead's room at the canvas scale, as [undercutDetailLeadDrawRange] measures it. */
    private fun Canvas.leadRoomPx(arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT): Float {
        val value = requireNotNull(
            undercutSetLeadValueMm(strip.chainStartMm, strip.chainEndMm, UndercutAnchorSide.FWD_SET, inputs.setLead!!.setMm),
        )
        return undercutSetLeadRoomPt(
            paint.measureDualLabel(formatLenDimDualLabel(value.toDouble(), UnitSystem.INCHES, false), false),
            arrowSizePt, inputs.scale,
        )
    }

    private fun Canvas.report() =
        "canvas %.0f px @ %.3f: AFT stock %.3f\" FWD stock %.3f\" (preview AFT %.3f\" FWD %.3f\")".format(
            widthPx, density, aftStockMm / inch, fwdStockMm / inch,
            (strip.chainStartMm - previewStartMm) / inch, (previewEndMm - strip.chainEndMm) / inch,
        )

    @Test
    fun `both ends of the detail canvas get the same stock past their chain datums`() {
        val c = canvas(1080f / 2.625f, 2.625f)
        assertEquals(UndercutAnchorSide.FWD_SET, c.inputs.setLead?.side)
        val msg = c.report()
        assertEquals(msg, c.aftStockMm, c.fwdStockMm, UNDERCUT_SPAN_EPS_MM)
        // Never narrower than the break-room preview it widens.
        assertTrue(msg, c.startMm <= c.previewStartMm + 1e-3f && c.endMm >= c.previewEndMm - 1e-3f)
        // At the canvas's final scale each end's stock is exactly the lead's room — no longer than
        // the lead's value, its pads and its two short stubs need.
        val lay = computeUndercutWindowLayout(
            widthPx = c.widthPx, profileTopPx = 0f, profileRowHeightPx = 140f * c.density,
            windowLengthMm = c.endMm - c.startMm, maxOdMm = c.inputs.maxOdMm,
            edgePadPx = UNDERCUT_EDGE_PAD_DP * c.density,
        )
        val roomPx = c.leadRoomPx()
        val detail = "$msg; lead room %.1f px = %.3f\" at %.3f px/mm".format(roomPx, roomPx / lay.pxPerMm / inch, lay.pxPerMm)
        assertEquals(detail, roomPx, c.fwdStockMm * lay.pxPerMm, 0.5f)
        assertEquals(detail, roomPx, c.aftStockMm * lay.pxPerMm, 0.5f)
    }

    @Test
    fun `the canvas lead still seats its value in its own line at every arrow size`() {
        listOf(1080f / 2.625f to 2.625f, 360f to 3f, 360f to 2f).forEach { (dp, density) ->
            PDF_ARROW_SIZES_PT.forEach { arrow ->
                val c = canvas(dp, density, arrow)
                val rails = requireNotNull(planUndercutDetailRails(c.inputs) { c.paint.measureDualLabel(it, false) })
                val lead = requireNotNull(rails.lead) { c.report() }
                assertTrue("${c.report()} arrow $arrow: lead seats", lead.seatsInBreak)
                assertTrue(lead.openEnd)
                assertEquals("${c.report()} arrow $arrow", c.aftStockMm, c.fwdStockMm, UNDERCUT_SPAN_EPS_MM)
                // The lead's open end is the drawn edge: nothing of it reaches into the edge inset.
                assertTrue(c.report(), lead.x1Pt <= c.widthPx - UNDERCUT_EDGE_PAD_DP * c.density + 0.5f)
            }
        }
    }

    @Test
    fun `the window-end breaks fit inside the edge inset`() {
        listOf(1080f / 2.625f to 2.625f, 360f to 3f, 360f to 2f).forEach { (dp, density) ->
            val c = canvas(dp, density)
            val lay = computeUndercutWindowLayout(
                widthPx = c.widthPx, profileTopPx = 0f, profileRowHeightPx = 140f * density,
                windowLengthMm = c.endMm - c.startMm, maxOdMm = c.inputs.maxOdMm,
                edgePadPx = UNDERCUT_EDGE_PAD_DP * density,
            )
            val halfStroke = 1.5f * density / 2f
            fun lobe(xMm: Float) =
                outerDiaAt(c.segs, xMm) * 0.5f * lay.pxPerMm * UNDERCUT_BREAK_AMP_FRAC_OF_R *
                    BREAK_EDGE_OUTWARD_REACH_FRAC + halfStroke
            val aftLobe = lobe(c.startMm + 1f)
            val fwdLobe = lobe(c.endMm - 1f)
            val msg = "${c.report()}: inset %.1f dp, AFT lobe %.1f dp, FWD lobe %.1f dp".format(
                lay.startPx / density, aftLobe / density, fwdLobe / density,
            )
            assertTrue(msg, lay.startPx - aftLobe >= 0f)
            assertTrue(msg, lay.endPx + fwdLobe <= c.widthPx)
            // The height fit's own bound on any lobe stays inside the inset.
            val boundDp = 0.36f * 140f * UNDERCUT_BREAK_AMP_FRAC_OF_R * BREAK_EDGE_OUTWARD_REACH_FRAC + 0.75f
            assertTrue(msg + " bound $boundDp dp", boundDp <= UNDERCUT_EDGE_PAD_DP)
        }
    }

    @Test
    fun `the printed strip of the same liner still seats its lead`() {
        val record = doc.undercutRecord.copy(undercuts = midCuts)
        val sh = undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        val strip = sh.strips.first { (it as? UndercutStrip.LinerStrip)?.linerId == midLinerId }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        listOf(36f to 576f, 36f to 756f, 36f to 390f).forEach { (left, right) ->
            PDF_ARROW_SIZES_PT.forEach { arrow ->
                val plan = requireNotNull(
                    planUndercutDetailStrip(
                        sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                        left, right, UnitSystem.INCHES, text, Paint(text).apply { textSize = 8f }, sh.shaftExtentMm,
                        startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                        arrowSizePt = arrow,
                        leadSide = UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                    ),
                )
                val lead = requireNotNull(plan.rails.lead) { "a lead on [$left, $right]" }
                assertTrue("the printed lead seats on [$left, $right] at arrow $arrow", lead.seatsInBreak)
            }
        }
    }
}
