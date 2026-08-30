package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.keywayClocking
import com.android.shaftschematic.model.withKeyways180Apart
import com.android.shaftschematic.model.withKeyways90Apart
import com.android.shaftschematic.model.withKeyways90Cw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutual exclusion of the two keyway-clocking drawing notes, exercised through the pure spec
 * transforms the ViewModel setters delegate to (`setKeyways180Apart` / `setKeyways90Apart` /
 * `setKeyways90Cw` are one-line `_spec.update { … }` wrappers around these).
 *
 * The setters carry no logic of their own, so this class exercises the pure transforms directly
 * rather than instantiating the AndroidViewModel around them.
 */
class ShaftViewModelKeywayClockingTest {

    // Mirrors ShaftViewModel.setKeyways180Apart / setKeyways90Apart / setKeyways90Cw.
    private fun setKeyways180Apart(spec: ShaftSpec, enabled: Boolean) = spec.withKeyways180Apart(enabled)
    private fun setKeyways90Apart(spec: ShaftSpec, enabled: Boolean) = spec.withKeyways90Apart(enabled)
    private fun setKeyways90Cw(spec: ShaftSpec, cw: Boolean) = spec.withKeyways90Cw(cw)

    @Test
    fun `enabling 90 clears 180`() {
        val start = ShaftSpec(overallLengthMm = 1000f, keyways180Apart = true)

        val result = setKeyways90Apart(start, true)

        assertTrue(result.keyways90Apart)
        assertFalse(result.keyways180Apart)
        assertEquals(KeywayClocking.DEG_90_CW, result.keywayClocking())
    }

    @Test
    fun `enabling 180 clears 90`() {
        val start = ShaftSpec(overallLengthMm = 1000f, keyways90Apart = true, keyways90Cw = false)

        val result = setKeyways180Apart(start, true)

        assertTrue(result.keyways180Apart)
        assertFalse(result.keyways90Apart)
        assertEquals(KeywayClocking.DEG_180, result.keywayClocking())
    }

    @Test
    fun `setKeyways90Cw round-trips the direction`() {
        val on = setKeyways90Apart(ShaftSpec(overallLengthMm = 1000f), true)

        val ccw = setKeyways90Cw(on, false)
        assertFalse(ccw.keyways90Cw)
        assertEquals(KeywayClocking.DEG_90_CCW, ccw.keywayClocking())

        val cw = setKeyways90Cw(ccw, true)
        assertTrue(cw.keyways90Cw)
        assertEquals(KeywayClocking.DEG_90_CW, cw.keywayClocking())
    }

    @Test
    fun `setting an unchanged value is a no-op`() {
        // No-op guard: the update lambda returns the same instance, so no state is emitted and
        // the document is not marked dirty.
        val spec = ShaftSpec(overallLengthMm = 1000f, keyways90Apart = true, keyways90Cw = true)

        assertTrue(spec === setKeyways90Apart(spec, true))
        assertTrue(spec === setKeyways90Cw(spec, true))
        assertTrue(spec === setKeyways180Apart(spec, false))
    }

    @Test
    fun `turning both notes off leaves no clocking`() {
        val spec = setKeyways90Apart(ShaftSpec(overallLengthMm = 1000f, keyways180Apart = true), true)

        val cleared = setKeyways90Apart(spec, false)

        assertFalse(cleared.keyways90Apart)
        assertFalse(cleared.keyways180Apart)
        assertEquals(KeywayClocking.NONE, cleared.keywayClocking())
    }

    @Test
    fun `clocking setters never touch geometry`() {
        val spec = ShaftSpec(overallLengthMm = 1234.5f)

        val result = setKeyways90Cw(setKeyways90Apart(spec, true), false)

        assertEquals(1234.5f, result.overallLengthMm, 0f)
        assertEquals(spec.copy(keyways90Apart = true, keyways90Cw = false), result)
    }
}
