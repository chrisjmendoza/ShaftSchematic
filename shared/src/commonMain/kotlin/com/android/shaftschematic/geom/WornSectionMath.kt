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

/**
 * Halo padding around the rotated text, as a multiple of the thickness the caller lays the value
 * out at — the value's INK thickness on the consolidated sheet (the digits' measured height, not the
 * font's line box), so the white box hugs the digits and neighbouring values can sit close
 * (on-device request: "tighten the white box behind the wear values").
 */
const val WORN_VALUE_HALO_PAD_FACTOR = 0.25f

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
    /**
     * How thick one column is ACROSS the shaft axis — one line by default.
     *
     * Rotating the text swaps the axes, so a two-line stacked dual value is two lines thick along
     * the shaft and only as LONG as its longer line. That is why stacking helps here where it costs
     * elsewhere: the band height a value demands (which competes with the drawn shaft diameter,
     * `fittedValueTextSize`) falls to the longer term, and the extra bulk goes into the axial
     * direction, where a worn section usually has room. Pass the stack height for a stacked value;
     * [lineHeight] still sets the halo padding, so the knockout does not bloat with it.
     */
    columnThickness: Float = lineHeight,
): WornSectionValueLayout {
    if (labelLengths.isEmpty()) return WornSectionValueLayout(emptyList(), overflows = false)

    val thickness = columnThickness.coerceAtLeast(lineHeight)
    val pitch = thickness * WORN_VALUE_COLUMN_PITCH_FACTOR
    val pad = lineHeight * WORN_VALUE_HALO_PAD_FACTOR
    val groupWidth = pitch * (labelLengths.size - 1)
    val centerX = (x0 + x1) / 2f
    val firstCx = centerX - groupWidth / 2f

    val columns = labelLengths.mapIndexed { i, len ->
        val cx = firstCx + i * pitch
        WornValueColumn(
            cx = cx,
            cy = cy,
            haloLeft = cx - thickness / 2f - pad,
            haloTop = cy - len / 2f - pad,
            haloRight = cx + thickness / 2f + pad,
            haloBottom = cy + len / 2f + pad,
        )
    }
    return WornSectionValueLayout(
        columns = columns,
        overflows = groupWidth + thickness > (x1 - x0),
    )
}

/**
 * One in-profile value to place: its planned [column], the drawn x span of the part it is on, and
 * its halo [pad] (the knockout's margin round the digits — `WORN_VALUE_HALO_PAD_FACTOR` × its line
 * height), so the digits' own box is the halo inset by [pad].
 */
data class ValueColumnRequest(
    val column: WornValueColumn,
    val spanLeft: Float,
    val spanRight: Float,
    val pad: Float = 0f,
)

/**
 * A value already on the sheet that a placed one must respect: its halo x range, halo pad, and
 * [orderX] — where it was planned, which side of it a placed value must stay on (defaults to its
 * centre).
 */
data class ValueBox(
    val haloLeft: Float,
    val haloRight: Float,
    val pad: Float = 0f,
    val orderX: Float = (haloLeft + haloRight) / 2f,
)

/**
 * The in-profile value columns of [requests], each moved — only as far as it must — so that
 *  - its DIGITS stay inside its own part's drawn span, [clearance] clear of each end line (the
 *    caller clips the knockout to the part, so the pad round the digits never erases the line —
 *    on-device report: a Ø read near a liner end knocked out the liner's end line; the outline
 *    takes precedence, and a value need not sit exactly on its station); and
 *  - its knockout never covers the digits of another value and its digits sit under no other
 *    knockout — the worn-section values in [obstacles] (placed first) and every reading placed
 *    before it. Two knockouts' pads may overlap: both are paper.
 * A column already clear does not move. A value moves at most two knockout widths and never
 * past another value — "pushed in a little", always in station order (a reading 2" from the AFT
 * end once jumped to the FWD end). Within that reach it takes the NEAREST clear spot; with none,
 * the spot inside its span that overlaps the other values least (the outline still wins); with
 * its span too narrow for the digits it stays where it was planned. Readings are placed AFT to FWD. Pure x shifts: each
 * column's height and thickness are unchanged.
 */
fun placeValueColumnsClear(
    requests: List<ValueColumnRequest>,
    clearance: Float,
    obstacles: List<ValueBox> = emptyList(),
): List<WornValueColumn> {
    val eps = 1e-3f
    val placedBoxes = obstacles.toMutableList()
    fun overlapLen(a0: Float, a1: Float, b0: Float, b1: Float) = (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0f)
    /** How far a knockout at [left] runs onto other digits, and its digits under other knockouts. */
    fun clash(left: Float, w: Float, pad: Float) = placedBoxes.sumOf { b ->
        (overlapLen(left, left + w, b.haloLeft + b.pad, b.haloRight - b.pad) +
            overlapLen(left + pad, left + w - pad, b.haloLeft, b.haloRight)).toDouble()
    }.toFloat()
    val out = requests.map { it.column }.toMutableList()
    requests.indices.sortedBy { requests[it].column.cx }.forEach { i ->
        val req = requests[i]
        val col = req.column
        val w = col.haloRight - col.haloLeft
        val pad = req.pad.coerceIn(0f, w / 2f)
        // Halo-left bounds that keep the digits [clearance] inside the span.
        val lo = req.spanLeft + clearance - pad
        val hi = req.spanRight - clearance - w + pad
        val left = if (lo <= hi) {
            buildList {
                add(col.haloLeft.coerceIn(lo, hi))
                placedBoxes.forEach { b ->
                    add(b.haloRight - pad)          // digits start where its knockout ends
                    add(b.haloRight - b.pad)        // knockout starts where its digits end
                    add(b.haloLeft - w + pad)       // digits end where its knockout starts
                    add(b.haloLeft + b.pad - w)     // knockout ends where its digits start
                }
            }
                .filter { cand ->
                    val centre = cand + w / 2f
                    cand >= lo - eps && cand <= hi + eps &&
                        kotlin.math.abs(cand - col.haloLeft) <= 2f * w + eps &&
                        // Station order: never past a value it was planned on the other side of.
                        placedBoxes.all { b ->
                            val bCentre = (b.haloLeft + b.haloRight) / 2f
                            if (col.cx >= b.orderX) centre >= bCentre - eps else centre <= bCentre + eps
                        }
                }
                .minWithOrNull(
                    compareBy<Float>({ if (clash(it, w, pad) <= eps) 0f else clash(it, w, pad) })
                        .thenBy { kotlin.math.abs(it - col.haloLeft) },
                )
        } else null
        // No candidate (the reach or the station order ruled them all out): the end-line spot,
        // which the outline is owed; a span too narrow for the digits keeps the planned spot.
        val l = left ?: if (lo <= hi) col.haloLeft.coerceIn(lo, hi) else col.haloLeft
        val placed = if (l == col.haloLeft) col else col.copy(cx = col.cx + (l - col.haloLeft), haloLeft = l, haloRight = l + w)
        out[i] = placed
        placedBoxes += ValueBox(placed.haloLeft, placed.haloRight, pad, orderX = col.cx)
    }
    return out
}
