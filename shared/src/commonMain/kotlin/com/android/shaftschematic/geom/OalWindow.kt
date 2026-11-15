package com.android.shaftschematic.geom

/**
 * Measurement window used for PDF dimensions.
 * All measurement-space X values are rebased so x=0 is the first counted AFT surface.
 */
data class OalWindow(
    val measureStartMm: Double,
    val measureEndMm: Double
) {
    val oalMm: Double get() = (measureEndMm - measureStartMm).coerceAtLeast(0.0)
    fun toMeasureX(rawX: Double): Double = rawX - measureStartMm
}
