package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.LengthFormat
import java.util.Locale

/**
 * Converts a canonical mm value to a display string in the active unit system.
 * Works with enums named MILLIMETERS/INCHES or any UnitSystem-like type
 * whose name includes "MM" or "INCH".
 */
fun formatDim(mm: Double, unit: Any?): String {
    val name = unit?.toString()?.uppercase(Locale.US) ?: "MM"
    return when {
        name.contains("INCH") -> {
            // Use 4 decimals for inches so common fractions (1/16, 1/32) round exactly.
            String.format(Locale.US, "%.4f in", mm / 25.4)
        }
        else -> {
            // Millimeters stay at 3 decimals – more than enough for typical shaft work.
            String.format(Locale.US, "%.3f mm", mm)
        }
    }
}

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
        name.contains("INCH") -> LengthFormat.formatInchesSmart(mm / 25.4) + " in"
        else -> String.format(Locale.US, "%.3f mm", mm)
    }
}
