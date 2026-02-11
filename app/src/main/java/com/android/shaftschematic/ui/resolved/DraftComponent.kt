package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.TaperOrientation
import com.android.shaftschematic.model.ThreadAttachment

/**
 * DraftComponent represents an in-progress component that is not yet committed.
 *
 * Drafts must preserve user-authored intent, including any AFT/FWD measure-from
 * reference for components that support it.
 */
sealed class DraftComponent {
    abstract val id: String
    abstract val startMmPhysical: Float
    abstract val lengthMm: Float

    data class Body(
        override val id: String,
        override val startMmPhysical: Float,
        val startInputMm: Float?,
        override val lengthMm: Float,
        val diaMm: Float,
    ) : DraftComponent()

    data class Taper(
        override val id: String,
        override val startMmPhysical: Float,
        val startInputMm: Float?,
        override val lengthMm: Float,
        val startDiaMm: Float,
        val endDiaMm: Float,
        val orientation: TaperOrientation,
        val keywayWidthMm: Float,
        val keywayDepthMm: Float,
        val keywayLengthMm: Float,
        val keywaySpooned: Boolean,
    ) : DraftComponent()

    data class Thread(
        override val id: String,
        override val startMmPhysical: Float,
        val startInputMm: Float?,
        override val lengthMm: Float,
        val majorDiaMm: Float,
        val pitchMm: Float,
        val excludeFromOal: Boolean,
        val endAttachment: ThreadAttachment? = null,
    ) : DraftComponent()

    data class Liner(
        override val id: String,
        override val startMmPhysical: Float,
        override val lengthMm: Float,
        val odMm: Float,
        val startInputMm: Float?,
        val measureFrom: LinerAuthoredReference,
        val label: String? = null,
    ) : DraftComponent()
}
