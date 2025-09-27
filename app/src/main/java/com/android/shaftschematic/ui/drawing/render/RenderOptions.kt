package com.android.shaftschematic.ui.drawing.render

/**
 * Visual configuration for the shaft renderer.
 *
 * All geometric inputs (in [com.android.shaftschematic.model.ShaftSpec]) are **canonical millimeters**.
 * These options only affect **presentation** (grid appearance, line weights, and text styling).
 *
 * ### Units & Coordinates
 * - Renderer draws inside a Compose `DrawScope` using **device pixels** (not dp).
 * - Text sizes are specified in **pixels** and converted to sp internally.
 * - `gridUseInches` toggles *labels/legend semantics* (in vs mm). **Geometry stays mm.**
 *
 * ### Tuning Tips
 * - Prefer small line widths (1–3 px) for on-screen crispness.
 * - Keep `gridMinPixelGap` ≥ 6 px to avoid over-dense minors on small canvases.
 * - If you want a bigger preview font, scale `textSizePx` (and optionally `legendTextSizePx`).
 */
data class RenderOptions(
    // ──────────────────────────────
    // Content sizing / padding
    // ──────────────────────────────
    /** If set, the renderer may use this as a sizing hint (not required in Compose). */
    val targetWidthInches: Float? = null,
    /** Max drawing height hint (used by non-Compose backends). */
    val maxHeightInches: Float = 2f,
    /** Inner padding between content rect and card/canvas border (px). */
    val paddingPx: Int = 16,

    // Reference direction for any annotations that care about AFT/FWD.
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,

    // ──────────────────────────────
    // Geometry & text styling
    // ──────────────────────────────
    /** Stroke width (px) for primary outlines (bodies, tapers, etc.). */
    val lineWidthPx: Float = 4f,
    /** Stroke width (px) for auxiliary lines (ticks, hatching, thin lines). */
    val dimLineWidthPx: Float = 2f,
    /** Base text size in **pixels** for labels drawn on the canvas. */
    val textSizePx: Float = 34f,

    // ──────────────────────────────
    // Visibility toggles
    // ──────────────────────────────
    /**
     * Draw the centerline/axis across the shaft. Turn this **OFF** in the in-app preview;
     * leave it **ON** for export or technical prints if desired.
     */
    val showCenterline: Boolean = true,

    // ──────────────────────────────
    // Grid visibility & semantics
    // ──────────────────────────────
    /** Whether to draw the engineering grid under the geometry. */
    val showGrid: Boolean = false,
    /** If true, grid labels/legend are shown in inches; otherwise millimeters. */
    val gridUseInches: Boolean = false,
    /** Target number of **major** divisions across the width (renderer will choose a nice spacing). */
    val gridDesiredMajors: Int = 20,
    /** Number of **minor** subdivisions per major. */
    val gridMinorsPerMajor: Int = 4,
    /** Stroke width (px) for **minor** grid lines. */
    val gridMinorStrokePx: Float = 1f,

    // ──────────────────────────────
    // Grid styling
    // ──────────────────────────────
    /** Stroke width (px) for **major** grid lines. */
    val gridMajorStrokePx: Float = 1.5f,
    /** ARGB color for **minor** grid lines. */
    val gridMinorColor: Int = 0x66888888.toInt(),
    /** ARGB color for **major** grid lines. */
    val gridMajorColor: Int = 0x99888888.toInt(),
    /**
     * Minimum pixel gap allowed between **minor** lines. If minors would be denser than this,
     * the renderer omits them to reduce visual clutter.
     */
    val gridMinPixelGap: Float = 10f,
    /** Show a small legend (e.g., “1.00 in” or “50 mm”) near the top-right of the content. */
    val showGridLegend: Boolean = true,
    /** ARGB color for legend text. */
    val legendTextColor: Int = 0xFF000000.toInt(),
    /**
     * Legend text size in **pixels**. If ≤ 0, the renderer derives it from [textSizePx]
     * (typically ~0.6 × `textSizePx`).
     */
    val legendTextSizePx: Float = 0f,
    /** Legend scale bar height (px). */
    val legendBarHeightPx: Float = 6f,
    /** Legend inner padding (px). */
    val legendPaddingPx: Int = 8,
)

enum class ReferenceEnd { AFT, FWD }
