package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.keywayPinnedBodySpans
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `keywayPinnedBodySpans` / `bodyKeywayProtectedSpansMm` — the true-scale pin behind a body
 * keyway, and the two rules that make it usable:
 *
 * 1. The pin covers the KEYWAY WINDOW (slot span padded by one keyway width, clamped to the
 *    body), never the whole host body — a 95%-shaft body pinned whole cannot render at all
 *    (on-device report); the rest of the body stays free to compress and break.
 * 2. It reads STORED bodies. A resolved body carries no keyway fields ([ResolvedBody] has
 *    none, and `bodyForPdf` builds a [Body] from geometry alone), so a pin filtered off the
 *    resolved list matches nothing, silently.
 */
class KeywayPinnedBodySpansTest {

    /** 300 mm body, 20 × 8 × 100 open AFT keyway → slot [0,100], window [0,120]. */
    private fun keyedBody(id: String = "b1", startMm: Float = 0f, lengthMm: Float = 300f) = Body(
        id = id,
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = 100f,
        keywayWidthMm = 20f,
        keywayDepthMm = 8f,
        keywayLengthMm = 100f,
    )

    private fun plainBody(id: String = "b2", startMm: Float = 0f, lengthMm: Float = 300f) =
        Body(id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = 100f)

    private fun resolvedOf(b: Body) = ResolvedBody(
        id = b.id,
        type = ResolvedComponentType.BODY,
        source = ResolvedComponentSource.EXPLICIT,
        startMmPhysical = b.startFromAftMm,
        endMmPhysical = b.startFromAftMm + b.lengthMm,
        diaMm = b.diaMm,
    )

    @Test
    fun `the pin covers the padded keyway window - never the whole body`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyedBody()))
        val spans = keywayPinnedBodySpans(spec)
        assertEquals(1, spans.size)
        assertEquals(0f, spans[0].startMm, 1e-3f)      // pad clamps at the body's aft face
        assertEquals(120f, spans[0].endMm, 1e-3f)      // slot end 100 + one keyway width
        assertEquals(Float.MAX_VALUE, spans[0].minWidthPt, 0f)
        assertTrue("the rest of the body must stay free to compress", spans[0].endMm < 300f)
    }

    @Test
    fun `a fwd keyway's window clamps at the fwd face`() {
        val spec = ShaftSpec(
            overallLengthMm = 300f,
            bodies = listOf(keyedBody().copy(keywayEnd = LinerAuthoredReference.FWD)),
        )
        val spans = keywayPinnedBodySpans(spec)
        assertEquals(1, spans.size)
        assertEquals(180f, spans[0].startMm, 1e-3f)    // slot [200,300] padded aft to 180
        assertEquals(300f, spans[0].endMm, 1e-3f)
    }

    @Test
    fun `a body without a keyway pins nothing`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(plainBody()))
        assertTrue(keywayPinnedBodySpans(spec).isEmpty())
    }

    /**
     * The regression: the resolved mapping drops every keyway field, so the same spec pins
     * nothing once it has been through `withResolvedBodies` — which is what both sheets used
     * to filter, leaving the pin permanently dead.
     */
    @Test
    fun `resolved bodies carry no keyway - the pin must come from the stored spec`() {
        val stored = keyedBody()
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(stored))
        val resolvedSpec = spec.withResolvedBodies(listOf(resolvedOf(stored)))

        assertTrue("stored body hosts the keyway", stored.hasKeyway)
        assertTrue("resolved runs carry no keyway", resolvedSpec.bodies.none { it.hasKeyway })
        assertTrue(keywayPinnedBodySpans(resolvedSpec).isEmpty())
        assertEquals(1, keywayPinnedBodySpans(spec).size)
    }

    /** The pinned window holds true scale in the solve — the scale yields, never the slot. */
    @Test
    fun `the pin holds the keyway window at true width and the scale yields`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyedBody()))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f,
            windowEndMm = 300f,
            features = keywayPinnedBodySpans(spec),
            contentWidth = 100f,
            scaleHi = 1f,
        )
        assertTrue("the scale must drop for the pinned window", solved < 1f)
        assertTrue(
            "a pinned window may never draw compressed",
            120f * solved <= 100f + 1e-2f,
        )
    }

    /** Unlike the retired whole-body pin, a long keyed body no longer crushes the scale. */
    @Test
    fun `only the window demands true width - a long keyed body keeps a usable scale`() {
        val longKeyed = keyedBody(lengthMm = 4500f)     // ~177 in, the on-device shaft
        val spec = ShaftSpec(overallLengthMm = 4500f, bodies = listOf(longKeyed))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f,
            windowEndMm = 4500f,
            features = keywayPinnedBodySpans(spec),
            contentWidth = 700f,
            scaleHi = 1f,
        )
        // Whole-body pinning would force 4500·s ≤ 700 (s ≈ 0.15); the window pin only needs
        // its 120 mm plus the body-run floor, so the desired scale survives.
        assertEquals(1f, solved, 1e-3f)
    }

    @Test
    fun `without a keyway the solve keeps the desired scale`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(plainBody()))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f,
            windowEndMm = 300f,
            features = keywayPinnedBodySpans(spec),
            contentWidth = 150f,
            scaleHi = 1f,
        )
        assertEquals(1f, solved, 1e-3f)
    }
}
