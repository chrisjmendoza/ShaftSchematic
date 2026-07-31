package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keyway clocking classification. The aft-most keyway (smallest absolute center — the shop's
 * measurement datum) stays primary; every other keyway is secondary. At 180° a secondary
 * renders hidden (far-side, dashed); at 90° it renders as a silhouette-edge notch, so
 * `hiddenKeywayHostIds()` must stay empty there. Only applies with ≥ 2 keyways.
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

    // ── defaults ─────────────────────────────────────────────────────────────

    @Test fun `a fresh spec has no clocking note and defaults to clockwise`() {
        val spec = ShaftSpec()
        assertFalse(spec.keyways180Apart)
        assertFalse(spec.keyways90Apart)
        assertTrue(spec.keyways90Cw)
        assertEquals(KeywayClocking.NONE, spec.keywayClocking())
    }

    // ── keywayClocking precedence ────────────────────────────────────────────

    @Test fun `90 apart resolves by direction flag`() {
        val cw = ShaftSpec(keyways90Apart = true, keyways90Cw = true)
        val ccw = ShaftSpec(keyways90Apart = true, keyways90Cw = false)
        assertEquals(KeywayClocking.DEG_90_CW, cw.keywayClocking())
        assertEquals(KeywayClocking.DEG_90_CCW, ccw.keywayClocking())
    }

    @Test fun `180 wins when a hand-edited document sets both flags`() {
        val spec = ShaftSpec(keyways180Apart = true, keyways90Apart = true, keyways90Cw = false)
        assertEquals(KeywayClocking.DEG_180, spec.keywayClocking())
    }

    @Test fun `direction flag alone is inert without the 90 note`() {
        assertEquals(KeywayClocking.NONE, ShaftSpec(keyways90Cw = false).keywayClocking())
    }

    // ── secondaryKeywayHostIds ───────────────────────────────────────────────

    @Test fun `no secondaries without a clocking note`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f), keyedBody("fwd", 1600f)),
        )
        assertTrue(spec.secondaryKeywayHostIds().isEmpty())
    }

    @Test fun `no secondaries with only one keyway at 90 apart`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f)),
            keyways90Apart = true,
        )
        assertTrue(spec.secondaryKeywayHostIds().isEmpty())
    }

    @Test fun `90 apart uses the same aft-most-primary rule as 180`() {
        val bodies = listOf(keyedBody("fwd", 1600f), keyedBody("aft", 0f))
        val cw = ShaftSpec(overallLengthMm = 2000f, bodies = bodies, keyways90Apart = true, keyways90Cw = true)
        val ccw = cw.copy(keyways90Cw = false)
        val deg180 = ShaftSpec(overallLengthMm = 2000f, bodies = bodies, keyways180Apart = true)

        assertEquals(setOf("fwd"), cw.secondaryKeywayHostIds())
        assertEquals(setOf("fwd"), ccw.secondaryKeywayHostIds())
        assertEquals(deg180.secondaryKeywayHostIds(), cw.secondaryKeywayHostIds())
    }

    @Test fun `90 apart marks every keyway but the aft-most as secondary`() {
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            tapers = listOf(keyedTaper("taper", 100f)),   // center ~150
            bodies = listOf(keyedBody("mid", 1300f), keyedBody("fwd", 2600f)),
            keyways90Apart = true,
        )
        assertEquals(setOf("mid", "fwd"), spec.secondaryKeywayHostIds())
    }

    @Test fun `at 180 the secondaries are exactly the hidden hosts`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(keyedBody("aft", 0f), keyedBody("fwd", 1600f)),
            keyways180Apart = true,
        )
        assertEquals(spec.secondaryKeywayHostIds(), spec.hiddenKeywayHostIds())
    }

    // ── hiddenKeywayHostIds is 180-only ──────────────────────────────────────

    @Test fun `no hidden ids in 90 apart modes`() {
        val bodies = listOf(keyedBody("aft", 0f), keyedBody("fwd", 1600f))
        val cw = ShaftSpec(overallLengthMm = 2000f, bodies = bodies, keyways90Apart = true, keyways90Cw = true)
        val ccw = cw.copy(keyways90Cw = false)

        // 90° secondaries render as silhouette notches, never hidden-dashed.
        assertTrue(cw.hiddenKeywayHostIds().isEmpty())
        assertTrue(ccw.hiddenKeywayHostIds().isEmpty())
        assertTrue(cw.secondaryKeywayHostIds().isNotEmpty())
    }

    // ── mutual exclusion helpers (the ViewModel setters delegate here) ────────

    @Test fun `enabling 180 clears 90 and vice versa`() {
        val with90 = ShaftSpec().withKeyways90Apart(true)
        assertTrue(with90.keyways90Apart)
        assertFalse(with90.keyways180Apart)

        val with180 = with90.withKeyways180Apart(true)
        assertTrue(with180.keyways180Apart)
        assertFalse(with180.keyways90Apart)

        val back90 = with180.withKeyways90Apart(true)
        assertTrue(back90.keyways90Apart)
        assertFalse(back90.keyways180Apart)
    }

    @Test fun `unchanged clocking sets return the same instance`() {
        val spec = ShaftSpec(keyways90Apart = true, keyways90Cw = false)
        assertTrue(spec === spec.withKeyways90Apart(true))
        assertTrue(spec === spec.withKeyways90Cw(false))
        assertTrue(spec === spec.withKeyways180Apart(false))
    }

    @Test fun `disabling one note leaves the other alone`() {
        val spec = ShaftSpec(keyways180Apart = true).withKeyways90Apart(false)
        assertTrue(spec.keyways180Apart)
        assertFalse(spec.keyways90Apart)
    }

    @Test fun `direction survives toggling the 90 note off and back on`() {
        val ccw = ShaftSpec().withKeyways90Apart(true).withKeyways90Cw(false)
        val cycled = ccw.withKeyways90Apart(false).withKeyways90Apart(true)
        assertFalse(cycled.keyways90Cw)
        assertEquals(KeywayClocking.DEG_90_CCW, cycled.keywayClocking())
    }
}
