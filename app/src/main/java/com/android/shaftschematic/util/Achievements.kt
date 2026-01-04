package com.android.shaftschematic.util

/**
 * Achievements are purely UI/engagement state.
 * They must not affect geometry or units (mm-only model remains canonical).
 */
object Achievements {

    object Id {
        const val FIRST_SAVE = "first_save"
        const val FIRST_PDF = "first_pdf"
        const val DEVOPS_UNLOCKED = "devops_unlocked"
    }

    data class Definition(
        val id: String,
        val title: String,
        val description: String,
    )

    val all: List<Definition> = listOf(
        Definition(
            id = Id.FIRST_SAVE,
            title = "First Save",
            description = "Save a drawing to the app’s storage.",
        ),
        Definition(
            id = Id.FIRST_PDF,
            title = "First PDF",
            description = "Export a drawing as a PDF.",
        ),
        Definition(
            id = Id.DEVOPS_UNLOCKED,
            title = "DevOps Unlocked",
            description = "Unlock Developer options.",
        ),
    )

    fun byId(id: String): Definition? = all.firstOrNull { it.id == id }
}
