package com.android.shaftschematic.util

import android.graphics.Paint

/**
 * Word wrapping for drawn spec text — the footer's answer to a value that no longer fits its column.
 *
 * The footer used to ellipsize: `Thread: 5.25" [133.4 mm] × 4 TPI × 5 13/16" [147.6 m…` — the value
 * the sheet exists to communicate, replaced by a `…` (on-device sheet,
 * `docs/archive/DualUnitStacking_PLAN.md` §1d). Dual units roughly double a footer line's width, so this
 * stopped being an edge case. Wrapping keeps every figure on the page; the caller pays for it in
 * line count, which the footer's pitch fit-clamp already knows how to absorb.
 *
 * ## Where it breaks
 * At spaces, and **after a `×`** — the separator that structures a spec line
 * (`KW: 1 3/4" [44.5 mm] × 3/4" [19 mm] × 21 1/2" [546.1 mm]`). Breaking after the `×` leaves it
 * hanging at the end of the line, which reads as "continues below" rather than as a new value.
 * A single run too wide to fit alone is NOT broken mid-token: it goes on its own line and
 * overhangs, because a dimension chopped through the middle is worse than one that touches its
 * neighbour.
 *
 * **A value never splits from its own parts** ([isGluedSpace]): the space inside a mixed number
 * (`8 1/4"`) and the space between a number and its `mm` are not break opportunities, so a value
 * moves to the next line whole. A wrap that left `8` at a line end and `1/4"` under it read as two
 * values, an 8 and a quarter (on-device report, footer text at 175%).
 *
 * ## Measuring
 * Widths come from [measureRichText] when [rich] is set, so a wrapped line carrying a built-up
 * fraction is measured exactly as it will be drawn — the same measure/draw pairing rule as
 * everywhere else. Continuation rows carry [CONTINUATION_INDENT] so a wrapped line is visibly the
 * tail of the one above it and not a new field.
 */

/** Hanging indent on every line after the first. */
const val CONTINUATION_INDENT: String = "   "

/**
 * Splits [text] into lines that each fit [maxWidth], breaking only at spaces and after `×`.
 *
 * Returns a single-element list when the text already fits (the overwhelmingly common case), so a
 * caller can wrap unconditionally without paying for it on ordinary lines. Never returns empty.
 */
fun wrapRichLines(
    text: String,
    paint: Paint,
    maxWidth: Float,
    rich: Boolean = false,
): List<String> {
    fun width(s: String): Float = if (rich) paint.measureRichText(s) else paint.measureText(s)

    if (text.isEmpty() || maxWidth <= 0f || width(text) <= maxWidth) return listOf(text)

    // Break opportunities: after every space a value does not glue, and after every "×". Tokens
    // keep their own trailing space so joining them back up reproduces the source text exactly.
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    text.forEachIndexed { i, ch ->
        sb.append(ch)
        if ((ch == ' ' && !isGluedSpace(text, i)) || ch == '×') {
            tokens += sb.toString()
            sb.clear()
        }
    }
    if (sb.isNotEmpty()) tokens += sb.toString()

    val out = mutableListOf<String>()
    var line = StringBuilder()
    fun flush() {
        if (line.isNotEmpty()) {
            out += line.toString().trimEnd()
            line = StringBuilder()
        }
    }
    tokens.forEach { token ->
        val indent = if (out.isEmpty() && line.isEmpty()) "" else CONTINUATION_INDENT
        val candidate = if (line.isEmpty()) indent + token else line.toString() + token
        if (width(candidate.trimEnd()) <= maxWidth || line.isEmpty()) {
            if (line.isEmpty()) line.append(indent)
            line.append(token)
        } else {
            flush()
            line.append(CONTINUATION_INDENT).append(token)
        }
    }
    flush()
    return if (out.isEmpty()) listOf(text) else out
}

private val FRACTION_AHEAD = Regex("""\d+/\d+""")
private val MM_AHEAD = Regex("""mm(?![A-Za-z])""")

/**
 * Whether the space at [i] in [text] holds a value together, so a wrap may not break there: a
 * digit before it and, after it, either a fraction (`8 1/4"` — the mixed number) or the `mm` unit
 * (`38.1 mm`). Any other space is an ordinary break opportunity.
 */
internal fun isGluedSpace(text: String, i: Int): Boolean {
    if (i <= 0 || i >= text.length - 1 || text[i] != ' ' || !text[i - 1].isDigit()) return false
    return FRACTION_AHEAD.matchesAt(text, i + 1) || MM_AHEAD.matchesAt(text, i + 1)
}
