package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `suggestedBodyKeywayEnd` — the seed for a NEW body keyway's AFT/FWD chips: opposite the
 * shaft's existing keyway when exactly one side is taken, AFT (the model default) otherwise.
 * A suggestion only — nothing stored ever moves through it, and the user's chip choice wins
 * (on-device report: with an aft taper keyway on the shaft, a new body keyway defaulting to
 * AFT read as a second aft keyway).
 */
class SuggestedBodyKeywayEndTest {

    private fun keyedTaper(startMm: Float, lengthMm: Float = 200f) = Taper(
        id = "t-$startMm", startFromAftMm = startMm, lengthMm = lengthMm,
        startDiaMm = 80f, endDiaMm = 60f,
        keywayWidthMm = 20f, keywayDepthMm = 8f, keywayLengthMm = 100f,
    )

    private fun keyedBody(id: String, startMm: Float, end: LinerAuthoredReference) = Body(
        id = id, startFromAftMm = startMm, lengthMm = 300f, diaMm = 90f,
        keywayWidthMm = 20f, keywayDepthMm = 8f, keywayLengthMm = 100f, keywayEnd = end,
    )

    @Test
    fun `no existing keyway defaults AFT`() {
        val spec = ShaftSpec(overallLengthMm = 2000f)
        assertEquals(LinerAuthoredReference.AFT, spec.suggestedBodyKeywayEnd())
    }

    @Test
    fun `an aft taper keyway suggests FWD`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, tapers = listOf(keyedTaper(0f)))
        assertEquals(LinerAuthoredReference.FWD, spec.suggestedBodyKeywayEnd())
    }

    @Test
    fun `a fwd taper keyway suggests AFT`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, tapers = listOf(keyedTaper(1800f)))
        assertEquals(LinerAuthoredReference.AFT, spec.suggestedBodyKeywayEnd())
    }

    @Test
    fun `keyways at both ends fall back to AFT`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(keyedTaper(0f), keyedTaper(1800f)),
        )
        assertEquals(LinerAuthoredReference.AFT, spec.suggestedBodyKeywayEnd())
    }

    @Test
    fun `an existing aft body keyway suggests FWD`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("b1", 0f, LinerAuthoredReference.AFT)),
        )
        assertEquals(LinerAuthoredReference.FWD, spec.suggestedBodyKeywayEnd())
    }

    /** The body being edited never votes with its own keyway. */
    @Test
    fun `the edited body's own keyway is excluded`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("b1", 0f, LinerAuthoredReference.AFT)),
        )
        assertEquals(LinerAuthoredReference.AFT, spec.suggestedBodyKeywayEnd(excludeBodyId = "b1"))
    }

    /** A taper without a keyway takes no side. */
    @Test
    fun `an unkeyed taper does not vote`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)
            ),
        )
        assertEquals(LinerAuthoredReference.AFT, spec.suggestedBodyKeywayEnd())
    }

    /** Auto OAL: sides classify against the content end when no manual OAL is set. */
    @Test
    fun `auto OAL uses the content end`() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            bodies = listOf(
                keyedBody("b1", 0f, LinerAuthoredReference.AFT),
                Body(id = "b2", startFromAftMm = 300f, lengthMm = 1700f, diaMm = 90f),
            ),
        )
        assertEquals(LinerAuthoredReference.FWD, spec.suggestedBodyKeywayEnd())
    }

    @Test
    fun `empty shaft defaults AFT`() {
        assertEquals(LinerAuthoredReference.AFT, ShaftSpec(overallLengthMm = 0f).suggestedBodyKeywayEnd())
    }
}
