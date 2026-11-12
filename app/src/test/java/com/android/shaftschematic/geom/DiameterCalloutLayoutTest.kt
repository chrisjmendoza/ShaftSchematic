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
        assertEquals(DiameterCalloutLayout.Placement(tier = 1, labelLeft = 226f, anchorX = 300f), blend)
        assertEquals(DiameterCalloutLayout.Placement(tier = 0, labelLeft = 196f, anchorX = 260f), dia)
        // The Ø's leader stops at row 0, above the blend's row; the blend's leader (300) is clear
        // of the Ø's row-0 footprint (196..260).
        assertTrue(300f > 260f + DiameterCalloutLayout.MIN_GAP)
    }

    @Test
    fun `a diameter's leader slides along its component to clear a feature's value`() {
        // Same crowding, but the Ø may stand anywhere over 200..290 of its liner. Its own anchor
        // (260) has no clear spot; the nearest one that does is 215, clear of the blend's row-0
        // footprint (226..300), with the value on the second row.
        val p = DiameterCalloutLayout.place(
            listOf(
                DiameterCalloutLayout.Item(260f, 50f, anchorMin = 200f, anchorMax = 290f),
                item(300f, 60f, feature = true),
            ),
            dogleg = 14f, contentRight = 360f,
        )
        assertEquals(DiameterCalloutLayout.Placement(tier = 0, labelLeft = 226f, anchorX = 300f), p[1])
        assertEquals(DiameterCalloutLayout.Placement(tier = 1, labelLeft = 229f, anchorX = 215f), p[0])
    }

    @Test
    fun `a leader that has room where it stands does not slide`() {
        val p = DiameterCalloutLayout.place(
            listOf(DiameterCalloutLayout.Item(100f, 40f, anchorMin = 0f, anchorMax = 200f)),
            dogleg = 14f,
        )
        assertEquals(DiameterCalloutLayout.Placement(tier = 0, labelLeft = 114f, anchorX = 100f), p[0])
    }

    @Test
    fun `a crowd two rows cannot clear takes a third row when the caller allows it`() {
        // Three wide values whose leaders stand 20 apart, none able to hang left (content starts
        // at 90): each later leader stands under every earlier value, so each needs a row of its
        // own above the ones before it — three rows. With two, one leader drops through a value.
        val items = listOf(item(100f, 80f), item(120f, 80f), item(140f, 80f))
        val two = DiameterCalloutLayout.place(items, dogleg = 14f, contentLeft = 90f, contentRight = 400f)
        assertTrue(two.all { it.tier < 2 })
        val three = DiameterCalloutLayout.place(
            items, dogleg = 14f, contentLeft = 90f, contentRight = 400f, fallbackTiers = 3,
        )
        assertEquals(listOf(2, 1, 0), three.map { it.tier })
    }

    @Test
    fun `a crowd two rows clear never takes the third`() {
        val items = listOf(item(0f, 60f), item(40f, 60f))
        assertEquals(
            DiameterCalloutLayout.place(items, dogleg = 14f),
            DiameterCalloutLayout.place(items, dogleg = 14f, fallbackTiers = 3),
        )
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

    @Test
    fun `a crowd takes as many rows as the caller allows, not just a third`() {
        // Four wide values 20 apart that cannot hang left: one row each.
        val items = listOf(item(100f, 100f), item(120f, 100f), item(140f, 100f), item(160f, 100f))
        val p = DiameterCalloutLayout.place(
            items, dogleg = 14f, contentLeft = 90f, contentRight = 400f, fallbackTiers = 4,
        )
        assertEquals(listOf(3, 2, 1, 0), p.map { it.tier })
    }

    @Test
    fun `a value runs into the margin only when no spot inside the content is clear`() {
        // A feature's value hung left of its leader covers the left; a diameter beside it is
        // too wide to hang right inside the content, and both rows on its left are taken.
        val blocker0 = item(200f, 150f, feature = true)
        val blocker1 = item(196f, 150f, feature = true)
        val dia = item(210f, 60f)
        val inside = DiameterCalloutLayout.place(
            listOf(dia), dogleg = 14f, contentLeft = 0f, contentRight = 280f, overhang = 10f,
        ).single()
        assertEquals("with room inside, the value flips left as before", 210f - 14f - 60f, inside.labelLeft)
        val crowded = DiameterCalloutLayout.place(
            listOf(blocker0, blocker1, dia), dogleg = 14f, contentLeft = 0f, contentRight = 280f, overhang = 10f,
        ).last()
        assertEquals("right of its leader, 4 past the content edge", 224f, crowded.labelLeft)
        val none = DiameterCalloutLayout.place(
            listOf(blocker0, blocker1, dia), dogleg = 14f, contentLeft = 0f, contentRight = 280f, overhang = 2f,
        ).last()
        assertTrue("an overhang too small for the value is not taken", none.labelLeft + 60f <= 280f)
    }

    @Test
    fun `with no clear spot a value overlaps its neighbour as little as it can`() {
        // One row, two values on leaders 30 apart, content too tight for either to clear the
        // other: the second hangs on the side that overlaps least instead of squarely on the first.
        val items = listOf(item(100f, 60f), item(130f, 60f))
        val p = DiameterCalloutLayout.place(items, dogleg = 14f, contentLeft = 20f, contentRight = 215f, maxTiers = 1)
        val (a, b) = p
        val overlap = minOf(a.labelLeft + 60f, b.labelLeft + 60f) - maxOf(a.labelLeft, b.labelLeft)
        assertTrue("overlap $overlap", overlap < 35f)
    }

    @Test
    fun `a diameter beside a blend's leader lets the blend's value drop a row`() {
        // Large print, the stretch between a liner and a taper: a body Ø, the liner's Ø, the
        // short section's Ø and its blend's angle, whose leader stands 5 pt from the section's.
        // Taken one at a time the section's Ø has no clear spot; arranged together they all do.
        val items = listOf(
            DiameterCalloutLayout.Item(498.4f, 65f, anchorMin = 405.4f, anchorMax = 591.5f),
            DiameterCalloutLayout.Item(145f, 75f),
            DiameterCalloutLayout.Item(673.5f, 65f, anchorMin = 659.5f, anchorMax = 687.5f),
            DiameterCalloutLayout.Item(175.9f, 41f, anchorMin = 153.9f, anchorMax = 197.9f),
            DiameterCalloutLayout.Item(625.5f, 65f, anchorMin = 603.5f, anchorMax = 647.5f),
            DiameterCalloutLayout.Item(654.5f, 69.6f, feature = true),
        )
        val p = DiameterCalloutLayout.place(
            items, dogleg = 14f, contentLeft = 36f, contentRight = 756f, fallbackTiers = 6, overhang = 18f,
        )
        assertClear(items, p)
    }

    // ── Two leaders: a liner and the body touching it share one value ───────────────────────────

    private fun joined(anchor: Float, second: Float, width: Float, alternatives: List<Float> = emptyList()) =
        DiameterCalloutLayout.Item(anchor, width, secondAnchorX = second, secondAlternatives = alternatives)

    @Test
    fun `a two-leader value hangs past its outer leader`() {
        val p = place(joined(anchor = 200f, second = 100f, width = 40f)).single()
        assertEquals(DiameterCalloutLayout.Placement(0, 214f, 200f, secondAnchorX = 100f), p)
        // Pushed left by the content edge, it hangs past the LEFTMOST leader instead.
        val left = DiameterCalloutLayout.place(
            listOf(joined(anchor = 200f, second = 100f, width = 40f)), dogleg = 14f, contentRight = 240f,
        ).single()
        assertEquals(100f - 14f - 40f, left.labelLeft, 1e-4f)
    }

    @Test
    fun `a two-leader callout's box spans both leaders`() {
        // A value at 154..174 sits between the leaders at 100 and 200. One leader at 200 would
        // clear it on row 0; the joining line from 100 cannot, so the shared value takes row 1,
        // where neither of its leaders crosses that value.
        val items = listOf(item(140f, 20f, feature = true), joined(anchor = 200f, second = 100f, width = 40f))
        val p = place(*items.toTypedArray())
        assertEquals(0, p[0].tier)
        assertEquals(1, p[1].tier)
        assertClearBoth(items, p)
    }

    @Test
    fun `no other leader drops between the two leaders on a deeper row`() {
        // The shared value's leaders stand at 0 and 100; a leader at 50 would cross its joining
        // line from any row below. The shared value is placed first (row 0), which leaves the single
        // one no clear spot; arranged together, the single value takes row 0 and the shared one
        // drops beneath it, both leaders clear of the single value's 50..84.
        val items = listOf(joined(anchor = 0f, second = 100f, width = 40f), item(50f, 20f))
        val p = place(*items.toTypedArray())
        assertEquals(1, p[0].tier)
        assertEquals(0, p[1].tier)
        assertClearBoth(items, p)
    }

    @Test
    fun `a deeper two-leader value never drops its second leader through a shallower value`() {
        // The single value hangs at 64..104 on row 0; the shared value's second leader is pinned at
        // 80, under it. The shared value takes row 0 instead and the single one drops beneath,
        // its leader (50) clear of the shared box 80..254.
        val items = listOf(item(50f, 40f), joined(anchor = 200f, second = 80f, width = 40f))
        val p = place(*items.toTypedArray())
        assertEquals(1, p[0].tier)
        assertEquals(0, p[1].tier)
        assertClearBoth(items, p)
    }

    @Test
    fun `a second leader falls back to its alternatives in order to clear a value`() {
        // Same crowd, but the second leader may fall back to 120, then 160. Its preferred stand (80)
        // is blocked; the first alternative that clears (120) is taken, and both values keep row 0.
        val items = listOf(item(50f, 40f), joined(anchor = 200f, second = 80f, width = 40f, alternatives = listOf(120f, 160f)))
        val p = place(*items.toTypedArray())
        assertEquals(listOf(0, 0), p.map { it.tier })
        assertEquals(120f, p[1].secondAnchorX!!, 1e-4f)
        assertClearBoth(items, p)
    }

    @Test
    fun `a second leader with room at its preferred stand does not move`() {
        val p = place(joined(anchor = 200f, second = 170f, width = 40f, alternatives = listOf(120f, 80f))).single()
        assertEquals(DiameterCalloutLayout.Placement(0, 214f, 200f, secondAnchorX = 170f), p)
    }

    /** [assertClear] with every leader counted, and a two-leader box spanning both leaders. */
    private fun assertClearBoth(items: List<DiameterCalloutLayout.Item>, p: List<DiameterCalloutLayout.Placement>) {
        val gap = DiameterCalloutLayout.MIN_GAP
        fun leaders(i: Int) = listOfNotNull(p[i].anchorX, p[i].secondAnchorX)
        fun left(i: Int) = minOf(leaders(i).min(), p[i].labelLeft)
        fun right(i: Int) = maxOf(leaders(i).max(), p[i].labelLeft + items[i].labelWidth)
        for (i in items.indices) for (j in items.indices) {
            if (i == j) continue
            if (p[i].tier == p[j].tier && i < j) {
                assertTrue("values $i and $j overlap: $p", right(i) + gap <= left(j) || right(j) + gap <= left(i))
            }
            if (p[i].tier > p[j].tier) {
                for (x in leaders(i)) {
                    assertTrue("leader $i through value $j: $p", x <= left(j) - gap || x >= right(j) + gap)
                }
            }
        }
    }

    /** No value on another on its row, and no leader through a value on a row above it. */
    private fun assertClear(items: List<DiameterCalloutLayout.Item>, p: List<DiameterCalloutLayout.Placement>) {
        val gap = DiameterCalloutLayout.MIN_GAP
        fun left(i: Int) = minOf(p[i].anchorX, p[i].labelLeft)
        fun right(i: Int) = maxOf(p[i].anchorX, p[i].labelLeft + items[i].labelWidth)
        for (i in items.indices) for (j in items.indices) {
            if (i == j) continue
            if (p[i].tier == p[j].tier && i < j) {
                assertTrue("values $i and $j overlap: $p", right(i) + gap <= left(j) || right(j) + gap <= left(i))
            }
            if (p[i].tier > p[j].tier) {
                assertTrue(
                    "leader $i through value $j: $p",
                    p[i].anchorX <= left(j) - gap || p[i].anchorX >= right(j) + gap,
                )
            }
        }
    }
}
