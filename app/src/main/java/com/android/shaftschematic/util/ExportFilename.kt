package com.android.shaftschematic.util

/**
 * ExportFilename — the one join behind an exported PDF's name.
 *
 * `<base><drawingSuffix>[_BlankDraft].pdf`. The drawing suffix names WHICH drawing of the
 * document a sheet is ("_Final", "_Final_Runout"); the blank-draft marker always comes last,
 * so a write-in copy of any drawing reads as that drawing plus "_BlankDraft" rather than
 * burying the drawing's identity in the middle of the name.
 *
 * Pure — no Android, no formatting decisions of its own. The base comes from
 * `DocumentNaming.suggestedBaseName` (or a caller's fallback) and is used verbatim.
 */
internal fun exportPdfFilename(
    base: String,
    drawingSuffix: String = "",
    blankDraft: Boolean = false,
): String = base + drawingSuffix + (if (blankDraft) "_BlankDraft" else "") + ".pdf"
