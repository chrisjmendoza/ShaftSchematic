package com.android.shaftschematic.geom

import com.android.shaftschematic.geom.DiameterCalloutLayout.Footprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiameterCalloutLayoutTest {

    private fun fp(left: Float, right: Float) = Footprint(left, right)

    @Test
    fun `empty input returns empty`() {
        assertTrue(DiameterCalloutLayout.assignTiers(emptyList()).isEmpty())
    }

    @Test
    fun `single footprint gets tier 0`() {
        assertEquals(listOf(0), DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f))))
    }

    @Test
    fun `two disjoint footprints both stay on tier 0`() {
        // Second starts well past the first's right edge + gap.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f), fp(100f, 120f)))
        assertEquals(listOf(0, 0), tiers)
    }

    @Test
    fun `two overlapping footprints split to tiers 0 and 1`() {
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 40f), fp(20f, 60f)))
        assertEquals(listOf(0, 1), tiers)
    }

    @Test
    fun `footprints closer than the gap bump to tier 1 even without literal overlap`() {
        // right=20, next left=22 → 20 + MIN_GAP(4) = 24 > 22, so it does not clear tier 0.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 20f), fp(22f, 42f)))
        assertEquals(listOf(0, 1), tiers)
    }

    @Test
    fun `third footprint clear of the first reuses tier 0`() {
        // A[0,40] and B[20,60] collide → tiers 0,1. C[80,100] clears A on tier 0.
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 40f), fp(20f, 60f), fp(80f, 100f)))
        assertEquals(listOf(0, 1, 0), tiers)
    }

    @Test
    fun `three mutually overlapping footprints never exceed maxTiers`() {
        val tiers = DiameterCalloutLayout.assignTiers(listOf(fp(0f, 50f), fp(10f, 60f), fp(20f, 70f)))
        assertEquals(3, tiers.size)
        assertTrue(tiers.all { it in 0 until DiameterCalloutLayout.MAX_TIERS })
    }

    @Test
    fun `result is parallel to input order for unsorted input`() {
        // Feed right-to-left; tiers must map back to original positions.
        // Input[0]=far-right disjoint → tier 0; Input[1],Input[2] overlap on the left.
        val tiers = DiameterCalloutLayout.assignTiers(
            listOf(fp(100f, 120f), fp(0f, 40f), fp(20f, 60f))
        )
        assertEquals(0, tiers[0])                 // the far-right one
        assertEquals(setOf(0, 1), setOf(tiers[1], tiers[2])) // the two left overlappers split
    }

    @Test
    fun `deterministic for identical input`() {
        val input = listOf(fp(0f, 40f), fp(20f, 60f), fp(15f, 55f), fp(200f, 220f))
        assertEquals(
            DiameterCalloutLayout.assignTiers(input),
            DiameterCalloutLayout.assignTiers(input),
        )
    }

    @Test
    fun `maxTiers of 1 forces everything to tier 0`() {
        val tiers = DiameterCalloutLayout.assignTiers(
            listOf(fp(0f, 40f), fp(20f, 60f), fp(30f, 70f)),
            maxTiers = 1,
        )
        assertEquals(listOf(0, 0, 0), tiers)
    }

    // ── Label placement against the content edge ──────────────────────────────────────

    @Test
    fun `a label with room hangs right of its leader as before`() {
        assertEquals(114f, DiameterCalloutLayout.labelLeft(100f, 40f, 14f, 36f, 756f), 1e-4f)
        assertEquals("unbounded = unchanged", 114f, DiameterCalloutLayout.labelLeft(100f, 40f, 14f), 1e-4f)
    }

    @Test
    fun `a label that would run past the content right flips left of its leader`() {
        // On-device report: Ø 5.998" ran into the page margin off a body at the FWD end.
        val left = DiameterCalloutLayout.labelLeft(730f, 40f, 14f, 36f, 756f)
        assertEquals(730f - 14f - 40f, left, 1e-4f)
        assertTrue(left + 40f <= 756f)
    }

    @Test
    fun `a label that fits neither side is kept inside the content`() {
        val left = DiameterCalloutLayout.labelLeft(60f, 60f, 14f, 36f, 100f)
        assertTrue(left >= 36f)
        assertTrue(left + 60f <= 100f + 1e-4f)
    }

    // ── place: rows and label sides together ─────────────────────────────────────────────────

    private fun item(anchor: Float, width: Float, feature: Boolean = false) =
        DiameterCalloutLayout.Item(anchor, width, feature)

    private fun place(vararg items: DiameterCalloutLayout.Item) =
        DiameterCalloutLayout.place(items.toList(), dogleg = 14f)

    @Test
    fun `callouts that never conflict keep row 0 on their usual side`() {
        val p = place(item(0f, 40f), item(200f, 40f))
        assertEquals(listOf(0, 0), p.map { it.tier })
        assertEquals(listOf(14f, 214f), p.map { it.labelLeft })
    }

    @Test
    fun `two diameters that overlap stack with neither leader through a value`() {
        // The second leader (40) stands under the first value's usual spot (14..74). Placed one at
        // a time it had no clear spot; together the first takes the second row and the second the
        // first row, so the first's leader (0) drops clear of the second's value (54..114).
        val p = place(item(0f, 60f), item(40f, 60f))
        assertEquals(listOf(1, 0), p.map { it.tier })
        assertEquals(14f, p[0].labelLeft, 1e-4f)
        assertEquals(54f, p[1].labelLeft, 1e-4f)
    }

    @Test
    fun `a feature leader is never dropped through a diameter's value`() {
        // On-device report: a liner Ø at 100 hangs right to 114..174; a blend's leader stands at
        // 150, under that value. The blend keeps row 0 at its spot and the Ø moves down a row,
        // where neither leader crosses the other's label.
        val p = place(item(100f, 60f), item(150f, 50f, feature = true))
        val dia = p[0]
        val blend = p[1]
        assertEquals(0, blend.tier)
        assertEquals(164f, blend.labelLeft, 1e-4f)
        assertEquals(1, dia.tier)
        // The Ø's leader (x = 100) is clear of the blend's row-0 footprint (150..214).
        assertTrue(100f < 150f - DiameterCalloutLayout.MIN_GAP)
    }

    @Test
    fun `a label flips to the other side of its leader when no row is clear`() {
        // The one row is taken right of the leader at 100; left of it is free.
        val p = DiameterCalloutLayout.place(
            listOf(item(104f, 60f, feature = true), item(100f, 40f)),
            dogleg = 14f, maxTiers = 1,
        )
        assertEquals(46f, p[1].labelLeft, 1e-4f)
        assertEquals(0, p[1].tier)
    }

    @Test
    fun `a feature value hung left gives way so a diameter's leader stays off it`() {
        // The blend's leader stands at 300 and its value cannot hang right (content ends at 360),
        // so it hangs left over 226..286 — across the liner's centre at 260, where the Ø leader
        // drops. Placed one at a time the Ø has no clear spot. Together: the blend takes row 1 and
        // the Ø row 0 on its left, each leader clear of the other's value.
        val p = DiameterCalloutLayout.place(
            listOf(item(260f, 50f), item(300f, 60f, feature = true)),
            dogleg = 14f, contentRight = 360f,
        )
        val dia = p[0]
        val blend = p[1]
        assertEquals(DiameterCalloutLayout.Placement(tier = 1, labelLeft = 226f), blend)
        assertEquals(DiameterCalloutLayout.Placement(tier = 0, labelLeft = 196f), dia)
        // The Ø's leader stops at row 0, above the blend's row; the blend's leader (300) is clear
        // of the Ø's row-0 footprint (196..260).
        assertTrue(300f > 260f + DiameterCalloutLayout.MIN_GAP)
    }

    @Test
    fun `callouts with no clear arrangement are still all placed`() {
        // Three wide values on one leader x, one row: nothing clears, nothing is dropped.
        val items = listOf(item(100f, 80f), item(100f, 80f), item(100f, 80f))
        val p = DiameterCalloutLayout.place(items, dogleg = 14f, maxTiers = 1)
        assertEquals(3, p.size)
        assertTrue(p.all { it.tier == 0 })
    }

    @Test
    fun `place is parallel to its input`() {
        val items = listOf(item(300f, 40f), item(0f, 40f, feature = true), item(150f, 40f))
        val p = DiameterCalloutLayout.place(items, dogleg = 14f)
        assertEquals(listOf(314f, 14f, 164f), p.map { it.labelLeft })
    }
}
