package com.android.shaftschematic.geom

import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding and removing a station on a component whose bubbles have been dragged.
 *
 * Mirrors what `ShaftViewModel.addRunoutStation`/`removeRunoutStation` wire together — plan the
 * insertion, apply it to the positions, re-key the readings — and asserts the contract those
 * three steps exist to hold: **a typed TIR never leaves the physical bubble it was measured
 * at**, and the stations stay in AFT→FWD index order.
 */
class RunoutStationCountEditTest {

    private val inch = 25.4f
    private val span = 40f * inch
    private val id = "l1"

    private fun runsOf(kind: RunoutComponentKind) =
        listOf(RunoutComponentSpan(id, kind, 0f, span))

    /** The add path, exactly as the ViewModel composes it. */
    private fun add(
        placements: RunoutStationPlacements,
        readings: RunoutReadings,
        kind: RunoutComponentKind = RunoutComponentKind.LINER,
        count: Int = placements.positionsFor(id).size,
    ): Pair<RunoutStationPlacements, RunoutReadings> {
        val runs = runsOf(kind)
        val full = currentLocalStationPositions(runs, count, placements.positionsFor(id))
        val insertion = planStationInsertion(
            full, runoutComponentSpanMm(runs), kind != RunoutComponentKind.BODY,
        )
        return placements.withComponent(id, insertStationPosition(full, insertion)) to
            readings.withStationInserted(id, insertion.index)
    }

    /** The remove path, exactly as the ViewModel composes it. */
    private fun remove(
        placements: RunoutStationPlacements,
        readings: RunoutReadings = RunoutReadings(),
        kind: RunoutComponentKind = RunoutComponentKind.LINER,
    ): Pair<RunoutStationPlacements, RunoutReadings> {
        val runs = runsOf(kind)
        val count = placements.positionsFor(id).size
        val full = currentLocalStationPositions(runs, count, placements.positionsFor(id))
        val index = authoredStationIndexToRemove(
            full, runoutComponentSpanMm(runs), kind != RunoutComponentKind.BODY,
        ) { readings.find(id, it) != null }
        if (index < 0) return placements to readings
        return placements.withComponent(id, removeStationPosition(full, index)) to
            readings.withStationRemoved(id, index)
    }

    private fun stationsOf(placements: RunoutStationPlacements, count: Int): List<Float> =
        collectRunoutStations(
            spans = listOf(RunoutComponentSpan(id, RunoutComponentKind.LINER, 0f, span)),
            overrides = mapOf(id to count),
            xAtMm = { it },
            placements = placements,
        ).map { it.stationMm }

    @Test
    fun `adding a third station leaves the dragged pair exactly where they were`() {
        val placements = RunoutStationPlacements().withComponent(id, listOf(200f, 700f))

        val (after, _) = add(placements, RunoutReadings())

        assertEquals(3, after.orderedFor(id).size)
        assertEquals(200f, after.orderedFor(id)[0], 1e-3f)
        assertEquals(450f, after.orderedFor(id)[1], 1e-3f) // midpoint of the widest gap
        assertEquals(700f, after.orderedFor(id)[2], 1e-3f)
    }

    @Test
    fun `an inserted station carries the readings above it along`() {
        val placements = RunoutStationPlacements().withComponent(id, listOf(200f, 700f))
        val readings = RunoutReadings(
            listOf(
                RunoutReading(id, 0, valueMm = 0.05f),
                RunoutReading(id, 1, valueMm = 0.09f, highSpotHalfHours = 6),
            )
        )

        val (newPlacements, newReadings) = add(placements, readings)
        val stations = stationsOf(newPlacements, count = 3)

        // Each value still sits on the station standing at the mm it was measured at.
        assertEquals(200f, stations[0], 1e-3f)
        assertEquals(0.05f, newReadings.find(id, 0)?.valueMm)
        assertEquals(700f, stations[2], 1e-3f)
        assertEquals(0.09f, newReadings.find(id, 2)?.valueMm)
        assertEquals(6, newReadings.find(id, 2)?.highSpotHalfHours)
        // The new station in the middle starts blank.
        assertEquals(450f, stations[1], 1e-3f)
        assertNull(newReadings.find(id, 1))
    }

