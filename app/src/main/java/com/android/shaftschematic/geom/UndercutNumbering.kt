package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut

/**
 * The ONE cut numbering every surface shows — the printed sheet's cut numbers, the Undercut tab's
 * list rows, and any message that names a cut: **AFT → FWD by render-clamped start**, ties in
 * record order, 1-based. Spatial rather than record order because the sheet is read left to
 * right and the cuts are rarely recorded in that order (on-device request). Display only — the
 * record keeps its own order (Undo restores by index), and ids stay the only reference between
 * cuts. Every cut gets a number, a zero-length or out-of-shaft one included, so a list row never
 * goes unnumbered.
 */
fun undercutSheetNumbers(undercuts: List<Undercut>, oalMm: Float): Map<String, Int> =
    undercuts.withIndex()
        .sortedWith(
            compareBy<IndexedValue<Undercut>> { clampUndercutSpan(it.value.startFromAftMm, it.value.lengthMm, oalMm).startMm }
                .thenBy { it.index },
        )
        .mapIndexed { n, iv -> iv.value.id to n + 1 }
        .toMap()
