package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_DENSITY_MAX
import com.android.shaftschematic.geom.THREAD_DENSITY_MIN
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "Thread density" slider's snap and label. The Settings row's "Default (50%)" button is
 * enabled by an exact `!=` against the shipped value, so a drag released on 50% must land on
 * exactly `0.5f`, not a float-step neighbour of it.
 */
class ThreadDensitySliderMathTest {

    @Test
    fun `a drag released near 50 percent snaps to exactly the shipped density`() {
        assertEquals(THREAD_DENSITY_DEFAULT, snappedThreadDensity(0.512f), 0f)
        assertEquals(THREAD_DENSITY_DEFAULT, snappedThreadDensity(0.49f), 0f)
    }

    @Test
    fun `snaps land on 5 percent steps inside the range`() {
        assertEquals(0.35f, snappedThreadDensity(0.36f), 1e-6f)
        assertEquals(THREAD_DENSITY_MIN, snappedThreadDensity(0.02f), 0f)
        assertEquals(THREAD_DENSITY_MAX, snappedThreadDensity(1.4f), 0f)
    }

    @Test
    fun `the label reads whole percent`() {
        assertEquals("50%", fmtThreadDensity(THREAD_DENSITY_DEFAULT))
        assertEquals("15%", fmtThreadDensity(THREAD_DENSITY_MIN))
        assertEquals("100%", fmtThreadDensity(THREAD_DENSITY_MAX))
    }
}
