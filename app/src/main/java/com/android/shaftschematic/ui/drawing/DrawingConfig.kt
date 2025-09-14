package com.android.shaftschematic.ui.drawing

import androidx.annotation.Px

/** Controls sizing, grid, and export scaling behavior. */
data class DrawingConfig(
    /** If non-null, use this physical width in inches for layout (e.g., 10" for export). */
    val targetWidthInches: Float? = null,
    /** Maximum height in inches for the shaft drawing area (not total view), default 2". */
    val maxHeightInches: Float = 2f,
    /** Outer padding around the drawing (in pixels). */
    @Px val paddingPx: Int = 48,
    /** Show faint engineering grid for alignment. */
    val showGrid: Boolean = false,
    /** Distance/labels measured from which end. */
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,
    /** Line/text sizes (px) */
    @Px val lineWidthPx: Float = 4f,
    @Px val dimLineWidthPx: Float = 3f,
    @Px val textSizePx: Float = 34f
)
