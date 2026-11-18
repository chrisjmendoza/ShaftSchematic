package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.undercutDrawnShaftEndsMm
import com.android.shaftschematic.geom.undercutPreviewDrawRange
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.pdf.undercutSheetStrips
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Undercut tab's detail canvas draws the end thread where the printed strip does: on the
 * on-device job (fixture `undercut_end_thread_pacific_warrior.shaft`) the AFT liner strip reaches
 * the shaft end, and the canvas's drawn range — the previewed range, then the S.E.T. lead's
 * widening — runs on over the AFT thread excluded from the OAL to its outer end, as the sheet's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutDetailEndThreadTest {

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_end_thread_pacific_warrior.shaft")).readText(),
    )
    private val spec = doc.spec
    private val oal = spec.overallLengthMm

    @Test
    fun `the canvas's drawn range runs over the AFT thread like the printed strip`() {
        val (aftEnd, fwdEnd) = undercutDrawnShaftEndsMm(spec)
        assertEquals(-155.575f, aftEnd, 1e-3f)
        val strip = undercutSheetStrips(spec, resolveComponents(spec), doc.undercutRecord, blankValues = false)
            .strips.filterIsInstance<UndercutStrip.LinerStrip>().minBy { it.linerStartMm }
        assertEquals("the printed strip runs to the thread's outer end", aftEnd, strip.drawStartMm, 1e-3f)
        assertEquals("the chain still starts at the S.E.T.", 0f, strip.chainStartMm, 1e-3f)

        val spans = doc.undercutRecord.undercuts
            .filter { it.id in strip.undercutIds }
            .map { u ->
                val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oal)
                UndercutSpanMm(u.id, c.startMm, c.endMm)
            }
        val (previewStart, previewEnd) = undercutPreviewDrawRange(
            strip, spans, oal, cutSpans = spans, aftEndMm = aftEnd, fwdEndMm = fwdEnd,
        )
        assertEquals(aftEnd, previewStart, 1e-3f)

        val widthOf: (DualLabel) -> Float = { it.inline().length * 14f }
        val range = undercutDetailLeadDrawRange(
            strip, spec, UndercutSetDatum.NEAREST, spans, previewStart, previewEnd, oal,
            widthPx = 1000f, edgePadPx = 84f, profileRowHeightPx = 367f, maxOdMm = 254f,
            scale = 3.28f, arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
            unit = UnitSystem.INCHES, labelWidthPx = widthOf,
        )
        assertEquals("the canvas draws the thread too", aftEnd, range.startMm, 1e-3f)
    }
}
