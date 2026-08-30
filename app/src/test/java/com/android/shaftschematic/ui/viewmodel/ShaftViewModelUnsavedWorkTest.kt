package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.data.shouldWriteDraft
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ShaftViewModel.hasUnsavedWork()` must be the SAME source of truth as the autosave draft
 * dirty gate: it compares the full [AutosaveManager.SessionSnapshot] via [shouldWriteDraft]
 * (with the `isSessionDefault()` short-circuit), so reference-only edits — wear records and
 * runout readings — count as unsaved work. Comparing only spec + metadata would leave those
 * edits unguarded.
 *
 * This class mirrors `hasUnsavedWork()`'s exact body so it can drive the real [shouldWriteDraft]
 * predicate over real snapshots — the predicate and the snapshot comparison are what it is
 * about, not the ViewModel plumbing around them.
 */
class ShaftViewModelUnsavedWorkTest {

    // Exact mirror of ShaftViewModel.hasUnsavedWork().
    private fun hasUnsavedWork(
        current: AutosaveManager.SessionSnapshot,
        saved: AutosaveManager.SessionSnapshot?,
        isSessionDefault: Boolean,
    ): Boolean {
        if (isSessionDefault) return false
        return shouldWriteDraft(current, saved)
    }

    // A non-default baseline (has geometry) so the isSessionDefault short-circuit is not in play.
    private fun baseline(): AutosaveManager.SessionSnapshot =
        AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 300f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = "Acme",
            vessel = "Tug",
            jobNumber = "J-1",
            notes = "",
        )

    @Test
    fun `wear-record-only change is unsaved work`() {
        val saved = baseline()
        val current = saved.copy(
            wearRecord = WearRecord(
                spots = listOf(WearSpot(id = "s1", linerId = "ln1", startMm = 5f, lengthMm = 20f))
            )
        )
        assertTrue(hasUnsavedWork(current, saved, isSessionDefault = false))
    }

    @Test
    fun `runout-reading-only change is unsaved work`() {
        val saved = baseline()
        val current = saved.copy(
            runoutReadings = RunoutReadings().withReading(
                RunoutReading(componentId = "c1", stationIndex = 0, valueMm = 0.4f, highSpotHalfHours = 6)
            )
        )
        assertTrue(hasUnsavedWork(current, saved, isSessionDefault = false))
    }

    @Test
    fun `session clean after markDocumentSaved reports no unsaved work`() {
        // markDocumentSaved() captures savedSnapshot = buildCurrentSnapshot(), so saved == current.
        val current = baseline().copy(
            wearRecord = WearRecord(
                spots = listOf(WearSpot(id = "s1", linerId = "ln1", startMm = 5f, lengthMm = 20f))
            )
        )
        val saved = current
        assertFalse(hasUnsavedWork(current, saved, isSessionDefault = false))
    }

    @Test
    fun `blank default session reports no unsaved work`() {
        val blank = baseline().copy(shaftSpec = ShaftSpec(), customer = "", vessel = "", jobNumber = "")
        // Even with a null baseline, the isSessionDefault short-circuit wins.
        assertFalse(hasUnsavedWork(blank, saved = null, isSessionDefault = true))
    }
}
