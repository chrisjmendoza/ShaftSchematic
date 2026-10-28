package com.android.shaftschematic.pdf

import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.util.OutputTypography

/**
 * The ONE constructor for a fresh text [Paint] on a printed sheet.
 *
 * Every sheet is set in Settings → Drawing → "Output font", which reaches the composers only
 * through the [OutputTypography.active] mirror. A text paint built any other way prints in the
 * platform sans beside the chosen face, and the sheet reads as two typefaces (on-device report:
 * the rail values and Ø callouts stayed sans under Serif while the names and footer changed).
 * A `Paint(text)` copy of a paint built here inherits the face, so derived paints need nothing
 * more; `SheetTextPaintSourceTest` fails on a fresh text paint in `pdf/` that bypasses this.
 */
fun sheetTextPaint(
    textSize: Float,
    align: Paint.Align = Paint.Align.LEFT,
    color: Int = Color.BLACK,
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.textSize = textSize
    textAlign = align
    typeface = OutputTypography.active
    this.color = color
}
