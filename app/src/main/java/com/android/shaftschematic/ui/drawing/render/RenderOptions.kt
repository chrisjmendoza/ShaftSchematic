package com.android.shaftschematic.ui.drawing.render

/**
 * Visual configuration for the shaft renderer.
 *
 * All geometric inputs (in [com.android.shaftschematic.model.ShaftSpec]) are **canonical millimeters**.
 * These options only affect **presentation** (grid appearance, line weights, and text styling).
 *
 * ### Units & Coordinates
 * - Renderer draws inside a Compose `DrawScope` using **device pixels** (not dp).
 * - Text sizes are also specified in **pixels** and converted to sp internally.
 * - `gridUseInches` toggles *labels/legend semantics* (in vs mm). **Geometry stays mm.**
 *
 * ### Tuning Tips
 * - Prefer small line widths (1–3 px) for on-screen crispness.
 * - Keep `gridMinPixelGap` ≥ 6 px to avoid over-dense minors on small canvases.
 * - If you want a bigger preview font, scale `textSizePx` (and optionally `legendTextSizePx`).
 */
data class RenderOptions(
    // Sizing for the drawing area
    val targetWidthInches: Float? = null,
    val maxHeightInches: Float = 2f,
    val paddingPx: Int = 16,

    // Dimensioning / reference
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,

    // ──────────────────────────────
    // Geometry & dimension styling
    // ──────────────────────────────

    /**
     * Stroke width (px) for primary shaft outlines (e.g., body/taper edges).
     */
    val lineWidthPx: Float = 4f,       // main outlines

    /**
     * Stroke width (px) for auxiliary lines (centerline segments, end ticks, hatching).
     */
    val dimLineWidthPx: Float = 2f,    // dimension / thin lines

    /**
     * Base text size in **pixels** for labels rendered on the drawing (overall length, etc.).
     */
    val textSizePx: Float = 34f,

    // ──────────────────────────────
    // Grid visibility & semantics
    // ──────────────────────────────

    /**
     * Whether to draw the engineering grid under the shaft geometry.
     */
    val showGrid: Boolean = false,

    /**
     * If `true`, grid legend/labels are expressed in **inches**; otherwise **millimeters**.
     * Geometry remains canonical in mm either way.
     */
    val gridUseInches: Boolean = false, // set from UI based on selected Units

    /**
     * Target count of **major** grid divisions across the content width.
     * The layout computes the actual spacing and will clamp to a reasonable range.
     */
    val gridDesiredMajors: Int = 20,    // target majors across the width

    /**
     * Number of **minor** subdivisions within each major step.
     * Example: 5 minors ⇒ 10 mm minors when majors are 50 mm.
     */
    val gridMinorsPerMajor: Int = 4,    // number of minor lines between majors

    /**
     * Stroke width (px) for **minor** grid lines.
     */
    val gridMinorStrokePx: Float = 1f,

    // ──────────────────────────────
    // Grid styling
    // ──────────────────────────────

    /**
     * Stroke width (px) for **major** grid lines.
     */
    val gridMajorStrokePx: Float = 1.5f,

    /**
     * ARGB color for **minor** grid lines (e.g., 0x1A000000 = black @ ~10% alpha).
     */
    val gridMinorColor: Int = 0x66888888.toInt(), // subtle gray

    /**
     * ARGB color for **major** grid lines (e.g., 0x33000000 = black @ ~20% alpha).
     */
    val gridMajorColor: Int = 0x99888888.toInt(),

    /**
     * Minimum pixel gap allowed between **minor** lines. If minors would be denser than this
     * threshold, the renderer will **omit** them to reduce visual clutter.
     */
    val gridMinPixelGap: Float = 10f,             // never draw lines closer than this

    /**
     * Show a small numeric legend indicating the physical length of one major step
     * (e.g., “1.00 in” or “50 mm”), positioned near the top-right of the content area.
     */
    val showGridLegend: Boolean = true,

    /**
     * ARGB color of the legend text.
     */
    val legendTextColor: Int = 0xFF000000.toInt(),

    /**
     * Legend text size in **pixels**. If ≤ 0, the renderer derives it from [textSizePx].
     */
    val legendTextSizePx: Float = 0f,  // 0 => auto (0.6 * textSizePx)
    val legendBarHeightPx: Float = 6f,
    val legendPaddingPx: Int = 8
)

enum class ReferenceEnd { AFT, FWD }
