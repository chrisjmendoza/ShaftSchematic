package com.android.shaftschematic.util

/**
 * Format a canonical mm runout (TIR) value into the active [unit] for display: convert, then show
 * a fixed **3 decimal places (thousandths) with trailing zeros kept**, in both units, so every
 * bubble reads at the same precision (.010 stays .010, not .01). Unit conversion happens only
 * here, at the UI/output edge — the model always stores canonical mm.
 *
 * Shared by the runout bubble editor dialog, the on-screen preview, and the PDF composer so the
 * value renders identically everywhere. See `docs/RunoutBubbleEditor_PLAN.md`.
 */
fun formatRunoutValue(valueMm: Float, unit: UnitSystem): String {
    val display = unit.fromMillimeters(valueMm.toDouble())
    // Fixed thousandths, trailing zeros KEPT. Normalize "-0.000" (a tiny negative that rounds to
    // zero) so it never prints a sign.
    val fixed = "%.3f".format(display).let { if (it == "-0.000") "0.000" else it }
    // Drop the leading zero before the decimal (0.010 → .010, -0.010 → -.010) so the value
    // fits the bubble better — matches how a machinist hand-writes a TIR reading.
    return when {
        fixed.startsWith("0.")  -> fixed.substring(1)
        fixed.startsWith("-0.") -> "-" + fixed.substring(2)
        else -> fixed
    }
}
