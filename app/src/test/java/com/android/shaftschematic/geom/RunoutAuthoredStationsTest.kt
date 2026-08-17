package com.android.shaftschematic.geom

import com.android.shaftschematic.model.RunoutStationPlacements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `collectRunoutStations` with dragged positions overlaid.
 *
 * The overlay is what makes an authored position sacred: whatever the derivation would have
 * produced, a station the user moved stays where they put it — on the linear canvas map and on
 * the compressed PDF map alike, because only mm is stored.
 */
class RunoutAuthoredStationsTest {

    /** Identity x mapping — station mm and x are interchangeable here. */
    private val xAt: (Float) -> Float = { it }

    private fun liner(start: Float, len: Float) =
        RunoutComponentSpan("l1", RunoutComponentKind.LINER, start, len)

    private fun body(id: String, start: Float, len: Float) =
        RunoutComponentSpan(id, RunoutComponentKind.BODY, start, len)

    @Test
    fun `no placements derives exactly as before`() {
        val spans = listOf(liner(1000f, 500f))
        val derived = collectRunoutStations(spans, emptyMap(), xAt)
        val withEmpty = collectRunoutStations(
            spans, emptyMap(), xAt, placements = RunoutStationPlacements(),
        )
        assertEquals(derived, withEmpty)
    }

    @Test
    fun `an authored position replaces the derived one`() {
        val spans = listOf(liner(1000f, 500f))
        val derived = collectRunoutStations(spans, emptyMap(), xAt)
        val placements = RunoutStationPlacements()
            .withComponent("l1", listOf(derived[0].stationMm - 1000f, 300f))

        val stations = collectRunoutStations(spans, emptyMap(), xAt, placements = placements)

        assertEquals(2, stations.size)
        // Station 1 was dragged to local 300mm → shaft 1300mm.
        assertEquals(1300f, stations[1].stationMm, 1e-3f)
        assertEquals(1300f, stations[1].stationX, 1e-3f)
        assertNotEquals(derived[1].stationMm, stations[1].stationMm)
        // Station 0 was frozen at its derived spot and did not move.
        assertEquals(derived[0].stationMm, stations[0].stationMm, 1e-3f)
    }

    @Test
    fun `authored positions do not change the station count`() {
        // The count still comes from the override; placements only say where stations sit.
        val spans = listOf(liner(0f, 1000f))
        val placements = RunoutStationPlacements().withComponent("l1", listOf(10f, 20f, 30f))
        val stations = collectRunoutStations(
            spans, mapOf("l1" to 2), xAt, placements = placements,
        )
        assertEquals(2, stations.size)
        assertEquals(listOf(10f, 20f), stations.map { it.stationMm })
    }

    @Test
    fun `a placement beyond the current count is ignored, not drawn`() {
        // The user dropped the count after dragging: the extra placement is an orphan and must
        // simply go unread — never pruned, so raising the count restores it.
        val spans = listOf(liner(0f, 1000f))
        val placements = RunoutStationPlacements().withComponent("l1", listOf(100f, 200f, 900f))

        val two = collectRunoutStations(spans, mapOf("l1" to 2), xAt, placements = placements)
        assertEquals(listOf(100f, 200f), two.map { it.stationMm })

        val three = collectRunoutStations(spans, mapOf("l1" to 3), xAt, placements = placements)
        assertEquals(listOf(100f, 200f, 900f), three.map { it.stationMm })
    }

    @Test
    fun `station indices stay AFT to FWD under authored positions`() {
        val spans = listOf(liner(0f, 1000f))
        val placements = RunoutStationPlacements().withComponent("l1", listOf(100f, 400f, 800f))
        val stations = collectRunoutStations(
            spans, mapOf("l1" to 3), xAt, placements = placements,
        )
        assertEquals(listOf(0, 1, 2), stations.map { it.stationIndex })
    }

    @Test
    fun `authored positions span a fragmented body's runs`() {
        // One body cut by a liner: local mm is measured across the gap, so a station authored
        // at 900mm lands on the fwd run.
        val spans = listOf(body("b1", 0f, 500f), body("b1", 700f, 500f))
        val placements = RunoutStationPlacements().withComponent("b1", listOf(100f, 900f))

        val stations = collectRunoutStations(
            spans, mapOf("b1" to 2), xAt, mmAtX = { it }, placements = placements,
        )

        assertEquals(2, stations.size)
        assertEquals(100f, stations[0].stationMm, 1e-3f)
        assertEquals(900f, stations[1].stationMm, 1e-3f)
    }

