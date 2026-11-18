package com.android.shaftschematic.pdf

import com.android.shaftschematic.gfx.Canvas
import com.android.shaftschematic.gfx.isFakeBoldText
import com.android.shaftschematic.gfx.left
import com.android.shaftschematic.gfx.Paint
import com.android.shaftschematic.gfx.PaintStyle
import com.android.shaftschematic.gfx.right
import com.android.shaftschematic.gfx.strokeWidth
import com.android.shaftschematic.gfx.style
import com.android.shaftschematic.gfx.textSize
import com.android.shaftschematic.gfx.top
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.printedDateOrNull

// ──────────────────────────────────────────────────────────────────────────────
// The two-line job-info header — ONE implementation for the wear document and the
// undercut document, which carried byte-identical copies of it.
//
// The classic RUNOUT sheet deliberately keeps its own one-line header
// (`drawRunoutHeader`): it prints no title line, left-aligns its job info instead of
// centring it, takes its block height as a parameter rather than branching on blank
// mode, and spreads its blank rules at drawLabelWithRule's default width. Folding it in
// would mean a function that is mostly flags — so it stays a separate one-liner.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The blank-draft job-info labels, in print order — one writing rule per label.
 *
 * ONE list for every sheet's blank header (this one and the classic runout sheet's), so a field
 * added to one draft cannot go missing from another.
 */
val JOB_INFO_BLANK_LABELS =
    listOf("Customer:", "Vessel:", "Job #:", "Item:", "Date:", "Side:")

/**
 * Today's date as a printed sheet writes it — the string handed to [printedDateOrNull] for
 * [com.android.shaftschematic.model.SheetDateMode.TODAY]. ONE format for every print site.
 */
expect fun sheetTodayDate(): String

/**
 * The printed job-info line: whichever identity fields the document actually carries, then the
 * date (null when the document prints none) and the drawing label.
 *
 * ONE construction for every sheet header, the counterpart to [JOB_INFO_BLANK_LABELS]. A blank
 * field prints no label at all — a label with nothing after it is an orphan on a finished
 * drawing — which is exactly why Item can be optional without an "Item:" ever printing bare, and
 * why a null [date] drops the "Date:" piece together with its separator: a stray run of spaces
 * would shift the centred line.
 *
 * The shaft's SIDE is not on this line: every header prints it once, large, in its upper right
 * corner ([drawHeaderSideBadge]).
 */
fun jobInfoHeaderLine(project: ProjectInfo, date: String?): String {
    val drawing = project.drawingLabel.takeIf { it.isNotBlank() }?.let { "  Drawing: $it" } ?: ""
    val fields = buildList {
        if (project.customer.isNotBlank())  add("Customer: ${project.customer}")
        if (project.vessel.isNotBlank())    add("Vessel: ${project.vessel}")
        if (project.jobNumber.isNotBlank()) add("Job #: ${project.jobNumber}")
        if (project.item.isNotBlank())      add("Item: ${project.item}")
        if (date != null)                   add("Date: $date")
    }
    return (fields.joinToString("   ") + drawing).trimStart()
}

/** The header side badge's text size, as a fraction of the header block's height. */
const val HEADER_SIDE_BADGE_HEIGHT_FRAC = 0.8f

/** Paper kept between a header's text and its side badge. */
internal const val HEADER_SIDE_BADGE_GAP_PT = 10f

/** Paper kept between the side badge's capitals and the header rule under them. */
private const val HEADER_SIDE_BADGE_RULE_CLEAR_PT = 3f

/** Height of a capital letter, as a fraction of the text size — close enough to centre a badge. */
private const val CAP_HEIGHT_FRAC = 0.72f

/**
 * The header side badge's text size for a header block [blockHeightPt] tall: the largest bold
 * capitals the block holds above its rule. Pure, so the sizing is tested without a canvas.
 */
fun headerSideBadgeTextSizePt(blockHeightPt: Float): Float = blockHeightPt * HEADER_SIDE_BADGE_HEIGHT_FRAC

/**
 * The shaft's side (PORT / STBD / CENTER, `ShaftPosition.printableLabelOrNull`) in large bold
 * capitals, tucked into the header's upper right corner (on-device request: with the port and
 * starboard shafts' sheets side by side, the side has to be found at a glance, not read out of
 * the job line). Sized off the header block, so it costs the sheet no height.
 *
 * Returns the badge's left edge — the header's text stays [HEADER_SIDE_BADGE_GAP_PT] clear of
 * it — or [right] when the job has no side and nothing was drawn. Printed headers only: a blank
 * draft's header rules a "Side:" line to be written in.
 */