    @Test
    fun `removing takes the most redundant station when every one is measured`() {
        // Station 1 sits exactly midway between its neighbours — it adds nothing they do not
        // already cover, so it goes even though all three carry values.
        val placements = RunoutStationPlacements().withComponent(id, listOf(200f, 450f, 700f))
        val readings = RunoutReadings(
            listOf(
                RunoutReading(id, 0, valueMm = 0.05f),
                RunoutReading(id, 1, valueMm = 0.07f),
                RunoutReading(id, 2, valueMm = 0.09f),
            )
        )

        val (after, newReadings) = remove(placements, readings)

        assertEquals(listOf(200f, 700f), stationsOf(after, count = 2))
        assertEquals(0.05f, newReadings.find(id, 0)?.valueMm)
        // The fwd reading moved down with its own bubble; the middle value is the one lost.
        assertEquals(0.09f, newReadings.find(id, 1)?.valueMm)
        assertNull(newReadings.find(id, 2))
    }

    @Test
    fun `removing prefers an unmeasured station over a more redundant measured one`() {
        // The middle station is the redundant one, but it has been read; station 2 is blank, so
        // the button gives that one up instead of destroying a measurement.
        val placements = RunoutStationPlacements().withComponent(id, listOf(200f, 450f, 700f))
        val readings = RunoutReadings(
            listOf(
                RunoutReading(id, 0, valueMm = 0.05f),
                RunoutReading(id, 1, valueMm = 0.07f),
            )
        )

        val (after, newReadings) = remove(placements, readings)

        assertEquals(listOf(200f, 450f), stationsOf(after, count = 2))
        assertEquals(0.05f, newReadings.find(id, 0)?.valueMm)
        assertEquals(0.07f, newReadings.find(id, 1)?.valueMm)
    }

    @Test
    fun `add then remove round-trips the dragged positions`() {
        // "−" must undo "+": the inserted station starts blank, so it is the one that goes,
        // leaving the two the user dragged exactly where they were.
        val original = listOf(200f, 700f)
        val placements = RunoutStationPlacements().withComponent(id, original)

        val (added, addedReadings) = add(placements, RunoutReadings())
        val (back, _) = remove(added, addedReadings)

        assertEquals(original, back.orderedFor(id))
    }

    @Test
    fun `add then remove keeps every reading on its own bubble`() {
        val placements = RunoutStationPlacements().withComponent(id, listOf(200f, 700f))
        val readings = RunoutReadings(
            listOf(
                RunoutReading(id, 0, valueMm = 0.05f),
                RunoutReading(id, 1, valueMm = 0.09f, highSpotHalfHours = 6),
            )
        )

        val (added, addedReadings) = add(placements, readings)
        val (back, backReadings) = remove(added, addedReadings)

        assertEquals(listOf(200f, 700f), stationsOf(back, count = 2))
        assertEquals(0.05f, backReadings.find(id, 0)?.valueMm)
        assertEquals(0.09f, backReadings.find(id, 1)?.valueMm)
        assertEquals(6, backReadings.find(id, 1)?.highSpotHalfHours)
    }

    @Test
    fun `repeated adds keep the stations ordered and inside the component`() {
        var placements = RunoutStationPlacements().withComponent(id, listOf(200f, 700f))
        repeat(6) { placements = add(placements, RunoutReadings()).first }

        val positions = placements.orderedFor(id)
        assertEquals(8, positions.size)
        assertEquals("stations fell out of AFT→FWD order", positions.sorted(), positions)
        assertTrue("a station left the component", positions.all { it in 0f..span })
    }

    @Test
    fun `adding to a partially pinned component keeps pin and derived sibling planted`() {
        // Station 1 pinned at 700, station 0 still derived (the ordinary post-drag state).
        // "+" merges the pin over the derived spots, inserts into the widest gap, and freezes
        // the whole set — neither existing bubble moves.
        val placements = RunoutStationPlacements().withPosition(id, 1, 700f)

        val (after, _) = add(placements, RunoutReadings(), count = 2)
        val positions = after.orderedFor(id)

        assertEquals(3, positions.size)
        assertEquals(RunoutConfig.RUNOUT_EDGE_INSET_MM, positions[0], 1e-3f)
        assertEquals((RunoutConfig.RUNOUT_EDGE_INSET_MM + 700f) / 2f, positions[1], 1e-3f)
        assertEquals(700f, positions[2], 1e-3f)
    }

    @Test
    fun `adding to a body uses the full span, not the inset band`() {
        // A body's derived stations are cell midpoints with no edge inset, so an insertion at
        // the empty end may legitimately reach the component's very edge.
        val placements = RunoutStationPlacements().withComponent(id, listOf(10f, 30f))
        val (after, _) = add(placements, RunoutReadings(), kind = RunoutComponentKind.BODY)

        val positions = after.orderedFor(id)
        assertEquals(3, positions.size)
        assertEquals("new station should go to the empty fwd end", 2, positions.indexOf(positions.max()))
        assertEquals((30f + span) / 2f, positions[2], 1e-3f)
    }
}
