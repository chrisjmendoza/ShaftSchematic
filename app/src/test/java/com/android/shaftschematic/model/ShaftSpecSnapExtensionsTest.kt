package com.android.shaftschematic.model

import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [snapForwardFrom], [snapForwardFromOrdered], and [buildPhysicalKeyOrder].
 *
 * These are JVM-only tests with no Android dependencies.
 */
class ShaftSpecSnapExtensionsTest {

    @Test
    fun snapForwardFrom_alignsRightNeighbors_endToStart() {
        val bodyA = Body(
            id = "A",
            startFromAftMm = 0f,
            lengthMm = 100f,
            diaMm = 50f
        )
        val bodyB = Body(
            id = "B",
            startFromAftMm = 150f,
            lengthMm = 50f,
            diaMm = 50f
        )
        val bodyC = Body(
            id = "C",
            startFromAftMm = 250f,
            lengthMm = 50f,
            diaMm = 50f
        )

        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(bodyA, bodyB, bodyC),
            tapers = emptyList(),
            threads = emptyList(),
            liners = emptyList()
        )

        val snapped = spec.snapForwardFrom(
            ComponentKey(id = "A", kind = ComponentKind.BODY)
        )

        val snappedBodies = snapped.bodies
        val snappedA = snappedBodies[0]
        val snappedB = snappedBodies[1]
        val snappedC = snappedBodies[2]

        assertEquals(0f, snappedA.startFromAftMm, 0.0001f)
        assertEquals(100f, snappedA.lengthMm, 0.0001f)

        assertEquals(100f, snappedB.startFromAftMm, 0.0001f)
        assertEquals(50f, snappedB.lengthMm, 0.0001f)

