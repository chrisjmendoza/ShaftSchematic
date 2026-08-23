package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.ui.config.AddDefaultsConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The face-finish chips project a stored `(length, seal)` pair onto three mutually exclusive
 * modes and back. Pins the round trip and the one behaviour a user would notice if it broke:
 * switching Blend ↔ Seal area must keep the typed length, since the two differ only by the cuts.
 */
class BlendFaceModeTest {

    private val eps = 1e-3f
    private val preset = AddDefaultsConfig.BLEND_LEN_IN * 25.4f

    @Test
    fun `stored values read back as the mode that produced them`() {
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(0f, seal = false))
        assertEquals(BlendFaceMode.BLEND, blendFaceMode(50f, seal = false))
        assertEquals(BlendFaceMode.SEAL, blendFaceMode(50f, seal = true))
    }

    /** A seal flag on a face with no blend is not a seal area — the cuts need a blend to sit on. */
    @Test
    fun `a stray seal flag without a length still reads square`() {
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(0f, seal = true))
    }

    @Test
    fun `switching between blend and seal keeps the typed length`() {
        val typed = 33.3f
        assertEquals(typed, blendLenForMode(BlendFaceMode.SEAL, typed, bodyLengthMm = 800f), eps)
        assertEquals(typed, blendLenForMode(BlendFaceMode.BLEND, typed, bodyLengthMm = 800f), eps)
    }

    @Test
    fun `leaving square offers the starting preset, and returning clears`() {
        assertEquals(preset, blendLenForMode(BlendFaceMode.BLEND, 0f, bodyLengthMm = 800f), eps)
        assertEquals(preset, blendLenForMode(BlendFaceMode.SEAL, 0f, bodyLengthMm = 800f), eps)
        assertEquals(0f, blendLenForMode(BlendFaceMode.SQUARE, 99f, bodyLengthMm = 800f), eps)
    }

    /** A body too short for the preset takes a quarter of itself instead. */
    @Test
    fun `a short body gets a proportional starting length`() {
        assertEquals(20f, blendLenForMode(BlendFaceMode.BLEND, 0f, bodyLengthMm = 80f), eps)
    }

    /** Every mode survives a round trip through the stored pair. */
    @Test
    fun `mode round-trips through the stored pair`() {
        for (mode in BlendFaceMode.values()) {
            val len = blendLenForMode(mode, currentMm = 0f, bodyLengthMm = 800f)
            assertEquals(mode, blendFaceMode(len, seal = mode == BlendFaceMode.SEAL))
        }
    }
}
