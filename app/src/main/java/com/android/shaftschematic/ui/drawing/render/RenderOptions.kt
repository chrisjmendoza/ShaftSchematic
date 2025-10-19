package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.graphics.Color

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
    val targetWidthInches: Float? = null,
    val maxHeightInches: Float = 2f,
    val paddingPx: Int = 16,

    // Reference direction (for labels if/when added)
    val referenceEnd: ReferenceEnd = ReferenceEnd.AFT,

    // ──────────────────────────────
    // Geometry & text styling
    // ──────────────────────────────
    /** Primary outline width (px) for bodies/tapers/liners/envelopes. */
    val outlineWidthPx: Float = 1.5f,
    /** Stroke width (px) for auxiliary/secondary lines. */
    val dimLineWidthPx: Float = 1f,
    /** Base text size in **pixels**. */
    val textSizePx: Float = 34f,

    // ──────────────────────────────
    // Visibility toggles
    // ──────────────────────────────
    val showCenterline: Boolean = true,

    // ──────────────────────────────
    // Grid visibility & semantics
    // ──────────────────────────────
    val showGrid: Boolean = false,
    val gridUseInches: Boolean = false,
    val gridDesiredMajors: Int = 20,
    val gridMinorsPerMajor: Int = 4,
    val gridMinorStrokePx: Float = 1f,

    // ──────────────────────────────
    // Grid styling
    // ──────────────────────────────
    val gridMajorStrokePx: Float = 1.5f,
    val gridMinorColor: Int = 0x66888888.toInt(),
    val gridMajorColor: Int = 0x99888888.toInt(),
    val gridMinPixelGap: Float = 10f,
    val showGridLegend: Boolean = true,
    val legendTextColor: Int = 0xFF000000.toInt(),
    val legendTextSizePx: Float = 0f,
    val legendBarHeightPx: Float = 6f,
    val legendPaddingPx: Int = 8,

    // ──────────────────────────────
    // Core colors  (added to match ShaftRenderer expectations)
    // ──────────────────────────────
    /** ARGB for primary outlines. */
    val outlineColor: Int = 0xFF000000.toInt(),
    /** Fill under bodies. */
    val bodyFillColor: Int = 0x11000000,
    /** Fill under tapers (trapezoids). */
    val taperFillColor: Int = 0x11000000,
    /** Fill under liners. */
    val linerFillColor: Int = 0x11000000,

    // ──────────────────────────────
    // Thread styling
    // ──────────────────────────────
    /** How to render threads. */
    val threadStyle: ThreadStyle = ThreadStyle.UNIFIED, // UNIFIED = rails+flanks, HATCH = legacy hatch
    /**
     * If true, thread flanks (zig-zag between rails) use [threadHatchColor].
     * Rails (crest/root) always use [outlineColor].
     */
    val threadUseHatchColor: Boolean = true,
    /** ARGB color used for thread flanks when [threadUseHatchColor] is true. */
    val threadHatchColor: Int = 0x99000000.toInt(),
    /**
     * Stroke width (px) for thread lines. If ≤ 0, the renderer falls back to
     * [outlineWidthPx] for rails and [dimLineWidthPx] for flanks.
     */
    val threadStrokePx: Float = 0f,
    /**
     * Fill color under the thread envelope (low alpha helps separate thread zone from grid).
     * If fully transparent, the renderer may skip the underlay.
     */
    val threadFillColor: Int = 0x22000000,

    // ──────────────────────────────
    // Highlighting
    // ──────────────────────────────
    /**
     * When true and [highlightId] matches a component, the renderer paints a two-ring
     * under-stroke (glow + edge) before the normal outline, then draws the normal stroke
     * on top. When false or when ids don’t match, output is pixel-identical to legacy.
     */
    val highlightEnabled: Boolean = false,
    /** Id of the selected component; type should match the ids carried by your segments. */
    val highlightId: Any? = null,

    /**
     * Wide, colored under-ring beneath the normal outline. `Color` is used instead of `Int`
     * to allow direct use of theme colors and alpha blending in Compose.
     */
    val highlightGlowColor: Color = Color(0xFF00E5FF),

    /** Crisp inner ring on top of the glow; usually white for maximum contrast. */
    val highlightEdgeColor: Color = Color.White,

    /** Opacity for the glow ring. */
    val highlightGlowAlpha: Float = 0.55f,
    /** Opacity for the edge ring. */
    val highlightEdgeAlpha: Float = 1.0f,
    /** Extra stroke width (px) added to the glow beyond the base outline width. */
    val highlightGlowExtraPx: Float = 8f,
    /** Extra stroke width (px) added to the edge beyond the base outline width. */
    val highlightEdgeExtraPx: Float = 4f
)

enum class ReferenceEnd { AFT, FWD }

/** Allowed thread drawing styles. */
enum class ThreadStyle { UNIFIED, HATCH }
