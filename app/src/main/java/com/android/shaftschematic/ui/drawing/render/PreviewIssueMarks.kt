package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.ui.util.SpecIssue
import com.android.shaftschematic.ui.util.SpecIssueKind

/** How the editor preview marks a stretch of shaft that has a problem. */
enum class PreviewIssueMarkKind {
    /** Two parts on the same stretch: a translucent red band with a red outline. */
    OVERLAP,

    /** The part of a component beyond a shaft end: red-tinted, with that end marked. */
    OUT_OF_BOUNDS,
}

/**
 * One red mark on the editor preview, in mm from the AFT face.
 *
 * @property boundMm the shaft end an [PreviewIssueMarkKind.OUT_OF_BOUNDS] part runs beyond
 *   (0 or the OAL), drawn as a dashed red line where the shaft really ends; null for an overlap.
 */
data class PreviewIssueMark(
    val startMm: Float,
    val endMm: Float,
    val kind: PreviewIssueMarkKind,
    val boundMm: Float? = null,
)

/**
 * The preview's marks for [issues] — the SAME `specIssues` list the warning banner lists, so the
 * drawing marks exactly the problems the banner names: each overlapping pair's shared stretch,
 * and each past-end part's overhang. A tiny segment marks nothing (it has no stretch to show),
 * and a degenerate stretch is dropped.
 */
fun previewIssueMarks(issues: List<SpecIssue>): List<PreviewIssueMark> = issues.mapNotNull { issue ->
    val kind = when (issue.kind) {
        SpecIssueKind.OVERLAP -> PreviewIssueMarkKind.OVERLAP
        SpecIssueKind.PAST_FWD_END, SpecIssueKind.PAST_AFT_END -> PreviewIssueMarkKind.OUT_OF_BOUNDS
        SpecIssueKind.TINY_SEGMENT -> return@mapNotNull null
    }
    if (issue.markEndMm <= issue.markStartMm) return@mapNotNull null
    PreviewIssueMark(issue.markStartMm, issue.markEndMm, kind, issue.boundMm)
}

/**
 * The axial range the marks reach, so the preview's layout can widen to show a part hanging off
 * an end (the authored OAL is never rewritten — only the drawn window grows). Null for no marks.
 */
fun List<PreviewIssueMark>.axialExtentMm(): Pair<Float, Float>? =
    if (isEmpty()) null else minOf { it.startMm } to maxOf { it.endMm }

/**
 * The marks' ink: a fixed warning red, not a theme role. The editor preview is theme-styled
 * (`docs/contracts/Appearance.md` — not one of the paper canvases), and this red reads on its
 * light, dark and high-contrast backgrounds alike; a theme `error` role differs per scheme, and
 * the marks are a statement about the drawing, not chrome.
 */
const val PREVIEW_ISSUE_INK: Int = 0xFFE53935.toInt()

/** The narrowest a mark's band draws (px), so a short overlap on a long shaft is still seen. */
internal const val PREVIEW_ISSUE_MIN_WIDTH_PX = 10f

/** Fill alpha of a mark's band, over the component's own fill. */
internal const val PREVIEW_ISSUE_FILL_ALPHA = 0.30f
