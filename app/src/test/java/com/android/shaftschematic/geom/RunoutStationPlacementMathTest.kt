package com.android.shaftschematic.geom

import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authored runout-station placement: the clamp a drag lands under, where an added station goes
 * once positions are authored, and how a local position maps back onto a fragmented component.
 *
 * These rules are what keep a dragged bubble from crossing its neighbours (which would renumber
 * stations under their typed TIR values) and what makes "+" insert between the existing bubbles
 * instead of re-deriving every position.
 */
class RunoutStationPlacementMathTest {

    private val inch = 25.4f

    private fun liner(start: Float, len: Float) =
        RunoutComponentSpan("l1", RunoutComponentKind.LINER, start, len)

    private fun body(start: Float, len: Float) =
        RunoutComponentSpan("b1", RunoutComponentKind.BODY, start, len)

    // ── Clamp ────────────────────────────────────────────────────────────────

    @Test
    fun `a drag inside the component and clear of its neighbours passes through`() {
        val positions = listOf(0f, 500f, 1000f)
        val got = clampDraggedStationMm(positions, index = 1, targetMm = 600f, spanMm = 1000f)
        assertEquals(600f, got, 1e-3f)
    }

    @Test
    fun `a drag past the next station stops a minimum gap short of it`() {
        val positions = listOf(0f, 500f, 1000f)
        val got = clampDraggedStationMm(positions, index = 1, targetMm = 2000f, spanMm = 1000f)
        assertEquals(1000f - RUNOUT_MIN_STATION_GAP_MM, got, 1e-3f)
    }

    @Test
    fun `a drag past the previous station stops a minimum gap after it`() {
        val positions = listOf(0f, 500f, 1000f)
        val got = clampDraggedStationMm(positions, index = 1, targetMm = -800f, spanMm = 1000f)
        assertEquals(RUNOUT_MIN_STATION_GAP_MM, got, 1e-3f)
    }

    @Test
    fun `the end stations clamp to the component's own edges`() {
        val positions = listOf(100f, 500f, 900f)
        assertEquals(0f, clampDraggedStationMm(positions, 0, -50f, 1000f), 1e-3f)
        assertEquals(1000f, clampDraggedStationMm(positions, 2, 5000f, 1000f), 1e-3f)
    }

    @Test
    fun `station order survives every drag`() {
        // Whatever the target, the clamped value never crosses a neighbour — this is the
        // invariant that keeps a typed TIR on its own bubble.
        val positions = listOf(0f, 250f, 500f, 750f, 1000f)
        val targets = listOf(-9999f, -1f, 0f, 260f, 499f, 501f, 900f, 9999f)
        for (i in positions.indices) {
            for (t in targets) {
                val got = clampDraggedStationMm(positions, i, t, spanMm = 1000f)
                if (i > 0) assertTrue("index $i target $t crossed aft", got >= positions[i - 1])
                if (i < positions.size - 1) {
                    assertTrue("index $i target $t crossed fwd", got <= positions[i + 1])
                }
            }
        }
    }

    @Test
    fun `a short component shrinks the gap instead of locking its stations`() {
        // 1" liner with three stations: the nominal half-inch gap cannot fit, so it scales
        // down rather than pinning every station where it already sits.
        val span = 1f * inch
        val positions = listOf(0f, span / 2f, span)
        val got = clampDraggedStationMm(positions, index = 1, targetMm = span * 0.9f, spanMm = span)
        assertTrue("drag made no progress", got > span / 2f)
        assertTrue("drag crossed its neighbour", got <= span)
    }

    // ── Insertion ────────────────────────────────────────────────────────────

    @Test
    fun `adding to a two-station liner lands between them`() {
        val span = 40f * inch
        val defaults = runoutStationPositionsMm(0f, span, 2, useEdgeInset = true)
        val insertion = planStationInsertion(defaults, span, useEdgeInset = true)

        assertEquals("inserted at the wrong index", 1, insertion.index)
        assertEquals((defaults[0] + defaults[1]) / 2f, insertion.axialMm, 1e-3f)
    }

