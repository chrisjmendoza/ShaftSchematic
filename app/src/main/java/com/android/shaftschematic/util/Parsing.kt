package com.android.shaftschematic.util

import kotlin.math.abs

/*
 * The ONE numeric grammar every typed field reads (see `docs/contracts/NumberField.md`).
 *
 * A number is, after trimming and collapsing whitespace:
 * - a decimal: `12`, `1.25`, `.5`, `-3`;
 * - a fraction: `3/4`, `-1/4`;
 * - a mixed fraction, space- or hyphen-separated: `15 1/2`, `10-1/4`, `-10-1/4`. Only a `-`
 *   BETWEEN a whole number and a fraction is a separator; a leading `-` is a sign;
 * - a ratio `1:12` (a typed taper rate);
 * - a decimal comma: `1,5` and `12,75` read as 1.5 and 12.75 (a European-locale numeric keypad
 *   sends `,` as its decimal key). `1,500` — one comma before exactly three digits, with a
 *   non-zero whole part — could be 1.5 or 1500, so it is NOT a number. With a `.` present,
 *   commas are thousands separators only on 3-digit groups before the dot (`1,234.5`).
 *
 * A LENGTH may end in a unit suffix — `mm`, or `in`, `"`, `″`, `''` for inches, any case, with
 * or without a space before it. A suffix names the unit the value is in, whatever the document
 * unit: `260mm` in an inch drawing is 260 mm, `11"` in a millimetre drawing is 279.4 mm. Any
 * other trailing letters make the text not a number — a suffix is either read or refused, never
 * dropped.
 */

/** A typed value split into its numeric body and the unit its suffix names (null: no suffix). */
private class SuffixSplit(val body: String, val unit: UnitSystem?)

/** Recognised unit suffixes, checked against the END of the trimmed text, case-insensitively. */
private val UNIT_SUFFIXES: List<Pair<String, UnitSystem>> = listOf(
    "mm" to UnitSystem.MILLIMETERS,
    "in" to UnitSystem.INCHES,
    "''" to UnitSystem.INCHES,
    "\"" to UnitSystem.INCHES,
    "″" to UnitSystem.INCHES,
)

/** Characters a numeric body may hold; anything else (a letter, a stray mark) is not a number. */
private const val BODY_CHARS = "0123456789.,/:+- "

private fun splitUnitSuffix(raw: String): SuffixSplit? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    for ((suffix, unit) in UNIT_SUFFIXES) {
        if (s.endsWith(suffix, ignoreCase = true)) {
            return SuffixSplit(s.dropLast(suffix.length).trim(), unit)
        }
    }
    return SuffixSplit(s, null)
}

/**
 * Parses a UNITLESS number — decimal, shop fraction, mixed fraction, ratio or decimal comma (see
 * the grammar above) — returning `null` on blank or invalid input.
 *
 * Unit-blind by design, so a value carrying ANY unit suffix (`4in`, `260mm`, `11"`) is `null`
 * here: a caller that multiplied the result by the document unit would silently throw the
 * suffix away. Lengths and diameters go through [toMmOrNull], [parseToMm] or
 * [parseLengthInUnit], which read the suffix. This parser is for TPI, counts, ratios and runout
 * TIR values.
 */
fun parseFractionOrDecimal(raw: String): Double? {
    val split = splitUnitSuffix(raw) ?: return null
    if (split.unit != null) return null
    return parseNumericBody(split.body)
}

/**
 * Parses a typed LENGTH into canonical millimetres: the suffix's unit when the text carries one
 * (`260mm`, `11"`), [unit] (the document unit) otherwise. `null` on blank or invalid input.
 */
fun parseLengthMmOrNull(raw: String, unit: UnitSystem): Double? {
    val split = splitUnitSuffix(raw) ?: return null
    val v = parseNumericBody(split.body) ?: return null
    return (split.unit ?: unit).toMillimeters(v)
}

/**
 * Parses a typed LENGTH into [unit] — a suffix in another unit is converted (`20mm` in an inch
 * calculator reads 0.7874). For tools that compute in the entry unit rather than in mm.
 */
fun parseLengthInUnit(raw: String, unit: UnitSystem): Double? =
    parseLengthMmOrNull(raw, unit)?.let { unit.fromMillimeters(it) }

/** True when [raw] reads as a length (suffix or not) — the commit gate for length/Ø fields. */
fun isLengthText(raw: String): Boolean = parseLengthMmOrNull(raw, UnitSystem.MILLIMETERS) != null

