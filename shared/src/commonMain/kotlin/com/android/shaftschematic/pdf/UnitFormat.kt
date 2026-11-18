package com.android.shaftschematic.pdf

import com.android.shaftschematic.pdf.notes.DiaToleranceText
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.formatFixedUs
import com.android.shaftschematic.util.mathRound

/** Printed inch lengths: fractions down to 1/32, the same finest step the input fields show. */
private val PRINT_LENGTH_INCH_FORMAT = LengthFormat.InchFormatOptions(maxDenominator = 32)

/**
 * Formats a *length* dimension for PDF labels.
 *
 * - Model geometry remains in millimeters.
 * - In inches mode, prefers mixed fractions to the nearest 1/32 (reduced) — the shop's finest
 *   step, so a typed `12 11/32` prints as itself, not as `12.344"` (on-device ruling) — with
 *   fallback to 3-decimal inches.
 */
fun formatLenDim(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase() ?: "MM"
    return when {
        name.contains("INCH") -> LengthFormat.formatInchesSmart(mm / 25.4, PRINT_LENGTH_INCH_FORMAT) + "\""
        else -> formatFixedUs(mm, 3) + " mm"
    }
}

/**
 * Formats a *length* for footer fields, always including a unit suffix.
 *
 * - Inches: the smart inch formatter, fractions to the nearest 1/32 as [formatLenDim].
 * - Millimeters: uses a compact 1-decimal format to keep footer lines readable.
 */
fun formatLenWithUnit(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase() ?: "MM"
    return when {
        name.contains("INCH") -> LengthFormat.formatInchesSmart(mm / 25.4, PRINT_LENGTH_INCH_FORMAT) + "\""
        else -> {
            val s = formatFixedUs(mm, 1).trimEnd('0').trimEnd('.')
            "$s mm"
        }
    }
}

/**
 * Formats a *diameter* for footer fields and callouts, always including a unit suffix.
 *
 * - Inches: a whole number prints bare (`11"`); anything else prints all three decimals
 *   (`10.990"`, `10.500"`). Trimming trailing zeros made one sheet read `10.99"` beside `8.266"`
 *   (on-device report), so a value with any decimal keeps the same three places — and an exact
 *   odd sixteenth takes its fourth (`10.9375"`, [LengthFormat.formatInchesDecimal]).
 * - Millimeters: compact 1-decimal, trailing zeros trimmed.
 */
fun formatDiaWithUnit(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase() ?: "MM"
    return when {
        name.contains("INCH") -> {
            val inches = mm / 25.4
            val rounded = mathRound(inches)
            val s = if (kotlin.math.abs(inches - rounded) < 5e-4) {
                rounded.toString()
            } else {
                LengthFormat.formatInchesDecimal(inches)
            }
            "$s\""
        }
        else -> {
            val s = formatFixedUs(mm, 1).trimEnd('0').trimEnd('.')
            "$s mm"
        }
    }
}

/**
 * A liner or body Ø tolerance as its callout prints it — the two limits a stacked tolerance sets
 * after the value (`Ø 9.000"` then `+.002` over `-.000`), or null when neither side is > 0.
 * [plusMm] and [minusMm] are the stored MAGNITUDES of the upper and lower deviation.
 *
 * - Inches (drafting practice): sign + three decimals with no leading zero (`+.002`, `-.000`); a
 *   limit of an inch or more keeps its integer part (`+1.250`). A zero side keeps its sign and
 *   decimals. Both limits take the same places — four when either needs a fourth to print exactly
 *   (`+.0005`), never more.
 * - Millimetres (ISO practice): sign + the fewest decimals (1 to 3) both limits print exactly at,
 *   shared by the two (`+0.05` / `-0.10`); a zero side prints a bare `0`.
 *
 * No unit mark on either limit: they read in the value's unit. Pure — the one formatter behind the
 * callout builders and their tests.
 */
fun formatDiaTolerance(plusMm: Double, minusMm: Double, unit: UnitSystem): DiaToleranceText? {
    val plus = plusMm.coerceAtLeast(0.0)
    val minus = minusMm.coerceAtLeast(0.0)
    if (plus <= 0.0 && minus <= 0.0) return null
    return if (unit == UnitSystem.INCHES) {
        val pIn = plus / 25.4
        val mIn = minus / 25.4
        fun exactAt(v: Double, d: Int): Boolean {
            val scaled = v * pow10(d)
            return kotlin.math.abs(scaled - mathRound(scaled)) < 1e-3
        }
        val places = if (exactAt(pIn, 3) && exactAt(mIn, 3)) 3 else 4
        fun limit(sign: String, v: Double): String {
            val s = formatFixedUs(v, places)
            return sign + if (s.startsWith("0.")) s.substring(1) else s
        }
        DiaToleranceText(upper = limit("+", pIn), lower = limit("-", mIn))
    } else {
        fun exactAt(v: Double, d: Int): Boolean {
            val scaled = v * pow10(d)
            return kotlin.math.abs(scaled - mathRound(scaled)) < 1e-2
        }
        val places = (1..3).firstOrNull { d -> exactAt(plus, d) && exactAt(minus, d) } ?: 3
        fun limit(sign: String, v: Double): String =
            if (v <= 0.0) "0" else sign + formatFixedUs(v, places)
        DiaToleranceText(upper = limit("+", plus), lower = limit("-", minus))
    }
}

/**
 * A toleranced Ø value as its callout prints it: [formatDiaWithUnit], except that a WHOLE inch
 * value, which would print bare (`9"`), takes the limits' decimal places (`9.000"`) — drafting
 * practice sets an inch value and its tolerance to the same places. Millimetres print unchanged.
 */
