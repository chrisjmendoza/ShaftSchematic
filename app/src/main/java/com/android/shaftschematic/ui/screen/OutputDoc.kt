package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.ShaftPosition

/**
 * Document identities for the Consolidated Output tab (`EditorTab.OUTPUT`) — the surface
 * that previews/prints/exports the consolidated sheet and batch-exports the full document
 * set. Each original tab (Schematic, Runout, Wear, Undercut) keeps its own hard-wired
 * buttons producing its own document; this enum names every exportable document once, so
 * filenames and the export-all set stay consistent everywhere.
 */
enum class OutputDoc(
    /** Short label for chips/checkbox rows. */
    val label: String,
    /** Full document name for button text and preview titles. */
    val docName: String,
    /** Filename suffix — matches each document's historical export naming. */
    val filenameSuffix: String,
    /** Fallback filename base when customer/vessel/job are all blank. */
    val fallbackBase: String,
) {
    CONSOLIDATED("Consolidated", "Consolidated Sheet", "consolidated", "ConsolidatedSheet"),
    RUNOUT("Runout", "Runout Sheet", "runout", "RunoutSheet"),
    SCHEMATIC("Schematic", "Schematic", "schematic", "Schematic"),
    WEAR("Wear", "Wear Document", "wear", "WearDocument"),
    UNDERCUT("Undercut", "Undercut Drawing", "undercuts", "Undercuts"),
}

/**
 * Which information the consolidated sheet carries (on-device request: "select which I
 * want — the schematic and runouts only, the schematic and wear, all three"). The
 * schematic's dimension rails and footer are always on; runout bubbles/TIR and the wear
 * marks/values are each electable. Maps to `composeRunoutPdf`'s
 * `includeBubbles`/`includeWearInfo` flags. [ALL] is the default — a normal inspection
 * wants the complete sheet.
 */
enum class ConsolidatedVariant(
    val label: String,
    val includeBubbles: Boolean,
    val includeWearInfo: Boolean,
) {
    ALL("All three", includeBubbles = true, includeWearInfo = true),
    SCHEMATIC_RUNOUT("Schematic + Runout", includeBubbles = true, includeWearInfo = false),
    SCHEMATIC_WEAR("Schematic + Wear", includeBubbles = false, includeWearInfo = true),
}

/**
 * Export filename for a document:
 * `{customer_vessel_job[_SIDE] | fallback}_{suffix}[_BlankDraft].pdf`.
 *
 * [shaftPosition] is required, not defaulted: a twin-screw job runs two shafts under ONE job
 * number, so without the side both mates propose the same filename and the second export
 * silently replaces the first. It follows the schematic's naming
 * (`DocumentNaming.suggestedBaseName` + `ShaftPosition.printableLabelOrNull`): last part of
 * the base, dropped entirely for a side that prints nothing, and never a filename on its own —
 * with nothing else to go on the document's [OutputDoc.fallbackBase] still wins.
 */
internal fun buildOutputFilename(
    customer: String,
    vessel: String,
    jobNumber: String,
    shaftPosition: ShaftPosition,
    doc: OutputDoc,
    blankDraft: Boolean = false,
): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    val side = shaftPosition.printableLabelOrNull()
    val base = if (parts.isNotEmpty()) {
        (parts + listOfNotNull(side)).joinToString("_")
    } else {
        doc.fallbackBase
    }
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return "${base}_${doc.filenameSuffix}$blankSuffix.pdf"
}
