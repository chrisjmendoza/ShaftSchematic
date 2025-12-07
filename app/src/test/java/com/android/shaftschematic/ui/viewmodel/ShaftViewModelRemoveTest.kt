package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for component removal logic (remove button race condition fix).
 *
 * These tests verify the data model consistency when components are removed.
 * The critical fix: orderRemove must happen AFTER spec update, not during it.
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
    fun `removing component from order works correctly`() {
        val order = listOf(
            ComponentKey("b1", ComponentKind.BODY),
            ComponentKey("t1", ComponentKind.TAPER),
            ComponentKey("b2", ComponentKind.BODY)
        )

        val idToRemove = "t1"
        val updatedOrder = order.filterNot { it.id == idToRemove }

        assertEquals(2, updatedOrder.size)
        assertEquals("b1", updatedOrder[0].id)
        assertEquals("b2", updatedOrder[1].id)
    }

    @Test
    fun `removing from spec and order maintains consistency`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val taper1 = Taper(id = "t1", startFromAftMm = 100f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 30f)
        val body2 = Body(id = "b2", startFromAftMm = 150f, lengthMm = 100f, diaMm = 30f)

        val spec = ShaftSpec(
            bodies = listOf(body1, body2),
            tapers = listOf(taper1)
        )

        val order = listOf(
            ComponentKey("b1", ComponentKind.BODY),
            ComponentKey("t1", ComponentKind.TAPER),
            ComponentKey("b2", ComponentKind.BODY)
        )

        // Remove taper
        val idToRemove = "t1"

        // Step 1: Remove from spec
        val taperIdx = spec.tapers.indexOfFirst { it.id == idToRemove }
        val updatedSpec = spec.copy(
            tapers = spec.tapers.toMutableList().apply { removeAt(taperIdx) }
        )

        // Step 2: Remove from order (AFTER spec update)
        val updatedOrder = order.filterNot { it.id == idToRemove }

        // Verify consistency
        assertEquals(0, updatedSpec.tapers.size)
        assertEquals(2, updatedSpec.bodies.size)
        assertEquals(2, updatedOrder.size)

        // Verify order only contains IDs that exist in spec
        val specIds = updatedSpec.bodies.map { it.id } +
                      updatedSpec.tapers.map { it.id } +
                      updatedSpec.threads.map { it.id } +
                      updatedSpec.liners.map { it.id }

        updatedOrder.forEach { key ->
            assertTrue("Order should only contain existing spec IDs", key.id in specIds)
        }
    }

    @Test
    fun `removing multiple components in sequence maintains consistency`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val body2 = Body(id = "b2", startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val body3 = Body(id = "b3", startFromAftMm = 200f, lengthMm = 100f, diaMm = 50f)

        var spec = ShaftSpec(bodies = listOf(body1, body2, body3))
        var order = listOf(
            ComponentKey("b1", ComponentKind.BODY),
            ComponentKey("b2", ComponentKind.BODY),
            ComponentKey("b3", ComponentKind.BODY)
        )

        // Remove b2
        val idx1 = spec.bodies.indexOfFirst { it.id == "b2" }
        spec = spec.copy(bodies = spec.bodies.toMutableList().apply { removeAt(idx1) })
        order = order.filterNot { it.id == "b2" }

        assertEquals(2, spec.bodies.size)
        assertEquals(2, order.size)

        // Remove b1
        val idx2 = spec.bodies.indexOfFirst { it.id == "b1" }
        spec = spec.copy(bodies = spec.bodies.toMutableList().apply { removeAt(idx2) })
        order = order.filterNot { it.id == "b1" }

        assertEquals(1, spec.bodies.size)
        assertEquals(1, order.size)
        assertEquals("b3", spec.bodies.first().id)
        assertEquals("b3", order.first().id)
    }

    @Test
    fun `order consistency check detects orphaned IDs`() {
        val spec = ShaftSpec(
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
            )
        )

        val order = listOf(
            ComponentKey("b1", ComponentKind.BODY),
            ComponentKey("b2", ComponentKind.BODY), // Orphaned - doesn't exist in spec
            ComponentKey("t1", ComponentKind.TAPER)  // Orphaned - doesn't exist in spec
        )

        val specIds = spec.bodies.map { it.id } +
                      spec.tapers.map { it.id } +
                      spec.threads.map { it.id } +
                      spec.liners.map { it.id }

        val orphanedIds = order.filterNot { it.id in specIds }

        assertEquals(2, orphanedIds.size)
        assertTrue(orphanedIds.any { it.id == "b2" })
        assertTrue(orphanedIds.any { it.id == "t1" })
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
}
