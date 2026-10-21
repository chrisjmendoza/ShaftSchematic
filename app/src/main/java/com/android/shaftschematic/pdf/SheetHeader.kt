package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.model.ProjectInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
internal val JOB_INFO_BLANK_LABELS =
    listOf("Customer:", "Vessel:", "Job #:", "Item:", "Date:", "Side:")

/**
 * The printed job-info line: whichever identity fields the document actually carries, then the
 * date and (when the shaft has one) its side.
 *
 * ONE construction for every sheet header, the counterpart to [JOB_INFO_BLANK_LABELS]. A blank
 * field prints no label at all — a label with nothing after it is an orphan on a finished
 * drawing — which is exactly why Item can be optional without an "Item:" ever printing bare.
 */
internal fun jobInfoHeaderLine(project: ProjectInfo, date: String): String {
    val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""
    val drawing = project.drawingLabel.takeIf { it.isNotBlank() }?.let { "  Drawing: $it" } ?: ""
    return buildString {
        if (project.customer.isNotBlank())  append("Customer: ${project.customer}   ")
        if (project.vessel.isNotBlank())    append("Vessel: ${project.vessel}   ")
        if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
        if (project.item.isNotBlank())      append("Item: ${project.item}   ")
        append("Date: $date$side$drawing")
    }
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
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val line1 = jobInfoHeaderLine(project, date)

        val width = right - left
        val line1W = text.measureText(line1)
        val line1Paint = if (minFitTextSizePt > 0f && line1W > width) {
            Paint(text).apply { textSize = (ts * width / line1W).coerceIn(minFitTextSizePt, ts) }
        } else {
            text
        }
        val line1Fit = ellipsizeToWidth(line1, line1Paint, width)
        val line2Fit = ellipsizeToWidth(title, text, width)
        c.drawText(line1Fit, centeredX(line1Fit, line1Paint), top + ts, line1Paint)
        c.drawText(line2Fit, centeredX(line2Fit), top + ts + ts * 1.4f, text)
    }

    val ruleY = top + (if (blankValues) headerHeightBlankPt else headerHeightPt)
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}
