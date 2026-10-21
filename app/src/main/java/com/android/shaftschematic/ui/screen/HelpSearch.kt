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
 * Words a question is phrased with that carry no topic of their own. Dropped from a query so
 * "how do I add a liner" searches for "add" and "liner" — kept when they are ALL the query has,
 * so a lone "why" still finds the FAQ.
 */
private val HELP_SEARCH_STOP_WORDS = setOf(
    "a", "an", "and", "are", "can", "do", "does", "for", "how", "i", "in", "is", "it", "my",
    "of", "on", "or", "the", "to", "what", "where", "why",
)

/**
 * The search words in [query]: lowercase, split on whitespace, end punctuation trimmed (inner
 * punctuation stays, so "S.E.T." and "3/4" survive whole), question filler dropped, and a plain
 * plural ending removed by [helpSearchStem] — a reader types "undercuts" for a topic titled
 * "undercut sections". Blank query → no terms.
 */
internal fun helpSearchTerms(query: String): List<String> {
    val words = query.lowercase()
        .split(Regex("\\s+"))
        .map { word -> word.trim { !it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    val content = words.filter { it !in HELP_SEARCH_STOP_WORDS }.ifEmpty { words }
    return content.map(::helpSearchStem).distinct()
}

/**
 * A search word with its plural ending dropped, so the stem is a substring of both the singular
 * and the plural in the text: "undercuts" → "undercut", "bodies" → "bod" (matches body and
 * bodies), "inches" → "inch", "TIR's" → "tir". Short words and -ss/-us/-is endings ("axis",
 * "class") are left alone.
 */
internal fun helpSearchStem(word: String): String = when {
    word.endsWith("'s") && word.length > 3 -> word.dropLast(2)
    word.length >= 5 && word.endsWith("ies") -> word.dropLast(3)
    word.length >= 5 && listOf("ches", "shes", "sses", "xes").any { word.endsWith(it) } ->
        word.dropLast(2)
    word.length >= 4 && word.endsWith("s") && listOf("ss", "us", "is").none { word.endsWith(it) } ->
        word.dropLast(1)
    else -> word
}

/** How many times [term] occurs in [text], ignoring case. */
private fun occurrences(text: String, term: String): Int {
    var count = 0
    var from = text.indexOf(term, ignoreCase = true)
    while (from >= 0) {
        count++
        from = text.indexOf(term, from + term.length, ignoreCase = true)
    }
    return count
}

/**
 * How strongly [topic] answers [terms], or null when one term is found nowhere — every word
 * must be found in the topic's title, its body, or its section's title.
 *
 * A word in the TITLE outranks any number in the body (the topic is ABOUT it); body hits add a
 * little per occurrence, capped so one long topic cannot drown a short exact one; a word only in
 * the section's title (a "glossary" query) qualifies the topic at the lowest weight.
 */
internal fun helpTopicScore(sectionTitle: String, topic: HelpTopic, terms: List<String>): Int? {
    var score = 0
    for (term in terms) {
        score += when {
            topic.title.contains(term, ignoreCase = true) -> 100
            topic.body.contains(term, ignoreCase = true) ->
                10 + occurrences(topic.body, term).coerceAtMost(5)
            sectionTitle.contains(term, ignoreCase = true) -> 5
            else -> return null
        }
    }
    return score
}

/**
 * The Help content narrowed and RANKED for [query] — see [helpSearchTerms] for what counts as a
 * word and [helpTopicScore] for what counts as a hit.
 *
 * A blank or whitespace-only query returns [sections] unchanged (identity, not a copy), so the
 * unfiltered screen is the untouched content list. Otherwise the category headings stay — a
 * result keeps saying where in Help it lives — but the categories are ordered by their best
 * topic and the topics inside each by their own score, so the topic a word is the title of
 * leads and the topics that only mention it follow. Ties keep content order, which is the
 * reader's mental index of the screen. Sections with no surviving topic are dropped.
 */
internal fun filterHelpSections(
    sections: List<HelpSection>,
    query: String,
): List<HelpSection> {
    val terms = helpSearchTerms(query)
    if (terms.isEmpty()) return sections
    return sections
        .mapNotNull { section ->
            val scored = section.topics
                .mapNotNull { topic -> helpTopicScore(section.title, topic, terms)?.let { topic to it } }
                .sortedByDescending { it.second } // stable: ties keep content order
            if (scored.isEmpty()) null
            else HelpSection(section.title, scored.map { it.first }) to scored.first().second
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** Word endings a highlight carries over — the inverse of [helpSearchStem]; longest first. */
private val HIGHLIGHT_ENDINGS = listOf("ies", "'s", "es", "s", "y")

/**
 * The spans of [text] a search for [terms] highlights: every case-insensitive occurrence of
 * each term, carried over a word ending that follows it and closes the word (so a stemmed
 * "undercut" lights all of "undercuts", and "bod" all of "body" and "bodies"), overlaps merged,
 * in text order. No terms → no spans.
 */
internal fun helpHighlightRanges(text: String, terms: List<String>): List<IntRange> {
    val hits = mutableListOf<IntRange>()
    for (term in terms) {
        var from = text.indexOf(term, ignoreCase = true)
        while (from >= 0) {
            var end = from + term.length
            for (ending in HIGHLIGHT_ENDINGS) {
                if (text.regionMatches(end, ending, 0, ending.length, ignoreCase = true) &&
                    (end + ending.length == text.length || !text[end + ending.length].isLetterOrDigit())
                ) {
                    end += ending.length
                    break
                }
            }
            hits += from until end
            from = text.indexOf(term, from + term.length, ignoreCase = true)
        }
    }
    val merged = mutableListOf<IntRange>()
    for (range in hits.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
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
