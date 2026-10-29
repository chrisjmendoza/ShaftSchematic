package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── The prompt says why Set is disabled ────────────────────────────────────

    @Test
    fun `the prompt names an entry it cannot read`() {
        assertEquals(NOT_A_NUMBER_ERROR, shaftDiaPromptError("10-1/4x", UnitSystem.INCHES))
        assertEquals(NOT_A_NUMBER_ERROR, shaftDiaPromptError("1,500", UnitSystem.INCHES))
        assertEquals(SHAFT_DIA_NOT_POSITIVE, shaftDiaPromptError("0", UnitSystem.INCHES))
        assertNull(shaftDiaPromptError("", UnitSystem.INCHES))
    }

    @Test
    fun `the prompt reads hyphenated fractions and unit suffixes`() {
        for (typed in listOf("10-1/4", "10-1/4\"", "10 1/4", "260mm", "10,25")) {
            assertNull("$typed must read", shaftDiaPromptError(typed, UnitSystem.INCHES))
        }
        // 260mm in an inch document is 260 mm, not 260 inches.
        assertEquals(260f, toMmOrNull("260mm", UnitSystem.INCHES)!!, 1e-4f)
    }
}
