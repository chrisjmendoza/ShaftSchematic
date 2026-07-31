package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Silhouette-notch geometry for keyways clocked 90° apart — the edge-on projection where the
 * keyway's depth is what shows in profile. Covers the polygon construction, the sloped floor on
 * a taper, the span/depth clamps, and the CW→bottom / CCW→top side mapping shared by both draw
 * sites.
 */
class KeywaySilhouetteMathTest {

    private val eps = 1e-3f

    // ── side mapping ─────────────────────────────────────────────────────────

    @Test fun `CW viewed from aft cuts the bottom edge, CCW the top`() {
        assertEquals(KeywaySilhouetteSide.BOTTOM, keywaySilhouetteSideFor(KeywayClocking.DEG_90_CW))
        assertEquals(KeywaySilhouetteSide.TOP, keywaySilhouetteSideFor(KeywayClocking.DEG_90_CCW))
    }

    @Test fun `no silhouette side for 180 or no clocking`() {
        assertNull(keywaySilhouetteSideFor(KeywayClocking.DEG_180))
        assertNull(keywaySilhouetteSideFor(KeywayClocking.NONE))
    }

    @Test fun `radius maps below the centerline on the bottom edge and above on the top`() {
        // cy = 100, scale = 2 px/mm, radius = 30 mm → 60 px off the centerline.
        assertEquals(40f, KeywaySilhouetteSide.TOP.yFor(100f, 30f, 2f), eps)
        assertEquals(160f, KeywaySilhouetteSide.BOTTOM.yFor(100f, 30f, 2f), eps)
    }

    // ── flat body notch ──────────────────────────────────────────────────────

