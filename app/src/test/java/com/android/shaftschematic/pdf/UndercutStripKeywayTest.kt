package com.android.shaftschematic.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which keyways an undercut detail strip draws ([undercutStripShowsSpan]): one the strip shows
 * whole — it reached the taper or the shaft end — draws, including one whose open end the strip
 * stops a hair short of at the S.E.T.; one the strip only clips at a break is left off.
 */
class UndercutStripKeywayTest {

    @Test
    fun `a keyway wholly inside the window draws`() {
        assertTrue(undercutStripShowsSpan(100f, 380f, 0f, 500f))
    }

    @Test
    fun `a strip ending just short of the face the keyway runs out through still draws it`() {
        // On-device job: the FWD S.E.T. sits 1/8" short of the body's FWD face.
        assertTrue(undercutStripShowsSpan(5668.96f, 5948.36f, 5500f, 5945.19f))
    }

    @Test
    fun `a keyway the window only clips at a break is left off`() {
        assertFalse(undercutStripShowsSpan(100f, 380f, 300f, 900f))
        assertFalse(undercutStripShowsSpan(100f, 380f, 400f, 900f))
    }

    @Test
    fun `an empty span never draws`() {
        assertFalse(undercutStripShowsSpan(200f, 200f, 0f, 500f))
    }
}
