package com.android.shaftschematic.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `hasAnyKeywayValue` — the seed for a card's Keyway section gate. Unlike [hasKeyway] (all three
 * of W/D/L), ANY one typed dimension counts: a half-typed keyway is authored work and its section
 * must stay open so the typed values stay on screen.
 */
class KeywayAnyValueTest {

    // ── Taper ────────────────────────────────────────────────────────────────────

    @Test fun `taper with all zeros has no keyway value`() {
        assertFalse(Taper(lengthMm = 200f).hasAnyKeywayValue)
    }

    @Test fun `taper with only W has a keyway value`() {
        val t = Taper(lengthMm = 200f, keywayWidthMm = 6f)
        assertTrue(t.hasAnyKeywayValue)
        assertFalse(t.hasKeyway)
    }

    @Test fun `taper with only L has a keyway value`() {
        val t = Taper(lengthMm = 200f, keywayLengthMm = 40f)
        assertTrue(t.hasAnyKeywayValue)
        assertFalse(t.hasKeyway)
    }

    @Test fun `taper with W, D and L has a keyway value`() {
        val t = Taper(lengthMm = 200f, keywayWidthMm = 6f, keywayDepthMm = 3f, keywayLengthMm = 40f)
        assertTrue(t.hasAnyKeywayValue)
        assertTrue(t.hasKeyway)
    }

    @Test fun `taper inset alone is not a keyway value`() {
        // The inset only positions a keyway; with no W/D/L there is nothing to keep open.
        assertFalse(Taper(lengthMm = 200f, keywayOffsetFromSetMm = 10f).hasAnyKeywayValue)
    }

    // ── Body ─────────────────────────────────────────────────────────────────────

    @Test fun `body with all zeros has no keyway value`() {
        assertFalse(Body(lengthMm = 400f, diaMm = 100f).hasAnyKeywayValue)
    }

    @Test fun `body with only W has a keyway value`() {
        val b = Body(lengthMm = 400f, diaMm = 100f, keywayWidthMm = 6f)
        assertTrue(b.hasAnyKeywayValue)
        assertFalse(b.hasKeyway)
    }

    @Test fun `body with only L has a keyway value`() {
        val b = Body(lengthMm = 400f, diaMm = 100f, keywayLengthMm = 40f)
        assertTrue(b.hasAnyKeywayValue)
        assertFalse(b.hasKeyway)
    }

    @Test fun `body with W, D and L has a keyway value`() {
        val b = Body(lengthMm = 400f, diaMm = 100f, keywayWidthMm = 6f, keywayDepthMm = 3f, keywayLengthMm = 40f)
        assertTrue(b.hasAnyKeywayValue)
        assertTrue(b.hasKeyway)
    }
}
