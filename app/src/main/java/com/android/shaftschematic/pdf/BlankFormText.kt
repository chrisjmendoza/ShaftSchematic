package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Blank-draft (write-in) helpers shared by the schematic, runout, and wear composers.
 *
 * A blank draft keeps the full drawing and every field LABEL, but replaces each printed
 * VALUE with a thin writing rule so the sheet can be filled in by hand. All three
 * composers must use the same rule style so the documents read as one family.
 */

/** Default writable rule length after a label (pt). */
internal const val BLANK_RULE_PT = 70f

/** Writable gap reserved inside a dimension-line break when the value is blanked (pt). */
internal const val BLANK_DIM_GAP_PT = 46f

/**
 * Air between a printed dimension value and each line stub when it seats in its break
 * (pt) — the wear/runout OAL lines and the wear strips' chained rails mimic the
 * schematic's value-in-a-break convention so all drawing outputs read the same.
 * `layoutWearStripRail`'s `textPadPt` default must stay equal to this: its inward-arrow
 * fit test is what guarantees a break cut at this pad leaves stub room for the arrows.
 */
internal const val DIM_BREAK_TEXT_PAD_PT = 4f

/** Rule sits slightly below the text baseline, like an underscore. */
private const val RULE_DROP_PT = 2f

private fun rulePaint(text: Paint): Paint = Paint(text).apply {
    style = Paint.Style.STROKE
    strokeWidth = 0.7f
}

/**
 * Draws `label` at ([x], [y]) followed by a writable rule of [ruleWidth] pt (clamped to
 * [maxRight]). Returns the x just past the rule (plus a small gap), so header segments
 * can be chained on one line.
 */
internal fun drawLabelWithRule(
    c: Canvas,
    label: String,
    x: Float,
    y: Float,
    text: Paint,
    ruleWidth: Float = BLANK_RULE_PT,
    maxRight: Float = Float.MAX_VALUE,
): Float {
    c.drawText(label, x, y, text)
    val ruleStart = x + text.measureText(label) + 4f
    val ruleEnd = (ruleStart + ruleWidth).coerceAtMost(maxRight)
    if (ruleEnd > ruleStart) c.drawLine(ruleStart, y + RULE_DROP_PT, ruleEnd, y + RULE_DROP_PT, rulePaint(text))
    return ruleEnd + 14f
}