        assertEquals(150f, snappedC.startFromAftMm, 0.0001f)
        assertEquals(50f, snappedC.lengthMm, 0.0001f)
    }

    @Test
    fun deleting_middle_component_snaps_followers_to_left_neighbor() {
        // Body A: 0–100
        // Body B: 140–180 (gap 40mm)
        // Body C: 220–250 (another gap)

        val bodyA = Body(
            id = "A",
            startFromAftMm = 0f,
            lengthMm = 100f,
            diaMm = 40f
        )
        val bodyB = Body(
            id = "B",
            startFromAftMm = 140f,
            lengthMm = 40f,
            diaMm = 40f
        )
        val bodyC = Body(
            id = "C",
            startFromAftMm = 220f,
            lengthMm = 30f,
            diaMm = 40f
        )

        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(bodyA, bodyB, bodyC),
            tapers = emptyList(),
            threads = emptyList(),
            liners = emptyList()
        )

        val removedKey = ComponentKey("B", ComponentKind.BODY)
        val leftNeighbor = spec.findLeftNeighbor(removedKey)
        assertEquals(ComponentKey("A", ComponentKind.BODY), leftNeighbor)

        val specWithoutB = spec.copy(bodies = listOf(bodyA, bodyC))

        val snapped = specWithoutB.snapForwardFrom(leftNeighbor!!)
        val snappedBodies = snapped.bodies

        assertEquals(2, snappedBodies.size)

        val a = snappedBodies[0]
        val c = snappedBodies[1]

        // A unchanged
        assertEquals(0f, a.startFromAftMm, 0.0001f)
        assertEquals(100f, a.lengthMm, 0.0001f)

        // C now starts exactly at A.end == 100
        assertEquals(100f, c.startFromAftMm, 0.0001f)
        assertEquals(30f, c.lengthMm, 0.0001f)
    }

    @Test
    fun buildPhysicalKeyOrder_ordersByStartThenKind() {
        val body = Body(
            id = "B",
            startFromAftMm = 0f,
            lengthMm = 50f,
            diaMm = 40f
        )
        val taper = Taper(
            id = "T",
            startFromAftMm = 25f,
            lengthMm = 25f,
            startDiaMm = 40f,
            endDiaMm = 45f
        )
        val thread = Threads(
            id = "Th",
            startFromAftMm = 25f,
            lengthMm = 10f,
            majorDiaMm = 38f,
            pitchMm = 2f
        )

        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(body),
            tapers = listOf(taper),
            threads = listOf(thread),
            liners = emptyList()
        )

        val ordered = spec.buildPhysicalKeyOrder()

        assertEquals(3, ordered.size)
        assertEquals(ComponentKey("B", ComponentKind.BODY), ordered[0])
        assertEquals(ComponentKey("T", ComponentKind.TAPER), ordered[1])
        assertEquals(ComponentKey("Th", ComponentKind.THREAD), ordered[2])
    }

    @Test
    fun deletingComponent_usesLeftNeighborToSnapFollowers() {
        val bodyA = Body(id = "A", startFromAftMm = 0f, lengthMm = 100f, diaMm = 40f)
        val bodyB = Body(id = "B", startFromAftMm = 140f, lengthMm = 40f, diaMm = 40f)
        val bodyC = Body(id = "C", startFromAftMm = 220f, lengthMm = 30f, diaMm = 40f)

        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(bodyA, bodyB, bodyC)
        )
        val leftNeighbor = spec.findLeftNeighbor(ComponentKey("B", ComponentKind.BODY))
        requireNotNull(leftNeighbor)

        val withoutB = spec.copy(bodies = listOf(bodyA, bodyC))
        val snapped = withoutB.snapForwardFrom(leftNeighbor)

        val snappedA = snapped.bodies[0]
        val snappedC = snapped.bodies[1]

        assertEquals(0f, snappedA.startFromAftMm, 0.0001f)
        assertEquals(100f, snappedA.lengthMm, 0.0001f)

        // Component C is now flush with A's end (100 mm)
        assertEquals(100f, snappedC.startFromAftMm, 0.0001f)
        assertEquals(30f, snappedC.lengthMm, 0.0001f)
    }

    @Test
    fun deletingFirstComponent_snapsToOriginAndCascades() {
        val bodyB = Body(id = "B", startFromAftMm = 140f, lengthMm = 40f, diaMm = 40f)
        val bodyC = Body(id = "C", startFromAftMm = 230f, lengthMm = 30f, diaMm = 40f)

        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(bodyB, bodyC)
        )

        val snapped = spec.snapFromOrigin()

        val snappedB = snapped.bodies[0]
        val snappedC = snapped.bodies[1]

        // First component zeroed
        assertEquals(0f, snappedB.startFromAftMm, 0.0001f)
        assertEquals(40f, snappedB.lengthMm, 0.0001f)

        // Following component now flush with B's end (40)
        assertEquals(40f, snappedC.startFromAftMm, 0.0001f)
        assertEquals(30f, snappedC.lengthMm, 0.0001f)
    }

    @Test
    fun undoing_delete_resnaps_followers_to_restored_component() {
        val bodyA = Body(id = "A", startFromAftMm = 0f, lengthMm = 100f, diaMm = 40f)
        val bodyB = Body(id = "B", startFromAftMm = 140f, lengthMm = 40f, diaMm = 40f)
        val bodyC = Body(id = "C", startFromAftMm = 220f, lengthMm = 30f, diaMm = 40f)

        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(bodyA, bodyB, bodyC)
        )
        val order = listOf(
            ComponentKey("A", ComponentKind.BODY),
            ComponentKey("B", ComponentKind.BODY),
            ComponentKey("C", ComponentKind.BODY)
        )

        val leftNeighbor = ComponentKey("A", ComponentKind.BODY)
        val removedKey = ComponentKey("B", ComponentKind.BODY)

        // Simulate delete: remove B and snap followers toward A
        val afterDelete = spec.copy(bodies = listOf(bodyA, bodyC))
            .snapForwardFrom(leftNeighbor)
        val snappedC = afterDelete.bodies[1]
        assertEquals(100f, snappedC.startFromAftMm, 0.0001f)

        // Simulate undo: restore B at its original geometry
        val restored = afterDelete.copy(
            bodies = listOf(afterDelete.bodies[0], bodyB, snappedC)
        )

        // Snap forward from the restored component so followers shift back to the right
        val afterUndo = restored.snapForwardFromOrdered(removedKey, order)
        val resA = afterUndo.bodies[0]
        val resB = afterUndo.bodies[1]
        val resC = afterUndo.bodies[2]

        assertEquals(0f, resA.startFromAftMm, 0.0001f)
        assertEquals(140f, resB.startFromAftMm, 0.0001f)
        assertEquals(180f, resC.startFromAftMm, 0.0001f)
    }
}