    @Test
    fun `adding to a single station lands where a second one normally would`() {
        // The one station sits at the AFT default; the new one takes the FWD default. The
        // existing station is authored, so it does not move to meet it.
        val span = 40f * inch
        val defaults = runoutStationPositionsMm(0f, span, 2, useEdgeInset = true)
        val insertion = planStationInsertion(listOf(defaults[0]), span, useEdgeInset = true)

        assertEquals(1, insertion.index)
        assertEquals(defaults[1], insertion.axialMm, 1e-3f)
    }

    @Test
    fun `a single station dragged forward puts the new one aft of it`() {
        val span = 40f * inch
        val defaults = runoutStationPositionsMm(0f, span, 2, useEdgeInset = true)
        val insertion = planStationInsertion(listOf(defaults[1]), span, useEdgeInset = true)

        assertEquals("new station should take index 0", 0, insertion.index)
        assertEquals(defaults[0], insertion.axialMm, 1e-3f)
    }

    @Test
    fun `stations clustered at one end send the new one to the empty end`() {
        val span = 100f * inch
        val clustered = listOf(0f, 20f, 40f)
        val insertion = planStationInsertion(clustered, span, useEdgeInset = false)

        assertEquals("new station belongs after the cluster", 3, insertion.index)
        assertTrue("new station stayed in the cluster", insertion.axialMm > span / 2f)
    }

    @Test
    fun `adding to an empty set centres the station`() {
        val insertion = planStationInsertion(emptyList(), 1000f, useEdgeInset = true)
        assertEquals(0, insertion.index)
        assertEquals(500f, insertion.axialMm, 1e-3f)
    }

    @Test
    fun `insertion keeps the positions sorted`() {
        val positions = listOf(100f, 900f)
        val out = insertStationPosition(positions, planStationInsertion(positions, 1000f, false))
        assertEquals(listOf(100f, 500f, 900f), out)
    }

    @Test
    fun `removal takes the station at the chosen index`() {
        assertEquals(listOf(100f, 900f), removeStationPosition(listOf(100f, 500f, 900f), 1))
        assertEquals(listOf(100f, 500f), removeStationPosition(listOf(100f, 500f, 900f), 2))
        // Out of range is a no-op rather than a crash.
        assertEquals(listOf(100f), removeStationPosition(listOf(100f), 7))
        assertEquals(emptyList<Float>(), removeStationPosition(emptyList(), 0))
    }

    @Test
    fun `removal takes the most redundant station`() {
        // The middle station sits exactly between its neighbours — nothing it covers is lost.
        val even = listOf(200f, 450f, 700f)
        assertEquals(1, authoredStationIndexToRemove(even, 1000f, useEdgeInset = true) { false })
    }

    @Test
    fun `removal prefers an unmeasured station over a more redundant measured one`() {
        val even = listOf(200f, 450f, 700f)
        // The redundant middle one is measured, so the blank fwd station goes instead.
        assertEquals(
            2,
            authoredStationIndexToRemove(even, 1000f, useEdgeInset = true) { it == 0 || it == 1 },
        )
    }

    @Test
    fun `removal degenerates safely`() {
        assertEquals(-1, authoredStationIndexToRemove(emptyList(), 1000f, true) { false })
        assertEquals(0, authoredStationIndexToRemove(listOf(500f), 1000f, true) { true })
    }

    @Test
    fun `add then remove is a round trip on a blank component`() {
        // The insertion lands at a gap midpoint, which is exactly the most redundant position —
        // so the two operations undo each other and the dragged stations come back untouched.
        val span = 40f * inch
        val dragged = listOf(200f, 700f)
        val added = insertStationPosition(
            dragged, planStationInsertion(dragged, span, useEdgeInset = true),
        )
        val removeAt = authoredStationIndexToRemove(added, span, useEdgeInset = true) { false }
        assertEquals(dragged, removeStationPosition(added, removeAt))
    }

    // ── Local ↔ shaft space ──────────────────────────────────────────────────

