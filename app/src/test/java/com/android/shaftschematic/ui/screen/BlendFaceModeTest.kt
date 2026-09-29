package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.withAutoBlend
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The face-finish chips project a stored blend length onto Square | Blend and back; the seal
 * area is a separate per-face toggle with its own length. Pins the round trips and the
 * independence a user would notice if it broke: a finish change never touches the seal flag, and
 * a seal toggle never touches the blend length.
 */
class BlendFaceModeTest {

    private val eps = 1e-3f
    private val preset = AddDefaultsConfig.BLEND_LEN_IN * 25.4f

    @Test
    fun `stored values read back as the mode that produced them`() {
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(0f))
        assertEquals(BlendFaceMode.BLEND, blendFaceMode(50f))
    }

    @Test
    fun `re-picking blend keeps the typed length`() {
        val typed = 33.3f
        assertEquals(typed, blendLenForMode(BlendFaceMode.BLEND, typed, bodyLengthMm = 800f), eps)
    }

    @Test
    fun `leaving square offers the starting preset, and returning clears`() {
        assertEquals(preset, blendLenForMode(BlendFaceMode.BLEND, 0f, bodyLengthMm = 800f), eps)
        assertEquals(0f, blendLenForMode(BlendFaceMode.SQUARE, 99f, bodyLengthMm = 800f), eps)
    }

    /** A body too short for the preset takes a quarter of itself instead. */
    @Test
    fun `a short body gets a proportional starting length`() {
        assertEquals(20f, blendLenForMode(BlendFaceMode.BLEND, 0f, bodyLengthMm = 80f), eps)
    }

    /** Every mode survives a round trip through the stored length. */
    @Test
    fun `mode round-trips through the stored length`() {
        for (mode in BlendFaceMode.values()) {
            val len = blendLenForMode(mode, currentMm = 0f, bodyLengthMm = 800f)
            assertEquals(mode, blendFaceMode(len))
        }
    }

    // ───────── turning a face back off ─────────

    /**
     * Picking **Square** must actually clear the blend — the chips are the only enable/disable
     * control for the shoulder. It must NOT clear the seal: a seal area is a section property,
     * so a sealed face picked Square keeps its grooves (from the face). Mirrors what the
     * explicit card hands `updateBodyBlend`.
     */
    @Test
    fun `square clears an explicit face's blend but keeps its seal, other face untouched`() {
        val sealed = Body(
            id = "b", startFromAftMm = 0f, lengthMm = 800f, diaMm = 200f,
            blendAftMm = 50f, blendAftSeal = true, blendAftSealLenMm = 90f,
            blendFwdMm = 60f, blendFwdSeal = true,
        )
        val cleared = sealed.copy(
            blendAftMm = blendLenForMode(BlendFaceMode.SQUARE, sealed.blendAftMm, sealed.lengthMm),
        )

        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(cleared.blendAftMm))
        assertEquals(0f, cleared.blendAftMm, eps)
        assertTrue("the seal flag survives a finish change", cleared.blendAftSeal)
        assertEquals(90f, cleared.blendAftSealLenMm, 0f)
        // The FWD face is untouched.
        assertEquals(BlendFaceMode.BLEND, blendFaceMode(cleared.blendFwdMm))
        assertEquals(60f, cleared.blendFwdMm, eps)
        assertTrue(cleared.blendFwdSeal)
    }

    /** The auto-span mirror: Square on an UNSEALED face drops the anchor, not a zero-length one. */
    @Test
    fun `square drops an unsealed auto span's anchor without touching the other face`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 50f, BlendProfile.OGEE, seal = false)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.FWD, 60f, BlendProfile.OGEE, seal = true)
        assertEquals(2, spec.autoBlends.size)

        val aft = spec.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!
        val cleared = spec.withAutoBlend(
            200f, 600f, LinerAuthoredReference.AFT,
            blendLenForMode(BlendFaceMode.SQUARE, aft.lengthMm, bodyLengthMm = 400f),
            BlendProfile.OGEE, aft.seal, aft.sealLenMm,
        )

        assertNull(cleared.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT))
        assertEquals(1, cleared.autoBlends.size)
        assertEquals(
            60f,
            cleared.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.FWD)!!.lengthMm,
            eps,
        )
    }

    /** Square on a SEALED auto face keeps the anchor: length 0, seal still on. */
    @Test
    fun `square on a sealed auto face keeps the anchor as seal-only`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 50f, BlendProfile.OGEE,
                seal = true, sealLenMm = 80f)
        val aft = spec.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!

        val squared = spec.withAutoBlend(
            200f, 600f, LinerAuthoredReference.AFT,
            blendLenForMode(BlendFaceMode.SQUARE, aft.lengthMm, bodyLengthMm = 400f),
            BlendProfile.OGEE, aft.seal, aft.sealLenMm,
        )

        val kept = squared.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)
        assertNotNull("a sealed face keeps its anchor when squared", kept)
        assertEquals(0f, kept!!.lengthMm, 0f)
        assertTrue(kept.seal)
        assertEquals(80f, kept.sealLenMm, 0f)
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(kept.lengthMm))
    }

    /** Unticking the seal on a square auto face leaves nothing to store — the anchor goes. */
    @Test
    fun `unticking the seal on a square auto face drops the anchor`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 0f, BlendProfile.OGEE,
                seal = true, sealLenMm = 80f)
        assertEquals(1, spec.autoBlends.size)
        val aft = spec.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!

        val off = spec.withAutoBlend(
            200f, 600f, LinerAuthoredReference.AFT, aft.lengthMm, BlendProfile.OGEE,
            seal = false, sealLenMm = sealLenForSeal(false, aft.sealLenMm, bodyLengthMm = 400f),
        )
        assertTrue(off.autoBlends.isEmpty())
    }

    /** Unticking the seal on a BLENDED auto face keeps the blend. */
    @Test
    fun `unticking the seal on a blended auto face keeps the blend`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 50f, BlendProfile.OGEE,
                seal = true, sealLenMm = 80f)
        val off = spec.withAutoBlend(
            200f, 600f, LinerAuthoredReference.AFT, 50f, BlendProfile.OGEE,
            seal = false, sealLenMm = sealLenForSeal(false, 80f, bodyLengthMm = 400f),
        )
        val kept = off.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!
        assertEquals(50f, kept.lengthMm, 0f)
        assertTrue(!kept.seal)
        assertEquals("the typed seal length survives the untick", 80f, kept.sealLenMm, 0f)
    }

    /** Square → Blend → Square returns to exactly the starting state, so the toggle is lossless. */
    @Test
    fun `a face round-trips off, on, and off again`() {
        var len = 0f
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(len))

        len = blendLenForMode(BlendFaceMode.BLEND, len, bodyLengthMm = 800f)
        assertEquals(BlendFaceMode.BLEND, blendFaceMode(len))

        len = blendLenForMode(BlendFaceMode.SQUARE, len, bodyLengthMm = 800f)
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(len))
        assertEquals(0f, len, eps)
    }

    // ───────── seal-area length ─────────

    private val sealPreset = AddDefaultsConfig.SEAL_LEN_IN * 25.4f

    @Test
    fun `the seal preset is 4 in, or a quarter of a short body`() {
        assertEquals(101.6f, sealPreset, eps)
        assertEquals(sealPreset, defaultSealLenMm(bodyLengthMm = 800f), eps)
        assertEquals(50f, defaultSealLenMm(bodyLengthMm = 200f), eps)
        // Unknown body length (dialog with a blank Length field): the plain preset.
        assertEquals(sealPreset, defaultSealLenMm(bodyLengthMm = -1f), eps)
    }

    @Test
    fun `ticking the seal seeds an empty seal length and keeps a typed one`() {
        assertEquals(sealPreset, sealLenForSeal(true, 0f, bodyLengthMm = 800f), eps)
        assertEquals(123.456f, sealLenForSeal(true, 123.456f, bodyLengthMm = 800f), 0f)
    }

    /**
     * The seal length is stored independently of the seal flag: unticking must pass it through
     * untouched — never zero it, never seed it — so ticking again restores the typed value.
     */
    @Test
    fun `unticking the seal keeps the typed seal length untouched`() {
        val typed = 123.456f
        assertEquals(typed, sealLenForSeal(false, typed, bodyLengthMm = 800f), 0f)
        assertEquals(0f, sealLenForSeal(false, 0f, bodyLengthMm = 800f), 0f)
    }

    @Test
    fun `seal off then on round-trips the typed seal length`() {
        var sealLen = sealLenForSeal(true, 0f, bodyLengthMm = 800f)
        sealLen = 76.2f // typed
        sealLen = sealLenForSeal(false, sealLen, bodyLengthMm = 800f)
        sealLen = sealLenForSeal(true, sealLen, bodyLengthMm = 800f)
        assertEquals(76.2f, sealLen, 0f)
    }

    /** A finish change never reads or writes the seal pair, and a seal toggle never the length. */
    @Test
    fun `finish and seal are independent`() {
        // Blend → Square with the seal on: blend length cleared, the seal pair not an input.
        assertEquals(0f, blendLenForMode(BlendFaceMode.SQUARE, 50f, bodyLengthMm = 800f), 0f)
        // Seal on at a square face: the seal length seeds, and there is no blend length involved.
        assertEquals(sealPreset, sealLenForSeal(true, 0f, bodyLengthMm = 800f), eps)
        assertEquals(BlendFaceMode.SQUARE, blendFaceMode(0f))
    }
}
