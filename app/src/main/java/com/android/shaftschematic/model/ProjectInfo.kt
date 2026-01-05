package com.android.shaftschematic.model

/**
 * Minimal project metadata used by the PDF footer.
 * Keep fields generic so routes can adapt easily.
 */
data class ProjectInfo(
    val customer: String = "",
    val vessel: String = "",
    val side: ShaftPosition = ShaftPosition.OTHER,
    val jobNumber: String = ""
)
