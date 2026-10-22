package com.android.shaftschematic.geom

import android.graphics.Paint
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.screen.UNDERCUT_DETAIL_DP_PER_PT
import com.android.shaftschematic.ui.screen.UNDERCUT_DETAIL_RAIL_TEXT_PT
import com.android.shaftschematic.ui.screen.UNDERCUT_EDGE_PAD_DP
import com.android.shaftschematic.ui.screen.linerSpansOf
import com.android.shaftschematic.ui.screen.undercutDetailLeadDrawRange
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.measureDualLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The window-end S-breaks keep EQUAL, roomy plain stock between each glyph's inner reach and the
 * nearest face or cut shoulder on the window's interior side (on-device request: the AFT break
 * sat on the liner face while the FWD end showed more body; the cuts should read centred between
 * the breaks). Measured on the MID liner of the on-device job (fixture
 * `undercut_polar_ranger_stbd.shaft`) with the MID-liner cuts of the on-device report set on it (the
 * fixture's own record has none there): the strip built as
 * the detail overlay builds it ([linerStripFor] + [undercutPreviewDrawRange] over the
 * `surfaceSegsFrom` envelope).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutBreakRoomSymmetryTest {

    private val inch = 25.4f
    private val midLinerId = "213e92ef-be16-410e-9ba6-a651f382db60"

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )

    private fun cut(id: String, startIn: Float, lenIn: Float, diaIn: Float) = Undercut(
        id = id, startFromAftMm = startIn * inch, lengthMm = lenIn * inch, diaMm = diaIn * inch,
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = midLinerId,
    )

    /** The MID liner (10" OD, 214"–250.75") cuts: the long one overhangs its FWD face into the body. */
    private val midCuts = listOf(
        cut("m1", 215.0625f, 2.0625f, 9.875f),
        cut("m2", 217.125f, 35.875f, 9.5f),
        cut("m3", 253.0f, 1.25f, 9.3f),
    )

    /** One window end's measurement, mm. */
    private data class EndMeasure(val endMm: Float, val faceMm: Float, val diaMm: Float, val reachMm: Float) {
        val ampMm get() = undercutBreakAmplitudeMm(diaMm)
        val daylightMm get() = abs(faceMm - endMm) - reachMm
        fun describe(inch: Float) =
            "end %.3f\" face %.3f\" Ø %.4f\" amp %.3f\" reach %.3f\" daylight %.3f\" (%.2f amp)".format(
                endMm / inch, faceMm / inch, diaMm / inch, ampMm / inch, reachMm / inch,
                daylightMm / inch, daylightMm / ampMm,
            )
    }

    /** Faces a break can kiss: every envelope piece boundary and every cut shoulder. */
    private fun faces(segs: List<SurfaceSeg>, cuts: List<UndercutSpanMm>): List<Float> {
        val env = buildSurfaceEnvelope(segs, segs.minOf { it.startMm }, segs.maxOf { it.endMm })
        return (env.flatMap { listOf(it.x0Mm, it.x1Mm) } + cuts.flatMap { listOf(it.startMm, it.endMm) }).sorted()
    }

    private fun measure(
        startMm: Float,
        endMm: Float,
        segs: List<SurfaceSeg>,
        cuts: List<UndercutSpanMm>,
    ): Pair<EndMeasure, EndMeasure> {
        val f = faces(segs, cuts)
        val eps = UNDERCUT_SPAN_EPS_MM
        fun at(x: Float, face: Float): EndMeasure {
            val d = outerDiaAt(segs, x + (if (face > x) eps else -eps) * 10f)
            return EndMeasure(x, face, d, undercutBreakAmplitudeMm(d) * BREAK_EDGE_OUTWARD_REACH_FRAC)
        }
        return at(startMm, f.first { it > startMm + eps }) to at(endMm, f.last { it < endMm - eps })
    }

    private class MidStrip(
        val segs: List<SurfaceSeg>,
        val spans: List<UndercutSpanMm>,
        val strip: UndercutStrip.LinerStrip,
        val oalMm: Float,
    )

    private fun midStrip(): MidStrip {
        val spec = doc.spec
        val oal = spec.overallLengthMm
        val rc = resolveComponents(spec)
        val segs = surfaceSegsFrom(rc, bodyBlends(spec, rc))
        val liner = linerSpansOf(rc).single { it.id == midLinerId }
        val spans = midCuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        return MidStrip(segs, spans, linerStripFor(liner, spans, oal, segs = segs, cutSpans = spans), oal)
    }

    @Test
    fun `the MID liner strip shows one equal amplitude of stock before each break`() {
        val m = midStrip()
        val (s, e) = undercutPreviewDrawRange(m.strip, m.spans, m.oalMm, segs = m.segs, cutSpans = m.spans)
        val (aft, fwd) = measure(s, e, m.segs, m.spans)
        val msg = "AFT ${aft.describe(inch)}\nFWD ${fwd.describe(inch)}\n" + canvasReport(m, s, e)
        // The nearest faces are the liner's AFT face and the overhanging cut's FWD shoulder.
        assertEquals(msg, 214f * inch, aft.faceMm, 0.01f)
        assertEquals(msg, 254.25f * inch, fwd.faceMm, 0.01f)
        assertTrue(msg, aft.daylightMm >= 0.9f * aft.ampMm)
        assertTrue(msg, fwd.daylightMm >= 0.9f * fwd.ampMm)
        assertEquals(msg, aft.daylightMm, fwd.daylightMm, UNDERCUT_SPAN_EPS_MM)
        // The strip as built and the overlay's preview of the same spans agree, and chain datums stay.
        assertEquals(m.strip.drawStartMm, s, 1e-3f)
        assertEquals(m.strip.drawEndMm, e, 1e-3f)
        assertEquals(214f * inch, m.strip.chainStartMm, 0.01f)
        assertEquals(254.25f * inch, m.strip.chainEndMm, 0.01f)
    }

    @Test
    fun `the break room on the job's strip is idempotent`() {
        val m = midStrip()
        val again = withUndercutBreakRoom(m.strip.drawStartMm, m.strip.drawEndMm, m.segs, m.oalMm, m.spans)
        assertEquals(m.strip.drawStartMm, again.first, 1e-4f)
        assertEquals(m.strip.drawEndMm, again.second, 1e-4f)
    }

    @Test
    fun `a cut overhanging one side into a smaller body still comes out symmetric`() {
        // Body Ø 230 AFT of a Ø 240 liner (1000..2000), a smaller Ø 200 body FWD of it, and a cut
        // overhanging the liner's FWD face by 80 mm into that body; the AFT end sits beside the
        // plain liner face. The two ends' glyphs differ in size; their daylight does not.
        val segs = listOf(
            SurfaceSeg(0f, 1000f, 230f, 230f),
            SurfaceSeg(1000f, 2000f, 240f, 240f),
            SurfaceSeg(2000f, 5000f, 200f, 200f),
        )
        val cuts = listOf(UndercutSpanMm("a", 1100f, 1200f), UndercutSpanMm("b", 1900f, 2080f))
        val strip = linerStripFor(UndercutLinerSpan("L", 1000f, 2000f), cuts, 5000f, segs = segs, cutSpans = cuts)
        val (aft, fwd) = measure(strip.drawStartMm, strip.drawEndMm, segs, cuts)
        val msg = "AFT ${aft.describe(1f)}\nFWD ${fwd.describe(1f)}"
        assertEquals(msg, 1000f, aft.faceMm, 1e-3f)
        assertEquals(msg, 2080f, fwd.faceMm, 1e-3f)
        assertEquals(msg, aft.daylightMm, fwd.daylightMm, UNDERCUT_SPAN_EPS_MM)
        assertTrue(msg, aft.daylightMm >= 0.9f * aft.ampMm && fwd.daylightMm >= 0.9f * fwd.ampMm)
        // Mirrored: the overhang on the AFT side gives the same result the other way round.
        val mirroredCuts = listOf(UndercutSpanMm("a", 920f, 1100f), UndercutSpanMm("b", 1800f, 1900f))
        val mirrored = linerStripFor(UndercutLinerSpan("L", 1000f, 2000f), mirroredCuts, 5000f, segs = segs, cutSpans = mirroredCuts)
        val (mAft, mFwd) = measure(mirrored.drawStartMm, mirrored.drawEndMm, segs, mirroredCuts)
        assertEquals(920f, mAft.faceMm, 1e-3f)
        assertEquals(2000f, mFwd.faceMm, 1e-3f)
        assertEquals(mAft.daylightMm, mFwd.daylightMm, UNDERCUT_SPAN_EPS_MM)
        assertEquals(fwd.daylightMm, mAft.daylightMm, UNDERCUT_SPAN_EPS_MM)
    }

    /**
     * The detail canvas's final range ([undercutDetailLeadDrawRange]) on a 1080 px phone — reported
     * alongside the pure range because the S.E.T. lead widens its own side further.
     */
    private fun canvasReport(m: MidStrip, s: Float, e: Float): String {
        val density = 2.625f
        val scale = density * UNDERCUT_DETAIL_DP_PER_PT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = UNDERCUT_DETAIL_RAIL_TEXT_PT * scale }
        val lead = undercutDetailLeadDrawRange(
            m.strip, doc.spec, doc.undercutRecord.setDatum, m.spans, s, e, m.oalMm,
            widthPx = 1080f, edgePadPx = UNDERCUT_EDGE_PAD_DP * density, profileRowHeightPx = 140f * density,
            maxOdMm = maxOuterDiaOver(m.segs, s, e), scale = scale, arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
            unit = UnitSystem.INCHES,
        ) { paint.measureDualLabel(it, false) }
        val (aft, fwd) = measure(lead.startMm, lead.endMm, m.segs, m.spans)
        return "canvas (S.E.T. lead applied) AFT ${aft.describe(inch)}\ncanvas FWD ${fwd.describe(inch)}"
    }

    @Test
    fun `the printed landscape strip of the same liner gets the same equal margins`() {
        val m = midStrip()
        val record = doc.undercutRecord.copy(undercuts = midCuts)
        val sh = com.android.shaftschematic.pdf.undercutSheetStrips(doc.spec, resolveComponents(doc.spec), record, blankValues = false)
        val strip = sh.strips.first { (it as? UndercutStrip.LinerStrip)?.linerId == midLinerId }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val plan = requireNotNull(
            com.android.shaftschematic.pdf.planUndercutDetailStrip(
                sh.surfaceSegs, strip, record.undercuts.filter { it.id in strip.undercutIds }, sh.clampedById,
                36f, 756f, UnitSystem.INCHES, text, Paint(text).apply { textSize = 8f }, sh.shaftExtentMm,
                startedStrip = false, dual = false, dualStacked = false, railSplitAtMm = emptyList(),
                arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                leadSide = com.android.shaftschematic.pdf.UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
            ),
        )
        val (pa, pf) = measure(plan.drawStartMm, plan.drawEndMm, m.segs, m.spans)
        val msg = "PDF AFT ${pa.describe(inch)}\nPDF FWD ${pf.describe(inch)}\n" +
            canvasReport(m, m.strip.drawStartMm, m.strip.drawEndMm)
        // The FWD S.E.T. lead's room already fits inside the break room here, so the print is symmetric.
        assertTrue(msg, pa.daylightMm >= 0.9f * pa.ampMm && pf.daylightMm >= 0.9f * pf.ampMm)
        assertEquals(msg, pa.daylightMm, pf.daylightMm, UNDERCUT_SPAN_EPS_MM)
    }
}
