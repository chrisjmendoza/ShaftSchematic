package com.android.shaftschematic.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object LengthFormat {

    data class InchFormatOptions(
        val maxDenominator: Int = 16,
        val snapToleranceInches: Double = 1e-4, // ~0.0001" ≈ 0.0025 mm
        val decimalPlaces: Int = 3
    )

    private val unicodeFractions = mapOf(
        "1/2" to "½", "1/4" to "¼", "3/4" to "¾",
        "1/8" to "⅛", "3/8" to "⅜", "5/8" to "⅝", "7/8" to "⅞",
    )

    fun formatInchesSmart(inches: Double, opts: InchFormatOptions = InchFormatOptions()): String {
        if (abs(inches) < 1e-9) return "0." + "0".repeat(opts.decimalPlaces)

        val sign = if (inches < 0) "-" else ""
        val a = abs(inches)

        val den = opts.maxDenominator
        val nRounded = (a * den).roundToInt()
        val snapped = nRounded.toDouble() / den

        if (abs(a - snapped) <= opts.snapToleranceInches) {
            val whole = floor(snapped).toInt()
            var n = ((snapped - whole) * den).roundToInt()

            var w = whole
            if (n >= den) {
                w += 1
                n -= den
            }

            if (n == 0) return sign + w.toString()

            val g = gcd(n, den)
            val nn = n / g
            val dd = den / g

            val frac = unicodeFractions["$nn/$dd"] ?: "$nn/$dd"
            return if (w == 0) "$sign$frac" else "$sign$w $frac"
        }

        return sign + formatDecimal(a, opts.decimalPlaces)
    }

    private fun formatDecimal(value: Double, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }
}
