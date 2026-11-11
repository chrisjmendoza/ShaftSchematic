package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wear detail canvas's start / end bars (on-device request: size a wear area on the drawing
 * instead of measuring its length): which bar a press reaches, and what a dragged bar does to
 * its area — one edge moves, the other keeps its stored position, the moved edge stays inside
 * the component and never crosses its partner, and the result lands on a readable grid so the
 * card's Start and Length read as clean values.
 */
class WearAreaDragMathTest {

    private val inch = 25.4f
    private val eighth = inch / 8f

    private fun onGrid(valueMm: Float, gridMm: Float): Boolean =
        abs(valueMm / gridMm - (valueMm / gridMm).roundToLong()) < 1e-3f

    // ── Bar hit-testing ──────────────────────────────────────────────────────────────────

    private val bars = listOf(
        WearBarTarget("a", WearAreaEdge.START, xPx = 100f, topPx = 40f, bottomPx = 120f, priority = 0),
        WearBarTarget("a", WearAreaEdge.END, xPx = 200f, topPx = 40f, bottomPx = 120f, priority = 0),
        WearBarTarget("b", WearAreaEdge.START, xPx = 200f, topPx = 40f, bottomPx = 120f, priority = 1),
        WearBarTarget("b", WearAreaEdge.END, xPx = 320f, topPx = 40f, bottomPx = 120f, priority = 1),
    )

    @Test fun `a press within reach of a bar picks it`() {
        assertEquals(WearBarHit("a", WearAreaEdge.START), pickWearAreaBar(118f, 80f, bars, reachPx = 24f))
        assertEquals(WearBarHit("b", WearAreaEdge.END), pickWearAreaBar(300f, 80f, bars, reachPx = 24f))
    }

    @Test fun `a press outside every bar's reach picks nothing`() {
        assertNull(pickWearAreaBar(150f, 80f, bars, reachPx = 24f))
        assertNull(pickWearAreaBar(60f, 80f, bars, reachPx = 24f))
    }

    @Test fun `a press above or below the band misses unless inside the vertical pad`() {
        assertNull(pickWearAreaBar(100f, 20f, bars, reachPx = 24f))
        assertNull(pickWearAreaBar(100f, 140f, bars, reachPx = 24f))
        assertEquals(WearBarHit("a", WearAreaEdge.START), pickWearAreaBar(100f, 28f, bars, reachPx = 24f, yPadPx = 16f))
    }

    @Test fun `with two bars in reach the nearest wins`() {
        val close = listOf(
            WearBarTarget("a", WearAreaEdge.START, 100f, 0f, 100f),
            WearBarTarget("a", WearAreaEdge.END, 130f, 0f, 100f),
        )
        assertEquals(WearAreaEdge.START, pickWearAreaBar(112f, 50f, close, reachPx = 24f)!!.edge)
        assertEquals(WearAreaEdge.END, pickWearAreaBar(118f, 50f, close, reachPx = 24f)!!.edge)
    }

    @Test fun `two butted bars at one spot go to the higher priority area`() {
        // a's end and b's start share x = 200; b was added later (or dragged last).
        assertEquals(WearBarHit("b", WearAreaEdge.START), pickWearAreaBar(203f, 80f, bars, reachPx = 24f))
        val aLast = bars.map { if (it.spotId == "a") it.copy(priority = Int.MAX_VALUE) else it }
        assertEquals(WearBarHit("a", WearAreaEdge.END), pickWearAreaBar(203f, 80f, aLast, reachPx = 24f))
    }

    // ── Grid ─────────────────────────────────────────────────────────────────────────────

    @Test fun `the grid is an eighth inch in an inch document and a millimetre in a metric one`() {
        assertEquals(3.175f, wearDragGridMm(UnitSystem.INCHES), 1e-6f)
        assertEquals(1f, wearDragGridMm(UnitSystem.MILLIMETERS), 0f)
        assertEquals(3.175f, wearDragMinLengthMm(UnitSystem.INCHES), 1e-6f)
        assertEquals(3f, wearDragMinLengthMm(UnitSystem.MILLIMETERS), 0f)
        assertEquals(41.275f, snapToGridMm(40f, eighth), 1e-4f)
        assertEquals(37f, snapToGridMm(37.3f, 1f), 0f)
        assertEquals(12.34f, snapToGridMm(12.34f, 0f), 0f)
    }

