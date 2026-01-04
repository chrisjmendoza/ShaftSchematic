package com.android.shaftschematic.util

import com.android.shaftschematic.model.MM_PER_IN
import kotlin.math.abs

/**
 * Parses user input text into a canonical millimeter Double.
 *
 * Rules:
 * - Filters out non-numeric characters (via filterDecimalPermissive).
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
 * Parses a user-entered number that may be decimal or a shop fraction.
 *
 * Supported:
 * - Decimal: "12", "1.25"
 * - Fraction: "3/4"
 * - Mixed fraction: "15 1/2"
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

private fun normalizeNumericText(raw: String): String {
    var s = raw.replace(",", "").trim()
    if (s.isEmpty()) return ""

    // Strip trailing unit-ish suffixes (letters/quotes/etc) while keeping numeric grammar.
    val allowed = "0123456789./+- "
    var end = s.length - 1
    while (end >= 0 && !allowed.contains(s[end])) end--
    if (end < 0) return ""
    s = s.substring(0, end + 1).trim()

    // Normalize internal whitespace.
    return s.replace(Regex("\\s+"), " ")
}
