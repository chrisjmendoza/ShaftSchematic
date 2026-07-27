package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the tap-to-add position math (TODO §5.2, "preview-tap → adds at correct position").
 *
 * Covers the pure core of `ShaftViewModel.snapRawPositionMm` and `gapToNextAnchorMm`, which
 * delegate here. The gesture itself (px → mm) is covered by `ShaftLayoutMappingTest`.
 */
class TapAddPositionTest {

    /** Bodies at 0–500 and 1000–1500 on a 3000 mm shaft, leaving a 500 mm gap between them. */
    private val spec = ShaftSpec(
        overallLengthMm = 3000f,
        bodies = listOf(
            Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 150f),
            Body(id = "b2", startFromAftMm = 1000f, lengthMm = 500f, diaMm = 150f)
        )
    )

    // ── snap tolerance ───────────────────────────────────────────────────────────

    @Test
    fun `metric tolerance is one millimeter`() {
        assertEquals(1.0f, snapToleranceMm(UnitSystem.MILLIMETERS), 1e-4f)
    }

    @Test
    fun `imperial tolerance is a hair over one millimeter`() {
        // 0.04 in — deliberately a touch looser than metric so the snap radius feels the
        // same size on screen in either unit.
        assertEquals(1.016f, snapToleranceMm(UnitSystem.INCHES), 1e-4f)
    }

    // ── snapRawPositionMm ────────────────────────────────────────────────────────

    @Test
    fun `a tap just shy of a body edge snaps onto it`() {
        assertEquals(500f, snapRawPositionMm(499.6f, spec, UnitSystem.MILLIMETERS), 1e-3f)
        assertEquals(500f, snapRawPositionMm(500.4f, spec, UnitSystem.MILLIMETERS), 1e-3f)
    }

    @Test
    fun `a tap out in open space is left alone`() {
        assertEquals(750f, snapRawPositionMm(750f, spec, UnitSystem.MILLIMETERS), 1e-3f)
    }

    @Test
    fun `a tap just outside tolerance is left alone`() {
        assertEquals(502f, snapRawPositionMm(502f, spec, UnitSystem.MILLIMETERS), 1e-3f)
    }

    @Test
    fun `imperial tolerance snaps a tap that metric would not`() {
        // 1.01 mm away: inside the imperial radius (1.016 mm), outside the metric one.
        assertEquals(500f, snapRawPositionMm(501.01f, spec, UnitSystem.INCHES), 1e-3f)
        assertEquals(501.01f, snapRawPositionMm(501.01f, spec, UnitSystem.MILLIMETERS), 1e-3f)
    }

    @Test
    fun `taps near the shaft faces snap to them`() {
        assertEquals(0f, snapRawPositionMm(0.3f, spec, UnitSystem.MILLIMETERS), 1e-3f)
        assertEquals(3000f, snapRawPositionMm(2999.7f, spec, UnitSystem.MILLIMETERS), 1e-3f)
    }

    @Test
    fun `a tap before the aft face is not snapped back onto the shaft`() {
        // Anchors are filtered to [0, OAL], so a far-negative tap stays negative rather
        // than silently becoming 0. Pinned because the add dialogs must reject it, not
        // quietly place a component at the aft face.
        val snapped = snapRawPositionMm(-40f, spec, UnitSystem.MILLIMETERS)
        assertEquals(-40f, snapped, 1e-3f)
    }

    @Test
    fun `an empty spec still snaps to its own faces`() {
        val empty = ShaftSpec(overallLengthMm = 1000f)
        assertEquals(0f, snapRawPositionMm(0.5f, empty, UnitSystem.MILLIMETERS), 1e-3f)
        assertEquals(1000f, snapRawPositionMm(999.5f, empty, UnitSystem.MILLIMETERS), 1e-3f)
        assertEquals(400f, snapRawPositionMm(400f, empty, UnitSystem.MILLIMETERS), 1e-3f)
    }

    // ── gapToNextAnchorMm ────────────────────────────────────────────────────────

    @Test
    fun `gap from one body edge to the next fills the space between them`() {
        // Tap lands on b1's forward face; the next anchor is b2's aft face 500 mm ahead.
        assertEquals(500f, gapToNextAnchorMm(spec, positionMm = 500f), 1e-3f)
    }

    @Test
    fun `an anchor exactly on the tap is not its own next anchor`() {
        // Without the epsilon, the anchor at 500 would be picked as the next one and the
        // dialog would prefill a zero length.
        assertTrue(gapToNextAnchorMm(spec, positionMm = 500f) > 0f)
    }

    @Test
    fun `gap from mid-space measures to the next edge`() {
        assertEquals(250f, gapToNextAnchorMm(spec, positionMm = 750f), 1e-3f)
    }

    @Test
    fun `a gap smaller than the minimum is floored`() {
        // 20 mm short of b2's aft face; the prefill must not be a sliver.
        assertEquals(DEFAULT_ADD_GAP_MM, gapToNextAnchorMm(spec, positionMm = 980f), 1e-3f)
    }

    @Test
    fun `past the last anchor the gap falls back to the forward face`() {
        assertEquals(1000f, gapToNextAnchorMm(spec, positionMm = 2000f), 1e-3f)
    }

    @Test
    fun `at the forward face the gap floors to the minimum`() {
        // Nothing ahead: next falls back to OAL, which is where we already are.
        assertEquals(DEFAULT_ADD_GAP_MM, gapToNextAnchorMm(spec, positionMm = 3000f), 1e-3f)
    }

    @Test
    fun `a custom minimum is respected`() {
        assertEquals(200f, gapToNextAnchorMm(spec, positionMm = 980f, minimumMm = 200f), 1e-3f)
    }

    // ── the two together, as the tap pipeline runs them ──────────────────────────

    @Test
    fun `tapping the gap between two bodies prefills a body that fills it`() {
        // User taps 1 mm inside b1's forward face. Snap pulls it onto the face, and the
        // prefill length then spans exactly to b2 — the new body fills the gap flush.
        val start = snapRawPositionMm(499.5f, spec, UnitSystem.MILLIMETERS)
        val length = gapToNextAnchorMm(spec, start)
        assertEquals(500f, start, 1e-3f)
        assertEquals(500f, length, 1e-3f)
        assertEquals(1000f, start + length, 1e-3f)
    }
}
