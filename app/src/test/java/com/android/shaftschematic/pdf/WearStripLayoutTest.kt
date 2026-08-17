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
 * `docs/archive/LinerWearAreas_Proposal.md` §6.2/§9). Mirrors the style of
 * `PdfLayoutBoundsTest` — plain JVM assertions, no Robolectric — but exercises the
 * extracted functions in `WearStripLayout.kt` directly rather than replicating
 * their formulas in the test.
 */
class WearStripLayoutTest {

    private fun liner(id: String, startMm: Float, lengthMm: Float, odMm: Float = 80f) =
        Liner(id = id, startFromAftMm = startMm, lengthMm = lengthMm, odMm = odMm)

    private fun spot(linerId: String, startMm: Float = 0f, lengthMm: Float = 25f, minDiaMm: Float = 0f) =
        WearSpot(linerId = linerId, startMm = startMm, lengthMm = lengthMm, minDiaMm = minDiaMm)

    // ── collectWearLinerGroups (EVERY drawable liner gets a strip, spots or not) ──

    @Test
    fun `liner with no spots still gets a strip with an empty spot list`() {
        val liners = listOf(liner("a", 0f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(spots = emptyList()))
        assertEquals(1, groups.size)
        assertEquals("a", groups[0].liner.id)
        assertTrue(groups[0].spots.isEmpty())
    }

    @Test
    fun `orphan spot referencing missing liner is dropped but the real liner keeps its strip`() {
        val liners = listOf(liner("a", 0f, 200f))
        val record = WearRecord(spots = listOf(spot(linerId = "ghost")))
        val groups = collectWearLinerGroups(liners, record)
        assertEquals(1, groups.size)
        assertEquals("a", groups[0].liner.id)
        assertTrue("the ghost spot must not attach to a different liner", groups[0].spots.isEmpty())
    }

    @Test
    fun `degenerate liners with zero length or zero OD get no strip`() {
        // A zero-length/zero-OD liner can't be drawn (drawWearDetailStrip bails) — including it
        // would only claim an empty grid cell.
        val liners = listOf(
            liner("ok", 0f, 200f),
            liner("no-len", 300f, 0f),
            liner("no-od", 400f, 100f, odMm = 0f),
        )
        val groups = collectWearLinerGroups(liners, WearRecord(spots = emptyList()))
        assertEquals(listOf("ok"), groups.map { it.liner.id })
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

    // ── Strip election (WearRecord.stripComponentIds) ─────────────────────────

    @Test
    fun `a null election prints every drawable liner - the default sheet`() {
        val liners = listOf(liner("a", 0f, 200f), liner("b", 300f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(), stripComponentIds = null)
        assertEquals(listOf("a", "b"), groups.map { it.liner.id })
        assertEquals(defaultWearStripComponentIds(liners), groups.map { it.liner.id })
    }

    @Test
    fun `an explicit election filters the strips down to the elected liners`() {
        val liners = listOf(liner("a", 0f, 200f), liner("b", 300f, 200f), liner("c", 600f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(), stripComponentIds = listOf("c", "a"))
        // Election order is irrelevant — strips always print aft→fwd.
        assertEquals(listOf("a", "c"), groups.map { it.liner.id })
    }

    @Test
    fun `an empty election prints no strips at all`() {
        val liners = listOf(liner("a", 0f, 200f), liner("b", 300f, 200f))
        assertTrue(collectWearLinerGroups(liners, WearRecord(), stripComponentIds = emptyList()).isEmpty())
    }

    @Test
    fun `elected ids that resolve to no liner are skipped, never fatal`() {
        // Taper/body ids are legal in the election (they get strips in a later phase) and an id
        // whose component was edited away is an orphan — both are simply skipped at render.
        val liners = listOf(liner("a", 0f, 200f))
        val groups = collectWearLinerGroups(
            liners, WearRecord(),
            stripComponentIds = listOf("a", "taper-1", "auto_body_0.000_100.000", "ghost"),
        )
        assertEquals(listOf("a"), groups.map { it.liner.id })
    }

    @Test
    fun `the default election skips degenerate liners, same as the strip collector`() {
        val liners = listOf(liner("ok", 0f, 200f), liner("no-len", 300f, 0f), liner("no-od", 400f, 100f, odMm = 0f))
        assertEquals(listOf("ok"), defaultWearStripComponentIds(liners))
    }

    // ── selectWearStripWindowsForPage ─────────────────────────────────────────

    private fun linerWindow(id: String, startMm: Float, lengthMm: Float): WearStripWindow =
        WearStripWindow(listOf(WearStripComponentSeg(WearStripComponent(
            id, WearStripComponentKind.LINER, startMm, startMm + lengthMm, 100f, 100f,
        ))))

    @Test
    fun `at or under the page limit produces no overflow`() {
        val windows = (1..3).map { linerWindow("l$it", it * 100f, 50f) }
        val selection = selectWearStripWindowsForPage(windows)
        assertEquals(3, selection.onPage.size)
        assertTrue(selection.overflow.isEmpty())
    }

    @Test
    fun `over the page limit overflows the remainder`() {
        val windows = (1..5).map { linerWindow("l$it", it * 100f, 50f) }
        val selection = selectWearStripWindowsForPage(windows)
        assertEquals(3, selection.onPage.size)
        assertEquals(2, selection.overflow.size)
        // Overflow keeps the aft→fwd tail, not an arbitrary subset.
        assertEquals(listOf("l4", "l5"), selection.overflow.map { w -> w.components.single().id })
    }

    // ── determineWearPdfMode (rule: 0 -> profile form, 1 -> combined, 2+ -> grid;
    // the shaft profile is always kept on top. The count is the shaft's drawable
    // LINER count, since every liner now gets a strip whether or not it has recorded wear) ──

    @Test
    fun `zero liners selects the profile form`() {
        assertEquals(WearPdfMode.PROFILE_FORM, determineWearPdfMode(0))
    }

    @Test
    fun `one liner selects the combined page`() {
        assertEquals(WearPdfMode.COMBINED, determineWearPdfMode(1))
    }

    @Test
    fun `two or more liners select the grid`() {
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(WEAR_STRIP_GRID_MIN_LINERS))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(2))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(3))
        assertEquals(WearPdfMode.GRID, determineWearPdfMode(10))
    }

    @Test
    fun `a shaft with no liners resolves to the profile form via collectWearLinerGroups`() {
        // composeWearPdf's mode is `determineWearPdfMode(collectWearLinerGroups(...).size)` — no
        // separate pure function re-derives the liner count, it reuses the already-tested
        // grouping/orphan-drop logic. Spelled out explicitly here for this feature's mode switch.
        val groups = collectWearLinerGroups(emptyList(), WearRecord(spots = listOf(spot("ghost"))))
        assertEquals(WearPdfMode.PROFILE_FORM, determineWearPdfMode(groups.size))
    }

    @Test
    fun `one liner with no recorded wear still resolves to the combined page`() {
        // Liners appear on the wear sheet regardless of recorded wear (normal shop
        // operating procedure).
        val liners = listOf(liner("a", 0f, 200f))
        val groups = collectWearLinerGroups(liners, WearRecord(spots = emptyList()))
        assertEquals(WearPdfMode.COMBINED, determineWearPdfMode(groups.size))
    }

    @Test
    fun `exactly two liners cross into the grid even when only one has wear`() {
        val liners = listOf(liner("a", 0f, 200f), liner("b", 300f, 200f))
        val record = WearRecord(spots = listOf(spot("a")))
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

    // ── Profile-height preference / per-strip cap (device feedback) ────────────────

    @Test
    fun `preferred profile height gives the surplus to the strips`() {
        val areaTop = 88f; val areaBottom = 524f
        val layout = computeWearVerticalLayout(
            areaTop, areaBottom, stripCount = 2,
            preferredProfileHeightPt = 140f,  // well above the 70pt floor
        )
        assertEquals("profile shrinks to its preferred height",
            140f, layout.profileBottom - layout.profileTop, 1e-3f)
        // Nothing wasted: the strips absorbed everything the profile gave back.
        assertEquals(areaBottom, layout.stripBottoms.last(), 1e-3f)
        val perStrip = layout.stripBottoms[0] - layout.stripTops[0]
        assertTrue("strips grew past their preferred height ($perStrip)",
            perStrip > WEAR_STRIP_HEIGHT_PT + 1e-3f)
    }

    @Test
    fun `preferred profile height never squeezes the profile below the minimum`() {
        val layout = computeWearVerticalLayout(
            areaTop = 88f, areaBottom = 524f, stripCount = 2,
            preferredProfileHeightPt = 10f,  // absurdly small — the floor must win
        )
        assertEquals(WEAR_MIN_PROFILE_HEIGHT_PT, layout.profileBottom - layout.profileTop, 1e-3f)
    }

    @Test
    fun `strip growth cap returns leftover height to the profile`() {
        val areaTop = 88f; val areaBottom = 524f
        val layout = computeWearVerticalLayout(
            areaTop, areaBottom, stripCount = 1,
            preferredProfileHeightPt = 100f,
            maxStripHeightPt = 170f,
        )
        assertEquals("strip capped", 170f, layout.stripBottoms[0] - layout.stripTops[0], 1e-3f)
        assertTrue("profile absorbed the overflow past the cap",
            layout.profileBottom - layout.profileTop > 100f + 1e-3f)
        assertEquals(areaBottom, layout.stripBottoms.last(), 1e-3f)
    }

    @Test
    fun `default parameters reproduce the old absorb-all-slack layout`() {
        val defaults = computeWearVerticalLayout(88f, 524f, stripCount = 3)
        val explicit = computeWearVerticalLayout(
            88f, 524f, stripCount = 3,
            preferredProfileHeightPt = Float.MAX_VALUE,
            maxStripHeightPt = Float.MAX_VALUE,
        )
        assertEquals(defaults.profileBottom, explicit.profileBottom, 1e-3f)
        defaults.stripTops.forEachIndexed { i, t -> assertEquals(t, explicit.stripTops[i], 1e-3f) }
        defaults.stripBottoms.forEachIndexed { i, b -> assertEquals(b, explicit.stripBottoms[i], 1e-3f) }
    }

    // ── computeWearStripGridLayout (WearPdfMode.GRID — two strips per row) ─────────

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

    @Test
    fun `grid layout forwards the profile preference`() {
        val g = computeWearStripGridLayout(
            88f, 524f, gridLeft, gridRight, stripCount = 2,  // 2 columns → one row
            preferredProfileHeightPt = 140f,
        )
        assertEquals(140f, g.profileBottom - g.profileTop, 1e-3f)
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

    // ── sharedWearStripPtPerMm — one scale per sheet, so lengths read true ─────

    private fun innerWidth(left: Float, right: Float) = right - left - 2f * WEAR_STRIP_STUB_WIDTH_PT

    @Test
    fun `two liners in equal cells draw at their true length ratio`() {
        // The bug this pins: each strip scaled to fill its own cell, so a half-length liner
        // printed just as wide as its sibling (on-device report).
        val left = 36f; val right = 396f
        val inner = innerWidth(left, right)
        val shared = sharedWearStripPtPerMm(listOf(400f, 200f), listOf(inner, inner))
        val long = computeWearStripHorizontalLayout(left, right, 400f, ptPerMmOverride = shared)
        val short = computeWearStripHorizontalLayout(left, right, 200f, ptPerMmOverride = shared)
        val longW = long.linerRightPt - long.linerLeftPt
        val shortW = short.linerRightPt - short.linerLeftPt
        assertEquals(2f, longW / shortW, 1e-3f)
        // The longest strip still fills its cell exactly; the short one is centered in the slack.
        assertEquals(inner, longW, 1e-3f)
        assertEquals((left + right) / 2f, (short.linerLeftPt + short.linerRightPt) / 2f, 1e-3f)
    }

    @Test
    fun `the shared scale keeps every strip inside its own cell`() {
        val left = 36f; val right = 396f
        val inner = innerWidth(left, right)
        val lengths = listOf(120f, 900f, 55f)
        val shared = sharedWearStripPtPerMm(lengths, List(lengths.size) { inner })
        lengths.forEach { len ->
            val h = computeWearStripHorizontalLayout(left, right, len, ptPerMmOverride = shared)
            assertTrue(h.linerLeftPt - h.stubWidthPt >= left - 1e-3f)
            assertTrue(h.linerRightPt + h.stubWidthPt <= right + 1e-3f)
        }
    }

    @Test
    fun `the shared scale is capped, so a page of short liners does not explode`() {
        val shared = sharedWearStripPtPerMm(listOf(2f, 3f), listOf(600f, 600f))
        assertEquals(WEAR_STRIP_MAX_PT_PER_MM, shared, 1e-6f)
    }

    @Test
    fun `the shared scale is NOT floored - a very long liner shrinks everything together`() {
        // Flooring a SHARED scale would overflow the longest strip's cell; proportion wins.
        val inner = innerWidth(36f, 396f)
        val shared = sharedWearStripPtPerMm(listOf(100000f, 200f), listOf(inner, inner))
        assertTrue("shared scale must be free to fall below the per-strip floor",
            shared < WEAR_STRIP_MIN_PT_PER_MM)
        val h = computeWearStripHorizontalLayout(36f, 396f, 100000f, ptPerMmOverride = shared)
        assertTrue(h.linerRightPt + h.stubWidthPt <= 396f + 1e-3f)
    }

    @Test
    fun `an empty page yields the cap and no strips to place`() {
        assertEquals(WEAR_STRIP_MAX_PT_PER_MM, sharedWearStripPtPerMm(emptyList(), emptyList()), 1e-6f)
    }

    @Test
    fun `a null override reproduces the legacy per-strip layout exactly`() {
        // The undercut document's strips call through this same function with no override —
        // their behavior must be byte-identical.
        listOf(1f, 150f, 4000f, 100000f).forEach { len ->
            val legacy = computeWearStripHorizontalLayout(36f, 756f, len)
            val explicitNull = computeWearStripHorizontalLayout(36f, 756f, len, ptPerMmOverride = null)
            assertEquals(legacy.ptPerMm, explicitNull.ptPerMm, 0f)
            assertEquals(legacy.linerLeftPt, explicitNull.linerLeftPt, 0f)
            assertEquals(legacy.linerRightPt, explicitNull.linerRightPt, 0f)
            assertEquals(legacy.stubWidthPt, explicitNull.stubWidthPt, 0f)
        }
    }

    @Test
    fun `a non-positive override falls back to the per-strip fit`() {
        val legacy = computeWearStripHorizontalLayout(36f, 756f, 150f)
        val zero = computeWearStripHorizontalLayout(36f, 756f, 150f, ptPerMmOverride = 0f)
        assertEquals(legacy.ptPerMm, zero.ptPerMm, 0f)
        assertEquals(legacy.linerLeftPt, zero.linerLeftPt, 0f)
    }

    // ── Hidden shaft profile (WearRecord.showShaftProfile = false) ────────────

    @Test
    fun `a hidden profile band leaves no phantom gap and hands its height to the strips`() {
        // What composeWearPdf passes with showShaftProfile = false: no floor, no preference,
        // no profile→strips gap, no per-strip growth cap.
        val areaTop = 88f; val areaBottom = 524f
        val hidden = computeWearVerticalLayout(
            areaTop, areaBottom, stripCount = 2,
            minProfileHeightPt = 0f,
            profileToStripsGapPt = 0f,
            preferredProfileHeightPt = 0f,
            maxStripHeightPt = Float.MAX_VALUE,
        )
        assertEquals("the band starts where the profile would have", areaTop, hidden.profileBottom, 1e-3f)
        assertEquals(areaTop, hidden.stripTops.first(), 1e-3f)
        assertEquals(areaBottom, hidden.stripBottoms.last(), 1e-3f)

        val shown = computeWearVerticalLayout(
            areaTop, areaBottom, stripCount = 2,
            preferredProfileHeightPt = 140f,
            maxStripHeightPt = WEAR_STRIP_HEIGHT_PT,
        )
        val hiddenH = hidden.stripBottoms[0] - hidden.stripTops[0]
        val shownH = shown.stripBottoms[0] - shown.stripTops[0]
        assertTrue("hiding the profile must grow the strips ($hiddenH vs $shownH)", hiddenH > shownH)
    }

    // ── computeWearStripInnerLayout (dimension rail: fixed budget, independent of
    // spot count; fallback label rows ABOVE the rail line) ──────────────────────

    @Test
    fun `inner layout fits the cylinder and the full rail row budget in an ordinary strip`() {
        val stripTop = 100f
        val stripBottom = stripTop + 15f + WEAR_STRIP_LABEL_HEADROOM_PT + 40f +
            2 * WEAR_STRIP_ROW_HEIGHT_PT + WEAR_RAIL_WITNESS_RUN_PT
        val inner = computeWearStripInnerLayout(
            stripTop = stripTop, stripBottom = stripBottom, titleHeightPt = 15f,
        )
        // Rail sits ABOVE the cylinder (railY <= cylTop), title below (cylBottom below).
        assertTrue(inner.railY >= stripTop - 1e-3f)
        assertTrue(inner.railY <= inner.cylTop + 1e-3f)
        assertTrue(inner.cylBottom > inner.cylTop)
        assertTrue(inner.cylBottom <= stripBottom + 1e-3f)
        assertEquals(WEAR_RAIL_MAX_LABEL_ROWS, inner.railLabelRows)
        // Only the witness run separates the rail from the cylinder; the label rows live above.
        assertEquals(inner.cylTop - WEAR_RAIL_WITNESS_RUN_PT, inner.railY, 1e-3f)
    }

    @Test
    fun `the fallback label band sits entirely above the rail and inside the strip`() {
        // The regression this pins: rows stacked BELOW the rail land in the witness lines' run
        // between the rail and the cylinder, so the value prints across them (on-device report).
        val stripTop = 100f
        val stripBottom = stripTop + 15f + WEAR_STRIP_LABEL_HEADROOM_PT + 40f +
            2 * WEAR_STRIP_ROW_HEIGHT_PT + WEAR_RAIL_WITNESS_RUN_PT
        val inner = computeWearStripInnerLayout(
            stripTop = stripTop, stripBottom = stripBottom, titleHeightPt = 15f,
        )
        val bandTop = inner.railY - inner.railLabelRows * WEAR_STRIP_ROW_HEIGHT_PT
        assertTrue("label rows must fit above the rail, inside the strip", bandTop >= stripTop - 1e-3f)
        assertTrue("the label band must end at the rail line", inner.railY <= inner.cylTop + 1e-3f)
        assertTrue("the rail-to-cylinder run belongs to the witness lines alone",
            inner.cylTop - inner.railY <= WEAR_RAIL_WITNESS_RUN_PT + 1e-3f)
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
        assertTrue("railY must not fall below stripTop", inner.railY >= 100f - 1e-3f)
        assertTrue("railY must sit at or above the cylinder top", inner.railY <= inner.cylTop + 1e-3f)
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
        // After reserving the title (+ headroom) at the bottom and the rail's witness run, the
        // space left above the rail is between 1 and 2 label rows: the cylinder collapses to zero
        // first, then the rail drops from its full budget to a single row.
        val stripBottom = titleH + WEAR_STRIP_LABEL_HEADROOM_PT + WEAR_RAIL_WITNESS_RUN_PT + 18f
        val inner = computeWearStripInnerLayout(stripTop, stripBottom, titleH, rowHeightPt = rowH)
        val cylBottomExpected = stripBottom - titleH - WEAR_STRIP_LABEL_HEADROOM_PT
        assertEquals(cylBottomExpected, inner.cylBottom, 1e-3f)
        assertEquals("cylinder squeezed to zero height", inner.cylTop, inner.cylBottom, 1e-3f)
        assertEquals(1, inner.railLabelRows)
        assertTrue(inner.railY >= stripTop - 1e-3f)
        assertTrue(inner.railY <= inner.cylTop + 1e-3f)
    }

    // ── computeWearStripInnerLayout — label headroom ──

    @Test
    fun `label headroom is reserved between the cylinder and the title`() {
        // titleHeightPt here stands in for the title text's own line height (no
        // ad hoc fudge folded in) — the headroom must appear as an explicit,
        // separate gap between the cylinder bottom and the title, not be absorbed into it.
        val inner = computeWearStripInnerLayout(
            stripTop = 100f, stripBottom = 300f, titleHeightPt = 9f,
        )
        assertEquals(300f - 9f - WEAR_STRIP_LABEL_HEADROOM_PT, inner.cylBottom, 1e-3f)
    }

    @Test
    fun `title plus headroom exceeding a pathologically short strip still clamps, never inverts`() {
        val inner = computeWearStripInnerLayout(
            stripTop = 0f, stripBottom = 12f, titleHeightPt = 9f,
        )
        assertTrue("cylBottom must not fall below stripTop even though title+headroom exceed the strip",
            inner.cylBottom >= 0f - 1e-3f)
        assertTrue(inner.cylBottom >= inner.cylTop)
        assertTrue("cylTop must not fall below stripTop", inner.cylTop >= 0f - 1e-3f)
        assertTrue(inner.railY >= 0f - 1e-3f)
        assertTrue(inner.railY <= inner.cylTop + 1e-3f)
    }

    // ── buildWearStripRailSpans (dimension rail) ────────────────────────

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
    fun `no bands at all yields one full-length span - the spotless-liner and blank-template rail`() {
        // A liner with no recorded wear (every liner gets a strip) and every
        // liner on the blank write-in template share this rail: a single span across the whole
        // liner. The span still exists in the PURE MATH — it anchors the x-positions of the two
        // liner-edge witness bars — but the COMPOSER draws a band-less rail as
        // those edge bars only: no spanning line, no arrowheads, no label (the full-length span
        // would just re-state the liner's own length, and the rail measures distances to wear
        // areas). Only DRAWING changed; buildWearStripRailSpans still returns the span.
        val spans = buildWearStripRailSpans(250f, emptyList(), UnitSystem.MILLIMETERS)
        assertEquals(1, spans.size)
        assertEquals(0f, spans[0].startMm, 1e-6f)
        assertEquals(250f, spans[0].endMm, 1e-6f)
        assertEquals(formatLenDim(250.0, UnitSystem.MILLIMETERS), spans[0].label)
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

    // ── layoutWearStripRail (dimension-rail rework) ─────────────────────────────

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
        assertFalse("an overhanging label cannot seat in the line", layout[0].seatsInBreak)
        // ...but the 50 pt span itself still holds two arrowheads, so they stay inward: the
        // tips-in convention is for narrow SPANS, not for wide spans with a wide label.
        assertTrue("a wide span keeps inward arrows when its label falls back", layout[0].arrowInward)
    }

    @Test
    fun `only a span too narrow for both arrowheads prints them outward`() {
        // 4 pt of rail between the witness lines — two 4 pt heads cannot live inside it.
        val layout = layoutWearStripRail(
            listOf(WearRailSpan(0f, 4f, "1mm")),
            xAtStripMm = { it },
            labelWidthPt = { charWidth(it) },
        )
        assertFalse("cramped span flips its arrows outward", layout[0].arrowInward)
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

    // ── computeWearStripRadii — uniform strip height ──

    @Test
    fun `liner cylinder always fills the vertical budget regardless of scale or od`() {
        // Strips of different liner lengths/ODs must render the liner at one common height.
        val a = computeWearStripRadii(
            linerOdMm = 140f, aftDiaMm = 120f, fwdDiaMm = 120f, maxRadiusPt = 31f,
        )
        val b = computeWearStripRadii(
            linerOdMm = 80f, aftDiaMm = 60f, fwdDiaMm = 60f, maxRadiusPt = 31f,
        )
        assertEquals(31f, a.linerRPt, 1e-3f)
        assertEquals(31f, b.linerRPt, 1e-3f)
    }

    @Test
    fun `neighbor stubs keep their true diameter ratio to the liner`() {
        val radii = computeWearStripRadii(
            linerOdMm = 200f, aftDiaMm = 175f, fwdDiaMm = 10f, maxRadiusPt = 18f,
        )
        assertEquals(18f * (175f / 200f), radii.aftRPt, 1e-3f)
        assertEquals(18f * (10f / 200f), radii.fwdRPt, 1e-3f)
    }

    @Test
    fun `a neighbor larger than the liner clamps to the liner radius`() {
        val radii = computeWearStripRadii(
            linerOdMm = 100f, aftDiaMm = 140f, fwdDiaMm = 100f, maxRadiusPt = 20f,
        )
        assertEquals(20f, radii.aftRPt, 1e-3f)
        assertEquals(20f, radii.fwdRPt, 1e-3f)
    }

    @Test
    fun `zero budget or zero od collapses all radii to zero without throwing`() {
        val zeroBudget = computeWearStripRadii(
            linerOdMm = 200f, aftDiaMm = 175f, fwdDiaMm = 175f, maxRadiusPt = 0f,
        )
        assertEquals(0f, zeroBudget.linerRPt, 1e-6f)
        assertEquals(0f, zeroBudget.aftRPt, 1e-6f)
        assertEquals(0f, zeroBudget.fwdRPt, 1e-6f)

        val zeroOd = computeWearStripRadii(
            linerOdMm = 0f, aftDiaMm = 175f, fwdDiaMm = 175f, maxRadiusPt = 20f,
        )
        assertEquals(0f, zeroOd.linerRPt, 1e-6f)
        assertEquals(0f, zeroOd.aftRPt, 1e-6f)
        assertEquals(0f, zeroOd.fwdRPt, 1e-6f)
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

    @Test
    fun `anchor suffix wording matches the printed label for both SETs`() {
        // The blank template prints a writing rule + this suffix where the value would go — the
        // wording must be exactly what buildLinerAnchorLabel appends after the number.
        assertEquals("FROM AFT S.E.T.", linerAnchorSuffix(com.android.shaftschematic.model.LinerAnchor.AFT_SET))
        assertEquals("FROM FWD S.E.T.", linerAnchorSuffix(com.android.shaftschematic.model.LinerAnchor.FWD_SET))
    }

    @Test
    fun `blank anchor suffix offers both directions for circling`() {
        // The write-in template must not presume a direction — both print, machinist circles one.
        assertTrue(WEAR_BLANK_ANCHOR_SUFFIX.contains("AFT / FWD"))
        assertTrue(WEAR_BLANK_ANCHOR_SUFFIX.startsWith("FROM"))
        assertTrue(WEAR_BLANK_ANCHOR_SUFFIX.endsWith("S.E.T."))
    }

    // ── Span anchor labels — the SAME construction for taper/body strips ───────
    //
    // Wear is measured from a S.E.T. or a liner edge, so a taper/body strip needs the anchor
    // dimension a liner strip has always printed (on-device answer). The rule must be the
    // liner's exactly: nearer edge to its own SET, ties AFT.

    /** A 1000 mm shaft with end tapers, so AFT SET = 0 and FWD SET = 1000. */
    private fun setSpec(vararg extra: Taper): ShaftSpec = ShaftSpec(
        overallLengthMm = 1000f,
        tapers = listOf(
            Taper(id = "aftEnd", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f),
            Taper(id = "fwdEnd", startFromAftMm = 800f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 100f),
        ) + extra.toList(),
    )

    private val sets1000 = SetPositions(aftSETxMm = 0.0, fwdSETxMm = 1000.0)

    @Test
    fun `a span nearer the AFT SET is measured from it`() {
        val spec = setSpec()
        val dim = wearStripAnchorForSpan(spec, 250f, 350f, sets1000)
        assertEquals(com.android.shaftschematic.model.LinerAnchor.AFT_SET, dim.anchor)
        assertEquals(250.0, dim.distanceMm, 1e-6)
        assertEquals(
            "250.000 mm FROM AFT S.E.T.",
            buildSpanAnchorLabel(spec, 250f, 350f, sets1000, UnitSystem.MILLIMETERS),
        )
    }

    @Test
    fun `a span nearer the FWD SET is measured from it, FWD edge first`() {
        val spec = setSpec()
        // FWD edge at 900 → 100 from the FWD SET; AFT edge at 700 → 700 from the AFT SET.
        val dim = wearStripAnchorForSpan(spec, 700f, 900f, sets1000)
        assertEquals(com.android.shaftschematic.model.LinerAnchor.FWD_SET, dim.anchor)
        assertEquals(100.0, dim.distanceMm, 1e-6)
        assertEquals(
            "100.000 mm FROM FWD S.E.T.",
            buildSpanAnchorLabel(spec, 700f, 900f, sets1000, UnitSystem.MILLIMETERS),
        )
    }

    @Test
    fun `an equidistant span goes to the AFT SET`() {
        // AFT edge 300 from the AFT SET, FWD edge 300 from the FWD SET — the tie goes AFT,
        // exactly as the liner rule does.
        val spec = setSpec()
        val dim = wearStripAnchorForSpan(spec, 300f, 700f, sets1000)
        assertEquals(com.android.shaftschematic.model.LinerAnchor.AFT_SET, dim.anchor)
        assertEquals(300.0, dim.distanceMm, 1e-6)
    }

    @Test
    fun `the liner label is exactly the span label over the liner's own span`() {
        // The pin for the shared helper: refactoring the liner path onto it must not move a
        // single character of what a liner strip prints.
        listOf(
            liner("aftish", 250f, 100f),   // AFT-referenced
            liner("fwdish", 700f, 200f),   // FWD-referenced
            liner("tie", 300f, 400f),      // equidistant — AFT wins
        ).forEach { ln ->
            val spec = setSpec().copy(liners = listOf(ln))
            listOf(UnitSystem.MILLIMETERS, UnitSystem.INCHES).forEach { unit ->
                assertEquals(
                    "liner ${ln.id} in $unit",
                    buildSpanAnchorLabel(
                        spec, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm, sets1000, unit,
                    ),
                    buildLinerAnchorLabel(spec, ln, sets1000, unit),
                )
            }
        }
    }

    @Test
    fun `liner anchor labels keep their printed wording`() {
        // Byte-level pin of the two shipped forms, so neither the number nor the wording can
        // drift under the shared helper.
        val aft = setSpec().copy(liners = listOf(liner("a", 250f, 100f)))
        assertEquals(
            "250.000 mm FROM AFT S.E.T.",
            buildLinerAnchorLabel(aft, aft.liners[0], sets1000, UnitSystem.MILLIMETERS),
        )
        val fwd = setSpec().copy(liners = listOf(liner("f", 700f, 200f)))
        assertEquals(
            "100.000 mm FROM FWD S.E.T.",
            buildLinerAnchorLabel(fwd, fwd.liners[0], sets1000, UnitSystem.MILLIMETERS),
        )
    }

    @Test
    fun `the title alignment cue reads the same anchor the label does`() {
        val aft = setSpec().copy(liners = listOf(liner("a", 250f, 100f)))
        assertEquals(com.android.shaftschematic.model.LinerAnchor.AFT_SET, linerAnchorForPdf(aft, aft.liners[0]))
        val fwd = setSpec().copy(liners = listOf(liner("f", 700f, 200f)))
        assertEquals(com.android.shaftschematic.model.LinerAnchor.FWD_SET, linerAnchorForPdf(fwd, fwd.liners[0]))
        assertNull(linerAnchorForPdf(aft, liner("ghost", 0f, 10f)))
    }

    // ── packWearStripWindows — rows filled by ACTUAL drawn width, whitespace first ─

    /** Landscape US Letter content width, the real page the wear sheet packs into. */
    private val packLeft = 36f
    private val packRight = 756f
    private val packWidth = packRight - packLeft

    private val fullSpacing = WearStripSpacing(WEAR_STRIP_STUB_WIDTH_PT, WEAR_STRIP_COL_GAP_PT)
    private val tightSpacing = WearStripSpacing(WEAR_STRIP_STUB_MIN_PT, WEAR_STRIP_COL_GAP_MIN_PT)

    private fun packWindows(count: Int, lengthMm: Float): List<WearStripWindow> =
        (0 until count).map { linerWindow("p$it", it * 2000f, lengthMm) }

    private fun rowsOf(p: WearStripPacking): List<List<Int>> =
        p.cells.groupBy { it.row }.toSortedMap().values.map { row -> row.map { it.windowIndex } }

    /** Every packed cell must sit inside the content span, in order, without overlapping. */
    private fun assertCellsWellFormed(p: WearStripPacking) {
        assertEquals(
            "cells must map to windows 0..placedCount-1 in AFT→FWD order",
            (0 until p.placedCount).toList(),
            p.cells.map { it.windowIndex },
        )
        p.cells.forEach { c ->
            assertTrue("cell inside the content span", c.left >= packLeft - 1e-3f && c.right <= packRight + 1e-3f)
            assertTrue("cell has positive width", c.right > c.left)
        }
        rowsOf(p).indices.forEach { r ->
            val row = p.cells.filter { it.row == r }
            row.zipWithNext { a, b ->
                assertTrue("cells in a row must not overlap", b.left >= a.right - 1e-3f)
            }
            // Rows are centered: the slack at each margin is equal.
            val leadPad = row.first().left - packLeft
            val trailPad = packRight - row.last().right
            assertEquals("row $r is centered", leadPad, trailPad, 1e-2f)
        }
    }

    @Test
    fun `wearStripMaxRows gives the strips the profile band's row when it is hidden`() {
        assertEquals(2, wearStripMaxRows(showShaftProfile = true))
        assertEquals(3, wearStripMaxRows(showShaftProfile = false))
        assertEquals(WEAR_STRIP_MAX_ROWS_WITH_PROFILE, wearStripMaxRows(true))
        assertEquals(WEAR_STRIP_MAX_ROWS_NO_PROFILE, wearStripMaxRows(false))
    }

    /**
     * Largest scale for the row count the packer chose: either it is already at the cap, or a
     * nudge upward would need another row (or overrun the page).
     */
    private fun assertScaleMaximalForItsRows(p: WearStripPacking, windows: List<WearStripWindow>) {
        if (p.ptPerMm >= WEAR_STRIP_MAX_PT_PER_MM - 1e-4f) return
        val bumped = packWearStripWindows(
            windows, packLeft, packRight, maxRows = p.rowCount, minPtPerMm = p.ptPerMm * 1.02f,
        )
        assertTrue(
            "a 2% larger scale must cost a row or overrun the page",
            bumped.rowCount > p.rowCount || bumped.placedCount < p.placedCount ||
                bumped.cells.any { it.right > packRight + 1e-3f },
        )
    }

    @Test
    fun `long windows fill a row by width, so a fourth is pushed to the next row`() {
        // Four long components: two fill the page's width between them, so the rows come out 2 + 2
        // — a row's capacity is its WIDTH, not a fixed column count.
        val windows = packWindows(4, 900f)
        val p = packWearStripWindows(windows, packLeft, packRight, maxRows = 2)
        assertEquals(4, p.placedCount)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), rowsOf(p))
        assertCellsWellFormed(p)
        assertScaleMaximalForItsRows(p, windows)
    }

    @Test
    fun `a pair already at the scale cap stays side by side on ONE row`() {
        // Two short strips reach WEAR_STRIP_MAX_PT_PER_MM sharing a row, so a second row would
        // buy nothing — the packer keeps them side by side, tall, whatever the row budget
        // (WEAR_PACK_ROW_SCALE_GAIN: an extra row must buy a meaningfully bigger drawing).
        val windows = packWindows(2, 100f)
        listOf(2, 3).forEach { budget ->
            val p = packWearStripWindows(windows, packLeft, packRight, maxRows = budget)
            assertEquals("row budget $budget", 1, p.rowCount)
            assertEquals(WEAR_STRIP_MAX_PT_PER_MM, p.ptPerMm, 1e-4f)
            assertEquals(2, p.placedCount)
            assertEquals(listOf(listOf(0, 1)), rowsOf(p))
            assertCellsWellFormed(p)
        }
    }

    @Test
    fun `a pair below the cap stacks when a row each buys real scale`() {
        // Two 150mm strips share a row only at ~2.1 pt/mm; a row each reaches the 3.0 cap — a
        // ≥ WEAR_PACK_ROW_SCALE_GAIN gain, so the packer spends the second row and each strip
        // draws page-wide instead of cramped side by side over a half-empty band (on-device
        // report: an election forced into one row at a fraction of the page's possible size).
        val p = packWearStripWindows(packWindows(2, 150f), packLeft, packRight, maxRows = 2)
        assertEquals(2, p.rowCount)
        assertEquals(WEAR_STRIP_MAX_PT_PER_MM, p.ptPerMm, 1e-4f)
        assertEquals(listOf(listOf(0), listOf(1)), rowsOf(p))
        assertCellsWellFormed(p)
    }

    @Test
    fun `the freed profile band is spent when a deeper layout draws bigger`() {
        // Five 200mm components fit two rows — but three rows re-split the election so the
        // binding row carries two windows instead of three, a ≥ WEAR_PACK_ROW_SCALE_GAIN scale
        // gain. With the profile shown the budget stops at two rows; hiding it frees the third
        // and the whole election draws larger (on-device report: use the white space).
        val windows = packWindows(5, 200f)
        val shown = packWearStripWindows(windows, packLeft, packRight, wearStripMaxRows(true))
        val hidden = packWearStripWindows(windows, packLeft, packRight, wearStripMaxRows(false))
        assertEquals(2, shown.rowCount)
        assertEquals(3, hidden.rowCount)
        assertTrue(
            "the third row must buy the gain that earned it " +
                "(${hidden.ptPerMm} vs ${shown.ptPerMm})",
            hidden.ptPerMm >= shown.ptPerMm * WEAR_PACK_ROW_SCALE_GAIN - 1e-4f,
        )
        assertEquals(5, shown.placedCount)
        assertEquals(5, hidden.placedCount)
        assertCellsWellFormed(shown)
        assertCellsWellFormed(hidden)
        assertScaleMaximalForItsRows(shown, windows)
        assertScaleMaximalForItsRows(hidden, windows)
    }

    @Test
    fun `the freed profile band is spent only when two rows cannot hold the election`() {
        // Seven components need three rows even at the scale floor: with the shaft on the page the
        // tail overflows to the "+N more" note, and hiding it prints the whole election.
        val windows = packWindows(7, 200f)
        val shown = packWearStripWindows(windows, packLeft, packRight, wearStripMaxRows(true))
        val hidden = packWearStripWindows(windows, packLeft, packRight, wearStripMaxRows(false))
        assertEquals(2, shown.rowCount)
        assertEquals(6, shown.placedCount)
        assertEquals("the overflowing tail keeps its order", listOf(0, 1, 2, 3, 4, 5),
            shown.cells.map { it.windowIndex })
        assertEquals(3, hidden.rowCount)
        assertEquals(7, hidden.placedCount)
        assertCellsWellFormed(shown)
        assertCellsWellFormed(hidden)
        // An overflowing page still draws its survivors as large as they fit, not pinned at the
        // scale floor.
        assertTrue("survivors must not be stuck at the floor", shown.ptPerMm > WEAR_STRIP_MIN_PT_PER_MM)
    }

    @Test
    fun `three short windows share one row - the whitespace a fixed 2-column grid wasted`() {
        // The on-device case: two short strips each hogged half the page, so a third could never
        // join them. Four short windows now pack 3 + 1 instead of 2 + 2.
        val p = packWearStripWindows(packWindows(4, 60f), packLeft, packRight, maxRows = 2)
        assertEquals(4, p.placedCount)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), rowsOf(p))
        assertEquals("a row never holds more than the cap", WEAR_STRIP_MAX_PER_ROW, rowsOf(p)[0].size)
        assertCellsWellFormed(p)
    }

    @Test
    fun `whitespace is spent before the scale - a page that only fits tightened keeps full scale`() {
        // Three 40 mm components at the cap draw 120 pt each: 3 × (120 + 2 × stub) + 2 × gutter is
        // 608 pt at full spacing and 500 pt at tight, so a 550 pt page fits only once the stubs and
        // gutters give. The scale must not move.
        val windows = packWindows(3, 40f)
        val narrowRight = packLeft + 550f
        val p = packWearStripWindows(windows, packLeft, narrowRight, maxRows = 1)
        assertEquals(3, p.placedCount)
        assertEquals(1, p.rowCount)
        assertEquals("scale untouched — whitespace paid for it", WEAR_STRIP_MAX_PT_PER_MM, p.ptPerMm, 1e-4f)
        assertTrue("stub squeezed below full", p.spacing.stubWidthPt < fullSpacing.stubWidthPt)
        assertTrue("…but never past its floor", p.spacing.stubWidthPt >= tightSpacing.stubWidthPt - 1e-4f)
        assertTrue(p.spacing.colGapPt <= fullSpacing.colGapPt + 1e-4f)
        assertTrue(p.spacing.colGapPt >= tightSpacing.colGapPt - 1e-4f)
    }

    @Test
    fun `nothing is squeezed when the page has room to spare`() {
        val p = packWearStripWindows(packWindows(2, 8f), packLeft, packRight, maxRows = 2)
        assertEquals(WEAR_STRIP_MAX_PT_PER_MM, p.ptPerMm, 1e-4f)
        assertEquals("full stub", fullSpacing.stubWidthPt, p.spacing.stubWidthPt, 1e-4f)
        assertEquals("full gutter", fullSpacing.colGapPt, p.spacing.colGapPt, 1e-4f)
        assertCellsWellFormed(p)
    }

    @Test
    fun `the shared scale shrinks only once tight spacing is not enough`() {
        // Six 700 mm components can't fit two rows at any comfortable scale, so everything draws
        // smaller together rather than a strip being dropped.
        val windows = packWindows(6, 700f)
        val p = packWearStripWindows(windows, packLeft, packRight, maxRows = 2)
        assertEquals("every window still placed", 6, p.placedCount)
        assertTrue("the shared scale gave way", p.ptPerMm < WEAR_STRIP_MAX_PT_PER_MM)
        assertTrue(p.ptPerMm > 0f)
        assertCellsWellFormed(p)
        assertScaleMaximalForItsRows(p, windows)
    }

    @Test
    fun `an election too big even at the scale floor places a prefix and overflows the rest`() {
        // 5 m components: one alone fills the page at the floor scale, so only maxRows of them
        // can print. The overflow is the aft→fwd TAIL — order is never rearranged to fit more.
        val windows = packWindows(4, 5000f)
        val p = packWearStripWindows(windows, packLeft, packRight, maxRows = 2)
        assertEquals(WEAR_STRIP_MIN_PT_PER_MM, p.ptPerMm, 1e-4f)
        assertEquals(2, p.placedCount)
        assertEquals(2, p.rowCount)
        assertEquals(listOf(0, 1), p.cells.map { it.windowIndex })
    }

    @Test
    fun `one shared scale - every cell is exactly its own window's drawn width plus two stubs`() {
        // The proportionality invariant: the packer divides the page's WIDTH, it never gives a
        // window its own scale. A 900 mm window must draw exactly three times a 300 mm one.
        val windows = listOf(
            linerWindow("a", 0f, 300f), linerWindow("b", 2000f, 900f), linerWindow("c", 4000f, 150f),
        )
        val p = packWearStripWindows(windows, packLeft, packRight, maxRows = 3)
        assertEquals(3, p.placedCount)
        p.cells.forEach { cell ->
            val expected = windows[cell.windowIndex].drawnWidthPt(p.ptPerMm) + 2f * p.spacing.stubWidthPt
            assertEquals("cell ${cell.windowIndex}", expected, cell.right - cell.left, 1e-3f)
        }
        val drawn = p.cells.map { windows[it.windowIndex].drawnWidthPt(p.ptPerMm) }
        assertEquals(3f, drawn[1] / drawn[0], 1e-3f)
        assertEquals(0.5f, drawn[2] / drawn[0], 1e-3f)
        assertCellsWellFormed(p)
    }

    @Test
    fun `a capped packed row pins to the top of the band when the profile is hidden`() {
        // What composeWearPdf's packed branch passes with showShaftProfile = false. The per-row
        // height cap holds even with no profile band: the packer takes the FEWEST rows, so a lone
        // uncapped row would stretch to the whole band and print a short fat cylinder. The height
        // it gives back is lifted out from under the header, landing above the notes as ordinary
        // bottom margin instead of as a white hole at the top.
        val areaTop = 84f
        val areaBottom = 500f
        val capPt = 170f      // mirrors the composer's private WEAR_STRIP_HEIGHT_MAX_PT
        val v = computeWearVerticalLayout(
            areaTop, areaBottom, stripCount = 1,
            minProfileHeightPt = 0f, profileToStripsGapPt = 0f, preferredProfileHeightPt = 0f,
            maxStripHeightPt = capPt,
        )
        assertEquals("the cap holds", capPt, v.stripBottoms[0] - v.stripTops[0], 1e-3f)
        assertTrue("uncapped, the row would start at the band top", v.stripTops[0] > areaTop + 1e-3f)
        val lift = v.stripTops[0] - areaTop
        assertEquals("the row pins to the band top", areaTop, v.stripTops[0] - lift, 1e-3f)
        assertTrue(
            "…so the reclaimed height ends up as bottom margin",
            (v.stripBottoms[0] - lift) < areaBottom - 1e-3f,
        )
    }

    @Test
    fun `degenerate packing inputs come back empty rather than throwing`() {
        val windows = packWindows(3, 200f)
        listOf(
            packWearStripWindows(emptyList(), packLeft, packRight, maxRows = 2),
            packWearStripWindows(windows, packLeft, packLeft, maxRows = 2),
            packWearStripWindows(windows, packLeft, packRight, maxRows = 0),
        ).forEach { p ->
            assertEquals(0, p.rowCount)
            assertEquals(0, p.placedCount)
            assertTrue(p.cells.isEmpty())
        }
        assertEquals("a 720 pt page is wider than nothing", 720f, packWidth, 1e-6f)
    }

    // ── spreadWearStripRowGutters — facing break curls get the gutter they need ──

    private fun cell(idx: Int, row: Int, left: Float, width: Float) =
        WearStripPackedCell(windowIndex = idx, row = row, left = left, right = left + width)

    @Test
    fun `deficient gutter widens to the requirement and the row re-centers`() {
        // Two cells (450 + 220 wide) at a 22pt gutter, centered: row spans 692 in 720.
        val rowW = 450f + 22f + 220f
        val x0 = packLeft + (packWidth - rowW) / 2f
        val cells = listOf(cell(0, 0, x0, 450f), cell(1, 0, x0 + 472f, 220f))
        val out = spreadWearStripRowGutters(cells, packLeft, packRight) { _, _ -> 49f }
        assertEquals("footprints preserved", 450f, out[0].right - out[0].left, 1e-3f)
        assertEquals("footprints preserved", 220f, out[1].right - out[1].left, 1e-3f)
        assertEquals("gutter widened to the requirement", 49f, out[1].left - out[0].right, 1e-2f)
        val leadPad = out[0].left - packLeft
        val trailPad = packRight - out[1].right
        assertEquals("row re-centered", leadPad, trailPad, 1e-2f)
    }

    @Test
    fun `zero requirement and already-wide gutters stay exactly where they were`() {
        val cells = listOf(cell(0, 0, 100f, 200f), cell(1, 0, 322f, 200f))
        assertEquals(cells, spreadWearStripRowGutters(cells, packLeft, packRight) { _, _ -> 0f })
        // Requirement below the current gutter: never shrinks, never moves.
        assertEquals(cells, spreadWearStripRowGutters(cells, packLeft, packRight) { _, _ -> 10f })
    }

    @Test
    fun `insufficient slack widens proportionally and never overruns the page`() {
        // 340 + 340 wide at a 22pt gutter = 702 of 720: only 18pt of slack for a 60pt ask.
        val rowW = 340f + 22f + 340f
        val x0 = packLeft + (packWidth - rowW) / 2f
        val cells = listOf(cell(0, 0, x0, 340f), cell(1, 0, x0 + 362f, 340f))
        val out = spreadWearStripRowGutters(cells, packLeft, packRight) { _, _ -> 60f }
        assertEquals("all slack spent", 22f + 18f, out[1].left - out[0].right, 1e-2f)
        assertTrue("row inside the content span", out[0].left >= packLeft - 1e-3f)
        assertTrue("row inside the content span", out[1].right <= packRight + 1e-3f)
    }

    @Test
    fun `single-cell rows and other rows are untouched by a widening row`() {
        val lone = cell(0, 0, 200f, 300f)
        val a = cell(1, 1, 100f, 250f)
        val b = cell(2, 1, 372f, 250f)
        val out = spreadWearStripRowGutters(listOf(lone, a, b), packLeft, packRight) { _, _ -> 50f }
        assertEquals("lone row untouched", lone, out[0])
        assertEquals("output keeps input order", listOf(0, 1, 2), out.map { it.windowIndex })
        assertEquals("row 1 widened", 50f, out[2].left - out[1].right, 1e-2f)
    }

    @Test
    fun `per-pair requirements widen only the gutters that ask`() {
        val cells = listOf(cell(0, 0, 50f, 150f), cell(1, 0, 222f, 150f), cell(2, 0, 394f, 150f))
        val out = spreadWearStripRowGutters(cells, packLeft, packRight) { l, r ->
            if (l == 0 && r == 1) 60f else 0f
        }
        assertEquals("asking gutter widened", 60f, out[1].left - out[0].right, 1e-2f)
        assertEquals("silent gutter unchanged", 22f, out[2].left - out[1].right, 1e-2f)
    }

    // ── wearStripHeightFrac — strip heights read proportional across the page ──

    @Test
    fun `strip heights scale by true diameter ratio to the page's largest reference`() {
        // A body strip an inch smaller in OD than the page's big liner must draw shorter by
        // exactly the true ratio (on-device report: both drew at the same height).
        assertEquals(1f, wearStripHeightFrac(11.004f, 11.004f), 1e-6f)
        assertEquals(10.5f / 11.004f, wearStripHeightFrac(10.5f, 11.004f), 1e-6f)
        // The reference itself always fills its band; ratios above 1 clamp (defensive).
        assertEquals(1f, wearStripHeightFrac(12f, 11f), 1e-6f)
        // Degenerate inputs fall back to full height rather than collapsing the strip.
        assertEquals(1f, wearStripHeightFrac(0f, 11f), 1e-6f)
        assertEquals(1f, wearStripHeightFrac(11f, 0f), 1e-6f)
    }

    // ── wearStripBreakAmplitudePt — the curl flattens before it ever crosses ──

    @Test
    fun `break amplitude is full when the void side is unbounded or roomy`() {
        assertEquals(0.6f * 80f, wearStripBreakAmplitudePt(80f), 1e-3f)
        assertEquals(0.6f * 80f, wearStripBreakAmplitudePt(80f, outwardRoomPt = 500f, strokeWidthPt = 1.4f), 1e-3f)
    }

    @Test
    fun `break amplitude clamps to the outward room and reaches zero gracefully`() {
        val r = 80f
        val stroke = 1.4f
        val room = 10f
        val amp = wearStripBreakAmplitudePt(r, room, stroke)
        assertTrue("clamped below full", amp < 0.6f * r)
        assertEquals("reach fills exactly the room", room - stroke, amp * BREAK_EDGE_OUTWARD_REACH_FRAC, 1e-2f)
        assertEquals("no room, no curl", 0f, wearStripBreakAmplitudePt(r, 0f, stroke), 1e-3f)
        assertEquals("negative room, no curl", 0f, wearStripBreakAmplitudePt(r, -5f, stroke), 1e-3f)
    }

}
