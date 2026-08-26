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
 * Job info on line 1, the document's [title] centred on line 2, and a hairline rule under
 * the block.
 *
 * Blank-draft mode spreads the five job-info fields edge-to-edge across the full content
 * width with equal writing rules — handwriting room. The OAL belongs to the drawing's
 * end-to-end span (each sheet's own OAL line), so it is never printed here in either mode.
 *
 * @param headerHeightPt      Block height (rule baseline offset) for the printed header.
 * @param headerHeightBlankPt Block height for the taller blank write-in header.
 * @param blankLineGapPt      Baseline gap between the blank header's two lines; the
 *                            printed header keeps the proportional `textSize × 1.4`.
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
) {
    val ts = text.textSize

    fun centeredX(str: String): Float =
        ((left + right - text.measureText(str)) * 0.5f).coerceAtLeast(left)

    if (blankValues) {
        val line1Y = top + ts + 4f
        val line2Y = line1Y + blankLineGapPt
        val labels = listOf("Customer:", "Vessel:", "Job #:", "Date:", "Side:")
        val labelsW = labels.map { text.measureText(it) }.sum()
        // drawLabelWithRule inserts 4f label→rule and returns ruleEnd + 14f (inter-field gap).
        val ruleW = ((right - left - labelsW - labels.size * 4f - (labels.size - 1) * 14f) / labels.size)
            .coerceAtLeast(BLANK_RULE_PT * 0.5f)
        var x = left
        labels.forEach { label -> x = drawLabelWithRule(c, label, x, line1Y, text, ruleWidth = ruleW, maxRight = right) }

        c.drawText(title, centeredX(title), line2Y, text)
    } else {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val side = project.side.printableLabelOrNull()?.let { "  $it" } ?: ""

        val line1 = buildString {
            if (project.customer.isNotBlank()) append("Customer: ${project.customer}   ")
            if (project.vessel.isNotBlank())   append("Vessel: ${project.vessel}   ")
            if (project.jobNumber.isNotBlank()) append("Job #: ${project.jobNumber}   ")
            append("Date: $date$side")
        }

        val line1Fit = ellipsizeToWidth(line1, text, right - left)
        val line2Fit = ellipsizeToWidth(title, text, right - left)
        c.drawText(line1Fit, centeredX(line1Fit), top + ts, text)
        c.drawText(line2Fit, centeredX(line2Fit), top + ts + ts * 1.4f, text)
    }

    val ruleY = top + (if (blankValues) headerHeightBlankPt else headerHeightPt)
    c.drawLine(left, ruleY, right, ruleY, Paint(text).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
    })
}
