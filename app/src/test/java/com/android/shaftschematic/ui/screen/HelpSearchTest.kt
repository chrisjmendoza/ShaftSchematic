package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Help screen's pure layer: slug keys, search filtering, and the deep-link item index.
 * Plain JUnit — none of it touches Compose, which is the point of keeping it out of the
 * screen file.
 */
class HelpSearchTest {

    private val sections = listOf(
        HelpSection(
            "Alpha",
            listOf(
                HelpTopic("Record runout", "Stations and TIR values per component."),
                HelpTopic("Record wear readings", "Pits, wear areas, and measured diameters."),
            ),
        ),
        HelpSection(
            "Beta",
            listOf(
                HelpTopic("Where are my files?", "Shafts live in the app's private storage."),
            ),
        ),
    )

    // ── Keys ────────────────────────────────────────────────────────────────

    @Test
    fun `a title slugifies to kebab case`() {
        assertEquals("record-runout", helpTopicKey("Record runout"))
        assertEquals("record-undercut-sections", helpTopicKey("Record undercut sections"))
        assertEquals(
            "consolidated-output-and-export-all",
            helpTopicKey("Consolidated output and Export all"),
        )
    }

    @Test
    fun `punctuation collapses and never leads or trails`() {
        assertEquals("aft-fwd", helpTopicKey("AFT / FWD"))
        assertEquals("tir-total-indicated-runout", helpTopicKey("TIR (total indicated runout)"))
        assertEquals("why-are-my-files", helpTopicKey("  Why are my files?  "))
    }

    @Test
    fun `a topic carries the key its title yields`() {
        assertEquals("record-runout", HelpTopic("Record runout", "body").key)
    }

