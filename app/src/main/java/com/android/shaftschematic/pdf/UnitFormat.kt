package com.android.shaftschematic.pdf

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
 * Inline dual-unit rendering: `<primary> [<secondary>]` — e.g. `1 1/2" [38.1 mm]`.
 *
 * Rendered as a SINGLE line (never a two-line stack). Dual growth is width-only, so it flows
 * through the same rich-text measurement path the fraction stacks already use — no height
 * constant or strip row/tier cap changes are required. A stacked two-line rendering is the
 * configuration that overflowed those fixed budgets before; do not reintroduce it here.
 *
 * The secondary reuses the SAME formatter as the primary, so it prints identically to how it
 * would as a primary value (inch fractions included), and BOTH terms keep their unit suffix —
 * the "every dimension carries its own unit" safety rule on a mixed sheet.
 *
 * When [dual] is false these collapse to the plain single-unit formatters above.
 */
private inline fun composeDual(primary: UnitSystem, dual: Boolean, fmt: (UnitSystem) -> String): String {
    val p = fmt(primary)
    if (!dual) return p
    val secondary = if (primary == UnitSystem.INCHES) UnitSystem.MILLIMETERS else UnitSystem.INCHES
    return "$p [${fmt(secondary)}]"
}

/** Dual-aware [formatLenDim]. */
fun formatLenDimDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    composeDual(primary, dual) { formatLenDim(mm, it) }

/** Dual-aware [formatLenWithUnit]. */
fun formatLenWithUnitDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    composeDual(primary, dual) { formatLenWithUnit(mm, it) }

/** Dual-aware [formatDiaWithUnit]. */
fun formatDiaWithUnitDual(mm: Double, primary: UnitSystem, dual: Boolean): String =
    composeDual(primary, dual) { formatDiaWithUnit(mm, it) }
