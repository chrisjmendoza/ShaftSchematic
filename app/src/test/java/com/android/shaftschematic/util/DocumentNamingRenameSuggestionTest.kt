package com.android.shaftschematic.util

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentNamingRenameSuggestionTest {

    @Test
    fun renameSuggestionBase_unnamedDocument_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = null,
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
            )
        )
    }

    @Test
    fun renameSuggestionBase_noProjectInfo_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "Shaft_20260810_1432$SHAFT_DOT_EXT",
                jobNumber = "",
                customer = "  ",
                vessel = "\t",
            )
        )
    }

    @Test
    fun renameSuggestionBase_suffixAloneIsNotAName_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "Shaft_20260810_1432$SHAFT_DOT_EXT",
                jobNumber = "",
                customer = "",
                vessel = "",
                positionSuffix = "PORT",
            )
        )
    }

    @Test
    fun renameSuggestionBase_nameAlreadyMatches_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "J-123 - Acme Marine - Sea Queen$SHAFT_DOT_EXT",
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
            )
        )
    }

    @Test
    fun renameSuggestionBase_nameMatchesInDifferentCase_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "j-123 - acme marine - SEA QUEEN$SHAFT_DOT_EXT",
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
            )
        )
    }

    @Test
    fun renameSuggestionBase_nameMatchesIncludingSuffix_returnsNull() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "J-123 - Acme Marine - Sea Queen - PORT$SHAFT_DOT_EXT",
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
                positionSuffix = "PORT",
            )
        )
    }

    @Test
    fun renameSuggestionBase_staleDateName_returnsSuggestion() {
        val current = "Shaft_20260810_1432$SHAFT_DOT_EXT"
        val suggestion = DocumentNaming.renameSuggestionBase(
            currentDocumentName = current,
            jobNumber = "J-123",
            customer = "Acme Marine",
            vessel = "Sea Queen",
        )

        assertEquals("J-123 - Acme Marine - Sea Queen", suggestion)
        assertEquals(
            DocumentNaming.suggestedBaseName(
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
            ),
            suggestion
        )
    }

    @Test
    fun renameSuggestionBase_extensionlessCurrentName_stillCompares() {
        assertNull(
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "J-123 - Acme Marine - Sea Queen",
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
            )
        )
    }

    @Test
    fun renameSuggestionBase_onlySuffixDiffers_returnsSuggestion() {
        assertEquals(
            "J-123 - Acme Marine - Sea Queen - PORT",
            DocumentNaming.renameSuggestionBase(
                currentDocumentName = "J-123 - Acme Marine - Sea Queen$SHAFT_DOT_EXT",
                jobNumber = "J-123",
                customer = "Acme Marine",
                vessel = "Sea Queen",
                positionSuffix = "PORT",
            )
        )
    }
}