    @Test
    fun `every real topic key is unique`() {
        val keys = helpSections.flatMap { it.topics }.map { it.key }
        val duplicates = keys.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("Help topic keys must be unique; duplicated: $duplicates", duplicates.isEmpty())
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `no real topic slugifies to nothing`() {
        helpSections.flatMap { it.topics }.forEach { topic ->
            assertTrue("‘${topic.title}’ has no usable key", topic.key.isNotEmpty())
        }
    }

    @Test
    fun `every deep-linked key names a topic that still exists`() {
        val keys = helpSections.flatMap { it.topics }.map { it.key }.toSet()
        listOf(
            HELP_TOPIC_RECORD_RUNOUT,
            HELP_TOPIC_RECORD_WEAR,
            HELP_TOPIC_RECORD_UNDERCUT,
            HELP_TOPIC_CONSOLIDATED_OUTPUT,
        ).forEach { key ->
            assertTrue("no Help topic keyed ‘$key’", key in keys)
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────

    @Test
    fun `a blank query returns the content unchanged`() {
        assertSame(sections, filterHelpSections(sections, ""))
        assertSame(sections, filterHelpSections(sections, "   "))
    }

    @Test
    fun `a title hit keeps its topic`() {
        val hit = filterHelpSections(sections, "runout")
        assertEquals(listOf("Alpha"), hit.map { it.title })
        assertEquals(listOf("Record runout"), hit.single().topics.map { it.title })
    }

    @Test
    fun `a body hit keeps its topic`() {
        val hit = filterHelpSections(sections, "private storage")
        assertEquals(listOf("Beta"), hit.map { it.title })
        assertEquals(listOf("Where are my files?"), hit.single().topics.map { it.title })
    }

    @Test
    fun `matching ignores case on both sides`() {
        assertEquals(1, filterHelpSections(sections, "TIR").single().topics.size)
        assertEquals(1, filterHelpSections(sections, "tir").single().topics.size)
        assertEquals(1, filterHelpSections(sections, "ReCoRd WeAr").single().topics.size)
    }

    @Test
    fun `a section with no matching topic is dropped, and order is preserved`() {
        val hit = filterHelpSections(sections, "record")
        assertEquals(listOf("Alpha"), hit.map { it.title })
        assertEquals(
            listOf("Record runout", "Record wear readings"),
            hit.single().topics.map { it.title },
        )
    }

    @Test
    fun `a query nothing matches yields no sections`() {
        assertTrue(filterHelpSections(sections, "zzz no such topic").isEmpty())
    }

    @Test
    fun `surrounding whitespace does not defeat a match`() {
        assertEquals(1, filterHelpSections(sections, "  runout  ").single().topics.size)
    }

    // ── Search words ────────────────────────────────────────────────────────

    @Test
    fun `a query splits into words with question filler dropped`() {
        assertEquals(listOf("add", "liner"), helpSearchTerms("How do I add a liner?"))
    }

    @Test
    fun `filler is kept when it is all the query has`() {
        assertEquals(listOf("why"), helpSearchTerms("Why"))
    }

    @Test
    fun `end punctuation is trimmed but inner punctuation survives`() {
        assertEquals(listOf("s.e.t"), helpSearchTerms("S.E.T."))
        assertEquals(listOf("3/4"), helpSearchTerms("(3/4)"))
        assertTrue(helpSearchTerms("  ?! ").isEmpty())
    }

    @Test
    fun `plural endings stem to a substring of singular and plural`() {
        assertEquals("undercut", helpSearchStem("undercuts"))
        assertEquals("bod", helpSearchStem("bodies"))
        assertEquals("inch", helpSearchStem("inches"))
        assertEquals("tir", helpSearchStem("tir's"))
        assertEquals("axis", helpSearchStem("axis"))
        assertEquals("class", helpSearchStem("class"))
        assertEquals("oal", helpSearchStem("oal"))
    }

    @Test
    fun `words match anywhere, not as one phrase`() {
        // "runout" is in the title, "TIR" in the body — a phrase substring found neither order.
        assertEquals(
            listOf("Record runout"),
            filterHelpSections(sections, "TIR runout").single().topics.map { it.title },
        )
    }

    @Test
    fun `every word must be found`() {
        assertTrue(filterHelpSections(sections, "runout storage").isEmpty())
    }

    @Test
    fun `a plural query finds the real undercut topic first`() {
        val hit = filterHelpSections(helpSections, "undercuts")
        assertEquals("Record undercut sections", hit.first().topics.first().title)
    }

    // ── Ranking ─────────────────────────────────────────────────────────────

    private val ranked = listOf(
        HelpSection(
            "Early",
            listOf(
                HelpTopic("Saving", "A liner is mentioned here once."),
                HelpTopic("Liners and threads", "Sleeves over the shaft."),
            ),
        ),
        HelpSection(
            "Late",
            listOf(HelpTopic("Liner shoulders", "A step at the liner end.")),
        ),
    )

    @Test
    fun `a title hit leads its section`() {
        val early = filterHelpSections(ranked, "liner").first { it.title == "Early" }
        assertEquals(listOf("Liners and threads", "Saving"), early.topics.map { it.title })
    }

    @Test
    fun `a section is ordered by its best topic`() {
        val only = listOf(
            HelpSection("Mentions", listOf(HelpTopic("Saving", "A liner, a liner, a liner."))),
            HelpSection("About", listOf(HelpTopic("Liner shoulders", "Steps."))),
        )
        assertEquals(listOf("About", "Mentions"), filterHelpSections(only, "liner").map { it.title })
    }

    @Test
    fun `equal scores keep content order`() {
        assertEquals(listOf("Early", "Late"), filterHelpSections(ranked, "liner").map { it.title })
    }

    @Test
    fun `a section-title word qualifies every topic in it`() {
        assertEquals(
            listOf("Where are my files?"),
            filterHelpSections(sections, "beta").single().topics.map { it.title },
        )
    }

    // ── Highlight ───────────────────────────────────────────────────────────

    @Test
    fun `a highlight covers every occurrence, ignoring case`() {
        assertEquals(
            listOf(0..7, 13..20),
            helpHighlightRanges("Undercut and undercut", listOf("undercut")),
        )
    }

    @Test
    fun `a highlight carries over the plural the stem dropped`() {
        assertEquals(listOf(4..12), helpHighlightRanges("Two undercuts.", listOf("undercut")))
        assertEquals(listOf(0..3, 9..14), helpHighlightRanges("body and bodies", listOf("bod")))
    }

    @Test
    fun `a highlight never runs into the rest of a longer word`() {
        // "s" is followed by more letters, so it is not a plural ending here.
        assertEquals(listOf(0..4), helpHighlightRanges("taperset", listOf("taper")))
    }

    @Test
    fun `overlapping hits merge`() {
        assertEquals(listOf(0..7), helpHighlightRanges("undercut", listOf("under", "dercut")))
    }

    @Test
    fun `no terms highlight nothing`() {
        assertTrue(helpHighlightRanges("anything", emptyList()).isEmpty())
    }

    // ── Deep-link index ─────────────────────────────────────────────────────

    @Test
    fun `the item index counts one header per section`() {
        // 0 = "Alpha" header, 1 = Record runout, 2 = Record wear readings,
        // 3 = "Beta" header, 4 = Where are my files?
        assertEquals(1, helpTopicItemIndex(sections, "record-runout"))
        assertEquals(2, helpTopicItemIndex(sections, "record-wear-readings"))
        assertEquals(4, helpTopicItemIndex(sections, "where-are-my-files"))
    }

    @Test
    fun `an unknown, blank, or absent key has no index`() {
        assertNull(helpTopicItemIndex(sections, "no-such-topic"))
        assertNull(helpTopicItemIndex(sections, ""))
        assertNull(helpTopicItemIndex(sections, null))
    }

    @Test
    fun `every deep-linked key resolves to an index in the real content`() {
        listOf(
            HELP_TOPIC_RECORD_RUNOUT,
            HELP_TOPIC_RECORD_WEAR,
            HELP_TOPIC_RECORD_UNDERCUT,
            HELP_TOPIC_CONSOLIDATED_OUTPUT,
        ).forEach { key ->
            assertNotNull("‘$key’ must resolve to a list position", helpTopicItemIndex(helpSections, key))
        }
    }
}
