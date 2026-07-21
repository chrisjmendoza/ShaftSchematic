package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for the wear-PDF detail-strip layout (Phase 4,
 * `docs/LinerWearAreas_Proposal.md` §6.2/§9). Mirrors the style of
 * `PdfLayoutBoundsTest` — plain JVM assertions, no Robolectric — but exercises the
 * extracted functions in `WearStripLayout.kt` directly rather than replicating
 * their formulas in the test.
 */
class WearStripLayoutTest {

    private fun liner(id: String, startMm: Float, lengthMm: Float, odMm: Float = 80f) =
        Liner(id = id, startFromAftMm = startMm, lengthMm = lengthMm, odMm = odMm)

    private fun spot(linerId: String, startMm: Float = 0f, lengthMm: Float = 25f, minDiaMm: Float = 0f) =
        WearSpot(linerId = linerId, startMm = startMm, lengthMm = lengthMm, minDiaMm = minDiaMm)

    // ── collectWearLinerGroups ────────────────────────────────────────────────

    @Test
    fun `liner with no spots gets no strip`() {
        val liners = listOf(liner("a", 0f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(spots = emptyList()))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `orphan spot referencing missing liner is dropped`() {
        val liners = listOf(liner("a", 0f, 200f))
        val record = WearRecord(spots = listOf(spot(linerId = "ghost")))
        assertTrue(collectWearLinerGroups(liners, record).isEmpty())
    }

    @Test
    fun `groups are sorted aft to fwd regardless of input order`() {
        val liners = listOf(liner("fwd", 700f, 100f), liner("aft", 50f, 100f))
        val record = WearRecord(spots = listOf(spot("fwd"), spot("aft")))
        val groups = collectWearLinerGroups(liners, record)
        assertEquals(listOf("aft", "fwd"), groups.map { it.liner.id })
    }

    @Test
    fun `multiple spots on one liner are all kept`() {
        val liners = listOf(liner("a", 0f, 200f))
        val record = WearRecord(spots = listOf(spot("a", 0f, 10f), spot("a", 50f, 10f)))
        val groups = collectWearLinerGroups(liners, record)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].spots.size)
    }

    // ── selectWearStripsForPage ───────────────────────────────────────────────

    @Test
    fun `at or under the page limit produces no overflow`() {
        val groups = (1..3).map { WearLinerGroup(liner("l$it", it * 100f, 50f), listOf(spot("l$it"))) }
        val selection = selectWearStripsForPage(groups)
        assertEquals(3, selection.onPage.size)
        assertTrue(selection.overflow.isEmpty())
    }

    @Test
    fun `over the page limit overflows the remainder`() {
        val groups = (1..5).map { WearLinerGroup(liner("l$it", it * 100f, 50f), listOf(spot("l$it"))) }
        val selection = selectWearStripsForPage(groups)
        assertEquals(3, selection.onPage.size)
        assertEquals(2, selection.overflow.size)
        // Overflow keeps the aft→fwd tail, not an arbitrary subset.
        assertEquals(listOf("l4", "l5"), selection.overflow.map { it.liner.id })
    }

    // ── determineWearPdfMode (2026-07-21 rule: 0 -> profile form, 1 -> combined, 2+ -> grid;
    // the shaft profile is always kept on top) ─────────────────────────────────────────────

    @Test
    fun `zero wear liners selects the profile form`() {
        assertEquals(WearPdfMode.PROFILE_FORM, determineWearPdfMode(0))
    }

    @Test
    fun `one wear liner selects the combined page`() {
        assertEquals(WearPdfMode.COMBINED, determineWearPdfMode(1))
    }

