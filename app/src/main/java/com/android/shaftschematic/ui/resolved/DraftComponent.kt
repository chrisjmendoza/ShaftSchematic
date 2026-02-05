package com.android.shaftschematic.ui.resolved

/**
 * DraftComponent represents an in-progress component that exists ONLY in
 * physical shaft space (mm from AFT).
 *
 * Drafts do NOT store authored references (AFT/FWD) and must never mutate
 * ShaftSpec until committed.
 *
 * All authored reference conversion must happen BEFORE draft creation.
 */
sealed class DraftComponent {
    abstract val id: String
    abstract val startMmPhysical: Float
    abstract val lengthMm: Float

    data class Body(
        override val id: String,
        override val startMmPhysical: Float,
        override val lengthMm: Float,
        val diaMm: Float,
    ) : DraftComponent()

    data class Taper(
        override val id: String,
        override val startMmPhysical: Float,
        override val lengthMm: Float,
        val startDiaMm: Float,
        val endDiaMm: Float,
    ) : DraftComponent()

    data class Thread(
        override val id: String,
        override val startMmPhysical: Float,
        override val lengthMm: Float,
        val majorDiaMm: Float,
        val pitchMm: Float,
        val excludeFromOal: Boolean,
    ) : DraftComponent()

    data class Liner(
        override val id: String,
        override val startMmPhysical: Float,
        override val lengthMm: Float,
        val odMm: Float,
        val label: String? = null,
    ) : DraftComponent()
}
