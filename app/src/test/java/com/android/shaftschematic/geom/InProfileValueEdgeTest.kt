package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measured Ø printed inside the profile near a component's end is pushed in off the end line
 * (on-device report: the knockout covered the liner's end line — the outline takes precedence).
 */
class InProfileValueEdgeTest {

    private fun col(cx: Float, w: Float = 10f) =
        WornValueColumn(cx = cx, cy = 50f, haloLeft = cx - w / 2f, haloTop = 20f, haloRight = cx + w / 2f, haloBottom = 80f)

    @Test fun `a value clear of the ends does not move`() {
        val c = col(150f)
        assertEquals(listOf(c), keepValueColumnsInsideSpan(listOf(c), 100f, 300f, 3f))
    }

    @Test fun `a value on an end line is pushed in past it`() {
        val left = keepValueColumnsInsideSpan(listOf(col(101f)), 100f, 300f, 3f).single()
        assertEquals(103f, left.haloLeft, 1e-4f)
        assertEquals(108f, left.cx, 1e-4f)
        val right = keepValueColumnsInsideSpan(listOf(col(299f)), 100f, 300f, 3f).single()
        assertEquals(297f, right.haloRight, 1e-4f)
        // Only x moves.
        assertEquals(20f, right.haloTop, 0f)
        assertEquals(80f, right.haloBottom, 0f)
    }

    @Test fun `values the push would stack are spread apart, in order, inside the span`() {
        val kept = keepValueColumnsInsideSpan(listOf(col(100f), col(102f)), 100f, 300f, 3f)
        assertTrue(kept[0].haloRight <= kept[1].haloLeft + 1e-4f)
        assertTrue(kept[0].haloLeft >= 103f - 1e-4f)
        val atFwd = keepValueColumnsInsideSpan(listOf(col(298f), col(300f)), 100f, 300f, 3f)
        assertTrue(atFwd[0].haloRight <= atFwd[1].haloLeft + 1e-4f)
        assertTrue(atFwd[1].haloRight <= 297f + 1e-4f)
    }

    @Test fun `a span too narrow keeps its values centred on it`() {
        val kept = keepValueColumnsInsideSpan(listOf(col(100f), col(105f)), 100f, 115f, 3f)
        assertEquals(97.5f, kept[0].haloLeft, 1e-4f)
        assertEquals(117.5f, kept[1].haloRight, 1e-4f)
    }
}