    @Test
    fun `span and origin come from the component's outer edges`() {
        val runs = listOf(body(200f, 300f), body(700f, 300f))
        assertEquals(200f, runoutComponentOriginMm(runs), 1e-3f)
        // Aft edge to fwd edge INCLUDING the gap — not the 600mm of summed metal.
        assertEquals(800f, runoutComponentSpanMm(runs), 1e-3f)
    }

    @Test
    fun `a local position resolves against the component's aft edge`() {
        val runs = listOf(liner(1000f, 500f))
        assertEquals(1250f, resolveStationShaftMm(runs, 250f), 1e-3f)
    }

    @Test
    fun `a position stranded in a gap is pulled onto the nearest run`() {
        // A body cut by a liner: local 320mm falls in the 500..700 gap, closer to the aft run.
        val runs = listOf(body(0f, 500f), body(700f, 500f))
        assertEquals(500f, resolveStationShaftMm(runs, 520f), 1e-3f)
        // Closer to the fwd run instead.
        assertEquals(700f, resolveStationShaftMm(runs, 680f), 1e-3f)
    }

    @Test
    fun `freezing reads the drawn stations back as local positions`() {
        val runs = listOf(liner(1000f, 500f))
        val stations = listOf(
            RunoutStationX("l1", 1025f, 0f, stationIndex = 0),
            RunoutStationX("l1", 1475f, 0f, stationIndex = 1),
        )
        assertEquals(listOf(25f, 475f), localStationPositions(runs, stations))
    }

    @Test
    fun `freezing orders by station index, not by list order`() {
        val runs = listOf(liner(0f, 500f))
        val stations = listOf(
            RunoutStationX("l1", 400f, 0f, stationIndex = 1),
            RunoutStationX("l1", 100f, 0f, stationIndex = 0),
        )
        assertEquals(listOf(100f, 400f), localStationPositions(runs, stations))
    }

    @Test
    fun `the effective gap never exceeds the nominal one`() {
        assertTrue(effectiveStationGapMm(10_000f, 2) <= RUNOUT_MIN_STATION_GAP_MM)
        assertEquals(RUNOUT_MIN_STATION_GAP_MM, effectiveStationGapMm(10_000f, 2), 1e-3f)
        assertEquals(0f, effectiveStationGapMm(1000f, 1), 1e-3f)
    }

    @Test
    fun `current positions merge pins over derived siblings`() {
        val runs = listOf(liner(100f, 1000f))
        val merged = currentLocalStationPositions(runs, 2, mapOf(1 to 300f))

        // Station 0 stays at its derived edge-inset spot; only the pinned one differs.
        assertEquals(RunoutConfig.RUNOUT_EDGE_INSET_MM, merged[0], 1e-3f)
        assertEquals(300f, merged[1], 1e-3f)
    }

    @Test
    fun `current positions clamp a derived sibling to an out-of-order pin, never the pin`() {
        // The pin sits AFT of where the sibling's derived spot lands (a geometry edit can do
        // this) — the derived value yields so the merged list stays AFT→FWD; the pin is
        // stored-verbatim data and must come through untouched.
        val runs = listOf(liner(100f, 1000f))
        val merged = currentLocalStationPositions(runs, 2, mapOf(1 to 10f))

        assertEquals(10f, merged[1], 1e-3f)
        assertTrue(merged[0] <= merged[1])
    }

    @Test
    fun `current positions derive body cell midpoints when nothing is pinned`() {
        val runs = listOf(body(0f, 900f))
        assertEquals(
            listOf(150f, 450f, 750f),
            currentLocalStationPositions(runs, 3, emptyMap()),
        )
    }

    @Test
    fun `the edge inset band matches the derived taper and liner convention`() {
        // A taper's two stations sit inset from each end; inserting a third must land between
        // them, inside that same band.
        val span = 12f * inch
        val defaults = runoutStationPositionsMm(
            0f, span, 2, useEdgeInset = true, edgeInsetMm = RunoutConfig.RUNOUT_EDGE_INSET_MM,
        )
        val insertion = planStationInsertion(defaults, span, useEdgeInset = true)
        assertTrue(insertion.axialMm > defaults[0])
        assertTrue(insertion.axialMm < defaults[1])
    }
}
