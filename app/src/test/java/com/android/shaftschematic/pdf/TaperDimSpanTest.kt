package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.android.shaftschematic.util.DualLabel
import org.junit.Test

class TaperDimSpanTest {

    @Test
    fun `adds one local span per end taper in measure space`() {
        val aft = Taper(
            startFromAftMm = 0f,
            lengthMm = 200f,
            startDiaMm = 60f,
            endDiaMm = 40f
        )
        val fwd = Taper(
            startFromAftMm = 800f,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 30f
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(aft, fwd)
        )

        val win = computeOalWindow(spec)
        val spans = buildTaperLengthSpans(spec, win, UnitSystem.MILLIMETERS)

        assertEquals(2, spans.size)
        spans.forEach { assertEquals(SpanKind.LOCAL, it.kind) }

        // Translation-only mapping => measure-space endpoints match raw endpoints when measureStart=0
        val xs = spans.map { minOf(it.x1Mm, it.x2Mm) to maxOf(it.x1Mm, it.x2Mm) }.toSet()
        assertTrue(xs.contains(0.0 to 200.0))
        assertTrue(xs.contains(800.0 to 1000.0))

        val expectedLabel = formatLenDim(200.0, UnitSystem.MILLIMETERS)
        spans.forEach { assertEquals(expectedLabel, it.label.inline()) }
    }

    @Test
    fun `taper at shaft AFT end with excluded thread spans physical coordinates`() {
        val excludedAftThread = Threads(
            startFromAftMm = 0f,
            lengthMm = 100f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val aftTaper = Taper(
            startFromAftMm = 0f,
            lengthMm = 200f,
            startDiaMm = 60f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(excludedAftThread),
            tapers = listOf(aftTaper)
        )

        val win = computeOalWindow(spec)
        // Window always spans full shaft — excludeFromOAL does not shift the origin
        assertEquals(0.0, win.measureStartMm, 1e-6)

        val spans = buildTaperLengthSpans(spec, win, UnitSystem.MILLIMETERS)
        assertEquals(1, spans.size)

        val s = spans.single()
        val lo = minOf(s.x1Mm, s.x2Mm)
        val hi = maxOf(s.x1Mm, s.x2Mm)
        // Taper spans its physical position [0, 200]
        assertEquals(0.0, lo, 1e-6)
        assertEquals(200.0, hi, 1e-6)
    }

    // A taper that stands off both SETs — a mid-shaft step ahead of a liner — is located like a
    // liner: a DATUM span from the nearer SET to its near edge plus its LOCAL length. The
    // on-device shaft: AFT end taper, a step-down taper at 180", a liner, a plain body at FWD.
    private val aftEnd = Taper(id = "aft", startFromAftMm = 0f, lengthMm = 409.575f, startDiaMm = 131.6f, endDiaMm = 165.8f)
    private val mid = Taper(id = "mid", startFromAftMm = 4572f, lengthMm = 161.925f, startDiaMm = 165.1f, endDiaMm = 150f)
    private val stepShaft = ShaftSpec(overallLengthMm = 6070.6f, tapers = listOf(aftEnd, mid))

    @Test
    fun `mid-shaft taper gets a datum span from the nearer SET plus its length`() {
        val win = computeOalWindow(stepShaft)
        val spans = buildTaperLengthSpans(stepShaft, win, UnitSystem.MILLIMETERS)

        assertEquals(3, spans.size)
        val endSpan = spans[0]
        assertEquals(SpanKind.LOCAL, endSpan.kind)
        assertEquals(409.575, maxOf(endSpan.x1Mm, endSpan.x2Mm), 1e-3)

        // No FWD taper, so the FWD SET is the shaft end; the mid taper is nearer to it.
        val datum = spans.single { it.kind == SpanKind.DATUM }
        assertEquals(6070.6, datum.x1Mm, 1e-3)
        assertEquals(4733.925, datum.x2Mm, 1e-3)
        assertEquals(formatLenDim(6070.6 - 4733.925, UnitSystem.MILLIMETERS), datum.label.inline())

        val local = spans.last()
        assertEquals(SpanKind.LOCAL, local.kind)
        assertEquals(4733.925, local.x1Mm, 1e-3)
        assertEquals(4572.0, local.x2Mm, 1e-3)
        assertEquals(formatLenDim(161.925, UnitSystem.MILLIMETERS), local.label.inline())
    }

    @Test
    fun `measurement reference forces the mid-shaft taper's datum like a liner's`() {
        val dims = mapToMidTaperDimsForPdf(stepShaft, PdfTieringMode.AFT)
        assertEquals(listOf("mid"), dims.map { it.id })
        assertEquals(LinerAnchor.AFT_SET, dims.single().anchor)
        assertEquals(4572.0, dims.single().offsetFromSetMm, 1e-3)
        assertEquals(161.925, dims.single().lengthMm, 1e-3)

        val win = computeOalWindow(stepShaft)
        val datum = buildTaperLengthSpans(stepShaft, win, UnitSystem.MILLIMETERS, measureFrom = PdfTieringMode.AFT)
            .single { it.kind == SpanKind.DATUM }
        assertEquals(0.0, datum.x1Mm, 1e-3)
        assertEquals(4572.0, datum.x2Mm, 1e-3)
    }

    @Test
    fun `end tapers and zero-length tapers are never mid-shaft dims`() {
        val fwdEnd = Taper(id = "fwd", startFromAftMm = 5800f, lengthMm = 270.6f, startDiaMm = 150f, endDiaMm = 120f)
        val empty = Taper(id = "empty", startFromAftMm = 3000f, lengthMm = 0f, startDiaMm = 150f, endDiaMm = 150f)
        val spec = stepShaft.copy(tapers = listOf(aftEnd, fwdEnd, empty))
        assertTrue(mapToMidTaperDimsForPdf(spec, PdfTieringMode.AUTO).isEmpty())
        val spans = buildTaperLengthSpans(spec, computeOalWindow(spec), UnitSystem.MILLIMETERS)
        assertEquals(2, spans.size)
        spans.forEach { assertEquals(SpanKind.LOCAL, it.kind) }
    }
}
