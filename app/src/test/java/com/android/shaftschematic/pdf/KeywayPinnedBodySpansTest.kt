package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `keywayPinnedBodySpans` — the true-width pin behind a body keyway, and the reason it must
 * read STORED bodies.
 *
 * A resolved body carries no keyway fields at all ([ResolvedBody] has none, and `bodyForPdf`
 * builds a [Body] from geometry alone), so a pin filtered off the resolved list matches
 * nothing: the body compresses, and on the runout sheet the slot it was pinned for could land
 * inside the S-break gap. These pin that the source is the stored spec and that the pin really
 * does hold the body at true width.
 */
class KeywayPinnedBodySpansTest {

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
    fun `a keyed body pins its full span at true width`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyedBody()))
        val spans = keywayPinnedBodySpans(spec)
        assertEquals(1, spans.size)
        assertEquals(0f, spans[0].startMm, 1e-3f)
        assertEquals(300f, spans[0].endMm, 1e-3f)
        assertEquals(Float.MAX_VALUE, spans[0].minWidthPt, 0f)
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

    /** A pinned span holds true width in the solve — the scale yields, never the body. */
    @Test
    fun `the pin holds the body at true width and the scale yields`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyedBody()))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f,
            windowEndMm = 300f,
            features = keywayPinnedBodySpans(spec),
            contentWidth = 150f,
            scaleHi = 1f,
        )
        // 300 mm pinned into 150 pt of paper — the scale must drop to fit it at true width.
        assertEquals(0.5f, solved, 1e-2f)
        assertTrue("a pinned span may never draw compressed", 300f * solved <= 150f + 1e-2f)
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
