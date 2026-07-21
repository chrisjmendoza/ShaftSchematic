package com.android.shaftschematic.util

/**
 * Format a canonical mm runout (TIR) value into the active [unit] for display: convert, then trim
 * to a sensible precision (4 decimals for inches, 3 for mm) with trailing zeros stripped. Unit
 * conversion happens only here, at the UI/output edge — the model always stores canonical mm.
 *
 * Shared by the runout bubble editor dialog, the on-screen preview, and the PDF composer so the
 * value renders identically everywhere. See `docs/RunoutBubbleEditor_PLAN.md`.
 */
fun formatRunoutValue(valueMm: Float, unit: UnitSystem): String {
    val display = unit.fromMillimeters(valueMm.toDouble())
    val decimals = if (unit == UnitSystem.INCHES) 4 else 3
    val trimmed = "%.${decimals}f".format(display).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    // Drop the leading zero before the decimal (0.003 → .003, -0.003 → -.003) so the value
    // fits the bubble better — matches how a machinist hand-writes a TIR reading.
    return when {
        trimmed.startsWith("0.")  -> trimmed.substring(1)
        trimmed.startsWith("-0.") -> "-" + trimmed.substring(2)
        else -> trimmed
    }
}
