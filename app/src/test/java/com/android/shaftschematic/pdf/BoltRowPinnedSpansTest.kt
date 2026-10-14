package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SCHEMATIC_MIN_BODY_RUN_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_LINER_PT
import com.android.shaftschematic.geom.SCHEMATIC_MIN_THREAD_PT
import com.android.shaftschematic.geom.boltHoleCentersX
import com.android.shaftschematic.geom.boltRowPinnedSpans
import com.android.shaftschematic.geom.boltRowProtectedSpansMm
import com.android.shaftschematic.geom.buildCompressedProfileXMap
import com.android.shaftschematic.geom.profileFeatureSpans
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `boltRowProtectedSpansMm` / `boltRowPinnedSpans` — the keyway-window rule applied to coupler
 * bolt rows. The hole circles are sized on the diameter scale while their centers ride the
 * axial map, so inside a compressed body a 1"-on-3" row printed as an overlapping chain and the
 * S-break could land on it (manual-figure report). The row's padded window pins at true scale
 * through the ONE builder, and `boltHoleCentersX` places the holes off that window's own local
 * scale so the drawn pitch is the true pitch.
 */
class BoltRowPinnedSpansTest {

    private val inch = 25.4f

    private fun seamRow(startMm: Float = 172f * inch, count: Int = 3) = CouplerBoltSlot(
        id = "S1", startFromAftMm = startMm, holeDiaMm = 1f * inch, count = count, spacingMm = 3f * inch,
        holeStyle = BoltHoleStyle.SEAM,
    )

    private fun crossHole(startMm: Float = 164f * inch) = CouplerBoltSlot(
        id = "S2", startFromAftMm = startMm, holeDiaMm = 0.875f * inch, count = 1,
        holeStyle = BoltHoleStyle.CROSS, clocking = BoltHoleClocking.DEG_90,
    )

    @Test
    fun `a seam row's window spans first center minus reach to last center plus reach`() {
        val spec = ShaftSpec(overallLengthMm = 180f * inch, couplerBoltSlots = listOf(seamRow()))
        val w = boltRowProtectedSpansMm(spec).single()
        // reach = hole radius (0.5") + one hole diameter (1") = 1.5"
        assertEquals(172f * inch - 1.5f * inch, w.loMm, 1e-3f)
        assertEquals(178f * inch + 1.5f * inch, w.hiMm, 1e-3f)
    }

    @Test
    fun `a cross-drilled hole's window is the same construction, one hole wide`() {
        val spec = ShaftSpec(overallLengthMm = 180f * inch, couplerBoltSlots = listOf(crossHole()))
        val w = boltRowProtectedSpansMm(spec).single()
        assertEquals(164f * inch - 1.5f * 0.875f * inch, w.loMm, 1e-3f)
        assertEquals(164f * inch + 1.5f * 0.875f * inch, w.hiMm, 1e-3f)
    }

    @Test
    fun `the window is clamped to the shaft`() {
        val spec = ShaftSpec(overallLengthMm = 180f * inch, couplerBoltSlots = listOf(seamRow(startMm = 173.5f * inch)))
        val w = boltRowProtectedSpansMm(spec).single()
        assertEquals(180f * inch, w.hiMm, 1e-3f)
        val aft = ShaftSpec(overallLengthMm = 180f * inch, couplerBoltSlots = listOf(seamRow(startMm = 0.5f * inch)))
        assertEquals(0f, boltRowProtectedSpansMm(aft).single().loMm, 1e-3f)
    }

    @Test
    fun `an empty row pins nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 180f * inch,
            couplerBoltSlots = listOf(seamRow().copy(holeDiaMm = 0f), seamRow().copy(count = 0)),
        )
        assertTrue(boltRowProtectedSpansMm(spec).isEmpty())
        assertTrue(boltRowPinnedSpans(spec).isEmpty())
    }

    /** The whole point of the single builder: every consumer sees the pin without asking. */
    @Test
    fun `the shared builder contributes the pinned window`() {
        val spec = ShaftSpec(overallLengthMm = 180f * inch, couplerBoltSlots = listOf(seamRow()))
        val features = profileFeatureSpans(
            spec,
            linerFloorPt = SCHEMATIC_MIN_LINER_PT,
            threadFloorPt = SCHEMATIC_MIN_THREAD_PT,
            linerMinFracOfTrue = 0f,
        )
        val window = boltRowProtectedSpansMm(spec).single()
        assertTrue(
            "the bolt window must reach every consumer through the one builder",
            features.any {
                it.minWidthPt == Float.MAX_VALUE &&
                    kotlin.math.abs(it.startMm - window.loMm) < 1e-3f &&
                    kotlin.math.abs(it.endMm - window.hiMm) < 1e-3f
            },
        )
    }

    // ── The drawn pitch is the true pitch inside a compressed body ──────────────

    /**
     * A long bare shaft (compressed hard) carrying one seam row: the window pins, and the
     * holes' drawn pitch equals the true pitch × the window's local pt/mm — which, pinned, is
     * the diameter scale itself. The holes therefore stay round AND correctly spaced.
     */
    @Test
    fun `the drawn pitch equals the true pitch at the window's local scale`() {
        val row = seamRow(startMm = 100f * inch)
        val spec = ShaftSpec(overallLengthMm = 400f * inch, couplerBoltSlots = listOf(row))
        val features = profileFeatureSpans(spec, SCHEMATIC_MIN_LINER_PT, SCHEMATIC_MIN_THREAD_PT, 0f)
        val diaPtPerMm = 0.35f
        val solved = solveMaxProfileScale(
            0f, spec.overallLengthMm, features, contentWidth = 720f, scaleHi = diaPtPerMm,
            gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
        )
        val map = buildCompressedProfileXMap(
            0f, spec.overallLengthMm, features, 0f, 720f, solved,
            gapMinWidthPt = SCHEMATIC_MIN_BODY_RUN_PT,
        )
        // The shaft as a whole is compressed (true 3556 pt into 720)…
        assertTrue(map.segments.any { it.compressed })
        // …but the row's window is not.
        val window = boltRowProtectedSpansMm(spec).single()
        val local = (map.xAt(window.hiMm) - map.xAt(window.loMm)) / (window.hiMm - window.loMm)
        assertEquals("the window holds the diameter scale", solved, local, 1e-4f)

        val xs = boltHoleCentersX(row, map::xAt)
        assertEquals(3, xs.size)
        for (i in 1 until xs.size) {
            assertEquals("drawn pitch = true pitch × local pt/mm", row.spacingMm * local, xs[i] - xs[i - 1], 1e-2f)
        }
        // Holes of Ø1" at this scale cannot overlap at a 3" pitch.
        val holeDiaPt = row.holeDiaMm * solved
        assertTrue("holes must not overlap", xs[1] - xs[0] > holeDiaPt + 1e-2f)
        // And the centers are exactly where the map puts them (the window is linear).
        xs.forEachIndexed { i, x -> assertEquals(map.xAt(row.centerMmAt(i)), x, 1e-3f) }
    }

    @Test
    fun `on a linear map the centers are xAt of each center`() {
        val row = seamRow()
        val xs = boltHoleCentersX(row) { it * 2f }
        assertEquals(3, xs.size)
        listOf(172f, 175f, 178f).forEachIndexed { i, c -> assertEquals(c * inch * 2f, xs[i], 1e-2f) }
        val one = boltHoleCentersX(crossHole()) { it * 2f }
        assertEquals(164f * inch * 2f, one.single(), 1e-3f)
        assertTrue(boltHoleCentersX(row.copy(count = 0)) { it }.isEmpty())
    }
}
