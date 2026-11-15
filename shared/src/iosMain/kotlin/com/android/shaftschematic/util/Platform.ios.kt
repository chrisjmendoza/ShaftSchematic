package com.android.shaftschematic.util

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.stringWithFormat
import platform.Foundation.timeIntervalSince1970

actual fun randomUuidString(): String = NSUUID().UUIDString().lowercase()

/**
 * C `printf` fixed-point through Foundation. It rounds the exact binary value as the JVM does; the
 * one difference is an EXACT binary tie (`0.125` to two places), which printf rounds to even and
 * `String.format` rounds up.
 */
actual fun formatFixedUs(value: Double, decimals: Int): String =
    NSString.stringWithFormat("%.${decimals}f", value)

actual fun formatFixedUs(value: Float, decimals: Int): String =
    NSString.stringWithFormat("%.${decimals}f", value.toDouble())

actual fun mathRound(x: Float): Int = if (x.isNaN()) 0 else x.roundToInt()

actual fun mathRound(x: Double): Long = if (x.isNaN()) 0L else x.roundToLong()

/** The JDK 9+ form: one multiply by the rounded constant. */
actual fun mathToRadians(angdeg: Double): Double = angdeg * 0.017453292519943295

/** The JDK 9+ form: one multiply by the rounded constant. */
actual fun mathToDegrees(angrad: Double): Double = angrad * 57.29577951308232

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
