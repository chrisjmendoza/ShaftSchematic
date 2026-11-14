package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [printedDateOrNull] — the ONE resolution of the date every printed sheet carries — and the
 * tolerant [SheetDateMode.fromName] decode behind the stored mode.
 */
class SheetDateModeTest {

    private val today = "2026-11-13"

    @Test
    fun `Today prints today's date`() {
        assertEquals(today, ProjectInfo(dateMode = SheetDateMode.TODAY).printedDateOrNull(today))
    }

    @Test
    fun `Today ignores a typed date left from Custom`() {
        val p = ProjectInfo(dateMode = SheetDateMode.TODAY, dateText = "11/6/2026")
        assertEquals(today, p.printedDateOrNull(today))
    }

    @Test
    fun `Custom prints the typed text verbatim`() {
        val p = ProjectInfo(dateMode = SheetDateMode.CUSTOM, dateText = "11/6/2026")
        assertEquals("11/6/2026", p.printedDateOrNull(today))
    }

    @Test
    fun `Custom keeps free text exactly as typed`() {
        val p = ProjectInfo(dateMode = SheetDateMode.CUSTOM, dateText = "Nov 6, 2026 (week 45)")
        assertEquals("Nov 6, 2026 (week 45)", p.printedDateOrNull(today))
    }

    @Test
    fun `Custom with a blank text prints today rather than an empty date`() {
        assertEquals(today, ProjectInfo(dateMode = SheetDateMode.CUSTOM, dateText = "").printedDateOrNull(today))
        assertEquals(today, ProjectInfo(dateMode = SheetDateMode.CUSTOM, dateText = "   ").printedDateOrNull(today))
    }

    @Test
    fun `None prints no date`() {
        assertNull(ProjectInfo(dateMode = SheetDateMode.NONE, dateText = "11/6/2026").printedDateOrNull(today))
    }

    @Test
    fun `a default project prints today`() {
        assertEquals(today, ProjectInfo().printedDateOrNull(today))
    }

    @Test
    fun `fromName decodes every mode by name and falls back to Today`() {
        SheetDateMode.values().forEach { assertEquals(it, SheetDateMode.fromName(it.name)) }
        assertEquals(SheetDateMode.TODAY, SheetDateMode.fromName(null))
        assertEquals(SheetDateMode.TODAY, SheetDateMode.fromName(""))
        assertEquals(SheetDateMode.TODAY, SheetDateMode.fromName("YESTERDAY"))
    }
}