    @Test
    fun `two or more wear liners select the grid`() {
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(WEAR_STRIP_GRID_MIN_LINERS))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(2))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(3))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(10))
    }

    @Test
    fun `no recorded spots at all resolves to the profile form via collectWearLinerGroups`() {
        // composeWearPdf's mode is `determineWearPdfMode(collectWearLinerGroups(...).size)` — no
        // separate pure function re-derives "are there any spots", it reuses the already-tested
        // grouping/orphan-drop logic. Spelled out explicitly here for this feature's mode switch.
        val liners = listOf(liner("a", 0f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(spots = emptyList()))
        assertEquals(WearPdfMode.PROFILE_FORM, determineWearPdfMode(groups.size))
    }

    @Test
    fun `spots recorded only against since-deleted liners resolve to the profile form too`() {
        // All three "wear liners" here are orphans (their liner no longer exists in the spec) —
        // collectWearLinerGroups drops them, so the mode switch correctly falls back to the
        // profile form instead of rendering a grid page with nothing on it.
        val liners = listOf(liner("still-here", 0f, 200f))
        val record = WearRecord(
            spots = listOf(spot(linerId = "deleted-1"), spot(linerId = "deleted-2"), spot(linerId = "deleted-3")),
        )
        val groups = collectWearLinerGroups(liners, record)
        assertEquals(WearPdfMode.PROFILE_FORM, determineWearPdfMode(groups.size))
    }

    @Test
    fun `exactly two wear liners crosses into the grid`() {
        val liners = listOf(liner("a", 0f, 200f), liner("b", 300f, 200f))
        val record = WearRecord(spots = listOf(spot("a"), spot("b")))
        val groups = collectWearLinerGroups(liners, record)
        assertEquals(2, groups.size)
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(groups.size))
    }

    // ── clampWearBandToLiner ──────────────────────────────────────────────────

    @Test
    fun `band fully inside the liner is unchanged`() {
        val clamp = clampWearBandToLiner(spotStartMm = 20f, spotLengthMm = 30f, linerLengthMm = 100f)
        assertEquals(20f, clamp.startMm, 1e-6f)
        assertEquals(30f, clamp.lengthMm, 1e-6f)
    }

    @Test
    fun `band overrunning the liner end is clamped`() {
        val clamp = clampWearBandToLiner(spotStartMm = 80f, spotLengthMm = 50f, linerLengthMm = 100f)
        assertEquals(80f, clamp.startMm, 1e-6f)
        assertEquals(20f, clamp.lengthMm, 1e-6f)   // clamped to the remaining 20mm
    }

    @Test
    fun `band starting before the liner aft edge is clamped at zero`() {
        val clamp = clampWearBandToLiner(spotStartMm = -10f, spotLengthMm = 30f, linerLengthMm = 100f)
        assertEquals(0f, clamp.startMm, 1e-6f)
        assertEquals(20f, clamp.lengthMm, 1e-6f)
    }

    @Test
    fun `band entirely past the liner end clamps to zero length`() {
        val clamp = clampWearBandToLiner(spotStartMm = 150f, spotLengthMm = 20f, linerLengthMm = 100f)
        assertEquals(0f, clamp.lengthMm, 1e-6f)
    }

    // ── computeWearVerticalLayout ─────────────────────────────────────────────

    @Test
    fun `zero strips leaves the profile filling the whole area`() {
        val layout = computeWearVerticalLayout(areaTop = 100f, areaBottom = 400f, stripCount = 0)
        assertEquals(100f, layout.profileTop, 1e-6f)
        assertEquals(400f, layout.profileBottom, 1e-6f)
        assertTrue(layout.stripTops.isEmpty())
    }

    @Test
    fun `strips stay within the content band and are ordered top to bottom`() {
        val areaTop = 88f; val areaBottom = 524f  // representative of WearPdfComposer's midTop/midBot
        val layout = computeWearVerticalLayout(areaTop, areaBottom, stripCount = 3)

        assertEquals(3, layout.stripTops.size)
        assertTrue("profile must not shrink below the floor",
            layout.profileBottom - layout.profileTop >= WEAR_MIN_PROFILE_HEIGHT_PT - 1e-3f)
        layout.stripTops.forEachIndexed { i, top ->
            assertTrue("strip $i top >= profile bottom", top >= layout.profileBottom - 1e-3f)
            assertTrue("strip $i bottom <= areaBottom", layout.stripBottoms[i] <= areaBottom + 1e-3f)
            assertTrue("strip $i has positive height", layout.stripBottoms[i] > top)
        }
        // No overlaps between consecutive strips.
        for (i in 0 until layout.stripTops.size - 1) {
            assertTrue(layout.stripBottoms[i] <= layout.stripTops[i + 1] + 1e-3f)
        }
        // Last strip bottom lands exactly on areaBottom (nothing wasted / nothing overflows).
        assertEquals(areaBottom, layout.stripBottoms.last(), 1e-3f)
    }

    @Test
    fun `reserved bottom space for the overflow note is respected`() {
        val areaTop = 88f; val areaBottom = 524f; val reserved = 16f
        val layout = computeWearVerticalLayout(areaTop, areaBottom, stripCount = 3, reservedBottomPt = reserved)
        assertTrue(layout.stripBottoms.last() <= areaBottom - reserved + 1e-3f)
    }

    @Test
    fun `very small area still keeps strips within bounds and never inverts`() {
        // Degenerate case: not enough room for the preferred sizes. Everything must still
        // clamp to non-negative, non-overlapping bands inside the area.
        val layout = computeWearVerticalLayout(areaTop = 0f, areaBottom = 120f, stripCount = 3)
        assertTrue(layout.profileBottom >= layout.profileTop)
        layout.stripTops.forEachIndexed { i, top ->
            assertTrue(top <= layout.stripBottoms[i] + 1e-3f)
            assertTrue(layout.stripBottoms[i] <= 120f + 1e-3f)
            assertTrue(top >= 0f - 1e-3f)
        }
    }

    // ── computeWearStripGridLayout (WearPdfMode.GRID, 2026-07-21 — two strips per row) ─────────

    private val gridLeft = 36f
    private val gridRight = 756f

    @Test
    fun `grid zero strips keeps the full profile band and no cells`() {
        val g = computeWearStripGridLayout(88f, 524f, gridLeft, gridRight, stripCount = 0)
        assertTrue(g.cells.isEmpty())
        assertEquals(88f, g.profileTop, 1e-3f)
        assertEquals(524f, g.profileBottom, 1e-3f)
    }

    @Test
    fun `two strips sit side by side on one row, profile kept on top`() {
        val g = computeWearStripGridLayout(88f, 524f, gridLeft, gridRight, stripCount = 2)
        assertEquals(2, g.cells.size)
        // Same row → same vertical band.
        assertEquals(g.cells[0].top, g.cells[1].top, 1e-3f)
        assertEquals(g.cells[0].bottom, g.cells[1].bottom, 1e-3f)
        // Left column is left of the right column, non-overlapping in x.
        assertTrue(g.cells[0].right <= g.cells[1].left + 1e-3f)
        // The strips start below the (shrunk) profile.
        assertTrue(g.cells[0].top >= g.profileBottom - 1e-3f)
        // A full row spans the content width edge-to-edge.
        assertEquals(gridLeft, g.cells[0].left, 1e-3f)
        assertEquals(gridRight, g.cells[1].right, 1e-3f)
    }

    @Test
    fun `three strips take two rows, two over one, and the lone third is centered`() {
        val g = computeWearStripGridLayout(88f, 524f, gridLeft, gridRight, stripCount = 3)
        assertEquals(3, g.cells.size)
        // Row 0 = cells 0,1 ; row 1 = cell 2. Two rows, not three.
        assertEquals(g.cells[0].top, g.cells[1].top, 1e-3f)
        assertTrue("third strip is on a lower row", g.cells[2].top > g.cells[0].top + 1e-3f)
        // Lone third strip keeps a column width and is centered in the content span.
        val colW = g.cells[0].right - g.cells[0].left
        assertEquals("same column width as a full row", colW, g.cells[2].right - g.cells[2].left, 1e-3f)
        val contentMid = (gridLeft + gridRight) / 2f
        assertEquals("centered", contentMid, (g.cells[2].left + g.cells[2].right) / 2f, 1e-3f)
    }

    @Test
    fun `four strips fill a two by two grid within bounds`() {
        val g = computeWearStripGridLayout(88f, 524f, gridLeft, gridRight, stripCount = 4)
        assertEquals(4, g.cells.size)
        // Rows: {0,1} and {2,3}.
        assertEquals(g.cells[0].top, g.cells[1].top, 1e-3f)
        assertEquals(g.cells[2].top, g.cells[3].top, 1e-3f)
        assertTrue(g.cells[2].top > g.cells[0].top + 1e-3f)
        g.cells.forEach { c ->
            assertTrue("cell within content x", c.left >= gridLeft - 1e-3f && c.right <= gridRight + 1e-3f)
            assertTrue("cell below profile", c.top >= g.profileBottom - 1e-3f)
            assertTrue("cell has positive area", c.right > c.left && c.bottom > c.top)
        }
    }

    // ── computeWearStripHorizontalLayout ──────────────────────────────────────

    @Test
    fun `liner and stubs stay within the strip bounds`() {
        val left = 36f; val right = 756f
        val hLayout = computeWearStripHorizontalLayout(left, right, linerLengthMm = 150f)
        assertTrue(hLayout.linerLeftPt - hLayout.stubWidthPt >= left - 1e-3f)
        assertTrue(hLayout.linerRightPt + hLayout.stubWidthPt <= right + 1e-3f)
        assertTrue(hLayout.linerRightPt > hLayout.linerLeftPt)
    }

    @Test
    fun `very short liner scale is capped, not exploded`() {
        val hLayout = computeWearStripHorizontalLayout(36f, 756f, linerLengthMm = 1f)
        assertTrue(hLayout.ptPerMm <= WEAR_STRIP_MAX_PT_PER_MM + 1e-6f)
    }

    @Test
    fun `very long liner scale is floored, not vanished`() {
        val hLayout = computeWearStripHorizontalLayout(36f, 756f, linerLengthMm = 100000f)
        assertTrue(hLayout.ptPerMm >= WEAR_STRIP_MIN_PT_PER_MM - 1e-6f)
        assertTrue(hLayout.linerRightPt > hLayout.linerLeftPt)
    }

    // ── computeWearStripInnerLayout (2026-07-18 dimension-rail rework: fixed rail budget,
    // no longer proportional to spot count) ──────────────────────────────────────────────

    @Test
    fun `inner layout fits the cylinder and the full rail row budget in an ordinary strip`() {
        val inner = computeWearStripInnerLayout(
            stripTop = 100f, stripBottom = 100f + 15f + WEAR_STRIP_LABEL_HEADROOM_PT + 40f + 2 * WEAR_STRIP_ROW_HEIGHT_PT,
            titleHeightPt = 15f,
        )
        assertTrue(inner.cylTop >= 100f)
        assertTrue(inner.cylBottom <= inner.railY + 1e-3f)
        assertTrue(inner.cylBottom > inner.cylTop)
        assertEquals(WEAR_RAIL_MAX_LABEL_ROWS, inner.railLabelRows)
        assertEquals(inner.cylBottom + inner.railLabelRows * WEAR_STRIP_ROW_HEIGHT_PT, inner.railY, 1e-3f)
    }

    @Test
    fun `inner layout never overflows the strip even when nothing fits`() {
        // Pathologically short strip: title alone barely fits, no room for a cylinder or rail rows.
        val inner = computeWearStripInnerLayout(
            stripTop = 100f, stripBottom = 103f, titleHeightPt = 15f,
        )
        assertTrue("cylTop must not exceed stripBottom", inner.cylTop <= 103f + 1e-3f)
        assertTrue("cylBottom must not exceed stripBottom", inner.cylBottom <= 103f + 1e-3f)
        assertTrue("cylBottom must not be before cylTop", inner.cylBottom >= inner.cylTop)
        assertTrue("railY must not exceed stripBottom", inner.railY <= 103f + 1e-3f)
        assertTrue("no room means no label rows fit", inner.railLabelRows == 0)
    }

    @Test
    fun `inner layout gives the rail fewer than its full budget once the cylinder is squeezed to zero`() {
        // The cylinder shrinks to zero height FIRST (matching the pre-existing contract); only
        // once that happens does the rail's own row budget start dropping below
        // WEAR_RAIL_MAX_LABEL_ROWS — as long as the cylinder has any positive height at all, the
        // rail always gets its full fixed budget (the reservation has priority over the cylinder).
        val titleH = 15f
        val rowH = WEAR_STRIP_ROW_HEIGHT_PT
        val stripTop = 0f
        val cylTopExpected = stripTop + titleH + WEAR_STRIP_LABEL_HEADROOM_PT
        val stripBottom = cylTopExpected + 20f // between 1 row (13pt) and the full 2-row (26pt) budget
        val inner = computeWearStripInnerLayout(stripTop, stripBottom, titleH, rowHeightPt = rowH)
        assertEquals(cylTopExpected, inner.cylTop, 1e-3f)
        assertEquals("cylinder squeezed to zero height", inner.cylTop, inner.cylBottom, 1e-3f)
        assertEquals(1, inner.railLabelRows)
        assertTrue(inner.railY <= stripBottom + 1e-3f)
    }

    // ── computeWearStripInnerLayout — label headroom (2026-07-18 SVG review, defect 2) ──

    @Test
    fun `label headroom is reserved between the title and the cylinder top`() {
        // titleHeightPt here stands in for the title text's own line height (no
        // ad hoc fudge folded in) — the headroom must appear as an explicit,
        // separate gap on top of that, not be absorbed into it.
        val inner = computeWearStripInnerLayout(
            stripTop = 100f, stripBottom = 300f, titleHeightPt = 9f,
        )
        assertEquals(100f + 9f + WEAR_STRIP_LABEL_HEADROOM_PT, inner.cylTop, 1e-3f)
    }

    @Test
    fun `title plus headroom exceeding a pathologically short strip still clamps, never inverts`() {
        val inner = computeWearStripInnerLayout(
            stripTop = 0f, stripBottom = 12f, titleHeightPt = 9f,
        )
        assertTrue("cylTop must not exceed stripBottom even though title+headroom alone exceed it",
            inner.cylTop <= 12f + 1e-3f)
        assertTrue(inner.cylBottom >= inner.cylTop)
        assertTrue(inner.railY <= 12f + 1e-3f)
    }

    // ── buildWearStripRailSpans (2026-07-18 dimension-rail rework) ────────────────────────

    private fun bands(vararg pairs: Pair<Float, Float>) = pairs.map { (s, l) -> WearBandClamp(s, l) }

    @Test
    fun `chain covers the full liner length exactly, sum of spans equals liner length`() {
        val linerLen = 400f
        val clamped = bands(60f to 130f, 290f to 90f) // matches the reviewed example (liner-1)
        val spans = buildWearStripRailSpans(linerLen, clamped, UnitSystem.MILLIMETERS)
        val total = spans.sumOf { (it.endMm - it.startMm).toDouble() }
        assertEquals(linerLen.toDouble(), total, 1e-3)
        // Chain is contiguous: each span's end equals the next span's start.
        for (i in 0 until spans.size - 1) {
            assertEquals(spans[i].endMm, spans[i + 1].startMm, 1e-3f)
        }
        assertEquals(0f, spans.first().startMm, 1e-6f)
        assertEquals(linerLen, spans.last().endMm, 1e-6f)
    }

    @Test
    fun `zero-length leading, trailing, and gap spans are omitted`() {
        // Band starts at 0 (no leading gap), a second band starts immediately where the
        // first ends (no inter-band gap), and the second band ends exactly at the liner's
        // own length (no trailing span) — only the two band-length spans should remain.
        val linerLen = 200f
        val clamped = bands(0f to 100f, 100f to 100f)
        val spans = buildWearStripRailSpans(linerLen, clamped, UnitSystem.MILLIMETERS)
        assertEquals(2, spans.size)
        assertEquals(0f, spans[0].startMm, 1e-6f); assertEquals(100f, spans[0].endMm, 1e-6f)
        assertEquals(100f, spans[1].startMm, 1e-6f); assertEquals(200f, spans[1].endMm, 1e-6f)
    }

    @Test
    fun `a zero-length band (fully clamped away) contributes no span of its own`() {
        val linerLen = 100f
        // What clampWearBandToLiner(spotStartMm=150, spotLengthMm=20, linerLengthMm=100) actually
        // returns: start clamped to 100, end clamped to 100, length 0 — a spot entirely past the
        // liner end collapses to a zero-length clamp.
        val clamped = bands(100f to 0f)
        val spans = buildWearStripRailSpans(linerLen, clamped, UnitSystem.MILLIMETERS)
        // Only the trailing remainder (0 -> 100, the whole liner) should appear.
        assertEquals(1, spans.size)
        assertEquals(0f, spans[0].startMm, 1e-6f); assertEquals(100f, spans[0].endMm, 1e-6f)
    }

    @Test
    fun `overlapping bands do not double-count or run the chain backward`() {
        val linerLen = 300f
        val clamped = bands(50f to 100f, 80f to 100f) // second band starts inside the first
        val spans = buildWearStripRailSpans(linerLen, clamped, UnitSystem.MILLIMETERS)
        val total = spans.sumOf { (it.endMm - it.startMm).toDouble() }
        assertEquals(linerLen.toDouble(), total, 1e-3)
        for (i in 0 until spans.size - 1) {
            assertTrue("span $i must not run backward", spans[i].endMm <= spans[i + 1].startMm + 1e-3f)
        }
    }

    @Test
    fun `labels use formatLenDim in the active unit`() {
        val spans = buildWearStripRailSpans(200f, bands(50f to 30f), UnitSystem.MILLIMETERS)
        assertEquals(3, spans.size)
        assertEquals(formatLenDim(50.0, UnitSystem.MILLIMETERS), spans[0].label)
        assertEquals(formatLenDim(30.0, UnitSystem.MILLIMETERS), spans[1].label)
        assertEquals(formatLenDim(120.0, UnitSystem.MILLIMETERS), spans[2].label)
    }

    // ── layoutWearStripRail (2026-07-18 dimension-rail rework) ─────────────────────────────

    /** A cheap deterministic stand-in for `Paint.measureText` in these pure JVM tests. */
    private fun charWidth(s: String, ptPerChar: Float = 6f) = s.length * ptPerChar

    @Test
    fun `ordinary well-spaced spans all land on row 0 with inward arrows`() {
        val spans = listOf(
            WearRailSpan(0f, 60f, "60mm"),
            WearRailSpan(60f, 190f, "130mm"),
            WearRailSpan(190f, 400f, "210mm"),
        )
        val layout = layoutWearStripRail(spans, xAtStripMm = { it }, labelWidthPt = { charWidth(it) })
        assertEquals(3, layout.size)
        layout.forEach {
            assertEquals(0, it.labelRow)
            assertTrue(it.arrowInward)
        }
    }

    @Test
    fun `narrow adjacent spans bump the colliding label to a fallback row`() {
        // Two very short, back-to-back spans with wide labels — row 0 alone would have both
        // labels overlap horizontally (this is the crowding case a short wear band + a tight
        // inter-band gap produces on a real liner).
        val spans = listOf(
            WearRailSpan(0f, 10f, "12.345mm"),
            WearRailSpan(10f, 20f, "67.890mm"),
        )
        val layout = layoutWearStripRail(spans, xAtStripMm = { it }, labelWidthPt = { charWidth(it) })
        assertEquals(2, layout.size)
        assertEquals(0, layout[0].labelRow)
        assertTrue("the second, colliding label must move off row 0", layout[1].labelRow > 0)
    }

    @Test
    fun `a label wider than its span is centered on the span midpoint, never dropped`() {
        val spans = listOf(WearRailSpan(0f, 5f, "999.999mm"))
        val layout = layoutWearStripRail(spans, xAtStripMm = { it * 10f }, labelWidthPt = { charWidth(it) })
        assertEquals(1, layout.size)
        val expectedMid = (layout[0].x0Pt + layout[0].x1Pt) / 2f
        assertEquals(expectedMid, layout[0].labelCxPt, 1e-3f)
        assertFalse("arrows must flip outward when the label overhangs the span", layout[0].arrowInward)
    }

    @Test
    fun `rail geometry stays inside the strip bounds end to end`() {
        // Reviewed example: liner-1, 400mm long, two recorded wear bands.
        val linerLen = 400f
        val clamped = bands(60f to 130f, 290f to 90f)
        val railSpans = buildWearStripRailSpans(linerLen, clamped, UnitSystem.INCHES)
        val stripLeft = 100f; val stripRight = 700f
        val ptPerMm = (stripRight - stripLeft) / linerLen
        val layout = layoutWearStripRail(
            railSpans,
            xAtStripMm = { mm -> stripLeft + mm * ptPerMm },
            labelWidthPt = { charWidth(it) },
        )
        assertTrue(layout.isNotEmpty())
        assertEquals(stripLeft, layout.first().x0Pt, 1e-3f)
        assertEquals(stripRight, layout.last().x1Pt, 1e-3f)
        layout.forEach {
            assertTrue(it.x0Pt >= stripLeft - 1e-3f)
            assertTrue(it.x1Pt <= stripRight + 1e-3f)
        }
    }

    // ── computeWearStripRadii — common-factor scaling (2026-07-18 SVG review, defect 1) ──

    @Test
    fun `radii within budget are left unscaled`() {
        val radii = computeWearStripRadii(
            linerOdMm = 40f, aftDiaMm = 30f, fwdDiaMm = 35f, ptPerMm = 1f, maxRadiusPt = 100f,
        )
        assertEquals(20f, radii.linerRPt, 1e-3f)
        assertEquals(15f, radii.aftRPt, 1e-3f)
        assertEquals(17.5f, radii.fwdRPt, 1e-3f)
    }

    @Test
    fun `common-factor scaling preserves the liner-vs-neighbor radius ratio when over budget`() {
        // Liner OD 200mm over a 175mm neighbor — same 8-over-7 ratio as the reviewed
        // liner-OD-over-shaft-OD case that exposed the bug: independent capping
        // (min(raw, budget) applied to each) would clamp BOTH to the same 18pt cap,
        // rendering the liner and its neighbor at an identical radius and erasing
        // the visible diameter step entirely.
        val radii = computeWearStripRadii(
            linerOdMm = 200f, aftDiaMm = 175f, fwdDiaMm = 175f, ptPerMm = 1.66f, maxRadiusPt = 18f,
        )
        assertEquals("liner (the largest raw radius) hits the budget exactly",
            18f, radii.linerRPt, 1e-3f)
        assertTrue("neighbor radius must stay proportionally smaller, not clamp to the same value",
            radii.aftRPt < radii.linerRPt - 1f)
        assertEquals(175f / 200f, radii.aftRPt / radii.linerRPt, 1e-3f)
        assertEquals(radii.aftRPt, radii.fwdRPt, 1e-6f)
    }

    @Test
    fun `only the smaller neighbor is scaled down when just the liner is over budget`() {
        // liner raw radius over budget, aft neighbor raw radius already under it —
        // aft should render at its true size (scale only ever shrinks, never grows).
        val radii = computeWearStripRadii(
            linerOdMm = 200f, aftDiaMm = 10f, fwdDiaMm = 200f, ptPerMm = 1.66f, maxRadiusPt = 18f,
        )
        assertEquals(18f, radii.linerRPt, 1e-3f)
        val expectedAft = (10f * 0.5f * 1.66f) * (18f / (200f * 0.5f * 1.66f))
        assertEquals(expectedAft, radii.aftRPt, 1e-3f)
        assertEquals(radii.linerRPt, radii.fwdRPt, 1e-6f)
    }

    @Test
    fun `zero budget collapses all radii to zero without throwing`() {
        val radii = computeWearStripRadii(
            linerOdMm = 200f, aftDiaMm = 175f, fwdDiaMm = 175f, ptPerMm = 1.66f, maxRadiusPt = 0f,
        )
        assertEquals(0f, radii.linerRPt, 1e-6f)
        assertEquals(0f, radii.aftRPt, 1e-6f)
        assertEquals(0f, radii.fwdRPt, 1e-6f)
    }

    // ── neighborDiaMmAtAft / neighborDiaMmAtFwd ───────────────────────────────

    @Test
    fun `aft neighbor body diameter is found at the abutting edge`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 90f)),
            liners = listOf(liner("a", 100f, 100f)),
        )
        assertEquals(90f, neighborDiaMmAtAft(spec, linerAftMm = 100f)!!, 1e-6f)
    }

    @Test
    fun `fwd neighbor taper start diameter is found at the abutting edge`() {
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            tapers = listOf(Taper(startFromAftMm = 200f, lengthMm = 50f, startDiaMm = 70f, endDiaMm = 60f)),
            liners = listOf(liner("a", 100f, 100f)),
        )
        assertEquals(70f, neighborDiaMmAtFwd(spec, linerFwdMm = 200f)!!, 1e-6f)
    }

    @Test
    fun `no neighbor present returns null`() {
        val spec = ShaftSpec(overallLengthMm = 500f, liners = listOf(liner("a", 100f, 100f)))
        assertNull(neighborDiaMmAtAft(spec, linerAftMm = 100f))
        assertNull(neighborDiaMmAtFwd(spec, linerFwdMm = 200f))
    }

    // ── buildLinerAnchorLabel ──────────────────────────────────────────────────

    @Test
    fun `anchor label reuses LinerSpanBuilder math and reads FROM the nearer SET`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f),
                Taper(startFromAftMm = 800f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 100f),
            ),
            liners = listOf(liner("a", 250f, 100f)),  // 250mm from AFT SET(0), nearer AFT
        )
        val sets = SetPositions(aftSETxMm = 0.0, fwdSETxMm = 1000.0)
        val label = buildLinerAnchorLabel(spec, spec.liners[0], sets, UnitSystem.MILLIMETERS)
        assertTrue("expected AFT S.E.T. wording, got: $label", label.contains("AFT S.E.T."))
        assertTrue("expected the 250mm offset in the label, got: $label", label.contains("250"))
    }

    @Test
    fun `anchor label returns empty string for an unknown liner`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val sets = SetPositions(aftSETxMm = 0.0, fwdSETxMm = 1000.0)
        val label = buildLinerAnchorLabel(spec, liner("ghost", 0f, 10f), sets, UnitSystem.MILLIMETERS)
        assertEquals("", label)
    }

    // ── formatMinDiaLabelOrNull ────────────────────────────────────────────────

    @Test
    fun `min-diameter label is omitted when unrecorded`() {
        assertNull(formatMinDiaLabelOrNull(0f, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `min-diameter label is printed when recorded`() {
        val label = formatMinDiaLabelOrNull(139.7f, UnitSystem.MILLIMETERS)
        assertTrue(label != null && label.contains("139.7"))
    }
}
