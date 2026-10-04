package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The thread relief's seeds and switch decisions — the pure helpers both authoring surfaces route
 * through. `AddThreadDialog` cannot render under Robolectric (an `AlertDialog` window never
 * settles; see `KeywaySectionTest`), so its side of the parity is pinned here: the dialog seeds
 * through [threadReliefForSwitch] and submits through [threadReliefSubmitValues].
 */
class ThreadReliefSeedTest {

    private val eps = 1e-3f
    private val pitch4Tpi = 25.4f / 4f

    @Test
    fun `length seeds at one and a half pitches`() {
        assertEquals(0.375f * 25.4f, defaultThreadReliefLenMm(pitch4Tpi), eps)
    }

    @Test
    fun `length seeds from the hatch fallback pitch when the thread has none`() {
        assertEquals(1.5f * 2.5f, defaultThreadReliefLenMm(0f), eps)
    }

    @Test
    fun `diameter seeds just under the root`() {
        val major = 4.5f * 25.4f
        assertEquals(major - 1.3f * pitch4Tpi, defaultThreadReliefDiaMm(major, pitch4Tpi), eps)
    }

    @Test
    fun `diameter has nothing to seed from without a major diameter`() {
        assertEquals(0f, defaultThreadReliefDiaMm(0f, pitch4Tpi), 0f)
        assertEquals(0f, defaultThreadReliefDiaMm(-1f, pitch4Tpi), 0f)
        // A tiny thread with a coarse pitch never seeds a negative Ø.
        assertEquals(0f, defaultThreadReliefDiaMm(5f, pitch4Tpi), 0f)
    }

    @Test
    fun `switching on with nothing stored or remembered seeds the presets`() {
        val v = threadReliefForSwitch(true, 0f, 0f, 0f, 0f, majorDiaMm = 114.3f, pitchMm = pitch4Tpi)
        assertEquals(defaultThreadReliefLenMm(pitch4Tpi), v.lenMm, eps)
        assertEquals(defaultThreadReliefDiaMm(114.3f, pitch4Tpi), v.diaMm, eps)
    }

    @Test
    fun `switching on restores the remembered values`() {
        val v = threadReliefForSwitch(true, 0f, 0f, 12f, 100f, majorDiaMm = 114.3f, pitchMm = pitch4Tpi)
        assertEquals(12f, v.lenMm, 0f)
        assertEquals(100f, v.diaMm, 0f)
    }

    @Test
    fun `switching on keeps a stored value verbatim`() {
        val v = threadReliefForSwitch(true, 9.123f, 0f, 12f, 100f, majorDiaMm = 114.3f, pitchMm = pitch4Tpi)
        assertEquals(9.123f, v.lenMm, 0f)
        assertEquals(100f, v.diaMm, 0f)
    }

    @Test
    fun `switching off clears both`() {
        val v = threadReliefForSwitch(false, 9f, 100f, 12f, 100f, majorDiaMm = 114.3f, pitchMm = pitch4Tpi)
        assertEquals(ThreadReliefValues(0f, 0f), v)
    }

    @Test
    fun `the dialog submits nothing while its switch is off`() {
        assertEquals(ThreadReliefValues(0f, 0f), threadReliefSubmitValues(false, 10f, 100f))
    }

    @Test
    fun `the dialog submits its typed values verbatim while on`() {
        assertEquals(ThreadReliefValues(10.001f, 100.5f), threadReliefSubmitValues(true, 10.001f, 100.5f))
        assertEquals(ThreadReliefValues(0f, 100.5f), threadReliefSubmitValues(true, null, 100.5f))
        assertEquals(ThreadReliefValues(0f, 0f), threadReliefSubmitValues(true, -3f, null))
    }
}
