package com.android.shaftschematic.geom

import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Length-driven station counts: one bubble per 20 inches, replacing the flat "3 per body".
 */
class RunoutStationIntervalTest {

    private val inch = 25.4f

    // ── Derived counts ────────────────────────────────────────────────────────

    @Test
    fun `a short body gets one station, not three`() {
        // The reported case: a 1-2" leftover run collected a full default's worth of bubbles.
        assertEquals(1, defaultStationCount(RunoutComponentKind.BODY, 1f * inch))
        assertEquals(1, defaultStationCount(RunoutComponentKind.BODY, 2f * inch))
    }

    @Test
    fun `a body takes one station per twenty inches, rounded up`() {
        assertEquals(1, defaultStationCount(RunoutComponentKind.BODY, 20f * inch))
        assertEquals(2, defaultStationCount(RunoutComponentKind.BODY, 21f * inch))
        assertEquals(2, defaultStationCount(RunoutComponentKind.BODY, 40f * inch))
        assertEquals(5, defaultStationCount(RunoutComponentKind.BODY, 100f * inch))
    }

    @Test
    fun `a sixty inch body still gets three, as it always did`() {
        assertEquals(3, defaultStationCount(RunoutComponentKind.BODY, 60f * inch))
    }

    @Test
    fun `tapers stay at two whatever their length`() {
        assertEquals(2, defaultStationCount(RunoutComponentKind.TAPER, 4f * inch))
        assertEquals(2, defaultStationCount(RunoutComponentKind.TAPER, 60f * inch))
    }

    @Test
    fun `liners never drop below two but do scale up`() {
        assertEquals(2, defaultStationCount(RunoutComponentKind.LINER, 6f * inch))
        assertEquals(2, defaultStationCount(RunoutComponentKind.LINER, 40f * inch))
        assertEquals(3, defaultStationCount(RunoutComponentKind.LINER, 60f * inch))
    }

    @Test
    fun `a very long shaft is capped`() {
        assertEquals(
            RunoutConfig.MAX_STATIONS_PER_COMPONENT,
            defaultStationCount(RunoutComponentKind.BODY, 4000f * inch),
        )
    }

    @Test
    fun `a zero length component gets no stations`() {
        assertEquals(0, defaultStationCount(RunoutComponentKind.BODY, 0f))
        assertEquals(0, defaultStationCount(RunoutComponentKind.TAPER, -5f))
    }

    // ── Apportionment across a fragmented body's runs ─────────────────────────

    @Test
    fun `apportionment splits by length and totals exactly`() {
        val out = apportionStations(listOf(300f, 100f), 4)
        assertEquals(4, out.sum())
        assertEquals(listOf(3, 1), out)
    }

    @Test
    fun `a sliver run yields to the long run`() {
        val out = apportionStations(listOf(1000f, 10f), 2)
        assertEquals(2, out.sum())
        assertEquals(listOf(2, 0), out)
    }

    @Test
    fun `equal runs split evenly`() {
        assertEquals(listOf(2, 2), apportionStations(listOf(500f, 500f), 4))
    }

    @Test
    fun `more stations than runs still totals exactly`() {
        val out = apportionStations(listOf(100f, 100f, 100f), 8)
        assertEquals(8, out.sum())
        assertTrue(out.all { it > 0 })
    }

    @Test
    fun `degenerate inputs are safe`() {
        assertEquals(emptyList<Int>(), apportionStations(emptyList(), 3))
        assertEquals(listOf(0, 0), apportionStations(listOf(100f, 100f), 0))
        assertEquals(listOf(0, 0), apportionStations(listOf(0f, 0f), 4))
    }
}
