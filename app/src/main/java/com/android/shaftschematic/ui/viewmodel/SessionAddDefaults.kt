package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.ui.config.AddDefaultsConfig

/**
 * Session-scoped (non-persisted) defaults for "Add component" actions.
 *
 * Contract
 * - Stored in canonical millimeters (mm).
 * - Reset when a new document is created or a document is loaded.
 * - Updated whenever the user adds or edits a component so subsequent adds reuse the last-used sizes.
 */
data class SessionAddDefaults(
    val bodyDiaMm: Float,
    val bodyLenMm: Float,
    val linerOdMm: Float,
    val linerLenMm: Float,
    val taperSetDiaMm: Float,
    val taperLetDiaMm: Float,
    val taperLenMm: Float,
    val threadMajorDiaMm: Float,
    val threadLenMm: Float,
    val threadPitchMm: Float,
) {
    companion object {
        private const val MM_PER_INCH = 25.4f

        fun initial(): SessionAddDefaults = SessionAddDefaults(
            bodyDiaMm = AddDefaultsConfig.BODY_DIA_IN * MM_PER_INCH,
            bodyLenMm = AddDefaultsConfig.BODY_LEN_IN * MM_PER_INCH,
            linerOdMm = AddDefaultsConfig.LINER_OD_IN * MM_PER_INCH,
            linerLenMm = AddDefaultsConfig.LINER_LEN_IN * MM_PER_INCH,
            taperSetDiaMm = AddDefaultsConfig.TAPER_SET_IN * MM_PER_INCH,
            taperLetDiaMm = AddDefaultsConfig.TAPER_LET_IN * MM_PER_INCH,
            taperLenMm = AddDefaultsConfig.TAPER_LEN_IN * MM_PER_INCH,
            threadMajorDiaMm = AddDefaultsConfig.THREAD_MAJ_DIA_IN * MM_PER_INCH,
            threadLenMm = AddDefaultsConfig.THREAD_LEN_IN * MM_PER_INCH,
            threadPitchMm = MM_PER_INCH / AddDefaultsConfig.THREAD_TPI_IN,
        )
    }
}
