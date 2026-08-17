package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for component removal logic.
 *
 * These tests verify the data model consistency when components are removed. Carousel rows
 * are derived from the spec (resolved components in physical order), so a delete has exactly
 * one piece of state to keep consistent.
 *
 * Recovery from a delete flows through the general session undo/redo. The
 * `delete + undoEdit` tests at the bottom assert that deleting a component then undoing
 * restores the spec, exercised through the real [SessionHistory] + [EditState] the ViewModel
 * records (see ShaftViewModelUndoRedoTest for why the AndroidViewModel itself is not
 * instantiated in this JVM suite).
 */
class ShaftViewModelRemoveTest {

    @Test
    fun `removing component from spec works correctly`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val body2 = Body(id = "b2", startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val spec = ShaftSpec(bodies = listOf(body1, body2))

        // Simulate remove: find index and create new spec without it
        val idToRemove = "b1"
        val idx = spec.bodies.indexOfFirst { it.id == idToRemove }
        assertTrue("Should find body to remove", idx >= 0)

        val updatedSpec = spec.copy(
            bodies = spec.bodies.toMutableList().apply { removeAt(idx) }
        )

        assertEquals(1, updatedSpec.bodies.size)
        assertEquals("b2", updatedSpec.bodies.first().id)
    }

    @Test
    fun `removing a taper leaves the flanking bodies untouched`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val taper1 = Taper(id = "t1", startFromAftMm = 100f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 30f)
        val body2 = Body(id = "b2", startFromAftMm = 150f, lengthMm = 100f, diaMm = 30f)

        val spec = ShaftSpec(
            bodies = listOf(body1, body2),
            tapers = listOf(taper1)
        )

        val idToRemove = "t1"
        val taperIdx = spec.tapers.indexOfFirst { it.id == idToRemove }
        val updatedSpec = spec.copy(
            tapers = spec.tapers.toMutableList().apply { removeAt(taperIdx) }
        )

        assertEquals(0, updatedSpec.tapers.size)
        assertEquals(2, updatedSpec.bodies.size)
        assertEquals(listOf("b1", "b2"), updatedSpec.bodies.map { it.id })
        assertEquals(150f, updatedSpec.bodies[1].startFromAftMm, 0.001f)
    }

    @Test
    fun `removing multiple components in sequence maintains consistency`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val body2 = Body(id = "b2", startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val body3 = Body(id = "b3", startFromAftMm = 200f, lengthMm = 100f, diaMm = 50f)

        var spec = ShaftSpec(bodies = listOf(body1, body2, body3))

        // Remove b2
        val idx1 = spec.bodies.indexOfFirst { it.id == "b2" }
        spec = spec.copy(bodies = spec.bodies.toMutableList().apply { removeAt(idx1) })

        assertEquals(2, spec.bodies.size)

        // Remove b1
        val idx2 = spec.bodies.indexOfFirst { it.id == "b1" }
        spec = spec.copy(bodies = spec.bodies.toMutableList().apply { removeAt(idx2) })

        assertEquals(1, spec.bodies.size)
        assertEquals("b3", spec.bodies.first().id)
    }

    @Test
    fun `removing nonexistent ID from list is no-op`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val spec = ShaftSpec(bodies = listOf(body1))

        val idx = spec.bodies.indexOfFirst { it.id == "fake-id" }
        assertTrue("Should not find fake ID", idx < 0)

        // When idx < 0, the original spec is returned unchanged
        val updatedSpec = if (idx < 0) spec else spec.copy(
            bodies = spec.bodies.toMutableList().apply { removeAt(idx) }
        )

        assertEquals(1, updatedSpec.bodies.size)
        assertEquals("b1", updatedSpec.bodies.first().id)
    }

    // ── Delete-undo recovery (via general session undo) ──────────

    private fun editState(spec: ShaftSpec) = EditState(
        spec = spec,
        wearRecord = WearRecord(),
        runoutReadings = RunoutReadings(),
        runoutStationPlacements = RunoutStationPlacements(),
        stationCountOverrides = emptyMap(),
        undercutRecord = UndercutRecord(),
        overallIsManual = false,
    )

    @Test
    fun `deleting a body then undoEdit restores the body`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val body2 = Body(id = "b2", startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val before = editState(ShaftSpec(bodies = listOf(body1, body2)))

        // removeBody("b1"): the spec drops b1; its carousel row goes with it.
        val after = editState(before.spec.copy(bodies = listOf(body2)))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)      // recorder seeds pre-delete state
        h.record(after, 2_000)       // delete is its own step (gap past window)

        val restored = h.undo(after)!!
        assertEquals("body restored in spec", 2, restored.spec.bodies.size)
        assertTrue("deleted body id back in spec", restored.spec.bodies.any { it.id == "b1" })
        assertEquals("bodies restored in their stored sequence",
            listOf("b1", "b2"), restored.spec.bodies.map { it.id })
    }

    @Test
    fun `deleting a taper then undoEdit restores the taper`() {
        val body  = Body(id = "b1", startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val taper = Taper(id = "t1", startFromAftMm = 100f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 30f)
        val before = editState(ShaftSpec(bodies = listOf(body), tapers = listOf(taper)))

        val after = editState(before.spec.copy(tapers = emptyList()))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)
        h.record(after, 2_000)

        val restored = h.undo(after)!!
        assertEquals("taper restored", 1, restored.spec.tapers.size)
        assertEquals("t1", restored.spec.tapers.first().id)
        assertEquals("taper restored at its stored span", 100f,
            restored.spec.tapers.first().startFromAftMm, 0.001f)
    }
}