    // ── Drag: a liner-AFT area, 1" in, 2" long, on a 10" host ────────────────────────────

    private fun drag(
        edge: WearAreaEdge,
        toMm: Float,
        startMm: Float = inch,
        lengthMm: Float = 2f * inch,
        hostLengthMm: Float = 10f * inch,
        reference: WearSpotReference = WearSpotReference.LINER_AFT,
        hostStartFromAftMm: Float = 1000f,
        aftSetXMm: Float = 12.7f,
        fwdSetXMm: Float = 3000f,
        unit: UnitSystem = UnitSystem.INCHES,
    ) = dragWearAreaEdge(
        edge, toMm, startMm, lengthMm, hostLengthMm, reference, hostStartFromAftMm, aftSetXMm, fwdSetXMm,
        gridMm = wearDragGridMm(unit), minLengthMm = wearDragMinLengthMm(unit),
    )

    @Test fun `dragging the end bar changes the length only, to the nearest eighth`() {
        val r = drag(WearAreaEdge.END, toMm = 101f)
        assertEquals("the start is the stored value, untouched", inch, r.startMm, 0f)
        assertEquals(3f * inch, r.lengthMm, 1e-4f)
    }

    @Test fun `dragging the start bar moves the start and keeps the end where it was`() {
        val r = drag(WearAreaEdge.START, toMm = 40f)
        assertEquals(41.275f, r.startMm, 1e-4f) // 1 5/8"
        assertEquals("the end did not move", 3f * inch, r.startMm + r.lengthMm, 1e-4f)
    }

    @Test fun `an edge cannot cross the other - a minimum length is kept`() {
        val start = drag(WearAreaEdge.START, toMm = 200f)
        assertEquals(eighth, start.lengthMm, 1e-4f)
        assertEquals(3f * inch, start.startMm + start.lengthMm, 1e-4f)

        val end = drag(WearAreaEdge.END, toMm = 5f)
        assertEquals(inch, end.startMm, 0f)
        assertEquals(eighth, end.lengthMm, 1e-4f)
    }

    @Test fun `an edge is held inside the component`() {
        val start = drag(WearAreaEdge.START, toMm = -80f)
        assertEquals(0f, start.startMm, 0f)
        assertEquals(3f * inch, start.lengthMm, 1e-4f)

        val end = drag(WearAreaEdge.END, toMm = 900f)
        assertEquals(10f * inch, end.startMm + end.lengthMm, 1e-4f)
    }

    @Test fun `a millimetre document snaps to whole millimetres and leaves a typed start alone`() {
        val r = drag(
            WearAreaEdge.END, toMm = 47.7f, startMm = 10.4f, lengthMm = 20f, hostLengthMm = 300f,
            unit = UnitSystem.MILLIMETERS,
        )
        assertEquals("a typed 10.4 is not snapped by dragging the other edge", 10.4f, r.startMm, 0f)
        assertEquals(37f, r.lengthMm, 1e-4f)

        val s = drag(
            WearAreaEdge.START, toMm = 12.6f, startMm = 10.4f, lengthMm = 20f, hostLengthMm = 300f,
            unit = UnitSystem.MILLIMETERS,
        )
        assertEquals(13f, s.startMm, 1e-4f)
        assertEquals(30.4f, s.startMm + s.lengthMm, 1e-4f)
    }

    // ── FWD-flavoured and S.E.T. references: the card's Start reads clean ────────────────

    private fun displayedStart(r: WearAreaDragResult, reference: WearSpotReference) = canonicalToWearStartMm(
        reference, r.startMm, r.lengthMm,
        linerStartFromAftMm = 1000f, linerLengthMm = 10f * inch, aftSetXMm = 12.7f, fwdSetXMm = 3000f,
    )

