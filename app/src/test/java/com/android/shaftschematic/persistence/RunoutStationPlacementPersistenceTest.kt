package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.RunoutStationPlacement
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence for dragged runout station positions: envelope round-trip, older-file defaults,
 * and the no-pruning rule.
 *
 * A placement is keyed like a runout reading, so it follows the same orphan policy — station
 * identity depends on resolved components and count overrides that the codec cannot see, which
 * means nothing may be dropped here.
 */
class RunoutStationPlacementPersistenceTest {

    private fun spec(): ShaftSpec = ShaftSpec(overallLengthMm = 2000f)

    @Test
    fun `envelope round trip preserves placements verbatim`() {
        val placements = RunoutStationPlacements(
            listOf(
                RunoutStationPlacement("liner-1", 0, 25.4f),
                RunoutStationPlacement("liner-1", 1, 431.877f),
            )
        )
        val doc = ShaftDocCodec.ShaftDocV1(spec = spec(), runoutStationPlacements = placements)

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected runout_stations key in JSON", raw.contains("\"runout_stations\""))

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(2, decoded.runoutStationPlacements.placements.size)
        val second = decoded.runoutStationPlacements.placements[1]
        assertEquals("liner-1", second.componentId)
        assertEquals(1, second.stationIndex)
        // The dragged position is an authored value: it round-trips exactly, no rounding.
        assertEquals(431.877f, second.axialMm, 0f)
    }

    @Test
    fun `a position of exactly zero round trips (a station dragged to the aft edge)`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = spec(),
            runoutStationPlacements = RunoutStationPlacements(
                listOf(RunoutStationPlacement("liner-1", 0, 0f))
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(1, decoded.runoutStationPlacements.placements.size)
        assertEquals(0f, decoded.runoutStationPlacements.placements.single().axialMm, 0f)
    }

    @Test
    fun `envelope without runout_stations decodes to derived placement`() {
        val raw = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "unit_locked": true,
              "job_number": "",
              "customer": "",
              "vessel": "",
              "shaft_position": "OTHER",
              "notes": "",
              "spec": { "overallLengthMm": 500.0 }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertTrue(decoded.runoutStationPlacements.placements.isEmpty())
    }

    @Test
    fun `legacy bare-spec file decodes to derived placement`() {
        val decoded = ShaftDocCodec.decode("""{ "overallLengthMm": 500.0 }""")

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertTrue(decoded.runoutStationPlacements.placements.isEmpty())
    }

    @Test
    fun `placements survive decode even when nothing in the spec matches them`() {
        // Same rule as runout readings: whether a placement still has a station depends on
        // resolved components and count overrides, neither of which the codec has. Pruning here
        // would silently discard positions that come back the moment the count is raised.
        val placements = RunoutStationPlacements(
            listOf(
                RunoutStationPlacement("gone-liner", 0, 10f),
                RunoutStationPlacement("gone-liner", 1, 20f),
            )
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 500f), // no bodies/tapers/liners at all
            runoutStationPlacements = placements,
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(2, decoded.runoutStationPlacements.placements.size)
        assertEquals(listOf(10f, 20f), decoded.runoutStationPlacements.orderedFor("gone-liner"))
    }
}
