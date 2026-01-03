package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        spans.forEach { assertEquals(expectedLabel, it.labelTop) }
    }

    @Test
    fun `does not clamp negative measure coordinates`() {
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
        // measureStart should be 100mm due to excluded thread
        assertEquals(100.0, win.measureStartMm, 1e-6)

        val spans = buildTaperLengthSpans(spec, win, UnitSystem.MILLIMETERS)
        assertEquals(1, spans.size)

        val s = spans.single()
        val lo = minOf(s.x1Mm, s.x2Mm)
        val hi = maxOf(s.x1Mm, s.x2Mm)
        assertEquals(-100.0, lo, 1e-6)
        assertEquals(100.0, hi, 1e-6)
    }
}
