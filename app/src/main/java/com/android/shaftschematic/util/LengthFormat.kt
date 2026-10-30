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

    /**
     * Fractions come out as plain `n/d`, never as a Unicode vulgar glyph.
     *
     * The typography is the renderer's job ([FractionText] / [FractionTextRenderer] build the
     * stacked fraction at the draw site), and a font only carries vulgar glyphs for a handful
     * of denominators — which is what made a sheet mix a proper `⅝` with a typed-out `11/16`.
     * One spelling in, one look out.
     */
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

            val frac = "$nn/$dd"
            return if (w == 0) "$sign$frac" else "$sign$w $frac"
        }

        return sign + formatDecimal(a, opts.decimalPlaces)
    }

    /**
     * Decimal inches for print: three places, FOUR when the value is exactly an odd sixteenth
     * (`15/16` = `0.9375`), so a fraction whose decimal ends in the fourth place prints exactly
     * instead of rounded at the thousandth (on-device ruling). An odd 32nd still prints at three
     * — the ruling keeps the typical three and spends a fourth place only where it is exact.
     */
    fun formatInchesDecimal(inches: Double): String {
        val sixteenths = inches * 16.0
        val n = sixteenths.roundToInt()
        val exactOddSixteenth = n % 2 != 0 && abs(inches - n / 16.0) <= SIXTEENTH_TOLERANCE_IN
        return formatDecimal(inches, if (exactOddSixteenth) 4 else 3)
    }

    /**
     * How near an odd sixteenth a value must sit to print its fourth place. A diameter's `Float`
     * storage noise is under 0.000003″; a typed `10.9374` is not `10 15/16` and keeps three places.
     */
    private const val SIXTEENTH_TOLERANCE_IN = 1e-5

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
