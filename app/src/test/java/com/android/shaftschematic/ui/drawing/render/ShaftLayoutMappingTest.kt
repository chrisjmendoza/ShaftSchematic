package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the px ↔ mm mapping that turns a preview tap into an axial position
 * (TODO §5.2, "preview-tap → adds at correct position").
 *
 * `ShaftDrawing`'s tap handler calls `xMmFromPx` on the same layout the canvas drew with,
 * so a round-trip failure here lands a component somewhere other than where the user tapped.
 */
class ShaftLayoutMappingTest {

    private val spec = ShaftSpec(
        overallLengthMm = 3000f,
        bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 3000f, diaMm = 150f))
    )

    private fun layout(spec: ShaftSpec = this.spec) = ShaftLayout.compute(
        spec = spec,
        leftPx = 0f, topPx = 0f, rightPx = 1000f, bottomPx = 400f, marginPx = 12f
    )

    @Test
    fun `px to mm inverts mm to px`() {
        val l = layout()
        listOf(0f, 1f, 750f, 1500f, 2999f, 3000f).forEach { mm ->
            assertEquals("round-trip at $mm mm", mm, l.xMmFromPx(l.xPx(mm)), 1e-2f)
        }
    }

    @Test
    fun `mm to px inverts px to mm`() {
        val l = layout()
        listOf(l.contentLeftPx, 200f, 500f, l.contentRightPx).forEach { px ->
            assertEquals("round-trip at $px px", px, l.xPx(l.xMmFromPx(px)), 1e-2f)
        }
    }

    @Test
    fun `content left edge maps to the start of the axial window`() {
        val l = layout()
        assertEquals(l.minXMm, l.xMmFromPx(l.contentLeftPx), 1e-3f)
    }

    @Test
    fun `mapping is monotonic aft to fwd`() {
        val l = layout()
        assertTrue(l.xPx(0f) < l.xPx(1500f))
        assertTrue(l.xPx(1500f) < l.xPx(3000f))
    }

    @Test
    fun `a tap left of the content rect reads as a position before the aft face`() {
        // ShaftDrawing passes this through unclamped, so the negative must survive the
        // mapping rather than being folded to zero — the add gates rely on seeing it.
        val l = layout()
        assertTrue(l.xMmFromPx(l.contentLeftPx - 20f) < l.minXMm)
    }

    @Test
    fun `an excluded aft thread widens the window to negative mm`() {
        // An AFT thread excluded from OAL renders off the aft face at a negative start.
        // The window must open up to include it, or tapping it would read the wrong mm.
        val withThread = spec.copy(
            threads = listOf(
                Threads(
                    id = "th1",
                    startFromAftMm = -100f,
                    lengthMm = 100f,
                    majorDiaMm = 120f,
                    pitchMm = 5f,
                    excludeFromOAL = true,
                    isAftEnd = true
                )
            )
        )
        val l = layout(withThread)
        assertEquals(-100f, l.minXMm, 1e-3f)
        // The aft face is no longer at the content edge, and mm still round-trips there.
        assertTrue(l.xPx(0f) > l.contentLeftPx)
        assertEquals(0f, l.xMmFromPx(l.xPx(0f)), 1e-2f)
        assertEquals(-100f, l.xMmFromPx(l.contentLeftPx), 1e-2f)
    }

    @Test
    fun `a degenerate canvas still produces a usable mapping`() {
        // Guards the pxPerMm floor: a zero-size canvas must not yield a divide-by-zero or
        // NaN position when a stray tap arrives mid-layout.
        val l = ShaftLayout.compute(
            spec = spec,
            leftPx = 0f, topPx = 0f, rightPx = 0f, bottomPx = 0f, marginPx = 12f
        )
        assertTrue("pxPerMm must stay positive", l.pxPerMm > 0f)
        val mm = l.xMmFromPx(0f)
        assertTrue("mapped position must be finite, was $mm", mm.isFinite())
    }
}
