package com.android.shaftschematic.ui.drawing.render

import androidx.annotation.Px

enum class ReferenceEnd { AFT, FWD }

/** UI-agnostic render options used by both screen and PDF. */
data class RenderOptions(
    val targetWidthInches: Float? = null,   // null = fit available width
    val maxHeightInches: Float = 2f,        // cap shaft band height
    @Px val paddingPx: Int = 48,
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,
    @Px val lineWidthPx: Float = 4f,
    @Px val dimLineWidthPx: Float = 3f,
    @Px val textSizePx: Float = 34f,
    val showGrid: Boolean = false
)
