package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.withAutoBlend
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ───────── turning a face back off ─────────

    /**
     * Picking **Square** must actually clear the face — the chips are the only enable/disable
     * control, so a mode that cannot be switched off would strand a body with a permanent seal
     * area. Mirrors what `updateBodyBlend` does inside `_spec.update {}`.
     */
    @Test
    fun `square clears an explicit face without touching the other one`() {
        val sealed = Body(
            id = "b", startFromAftMm = 0f, lengthMm = 800f, diaMm = 200f,
            blendAftMm = 50f, blendAftSeal = true,
            blendFwdMm = 60f, blendFwdSeal = true,
        )
        val mode = BlendFaceMode.SQUARE
        val cleared = sealed.copy(
            blendAftMm = blendLenForMode(mode, sealed.blendAftMm, sealed.lengthMm),
            blendAftSeal = mode == BlendFaceMode.SEAL,
        )

        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(cleared.blendAftMm, cleared.blendAftSeal))
        assertEquals(0f, cleared.blendAftMm, eps)
        assertTrue("the seal flag must not survive the clear", !cleared.blendAftSeal)
        // The FWD face is untouched.
        assertEquals(BlendFaceMode.SEAL, blendFaceMode(cleared.blendFwdMm, cleared.blendFwdSeal))
        assertEquals(60f, cleared.blendFwdMm, eps)
    }

    /** The auto-span mirror: Square drops the anchor rather than storing a zero-length one. */
    @Test
    fun `square drops an auto span's anchor without touching the other face`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 50f, BlendProfile.OGEE, seal = true)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.FWD, 60f, BlendProfile.OGEE, seal = true)
        assertEquals(2, spec.autoBlends.size)

        val mode = BlendFaceMode.SQUARE
        val cleared = spec.withAutoBlend(
            200f, 600f, LinerAuthoredReference.AFT,
            blendLenForMode(mode, 50f, bodyLengthMm = 400f),
            BlendProfile.OGEE, seal = mode == BlendFaceMode.SEAL,
        )

        assertNull(cleared.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT))
        assertEquals(1, cleared.autoBlends.size)
        assertEquals(
            60f,
            cleared.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.FWD)!!.lengthMm,
            eps,
        )
    }

    /** Square → Blend → Square returns to exactly the starting state, so the toggle is lossless. */
    @Test
    fun `a face round-trips off, on, and off again`() {
        var len = 0f
        var seal = false
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(len, seal))

        len = blendLenForMode(BlendFaceMode.SEAL, len, bodyLengthMm = 800f); seal = true
        assertEquals(BlendFaceMode.SEAL, blendFaceMode(len, seal))

        len = blendLenForMode(BlendFaceMode.SQUARE, len, bodyLengthMm = 800f); seal = false
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(len, seal))
        assertEquals(0f, len, eps)
    }
}