fun formatTolerancedDiaWithUnit(mm: Double, unit: UnitSystem, tolerance: DiaToleranceText): String {
    val plain = formatDiaWithUnit(mm, unit)
    if (unit != UnitSystem.INCHES || '.' in plain) return plain
    val places = tolerance.upper.substringAfter('.', "").length.coerceIn(3, 4)
    return formatFixedUs(mm / 25.4, places) + "\""
}

private fun pow10(d: Int): Double {
    var r = 1.0
    repeat(d) { r *= 10.0 }
    return r
}

/**
 * Formats a THREAD's major diameter for the footer's "Thread:" line — the one exception to the
 * three-decimal diameter rule of [formatDiaWithUnit] (on-device ruling).
 *
 * - Inches: a size on the 1/32 grid prints as its fraction, exactly as the thread's length on
 *   the same line does (`5 1/2"`, `3 3/4"`, a whole number bare) — a decimal major Ø beside a
 *   fractional length mixed two notations within one thread callout (on-device report). A size
 *   off that grid prints at most three decimals with trailing zeros dropped (`4.123"`). A
 *   thread's major Ø is a nominal size, called out the way it is written, not a ground
 *   diameter read off a micrometer.
 * - Millimeters: the same compact 1-decimal form as [formatDiaWithUnit].
 */
fun formatThreadDiaWithUnit(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase() ?: "MM"
    if (!name.contains("INCH")) return formatDiaWithUnit(mm, unit)
    val inches = mm / 25.4
    val onGrid = LengthFormat.formatInchesSmart(inches, PRINT_LENGTH_INCH_FORMAT)
    val s = if ('.' !in onGrid) onGrid
    else LengthFormat.formatInchesDecimal(inches).trimEnd('0').trimEnd('.')
    return "$s\""
}

/**
 * The unit a dual value's SECONDARY term prints in — simply the other one.
 */
fun dualSecondaryUnit(primary: UnitSystem): UnitSystem =
    if (primary == UnitSystem.INCHES) UnitSystem.MILLIMETERS else UnitSystem.INCHES

/**
 * Dual-unit rendering: a [primary] value plus the converted [secondary] one.
 *
 * BOTH terms always keep their unit suffix — the "every dimension carries its own unit" safety
 * rule: on a sheet that mixes units a bare number is how a shaft gets machined wrong.
 *
 * **The secondary is formatted COMPACTLY, not with the primary's formatter.** A converted value is
 * a courtesy, not a measurement, so it prints at the precision the unit deserves (mm to 1 decimal,
 * trailing zeros trimmed) rather than inheriting a dimension formatter's 3 decimals. Reusing the
 * primary's formatter put `[3378.200 mm]` and `[649.287 mm]` on the rails while the Ø callouts on
 * the same sheet read `[279.4 mm]` — two precision conventions per sheet, and roughly 17 pt of
 * label width per rail spent on conversion noise (on-device sheet, `docs/DualUnitStacking_PLAN.md`
 * §1c).
 *
 * Two renderings exist and both come from here: the inline one-liner (`*Dual`, returning a
 * `String`) and the two-term [DualLabel] (`*DualLabel`) that a stacked layout needs. Whether a
 * dual label is SET stacked is a layout decision made at the draw site
 * (`util/DualLabelRenderer.kt`), never here.
 *
 * When [dual] is false all of these collapse to the plain single-unit formatters above.
 */
private fun composeDual(
    primary: UnitSystem,
    dual: Boolean,
    fmt: (UnitSystem) -> String,
    fmtSecondary: (UnitSystem) -> String = fmt,
): DualLabel {
    val p = fmt(primary)
    if (!dual) return DualLabel.single(p)
    return DualLabel(p, fmtSecondary(dualSecondaryUnit(primary)))
}

/** Dual-aware [formatLenDim], inline. The secondary takes the compact length format. */
fun formatLenDimDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    formatLenDimDualLabel(mm, primary, dual).inline()

/** Dual-aware [formatLenWithUnit], inline. */
fun formatLenWithUnitDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    formatLenWithUnitDualLabel(mm, primary, dual).inline()

/** Dual-aware [formatDiaWithUnit], inline. */
fun formatDiaWithUnitDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    formatDiaWithUnitDualLabel(mm, primary, dual).inline()

/** Dual-aware [formatThreadDiaWithUnit], inline. The secondary takes the same thread form. */
fun formatThreadDiaWithUnitDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    composeDual(primary, dual, fmt = { formatThreadDiaWithUnit(mm, it) }).inline()

/**
 * [formatLenDim] as a two-term label. The primary keeps the dimension formatter (a mm PRIMARY
 * still prints 3 decimals — unchanged single-unit output); the secondary takes the compact
 * [formatLenWithUnit] form.
 */
fun formatLenDimDualLabel(mm: Double, primary: UnitSystem, dual: Boolean): DualLabel =
    composeDual(primary, dual, fmt = { formatLenDim(mm, it) }, fmtSecondary = { formatLenWithUnit(mm, it) })

/** [formatLenWithUnit] as a two-term label — already the compact form on both sides. */
fun formatLenWithUnitDualLabel(mm: Double, primary: UnitSystem, dual: Boolean): DualLabel =
    composeDual(primary, dual, fmt = { formatLenWithUnit(mm, it) })

/** [formatDiaWithUnit] as a two-term label — compact mm, inches whole or 3-decimal (4 at an exact odd sixteenth). */
fun formatDiaWithUnitDualLabel(mm: Double, primary: UnitSystem, dual: Boolean): DualLabel =
    composeDual(primary, dual, fmt = { formatDiaWithUnit(mm, it) })
