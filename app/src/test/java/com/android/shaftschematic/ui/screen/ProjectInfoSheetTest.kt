package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.android.shaftschematic.model.ShaftPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Save/Cancel contract of the Project Information sheet: the fields are a draft, and
 * nothing reaches the setters until Save. Pins the on-device failure it replaced — text
 * typed into the last field used to be lost because only a blur committed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ProjectInfoSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val commits = mutableListOf<Pair<String, String>>()
    private var dismissed = false

    private fun host(
        customer: String = "",
        vessel: String = "",
        jobNumber: String = "",
        item: String = "",
        notes: String = "",
    ) {
        rule.setContent {
            MaterialTheme {
                ProjectInfoBottomSheet(
                    customer = customer,
                    vessel = vessel,
                    jobNumber = jobNumber,
                    item = item,
                    shaftPosition = ShaftPosition.OTHER,
                    notes = notes,
                    onSetCustomer = { commits += "customer" to it },
                    onSetVessel = { commits += "vessel" to it },
                    onSetJobNumber = { commits += "job" to it },
                    onSetItem = { commits += "item" to it },
                    onSetShaftPosition = { commits += "position" to it.name },
                    onSetNotes = { commits += "notes" to it },
                    onDismiss = { dismissed = true },
                )
            }
        }
    }

    @Test
    fun `item field commits through save`() {
        host()

        rule.onNodeWithTag("project_info_item").performTextReplacement("Tail shaft")
        rule.onNodeWithTag("project_info_save").performClick()

        assertEquals(listOf("item" to "Tail shaft"), commits)
    }

    @Test
    fun `typing without leaving the field still saves`() {
        host()

        rule.onNodeWithTag("project_info_notes").performTextReplacement("wiped hard")
        rule.onNodeWithTag("project_info_save").performClick()

        assertEquals(listOf("notes" to "wiped hard"), commits)
        assertTrue("Save closes the sheet", dismissed)
    }

    @Test
    fun `editing commits nothing before Save`() {
        host()

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Acme")
        rule.waitForIdle()

        assertEquals("no setter fires while editing", emptyList<Pair<String, String>>(), commits)
    }

    @Test
    fun `Cancel reverts an edited field`() {
        host(customer = "Acme")

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Typo Inc")
        rule.onNodeWithTag("project_info_cancel").performClick()

        assertEquals(emptyList<Pair<String, String>>(), commits)
        assertTrue("Cancel closes the sheet", dismissed)
    }

    @Test
    fun `Cancel leaves a blank field blank`() {
        host()

        rule.onNodeWithTag("project_info_vessel").performTextReplacement("Wanderer")
        rule.onNodeWithTag("project_info_cancel").performClick()

        assertEquals(emptyList<Pair<String, String>>(), commits)
    }

    @Test
    fun `Save pushes only the changed fields`() {
        host(customer = "Acme", vessel = "Wanderer", jobNumber = "J-1")

        rule.onNodeWithTag("project_info_vessel").performTextReplacement("Drifter")
        rule.onNodeWithTag("project_info_save").performClick()

        // Untouched fields must not fire — otherwise open-and-save marks the document dirty.
        assertEquals(listOf("vessel" to "Drifter"), commits)
    }

    @Test
    fun `Save with no edit commits nothing`() {
        host(customer = "Acme", jobNumber = "J-1")

        rule.onNodeWithTag("project_info_save").performClick()

        assertEquals(emptyList<Pair<String, String>>(), commits)
        assertTrue(dismissed)
    }

    /* ── Swiping the sheet away with a pending edit ── */

    private fun swipeSheetAway() {
        rule.onNodeWithTag("project_info_sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()
    }

    @Test
    fun `swiping away a dirty draft asks first`() {
        host(customer = "Acme")

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Acme Marine")
        swipeSheetAway()

        rule.onNodeWithTag("project_info_keep_editing").assertExists()
        assertFalse("the sheet must not close behind the prompt", dismissed)
        assertEquals(emptyList<Pair<String, String>>(), commits)
    }

    @Test
    fun `swiping away a clean draft closes silently`() {
        host(customer = "Acme")

        swipeSheetAway()

        rule.onNodeWithTag("project_info_keep_editing").assertDoesNotExist()
        assertTrue(dismissed)
        assertEquals(emptyList<Pair<String, String>>(), commits)
    }

    @Test
    fun `Save from the prompt commits the draft`() {
        host(customer = "Acme")

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Acme Marine")
        swipeSheetAway()
        rule.onNodeWithTag("project_info_discard_save").performClick()

        assertEquals(listOf("customer" to "Acme Marine"), commits)
        assertTrue(dismissed)
    }

    @Test
    fun `Discard from the prompt closes without committing`() {
        host(customer = "Acme")

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Typo Inc")
        swipeSheetAway()
        rule.onNodeWithTag("project_info_discard_confirm").performClick()

        assertEquals(emptyList<Pair<String, String>>(), commits)
        assertTrue(dismissed)
    }

    @Test
    fun `Keep editing returns to the sheet with the draft intact`() {
        host(customer = "Acme")

        rule.onNodeWithTag("project_info_customer").performTextReplacement("Acme Marine")
        swipeSheetAway()
        rule.onNodeWithTag("project_info_keep_editing").performClick()

        assertFalse(dismissed)
        assertEquals(emptyList<Pair<String, String>>(), commits)
        rule.onNodeWithTag("project_info_customer").assertTextContains("Acme Marine")

        // …and the draft is still committable afterwards.
        rule.onNodeWithTag("project_info_save").performClick()
        assertEquals(listOf("customer" to "Acme Marine"), commits)
    }
}
