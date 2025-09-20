package com.android.shaftschematic.util

import kotlin.math.atan
import kotlin.math.tan
import kotlin.math.PI
import kotlin.math.abs

private fun ratioToAngleDeg(perFoot: Double): Double =
    Math.toDegrees(atan(perFoot / 12.0))

private fun angleToPerFoot(angleDeg: Double): Double =
    12.0 * tan(angleDeg / 180.0 * PI)

/**
 * Returns a friendly display, e.g. "1:12 (≈ 1.62°)".
 * Accepts:
 *  - "1:12", "1/12"
 *  - "1.62°", "1.62 deg"
 *  - plain numbers: <= 0.2 treated as pure ratio (e.g., 0.0833 ≈ 1/12),
 *    else treated as inches-per-foot directly.
 */
fun parseTaperDisplay(input: String): String? {
    val s = input.trim().lowercase()
    if (s.isEmpty()) return null

    // "1:12" or "1/12"
    Regex("""^\s*([0-9]*\.?[0-9]+)\s*[:/]\s*([0-9]*\.?[0-9]+)\s*$""")
        .matchEntire(s)?.let { m ->
            val a = m.groupValues[1].toDoubleOrNull()
            val b = m.groupValues[2].toDoubleOrNull()
            if (a != null && b != null && b != 0.0) {
                val perFoot = 12.0 * (a / b)
                val angle = ratioToAngleDeg(perFoot)
                return "${trimTrailing(a)}:${trimTrailing(b)} (≈ ${"%.2f".format(angle)}°)"
            }
        }

    // angle with suffix
    Regex("""^\s*([0-9]*\.?[0-9]+)\s*(deg|°)\s*$""")
        .matchEntire(s)?.let { m ->
            val angle = m.groupValues[1].toDoubleOrNull()
            if (angle != null) {
                val perFoot = angleToPerFoot(angle)
                val ratio = 12.0 / perFoot
                return "1:${"%.0f".format(ratio)} (≈ ${"%.2f".format(angle)}°)"
            }
        }

    // plain number
    s.toDoubleOrNull()?.let { x ->
        val perFoot = if (x <= 0.2) 12.0 * x else x
        val angle = ratioToAngleDeg(perFoot)
        val ratio = 12.0 / perFoot
        return "1:${"%.0f".format(ratio)} (≈ ${"%.2f".format(angle)}°)"
    }

    // fallback
    return input
}

private fun trimTrailing(d: Double): String {
    val s = d.toString()
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
