package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "Captured keyway" toggle's inset rule. A keyway is captured exactly when its inset is > 0,
 * so the toggle commits an inset: OFF clears it, ON keeps a typed inset, else restores the last
 * one typed this card session, else seeds the 0.5 in preset.
 */
class CapturedKeywayTest {

    private val eps = 1e-4f

    @Test
    fun `ON with a typed inset keeps it`() {
        assertEquals(20f, keywayInsetForCaptured(on = true, currentMm = 20f, rememberedMm = 35f), eps)
    }

    @Test
    fun `ON with no inset restores the remembered one`() {
        assertEquals(35f, keywayInsetForCaptured(on = true, currentMm = 0f, rememberedMm = 35f), eps)
    }

    @Test
    fun `ON with neither seeds the half-inch preset`() {
        assertEquals(12.7f, keywayInsetForCaptured(on = true, currentMm = 0f, rememberedMm = 0f), eps)
        assertEquals(12.7f, defaultKeywayInsetMm(), eps)
    }

    @Test
    fun `OFF clears the inset`() {
        assertEquals(0f, keywayInsetForCaptured(on = false, currentMm = 20f, rememberedMm = 35f), eps)
        assertEquals(0f, keywayInsetForCaptured(on = false, currentMm = 0f, rememberedMm = 0f), eps)
    }

    @Test
    fun `on, type 20, off, on restores 20`() {
        // Mirrors the card: the remembered value captures what the OFF toggle clears.
        var stored = keywayInsetForCaptured(on = true, currentMm = 0f, rememberedMm = 0f)
        assertEquals(12.7f, stored, eps)
        stored = 20f  // typed
        val remembered = stored
        stored = keywayInsetForCaptured(on = false, currentMm = stored, rememberedMm = remembered)
        assertEquals(0f, stored, eps)
        stored = keywayInsetForCaptured(on = true, currentMm = stored, rememberedMm = remembered)
        assertEquals(20f, stored, eps)
    }
}