/** True when [raw] reads as a unitless number — the commit gate for TPI and similar fields. */
fun isUnitlessNumberText(raw: String): Boolean = parseFractionOrDecimal(raw) != null

/**
 * Parses user input text into a canonical millimeter Double, honouring a unit suffix (see
 * [parseLengthMmOrNull]). Empty or invalid input yields 0.0, for callers that always need a
 * number.
 *
 * This keeps parsing neutral: do not clamp negatives or enforce ranges here.
 * Callers (e.g., ViewModel setters) can layer validation if needed.
 */
fun parseToMm(raw: String, unit: UnitSystem): Double = parseLengthMmOrNull(raw, unit) ?: 0.0

/**
 * Parses user input text (see [parseLengthMmOrNull]) into millimeters, returning `null` on blank
 * or unparseable input. Unlike [parseToMm] (which yields 0.0 on invalid input, for callers that
 * always need a number), this is for commit-on-blur field handlers that must distinguish
 * "no edit" from "typed zero".
 */
fun toMmOrNull(text: String, unit: UnitSystem): Float? = parseLengthMmOrNull(text, unit)?.toFloat()

/**
 * The value an edit dialog's Save commits for one field that opened on a STORED measurement.
 *
 * The field is seeded with a rounded display of [storedMm] (a TIR at 3 decimals, a length or Ø
 * at the card precision), so re-parsing an untouched seed would rewrite the typed value with its
 * own rounding — a `0.0015"` TIR saved back as `.002"` when only the high spot was moved
 * (emulator QA). Until the user has [edited] the field the stored value is returned untouched;
 * an edited text always goes through [parse], even one retyped to read exactly like the seed —
 * the user typed that value, so it is the value kept. A field that opened empty ([storedMm]
 * null) always parses.
 */
fun valueForSave(
    text: String,
    edited: Boolean,
    storedMm: Float?,
    parse: (String) -> Float?,
): Float? = if (storedMm != null && !edited) storedMm else parse(text)

/** `W-N/D` with an optional sign on the whole number: `10-1/4`, `-10-1/4`, `10 - 1/4`. */
private val HYPHEN_MIXED = Regex("""^([+-]?)(\d+)\s*-\s*(\d+(?:\.\d+)?\s*/\s*\d+(?:\.\d+)?)$""")

/** A decimal-comma value: one comma, digits after it, no dot. */
private val DECIMAL_COMMA = Regex("""^[+-]?\d*,\d+$""")

/** One comma before exactly three digits with a non-zero whole part — 1.5 or 1500? Refused. */
private val THOUSANDS_AMBIGUOUS = Regex("""^[+-]?[1-9]\d{0,2},\d{3}$""")

/** Thousands groups before a decimal point: `1,234.5`, `12,345,678.25`. */
private val THOUSANDS_WITH_DOT = Regex("""^[+-]?\d{1,3}(?:,\d{3})+\.\d*$""")

private fun parseNumericBody(body: String): Double? {
    if (body.isEmpty()) return null
    if (body.any { it !in BODY_CHARS && !it.isWhitespace() }) return null
    var t = body.trim().replace(Regex("\\s+"), " ")
    if (t.isEmpty()) return null

    if (t.contains(',')) {
        t = when {
            t.contains('.') ->
                if (THOUSANDS_WITH_DOT.matches(t)) t.replace(",", "") else return null
            THOUSANDS_AMBIGUOUS.matches(t) -> return null
            DECIMAL_COMMA.matches(t) -> t.replace(',', '.')
            else -> return null
        }
    }

    // Mixed fraction, hyphen-separated: W-N/D. Checked before the space form so "10 - 1/4"
    // is not split into "10", "-", "1/4".
    HYPHEN_MIXED.matchEntire(t)?.let { m ->
        val whole = m.groupValues[2].toDoubleOrNull() ?: return null
        val frac = parseSimpleFraction(m.groupValues[3]) ?: return null
        val magnitude = whole + frac
        return if (m.groupValues[1] == "-") -magnitude else magnitude
    }

    // Mixed fraction: W N/D
    val parts = t.split(' ').filter { it.isNotBlank() }
    if (parts.size == 2 && parts[1].contains('/')) {
        val whole = parts[0].toDoubleOrNull() ?: return null
        val frac = parseSimpleFraction(parts[1]) ?: return null
        return if (whole < 0 || parts[0].startsWith("-")) whole - frac else whole + frac
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
