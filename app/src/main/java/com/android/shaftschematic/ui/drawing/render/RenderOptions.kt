package com.android.shaftschematic.ui.drawing.render

data class RenderOptions(
    // Sizing for the drawing area
    val targetWidthInches: Float? = null,
    val maxHeightInches: Float = 2f,
    val paddingPx: Int = 16,

    // Dimensioning / reference
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,

    // Strokes & text
    val lineWidthPx: Float = 4f,       // main outlines
    val dimLineWidthPx: Float = 2f,    // dimension / thin lines
    val textSizePx: Float = 34f,

    // Grid (visibility)
    val showGrid: Boolean = false,

    // Grid: units-aware dynamic majors/minors
    val gridUseInches: Boolean = false, // set from UI based on selected Units
    val gridDesiredMajors: Int = 20,    // target majors across the width
    val gridMinorsPerMajor: Int = 4,    // number of minor lines between majors

    // Grid appearance
    val gridMinorStrokePx: Float = 1f,
    val gridMajorStrokePx: Float = 1.5f,
    val gridMinorColor: Int = 0x66888888.toInt(), // subtle gray
    val gridMajorColor: Int = 0x99888888.toInt(),
    val gridMinPixelGap: Float = 10f,             // never draw lines closer than this

    // Legend
    val showGridLegend: Boolean = true,
    val legendTextColor: Int = 0xFF000000.toInt(),
    val legendTextSizePx: Float = 0f,  // 0 => auto (0.6 * textSizePx)
    val legendBarHeightPx: Float = 6f,
    val legendPaddingPx: Int = 8
)

enum class ReferenceEnd { AFT, FWD }
