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
