package com.android.shaftschematic.pdf

enum class PdfExportMode {
    Standard,
    Template
}

data class PdfExportOptions(
    val mode: PdfExportMode = PdfExportMode.Standard,
    val showDimensions: Boolean = true,
    val showLabels: Boolean = true,
    val showFooter: Boolean = true,
    /**
     * Blank-draft (write-in) mode: the full drawing, dimension lines, and footer layout are
     * kept, but every VALUE (dimension numbers, Ø callouts, footer specs, job info, date) is
     * replaced by a writable blank so the sheet can be filled in by hand in the field.
     */
    val blankValues: Boolean = false,
    /**
     * On a blank draft, whether the below-shaft Ø callout leaders print at all.
     *
     * true  → leaders and their write-in rules are drawn, ready to fill (the default, and
     *         what blank drafts have always done).
     * false → the callout pass is skipped entirely — line, arrow, and rule — leaving the
     *         shaft clear to annotate freehand. A blank leader with nothing to write on is
     *         worse than no leader, so this drops the whole mark rather than just the value.
     *
     * Ignored outside blank mode: a printed sheet always shows its diameters (subject to the
     * per-component `showDiaOnDrawing` toggles).
     */
    val blankDiaCallouts: Boolean = true,
) {
    /**
     * Whether the below-shaft Ø callout pass runs for this sheet. One rule, one place — every
     * call site that builds export options passes the raw preference and never re-derives
     * this itself.
     */
    val showDiaCallouts: Boolean get() = !blankValues || blankDiaCallouts
}
