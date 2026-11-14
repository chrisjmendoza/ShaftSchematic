package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.model.SheetDateSheet
import com.android.shaftschematic.model.sheetPrintsDate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared "Date on this sheet" row: its switch shows the sheet's resolved choice (the shipped
 * default until the job says otherwise), a tap writes that ONE sheet's choice, the caption says
 * what prints and that the choice is the job's, and while the job's date is None the switch
 * greys out with its value kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class SheetDateOnSheetRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun host(vm: ShaftViewModel, sheet: SheetDateSheet) {
        rule.setContent { MaterialTheme { SheetDateOnSheetRow(vm = vm, sheet = sheet) } }
    }

    @Test
    fun `the caption says what prints and ends Saved with this job`() {
        assertEquals(
            "Prints today's date. Saved with this job.",
            sheetDateOnSheetCaption(SheetDateMode.TODAY, ""),
        )
        assertEquals(
            "Prints 11/6/2026. Saved with this job.",
            sheetDateOnSheetCaption(SheetDateMode.CUSTOM, "11/6/2026"),
        )
        // A Custom date not typed yet prints today's, as the sheet does.
        assertEquals(
            "Prints today's date. Saved with this job.",
            sheetDateOnSheetCaption(SheetDateMode.CUSTOM, "  "),
        )
        assertEquals(
            "The job's date is set to None on the Shaft & job card.",
            sheetDateOnSheetCaption(SheetDateMode.NONE, "11/6/2026"),
        )
    }

    @Test
    fun `every sheet has its own tag`() {
        assertEquals(
            listOf(
                "sheet_date_on_schematic", "sheet_date_on_consolidated", "sheet_date_on_runout",
                "sheet_date_on_wear", "sheet_date_on_undercut", "sheet_date_on_final",
            ),
            SheetDateSheet.values().map(::sheetDateOnSheetTag),
        )
    }

    @Test
    fun `a working sheet starts off and a tap turns that one sheet on`() {
        val vm = vm()
        host(vm, SheetDateSheet.UNDERCUT)

        rule.onNodeWithText("Date on this sheet").assertExists()
        rule.onNodeWithText("Prints today's date. Saved with this job.").assertExists()
        rule.onNodeWithTag("sheet_date_on_undercut").assertIsOff().assertIsEnabled()

        rule.onNodeWithTag("sheet_date_on_undercut").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("sheet_date_on_undercut").assertIsOn()
        assertEquals(mapOf("UNDERCUT" to true), vm.sheetDateSheets.value)
        assertFalse("the wear sheet is untouched", sheetPrintsDate(SheetDateSheet.WEAR, vm.sheetDateSheets.value))
    }

    @Test
    fun `the schematic starts on and a tap turns it off`() {
        val vm = vm()
        host(vm, SheetDateSheet.SCHEMATIC)

        rule.onNodeWithTag("sheet_date_on_schematic").assertIsOn().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("sheet_date_on_schematic").assertIsOff()
        assertFalse(sheetPrintsDate(SheetDateSheet.SCHEMATIC, vm.sheetDateSheets.value))
        assertTrue(sheetPrintsDate(SheetDateSheet.FINAL, vm.sheetDateSheets.value))
    }

    @Test
    fun `a job date of None greys the switch and keeps its value`() {
        val vm = vm()
        vm.setSheetDateOnSheet(SheetDateSheet.WEAR, true)
        vm.setSheetDateMode(SheetDateMode.NONE)
        host(vm, SheetDateSheet.WEAR)

        rule.onNodeWithTag("sheet_date_on_wear").assertIsNotEnabled().assertIsOn()
        rule.onNodeWithText("The job's date is set to None on the Shaft & job card.").assertExists()
        assertEquals(mapOf("WEAR" to true), vm.sheetDateSheets.value)
    }
}
