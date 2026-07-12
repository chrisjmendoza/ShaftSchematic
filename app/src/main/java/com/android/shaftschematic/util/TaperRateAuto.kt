package com.android.shaftschematic.util

import java.util.Locale
import kotlin.math.abs

private const val DEFAULT_SLOPE_ERROR_TOLERANCE = 0.03f

val DEFAULT_COMMON_TAPER_ONE_TO_N: List<Float> = listOf(20f, 16f, 14f, 12f, 10f, 8f)

/**
 * Computes a display-friendly taper rate text from length/diameters.
 *
 * Returns either:
 * - a snapped common taper (for example, "1:16") when within tolerance, or
 * - an exact one-to-N form with fixed decimals (for example, "1:15.875").
 */
fun autoTaperRateText(
    lengthMm: Float,
    setDiaMm: Float,
    letDiaMm: Float,
    commonOneToN: List<Float> = DEFAULT_COMMON_TAPER_ONE_TO_N,
    maxRelativeSlopeError: Float = DEFAULT_SLOPE_ERROR_TOLERANCE,
    exactDecimals: Int = 3,
): String? {
    if (lengthMm <= 0f) return null

    val diaDelta = abs(letDiaMm - setDiaMm)
    if (diaDelta <= 0f) return null

    val exactSlope = diaDelta / lengthMm
    if (exactSlope <= 0f) return null

    val nearestCommon = commonOneToN
        .filter { it > 0f }
        .map { n ->
            val slope = 1f / n
            val relErr = abs(slope - exactSlope) / exactSlope
            Triple(n, slope, relErr)
        }
        .minByOrNull { it.third }

    if (nearestCommon != null && nearestCommon.third <= maxRelativeSlopeError) {
        return "1:${formatOneToN(nearestCommon.first, decimals = 0, trimTrailingZeros = true)}"
    }

    val exactN = lengthMm / diaDelta
    return "1:${formatOneToN(exactN, decimals = exactDecimals, trimTrailingZeros = false)}"
}

private fun formatOneToN(value: Float, decimals: Int, trimTrailingZeros: Boolean): String {
    val fmt = "%.${decimals}f"
    val out = String.format(Locale.US, fmt, value.toDouble())
    return if (trimTrailingZeros) out.trimEnd('0').trimEnd('.') else out
}
