package com.android.shaftschematic.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaperKeywayTest {

    private fun taper(
        lengthMm: Float = 400f,
        startDiaMm: Float = 120f,
        endDiaMm: Float = 80f,
        kwWidth: Float = 0f,
        kwDepth: Float = 0f,
        kwLength: Float = 0f,
        kwOffset: Float = 0f,
        spooned: Boolean = false,
    ) = Taper(
        startFromAftMm = 0f,
        lengthMm = lengthMm,
        startDiaMm = startDiaMm,
        endDiaMm = endDiaMm,
        keywayWidthMm = kwWidth,
        keywayDepthMm = kwDepth,
        keywayLengthMm = kwLength,
        keywayOffsetFromSetMm = kwOffset,
        keywaySpooned = spooned,
    )

    // ── hasKeyway ────────────────────────────────────────────────────────────

    @Test fun `hasKeyway is false when all dims are zero`() {
        assertFalse(taper().hasKeyway)
    }

    @Test fun `hasKeyway is false when only width is set`() {
        assertFalse(taper(kwWidth = 30f).hasKeyway)
    }

    @Test fun `hasKeyway is true when width, depth, and length are all non-zero`() {
        assertTrue(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 200f).hasKeyway)
    }

    // ── isValid: offset constraints ──────────────────────────────────────────

    @Test fun `isValid passes when keyway fits within taper with zero offset`() {
        assertTrue(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 200f, kwOffset = 0f).isValid(1000f))
    }

    @Test fun `isValid passes when floating keyway fits within taper`() {
        // offset 50 + length 200 = 250 <= taper length 400
        assertTrue(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 200f, kwOffset = 50f).isValid(1000f))
    }

    @Test fun `isValid fails when floating keyway overruns taper length`() {
        // offset 300 + length 200 = 500 > taper length 400
        assertFalse(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 200f, kwOffset = 300f).isValid(1000f))
    }

    @Test fun `isValid fails when offset is exactly equal to taper length`() {
        // offset 400 + length 1 = 401 > 400
        assertFalse(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 1f, kwOffset = 400f).isValid(1000f))
    }

    @Test fun `isValid fails when negative offset`() {
        assertFalse(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 100f, kwOffset = -1f).isValid(1000f))
    }

    @Test fun `isValid passes with no keyway and non-zero offset (offset field ignored when no keyway)`() {
        // offset is present but keyway dims are zero — the (offset + length <= taperLen) check:
        // 50 + 0 = 50 <= 400, so valid
        assertTrue(taper(kwOffset = 50f).isValid(1000f))
    }

    @Test fun `isValid fails when keyway length alone exceeds taper length`() {
        assertFalse(taper(kwWidth = 30f, kwDepth = 15f, kwLength = 500f, kwOffset = 0f).isValid(1000f))
    }

    // ── backward-compat default ───────────────────────────────────────────────

    @Test fun `default keywayOffsetFromSetMm is zero`() {
        val t = Taper()
        assertTrue(t.keywayOffsetFromSetMm == 0f)
    }
}
