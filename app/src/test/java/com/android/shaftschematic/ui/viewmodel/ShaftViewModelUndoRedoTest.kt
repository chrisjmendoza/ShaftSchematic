package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session undo/redo at the ViewModel contract level, exercised through the REAL
 * [SessionHistory] + [EditState] the ViewModel uses.
 *
 * `ShaftViewModel` is an `AndroidViewModel` and is not instantiated in this JVM suite (same
 * convention as `ShaftViewModelUpdateTest` / `ShaftViewModelUnsavedWorkTest`). Instead these
 * tests drive the exact pieces `ShaftViewModel` wires together: the central recorder builds an
 * [EditState] from the flows and calls `editHistory.record(edit, now)`; `undoEdit()`/`redoEdit()`
 * call `editHistory.undo(currentEditState())` / `redo(...)`. Timestamps are supplied explicitly
 * (the ViewModel passes `System.currentTimeMillis()`), separated past the coalescing window so
 * each deliberate edit is its own step.
 */
class ShaftViewModelUndoRedoTest {

    private fun editState(spec: ShaftSpec) = EditState(
        spec = spec,
        wearRecord = WearRecord(),
        runoutReadings = RunoutReadings(),
        runoutStationPlacements = RunoutStationPlacements(),
        stationCountOverrides = emptyMap(),
        undercutRecord = UndercutRecord(),
    )

    @Test
    fun `a station count change is its own undo step`() {
        // The override slice of RunoutConfig is in the snapshot: undoing a "+" must take the
        // count back with the positions, or the extra bubble survives the undo at a derived
        // spot. The rest of the config (sliders) is not in EditState, so a slider commit
        // produces an identical snapshot and records nothing.
        val before = editState(ShaftSpec()).copy(stationCountOverrides = mapOf("l1" to 2))
        val after = before.copy(stationCountOverrides = mapOf("l1" to 3))

        val h = SessionHistory<EditState>()
        h.record(before, 0L)
        h.record(after, 10_000L)

        assertEquals(before, h.undo(after))
    }

    @Test
    fun `field edit undo restores the prior body diameter`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val before = editState(ShaftSpec(bodies = listOf(body)))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)   // recorder seeds the head with the initial state

        // updateBody changes only the diameter (50 → 80).
        val after = editState(ShaftSpec(bodies = listOf(body.copy(diaMm = 80f))))
        h.record(after, 2_000)    // first edit after seed → its own undo step

        val restored = h.undo(after)
        assertEquals("undo restores the pre-edit diameter", 50f, restored!!.spec.bodies[0].diaMm, 0.001f)
    }

    @Test
    fun `redo after undo re-applies the edit`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val before = editState(ShaftSpec(bodies = listOf(body)))
        val after = editState(ShaftSpec(bodies = listOf(body.copy(diaMm = 80f))))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)
        h.record(after, 2_000)

        val undone = h.undo(after)!!
        assertEquals(50f, undone.spec.bodies[0].diaMm, 0.001f)
        assertTrue(h.canRedo)

        val redone = h.redo(undone)!!
        assertEquals("redo re-applies the edited diameter", 80f, redone.spec.bodies[0].diaMm, 0.001f)
        assertFalse(h.canRedo)
    }

    @Test
    fun `importJson clears history so undo cannot cross the document boundary`() {
        // Mirrors ShaftViewModel.importJson() calling clearEditHistory().
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val h = SessionHistory<EditState>()
        h.record(editState(ShaftSpec(bodies = listOf(body))), 1_000)
        h.record(editState(ShaftSpec(bodies = listOf(body.copy(diaMm = 80f)))), 2_000)
        assertTrue(h.canUndo)

        h.clear()   // importJson boundary

        assertFalse("no undo across the imported document", h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.currentOrNull())
    }
}
