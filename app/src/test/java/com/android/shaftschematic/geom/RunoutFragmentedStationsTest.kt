package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Station identity across a fragmented body — the defect this pass fixes.
 *
 * A body split by a liner draws as several runs but is ONE component: one carousel name, one
 * station-editor row, one override, and one continuous run of station indices. Before this,
 * each run derived its own full count and restarted `stationIndex` at 0, so a single reading
 * key identified several bubbles at once.
 */
class RunoutFragmentedStationsTest {

    /** Identity x mapping — station mm and x are interchangeable here. */
    private val xAt: (Float) -> Float = { it }

    private fun bodyRuns(vararg spans: Pair<Float, Float>) =
        spans.map { (start, len) ->
            RunoutComponentSpan("b1", RunoutComponentKind.BODY, start, len)
        }

    @Test
    fun `station indices run continuously across a body's fragments`() {
        val stations = collectRunoutStations(
            spans = bodyRuns(0f to 500f, 700f to 500f),
            overrides = mapOf("b1" to 4),
            xAtMm = xAt,
        )

        assertEquals(4, stations.size)
        assertEquals(listOf(0, 1, 2, 3), stations.map { it.stationIndex })
        assertTrue(stations.all { it.componentId == "b1" })
    }

    @Test
    fun `the override governs the whole body, not each fragment`() {
        // Pre-fix this produced 3 x 3 = 9 stations for a three-run body.
        val stations = collectRunoutStations(
            spans = bodyRuns(0f to 400f, 500f to 400f, 1000f to 400f),
            overrides = mapOf("b1" to 3),
            xAtMm = xAt,
        )
        assertEquals(3, stations.size)
    }

    @Test
    fun `a short leftover run does not collect a full default`() {
        // 60" of body split into a 58" run and a 2" sliver: 3 stations for the body,
        // essentially all of them on the run that can host them.
        val inch = 25.4f
        val stations = collectRunoutStations(
            spans = bodyRuns(0f to 58f * inch, 1500f to 2f * inch),
            overrides = emptyMap(),
            xAtMm = xAt,
        )

        assertEquals(3, stations.size)
        assertTrue(stations.count { it.stationMm > 1500f } <= 1)
    }

    @Test
    fun `every station of a body carries the same component key`() {
        val stations = collectRunoutStations(
            spans = bodyRuns(0f to 500f, 700f to 500f),
            overrides = emptyMap(),
            xAtMm = xAt,
        )
        assertEquals(setOf("b1"), stations.map { it.componentId }.toSet())
    }

    @Test
    fun `distinct components keep independent index runs`() {
        val spans = listOf(
            RunoutComponentSpan("b1", RunoutComponentKind.BODY, 0f, 500f),
            RunoutComponentSpan("l1", RunoutComponentKind.LINER, 600f, 300f),
        )
        val stations = collectRunoutStations(spans, emptyMap(), xAt)

        assertEquals(listOf(0), stations.filter { it.componentId == "b1" }.map { it.stationIndex })
        assertEquals(listOf(0, 1), stations.filter { it.componentId == "l1" }.map { it.stationIndex })
    }

    @Test
    fun `no reading key is ever shared by two stations`() {
        val spans = bodyRuns(0f to 400f, 500f to 400f, 1000f to 400f) +
            RunoutComponentSpan("t1", RunoutComponentKind.TAPER, 1500f, 300f)
        val stations = collectRunoutStations(spans, emptyMap(), xAt)

        val keys = stations.map { it.componentId to it.stationIndex }
        assertEquals(keys.size, keys.toSet().size)
    }
}
