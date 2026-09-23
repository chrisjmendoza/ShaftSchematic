// file: app/src/main/java/com/android/shaftschematic/ui/screen/HelpSearch.kt
package com.android.shaftschematic.ui.screen

/**
 * Pure Help-screen logic: topic keys, search filtering, and the flat list index a deep link
 * scrolls to. No Android, no Compose — everything here is unit-testable on the JVM, so the
 * screen keeps only the Compose work.
 */

/**
 * The stable slug a topic is addressed by — the `LazyColumn` item key, the `rememberSaveable`
 * expansion key, and the `topic` query argument of the `help` route.
 *
 * Derived from the title so no topic carries a hand-written key that can drift from the text
 * above it; the trade is that titles must stay distinct under slugification, which
 * `HelpSearchTest` asserts across the real content.
 */
internal fun helpTopicKey(title: String): String =
    title.lowercase()
        .map { c -> if (c in 'a'..'z' || c in '0'..'9') c else '-' }
        .joinToString("")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString("-")

// Topic keys the document tabs deep-link to. Constants rather than literals at the call
// sites so a renamed topic breaks one test instead of silently opening Help at the top;
// `HelpSearchTest` pins each of these to a topic that still exists.
internal const val HELP_TOPIC_RECORD_RUNOUT = "record-runout"
internal const val HELP_TOPIC_RECORD_WEAR = "record-wear-readings"
internal const val HELP_TOPIC_RECORD_UNDERCUT = "record-undercut-sections"
internal const val HELP_TOPIC_CONSOLIDATED_OUTPUT = "consolidated-output-and-export-all"

/**
 * The Help content narrowed to [query] — case-insensitive substring over title OR body.
 *
 * A blank or whitespace-only query returns [sections] unchanged (identity, not a copy), so the
 * unfiltered screen is the untouched content list. Sections with no surviving topic are
 * dropped; section and topic order are preserved, because the reader's mental index of the
 * screen is its order.
 */
internal fun filterHelpSections(
    sections: List<HelpSection>,
    query: String,
): List<HelpSection> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return sections
    return sections.mapNotNull { section ->
        val hits = section.topics.filter { topic ->
            topic.title.lowercase().contains(needle) || topic.body.lowercase().contains(needle)
        }
        if (hits.isEmpty()) null else HelpSection(section.title, hits)
    }
}

/**
 * The flat `LazyColumn` index of the topic keyed [topicKey], or null when nothing matches —
 * an unknown key leaves the list at the top rather than throwing or guessing.
 *
 * Counts items exactly as the screen lays them out: one header per section, then its topics.
 * A second counting rule in the composable is what would make a deep link land beside its
 * topic instead of on it.
 */
internal fun helpTopicItemIndex(sections: List<HelpSection>, topicKey: String?): Int? {
    if (topicKey.isNullOrBlank()) return null
    var index = 0
    sections.forEach { section ->
        index++
        section.topics.forEach { topic ->
            if (topic.key == topicKey) return index
            index++
        }
    }
    return null
}
