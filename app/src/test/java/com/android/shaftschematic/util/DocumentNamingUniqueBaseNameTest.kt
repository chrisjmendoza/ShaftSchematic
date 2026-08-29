package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DocumentNaming.uniqueBaseName] — the shared "don't land on a name that already exists"
 * suffixer behind the duplicate-for-mate dialog.
 */
class DocumentNamingUniqueBaseNameTest {

    @Test
    fun `a free name is returned verbatim`() {
        assertEquals(
            "J-1 - Acme - STBD",
            DocumentNaming.uniqueBaseName(listOf("J-1 - Acme - PORT"), "J-1 - Acme - STBD"),
        )
    }

    @Test
    fun `an empty store never suffixes`() {
        assertEquals("Shaft", DocumentNaming.uniqueBaseName(emptyList(), "Shaft"))
    }

    @Test
    fun `a taken name gets the first free number`() {
        assertEquals("Shaft (2)", DocumentNaming.uniqueBaseName(listOf("Shaft"), "Shaft"))
    }

    @Test
    fun `the collision check ignores case`() {
        // The document store treats "shaft" and "Shaft" as one file, so a case-only match
        // would still overwrite.
        assertEquals("Shaft (2)", DocumentNaming.uniqueBaseName(listOf("SHAFT"), "Shaft"))
        assertEquals("shaft (2)", DocumentNaming.uniqueBaseName(listOf("Shaft"), "shaft"))
    }

    @Test
    fun `numbering steps past every taken candidate`() {
        assertEquals(
            "Shaft (4)",
            DocumentNaming.uniqueBaseName(listOf("Shaft", "shaft (2)", "Shaft (3)"), "Shaft"),
        )
    }

    @Test
    fun `a gap in the numbering is filled`() {
        assertEquals(
            "Shaft (2)",
            DocumentNaming.uniqueBaseName(listOf("Shaft", "Shaft (3)"), "Shaft"),
        )
    }

    @Test
    fun `an already-numbered desired name numbers again rather than counting on`() {
        assertEquals(
            "Shaft (2) (2)",
            DocumentNaming.uniqueBaseName(listOf("Shaft (2)"), "Shaft (2)"),
        )
    }
}
