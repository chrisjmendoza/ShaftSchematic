package com.android.shaftschematic.geom

/**
 * Whether a cut's measured Ø can be written INSIDE the shaft on the printed undercut sheet
 * (`PdfPrefs.undercutDiaInside`, line art only — on-device request, from a shop hand sketch), and
 * where. The value is a vertical DIMENSION: a thin line from the cut's top floor to its bottom
 * floor at the middle of a free piece of that floor, an arrowhead on each floor line, and the
 * value rotated 90° in a break at the centreline. A cut it does not fit keeps its below-shaft
 * leader callout.
 *
 * Everything here is in sheet POINTS on the strip's own axes: horizontal extents come off the
 * strip's x map, vertical ones off its drawn (exaggerated) floor. Once rotated, the text's
 * HEIGHT is its horizontal extent and its WIDTH its vertical one. Text is never shrunk to fit.
 *
 * Pure — no Android — so the fit rule is unit-tested on its own; the composer only measures the
 * text and draws.
 */

/** Clear paper between the rotated value and each face of its piece, points. */
const val UNDERCUT_INSIDE_DIA_SIDE_PAD_PT = 2f

/** The break in the dimension line: gap between each end of the value and its line stub, points. */
const val UNDERCUT_INSIDE_DIA_TEXT_PAD_PT = 3f

/** The least line each stub keeps past its arrowhead toward the value, points. */
const val UNDERCUT_INSIDE_DIA_MIN_STUB_PT = 2f

/**
 * Whether the rotated value fits ACROSS a free piece of the cut's floor [pieceWidthPt] wide: its
 * text height plus [sidePadPt] each side.
 */
fun undercutInsideDiaFitsWidth(
    pieceWidthPt: Float,
    textHeightPt: Float,
    sidePadPt: Float = UNDERCUT_INSIDE_DIA_SIDE_PAD_PT,
): Boolean = textHeightPt + 2f * sidePadPt <= pieceWidthPt

/**
 * The least drawn floor height (top floor to bottom floor) a value [textWidthPt] long needs: the
 * value, its break pad each end, an arrowhead of [arrowSizePt] on each floor line, and a stub of
 * at least [minStubPt] between each head and the break.
 */
fun undercutInsideDiaNeedHeightPt(
    textWidthPt: Float,
    arrowSizePt: Float,
    textPadPt: Float = UNDERCUT_INSIDE_DIA_TEXT_PAD_PT,
    minStubPt: Float = UNDERCUT_INSIDE_DIA_MIN_STUB_PT,
): Float = textWidthPt + 2f * textPadPt + 2f * arrowSizePt + 2f * minStubPt

/** Whether the rotated value fits UP a floor [floorHeightPt] tall ([undercutInsideDiaNeedHeightPt]). */
fun undercutInsideDiaFitsHeight(
    floorHeightPt: Float,
    textWidthPt: Float,
    arrowSizePt: Float,
    textPadPt: Float = UNDERCUT_INSIDE_DIA_TEXT_PAD_PT,
    minStubPt: Float = UNDERCUT_INSIDE_DIA_MIN_STUB_PT,
): Boolean = undercutInsideDiaNeedHeightPt(textWidthPt, arrowSizePt, textPadPt, minStubPt) <= floorHeightPt

/** Both tests: the value fits across the piece AND up the floor. */
fun undercutInsideDiaFits(
    pieceWidthPt: Float,
    floorHeightPt: Float,
    textWidthPt: Float,
    textHeightPt: Float,
    arrowSizePt: Float,
    sidePadPt: Float = UNDERCUT_INSIDE_DIA_SIDE_PAD_PT,
    textPadPt: Float = UNDERCUT_INSIDE_DIA_TEXT_PAD_PT,
    minStubPt: Float = UNDERCUT_INSIDE_DIA_MIN_STUB_PT,
): Boolean = undercutInsideDiaFitsWidth(pieceWidthPt, textHeightPt, sidePadPt) &&
    undercutInsideDiaFitsHeight(floorHeightPt, textWidthPt, arrowSizePt, textPadPt, minStubPt)

/**
 * The x centre the value sits at among [piecesPt] — the free pieces of one cut's own floor as
 * `(startPt, endPt)`, from `undercutFloorPieces` mapped through the strip's x map: the WIDEST piece,
 * when the value fits there ([undercutInsideDiaFits]); null when it does not (or there is no
 * piece), and the cut keeps its leader callout.
 */
fun undercutInsideDiaCentreXPt(
    piecesPt: List<Pair<Float, Float>>,
    floorHeightPt: Float,
    textWidthPt: Float,
    textHeightPt: Float,
    arrowSizePt: Float,
    sidePadPt: Float = UNDERCUT_INSIDE_DIA_SIDE_PAD_PT,
    textPadPt: Float = UNDERCUT_INSIDE_DIA_TEXT_PAD_PT,
    minStubPt: Float = UNDERCUT_INSIDE_DIA_MIN_STUB_PT,
): Float? {
    val widest = piecesPt.maxByOrNull { (a, b) -> b - a } ?: return null
    val (a, b) = widest
    return if (undercutInsideDiaFits(b - a, floorHeightPt, textWidthPt, textHeightPt, arrowSizePt, sidePadPt, textPadPt, minStubPt)) {
        (a + b) * 0.5f
    } else {
        null
    }
}

/**
 * The drawn floor height of a cut whose drawn floor is [floorDiaMm], on a strip whose shaft is
 * [cylHeightPt] tall at its largest surface Ø [stripMaxDiaMm] — the strip's radius map
 * (`rStrip`), both floor lines.
 */
fun undercutInsideDiaFloorHeightPt(cylHeightPt: Float, floorDiaMm: Float, stripMaxDiaMm: Float): Float =
    if (stripMaxDiaMm <= 0f) 0f else (cylHeightPt * (floorDiaMm / stripMaxDiaMm)).coerceIn(0f, cylHeightPt.coerceAtLeast(0f))
