package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.LengthFormat
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
