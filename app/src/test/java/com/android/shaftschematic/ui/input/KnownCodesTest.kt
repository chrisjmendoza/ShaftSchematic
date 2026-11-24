package com.android.shaftschematic.ui.input

import org.junit.Assert.assertEquals
import org.junit.Test

class KnownCodesTest {

    @Test
    fun `a finished code word is raised`() {
        assertEquals("Acme BC ", raiseKnownCodes("Acme bc "))
        assertEquals("4 holes 1 1/4 NC, on", raiseKnownCodes("4 holes 1 1/4 nc, on"))
    }

    @Test
    fun `a code still being typed at the end waits`() {
        assertEquals("Acme bc", raiseKnownCodes("Acme bc"))
        assertEquals("unc", raiseKnownCodes("unc"))
    }

    @Test
    fun `a saved field raises its last word too`() {
        assertEquals("Acme BC", raiseKnownCodes("Acme bc", finished = true))
    }

    @Test
    fun `only whole words match`() {
        assertEquals("Uncle abc bcd ", raiseKnownCodes("Uncle abc bcd "))
        assertEquals("uncle", raiseKnownCodes("uncle", finished = true))
    }

    @Test
    fun `other text and capitals already typed are kept`() {
        assertEquals("ACME Boating Co.", raiseKnownCodes("ACME Boating Co.", finished = true))
        assertEquals("", raiseKnownCodes("", finished = true))
    }
}
