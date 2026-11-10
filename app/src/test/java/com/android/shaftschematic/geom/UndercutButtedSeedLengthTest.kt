package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A butted seed's length edit holds the shared edge (on-device report: a "Next cut" under the FWD
 * S.E.T. grew into the cut it was butted against when its real length was typed).
 */
class UndercutButtedSeedLengthTest {

    // Cut 1 ends at 1000; the seed is butted FWD of it at the 25.4 placeholder length.
    private val cut1 = UndercutSpanMm("c1", 946f, 1000f)
    private val seedStart = 1000f
    private val seedLen = 25.4f

    @Test
    fun `a seed butted on its AFT end keeps its start at the new length`() {
        assertEquals(
            1000f,
            undercutButtedSeedStartForLength(seedStart, seedLen, 50.8f, seedStart, seedLen, listOf(cut1))!!,
            1e-4f,
        )
    }

    @Test
    fun `the FWD-datum rule alone would have grown it into cut 1`() {
        // FWD S.E.T. at 1400: Distance 374.6 pinned, length 50.8 -> start 974.6, inside cut 1.
        val pinned = undercutCanonicalForNewLength(
            com.android.shaftschematic.model.UndercutReference.FWD_SET,
            seedStart, seedLen, 50.8f, aftSetXMm = 0f, fwdSetXMm = 1400f,
        )
        assertEquals(974.6f, pinned, 1e-3f)
    }

    @Test
    fun `a second length edit still holds the edge`() {
        assertEquals(
            1000f,
            undercutButtedSeedStartForLength(seedStart, 50.8f, 63.5f, seedStart, seedLen, listOf(cut1))!!,
            1e-4f,
        )
    }

    @Test
    fun `a seed butted on its FWD end keeps its end`() {
        // The add flow's AFT-side seed: [920.6, 946] touching cut 1's start.
        assertEquals(
            895.2f,
            undercutButtedSeedStartForLength(920.6f, 25.4f, 50.8f, 920.6f, 25.4f, listOf(cut1))!!,
            1e-3f,
        )
    }

    @Test
    fun `a cut moved by a typed distance takes the ordinary rule`() {
        // Typed onto another cut's end: it butts, but not where it was seeded.
        val other = UndercutSpanMm("c2", 1100f, 1200f)
        assertNull(undercutButtedSeedStartForLength(1200f, seedLen, 50.8f, seedStart, seedLen, listOf(cut1, other)))
        // And a seed that touches nothing is not butted.
        assertNull(undercutButtedSeedStartForLength(500f, seedLen, 50.8f, 500f, seedLen, listOf(cut1)))
    }
}
