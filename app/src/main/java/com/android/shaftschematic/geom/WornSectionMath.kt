package com.android.shaftschematic.geom

/**
 * WornSectionMath — pure layout for a worn section's in-profile measured values.
 *
 * A worn section prints its measured Ø values **inside** the shaft profile: each value is a
 * column of text rotated 90° (reading bottom-to-top), columns stacked left→right across the
 * span, the group centered on the span's midpoint and on the shaft centerline. Behind every
 * column sits a **halo rectangle** — the draw sites erase it to sheet white before drawing
 * the text, which is what makes "lines do not draw where the measurement numbers are" true
 * for anything already on the profile (surface lines, component edges, hatches).
 *
 * Shared by the runout preview canvas and `RunoutPdfComposer` (no `pdf → ui` dep) — the
 * draw-both-sites rule; only text measurement is supplied by the caller, since px-per-char
 * differs between the two canvases.
 *
 * Units: px (or pt — any single linear unit, consistently).
 */

/** Column pitch (center-to-center) as a multiple of the text line height. */
const val WORN_VALUE_COLUMN_PITCH_FACTOR = 1.7f

/** Halo padding around the rotated text, as a multiple of the text line height. */
const val WORN_VALUE_HALO_PAD_FACTOR = 0.3f

/**
 * Fraction of the local band height (surface line to surface line) a value + halo may
 * occupy — the remainder keeps the halo just inside the profile strokes, so the knockout
 * never cuts the surface lines.
 */
const val WORN_VALUE_BAND_FIT_FRAC = 0.94f

/**
 * Band height (same unit as the inputs) a value needs to sit fully inside the profile:
 * its rotated length plus the halo padding both sides.
 */
fun wornValueBandHeightNeeded(labelLength: Float, lineHeight: Float): Float =
    labelLength + 2f * WORN_VALUE_HALO_PAD_FACTOR * lineHeight

/**
 * Text size at which a value fits the local [bandHeight]: [baseTextSize] when it already
 * fits, otherwise shrunk proportionally (label length and line height both scale linearly
 * with text size), floored at [minTextSize] — below that the number is unreadable, and a
 * slight halo overhang is the lesser evil. Inputs are measured at [baseTextSize].
 */
fun fittedValueTextSize(
    baseTextSize: Float,
    minTextSize: Float,
    labelLengthAtBase: Float,
    lineHeightAtBase: Float,
    bandHeight: Float,
): Float {
    if (labelLengthAtBase <= 0f || bandHeight <= 0f) return baseTextSize
    val neededAtBase = wornValueBandHeightNeeded(labelLengthAtBase, lineHeightAtBase)
    if (neededAtBase <= bandHeight) return baseTextSize
    return (baseTextSize * bandHeight / neededAtBase)
        .coerceIn(minTextSize.coerceAtMost(baseTextSize), baseTextSize)
}

/** One rotated value column: text center + the knockout rect behind it. */
data class WornValueColumn(
    val cx: Float,
    val cy: Float,
    val haloLeft: Float,
    val haloTop: Float,
    val haloRight: Float,
    val haloBottom: Float,
)

data class WornSectionValueLayout(
    val columns: List<WornValueColumn>,
    /**
     * True when the column group (plus one line height of air) is wider than the span.
     * The group still draws — centered, overhanging both boundaries symmetrically — the
     * flag lets authoring surfaces warn rather than silently clip a measurement.
     */
    val overflows: Boolean,
)

/**
 * Lay out [labelLengths] value columns (text lengths in px, measured by the caller at the
 * draw site's text size) inside the span [x0]..[x1], centered on centerline [cy].
 *
 * Order-preserving: column i is labels[i], left→right — the record's list order is the
 * print order (golden rule posture: the system never reorders the machinist's readings).
 */
fun layoutWornSectionValues(
    x0: Float,
    x1: Float,
    cy: Float,
    labelLengths: List<Float>,
    lineHeight: Float,
): WornSectionValueLayout {
    if (labelLengths.isEmpty()) return WornSectionValueLayout(emptyList(), overflows = false)

    val pitch = lineHeight * WORN_VALUE_COLUMN_PITCH_FACTOR
    val pad = lineHeight * WORN_VALUE_HALO_PAD_FACTOR
    val groupWidth = pitch * (labelLengths.size - 1)
    val centerX = (x0 + x1) / 2f
    val firstCx = centerX - groupWidth / 2f

    val columns = labelLengths.mapIndexed { i, len ->
        val cx = firstCx + i * pitch
        WornValueColumn(
            cx = cx,
            cy = cy,
            haloLeft = cx - lineHeight / 2f - pad,
            haloTop = cy - len / 2f - pad,
            haloRight = cx + lineHeight / 2f + pad,
            haloBottom = cy + len / 2f + pad,
        )
    }
    return WornSectionValueLayout(
        columns = columns,
        overflows = groupWidth + lineHeight > (x1 - x0),
    )
}
