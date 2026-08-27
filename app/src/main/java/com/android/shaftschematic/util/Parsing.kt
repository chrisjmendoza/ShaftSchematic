package com.android.shaftschematic.util

import com.android.shaftschematic.model.MM_PER_IN
import kotlin.math.abs

/**
 * Parses user input text into a canonical millimeter Double.
 *
 * Rules:
 * - Respects the provided UnitSystem (millimeters or inches).
 * - Empty or invalid input yields 0.0.
 *
 * This keeps parsing neutral: do not clamp negatives or enforce ranges here.
 * Callers (e.g., ViewModel setters) can layer validation if needed.
 */
fun parseToMm(raw: String, unit: UnitSystem): Double {
    val v = parseFractionOrDecimal(raw) ?: return 0.0
    return if (unit == UnitSystem.MILLIMETERS) v else v * MM_PER_IN
}

/**
 * Parses a user-entered number that may be decimal, a shop fraction, or a taper-rate ratio.
 *
 * Supported:
 * - Decimal: "12", "1.25"
 * - Fraction: "3/4"
 * - Mixed fraction: "15 1/2"
 * - Ratio: "1:12" (a colon-separated N:D, e.g. a taper rate)
 *
 * Also tolerates trailing unit suffixes like "in", "mm", or quotes.
 */
fun parseFractionOrDecimal(raw: String): Double? {
    val t = normalizeNumericText(raw)
    if (t.isEmpty()) return null

    // Mixed fraction: W N/D
    val parts = t.split(' ').filter { it.isNotBlank() }
    if (parts.size == 2 && parts[1].contains('/')) {
        val whole = parts[0].toDoubleOrNull() ?: return null
        val frac = parseSimpleFraction(parts[1]) ?: return null
        return if (whole < 0) whole - frac else whole + frac
    }

    // Ratio: N:D
    if (t.contains(':')) return parseSimpleRatio(t)

    // Simple fraction: N/D
    if (t.contains('/')) return parseSimpleFraction(t)

    return t.toDoubleOrNull()
}

private fun parseSimpleFraction(text: String): Double? {
    val s = text.trim()
    val slash = s.indexOf('/')
    if (slash <= 0 || slash >= s.lastIndex) return null
    val a = s.substring(0, slash).trim().toDoubleOrNull() ?: return null
    val b = s.substring(slash + 1).trim().toDoubleOrNull() ?: return null
    if (abs(b) < 1e-12) return null
    return a / b
}

private fun parseSimpleRatio(text: String): Double? {
    val s = text.trim()
    val colon = s.indexOf(':')
    if (colon <= 0 || colon >= s.lastIndex) return null
    val a = s.substring(0, colon).trim().toDoubleOrNull() ?: return null
    val b = s.substring(colon + 1).trim().toDoubleOrNull() ?: return null
    if (abs(b) < 1e-12) return null
    return a / b
}

private fun normalizeNumericText(raw: String): String {
    var s = raw.replace(",", "").trim()
    if (s.isEmpty()) return ""

    // Strip trailing unit-ish suffixes (letters/quotes/etc) while keeping numeric grammar.
    val allowed = "0123456789./:+- "
    var end = s.length - 1
    while (end >= 0 && !allowed.contains(s[end])) end--
    if (end < 0) return ""
    s = s.substring(0, end + 1).trim()

    // Normalize internal whitespace.
    return s.replace(Regex("\\s+"), " ")
}

/**
 * Parses user input text (decimal, shop fraction, or ratio — see [parseFractionOrDecimal]) and
 * converts to millimeters, returning `null` on blank or unparseable input. Unlike [parseToMm]
 * (which yields 0.0 on invalid input, for callers that always need a number), this is for
 * commit-on-blur field handlers that must distinguish "no edit" from "typed zero".
 */
fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val num = parseFractionOrDecimal(t) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) num.toFloat() else (num * MM_PER_IN).toFloat()
}
