package com.android.shaftschematic.geom

/**
 * The wear document's MAIN profile band and the shared per-job "Shaft height" multiplier
 * (`RunoutConfig.heightScale` — ONE value behind every drawing).
 *
 * The wear sheet has no compression solve: its profile draws at ONE flat pt/mm fitted to the
 * SET-to-SET span, so the shaft's NATURAL (100%) drawn height is simply `maxDiaMm × ptPerMm`.
 * This is the multiplier that height rides, and it reaches the profile band only — the detail
 * strips carry their own scale and never see it.
 *
 * Pure: the composer and any slider read the same arithmetic, so the reported paper inches and
 * the drawn shaft can never disagree.
 */

/**
 * Multiplier on the wear profile's drawn RADIUS (and therefore its drawn height and its height
 * budget) for a per-job [heightFrac], given the profile's natural drawn height [naturalHeightPt]
 * at 100%.
 *
 * [heightFrac] is clamped to the shared multiplier bounds, and the resulting drawn height to the
 * absolute paper band [PROFILE_MIN_SHAFT_HEIGHT_PT] … [PROFILE_MAX_SHAFT_HEIGHT_PT] — the same
 * band [exaggeratedProfileScale] holds the schematic and runout sheets to.
 *
 * Neither end of the band ever moves a shaft AWAY from where it naturally draws: the floor never
 * raises a shaft whose natural height is already under it (the sizing-curve posture — a small
 * shaft is never fattened into something it isn't), and the ceiling likewise never lowers one that
 * naturally draws taller. The natural height here is a page-WIDTH fit rather than a solve the band
 * already bounded, so 100% returns exactly `1f` on every shaft — the sheet a job without the
 * slider prints is unchanged.
 */
fun wearProfileHeightScale(heightFrac: Float, naturalHeightPt: Float): Float {
    if (naturalHeightPt <= 0f) return 1f
    val frac = heightFrac.coerceIn(PROFILE_HEIGHT_SCALE_MIN, PROFILE_HEIGHT_SCALE_MAX)
    val ceiling = maxOf(PROFILE_MAX_SHAFT_HEIGHT_PT, naturalHeightPt) / naturalHeightPt
    val floor = minOf(PROFILE_MIN_SHAFT_HEIGHT_PT, naturalHeightPt) / naturalHeightPt
    return frac.coerceIn(floor, ceiling)
}

/** Drawn wear-profile height (pt) [heightFrac] produces — [wearProfileHeightScale] applied. */
fun wearProfileDrawnHeightPt(heightFrac: Float, naturalHeightPt: Float): Float =
    naturalHeightPt * wearProfileHeightScale(heightFrac, naturalHeightPt)
