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
    val item: String = ""
)
