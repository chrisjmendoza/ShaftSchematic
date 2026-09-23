package com.android.shaftschematic.model

/**
 * Minimal project metadata used by the PDF footer.
 * Keep fields generic so routes can adapt easily.
 */
data class ProjectInfo(
    val customer: String = "",
    val vessel: String = "",
    val side: ShaftPosition = ShaftPosition.OTHER,
    val jobNumber: String = "",
    /**
     * Optional shaft designation ("Tail shaft", "Line shaft", …). Blank by default and
     * skipped entirely in printed headers/footers when blank — an optional field must not
     * print an orphan label. Blank-draft (write-in) sheets always rule a line for it.
     */
    val item: String = "",
    /**
     * Optional label naming WHICH drawing of the document this sheet is ("Final" for the
     * final schematic / final runout sheet). Blank by default — every existing caller — and
     * prints nothing anywhere, so untouched output stays byte-identical. Set, it prints a
     * "Drawing: <label>" line in the footer job block and the sheet header's job-info line,
     * plus a bold badge beside the Side badge on the schematic footer, so a sheet from a
     * non-original drawing can never be mistaken for one.
     */
    val drawingLabel: String = ""
)
