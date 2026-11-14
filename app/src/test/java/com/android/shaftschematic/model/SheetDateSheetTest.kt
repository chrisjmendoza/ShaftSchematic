package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Date on this sheet" — the per-job, per-sheet switch over the job's one date: the ONE resolver
 * [sheetPrintsDate] (the job's choice, else the shipped default) and the ONE helper [forSheet]
 * every print site builds its composer's project through.
 */
class SheetDateSheetTest {

    private val today = "2026-11-13"

    @Test
    fun `the schematic, consolidated and final sheets print the date by default`() {
        assertTrue(sheetPrintsDate(SheetDateSheet.SCHEMATIC, emptyMap()))
        assertTrue(sheetPrintsDate(SheetDateSheet.CONSOLIDATED, emptyMap()))
        assertTrue(sheetPrintsDate(SheetDateSheet.FINAL, emptyMap()))
    }

    @Test
    fun `the runout, wear and undercut sheets print no date by default`() {
        assertFalse(sheetPrintsDate(SheetDateSheet.RUNOUT, emptyMap()))
        assertFalse(sheetPrintsDate(SheetDateSheet.WEAR, emptyMap()))
        assertFalse(sheetPrintsDate(SheetDateSheet.UNDERCUT, emptyMap()))
    }

    @Test
    fun `the job's choice wins over the default, one sheet at a time`() {
        val overrides = mapOf("UNDERCUT" to true, "SCHEMATIC" to false)

        assertTrue(sheetPrintsDate(SheetDateSheet.UNDERCUT, overrides))
        assertFalse(sheetPrintsDate(SheetDateSheet.SCHEMATIC, overrides))
        // Untouched sheets keep their defaults.
        assertFalse(sheetPrintsDate(SheetDateSheet.WEAR, overrides))
        assertTrue(sheetPrintsDate(SheetDateSheet.CONSOLIDATED, overrides))
    }

    @Test
    fun `a choice equal to the default changes nothing`() {
        assertTrue(sheetPrintsDate(SheetDateSheet.FINAL, mapOf("FINAL" to true)))
        assertFalse(sheetPrintsDate(SheetDateSheet.RUNOUT, mapOf("RUNOUT" to false)))
    }

    @Test
    fun `a name that is no sheet of this build is ignored`() {
        val overrides = mapOf("POSTER" to true, "undercut" to true, "" to true)

        SheetDateSheet.values().forEach {
            assertEquals(it.name, it.printsDateByDefault, sheetPrintsDate(it, overrides))
        }
    }

    @Test
    fun `forSheet with the switch off prints no date`() {
        val p = ProjectInfo(customer = "Acme", dateMode = SheetDateMode.CUSTOM, dateText = "11/6/2026")

        val off = p.forSheet(SheetDateSheet.UNDERCUT, emptyMap())

        assertNull(off.printedDateOrNull(today))
        assertEquals("everything but the date is untouched", p.copy(dateMode = SheetDateMode.NONE), off)
        assertEquals("the typed date is kept", "11/6/2026", off.dateText)
    }

    @Test
    fun `forSheet with the switch on hands the project through unchanged`() {
        val p = ProjectInfo(customer = "Acme", dateMode = SheetDateMode.CUSTOM, dateText = "11/6/2026")

        assertSame(p, p.forSheet(SheetDateSheet.SCHEMATIC, emptyMap()))
        val on = p.forSheet(SheetDateSheet.UNDERCUT, mapOf("UNDERCUT" to true))
        assertSame(p, on)
        assertEquals("11/6/2026", on.printedDateOrNull(today))
        assertEquals(today, ProjectInfo().forSheet(SheetDateSheet.FINAL, emptyMap()).printedDateOrNull(today))
    }

    @Test
    fun `a job whose date is None prints none on any sheet, switch on or off`() {
        val p = ProjectInfo(dateMode = SheetDateMode.NONE, dateText = "11/6/2026")
        val allOn = SheetDateSheet.values().associate { it.name to true }

        SheetDateSheet.values().forEach {
            assertNull(it.name, p.forSheet(it, allOn).printedDateOrNull(today))
            assertNull(it.name, p.forSheet(it, emptyMap()).printedDateOrNull(today))
        }
    }
}
