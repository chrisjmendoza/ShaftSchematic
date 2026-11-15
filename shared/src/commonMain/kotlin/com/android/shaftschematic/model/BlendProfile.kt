package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * How a blended face eases between its own diameter and its neighbour's.
 *
 * A **blend** is a machined smooth transition between two diameters — no square shoulder
 * and no dimensioned taper rate. It is a silhouette feature only: it carries no dimension
 * rail and no footer row, so no value here ever reaches a printed number.
 *
 * The drawn curve for each entry is built by `geom/BlendProfileMath.kt` (`easeAftFrac` /
 * `easeFwdFrac` → [com.android.shaftschematic.geom.blendPolyline]); this enum carries only
 * the stored choice.
 */
@Serializable
enum class BlendProfile {
    /** Pure S-curve: tangent at BOTH ends, inflection at mid-span. No corner anywhere. */
    OGEE,

    /** Tangent at the LARGE end only; the small end meets its neighbour at a corner. */
    FILLET,

    /** Straight cone through the middle with both corners eased. */
    EASED_CONE,
}
