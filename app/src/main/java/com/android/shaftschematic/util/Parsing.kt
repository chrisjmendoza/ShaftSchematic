package com.android.shaftschematic.util

import com.android.shaftschematic.model.MM_PER_IN

/**
 * Parses user input text into a canonical millimeter Double.
 *
 * Rules:
 * - Filters out non-numeric characters (via filterDecimalPermissive).
 * - Respects the provided UnitSystem (millimeters or inches).
 * - Empty or invalid input yields 0.0.
 *
 * This keeps parsing neutral: do not clamp negatives or enforce ranges here.
 * Callers (e.g., ViewModel setters) can layer validation if needed.
 */
fun parseToMm(raw: String, unit: UnitSystem): Double {
    val s = filterDecimalPermissive(raw).trim()
    val v = s.toDoubleOrNull() ?: 0.0
    return if (unit == UnitSystem.MILLIMETERS) v else v * MM_PER_IN
}
