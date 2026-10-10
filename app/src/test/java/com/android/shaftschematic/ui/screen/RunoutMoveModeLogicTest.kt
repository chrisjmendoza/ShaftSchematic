package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The pure decisions behind the Runout tab's Move mode: what a tap selects, which bubble a
 * press picks up, what survives a count change, and how a selection is labelled and read
 * against the current drawing.
 */
class RunoutMoveModeLogicTest {

    private fun bubble(id: String, index: Int, x: Float, y: Float = 100f) = PlacedRunoutBubble(
        componentId = id,
        stationMm = x,
        stationX = x,
        surfaceY = 50f,
        bubbleX = x,
        bubbleCenterY = y,
        row = 0,
        leader = emptyList(),
        stationIndex = index,
    )

    private val entries = listOf(
        RunoutComponentEntry(
            id = "b2", label = "Body 2", kind = RunoutComponentKind.BODY,
            lengthMm = 800f, startMm = 0f,
        ),
        RunoutComponentEntry(
            id = "l1", label = "Liner 1", kind = RunoutComponentKind.LINER,
            lengthMm = 400f, startMm = 800f,
        ),
    )

    // ── moveModeSelectionForTap ──────────────────────────────────────────────

    @Test
    fun `a tap on a bubble selects that station`() {
        val sel = moveModeSelectionForTap(bubble("b2", 2, 40f), hitComponentId = "b2")
        assertEquals(RunoutMoveSelection("b2", 2), sel)
    }

    @Test
    fun `a tap on a component run selects the component alone`() {
        assertEquals(RunoutMoveSelection("l1", null), moveModeSelectionForTap(null, "l1"))
    }

    @Test
    fun `a tap on bare paper clears the selection`() {
        assertNull(moveModeSelectionForTap(null, null))
    }

    // ── pickBubbleForMove ────────────────────────────────────────────────────

    @Test
    fun `without a selection the nearest bubble wins, as pickBubbleAt decides`() {
        val a = bubble("b2", 0, 100f)
        val b = bubble("b2", 1, 120f)
        // 112 is nearer b (8) than a (12); both are within reach.
        assertSame(b, pickBubbleForMove(listOf(a, b), 7f, 112f, 100f, 14f, selected = null))
    }

    @Test
    fun `the selected station wins a press within its reach even when another is nearer`() {
        val a = bubble("b2", 0, 100f)
        val b = bubble("b2", 1, 120f)
        val picked = pickBubbleForMove(
            listOf(a, b), 7f, 112f, 100f, 14f, selected = RunoutMoveSelection("b2", 0),
        )
        assertSame(a, picked)
    }

    @Test
    fun `the selected station loses a press outside its reach`() {
        val a = bubble("b2", 0, 100f)
        val b = bubble("b2", 1, 140f)
        // Reach is 7 + 7 = 14: the press is 35 from the selected a and 5 from b.
        val picked = pickBubbleForMove(
            listOf(a, b), 7f, 135f, 100f, 7f, selected = RunoutMoveSelection("b2", 0),
        )
        assertSame(b, picked)
    }

    @Test
    fun `a component-only selection falls back to the nearest bubble`() {
        val a = bubble("b2", 0, 100f)
        val b = bubble("l1", 0, 120f)
        val picked = pickBubbleForMove(
            listOf(a, b), 7f, 118f, 100f, 14f, selected = RunoutMoveSelection("b2", null),
        )
        assertSame(b, picked)
    }

    @Test
    fun `a press out of reach of every bubble picks nothing`() {
        val a = bubble("b2", 0, 100f)
        assertNull(
            pickBubbleForMove(listOf(a), 7f, 300f, 100f, 14f, RunoutMoveSelection("b2", 0)),
        )
    }

    // ── selectionAfterCountChange ────────────────────────────────────────────

    @Test
    fun `a count change keeps the component and drops the station`() {
        assertEquals(
            RunoutMoveSelection("b2", null),
            selectionAfterCountChange(RunoutMoveSelection("b2", 3)),
        )
        assertEquals(
            RunoutMoveSelection("b2", null),
            selectionAfterCountChange(RunoutMoveSelection("b2", null)),
        )
        assertNull(selectionAfterCountChange(null))
    }

    // ── liveMoveSelection ────────────────────────────────────────────────────

    @Test
    fun `a selection on a component that no longer resolves reads as none`() {
        assertNull(liveMoveSelection(RunoutMoveSelection("gone", 1), mapOf("b2" to 3)))
    }

    @Test
    fun `a station past the current count falls back to its component`() {
        assertEquals(
            RunoutMoveSelection("b2", null),
            liveMoveSelection(RunoutMoveSelection("b2", 3), mapOf("b2" to 3)),
        )
    }

    @Test
    fun `a station within the count is kept as is`() {
        val sel = RunoutMoveSelection("b2", 2)
        assertEquals(sel, liveMoveSelection(sel, mapOf("b2" to 3)))
        assertNull(liveMoveSelection(null, mapOf("b2" to 3)))
    }

    // ── runoutSpanIdAt ───────────────────────────────────────────────────────

    @Test
    fun `the run containing a position names its component, past either end none`() {
        val spans = listOf(
            RunoutComponentSpan("b2", RunoutComponentKind.BODY, startMm = 0f, lengthMm = 300f),
            RunoutComponentSpan("l1", RunoutComponentKind.LINER, startMm = 300f, lengthMm = 200f),
            RunoutComponentSpan("b2", RunoutComponentKind.BODY, startMm = 500f, lengthMm = 300f),
        )
        assertEquals("b2", runoutSpanIdAt(spans, 150f))
        assertEquals("l1", runoutSpanIdAt(spans, 400f))
        // A fragmented body's later run still names the one component.
        assertEquals("b2", runoutSpanIdAt(spans, 650f))
        assertNull(runoutSpanIdAt(spans, -1f))
        assertNull(runoutSpanIdAt(spans, 801f))
    }

    // ── stationCountFor / canRemoveStation (shared with the station editor) ──

    @Test
    fun `the toolbar's count is the editor's - override first, derived default otherwise`() {
        val body = entries.first()
        assertEquals(body.defaultCount, stationCountFor(body, emptyMap()))
        assertEquals(5, stationCountFor(body, mapOf("b2" to 5)))
        assertEquals(0, stationCountFor(body, mapOf("b2" to 0)))
    }

    @Test
    fun `minus stops at the legal floor of zero`() {
        assertEquals(false, canRemoveStation(0))
        assertEquals(true, canRemoveStation(1))
    }

    // ── moveSelectionLabel ───────────────────────────────────────────────────

    @Test
    fun `labels name the component and the 1-based station`() {
        assertEquals("Body 2 · Station 3", moveSelectionLabel(RunoutMoveSelection("b2", 2), entries))
        assertEquals("Liner 1", moveSelectionLabel(RunoutMoveSelection("l1", null), entries))
        assertEquals("Nothing selected", moveSelectionLabel(null, entries))
        assertEquals("Component", moveSelectionLabel(RunoutMoveSelection("x", null), entries))
    }
}
