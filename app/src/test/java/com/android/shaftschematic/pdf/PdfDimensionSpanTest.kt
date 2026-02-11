package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PdfDimensionSpanTest {

    @Test
    fun pdf_spans_aft_mode_are_cumulative() {
        val spec = baseFixtureSpec()
        val win = computeDimensionWindowForPdf(spec)
        val spans = buildPdfDimensionSpans(spec, UnitSystem.MILLIMETERS, win, PdfTieringMode.AFT)

        assertSpan(spans, 0.0, 10.0, "10.000 mm", SpanKind.DATUM)
        assertSpan(spans, 0.0, 70.0, "70.000 mm", SpanKind.DATUM)
        assertSpan(spans, 30.0, 35.0, "5.000 mm", SpanKind.LOCAL)
    }

    @Test
    fun pdf_spans_fwd_mode_are_cumulative() {
        val spec = baseFixtureSpec()
        val win = computeDimensionWindowForPdf(spec)
        val spans = buildPdfDimensionSpans(spec, UnitSystem.MILLIMETERS, win, PdfTieringMode.FWD)

        assertSpan(spans, 100.0, 20.0, "80.000 mm", SpanKind.DATUM)
        assertSpan(spans, 100.0, 80.0, "20.000 mm", SpanKind.DATUM)
        assertSpan(spans, 30.0, 35.0, "5.000 mm", SpanKind.LOCAL)
    }

    @Test
    fun pdf_spans_auto_mode_mixes_anchors() {
        val spec = baseFixtureSpec()
        val win = computeDimensionWindowForPdf(spec)
        val spans = buildPdfDimensionSpans(spec, UnitSystem.MILLIMETERS, win, PdfTieringMode.AUTO)

        assertSpan(spans, 0.0, 10.0, "10.000 mm", SpanKind.DATUM)
        assertSpan(spans, 100.0, 80.0, "20.000 mm", SpanKind.DATUM)
    }

    @Test
    fun excluded_threads_do_not_shift_window_and_do_not_span() {
        val spec = ShaftSpec(
            overallLengthMm = 120f,
            threads = listOf(
                Threads(id = "TH-A", startFromAftMm = 0f, lengthMm = 10f, majorDiaMm = 20f, pitchMm = 2f, excludeFromOAL = true),
                Threads(id = "TH-I", startFromAftMm = 30f, lengthMm = 5f, majorDiaMm = 20f, pitchMm = 2f, excludeFromOAL = false)
            )
        )

        val win = computeDimensionWindowForPdf(spec)
        val spans = buildPdfDimensionSpans(spec, UnitSystem.MILLIMETERS, win, PdfTieringMode.AFT)

        assertEquals(0.0, win.measureStartMm, 1e-6)
        assertSpan(spans, 30.0, 35.0, "5.000 mm", SpanKind.LOCAL)

        val excludedStart = win.toMeasureX(0.0)
        val excludedEnd = win.toMeasureX(10.0)
        assertTrue(
            "Excluded thread span should not exist",
            spans.none { it.x1Mm == excludedStart && it.x2Mm == excludedEnd }
        )

        val oalSpan = buildOalSpanForPdf(spec.overallLengthMm.toDouble(), UnitSystem.MILLIMETERS, win.measureStartMm)
        assertEquals("OAL 120.000 mm", oalSpan.labelTop)
    }

    @Test
    fun excluded_thread_mapping_shares_aft_baseline() {
        val spec = ShaftSpec(
            overallLengthMm = 120f,
            bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 30f, diaMm = 40f)),
            threads = listOf(
                Threads(
                    id = "TH-A",
                    startFromAftMm = 0f,
                    lengthMm = 10f,
                    majorDiaMm = 20f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.AFT
                )
            )
        )

        val win = computeDimensionWindowForPdf(spec)
        val ptPerMm = 1f

        val oalSpan = buildOalSpanForPdf(spec.overallLengthMm.toDouble(), UnitSystem.MILLIMETERS, win.measureStartMm)
        val innerSpan = buildPdfDimensionSpans(spec, UnitSystem.MILLIMETERS, win, PdfTieringMode.AFT)
            .first { it.kind == SpanKind.LOCAL }

        val oalStartPx = mapDimMmToPageOffsetPx(oalSpan.x1Mm, ptPerMm)
        val innerStartPx = mapDimMmToPageOffsetPx(innerSpan.x1Mm, ptPerMm)

        assertEquals("OAL and inner span should share aft baseline", oalStartPx, innerStartPx, 1e-3f)
    }

    private fun baseFixtureSpec(): ShaftSpec = ShaftSpec(
        overallLengthMm = 100f,
        bodies = listOf(
            Body(id = "B1", startFromAftMm = 0f, lengthMm = 30f, diaMm = 40f),
            Body(id = "B2", startFromAftMm = 50f, lengthMm = 20f, diaMm = 40f)
        ),
        threads = listOf(
            Threads(id = "TH1", startFromAftMm = 30f, lengthMm = 5f, majorDiaMm = 20f, pitchMm = 2f)
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 10f, lengthMm = 10f, odMm = 30f),
            Liner(id = "L2", startFromAftMm = 70f, lengthMm = 10f, odMm = 30f)
        )
    )

    private fun assertSpan(
        spans: List<DimSpan>,
        x1: Double,
        x2: Double,
        labelTop: String,
        kind: SpanKind,
        eps: Double = 1e-3,
    ) {
        assertTrue(
            "Expected span $x1..$x2 with $labelTop. Spans=$spans",
            spans.any {
                it.labelTop == labelTop && it.kind == kind &&
                    abs(it.x1Mm - x1) <= eps && abs(it.x2Mm - x2) <= eps
            }
        )
    }
}
