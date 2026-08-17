package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage rules for authored station positions, and the reading re-key an insertion forces.
 *
 * A component is authored as a unit, its stored indices are always contiguous, and inserting a
 * station in the middle carries the readings above it along — otherwise every typed TIR forward
 * of the insertion would slide one bubble aft.
 */
class RunoutStationPlacementsTest {

    @Test
    fun `a component is derived until it carries a position`() {
        val empty = RunoutStationPlacements()
        assertFalse(empty.isAuthored("l1"))
        assertTrue(empty.orderedFor("l1").isEmpty())
        assertTrue(empty.positionsFor("l1").isEmpty())

        val authored = empty.withComponent("l1", listOf(10f, 20f))
        assertTrue(authored.isAuthored("l1"))
        assertFalse(authored.isAuthored("l2"))
    }

    @Test
    fun `stored indices are renumbered contiguously in list order`() {
        val p = RunoutStationPlacements().withComponent("l1", listOf(300f, 100f, 200f))
        assertEquals(listOf(0, 1, 2), p.placements.map { it.stationIndex })
        // List order is preserved verbatim — the caller owns AFT→FWD ordering.
        assertEquals(listOf(300f, 100f, 200f), p.orderedFor("l1"))
    }

    @Test
    fun `writing a component replaces its whole set`() {
        val p = RunoutStationPlacements()
            .withComponent("l1", listOf(10f, 20f, 30f))
            .withComponent("l1", listOf(50f))
        assertEquals(listOf(50f), p.orderedFor("l1"))
        assertEquals(1, p.placements.size)
    }

    @Test
    fun `an empty write returns the component to derived placement`() {
        val p = RunoutStationPlacements()
            .withComponent("l1", listOf(10f, 20f))
            .withComponent("l1", emptyList())
        assertFalse(p.isAuthored("l1"))
    }

    @Test
    fun `removing one component leaves the others alone`() {
        val p = RunoutStationPlacements()
            .withComponent("l1", listOf(10f))
            .withComponent("b1", listOf(20f, 30f))
            .withoutComponent("l1")
        assertFalse(p.isAuthored("l1"))
        assertEquals(listOf(20f, 30f), p.orderedFor("b1"))
    }

    @Test
    fun `positionsFor keys by station index`() {
        val p = RunoutStationPlacements().withComponent("l1", listOf(10f, 20f))
        assertEquals(mapOf(0 to 10f, 1 to 20f), p.positionsFor("l1"))
    }

    // ── Single-station pins (the drag's storage form) ────────────────────────

    @Test
    fun `a single pin stores and reads back without touching siblings`() {
        val p = RunoutStationPlacements().withPosition("l1", 1, 300f)

        assertTrue(p.isAuthored("l1"))
        assertEquals(300f, p.position("l1", 1))
        assertNull(p.position("l1", 0))
        assertEquals(mapOf(1 to 300f), p.positionsFor("l1"))
    }

    @Test
    fun `re-pinning a station replaces its value only`() {
        val p = RunoutStationPlacements()
            .withPosition("l1", 0, 50f)
            .withPosition("l1", 1, 300f)
            .withPosition("l1", 1, 400f)

        assertEquals(mapOf(0 to 50f, 1 to 400f), p.positionsFor("l1"))
        assertEquals(2, p.placements.size)
    }

    @Test
    fun `un-pinning the last station returns the component to derived`() {
        val p = RunoutStationPlacements()
            .withPosition("l1", 1, 300f)
            .withoutPosition("l1", 1)

        assertFalse(p.isAuthored("l1"))
    }

    @Test
    fun `un-pinning one station keeps the other pins`() {
        val p = RunoutStationPlacements()
            .withPosition("l1", 0, 50f)
            .withPosition("l1", 1, 300f)
            .withPosition("b1", 0, 20f)
            .withoutPosition("l1", 1)

        assertEquals(mapOf(0 to 50f), p.positionsFor("l1"))
        assertEquals(mapOf(0 to 20f), p.positionsFor("b1"))
    }

    // ── Reading re-key on insertion ──────────────────────────────────────────

    @Test
    fun `inserting shifts the readings at and above the insertion point`() {
        val readings = RunoutReadings(
            listOf(
                RunoutReading("l1", 0, valueMm = 0.05f),
                RunoutReading("l1", 1, valueMm = 0.08f),
            )
        )
        // A station is inserted between them: the old station 1 becomes station 2 and keeps
        // its value, and the new station 1 starts empty.
        val after = readings.withStationInserted("l1", atIndex = 1)

        assertEquals(0.05f, after.find("l1", 0)?.valueMm)
        assertNull(after.find("l1", 1))
        assertEquals(0.08f, after.find("l1", 2)?.valueMm)
    }

    @Test
    fun `inserting leaves other components untouched`() {
        val readings = RunoutReadings(
            listOf(
                RunoutReading("l1", 1, valueMm = 0.05f),
                RunoutReading("b1", 1, valueMm = 0.09f),
            )
        )
        val after = readings.withStationInserted("l1", atIndex = 0)
        assertEquals(0.05f, after.find("l1", 2)?.valueMm)
        assertEquals(0.09f, after.find("b1", 1)?.valueMm)
    }

    @Test
    fun `appending at the end shifts nothing`() {
        val readings = RunoutReadings(
            listOf(RunoutReading("l1", 0, valueMm = 0.05f), RunoutReading("l1", 1, valueMm = 0.08f))
        )
        val after = readings.withStationInserted("l1", atIndex = 2)
        assertEquals(0.05f, after.find("l1", 0)?.valueMm)
        assertEquals(0.08f, after.find("l1", 1)?.valueMm)
    }

    @Test
    fun `the high spot marker travels with its value`() {
        val readings = RunoutReadings(
            listOf(RunoutReading("l1", 0, valueMm = 0.05f, highSpotHalfHours = 6))
        )
        val after = readings.withStationInserted("l1", atIndex = 0)
        val moved = after.find("l1", 1)
        assertEquals(0.05f, moved?.valueMm)
        assertEquals(6, moved?.highSpotHalfHours)
    }

    @Test
    fun `the coupling pilot reading is never shifted by a component insertion`() {
        val readings = RunoutReadings(
            listOf(RunoutReading(COUPLING_PILOT_COMPONENT_ID, 0, valueMm = 0.02f))
        )
        val after = readings.withStationInserted("l1", atIndex = 0)
        assertEquals(0.02f, after.find(COUPLING_PILOT_COMPONENT_ID, 0)?.valueMm)
    }
}
