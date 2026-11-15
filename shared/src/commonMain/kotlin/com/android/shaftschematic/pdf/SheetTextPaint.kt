package com.android.shaftschematic.pdf

import com.android.shaftschematic.gfx.ANTI_ALIAS_FLAG
import com.android.shaftschematic.gfx.Color
import com.android.shaftschematic.gfx.color
import com.android.shaftschematic.gfx.Paint
import com.android.shaftschematic.gfx.PaintAlign
import com.android.shaftschematic.gfx.PaintStyle
import com.android.shaftschematic.gfx.style
import com.android.shaftschematic.gfx.textAlign
import com.android.shaftschematic.gfx.textSize
import com.android.shaftschematic.gfx.typeface
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
    align: PaintAlign = PaintAlign.LEFT,
    color: Int = Color.BLACK,
): Paint = Paint(ANTI_ALIAS_FLAG).apply {
    style = PaintStyle.FILL
    this.textSize = textSize
    textAlign = align
    typeface = OutputTypography.active
    this.color = color
}
