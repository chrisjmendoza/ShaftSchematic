package com.android.shaftschematic.geom

import com.android.shaftschematic.geom.BelowShaftLabelLayout.Box
import com.android.shaftschematic.geom.BelowShaftLabelLayout.Placement
import com.android.shaftschematic.geom.BelowShaftLabelLayout.Request
import com.android.shaftschematic.geom.BelowShaftLabelLayout.RowBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The below-shaft collision space: component-name labels placed against the Ø callouts.
 *
 * The engine's promise is that a placed label overlaps NOTHING — no callout box, no other label —
 * whenever a clear placement exists at all, and that it spends horizontal slide before vertical
 * rows. [assertClear] is that promise; every scenario below asserts it.
 */
class BelowShaftLabelLayoutTest {

    // Three rows, 12 pt tall, 14 pt apart — the schematic's proportions.
    private val rows = listOf(
        RowBand(top = 24f, bottom = 36f),
        RowBand(top = 38f, bottom = 50f),
        RowBand(top = 52f, bottom = 64f),
    )

    private fun req(width: Float, preferredLeft: Float, minLeft: Float, maxRight: Float) =
        Request(width = width, preferredLeft = preferredLeft, minLeft = minLeft, maxRight = maxRight)

    /** Centered over [center] with the whole component span [spanL]..[spanR] to slide inside. */
    private fun centered(text: Float, center: Float, spanL: Float, spanR: Float) =
        req(text, center - text / 2f, minOf(spanL, center - text / 2f), maxOf(spanR, center + text / 2f))

    private fun overlaps(a: Box, b: Box, padX: Float = BelowShaftLabelLayout.PAD_X): Boolean =
        a.left < b.right + padX && a.right + padX > b.left && a.top < b.bottom && a.bottom > b.top

    private fun boxOf(request: Request, p: Placement): Box =
        Box(p.left, p.left + request.width, rows[p.row].top, rows[p.row].bottom)

    /** Every placed label clears every obstacle and every other placed label. */
    private fun assertClear(reqs: List<Request>, obstacles: List<Box>, placements: List<Placement>) {
        val boxes = reqs.mapIndexed { i, r -> boxOf(r, placements[i]) }
        boxes.forEachIndexed { i, b ->
            obstacles.forEach { o ->
                assertFalse("label $i overlaps obstacle $o at $b", overlaps(b, o))
            }
            boxes.forEachIndexed { j, other ->
                if (i != j) assertFalse("label $i overlaps label $j", overlaps(b, other))
            }
        }
    }

    // ── Degenerate inputs ─────────────────────────────────────────────────────

    @Test
    fun `no requests returns empty`() {
        assertTrue(BelowShaftLabelLayout.place(emptyList(), emptyList(), rows).isEmpty())
    }

