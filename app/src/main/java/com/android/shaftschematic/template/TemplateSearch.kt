package com.android.shaftschematic.template

/**
 * TemplateSearch — the browser's find-and-order rules, kept away from Compose.
 *
 * A template is found by two different facts: the name the user typed when they saved it, and the
 * SHAPE the browser derived for it (`template/TemplateDescriptor.kt`). Searching only names would
 * miss every template whose name says nothing — which is most of them, since a template is named
 * after a family rather than a job. So the query runs over the descriptor too: `A-A-F` finds a
 * layout, `3 liners` finds a count, `6"` finds a size.
 *
 * Generic over the row type on purpose: the accessors are what this file needs, and taking them as
 * lambdas keeps the whole thing Android-free (the browser's row type is `TemplateStorage`'s, which
 * is not) and unit-testable.
 */

/** Which column the browser orders by. Mirrors the Open screen's pair of chips. */
enum class TemplateSortColumn { NAME, DATE }

/** Ascending or descending, toggled by re-tapping the active chip. */
enum class TemplateSortDir { ASC, DESC }

/**
 * Whether [query] matches this template, case-insensitively, as a substring of either its
 * [displayName] or its derived [descriptor].
 *
 * A blank query matches everything — "no filter" and "filter that admits everything" are the same
 * list, and making the caller special-case blankness is how one of the two call paths forgets to.
 *
 * The descriptor's zone separator is normalized so a user typing the FILENAME form (`A-A-F`, what
 * `suggestedTemplateName` seeds) finds a card captioned in the display form (`A·A·F`), and vice
 * versa. The two spellings are the same fact.
 */
fun templateMatchesQuery(displayName: String, descriptor: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val needle = normalizeForSearch(q)
    return normalizeForSearch(displayName).contains(needle) ||
        normalizeForSearch(descriptor).contains(needle)
}

/** Lower-cased with the zone separators folded together — see [templateMatchesQuery]. */
private fun normalizeForSearch(s: String): String =
    s.lowercase().replace(TEMPLATE_ZONE_SEPARATOR, TEMPLATE_ZONE_NAME_SEPARATOR)

/**
 * [items] filtered by [query] and ordered by [column] / [dir].
 *
 * Order is stable within equal keys (`sortedBy` is), so two templates saved in the same millisecond
 * keep the store's own order rather than shuffling between recompositions.
 */
fun <T> filterAndSortTemplates(
    items: List<T>,
    query: String,
    column: TemplateSortColumn,
    dir: TemplateSortDir,
    displayName: (T) -> String,
    descriptor: (T) -> String,
    updatedAtEpochMs: (T) -> Long,
): List<T> {
    val filtered = items.filter { templateMatchesQuery(displayName(it), descriptor(it), query) }
    return sortTemplates(filtered, column, dir, displayName, updatedAtEpochMs)
}

/** [items] in [column]/[dir] order, unfiltered — what the accordion applies inside each group. */
fun <T> sortTemplates(
    items: List<T>,
    column: TemplateSortColumn,
    dir: TemplateSortDir,
    displayName: (T) -> String,
    updatedAtEpochMs: (T) -> Long,
): List<T> {
    val comparator = when (column) {
        TemplateSortColumn.NAME -> compareBy<T> { displayName(it).lowercase() }
        TemplateSortColumn.DATE -> compareBy<T> { updatedAtEpochMs(it) }
    }
    // Reverse the COMPARATOR, not the sorted list: a stable sort keeps equal keys in the
    // store's order either direction, where reversing the list would flip them.
    return items.sortedWith(if (dir == TemplateSortDir.DESC) comparator.reversed() else comparator)
}
