package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UNDERCUT_SPAN_EPS_MM
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.undercutPreviewChainMm
import com.android.shaftschematic.geom.undercutPreviewDrawRange
import com.android.shaftschematic.geom.undercutSetPositions
import com.android.shaftschematic.geom.undercutSetReachMm
import com.android.shaftschematic.geom.withChainMm
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.pdf.UndercutAnchorSide
import com.android.shaftschematic.pdf.planUndercutStripRails
import com.android.shaftschematic.pdf.undercutSheetStrips
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Undercut tab's detail canvas carries the printed strip's S.E.T. lead: its input comes from
 * the same side resolver over the canvas's own drawn range ([undercutDetailSetLead]) and runs
 * through the printed strip's ONE planner, so the canvas lead is the sheet's lead — on the
 * on-device job's FWD liner strip, "12 3/4"" from the chain end out toward the FWD S.E.T.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutDetailSetLeadTest {

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )
    private val spec = doc.spec

    @Test
    fun `the detail canvas's rail plan carries the lead on the job's strip`() {
        val strip = undercutSheetStrips(spec, resolveComponents(spec), doc.undercutRecord, blankValues = false)
            .strips.single() as UndercutStrip.LinerStrip
        val spans = doc.undercutRecord.undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, spec.overallLengthMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        val widthOf: (DualLabel) -> Float = { it.inline().length * 14f }
        // The canvas widens its drawn range on the lead's side, as the printed strip does, and the
        // other side to the same stock past its chain datum, so the cuts sit centred.
        val range = undercutDetailLeadDrawRange(
            strip, spec, UndercutSetDatum.NEAREST, spans, strip.drawStartMm, strip.drawEndMm,
            spec.overallLengthMm,
            widthPx = 1000f, edgePadPx = 84f, profileRowHeightPx = 367f, maxOdMm = 254f,
            scale = 3.28f, arrowSizePt = com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT,
            unit = UnitSystem.INCHES, labelWidthPx = widthOf,
        )
        assertTrue("the FWD (lead) side grows", range.endMm > strip.drawEndMm)
        assertTrue("neither side narrows", range.startMm <= strip.drawStartMm)
        assertEquals(
            "the same stock past each chain datum",
            strip.chainStartMm - range.startMm, range.endMm - strip.chainEndMm, UNDERCUT_SPAN_EPS_MM,
        )
        val lead = undercutDetailSetLead(
            strip, spec, UndercutSetDatum.NEAREST, spans, range.startMm, range.endMm,
        )
        assertEquals(UndercutAnchorSide.FWD_SET, lead.side)

        val inputs = UndercutDetailRailInputs(
            spans = spans,
            chainStartMm = strip.chainStartMm,
            chainEndMm = strip.chainEndMm,
            splitAtMm = emptyList(),
            unit = UnitSystem.INCHES,
            startedStrip = false,
            widthPx = 1000f,
            profileRowHeightPx = 367f,
            edgePadPx = 84f,
            drawStartMm = range.startMm,
            windowLengthMm = range.endMm - range.startMm,
            maxOdMm = 254f,
            scale = 3.28f,
            textSizePx = 26.25f,
            typeface = null,
            fractionStyle = null,
            setLead = lead,
        )
        val rails = requireNotNull(planUndercutDetailRails(inputs, widthOf))
        assertNotNull(rails.lead)
        assertTrue("the canvas lead value seats INLINE, not on a leader", rails.lead!!.seatsInBreak)
        assertEquals("12 3/4\"", rails.leadSpan!!.span.label.inline())
        assertTrue("the S.E.T. is off the canvas's drawn strip: a half dimension", rails.lead!!.openEnd)
        assertEquals(rails.lead, rails.chainWithLead.last())

        // The printed strip's planner over the canvas's x map, verbatim.
        val lay = computeUndercutWindowLayout(
            widthPx = inputs.widthPx, profileTopPx = 0f, profileRowHeightPx = inputs.profileRowHeightPx,
            windowLengthMm = inputs.windowLengthMm, maxOdMm = inputs.maxOdMm, edgePadPx = inputs.edgePadPx,
        )
        val direct = planUndercutStripRails(
            strip.chainStartMm, strip.chainEndMm, spans, UnitSystem.INCHES, dual = false, splitAtMm = emptyList(),
            startedStrip = false,
            xAt = { mm -> lay.startPx + (mm - inputs.drawStartMm) * lay.pxPerMm },
            labelWidth = widthOf,
            scale = inputs.scale,
            setLead = lead,
        )
        // The canvas always takes the lifted plan: its rail band grows to what the plan reserves.
        assertEquals(direct.withFallbacksLifted(), rails)

        // Forced AFT: the canvas lead swings to the AFT S.E.T. with the sheet's.
        val aft = undercutDetailSetLead(strip, spec, UndercutSetDatum.AFT, spans, range.startMm, range.endMm)
        assertEquals(UndercutAnchorSide.AFT_SET, aft.side)
        val aftRails = requireNotNull(planUndercutDetailRails(inputs.copy(setLead = aft), widthOf))
        assertTrue(aftRails.lead!!.openStart)
        assertEquals(aftRails.lead, aftRails.chainWithLead.first())
    }

    @Test
    fun `a previewed draft's chain carries the lead from where the draft leaves the chain`() {
        val oal = spec.overallLengthMm
        val strip = undercutSheetStrips(spec, resolveComponents(spec), doc.undercutRecord, blankValues = false)
            .strips.single() as UndercutStrip.LinerStrip
        val spans = doc.undercutRecord.undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }
        val (aftSet, fwdSet) = undercutSetPositions(spec)
        val reach = undercutSetReachMm(doc.undercutRecord.drawToSet)
        fun previewStrip(preview: List<UndercutSpanMm>): UndercutStrip {
            val (s, e) = undercutPreviewChainMm(strip, preview, oal, reachToSetMm = reach, aftSetMm = aftSet, fwdSetMm = fwdSet)
            return strip.withChainMm(s, e)
        }
        // Nothing moved: the stored chain, so an idle canvas and a no-op draft draw alike.
        assertEquals(strip, previewStrip(spans))

        // The FWD overhang cut moved 1" further FWD: the chain end follows it and the lead shortens
        // from the stored 12 3/4" to 11 3/4".
        val inch = 25.4f
        val fwdMost = spans.maxBy { it.endMm }
        val moved = spans.map { if (it.id == fwdMost.id) it.copy(startMm = it.startMm + inch, endMm = it.endMm + inch) else it }
        val draftStrip = previewStrip(moved)
        assertEquals(strip.chainEndMm + inch, draftStrip.chainEndMm, UNDERCUT_SPAN_EPS_MM)
        assertEquals(strip.chainStartMm, draftStrip.chainStartMm, UNDERCUT_SPAN_EPS_MM)

        val widthOf: (DualLabel) -> Float = { it.inline().length * 14f }
        val (previewStart, previewEnd) = undercutPreviewDrawRange(strip, moved, oal, cutSpans = moved)
        val range = undercutDetailLeadDrawRange(
            draftStrip, spec, UndercutSetDatum.NEAREST, moved, previewStart, previewEnd, oal,
            widthPx = 1000f, edgePadPx = 84f, profileRowHeightPx = 367f, maxOdMm = 254f,
            scale = 3.28f, arrowSizePt = com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT,
            unit = UnitSystem.INCHES, labelWidthPx = widthOf,
        )
        assertEquals(
            "the same stock past each of the DRAFT's chain datums",
            draftStrip.chainStartMm - range.startMm, range.endMm - draftStrip.chainEndMm, UNDERCUT_SPAN_EPS_MM,
        )
        val lead = undercutDetailSetLead(draftStrip, spec, UndercutSetDatum.NEAREST, moved, range.startMm, range.endMm)
        val rails = requireNotNull(
            planUndercutDetailRails(
                UndercutDetailRailInputs(
                    spans = moved,
                    chainStartMm = draftStrip.chainStartMm,
                    chainEndMm = draftStrip.chainEndMm,
                    splitAtMm = emptyList(),
                    unit = UnitSystem.INCHES,
                    startedStrip = false,
                    widthPx = 1000f,
                    profileRowHeightPx = 367f,
                    edgePadPx = 84f,
                    drawStartMm = range.startMm,
                    windowLengthMm = range.endMm - range.startMm,
                    maxOdMm = 254f,
                    scale = 3.28f,
                    textSizePx = 26.25f,
                    typeface = null,
                    fractionStyle = null,
                    setLead = lead,
                ),
                widthOf,
            ),
        )
        assertEquals("11 3/4\"", rails.leadSpan!!.span.label.inline())
        // The moved cut keeps its own span on the chain rather than collapsing against a stale datum.
        assertTrue(rails.chainSpans.any { kotlin.math.abs((it.endMm - it.startMm) - (fwdMost.endMm - fwdMost.startMm)) < 1e-2f })
    }
}
