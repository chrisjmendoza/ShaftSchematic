package com.android.shaftschematic.ui.config

/**
 * Central, app-wide defaults for new components.
 *
 * Contract:
 *  • Inch presets are authoritative for user intent; real defaulting converts them
 *    to mm in [com.android.shaftschematic.ui.viewmodel.SessionAddDefaults.initial].
 *  • User-facing intent for threads is expressed in in/TPI, then converted to mm.
 */
object AddDefaultsConfig {
    // ---- Inch presets (authoritative for user intent) ------------------------
    const val BODY_LEN_IN   = 16f
    const val LINER_LEN_IN  = 16f
    const val TAPER_LEN_IN  = 12f

    const val BODY_DIA_IN   = 7f
    const val LINER_OD_IN   = 8f
    const val TAPER_SET_IN  = 6f
    const val TAPER_LET_IN  = 7f

    // Thread defaults per spec: 6" length, 5" major, 4 TPI
    const val THREAD_LEN_IN      = 6f
    const val THREAD_MAJ_DIA_IN  = 5f
    const val THREAD_TPI_IN      = 4f

    /** Fallback bare-shaft Ø (7 in) when an auto-body has no neighbor to derive from. */
    const val BODY_DIA_MM        = 177.8f

    // ---- Coupler bolt slot defaults ------------------------------------------
    // 0.5" hole, 2 cutouts, 2" pitch, 0.25" blind depth.
    const val SLOT_HOLE_DIA_IN   = 0.5f
    const val SLOT_SPACING_IN    = 2f
    const val SLOT_DEPTH_IN      = 0.25f
    const val SLOT_COUNT_DEFAULT = 2
}
