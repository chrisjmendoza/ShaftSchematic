package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the runout bubble editor's pure interaction math (`RunoutReadingMath.kt`):
 * clock snapping (30-min increments), angle↔rim mapping, ring-band hit-testing, and
 * bubble picking. See `docs/RunoutBubbleEditor_PLAN.md`.
 */
class RunoutReadingMathTest {

    private val eps = 1e-3f

    // ── bubbleAngleDeg: 0° = up, clockwise ──────────────────────────────────────
    @Test fun angle_up_is_zero() = assertEquals(0f, bubbleAngleDeg(0f, 0f, 0f, -10f), eps)
    @Test fun angle_right_is_90() = assertEquals(90f, bubbleAngleDeg(0f, 0f, 10f, 0f), eps)
    @Test fun angle_down_is_180() = assertEquals(180f, bubbleAngleDeg(0f, 0f, 0f, 10f), eps)
    @Test fun angle_left_is_270() = assertEquals(270f, bubbleAngleDeg(0f, 0f, -10f, 0f), eps)
    @Test fun angle_center_is_zero() = assertEquals(0f, bubbleAngleDeg(5f, 5f, 5f, 5f), eps)

    // ── snapToClockTick: 24 ticks, 15° each, correct wraparound ─────────────────
    @Test fun snap_top() = assertEquals(0, snapToClockTick(0f))
    @Test fun snap_below_half_tick_rounds_down() = assertEquals(0, snapToClockTick(7f))
    @Test fun snap_above_half_tick_rounds_up() = assertEquals(1, snapToClockTick(8f))
    @Test fun snap_three_oclock() = assertEquals(6, snapToClockTick(90f))
    @Test fun snap_six_oclock() = assertEquals(12, snapToClockTick(180f))
    @Test fun snap_nine_oclock() = assertEquals(18, snapToClockTick(270f))
    @Test fun snap_wraps_near_360() = assertEquals(0, snapToClockTick(358f))
    @Test fun snap_last_tick() = assertEquals(23, snapToClockTick(345f))
    @Test fun snap_negative_wraps() = assertEquals(0, snapToClockTick(-2f))

    // ── clockTickAngleDeg ───────────────────────────────────────────────────────
    @Test fun tick_angles() {
        assertEquals(0f, clockTickAngleDeg(0), eps)
        assertEquals(90f, clockTickAngleDeg(6), eps)
        assertEquals(180f, clockTickAngleDeg(12), eps)
        assertEquals(345f, clockTickAngleDeg(23), eps)
    }

    // ── clockTickRimOffset: y-down screen space ────────────────────────────────
    @Test fun rim_offset_up() {
        val (dx, dy) = clockTickRimOffset(0, 100f)
        assertEquals(0f, dx, eps); assertEquals(-100f, dy, eps)
    }
    @Test fun rim_offset_right() {
        val (dx, dy) = clockTickRimOffset(6, 100f)
        assertEquals(100f, dx, eps); assertEquals(0f, dy, eps)
    }
    @Test fun rim_offset_down() {
        val (dx, dy) = clockTickRimOffset(12, 100f)
        assertEquals(0f, dx, eps); assertEquals(100f, dy, eps)
    }
    @Test fun rim_offset_left() {
        val (dx, dy) = clockTickRimOffset(18, 100f)
        assertEquals(-100f, dx, eps); assertEquals(0f, dy, eps)
    }

    // Round-trip: snapping a tick's own rim point returns the tick.
    @Test fun tick_rim_roundtrips_through_snap() {
        for (tick in 0 until RUNOUT_CLOCK_TICKS) {
            val (dx, dy) = clockTickRimOffset(tick, 50f)
            val angle = bubbleAngleDeg(0f, 0f, dx, dy)
            assertEquals("tick $tick", tick, snapToClockTick(angle))
        }
    }

    // ── clockTickLabel ─────────────────────────────────────────────────────────
    @Test fun labels() {
        assertEquals("12:00", clockTickLabel(0))
        assertEquals("12:30", clockTickLabel(1))
        assertEquals("1:00", clockTickLabel(2))
        assertEquals("7:30", clockTickLabel(15))
        assertEquals("11:30", clockTickLabel(23))
    }

    // ── isOnRingBand ───────────────────────────────────────────────────────────
    @Test fun on_rim_is_hit() = assertTrue(isOnRingBand(0f, 0f, 100f, 20f, 0f, -100f))
    @Test fun just_inside_band_is_hit() = assertTrue(isOnRingBand(0f, 0f, 100f, 20f, 0f, -90f))
    @Test fun center_is_miss() = assertFalse(isOnRingBand(0f, 0f, 100f, 20f, 0f, 0f))
    @Test fun far_outside_is_miss() = assertFalse(isOnRingBand(0f, 0f, 100f, 20f, 0f, -200f))

    // ── pickBubbleAt ───────────────────────────────────────────────────────────
    private fun bubble(id: String, x: Float, y: Float) = PlacedRunoutBubble(
        componentId = id, stationMm = 0f, stationX = x, surfaceY = 0f,
        bubbleX = x, bubbleCenterY = y, row = 0, leader = listOf(LeaderVertex(x, y)),
    )

    @Test fun picks_inside_bubble() {
        val bs = listOf(bubble("a", 0f, 0f), bubble("b", 100f, 0f))
        assertEquals("a", pickBubbleAt(bs, 20f, 5f, 5f)?.componentId)
    }
    @Test fun picks_nearest_when_overlapping_reach() {
        val bs = listOf(bubble("a", 0f, 0f), bubble("b", 25f, 0f))
        // Tap at x=14 is within reach (tol 10) of both; nearer to b (|14-25|=11 < 14).
        assertEquals("b", pickBubbleAt(bs, 20f, 14f, 0f, tolerance = 10f)?.componentId)
    }
    @Test fun miss_when_far() {
        val bs = listOf(bubble("a", 0f, 0f))
        assertNull(pickBubbleAt(bs, 20f, 200f, 200f))
    }
}
