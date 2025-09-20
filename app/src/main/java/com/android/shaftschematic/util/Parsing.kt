package com.android.shaftschematic.util

import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.model.MM_PER_IN

/** Parse user-entered number (in current unit) into mm for storage. */
fun parseToMm(raw: String, unit: UnitSystem): Double {
    val s = filterDecimalPermissive(raw).trim()
    val v = s.toDoubleOrNull() ?: 0.0
    return if (unit == UnitSystem.MILLIMETERS) v else v * MM_PER_IN
}
