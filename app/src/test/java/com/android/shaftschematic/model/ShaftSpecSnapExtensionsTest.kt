package com.android.shaftschematic.model

import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [snapForwardFrom] and [buildPhysicalKeyOrder].
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
    fun excludedThread_isTransparent_to_buildPhysicalKeyOrder() {
        val body = Body(id = "B", startFromAftMm = 0f, lengthMm = 300f, diaMm = 50f)
        val excluded = Threads(
            id = "T",
            startFromAftMm = 0f,
            lengthMm = 50f,
            majorDiaMm = 45f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(body),
            threads = listOf(excluded)
        )
        val ordered = spec.buildPhysicalKeyOrder()
        assertEquals(1, ordered.size)
        assertEquals(ComponentKey("B", ComponentKind.BODY), ordered[0])
    }

    @Test
    fun excludedThread_doesNotCascadeIntoNeighbors_on_resize() {
        val excluded = Threads(
            id = "T",
            startFromAftMm = 0f,
            lengthMm = 50f,
            majorDiaMm = 45f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val body = Body(id = "B", startFromAftMm = 0f, lengthMm = 300f, diaMm = 50f)
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(body),
            threads = listOf(excluded)
        )
        // snapForwardFrom an excluded thread key should be a no-op (not in physical order)
        val after = spec.snapForwardFrom(ComponentKey("T", ComponentKind.THREAD))
        assertEquals(0f, after.bodies[0].startFromAftMm, 0.001f)
        assertEquals(300f, after.bodies[0].lengthMm, 0.001f)
    }

    @Test
    fun includedThread_remainsInPhysicalKeyOrder() {
        val body = Body(id = "B", startFromAftMm = 0f, lengthMm = 300f, diaMm = 50f)
        val included = Threads(
            id = "T",
            startFromAftMm = 300f,
            lengthMm = 50f,
            majorDiaMm = 45f,
            pitchMm = 2f,
            excludeFromOAL = false
        )
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(body),
            threads = listOf(included)
        )
        val ordered = spec.buildPhysicalKeyOrder()
        assertEquals(2, ordered.size)
        assertEquals(ComponentKey("B", ComponentKind.BODY), ordered[0])
        assertEquals(ComponentKey("T", ComponentKind.THREAD), ordered[1])
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

}

