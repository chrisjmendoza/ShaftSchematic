package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for `geom/UndercutOverlayMath.kt`'s notch pipeline — the ONE builder behind both draw
 * sites (the canvas overlay and `pdf/UndercutPdfComposer.kt`). Pins the base single-cut shape
 * and the nested-cut staircase: a cut machined inside another is cut against its PARENT's
 * floor, drawn one step further down, and returned after its parent so it paints on top.
 */
class UndercutNotchBuildTest {

    private val oalMm = 2000f
    private val surfaceDiaMm = 200f
    private val segs = listOf(SurfaceSeg(0f, oalMm, surfaceDiaMm, surfaceDiaMm))
    private val ex = 0.25f
    private val eps = 1e-3f

    private fun cut(id: String, startMm: Float, lengthMm: Float, diaMm: Float) =
        Undercut(id = id, startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)

    private fun notches(vararg undercuts: Undercut) =
        buildUndercutNotches(undercuts.toList(), segs, oalMm, exaggerationFrac = ex)

    // ── Base behaviour (unchanged by nesting) ────────────────────────────────

    @Test
    fun `a lone cut is cut against the shaft surface at the exaggerated floor`() {
        val u = cut("a", 200f, 100f, diaMm = 190f)
        val n = notches(u).single()

        assertEquals("a", n.id)
        assertEquals(200f, n.startMm, eps)
        assertEquals(300f, n.endMm, eps)
        // Deepest (and only) cut on the sheet: it draws at the full chosen exaggeration.
        assertEquals(surfaceDiaMm - surfaceDiaMm * ex, n.floorDiaMm, eps)

        val p = n.profiles.single()
        assertEquals(n.floorDiaMm, p.floorDiaMm, eps)
        assertTrue("the surface polyline sits on the shaft surface",
            p.surface.all { kotlin.math.abs(it.diaMm - surfaceDiaMm) < eps })
    }

    @Test
    fun `a cut whose dia reaches the surface draws no region`() {
        assertTrue(notches(cut("a", 200f, 100f, diaMm = surfaceDiaMm)).single().profiles.isEmpty())
    }

    // ── Nested cuts ──────────────────────────────────────────────────────────

    @Test
    fun `a nested cut draws below its parent floor with the parent floor as its surface`() {
        val parent = cut("p", 200f, 200f, diaMm = 190f)
        val child = cut("c", 250f, 50f, diaMm = 186f)
        val built = notches(parent, child).associateBy { it.id }

        val p = built.getValue("p")
        val c = built.getValue("c")
        assertTrue("the child steps below the parent's drawn floor", c.floorDiaMm < p.floorDiaMm - eps)
        assertTrue("the child is never drawn shallower than true", c.floorDiaMm <= child.diaMm + eps)

        val cp = c.profiles.single()
        assertEquals(250f, cp.startMm, eps)
        assertEquals(300f, cp.endMm, eps)
        assertEquals(c.floorDiaMm, cp.floorDiaMm, eps)
        // The child's local surface IS its parent's DRAWN floor, so the faces it draws run
        // outer-drawn-floor → inner-drawn-floor and the pair reads as a staircase.
        assertTrue(
            "the child's surface polyline sits on the parent's drawn floor",
            cp.surface.all { kotlin.math.abs(it.diaMm - p.floorDiaMm) < eps },
        )
    }

    @Test
    fun `a nested cut at or above its parent floor draws nothing`() {
        val parent = cut("p", 200f, 200f, diaMm = 190f)
        // Ø equal to the parent's floor removes no further material — the card's non-blocking
        // warning case, and nothing to draw.
        val flush = cut("c", 250f, 50f, diaMm = 190f)
        assertTrue(notches(parent, flush).first { it.id == "c" }.profiles.isEmpty())
    }

    @Test
    fun `a cut flush with its parent's shoulder draws ONE face down to its own floor`() {
        // The shop's case: a 4" relief with a 1" deeper section reaching the relief's AFT
        // shoulder. At that station there is no material at the parent's floor, so the face runs
        // from the OUTER surface straight to the child's floor — the silhouette two separately
        // authored adjacent sections would print.
        val parent = cut("p", 200f, 101.6f, diaMm = 190f)
        val child = cut("c", 200f, 25.4f, diaMm = 186f)
        val built = notches(parent, child).associateBy { it.id }
        val cp = built.getValue("c").profiles.single()

        assertEquals(
            "the shared AFT face rises to the shaft surface",
            surfaceDiaMm, cp.surface.first().diaMm, eps,
        )
        // …and it is a zero-width STEP, so the void fill is unchanged: the next point sits at
        // the same station, back on the parent's drawn floor.
        assertEquals(cp.surface.first().xMm, cp.surface[1].xMm, eps)
        assertEquals(built.getValue("p").floorDiaMm, cp.surface[1].diaMm, eps)
        // The inboard (FWD) end is a normal step off the parent's floor.
        assertEquals(built.getValue("p").floorDiaMm, cp.surface.last().diaMm, eps)
    }

