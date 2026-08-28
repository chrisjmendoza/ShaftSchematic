package com.android.shaftschematic.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A template is found by its NAME or by its derived SHAPE. Most templates are named after a
 * family rather than a job, so a name-only search would miss the layouts entirely — "A-A-F" and
 * "3 liners" have to reach the descriptor.
 */
class TemplateSearchTest {

    private data class Row(val name: String, val descriptor: String, val at: Long)

    private val rows = listOf(
        Row("6in 3 liners A-M-F", "94 1/2\" OAL · Ø6\" max · 3 liners (A·M·F)", 300L),
        Row("Tug spare", "120\" OAL · Ø8\" max · 2 liners (A·F)", 100L),
        Row("straight stock", "60\" OAL · Ø4\" max · No liners", 200L),
    )

    private fun filtered(query: String) = filterAndSortTemplates(
        items = rows,
        query = query,
        column = TemplateSortColumn.DATE,
        dir = TemplateSortDir.DESC,
        displayName = { it.name },
        descriptor = { it.descriptor },
        updatedAtEpochMs = { it.at },
    )

    @Test
    fun `a name substring matches`() {
        assertTrue(templateMatchesQuery("Tug spare", "anything", "tug"))
        assertEquals(listOf("Tug spare"), filtered("spare").map { it.name })
    }

    @Test
    fun `a descriptor substring matches when the name says nothing`() {
        assertEquals(listOf("Tug spare"), filtered("2 liners").map { it.name })
        assertEquals(listOf("straight stock"), filtered("No liners").map { it.name })
    }

    @Test
    fun `a zone string matches in either spelling`() {
        // The card prints "A·M·F"; the save dialog seeds the filename form "A-M-F". One fact.
        assertEquals(listOf("6in 3 liners A-M-F"), filtered("A·M·F").map { it.name })
        assertEquals(listOf("6in 3 liners A-M-F"), filtered("a-m-f").map { it.name })
    }

    @Test
    fun `matching is case-insensitive on both fields`() {
        assertTrue(templateMatchesQuery("Tug Spare", "", "TUG"))
        assertTrue(templateMatchesQuery("", "3 LINERS (A·M·F)", "3 liners"))
    }

    @Test
    fun `a non-match is a non-match`() {
        assertFalse(templateMatchesQuery("Tug spare", "120\" OAL", "gearbox"))
        assertTrue(filtered("gearbox").isEmpty())
    }

    @Test
    fun `a blank query passes everything through`() {
        assertTrue(templateMatchesQuery("anything", "anything", ""))
        assertTrue(templateMatchesQuery("anything", "anything", "   "))
        assertEquals(rows.size, filtered("").size)
        assertEquals(rows.size, filtered("   ").size)
    }

    @Test
    fun `sorting by name is alphabetical regardless of case`() {
        val asc = sortTemplates(rows, TemplateSortColumn.NAME, TemplateSortDir.ASC, { it.name }, { it.at })
        assertEquals(listOf("6in 3 liners A-M-F", "straight stock", "Tug spare"), asc.map { it.name })

        val desc = sortTemplates(rows, TemplateSortColumn.NAME, TemplateSortDir.DESC, { it.name }, { it.at })
        assertEquals(listOf("Tug spare", "straight stock", "6in 3 liners A-M-F"), desc.map { it.name })
    }

    @Test
    fun `date descending is the store's own newest-first order`() {
        val desc = sortTemplates(rows, TemplateSortColumn.DATE, TemplateSortDir.DESC, { it.name }, { it.at })
        assertEquals(listOf(300L, 200L, 100L), desc.map { it.at })

        val asc = sortTemplates(rows, TemplateSortColumn.DATE, TemplateSortDir.ASC, { it.name }, { it.at })
        assertEquals(listOf(100L, 200L, 300L), asc.map { it.at })
    }

    @Test
    fun `filtering and sorting compose`() {
        val hits = filterAndSortTemplates(
            items = rows,
            query = "liners",
            column = TemplateSortColumn.NAME,
            dir = TemplateSortDir.ASC,
            displayName = { it.name },
            descriptor = { it.descriptor },
            updatedAtEpochMs = { it.at },
        )
        // All three descriptors mention liners; the order is the chips', not the store's.
        assertEquals(listOf("6in 3 liners A-M-F", "straight stock", "Tug spare"), hits.map { it.name })
    }
}
