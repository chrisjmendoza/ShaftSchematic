package com.android.shaftschematic.geom

/**
 * SET positions expressed in measurement-space.
 * Defaults: AFT SET at 0, FWD SET at OAL. Can later map to actual taper-derived SETs.
 */
data class SetPositions(
    val aftSETxMm: Double,
    val fwdSETxMm: Double
)
