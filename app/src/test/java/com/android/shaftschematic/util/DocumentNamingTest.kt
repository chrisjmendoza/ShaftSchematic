package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentNamingTest {

    @Test
    fun suggestedBaseName_allBlank_returnsNull() {
        assertNull(DocumentNaming.suggestedBaseName(jobNumber = "", customer = "", vessel = ""))
        assertNull(DocumentNaming.suggestedBaseName(jobNumber = "  ", customer = "\n", vessel = "\t"))
    }

    @Test
    fun suggestedBaseName_ordersJobThenCustomerThenVessel() {
        assertEquals(
            "J-123 - Acme Marine - Sea Queen",
            DocumentNaming.suggestedBaseName(jobNumber = "J-123", customer = "Acme Marine", vessel = "Sea Queen")
        )
    }

    @Test
    fun suggestedBaseName_omitsMissingParts_keepsOrder() {
        assertEquals(
            "J-123 - Sea Queen",
            DocumentNaming.suggestedBaseName(jobNumber = "J-123", customer = "", vessel = "Sea Queen")
        )
        assertEquals(
            "Acme Marine",
            DocumentNaming.suggestedBaseName(jobNumber = "", customer = "Acme Marine", vessel = "")
        )
    }

    @Test
    fun suggestedBaseName_sanitizes_problemCharacters() {
        assertEquals(
            "J_123 - A_B - Sea_Queen",
            DocumentNaming.suggestedBaseName(jobNumber = "J/123", customer = "A:B", vessel = "Sea|Queen")
        )
    }
}