    @Test fun `flat body notch has a floor parallel to the surface`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 100f, spanHiMm = 300f,
            hostStartMm = 0f, hostEndMm = 400f,
            hostStartRadiusMm = 60f, hostEndRadiusMm = 60f,
            depthMm = 15f, side = KeywaySilhouetteSide.BOTTOM,
        )!!

        assertEquals(100f, notch.startMm, eps)
        assertEquals(300f, notch.endMm, eps)
        assertEquals(60f, notch.surfaceRadiusStartMm, eps)
        assertEquals(60f, notch.surfaceRadiusEndMm, eps)
        assertEquals(45f, notch.floorRadiusStartMm, eps)
        assertEquals(45f, notch.floorRadiusEndMm, eps)
        assertEquals(KeywaySilhouetteSide.BOTTOM, notch.side)
    }

    @Test fun `fill polygon runs surface-floor-floor-surface in draw order`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 100f, spanHiMm = 300f,
            hostStartMm = 0f, hostEndMm = 400f,
            hostStartRadiusMm = 60f, hostEndRadiusMm = 60f,
            depthMm = 15f, side = KeywaySilhouetteSide.TOP,
        )!!

        val poly = notch.fillPolygonMm()
        assertEquals(4, poly.size)
        assertEquals(listOf(100f, 100f, 300f, 300f), poly.map { it.xMm })
        assertEquals(listOf(60f, 45f, 45f, 60f), poly.map { it.radiusMm })
    }

    @Test fun `walls run surface to floor and the floor runs aft to fwd`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 100f, spanHiMm = 300f,
            hostStartMm = 0f, hostEndMm = 400f,
            hostStartRadiusMm = 60f, hostEndRadiusMm = 60f,
            depthMm = 15f, side = KeywaySilhouetteSide.TOP,
        )!!

        val (wallStartA, wallStartB) = notch.wallStartMm()
        assertEquals(100f, wallStartA.xMm, eps)
        assertEquals(60f, wallStartA.radiusMm, eps)
        assertEquals(45f, wallStartB.radiusMm, eps)

        val (wallEndA, wallEndB) = notch.wallEndMm()
        assertEquals(300f, wallEndA.xMm, eps)
        assertEquals(45f, wallEndA.radiusMm, eps)
        assertEquals(60f, wallEndB.radiusMm, eps)

        val (floorA, floorB) = notch.floorMm()
        assertEquals(100f, floorA.xMm, eps)
        assertEquals(300f, floorB.xMm, eps)
        assertEquals(45f, floorA.radiusMm, eps)
        assertEquals(45f, floorB.radiusMm, eps)
    }

    // ── sloped taper floor ───────────────────────────────────────────────────

    @Test fun `taper floor tracks the sloped silhouette`() {
        // Radius 50 → 30 over 0..200 mm; notch spans 50..150 → surface radii 45 and 35.
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 50f, spanHiMm = 150f,
            hostStartMm = 0f, hostEndMm = 200f,
            hostStartRadiusMm = 50f, hostEndRadiusMm = 30f,
            depthMm = 10f, side = KeywaySilhouetteSide.BOTTOM,
        )!!

        assertEquals(45f, notch.surfaceRadiusStartMm, eps)
        assertEquals(35f, notch.surfaceRadiusEndMm, eps)
        assertEquals(35f, notch.floorRadiusStartMm, eps)
        assertEquals(25f, notch.floorRadiusEndMm, eps)
        // Floor drop equals surface drop — the floor is parallel to the sloped silhouette.
        assertEquals(
            notch.surfaceRadiusStartMm - notch.surfaceRadiusEndMm,
            notch.floorRadiusStartMm - notch.floorRadiusEndMm,
            eps,
        )
    }

    // ── clamps and degenerate input ──────────────────────────────────────────

    @Test fun `span is clamped to the host span`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = -50f, spanHiMm = 500f,
            hostStartMm = 100f, hostEndMm = 300f,
            hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
            depthMm = 10f, side = KeywaySilhouetteSide.TOP,
        )!!

        assertEquals(100f, notch.startMm, eps)
        assertEquals(300f, notch.endMm, eps)
    }

    @Test fun `reversed span endpoints are normalized`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 300f, spanHiMm = 100f,
            hostStartMm = 0f, hostEndMm = 400f,
            hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
            depthMm = 10f, side = KeywaySilhouetteSide.TOP,
        )!!

        assertEquals(100f, notch.startMm, eps)
        assertEquals(300f, notch.endMm, eps)
    }

    @Test fun `depth is clamped so the floor stays above the centerline`() {
        val notch = buildKeywaySilhouetteNotch(
            spanLoMm = 0f, spanHiMm = 100f,
            hostStartMm = 0f, hostEndMm = 100f,
            hostStartRadiusMm = 20f, hostEndRadiusMm = 20f,
            depthMm = 500f, side = KeywaySilhouetteSide.BOTTOM,
        )!!

        // Never at or through the axis: the floor radius stays strictly positive.
        assertTrue("floor must stay above the centerline", notch.floorRadiusStartMm > 0f)
        assertTrue("floor must sit below the surface", notch.floorRadiusStartMm < notch.surfaceRadiusStartMm)
    }

    @Test fun `a span fully outside the host draws nothing`() {
        assertNull(
            buildKeywaySilhouetteNotch(
                spanLoMm = 500f, spanHiMm = 600f,
                hostStartMm = 0f, hostEndMm = 400f,
                hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
                depthMm = 10f, side = KeywaySilhouetteSide.TOP,
            )
        )
    }

    @Test fun `a zero-length span draws nothing`() {
        assertNull(
            buildKeywaySilhouetteNotch(
                spanLoMm = 100f, spanHiMm = 100f,
                hostStartMm = 0f, hostEndMm = 400f,
                hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
                depthMm = 10f, side = KeywaySilhouetteSide.TOP,
            )
        )
    }

    @Test fun `non-positive depth or degenerate host draws nothing`() {
        assertNull(
            buildKeywaySilhouetteNotch(
                spanLoMm = 0f, spanHiMm = 100f,
                hostStartMm = 0f, hostEndMm = 400f,
                hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
                depthMm = 0f, side = KeywaySilhouetteSide.TOP,
            )
        )
        assertNull(
            buildKeywaySilhouetteNotch(
                spanLoMm = 0f, spanHiMm = 100f,
                hostStartMm = 0f, hostEndMm = 0f,
                hostStartRadiusMm = 40f, hostEndRadiusMm = 40f,
                depthMm = 10f, side = KeywaySilhouetteSide.TOP,
            )
        )
        assertNull(
            buildKeywaySilhouetteNotch(
                spanLoMm = 0f, spanHiMm = 100f,
                hostStartMm = 0f, hostEndMm = 400f,
                hostStartRadiusMm = 0f, hostEndRadiusMm = 0f,
                depthMm = 10f, side = KeywaySilhouetteSide.TOP,
            )
        )
    }

    // ── host adapters ────────────────────────────────────────────────────────

    @Test fun `body adapter resolves the AFT-referenced keyway span and flat radius`() {
        val b = Body(
            id = "b", startFromAftMm = 200f, lengthMm = 400f, diaMm = 120f,
            keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 100f,
            keywayOffsetFromEndMm = 50f, keywayEnd = LinerAuthoredReference.AFT,
        )

        val notch = b.keywaySilhouetteNotch(KeywayClocking.DEG_90_CW)!!
        assertEquals(250f, notch.startMm, eps)
        assertEquals(350f, notch.endMm, eps)
        assertEquals(60f, notch.surfaceRadiusStartMm, eps)
        assertEquals(45f, notch.floorRadiusEndMm, eps)
        assertEquals(KeywaySilhouetteSide.BOTTOM, notch.side)
    }

    @Test fun `taper adapter interpolates the surface across the keyway span`() {
        // SET at start (100 < 140): keyway runs 0..100 from the start face.
        val t = Taper(
            id = "t", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 140f,
            keywayWidthMm = 20f, keywayDepthMm = 10f, keywayLengthMm = 100f,
        )

        val notch = t.keywaySilhouetteNotch(KeywayClocking.DEG_90_CCW)!!
        assertEquals(0f, notch.startMm, eps)
        assertEquals(100f, notch.endMm, eps)
        assertEquals(50f, notch.surfaceRadiusStartMm, eps)
        assertEquals(60f, notch.surfaceRadiusEndMm, eps)
        assertEquals(KeywaySilhouetteSide.TOP, notch.side)
    }

    @Test fun `host adapters draw nothing at 180 or without a keyway`() {
        val b = Body(
            id = "b", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f,
            keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 100f,
        )
        assertNotNull(b.keywaySilhouetteNotch(KeywayClocking.DEG_90_CW))
        assertNull(b.keywaySilhouetteNotch(KeywayClocking.DEG_180))
        assertNull(b.keywaySilhouetteNotch(KeywayClocking.NONE))
        assertNull(b.copy(keywayDepthMm = 0f).keywaySilhouetteNotch(KeywayClocking.DEG_90_CW))
    }

    @Test fun `spooned hosts draw the same plain notch`() {
        val b = Body(
            id = "b", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f,
            keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 100f,
        )
        // The spoon bowl is a face-view construct; edge-on there is nothing extra to draw.
        assertEquals(
            b.keywaySilhouetteNotch(KeywayClocking.DEG_90_CW),
            b.copy(keywaySpooned = true).keywaySilhouetteNotch(KeywayClocking.DEG_90_CW),
        )
    }
}
