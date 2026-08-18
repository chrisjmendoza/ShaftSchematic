package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.UnitSystem
import java.util.Locale

/**
 * Formats a *length* dimension for PDF labels.
 *
 * - Model geometry remains in millimeters.
 * - In inches mode, prefers mixed fractions snapped to nearest 1/16 (reduced),
 *   with fallback to 3-decimal inches.
 */
fun formatLenDim(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase(Locale.US) ?: "MM"
    return when {
        name.contains("INCH") -> LengthFormat.formatInchesSmart(mm / 25.4) + "\""
        else -> String.format(Locale.US, "%.3f mm", mm)
    }
}

/**
 * Formats a *length* for footer fields, always including a unit suffix.
 *
 * - Inches: uses the existing smart inch formatter (fractions when appropriate).
 * - Millimeters: uses a compact 1-decimal format to keep footer lines readable.
 */
fun formatLenWithUnit(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase(Locale.US) ?: "MM"
    return when {
        name.contains("INCH") -> LengthFormat.formatInchesSmart(mm / 25.4) + "\""
        else -> {
            val s = String.format(Locale.US, "%.1f", mm).trimEnd('0').trimEnd('.')
            "$s mm"
        }
    }
}

/**
 * Formats a *diameter* for footer fields, always including a unit suffix.
 *
 * - Inches: fixed 3 decimals (shop print convention).
 * - Millimeters: compact 1-decimal.
 */
fun formatDiaWithUnit(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase(Locale.US) ?: "MM"
    return when {
        name.contains("INCH") -> {
            val s = String.format(Locale.US, "%.3f", mm / 25.4).trimEnd('0').trimEnd('.')
            "$s\""
        }
        else -> {
            val s = String.format(Locale.US, "%.1f", mm).trimEnd('0').trimEnd('.')
            "$s mm"
        }
    }
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

/** [formatDiaWithUnit] as a two-term label — compact mm, shop 3-decimal inches. */
fun formatDiaWithUnitDualLabel(mm: Double, primary: UnitSystem, dual: Boolean): DualLabel =
    composeDual(primary, dual, fmt = { formatDiaWithUnit(mm, it) })
