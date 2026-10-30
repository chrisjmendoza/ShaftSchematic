package com.android.shaftschematic.util

import java.util.Locale

/** The fraction chart's intervals, finest last. */
val FRACTION_CHART_INTERVALS: List<Int> = listOf(8, 16, 32, 64)

/** The interval the chart opens on — the shop's finest working step (on-device request). */
const val FRACTION_CHART_DEFAULT_INTERVAL = 32

/**
 * One row of the fraction chart: [numerator]/[denominator] of an inch, reduced, with its exact
 * decimal and its millimetres.
 *
 * [isMajor] marks the eighths when the chart is finer than eighths, so the eye can find its
 * place in a long column the way a printed decimal-equivalent chart sets them bold.
 */
data class FractionChartRow(
    val numerator: Int,
    val denominator: Int,
    val fraction: String,
    val decimal: String,
    val millimeters: String,
    val isMajor: Boolean,
)

/**
 * Every step of 1/[interval] inch from the first up to 1, as chart rows.
 *
 * Decimals are EXACT, never rounded: a 1/2ⁿ inch terminates, so each one is written to the
 * places its interval needs (`0.125` for eighths through `0.015625` for 64ths) and every row in
 * a column carries the same number of places, so the decimal points line up. Millimetres are a
 * conversion, three decimals.
 */
fun fractionChartRows(interval: Int): List<FractionChartRow> {
    require(interval in FRACTION_CHART_INTERVALS) { "interval $interval" }
    val places = decimalPlacesFor(interval)
    return (1..interval).map { n ->
        val g = gcd(n, interval)
        val num = n / g
        val den = interval / g
        val inches = n.toDouble() / interval
        FractionChartRow(
            numerator = num,
            denominator = den,
            fraction = if (den == 1) "$num" else "$num/$den",
            decimal = "%.${places}f".format(Locale.US, inches),
            millimeters = "%.3f".format(Locale.US, inches * 25.4),
            isMajor = interval > 8 && den <= 8,
        )
    }
}

/** Places that write every step of 1/[interval] exactly: 1/2ⁿ terminates after n places. */
private fun decimalPlacesFor(interval: Int): Int = Integer.numberOfTrailingZeros(interval)

private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) { val t = x % y; x = y; y = t }
    return x
}