    @Test
    fun `an authored body station stranded in a gap is drawn on metal`() {
        val spans = listOf(body("b1", 0f, 500f), body("b1", 700f, 500f))
        // Local 680mm falls inside the 500..700 liner gap, nearer the fwd run.
        val placements = RunoutStationPlacements().withComponent("b1", listOf(100f, 680f))

        val stations = collectRunoutStations(
            spans, mapOf("b1" to 2), xAt, mmAtX = { it }, placements = placements,
        )

        assertEquals(700f, stations[1].stationMm, 1e-3f)
        // Storage is untouched — only the drawn position moved.
        assertEquals(680f, placements.orderedFor("b1")[1], 1e-3f)
    }

    @Test
    fun `one component's placements never touch another's`() {
        val spans = listOf(liner(0f, 500f), body("b1", 600f, 500f))
        val derived = collectRunoutStations(spans, emptyMap(), xAt)
        val placements = RunoutStationPlacements().withComponent("l1", listOf(10f, 20f))

        val stations = collectRunoutStations(spans, emptyMap(), xAt, placements = placements)

        val bodyDerived = derived.filter { it.componentId == "b1" }.map { it.stationMm }
        val bodyNow = stations.filter { it.componentId == "b1" }.map { it.stationMm }
        assertEquals(bodyDerived, bodyNow)
    }

    @Test
    fun `a single pin leaves the sibling stations derived`() {
        // The ordinary post-drag state: one station pinned, the rest still automatic. The
        // sibling keeps its derived spot exactly — pinning one bubble moves nothing else.
        val spans = listOf(liner(1000f, 500f))
        val derived = collectRunoutStations(spans, emptyMap(), xAt)
        val placements = RunoutStationPlacements().withPosition("l1", 1, 300f)

        val stations = collectRunoutStations(spans, emptyMap(), xAt, placements = placements)

        assertEquals(derived[0].stationMm, stations[0].stationMm, 1e-3f)
        assertEquals(1300f, stations[1].stationMm, 1e-3f)
    }

    @Test
    fun `order repair keeps a derived body sibling from printing aft of a pin`() {
        // Compressed output map: forward of 1000mm foreshortens 20:1. The body's derived
        // stations place drawn-even (all landing in the aft region), while the pin holds
        // physical 1400mm — drawn FORWARD of the derived station with the higher index. The
        // derived one yields (clamped to the pin's position); the pin never moves.
        val xCompressed: (Float) -> Float = { mm -> if (mm <= 1000f) mm else 1000f + (mm - 1000f) * 0.05f }
        val mmAt: (Float) -> Float = { x -> if (x <= 1000f) x else 1000f + (x - 1000f) / 0.05f }
        val spans = listOf(body("b1", 0f, 2000f))
        val placements = RunoutStationPlacements().withPosition("b1", 1, 1400f)

        val stations = collectRunoutStations(
            spans, mapOf("b1" to 3), xCompressed, mmAt, placements,
        )

        assertEquals(1400f, stations[1].stationMm, 1e-3f) // the pin is sacred
        for (i in 1 until stations.size) {
            assertTrue(
                "sheet order broke at index $i",
                stations[i].stationX >= stations[i - 1].stationX,
            )
            assertTrue(
                "mm order broke at index $i",
                stations[i].stationMm >= stations[i - 1].stationMm - 1e-3f,
            )
        }
    }

    @Test
    fun `order repair never touches a fully pinned component`() {
        val spans = listOf(liner(0f, 1000f))
        val placements = RunoutStationPlacements().withComponent("l1", listOf(100f, 400f, 800f))
        val stations = collectRunoutStations(
            spans, mapOf("l1" to 3), xAt, placements = placements,
        )
        assertEquals(listOf(100f, 400f, 800f), stations.map { it.stationMm })
    }

    @Test
    fun `an authored position lands identically under a compressed x map`() {
        // The canvas maps mm linearly, the sheet compresses. Storing mm is what makes the two
        // agree about WHERE the station is; only its drawn x differs.
        val spans = listOf(liner(1000f, 500f))
        val placements = RunoutStationPlacements().withComponent("l1", listOf(50f, 450f))
        val compressed: (Float) -> Float = { mm -> mm * 0.25f + 40f }

        val linear = collectRunoutStations(spans, emptyMap(), xAt, placements = placements)
        val squeezed = collectRunoutStations(spans, emptyMap(), compressed, placements = placements)

        assertEquals(linear.map { it.stationMm }, squeezed.map { it.stationMm })
        assertEquals(compressed(1050f), squeezed[0].stationX, 1e-3f)
    }
}
