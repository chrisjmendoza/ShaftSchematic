package com.android.shaftschematic.pdf

import com.android.shaftschematic.util.UnitSystem
import java.util.Locale

/**
 * Formats a canonical mm value for display in the selected unit system.
 * Internal math stays in mm; only string output changes.
 */
fun formatDim(mm: Double, unit: UnitSystem): String {
    return when (unit) {
        UnitSystem.INCH -> String.format(Locale.US, "%.3f in", mm / 25.4)
        UnitSystem.MM   -> String.format(Locale.US, "%.3f mm", mm)
    }
}
