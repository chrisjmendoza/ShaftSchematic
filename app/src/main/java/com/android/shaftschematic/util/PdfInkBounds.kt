package com.android.shaftschematic.util

import android.graphics.Bitmap

/**
 * PdfInkBounds — where the drawn content actually sits on a rasterized PDF page.
 *
 * A composed sheet rarely inks its full height: the top margin plus whatever the dimension
 * rails did not need can be a third of the page. That blank paper is harmless on a full-page
 * preview and costly in the tuning **page strip**, where every dp of strip is height the
 * options sheet gives up. Cropping the strip to the page's ink band puts the drawing where
 * the eye is instead of a band of white (on-device report).
 *
 * The band is measured from the raster, never from layout assumptions, so anything inked —
 * the OAL rail, a stray callout, the footer — is inside it by construction.
 */

/** A pixel channel below this reads as ink; the rasterized page is clean white otherwise. */
private const val INK_CHANNEL_MAX = 0xF0

/**
 * The inked slice of a page, as fractions of its height — already padded, so
 * `0f ≤ topFrac < bottomFrac ≤ 1f` and the values are ready to crop with.
 */
data class InkBand(val topFrac: Float, val bottomFrac: Float) {
    /** Share of the page height the band spans. */
    val frac: Float get() = bottomFrac - topFrac
}

/**
 * The ink band of a page [height] pixels tall, given a per-row ink test.
 *
 * Rows `0, rowStep, 2·rowStep, …` are sampled, plus **always the last row**, so a page that
 * inks only its final pixel row is still found. The first and last inked rows bound the
 * band, which is then padded by [padFrac] of the page height on each side and clamped to the
 * page.
 *
 * [padFrac] must exceed `rowStep / height`: a hairline the row sampling stepped over lies
 * within one step of a sampled row, so the pad is the cushion that keeps it inside the band.
 * Returns null when no sampled row carries ink — the caller then shows the whole page.
 */
internal fun inkBandFromRows(
    rowHasInk: (Int) -> Boolean,
    height: Int,
    rowStep: Int,
    padFrac: Float,
): InkBand? {
    if (height <= 0) return null
    val step = rowStep.coerceAtLeast(1)
    var firstInk = -1
    var lastInk = -1
    var y = 0
    while (y < height) {
        if (rowHasInk(y)) {
            if (firstInk < 0) firstInk = y
            lastInk = y
        }
        y += step
    }
    // The final row is only on the sampling grid by coincidence; test it outright.
    val finalRow = height - 1
    if (finalRow % step != 0 && rowHasInk(finalRow)) {
        if (firstInk < 0) firstInk = finalRow
        lastInk = finalRow
    }
    if (firstInk < 0) return null

    val pad = padFrac.coerceIn(0f, 1f)
    val top = (firstInk.toFloat() / height - pad).coerceIn(0f, 1f)
    val bottom = ((lastInk + 1).toFloat() / height + pad).coerceIn(0f, 1f)
    return InkBand(topFrac = top, bottomFrac = bottom)
}

/**
 * The [InkBand] of a rasterized page, or null if the page is blank.
 *
 * Reads **one row at a time** into a reusable buffer — a 2× letter page is ~2 M pixels, and
 * pulling all of it into an `IntArray` to find two row indices would cost more memory than
 * the bitmap itself. Every [colStep]-th pixel of a sampled row is tested; a pixel counts as
 * ink when any of r/g/b falls below [INK_CHANNEL_MAX], which absorbs antialiasing without
 * mistaking the white page for content.
 *
 * Call off the main thread — this walks the raster.
 */
internal fun Bitmap.inkBand(
    rowStep: Int = maxOf(1, height / 200),
    colStep: Int = maxOf(1, width / 256),
    padFrac: Float = 0.025f,
): InkBand? {
    if (width <= 0 || height <= 0) return null
    val cols = colStep.coerceAtLeast(1)
    val rowBuf = IntArray(width)
    return inkBandFromRows(
        rowHasInk = { y ->
            getPixels(rowBuf, 0, width, 0, y, width, 1)
            var x = 0
            var inked = false
            while (x < width) {
                val px = rowBuf[x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r < INK_CHANNEL_MAX || g < INK_CHANNEL_MAX || b < INK_CHANNEL_MAX) {
                    inked = true
                    break
                }
                x += cols
            }
            inked
        },
        height = height,
        rowStep = rowStep,
        padFrac = padFrac,
    )
}
