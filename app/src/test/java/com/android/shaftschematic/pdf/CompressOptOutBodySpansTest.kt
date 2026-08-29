package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SCHEMATIC_MIN_LINER_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_THREAD_PT
import com.android.shaftschematic.geom.compressOptOutBodySpans
import com.android.shaftschematic.geom.keywayPinnedBodySpans
import com.android.shaftschematic.geom.profileFeatureSpans
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `compressOptOutBodySpans` — the true-scale pin behind a body whose author turned
 * "Compress on drawing" off (the default a newly authored explicit body is created with).
 *
 * It uses the SAME pin protocol as the keyway window (`minWidthPt == Float.MAX_VALUE`,
 * height yields) but deliberately covers a different span: the keyway pin protects only the
 * slot's padded WINDOW so the rest of a long keyed body can still compress and break, while
 * this one pins the body WHOLE — a named section that reads at true proportion for its first
 * inch and foreshortens for the rest would be worse than either.
 *
 * Both must come out of the one builder ([profileFeatureSpans]), which is what makes them
 * reach the schematic, the runout/consolidated sheet, and the UI's kept-% estimator alike.
 */
class CompressOptOutBodySpansTest {

    private fun body(
        id: String = "b1",
        startMm: Float = 0f,
        lengthMm: Float = 300f,
        compress: Boolean = false,
    ) = Body(
        id = id,
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = 100f,
        compressOnDrawing = compress,
    )

    @Test
    fun `an opted-out body pins its WHOLE stored span`() {
        val spec = ShaftSpec(overallLengthMm = 400f, bodies = listOf(body(startMm = 50f, lengthMm = 300f)))
        val spans = compressOptOutBodySpans(spec)
        assertEquals(1, spans.size)
        assertEquals(50f, spans[0].startMm, 1e-3f)
        assertEquals(350f, spans[0].endMm, 1e-3f)
        assertEquals(Float.MAX_VALUE, spans[0].minWidthPt, 0f)
    }

    /** The contrast with the keyway pin, on one and the same body. */
    @Test
    fun `the keyway pin covers a window - this one covers everything`() {
        val keyed = body(lengthMm = 300f).copy(
            keywayWidthMm = 20f, keywayDepthMm = 8f, keywayLengthMm = 100f,
        )
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyed))

        val window = keywayPinnedBodySpans(spec).single()
        assertEquals(0f, window.startMm, 1e-3f)
        assertEquals(120f, window.endMm, 1e-3f)   // slot 100 + one keyway width

        val whole = compressOptOutBodySpans(spec).single()
        assertEquals(0f, whole.startMm, 1e-3f)
        assertEquals(300f, whole.endMm, 1e-3f)
        assertTrue("the opt-out pin must outrun the keyway window", whole.endMm > window.endMm)
    }

    @Test
    fun `a body that still compresses pins nothing`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(body(compress = true)))
        assertTrue(compressOptOutBodySpans(spec).isEmpty())
    }

    /** Documents saved before the flag existed decode `true`, so they pin nothing either. */
    @Test
    fun `the model default compresses`() {
        val spec = ShaftSpec(
            overallLengthMm = 300f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 300f, diaMm = 100f)),
        )
        assertTrue(Body().compressOnDrawing)
        assertTrue(compressOptOutBodySpans(spec).isEmpty())
    }

    @Test
    fun `a degenerate span is skipped`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(body(lengthMm = 0f)))
        assertTrue(compressOptOutBodySpans(spec).isEmpty())
    }

    /** The whole point of the single builder: every consumer sees the pin without asking. */
    @Test
    fun `the shared builder contributes the pin`() {
        val spec = ShaftSpec(overallLengthMm = 400f, bodies = listOf(body(startMm = 50f, lengthMm = 300f)))
        val features = profileFeatureSpans(
            spec,
            linerFloorPt = SCHEMATIC_MIN_LINER_PT,
            threadFloorPt = SCHEMATIC_MIN_THREAD_PT,
            linerMinFracOfTrue = 0f,
        )
        assertTrue(
            "the opt-out span must reach every consumer through the one builder",
            features.any {
                it.minWidthPt == Float.MAX_VALUE &&
                    kotlin.math.abs(it.startMm - 50f) < 1e-3f &&
                    kotlin.math.abs(it.endMm - 350f) < 1e-3f
            },
        )
    }

    // ── The solve: the pin is a guarantee, the drawn HEIGHT is what yields ─────

    @Test
    fun `the pinned body holds true width and the scale yields`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(body()))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 300f,
            features = compressOptOutBodySpans(spec),
            contentWidth = 100f, scaleHi = 1f,
        )
        assertTrue("the scale must drop for the pinned body", solved < 1f)
        assertTrue("a pinned body may never draw compressed", 300f * solved <= 100f + 1e-2f)
    }

    @Test
    fun `a body that fits at the desired scale costs nothing`() {
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(body()))
        val solved = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 300f,
            features = compressOptOutBodySpans(spec),
            contentWidth = 400f, scaleHi = 1f,
        )
        assertEquals(1f, solved, 1e-3f)
    }

    /**
     * A keyed body that also opted out contributes two overlapping pins; the map's
     * normalization merges them keeping the larger floor, so the result is the whole body at
     * true width — not a double demand that crushes the scale twice over.
     */
    @Test
    fun `an opted-out keyed body merges to one whole-body pin`() {
        val keyed = body(lengthMm = 300f).copy(
            keywayWidthMm = 20f, keywayDepthMm = 8f, keywayLengthMm = 100f,
        )
        val spec = ShaftSpec(overallLengthMm = 300f, bodies = listOf(keyed))
        val features = keywayPinnedBodySpans(spec) + compressOptOutBodySpans(spec)

        val solved = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 300f,
            features = features, contentWidth = 150f, scaleHi = 1f,
        )
        // 300 mm pinned into 150 pt of paper is exactly 0.5 pt/mm; a double-counted overlap
        // would land near 0.357 (300 + 120 mm of demand).
        assertEquals(0.5f, solved, 5e-3f)
    }
}
