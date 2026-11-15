package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec

/**
 * The shaft-space span a sheet has to DRAW, in mm from the AFT S.E.T. datum: the overall
 * length, widened by any thread EXCLUDED from the OAL — an excluded AFT thread starts before 0,
 * an excluded FWD thread ends past the OAL — so every component the sheet draws lands inside the
 * content box.
 *
 * ONE builder for every composer whose x map could otherwise start at a datum a drawn component
 * sits outside of. The schematic always mapped this span; the runout/consolidated sheet mapped
 * the S.E.T.-to-S.E.T. measurement window and let `xAt` extrapolate past it, which drew an
 * excluded AFT thread off the left edge of the page (on-device report). The measurement window
 * is still what the OAL rail dimensions and the S.E.T. faces are read from — this span is only
 * what the page must have room for.
 */
data class DrawnExtentMm(val startMm: Float, val endMm: Float) {
    val spanMm: Float get() = (endMm - startMm).coerceAtLeast(1f)
}

/** See [DrawnExtentMm]. */
fun ShaftSpec.drawnExtentMm(): DrawnExtentMm {
    val startMm = minOf(
        0f,
        threads.filter { it.excludeFromOAL && it.isAftEnd }.minOfOrNull { it.startFromAftMm } ?: 0f,
    )
    val endMm = maxOf(
        overallLengthMm,
        threads.filter { it.excludeFromOAL && !it.isAftEnd }
            .maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: overallLengthMm,
    )
    return DrawnExtentMm(startMm, endMm)
}
