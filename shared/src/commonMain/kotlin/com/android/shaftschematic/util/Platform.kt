package com.android.shaftschematic.util

/**
 * The few platform calls the common core needs. Each is deliberately narrow, and each Android
 * `actual` is the exact JVM expression its callers used before they became common code — a
 * printed dimension, a component id or a drawn angle must come out bit-for-bit the same on Android.
 *
 * Do not replace one of these with a hand-written common implementation: `String.format`'s
 * HALF_UP rounding of the exact binary value, `Math.round`'s NaN-to-0, and the platform's own
 * `toRadians` association are what every stored sheet was printed with.
 */

/** A random RFC 4122 UUID in its canonical lowercase 36-character form — a new component id. */
expect fun randomUuidString(): String

/**
 * [value] fixed-point with [decimals] places in the US locale — `String.format(Locale.US,
 * "%.<decimals>f", value)` on Android.
 */
expect fun formatFixedUs(value: Double, decimals: Int): String

/** The [Float] overload of [formatFixedUs], keeping the argument's boxed type as the JVM sees it. */
expect fun formatFixedUs(value: Float, decimals: Int): String

/** `Math.round(float)`: half up, NaN → 0, infinities clamped to the [Int] range. */
expect fun mathRound(x: Float): Int

/** `Math.round(double)`: half up, NaN → 0, infinities clamped to the [Long] range. */
expect fun mathRound(x: Double): Long

/** `Math.toRadians` — the platform's own arithmetic, so drawn geometry keeps its last bit. */
expect fun mathToRadians(angdeg: Double): Double

/** `Math.toDegrees` — the platform's own arithmetic, so drawn geometry keeps its last bit. */
expect fun mathToDegrees(angrad: Double): Double

/** Wall-clock milliseconds since the epoch — `System.currentTimeMillis()` on Android. */
expect fun currentTimeMillis(): Long
