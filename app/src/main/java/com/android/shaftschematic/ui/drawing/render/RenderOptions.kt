package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.geom.WearTraceVertex
import androidx.compose.ui.graphics.Color
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT

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

    /**
     * Worn-profile traces by stored liner id (`wearLinerTraces`, `geom/WearTraceMath.kt`): a liner
     * with an entry draws its edges and fill through the measured diameters. Only the Wear tab
     * passes any (on-device report: its "Trace depth exaggeration" slider changed nothing on the
     * shaft drawing above it); empty keeps every other canvas byte-identical.
     */
    val wearLinerTraces: Map<String, List<WearTraceVertex>> = emptyMap(),

    /**
     * Problem marks (`previewIssueMarks`, the warning banner's own `specIssues` list): each
     * overlapping pair's shared stretch as a red band, each past-end overhang red-tinted with the
     * real shaft end dashed. Only the editor preview (Schematic and Final tabs) passes any
     * (on-device report: the banner said something was wrong and nothing showed which part);
     * empty keeps every other canvas byte-identical. The PDF composers never read [RenderOptions].
     */
    val issueMarks: List<PreviewIssueMark> = emptyList(),

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
    /**
     * Thread-hatch slant factor (`PdfPrefs.threadSlant`, `geom/ThreadHatchMath.kt`) — the same
     * app-wide value the PDF composers read from `ThreadHatchStyle.slant`, carried here as a
     * plain option so a change recomposes the canvas.
     */
    val threadSlant: Float = THREAD_SLANT_DEFAULT,
    /**
     * Thread-hatch density (`PdfPrefs.threadDensity`, `geom/ThreadHatchMath.kt`) — the fraction of
     * each thread's true crests the hatch draws; the same app-wide value the PDF composers read
     * from `ThreadHatchStyle.density`, carried here for the same reason as [threadSlant].
     */
    val threadDensity: Float = THREAD_DENSITY_DEFAULT,

    // ──────────────────────────────
    // PDF-shade mirror
    // ──────────────────────────────
    /**
     * Ids of the components that will print SHADED on the PDF — the same effective decision
     * the composers make (kind checkboxes + per-component `shadeOnDrawing`, resolved by the
     * `unshaded*Ids` builders). With [fillFollowsShade] on, these are the ONLY bodies, tapers
     * and liners that get their kind's fill colour; every other one draws as an empty outline,
     * so the editor preview answers "what prints shaded" the way the sheet does — filled or
     * not (on-device reports: first a checked Shade toggle that changed nothing in the box,
     * then a taper filled grey with its toggle off and darker with it on, two shades of "on").
     * Ignored while [fillFollowsShade] is false.
     */
    val shadedComponentIds: Set<String> = emptySet(),
    /**
     * `true` makes a body/taper/liner's fill conditional on [shadedComponentIds] — shaded
     * components take the kind fill, unshaded ones draw empty. `false` (the default, a
     * thumbnail) fills every component with its kind colour as a plain picture of the shaft.
     */
    val fillFollowsShade: Boolean = false,

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
