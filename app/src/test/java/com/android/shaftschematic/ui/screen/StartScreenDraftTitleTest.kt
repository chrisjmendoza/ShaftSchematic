package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Naming rule for the Start screen's "Unsaved drafts" rows.
 *
 * The row title is the one place a draft identifies itself before it is ever saved, so each
 * branch is pinned here: a saved name wins, project information names the row when there is no
 * saved name, and the literal placeholder is reached only when the draft has nothing at all.
 */
class StartScreenDraftTitleTest {

    private fun entry(
        documentName: String? = null,
        jobNumber: String = "",
        customer: String = "",
        vessel: String = "",
    ) = AutosaveManager.DraftEntry(
        draftId = "d-1",
        documentName = documentName,
        updatedAtEpochMs = 1_700_000_000_000L,
        snapshot = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 4_000f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = customer,
            vessel = vessel,
            jobNumber = jobNumber,
            notes = "",
        ),
    )

    @Test
    fun `a draft opened from a saved file keeps that name, extension stripped`() {
        assertEquals(
            "Job 4471 Shaft",
            draftRowTitle(entry(documentName = "Job 4471 Shaft.shaft", jobNumber = "J-99")),
        )
    }

    @Test
    fun `a job number alone names the row`() {
        assertEquals("J-1138", draftRowTitle(entry(jobNumber = "J-1138")))
    }

    @Test
    fun `customer and vessel name the row when there is no job number`() {
        assertEquals(
            "Bering Marine - Aleutian Spray",
            draftRowTitle(entry(customer = "Bering Marine", vessel = "Aleutian Spray")),
        )
    }

    @Test
    fun `all three project fields join in job-customer-vessel order`() {
        assertEquals(
            "J-1138 - Bering Marine - Aleutian Spray",
            draftRowTitle(
                entry(
                    jobNumber = "J-1138",
                    customer = "Bering Marine",
                    vessel = "Aleutian Spray",
                )
            ),
        )
    }

    @Test
    fun `a draft with nothing to name it falls back to the placeholder`() {
        assertEquals(UNTITLED_DRAFT_TITLE, draftRowTitle(entry()))
        assertEquals("Untitled draft", draftRowTitle(entry()))
    }

    @Test
    fun `blank project fields count as nothing, not as an empty name`() {
        // Whitespace-only entries must not produce a row titled " - " — the suggestion builder
        // drops them, and the placeholder is what is left.
        assertEquals(
            UNTITLED_DRAFT_TITLE,
            draftRowTitle(entry(jobNumber = "  ", customer = "\t", vessel = " ")),
        )
    }

    @Test
    fun `a blank saved name falls through to the project information`() {
        // An empty documentName is not a name; the draft is still named by what it carries.
        assertEquals("J-1138", draftRowTitle(entry(documentName = "   ", jobNumber = "J-1138")))
    }
}
