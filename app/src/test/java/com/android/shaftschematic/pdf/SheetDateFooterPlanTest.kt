package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The footer plan takes its date from the document through the ONE resolver when no caller
 * overrides it — the wiring every schematic / consolidated footer goes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetDateFooterPlanTest {

    private val spec = ShaftSpec(overallLengthMm = 2000f)
    private val cfg = FooterConfig(
        showAftThread = false, showFwdThread = false,
        showAftTaper = false, showFwdTaper = false,
    )

    private fun midLines(project: ProjectInfo, blank: Boolean = false): List<String> =
        planFooter(
            rectLeft = 0f, rectRight = 700f, spec = spec, unit = UnitSystem.INCHES,
            project = project, text = Paint().apply { textSize = 9f }, cfg = cfg, blankValues = blank,
        ).midLines

    @Test
    fun `Today prints the composing day`() {
        val lines = midLines(ProjectInfo(customer = "A"))
        assertTrue(lines.contains("Date: ${sheetTodayDate()}"))
    }

    @Test
    fun `Custom prints the typed date`() {
        val lines = midLines(ProjectInfo(dateMode = SheetDateMode.CUSTOM, dateText = "11/6/2026"))
        assertTrue(lines.contains("Date: 11/6/2026"))
        assertFalse(lines.contains("Date: ${sheetTodayDate()}"))
    }

    @Test
    fun `None leaves the date line off`() {
        val lines = midLines(ProjectInfo(dateMode = SheetDateMode.NONE))
        assertFalse(lines.any { it.startsWith("Date") })
    }

    @Test
    fun `a blank draft rules Date even under None`() {
        val lines = midLines(ProjectInfo(dateMode = SheetDateMode.NONE), blank = true)
        assertEquals(1, lines.count { it == "Date:" })
    }
}