    @Test
    fun `a cut flush with the FWD shoulder shares that face instead`() {
        val parent = cut("p", 200f, 101.6f, diaMm = 190f)
        val child = cut("c", 276.2f, 25.4f, diaMm = 186f)
        val built = notches(parent, child).associateBy { it.id }
        val cp = built.getValue("c").profiles.single()

        assertEquals(built.getValue("p").floorDiaMm, cp.surface.first().diaMm, eps)
        assertEquals(surfaceDiaMm, cp.surface.last().diaMm, eps)
        assertEquals(cp.surface.last().xMm, cp.surface[cp.surface.size - 2].xMm, eps)
    }

    @Test
    fun `a mid-span child steps off the parent floor at BOTH faces`() {
        val parent = cut("p", 200f, 101.6f, diaMm = 190f)
        val child = cut("c", 240f, 25.4f, diaMm = 186f)
        val built = notches(parent, child).associateBy { it.id }
        val cp = built.getValue("c").profiles.single()
        val parentFloor = built.getValue("p").floorDiaMm
        assertTrue(cp.surface.all { kotlin.math.abs(it.diaMm - parentFloor) < eps })
    }

    @Test
    fun `a level-2 cut flush through both levels reaches the shaft surface`() {
        val l0 = cut("l0", 200f, 200f, diaMm = 190f)
        val l1 = cut("l1", 200f, 120f, diaMm = 186f)
        val l2 = cut("l2", 200f, 50f, diaMm = 183f)
        val built = notches(l0, l1, l2).associateBy { it.id }
        assertEquals(surfaceDiaMm, built.getValue("l1").profiles.single().surface.first().diaMm, eps)
        assertEquals(surfaceDiaMm, built.getValue("l2").profiles.single().surface.first().diaMm, eps)
    }

    @Test
    fun `notches come back parents before children`() {
        // Sorted by nesting level first, then aft → fwd: the level-1 cut inside the AFT relief
        // still lands after the level-0 cut further FWD, so a child always paints over the
        // relief around it whatever the coordinates are.
        val aft = cut("aft", 200f, 200f, diaMm = 190f)
        val child = cut("child", 250f, 50f, diaMm = 186f)
        val fwd = cut("fwd", 800f, 100f, diaMm = 192f)
        assertEquals(listOf("aft", "fwd", "child"), notches(aft, child, fwd).map { it.id })
    }

    @Test
    fun `a level-2 cut steps down again`() {
        val l0 = cut("l0", 200f, 400f, diaMm = 190f)
        val l1 = cut("l1", 250f, 200f, diaMm = 186f)
        val l2 = cut("l2", 300f, 60f, diaMm = 183f)
        val built = notches(l0, l1, l2).associateBy { it.id }

        assertEquals(listOf("l0", "l1", "l2"), notches(l0, l1, l2).map { it.id })
        assertTrue(built.getValue("l1").floorDiaMm < built.getValue("l0").floorDiaMm - eps)
        assertTrue(built.getValue("l2").floorDiaMm < built.getValue("l1").floorDiaMm - eps)
        assertTrue(built.getValue("l2").floorDiaMm > 0f)
        assertTrue(
            "the level-2 profile sits on the level-1 drawn floor",
            built.getValue("l2").profiles.single().surface
                .all { kotlin.math.abs(it.diaMm - built.getValue("l1").floorDiaMm) < eps },
        )
    }

    @Test
    fun `a nested child never squashes the sheet's top-level cuts`() {
        // The child is deep from the BASE surface but shallow from its parent's floor. It is
        // excluded from the normalization pool, so the parent still owns the sheet's reference
        // and draws at the full exaggeration.
        val parent = cut("p", 200f, 200f, diaMm = 190f)
        val child = cut("c", 250f, 50f, diaMm = 120f)
        val lone = notches(parent).single().floorDiaMm
        val withChild = notches(parent, child).first { it.id == "p" }.floorDiaMm
        assertEquals(lone, withChild, eps)
    }
}
