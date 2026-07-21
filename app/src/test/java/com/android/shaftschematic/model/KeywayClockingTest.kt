package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hidden-line classification for keyways clocked 180° apart. The aft-most keyway (smallest
 * absolute center — the shop's measurement datum) stays solid; every other keyway is
 * far-side and renders hidden. Only applies when `keyways180Apart` and ≥ 2 keyways.
 */
class KeywayClockingTest {

    private fun keyedBody(
        id: String, startMm: Float, lengthMm: Float = 400f,
        kwLength: Float = 200f, kwOffset: Float = 0f,
        kwEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
    ) = Body(
        id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = 120f,
        keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = kwLength,
        keywayOffsetFromEndMm = kwOffset, keywayEnd = kwEnd,
    )

    private fun keyedTaper(
        id: String, startMm: Float, lengthMm: Float = 200f,
        startDia: Float = 100f, endDia: Float = 80f,
    ) = Taper(
        id = id, startFromAftMm = startMm, lengthMm = lengthMm,
        startDiaMm = startDia, endDiaMm = endDia,
        keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 100f,
    )

    // ── taper keyway absolute span ───────────────────────────────────────────

    @Test fun `taper keyway abs span, SET at start`() {
        // SET at start (startDia < endDia): keyway from SET face + offset, toward LET.
        val t = Taper(startFromAftMm = 100f, lengthMm = 300f, startDiaMm = 80f, endDiaMm = 120f,
            keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 150f, keywayOffsetFromSetMm = 25f)
        val (lo, hi) = t.keywayAbsSpanMm()!!
        assertEquals(125f, lo, 1e-3f)  // 100 + 25
        assertEquals(275f, hi, 1e-3f)  // + 150
    }

    @Test fun `taper keyway abs span, SET at fwd end`() {
        // SET at end (startDia > endDia): SET face is at start+length, keyway runs toward AFT.
        val t = Taper(startFromAftMm = 100f, lengthMm = 300f, startDiaMm = 120f, endDiaMm = 80f,
            keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 150f, keywayOffsetFromSetMm = 25f)
        val (lo, hi) = t.keywayAbsSpanMm()!!
        // SET face at 400; offset 25 toward AFT → near 375; length 150 → far 225.
        assertEquals(225f, lo, 1e-3f)
        assertEquals(375f, hi, 1e-3f)
    }

    // ── hiddenKeywayHostIds ──────────────────────────────────────────────────

    @Test fun `no hidden ids when flag is off`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f), keyedBody("fwd", 1600f)),
            keyways180Apart = false,
        )
        assertTrue(spec.hiddenKeywayHostIds().isEmpty())
    }

    @Test fun `no hidden ids with only one keyway`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f)),
            keyways180Apart = true,
        )
        assertTrue(spec.hiddenKeywayHostIds().isEmpty())
    }

    @Test fun `fwd body keyway is hidden, aft stays solid`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f), keyedBody("fwd", 1600f)),
            keyways180Apart = true,
        )
        assertEquals(setOf("fwd"), spec.hiddenKeywayHostIds())
    }

    @Test fun `aft-most is chosen by center, not list order`() {
        // fwd body listed first; classification must still keep the aft-most solid.
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("fwd", 1600f), keyedBody("aft", 0f)),
            keyways180Apart = true,
        )
        assertEquals(setOf("fwd"), spec.hiddenKeywayHostIds())
    }

    @Test fun `mixed taper aft plus body fwd hides the body`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(keyedTaper("taper", 100f)),   // center ~150
            bodies = listOf(keyedBody("body", 1600f)),    // center ~1700
            keyways180Apart = true,
        )
        assertEquals(setOf("body"), spec.hiddenKeywayHostIds())
    }

    @Test fun `three keyways hide all but the aft-most`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            bodies = listOf(
                keyedBody("aft", 0f),
                keyedBody("mid", 1300f),
                keyedBody("fwd", 2600f),
            ),
            keyways180Apart = true,
        )
        assertEquals(setOf("mid", "fwd"), spec.hiddenKeywayHostIds())
    }
}
