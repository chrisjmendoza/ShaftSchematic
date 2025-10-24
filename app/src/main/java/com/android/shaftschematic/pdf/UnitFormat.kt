package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.UnitSystem
import java.util.Locale

/**
 * Formats a canonical mm value for display in the selected unit system.
 * Detection is name-based to avoid coupling to specific enum constants.
 */
fun formatDim(mm: Double, unit: UnitSystem): String {
    // Accept common enum names without importing specific constants
    val name = unit.name.uppercase(Locale.US)
    val isInch =
        name.contains("INCH") ||    // INCH, INCHES
            name.contains("IMPERIAL")   // IMPERIAL, etc.

    return if (isInch) {
        String.format(Locale.US, "%.3f in", mm / 25.4)
    } else {
        String.format(Locale.US, "%.3f mm", mm)
    }
}
