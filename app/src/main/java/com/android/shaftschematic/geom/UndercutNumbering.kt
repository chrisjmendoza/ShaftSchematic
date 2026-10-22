package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut

/**
 * One section's cuts in numbering order. [sectionIndex] is the section's position in the list
 * handed to [undercutSections], or null for the cuts no section holds (a zero-length cut, or one
 * that fell off a shortened shaft), which are counted as a group of their own.
 */
data class UndercutNumberedSection(val sectionIndex: Int?, val cuts: List<Undercut>) {
    /** Each cut's 1-based number within this section. */
    val numbers: Map<String, Int> get() = cuts.withIndex().associate { it.value.id to it.index + 1 }
}

/**
 * The cuts of ONE section — [sectionIds], a strip's member ids — in numbering order: **AFT → FWD
 * by stored start**, ties in [sectionIds] order. Ids the record no longer holds drop out.
 *
 * The detail page builds its card pages from exactly this list (`buildUndercutPageIds`), so a
 * cut's number here is its card's "Cut N of M" position, the N its edge-to-edge chips and overlap
 * messages name, and the number drawn inside it on the detail canvas.
 */
fun undercutSectionCuts(undercuts: List<Undercut>, sectionIds: List<String>): List<Undercut> =
    sectionIds.distinct()
        .mapNotNull { id -> undercuts.firstOrNull { it.id == id } }
        .sortedBy { it.startFromAftMm }

/**
 * The ONE cut numbering every authoring surface shows: **counted within each section**, 1-based,
 * restarting at 1 in every section (on-device ruling: "we're just counting cuts in each particular
 * section for reference, so the numbers need to match within view"). A section is a strip the
 * detail page opens — a liner, or a bare-shaft cluster — handed in as [sections], each its member
 * ids, in the order the sections should list.
 *
 * Every recorded cut is numbered: one no section holds is counted in a trailing group
 * (`sectionIndex = null`), AFT → FWD, so a list row never goes unnumbered. An input aid only: no
 * sheet prints it. Display only — the record keeps its own order (Undo restores by index), and ids
 * stay the only reference between cuts.
 */
fun undercutSections(
    undercuts: List<Undercut>,
    sections: List<List<String>>,
): List<UndercutNumberedSection> {
    val grouped = sections.mapIndexed { i, ids ->
        UndercutNumberedSection(i, undercutSectionCuts(undercuts, ids))
    }
    val held = grouped.flatMap { s -> s.cuts.map { it.id } }.toSet()
    val loose = undercuts.filter { it.id !in held }.sortedBy { it.startFromAftMm }
    return grouped + if (loose.isEmpty()) emptyList() else listOf(UndercutNumberedSection(null, loose))
}
