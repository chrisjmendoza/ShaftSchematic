package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [railValueLeader] — the ONE leader both rail families stroke from a value off its line to its
 * own dimension line: the undercut strips' fallback rows (on-device request: a value lifted off its
 * line must say which span it states) and the schematic rails' displaced values (emulator QA).
 */
class RailValueLeaderTest {

    private val railY = 100f
    private val arrow = 3f

    @Test
    fun `a value above its line drops from its halo bottom to the line at its centre`() {
        val l = railValueLeader(10f, 60f, labelCxPt = 35f, arrowPt = arrow, railY = railY, labelNearEdgeY = 90f)
        assertEquals(RailValueLeader(35f, 90f, railY), l)
    }

    @Test
    fun `a value below its line rises from its halo top to the line`() {
        val l = railValueLeader(10f, 60f, labelCxPt = 35f, arrowPt = arrow, railY = railY, labelNearEdgeY = 112f)
        assertEquals(RailValueLeader(35f, 112f, railY), l)
    }

    @Test
    fun `a slid value's leader is clamped onto its span between the arrowheads`() {
        assertEquals(13f, railValueLeader(10f, 60f, 4f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(13f, railValueLeader(10f, 60f, 12f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(57f, railValueLeader(10f, 60f, 59f, arrow, railY, 90f)!!.x, 1e-4f)
        assertEquals(57f, railValueLeader(10f, 60f, 80f, arrow, railY, 90f)!!.x, 1e-4f)
        // A larger head narrows the landing zone with it.
        assertEquals(15f, railValueLeader(10f, 60f, 12f, 5f, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `a span too short for both heads takes its midpoint`() {
        assertEquals(12f, railValueLeader(10f, 14f, 30f, arrow, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `reversed span ends clamp the same way`() {
        assertEquals(57f, railValueLeader(60f, 10f, 59f, arrow, railY, 90f)!!.x, 1e-4f)
    }

    @Test
    fun `a value already on its line needs no leader`() {
        assertNull(railValueLeader(10f, 60f, 35f, arrow, railY, railY))
    }

    @Test
    fun `a value beside its span slants in from its nearer corner`() {
        // Span [10, 60], value centred at 80 with half width 10: box [70, 90].
        val l = railValueLeader(10f, 60f, 80f, arrow, railY, 90f, labelHalfWidth = 10f)!!
        assertEquals(57f, l.x, 1e-4f)
        assertEquals(70f, l.fromX, 1e-4f)
        assertEquals(90f, l.fromY, 1e-4f)
        assertEquals(railY, l.toY, 1e-4f)
    }

    @Test
    fun `a value standing over its landing point stays vertical`() {
        val l = railValueLeader(10f, 60f, 35f, arrow, railY, 90f, labelHalfWidth = 10f)!!
        assertEquals(l.x, l.fromX, 0f)
    }

    @Test
    fun `an anchor moved off the centre still lands between the heads`() {
        val l = railValueLeader(10f, 60f, 35f, arrow, railY, 90f, labelHalfWidth = 10f, anchorX = 2f)!!
        assertEquals(13f, l.x, 1e-4f)
        assertEquals(25f, l.fromX, 1e-4f)
    }
}
