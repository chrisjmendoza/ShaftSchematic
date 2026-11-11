package com.android.shaftschematic.pdf

/**
 * How a consolidated sheet prints its dimension and footer values: [STACKED] dual (two-line
 * values, the tallest rail lanes), [INLINE] dual (`primary [secondary]` on one line, the widest
 * footer lines), [SINGLE] (the document's primary unit only — no secondary term anywhere on the
 * sheet).
 *
 * The choice is made ONCE per sheet, never per label (`docs/DualUnitStacking_PLAN.md` §7): a page
 * with some two-line and some one-line values, or some dual and some single values, reads as a
 * mistake.
 */
internal enum class SheetDualMode {
    STACKED,
    INLINE,
    SINGLE;

    val dual: Boolean get() = this != SINGLE
    val stacked: Boolean get() = this == STACKED

    companion object {
        /** The mode a sheet ASKS for: the document's dual flag and the dual-unit layout pref. */
        fun wanted(dual: Boolean, stacked: Boolean): SheetDualMode = when {
            !dual -> SINGLE
            stacked -> STACKED
            else -> INLINE
        }
    }
}

/**
 * One step of the consolidated sheet's give-back order, each step giving more height to the drawn
 * shaft than the one before: [mode] is how the sheet prints its units, [tightFooter] whether the
 * footer may close its line pitch to `footerBlockMinHeightPt` to make the room.
 *
 * The footer pitch gives BEFORE the secondary unit does: a dual document printing one unit departs
 * further from what was asked than a tighter footer does. Stacking is given up before either.
 */
internal enum class SheetFallback(val mode: SheetDualMode, val tightFooter: Boolean) {
    STACKED(SheetDualMode.STACKED, tightFooter = false),
    INLINE(SheetDualMode.INLINE, tightFooter = false),
    INLINE_TIGHT_FOOTER(SheetDualMode.INLINE, tightFooter = true),
    SINGLE(SheetDualMode.SINGLE, tightFooter = true),
}

/**
 * The steps a sheet that wants [wanted] may print at, in give-back order — [wanted] untightened
 * first. A sheet never moves to a mode richer than it asked for; a single-unit sheet has only
 * [SheetFallback.SINGLE].
 */
internal fun sheetFallbacks(wanted: SheetDualMode): List<SheetFallback> =
    SheetFallback.entries.filter { it.mode.ordinal >= wanted.ordinal }

/**
 * The first step at or after [wanted] whose layout [fits] — on the consolidated sheet, the one that
 * still affords the drawn shaft its height floor. The last step is taken unasked when nothing
 * earlier fits (it gives the most room there is), so [fits] is never consulted for it and a sheet
 * that wants [SheetDualMode.SINGLE] decides nothing. The `fittedTextScale` posture.
 */
internal fun fittedSheetFallback(wanted: SheetDualMode, fits: (SheetFallback) -> Boolean): SheetFallback {
    val steps = sheetFallbacks(wanted)
    return steps.first { it == steps.last() || fits(it) }
}
