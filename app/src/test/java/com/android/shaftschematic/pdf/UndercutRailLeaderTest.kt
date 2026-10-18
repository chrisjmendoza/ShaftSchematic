package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [undercutRailLeader] — the leader `drawUndercutRail` strokes from a fallback value to its own
 * dimension line (on-device request: a value lifted off its line must say which span it states).
 */
class UndercutRailLeaderTest {

    private val railY = 100f
    private val arrow = 3f

    @Test
    fun `a value above its line drops from its halo bottom to the line at its centre`() {
        val l = undercutRailLeader(10f, 60f, labelCxPt = 35f, arrowPt = arrow, railY = railY, labelNearEdgeY = 90f)
        assertEquals(UndercutRailLeader(35f, 90f, railY), l)
    }

    @Test
    fun `a value below its line rises from its halo top to the line`() {
        val l = undercutRailLeader(10f, 60f, labelCxPt = 35f, arrowPt = arrow, railY = railY, labelNearEdgeY = 112f)
        assertEquals(UndercutRailLeader(35f, 112f, railY), l)
    }

    @Test
    fun `a slid value's leader is clamped onto its span between the arrowheads`() {
        assertEquals(13f, undercutRailLeader(10f, 60f, 4f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(13f, undercutRailLeader(10f, 60f, 12f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(57f, undercutRailLeader(10f, 60f, 59f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(57f, undercutRailLeader(10f, 60f, 80f, arrow, railY, 90f)!!.x, 1e-4f)
        // A larger head narrows the landing zone with it.
        assertEquals(15f, undercutRailLeader(10f, 60f, 12f, 5f, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `a span too short for both heads takes its midpoint`() {
        assertEquals(12f, undercutRailLeader(10f, 14f, 30f, arrow, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `reversed span ends clamp the same way`() {
        assertEquals(57f, undercutRailLeader(60f, 10f, 59f, arrow, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `a value already on its line needs no leader`() {
        assertNull(undercutRailLeader(10f, 60f, 35f, arrow, railY, railY))
    }
}
