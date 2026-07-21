// file: app/src/main/java/com/android/shaftschematic/geom/RunoutReadingMath.kt
package com.android.shaftschematic.geom

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Runout Bubble Editor — pure math helpers for the interactive high-spot marker + bubble
 * hit-testing.
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports) in the `geom`
 * package alongside the placement engine, so both the on-screen editor/preview (`ui.screen`) and
 * the PDF composer (`pdf`) can share one source of truth for the clock/marker geometry — and so
 * the snapping, angle mapping, and hit-testing rules are directly unit-testable in a plain JVM
 * test.
 *
 * ## Clock convention
 * The high-spot marker sits on the bubble rim at a "clock position" expressed in **half-hour
 * ticks** (30-minute increments on a 12-hour face — Chris's hand convention). There are 24 ticks
 * `[0, 23]`: tick 0 = 12 o'clock (straight up), increasing **clockwise**, so each tick is 15°
 * (`n × 15°`). Screen/PDF space is y-down, so a tick's rim point is
 * `(cx + r·sinθ, cy − r·cosθ)` with `θ = n × 15°`.
 *
 * See `docs/RunoutBubbleEditor_PLAN.md` and `model/RunoutReading.kt`.
 */

/** Number of clock ticks around the face (30-minute increments on a 12-hour clock). */
const val RUNOUT_CLOCK_TICKS = 24

/** Degrees per clock tick (360 / 24). */
const val RUNOUT_DEGREES_PER_TICK = 360f / RUNOUT_CLOCK_TICKS

/**
 * Angle in degrees `[0, 360)` of the vector from a bubble centre (cx, cy) to a point (px, py),
 * measured with 0° = straight up (12 o'clock) and increasing **clockwise**. Screen space is
 * y-down. Returns 0f when the point coincides with the centre.
 */
fun bubbleAngleDeg(cx: Float, cy: Float, px: Float, py: Float): Float {
    val dx = px - cx
    val dy = py - cy
    if (dx == 0f && dy == 0f) return 0f
    // atan2(dx, -dy): 0 at up (0,-r), +90 at right (r,0) → clockwise from top.
    val deg = Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()
    return ((deg % 360f) + 360f) % 360f
}

/**
 * Snap a free angle in degrees (0° = up, clockwise) to the nearest clock tick `[0, 23]`.
 * Wraps correctly near 360°/0° (e.g. 358° → tick 0).
 */
fun snapToClockTick(angleDeg: Float): Int {
    val norm = ((angleDeg % 360f) + 360f) % 360f
    return (norm / RUNOUT_DEGREES_PER_TICK).roundToInt() % RUNOUT_CLOCK_TICKS
}

/** The angle in degrees (0° = up, clockwise) at the centre of clock tick [tick]. */
fun clockTickAngleDeg(tick: Int): Float =
    (((tick % RUNOUT_CLOCK_TICKS) + RUNOUT_CLOCK_TICKS) % RUNOUT_CLOCK_TICKS) * RUNOUT_DEGREES_PER_TICK

/**
 * Offset `(dx, dy)` from a bubble centre to the rim point at clock tick [tick], for a bubble of
 * radius [radius]. Screen space is y-down: tick 0 → `(0, -radius)` (up). Add to the centre to get
 * the rim point.
 */
fun clockTickRimOffset(tick: Int, radius: Float): Pair<Float, Float> {
    val theta = Math.toRadians(clockTickAngleDeg(tick).toDouble())
    val dx = (radius * sin(theta)).toFloat()
    val dy = (-radius * cos(theta)).toFloat()
    return dx to dy
}

/**
 * Human-readable clock label for a tick, e.g. tick 0 → "12:00", 1 → "12:30", 2 → "1:00",
 * 15 → "7:30". Used on the dialog and (optionally) as a print label.
 */
fun clockTickLabel(tick: Int): String {
    val t = (((tick % RUNOUT_CLOCK_TICKS) + RUNOUT_CLOCK_TICKS) % RUNOUT_CLOCK_TICKS)
    val hour24 = t / 2
    val hour = if (hour24 % 12 == 0) 12 else hour24 % 12
    val minutes = if (t % 2 == 0) "00" else "30"
    return "$hour:$minutes"
}

/**
 * True when a point (px, py) lies within the annular **ring band** of a bubble — i.e. within
 * [bandHalfWidth] of the circle of radius [radius] centred at (cx, cy). Touches inside the band
 * count; a touch in the hollow centre (where the value input lives) or well outside the rim does
 * not. This is the "only acts if you click on the ring" rule.
 */
fun isOnRingBand(
    cx: Float,
    cy: Float,
    radius: Float,
    bandHalfWidth: Float,
    px: Float,
    py: Float,
): Boolean {
    val dist = hypot(px - cx, py - cy)
    return dist in (radius - bandHalfWidth)..(radius + bandHalfWidth)
}

/**
 * Pick the bubble whose circle a tap at (px, py) falls inside (within [radius] + [tolerance] of
 * the centre). When a tap is inside more than one (overlapping affordance zones), the nearest
 * centre wins. Returns null when the tap hits no bubble. Coordinates are in the same output space
 * as [PlacedRunoutBubble.bubbleX]/[PlacedRunoutBubble.bubbleCenterY].
 */
fun pickBubbleAt(
    bubbles: List<PlacedRunoutBubble>,
    radius: Float,
    px: Float,
    py: Float,
    tolerance: Float = 0f,
): PlacedRunoutBubble? {
    val reach = radius + tolerance
    return bubbles
        .map { it to hypot(px - it.bubbleX, py - it.bubbleCenterY) }
        .filter { it.second <= reach }
        .minByOrNull { it.second }
        ?.first
}
