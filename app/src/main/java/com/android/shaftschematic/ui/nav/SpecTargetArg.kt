package com.android.shaftschematic.ui.nav

import com.android.shaftschematic.ui.viewmodel.SpecTarget

/**
 * The route argument naming which of the document's two drawings a PDF surface acts on.
 *
 * The preview and export routes take it as an OPTIONAL argument defaulting to
 * [TARGET_ARG_ORIGINAL], so every existing `navigate("pdfPreview")` / `navigate("exportPdf")`
 * keeps working unchanged and lands on the original schematic.
 *
 * The target travels in the route rather than as ViewModel state: the sheet previewed, the
 * sheet printed, and the file written all have to be the same geometry, and a flag read from
 * the ViewModel would put the wrong one one tab-switch away.
 */
internal const val TARGET_ARG_ORIGINAL = "original"
internal const val TARGET_ARG_FINAL = "final"

/** The route-argument spelling of [target]. */
internal fun targetArg(target: SpecTarget): String = when (target) {
    SpecTarget.ORIGINAL -> TARGET_ARG_ORIGINAL
    SpecTarget.FINAL -> TARGET_ARG_FINAL
}

/**
 * Reads a route argument back. Anything but [TARGET_ARG_FINAL] — a missing argument, a stale
 * deep link, a typo — resolves to the ORIGINAL: the drawing that always exists.
 */
internal fun specTargetFromArg(arg: String?): SpecTarget =
    if (arg == TARGET_ARG_FINAL) SpecTarget.FINAL else SpecTarget.ORIGINAL
