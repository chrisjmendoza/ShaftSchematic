package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The undercut sheet's title line carries the shaft side in bold capitals
 * (`drawSheetHeader(boldSideOnTitle = true)`): the side comes off the job line so it prints once,
 * and a job with no side — or a sheet that does not ask — keeps the ordinary header.
 */
class SheetHeaderBoldSideTest {
    private val date = "2026-01-02"
    private fun project(side: ShaftPosition) =
        ProjectInfo(customer = "Pac-Fish", vessel = "Royal Atlantic", jobNumber = "935066", side = side)

    @Test
    fun `a job with a side moves it to the title line, in capitals`() {
        val (line, bold) = sheetHeaderJobLineAndBoldSide(project(ShaftPosition.PORT), date, boldSideOnTitle = true)
        assertEquals("PORT", bold)
        assertFalse(line.contains("PORT"))
        assertTrue(line.endsWith("Date: $date"))
        assertEquals("CENTER", sheetHeaderJobLineAndBoldSide(project(ShaftPosition.CENTER), date, true).second)
    }

    @Test
    fun `no side, or a sheet that does not ask, prints the ordinary job line`() {
        val none = project(ShaftPosition.OTHER)
        assertEquals(jobInfoHeaderLine(none, date) to null, sheetHeaderJobLineAndBoldSide(none, date, true))
        val port = project(ShaftPosition.PORT)
        val (line, bold) = sheetHeaderJobLineAndBoldSide(port, date, boldSideOnTitle = false)
        assertNull(bold)
        assertEquals(jobInfoHeaderLine(port, date), line)
        assertTrue(line.endsWith("PORT"))
    }
}