    @Test
    fun `no rows places every label at its preferred position, unfitted`() {
        val reqs = listOf(req(40f, 100f, 0f, 500f))
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), emptyList())

        assertEquals(100f, out[0].left, 0.01f)
        assertEquals(0, out[0].row)
        assertFalse(out[0].fitted)
    }

    // ── Nothing in the way ────────────────────────────────────────────────────

    @Test
    fun `an unobstructed label keeps its centered position on the first row`() {
        val reqs = listOf(centered(text = 40f, center = 200f, spanL = 100f, spanR = 300f))
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), rows)

        assertEquals(180f, out[0].left, 0.01f)
        assertEquals(0, out[0].row)
        assertTrue(out[0].fitted)
    }

    @Test
    fun `an obstacle in another row band is ignored`() {
        val reqs = listOf(centered(40f, 200f, 100f, 300f))
        // Sits squarely over the preferred position, but only in the third row's band.
        val obstacles = listOf(Box(left = 150f, right = 250f, top = 52f, bottom = 64f))
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertEquals(180f, out[0].left, 0.01f)
        assertEquals(0, out[0].row)
    }

    // ── Slide before drop ─────────────────────────────────────────────────────

    @Test
    fun `a label slides along its own span rather than dropping a row`() {
        val reqs = listOf(centered(40f, 200f, 100f, 300f))
        // The diameter value hangs to the right of the same center the name is aimed at.
        val obstacles = listOf(Box(left = 214f, right = 260f, top = 24f, bottom = 36f))
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertEquals("slid, not dropped", 0, out[0].row)
        assertTrue(out[0].fitted)
        assertTrue(
            "slid clear of the callout",
            out[0].left + 40f <= 214f - BelowShaftLabelLayout.PAD_X + 0.01f,
        )
        assertClear(reqs, obstacles, out)
    }

    @Test
    fun `the slide is the smallest one that clears`() {
        val reqs = listOf(centered(40f, 200f, 0f, 400f))
        val obstacles = listOf(Box(left = 190f, right = 220f, top = 24f, bottom = 36f))
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        // Left of the obstacle costs |147 − 180| = 33; right of it costs |223 − 180| = 43.
        assertEquals(190f - 40f - BelowShaftLabelLayout.PAD_X, out[0].left, 0.01f)
        assertEquals(0, out[0].row)
    }

    @Test
    fun `a label whose whole span is blocked drops to the next row`() {
        val reqs = listOf(centered(40f, 200f, 190f, 210f))
        val obstacles = listOf(Box(left = 100f, right = 300f, top = 24f, bottom = 36f))
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertEquals(1, out[0].row)
        assertEquals("no slide needed once the row is clear", 180f, out[0].left, 0.01f)
        assertClear(reqs, obstacles, out)
    }

    @Test
    fun `a label drops past every blocked row`() {
        val reqs = listOf(centered(40f, 200f, 195f, 205f))
        val obstacles = listOf(
            Box(100f, 300f, 24f, 36f),
            Box(100f, 300f, 38f, 50f),
        )
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertEquals(2, out[0].row)
        assertClear(reqs, obstacles, out)
    }

    // ── Labels against each other ─────────────────────────────────────────────

    @Test
    fun `two labels aimed at the same center never overlap`() {
        val reqs = listOf(
            centered(40f, 200f, 198f, 202f),
            centered(40f, 200f, 198f, 202f),
        )
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), rows)

        assertEquals(setOf(0, 1), out.map { it.row }.toSet())
        assertClear(reqs, emptyList(), out)
    }

    @Test
    fun `neighbouring labels with room slide apart on one row`() {
        val reqs = listOf(
            centered(40f, 200f, 120f, 280f),
            centered(40f, 230f, 150f, 310f),
        )
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), rows)

        assertTrue("both stayed on the first row", out.all { it.row == 0 })
        assertClear(reqs, emptyList(), out)
    }

    @Test
    fun `results are parallel to the input order, not the placement order`() {
        val reqs = listOf(
            centered(40f, 400f, 380f, 420f),
            centered(40f, 100f, 80f, 120f),
        )
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), rows)

        assertEquals(380f, out[0].left, 0.01f)
        assertEquals(80f, out[1].left, 0.01f)
    }

    // ── The on-device case ────────────────────────────────────────────────────

    /**
     * The reported sheet: two liners, each printing a name centered on its span AND a diameter
     * callout whose leader drops at that same center with the value hanging 14 pt to its right.
     */
    @Test
    fun `a liner name never prints through its own diameter callout`() {
        val centers = listOf(120f, 460f)
        val reqs = centers.map { centered(text = 46f, center = it, spanL = it - 70f, spanR = it + 70f) }
        val obstacles = centers.flatMap { cx ->
            listOf(
                Box(left = cx + 14f, right = cx + 60f, top = 24f, bottom = 36f),   // the value
                Box(left = cx - 1f, right = cx + 14f, top = 0f, bottom = 27f),     // the leader
            )
        }
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertTrue("every name found a clear spot", out.all { it.fitted })
        assertClear(reqs, obstacles, out)
    }

    // ── Nowhere to go ─────────────────────────────────────────────────────────

    @Test
    fun `a label with no clear placement anywhere is marked unfitted, never dropped`() {
        val reqs = listOf(centered(40f, 200f, 195f, 205f))
        val obstacles = rows.map { Box(100f, 300f, it.top, it.bottom) }
        val out = BelowShaftLabelLayout.place(reqs, obstacles, rows)

        assertEquals(1, out.size)
        assertFalse(out[0].fitted)
        assertEquals(180f, out[0].left, 0.01f)
    }

    @Test
    fun `a label wider than its window still places at its preferred position`() {
        // maxRight − minLeft < width: the window cannot hold it on any row.
        val reqs = listOf(req(width = 80f, preferredLeft = 200f, minLeft = 210f, maxRight = 240f))
        val out = BelowShaftLabelLayout.place(reqs, emptyList(), rows)

        assertEquals(200f, out[0].left, 0.01f)
        assertFalse(out[0].fitted)
    }
}