internal fun drawHeaderSideBadge(
    c: Canvas,
    text: Paint,
    right: Float,
    top: Float,
    blockHeightPt: Float,
    project: ProjectInfo,
): Float {
    val side = project.side.printableLabelOrNull() ?: return right
    val badge = Paint(text).apply {
        textSize = headerSideBadgeTextSizePt(blockHeightPt)
        isFakeBoldText = true
    }
    val capH = badge.textSize * CAP_HEIGHT_FRAC
    val x = right - badge.measureText(side)
    val baseline = top + (blockHeightPt - HEADER_SIDE_BADGE_RULE_CLEAR_PT + capH) * 0.5f
    c.drawText(side, x, baseline, badge)
    return x
}

/**
 * Job info on line 1, the document's [title] centred on line 2, and a hairline rule under
 * the block.
 *
 * Blank-draft mode spreads the job-info fields edge-to-edge across the full content
 * width with equal writing rules — handwriting room. The OAL belongs to the drawing's
 * end-to-end span (each sheet's own OAL line), so it is never printed here in either mode.
 *
 * @param headerHeightPt      Block height (rule baseline offset) for the printed header.
 * @param headerHeightBlankPt Block height for the taller blank write-in header.
 * @param blankLineGapPt      Baseline gap between the blank header's two lines; the
 *                            printed header keeps the proportional `textSize × 1.4`.
 * @param minFitTextSizePt    When positive, a printed job-info line too wide for the content
 *                            width is first set smaller — down to this size — and only then
 *                            ellipsized, so a narrow page keeps the date and side instead of
 *                            cutting them off. `0` (the default) ellipsizes at the base size.
 */
internal fun drawSheetHeader(
    c: Canvas,
    text: Paint,
    left: Float,
    right: Float,
    top: Float,
    project: ProjectInfo,
    title: String,
    headerHeightPt: Float,
    headerHeightBlankPt: Float,
    blankLineGapPt: Float,
    blankValues: Boolean,
    minFitTextSizePt: Float = 0f,
) {
    val ts = text.textSize

    fun centeredX(str: String, paint: Paint = text): Float =
        ((left + right - paint.measureText(str)) * 0.5f).coerceAtLeast(left)

    if (blankValues) {
        val line1Y = top + ts + 4f
        val line2Y = line1Y + blankLineGapPt
        val labels = JOB_INFO_BLANK_LABELS
        val labelsW = labels.map { text.measureText(it) }.sum()
        // drawLabelWithRule inserts 4f label→rule and returns ruleEnd + 14f (inter-field gap).
        val ruleW = ((right - left - labelsW - labels.size * 4f - (labels.size - 1) * 14f) / labels.size)
            .coerceAtLeast(BLANK_RULE_PT * 0.5f)
        var x = left
        labels.forEach { label -> x = drawLabelWithRule(c, label, x, line1Y, text, ruleWidth = ruleW, maxRight = right) }

        c.drawText(title, centeredX(title), line2Y, text)
    } else {
        val date = project.printedDateOrNull(sheetTodayDate())
        val line1 = jobInfoHeaderLine(project, date)
        // The side stands alone in the upper right corner; both text lines stay clear of it.
        val badgeLeft = drawHeaderSideBadge(c, text, right, top, headerHeightPt, project)
        val textRight = if (badgeLeft < right) badgeLeft - HEADER_SIDE_BADGE_GAP_PT else right

        // A line is centred on the page when that keeps it clear of the badge, and in the room
        // left of the badge otherwise.
        fun clearX(str: String, paint: Paint): Float {
            val w = paint.measureText(str)
            val pageX = centeredX(str, paint)
            return if (pageX + w <= textRight) pageX else ((left + textRight - w) * 0.5f).coerceAtLeast(left)
        }

        val width = textRight - left
        val line1W = text.measureText(line1)
        val line1Paint = if (minFitTextSizePt > 0f && line1W > width) {
            Paint(text).apply { textSize = (ts * width / line1W).coerceIn(minFitTextSizePt, ts) }
        } else {
            text
        }
        val line1Fit = ellipsizeToWidth(line1, line1Paint, width)
        val line2Fit = ellipsizeToWidth(title, text, width)
        c.drawText(line1Fit, clearX(line1Fit, line1Paint), top + ts, line1Paint)
        c.drawText(line2Fit, clearX(line2Fit, text), top + ts + ts * 1.4f, text)
    }

    val ruleY = top + (if (blankValues) headerHeightBlankPt else headerHeightPt)
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = PaintStyle.STROKE; strokeWidth = 0.5f
    })
}