    @Test fun `under liner FWD the end bar snaps the displayed start and the start bar the length`() {
        val end = drag(WearAreaEdge.END, toMm = 100f, reference = WearSpotReference.LINER_FWD)
        assertEquals(inch, end.startMm, 0f)
        assertTrue(onGrid(displayedStart(end, WearSpotReference.LINER_FWD), eighth))
        assertEquals(98.425f, end.startMm + end.lengthMm, 1e-3f)

        val start = drag(WearAreaEdge.START, toMm = 40f, reference = WearSpotReference.LINER_FWD)
        assertEquals("the FWD edge the Start locates stays put", 3f * inch, start.startMm + start.lengthMm, 1e-4f)
        assertTrue(onGrid(start.lengthMm, eighth))
        assertEquals(
            "so the displayed Start is exactly what it was",
            7f * inch, displayedStart(start, WearSpotReference.LINER_FWD), 1e-3f,
        )
    }

    @Test fun `under AFT SET the start bar lands the displayed start on the grid`() {
        val r = drag(WearAreaEdge.START, toMm = 40f, reference = WearSpotReference.AFT_SET)
        assertTrue(onGrid(displayedStart(r, WearSpotReference.AFT_SET), eighth))
        assertEquals(3f * inch, r.startMm + r.lengthMm, 1e-3f)
        assertTrue("within half a grid step of the finger", abs(r.startMm - 40f) <= eighth / 2f + 1e-3f)
    }

    @Test fun `under FWD SET the end bar lands the displayed start on the grid`() {
        val r = drag(WearAreaEdge.END, toMm = 100f, reference = WearSpotReference.FWD_SET)
        assertEquals(inch, r.startMm, 0f)
        assertTrue(onGrid(displayedStart(r, WearSpotReference.FWD_SET), eighth))
        assertTrue(abs(r.startMm + r.lengthMm - 100f) <= eighth / 2f + 1e-3f)
    }

    @Test fun `the edge that was not dragged keeps a stored position outside the component`() {
        // A start typed AFT of the host (drawn clamped): dragging the end leaves it as typed.
        val r = drag(WearAreaEdge.END, toMm = 60f, startMm = -10f, lengthMm = 50f)
        assertEquals(-10f, r.startMm, 0f)
        assertTrue(onGrid(r.lengthMm, eighth))
        // An end stored past the host: dragging the start keeps that end.
        val s = drag(WearAreaEdge.START, toMm = 100f, startMm = 50.8f, lengthMm = 400f)
        assertEquals(450.8f, s.startMm + s.lengthMm, 1e-3f)
        assertEquals(98.425f, s.startMm, 1e-4f) // 3 7/8"
    }

    // ── Labels: one helper, the stored enum unchanged ────────────────────────────────────

    @Test fun `the host-edge references name the body on a body's page`() {
        assertEquals("Liner AFT", wearReferenceChipLabel(WearSpotReference.LINER_AFT, hostIsLiner = true))
        assertEquals("Body AFT", wearReferenceChipLabel(WearSpotReference.LINER_AFT, hostIsLiner = false))
        assertEquals("Body FWD", wearReferenceChipLabel(WearSpotReference.LINER_FWD, hostIsLiner = false))
        assertEquals("AFT SET", wearReferenceChipLabel(WearSpotReference.AFT_SET, hostIsLiner = false))
        assertEquals("liner AFT edge", wearReferenceFieldLabel(WearSpotReference.LINER_AFT, hostIsLiner = true))
        assertEquals("body FWD edge", wearReferenceFieldLabel(WearSpotReference.LINER_FWD, hostIsLiner = false))
        assertEquals("FWD SET", wearReferenceFieldLabel(WearSpotReference.FWD_SET, hostIsLiner = false))
        assertEquals(
            "Wear spot extends past the body's FWD edge",
            WearSpotSpanProblem.PAST_LINER_FWD.messageOn(hostIsLiner = false),
        )
        assertEquals(WearSpotSpanProblem.PAST_LINER_FWD.message, WearSpotSpanProblem.PAST_LINER_FWD.messageOn(true))
        assertEquals("Extends past body end", WearSpotSpanProblem.PAST_LINER_AFT.shortNoteOn(hostIsLiner = false))
    }
}
