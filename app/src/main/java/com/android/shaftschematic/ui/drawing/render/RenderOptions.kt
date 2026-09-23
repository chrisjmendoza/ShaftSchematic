package com.android.shaftschematic.ui.drawing.render

import androidx.compose.ui.graphics.Color

/**
 * Visual configuration for the shaft renderer.
 *
 * All geometric inputs (in [com.android.shaftschematic.model.ShaftSpec]) are **canonical millimeters**.
 * These options only affect **presentation** (line weights, fills, highlight).
 *
 * ### Units & Coordinates
 * - Renderer draws inside a Compose `DrawScope` using **device pixels** (not dp).
 * - Prefer small line widths (1–3 px) for on-screen crispness.
 */
data class RenderOptions(
    val paddingPx: Int = 16,

    // ──────────────────────────────
    // Geometry styling
    // ──────────────────────────────
    /** Primary outline width (px) for bodies/tapers/liners/envelopes. */
    val outlineWidthPx: Float = 1.5f,
    /** Stroke width (px) for auxiliary/secondary lines. */
    val dimLineWidthPx: Float = 1f,

    // ──────────────────────────────
    // Core colors
    // ──────────────────────────────
    /** ARGB for primary outlines. */
    val outlineColor: Int = 0xFF000000.toInt(),
    /** Fill under bodies. */
    val bodyFillColor: Int = 0x11000000,
    /** Fill under tapers (trapezoids). */
    val taperFillColor: Int = 0x11000000,
    /** Fill under liners. */
    val linerFillColor: Int = 0x11000000,
    /** Fill for coupler bolt-slot cutouts (the half carved into the shaft). */
    val slotFillColor: Int = 0x33000000,

    // ──────────────────────────────
    // Thread styling (hatch)
    // ──────────────────────────────
    /** ARGB color used for thread flank hatching. */
    val threadHatchColor: Int = 0x99000000.toInt(),
    /**
     * Fill color under the thread envelope (low alpha helps separate the thread zone).
     * If fully transparent, the renderer may skip the underlay.
     */
    val threadFillColor: Int = 0x22000000,

    // ──────────────────────────────
    // PDF-shade mirror
    // ──────────────────────────────
    /**
     * Ids of the components that will print SHADED on the PDF — the same effective decision
     * the composers make (kind checkboxes + per-component `shadeOnDrawing`, resolved by the
     * `unshaded*Ids` builders). Each id's fill gets [shadeOverlayColor] drawn over it, so the
     * editor preview answers "what prints shaded" without opening the PDF preview
     * (on-device report: a card's checked Shade toggle changed nothing in the preview box).
     * Empty (the default) draws exactly as before the mirror existed.
     */
    val shadedComponentIds: Set<String> = emptySet(),
    /**
     * ARGB overlay for [shadedComponentIds]. The default is the PDF `shadeFill` grey
     * (40-alpha black); themed callers pass an onSurface-derived tint so the marker stays
     * visible on a dark canvas — this is a marker for the print decision, not print fidelity.
     */
    val shadeOverlayColor: Int = 0x28000000,

    // ──────────────────────────────
    // Highlighting
    // ──────────────────────────────
    /**
     * When true and [highlightId] matches a component, the renderer paints a glow
     * under-stroke before the normal outline, then draws the normal stroke on top.
     * When false or when ids don't match, the highlight has no effect on the drawn output.
     */
    val highlightEnabled: Boolean = false,
    /** Id of the selected component; type should match the ids carried by your segments. */
    val highlightId: Any? = null,
    /** Wide, colored under-ring beneath the normal outline. */
    val highlightGlowColor: Color = Color(0xFF00E5FF),
    /** Opacity for the glow ring. */
    val highlightGlowAlpha: Float = 0.55f,
    /** Extra stroke width (px) added to the glow beyond the base outline width. */
    val highlightGlowExtraPx: Float = 2f,
)
