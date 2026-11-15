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

/** C `printf` fixed-point through Foundation — the POSIX locale, whatever the device's. */
actual fun formatFixedDefaultLocale(value: Double, decimals: Int): String =
    NSString.stringWithFormat("%.${decimals}f", value)

actual fun formatFixedDefaultLocale(value: Float, decimals: Int): String =
    NSString.stringWithFormat("%.${decimals}f", value.toDouble())

actual fun sortedFloatSetOf(a: Float, b: Float): MutableSet<Float> = SortedFloatSet().apply { add(a); add(b) }

/**
 * A mutable set of floats iterated in ascending [Float.compareTo] order, de-duplicated by the same
 * comparison — the ordering and identity a JVM `TreeSet<Float>` gives.
 */
private class SortedFloatSet : AbstractMutableSet<Float>() {
    private val items = mutableListOf<Float>()

    override val size: Int get() = items.size

    override fun add(element: Float): Boolean {
        var lo = 0
        var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val c = items[mid].compareTo(element)
            if (c == 0) return false
            if (c < 0) lo = mid + 1 else hi = mid
        }
        items.add(lo, element)
        return true
    }

    override fun iterator(): MutableIterator<Float> = items.iterator()
}
