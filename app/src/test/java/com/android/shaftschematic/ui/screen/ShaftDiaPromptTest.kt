package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldPromptShaftDia] — the first OAL commit asks for the bare-shaft Ø only on a brand-new
 * shaft (on-device request); a loaded document or template, or a shaft whose Ø is already set,
 * never prompts.
 */
class ShaftDiaPromptTest {

    @Test
    fun `a fresh spec prompts`() {
        assertTrue(shouldPromptShaftDia(ShaftSpec()))
    }

    @Test
    fun `a spec with an OAL already set does not prompt`() {
        assertFalse(shouldPromptShaftDia(ShaftSpec(overallLengthMm = 1000f)))
    }

    @Test
    fun `a spec with any component does not prompt`() {
        listOf(
            ShaftSpec(bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f))),
            ShaftSpec(tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f))),
            ShaftSpec(threads = listOf(Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 3f))),
            ShaftSpec(liners = listOf(Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = 60f))),
            ShaftSpec(couplerBoltSlots = listOf(CouplerBoltSlot())),
        ).forEach { spec ->
            assertFalse("prompted with a component present: $spec", shouldPromptShaftDia(spec))
        }
    }

    @Test
    fun `a spec with the shaft-wide bare-shaft Ø set does not prompt`() {
        assertFalse(shouldPromptShaftDia(ShaftSpec(autoBodyDiaMm = 101.6f)))
    }
}
