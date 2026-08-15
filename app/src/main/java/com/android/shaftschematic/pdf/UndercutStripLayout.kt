// app/src/main/java/com/android/shaftschematic/pdf/UndercutStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.nearestSetReference
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Pure-math layout helpers for the undercut-PDF detail strips
 * (`docs/UndercutDrawing_PLAN.md` §8). Deliberately free of any `android.graphics` import
 * so the rail chain, the strip banding, the anchor-from-SET choice, and the measured-Ø
 * station build can be unit-tested directly on a plain JVM — the same posture (and the
 * same package) as [WearStripLayout.kt]'s wear helpers, several of which this file reuses
 * verbatim rather than re-deriving:
 *
 * - [computeWearVerticalLayout] / [computeWearStripGridLayout] — count-driven and
 *   content-agnostic, so a cluster window slots straight into what a liner used.
 * - [computeWearStripHorizontalLayout] — the strip's draw range plays the liner's role; the
 *   fixed side stub becomes the [UNDERCUT_STRIP_EDGE_INSET_PT] margin the break edges live in.
 * - [WearRailSpan] / [layoutWearStripRail] — the rail resolve/label-stacking conventions
 *   are identical; only the chain *content* differs, which is [buildUndercutRailSpans].
 * - [linerAnchorSuffix] / [WEAR_BLANK_ANCHOR_SUFFIX] — shared wording so the undercut
 *   sheet's "FROM … S.E.T." reads exactly like the wear sheet's.
 *
 * `UndercutPdfComposer.kt` calls these and does the actual `Canvas` drawing; this file
 * only computes *where things go*.
 */

// ──────────────────────────────────────────────────────────────────────────────
// Page mode — one detail strip per liner / bare-shaft cluster
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The undercut sheet's rendering mode, from the number of detail strips
 * (`geom/UndercutMath.kt`'s `buildUndercutStrips` — one per liner holding cuts, plus one
 * per bare-shaft cluster window): `0` → [WearPdfMode.PROFILE_FORM] (the profile-only
 * hand-marking form — also what blank/template mode always produces), `1` →
 * [WearPdfMode.COMBINED] (one full-width strip below the profile), `2+` →
 * [WearPdfMode.GRID] (a 2-column grid, [WEAR_STRIP_GRID_MAX_PER_PAGE] max plus a "+N more"
 * note).
 *
 * The rule is a plain count → mode mapping with no liner-specific content in it, so this
 * delegates to [determineWearPdfMode] instead of restating the thresholds — the two sheets
 * cannot drift apart in how many strips they will lay side by side.
 */
fun determineUndercutPdfMode(stripCount: Int): WearPdfMode = determineWearPdfMode(stripCount)

/**
 * Detail strips shown on one undercut page for [mode] — the grid's 2 × 2 cap, or the
 * single-column cap below that. Strips past this print as a "+N more" text note (the
 * single-page constraint documented on [selectWearStripWindowsForPage]).
 */
fun undercutStripsPerPage(mode: WearPdfMode): Int =
    if (mode == WearPdfMode.GRID) WEAR_STRIP_GRID_MAX_PER_PAGE else WEAR_STRIP_MAX_PER_PAGE

// ──────────────────────────────────────────────────────────────────────────────
// Horizontal + vertical strip banding
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Margin kept between a strip's edge and the drawn profile, points. Unlike a wear strip —
 * where the same slot holds a neighbour *stub* drawn outside the liner's own scale — an
 * undercut strip draws its whole range (liner + overhang + pad, or the padded cluster
 * window) right up to its break edges, so this is pure breathing room for the break
 * symbols' lobes (which bulge horizontally about the cut line) and the outermost rail
 * witness lines.
 */
const val UNDERCUT_STRIP_EDGE_INSET_PT = 16f

/**
 * Minimum DRAWN width, points, of the undimensioned neighbour-stock pad a strip keeps between
 * each chain datum and its end break — enforced by [computeUndercutStripDrawRange].
 *
 * The pad is authored in mm (`geom/UndercutMath.kt`'s `UNDERCUT_WINDOW_PAD_MM`), so how wide it
 * PRINTS depends entirely on the strip's own scale: the same 1 in of stock is ~36 pt beside a
 * short liner on a full-width strip and under 9 pt beside a long one in a grid cell, where the
 * break edge then sits all but against the liner's end face (on-device report: "oddly cramped").
 * Widening the mm constant instead would fix only one scale, and would additionally re-letter
 * every `FreeStrip`'s pad dimension — the bare-shaft chain runs window edge → window edge, so
 * its leading/trailing spans PRINT the pad, and 1 in is a figure worth reading.
 *
 * Sized so the break symbol has about as much air inboard as the [UNDERCUT_STRIP_EDGE_INSET_PT]
 * margin gives it outboard, plus the lobe's own excursion (≈ 8 pt at
 * [UNDERCUT_BREAK_AMP_MAX_PT]): 16 + 8. Undimensioned either way — this changes only how much
 * neighbouring stock is drawn, never where a datum sits.
 */
const val UNDERCUT_STRIP_MIN_PAD_PT = 24f

/**
 * Ceiling on [UNDERCUT_STRIP_MIN_PAD_PT] as a share of a strip's available inner width, so a
 * pathologically narrow cell cannot spend most of itself on margin: the two pads together never
 * take more than twice this. No real strip reaches it (the narrowest, a grid cell, has ~317 pt of
 * inner width against a 24 pt floor), so this only bounds the degenerate case.
 */
private const val UNDERCUT_STRIP_MAX_PAD_FRAC = 0.15f

/**
 * Ceiling on a strip end's S-break amplitude, points. The break's nominal amplitude is
 * `r × 0.6` — sized off the drawn cylinder so a small section still gets a legible symbol —
 * but on a tall strip that puts the lobes far outside the cylinder corners, where they read as
 * heavy "ears" hung off the ends rather than as a cut through round stock (on-device report).
 *
 * Capping in absolute points keeps the symbol the same size on every strip, and keeps it inside
 * [UNDERCUT_STRIP_EDGE_INSET_PT]: peak lateral deviation of the drawn curves is ≈ 0.29 × amp
 * (the S itself) and ≈ 0.43 × amp (the return sweep, widened by the break symbol's fullness
 * factor), so at this cap the widest excursion is ≈ 8 pt — comfortably inside the 16 pt inset.
 *
 * Applies to the strip/window END breaks only. The whole-shaft profile's mid-run compression
 * break keeps the uncapped `r × 0.6`: it is drawn on one shaft at one scale, where the lobes
 * have nothing to collide with.
 */
const val UNDERCUT_BREAK_AMP_MAX_PT = 18f

/**
 * Air reserved ABOVE the total-span rail line, inside [UNDERCUT_TOTAL_RAIL_BAND_PT]: the row a
 * total whose value cannot seat in a break falls back to (`drawUndercutRail`'s
 * `fallbackLabelAbove`, which draws its label above the line because the chained rail owns the
 * space below). Everything the band holds beyond this is clear separation DOWN to the chained
 * rail.
 */
const val UNDERCUT_TOTAL_RAIL_ABOVE_PT = 18f

/**
 * Vertical band reserved at the top of a strip for the strip's **total** dimension rail —
 * the second rail line above the chained one (the reference sketch's overall figure across
 * all the sections). Only reserved when a total span exists ([buildUndercutTotalSpan]).
 *
 * Split [UNDERCUT_TOTAL_RAIL_ABOVE_PT] above the line / the remainder below it, so the total's
 * value and the chained rail's values sit ~20 pt apart. Anything tighter reads as one crowded
 * band: the total ("16\"") appears to belong to the chain below it (on-device report, twice —
 * at 14 pt and again at 22 pt).
 */
const val UNDERCUT_TOTAL_RAIL_BAND_PT = 38f

/**
 * Row pitch for the chained rail's stacked fallback labels — the value a span too narrow to
 * seat its own figure drops to. Wider than the wear sheet's [WEAR_STRIP_ROW_HEIGHT_PT] because
 * `drawUndercutRail` starts row 0 clear of the outward arrowheads straddling the rail line: at
 * the wear pitch a two-row stack ran into the cylinder, and row 0 itself read as glued under
 * the rail (on-device report: the narrow-gap value "2\"" sat on the line).
 *
 * `drawUndercutRail` steps its label rows by this same constant, so the reserved budget
 * ([UndercutStripInnerLayout.railLabelRows]) and the drawn rows cannot drift apart.
 */
const val UNDERCUT_RAIL_ROW_HEIGHT_PT = 17f

/**
 * Ceiling on the drawn cylinder's height, as a fraction of the strip's own band. Without the
 * main profile a single strip owns nearly the whole page, and an uncapped cylinder would eat
 * that band as one black slab; the reclaimed height belongs to the rails, the Ø callouts and
 * the margins first, the cylinder second.
 *
 * Deliberately well under half the band: a section drawn any larger reads as oversized for what
 * it carries, and its end breaks sprawl (on-device report on the strips-only sheet).
 */
const val UNDERCUT_CYL_MAX_HEIGHT_FRAC = 0.38f

/**
 * Absolute ceiling over [UNDERCUT_CYL_MAX_HEIGHT_FRAC] — `min(frac × band, this)`. A fraction
 * alone scales with the band, so a taller band would keep growing the section; past this height
 * a strip stops being a drawing and becomes a poster of one cylinder. Beyond the cap every extra
 * point goes to the surplus split below.
 */
const val UNDERCUT_CYL_MAX_ABS_PT = 170f

/**
 * Floor under [UNDERCUT_CYL_MAX_HEIGHT_FRAC]/[UNDERCUT_CYL_MAX_ABS_PT]: a short strip (a 4-up
 * grid cell) is never capped below what it could already draw, so the cap only ever bites on
 * the tall strips-own-page bands.
 */
const val UNDERCUT_CYL_MAX_FLOOR_PT = 96f

/**
 * Most extra air the capped cylinder's surplus may put between the rail labels and the cylinder.
 * Kept small: together with the rows-used budget ([undercutRailRowBudget]) it is what holds the
 * chained rail CLOSE to the shaft on a tall band — the dimension figures belong to the surface
 * they measure, while level-to-level separation is owned by [UNDERCUT_TOTAL_RAIL_BAND_PT] and
 * [UNDERCUT_RAIL_ROW_HEIGHT_PT] (a larger value here read as the labels floating far above the
 * shaft — on-device report).
 */
const val UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT = 15f

/**
 * Most extra air that surplus may put between the Ø callout band and the strip title. Sized so
 * the even split of the remainder (half here, half above the rails) stays even on a full-page
 * strip rather than capping out on one side and stacking the rest at the top — which is also
 * what keeps the drawn cylinder vertically centred on the same line whatever the cap is set to
 * (`cylBottom = bottom − rest/2` and `cylTop = cylBottom − cap` move the two edges by equal and
 * opposite amounts). Sized for the largest real surplus, the started (write-in) strip's, since
 * the tighter cylinder cap frees more of it than before; past that it is only a guard against a
 * pathologically tall band.
 */
const val UNDERCUT_CYL_BELOW_EXTRA_MAX_PT = 96f

/** Share of the cylinder's surplus height spent on rail-label → cylinder air (rest splits below/above). */
private const val UNDERCUT_SURPLUS_RAIL_SHARE = 0.4f

/** A strip's drawn axial range in shaft-space mm — see [computeUndercutStripDrawRange]. */
data class UndercutStripDrawRangeMm(val startMm: Float, val endMm: Float)

/**
 * The axial range a strip actually DRAWS, widening the strip's own
 * (`geom/UndercutMath.kt` `UndercutStrip.drawStartMm`/`drawEndMm`) just enough that the
 * neighbour-stock pad outside each chain datum prints at least [minPadPt] wide.
 *
 * The mm pad the geometry carries is scale-blind (see [UNDERCUT_STRIP_MIN_PAD_PT]); this is the
 * scale-aware half, applied at layout time so a half-width grid cell and a full-width strip both
 * read with the same air at their ends. A strip whose pad already prints wide enough — a short
 * liner, or any strip on a wide cell — comes back untouched.
 *
 * Only ever widens, and only outward from the CHAIN datums, so:
 * - no datum moves and no dimension changes (the pad stays undimensioned on a `LinerStrip`, and
 *   a `FreeStrip`'s window-edge chain keeps printing the same 1 in pad spans — the extra stock is
 *   drawn OUTSIDE the window, between its edge and the break);
 * - the range stays clamped to `[0, oalMm]`, so a cut near a shaft end keeps its flat physical
 *   end rather than growing a break past the metal.
 *
 * [stripLeftPt]/[stripRightPt] are the cell the strip is laid out in; the usable width is that
 * less two [edgeInsetPt] margins, matching [computeWearStripHorizontalLayout]'s stub arithmetic,
 * and [minPtPerMm]/[maxPtPerMm] are its scale clamps so the pad this reserves is the pad that
 * layout then draws.
 */
fun computeUndercutStripDrawRange(
    drawStartMm: Float,
    drawEndMm: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    stripLeftPt: Float,
    stripRightPt: Float,
    oalMm: Float,
    edgeInsetPt: Float = UNDERCUT_STRIP_EDGE_INSET_PT,
    minPadPt: Float = UNDERCUT_STRIP_MIN_PAD_PT,
    minPtPerMm: Float = WEAR_STRIP_MIN_PT_PER_MM,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
): UndercutStripDrawRangeMm {
    val unchanged = UndercutStripDrawRangeMm(drawStartMm, drawEndMm)
    val drawLenMm = drawEndMm - drawStartMm
    if (drawLenMm <= UNDERCUT_RAIL_SPAN_EPS_MM) return unchanged

    val availPt = (stripRightPt - stripLeftPt - 2f * edgeInsetPt).coerceAtLeast(1f)
    val padPt = minPadPt.coerceIn(0f, availPt * UNDERCUT_STRIP_MAX_PAD_FRAC)
    // What the narrower of the two pads prints at today's scale.
    val nowPtPerMm = (availPt / drawLenMm).coerceIn(minPtPerMm, maxPtPerMm)
    val nowPadPt = minOf(chainStartMm - drawStartMm, drawEndMm - chainEndMm)
        .coerceAtLeast(0f) * nowPtPerMm
    if (nowPadPt >= padPt) return unchanged

    // Re-solve for the scale that leaves exactly `padPt` each side: the chain gets the rest of
    // the width, and the pad in mm follows from it. Clamping the scale the same way layout does
    // keeps the two in step — a scale pinned at a clamp yields a proportionally larger mm pad,
    // which then draws at the intended width anyway.
    val chainLenMm = (chainEndMm - chainStartMm).coerceAtLeast(UNDERCUT_RAIL_SPAN_EPS_MM)
    val ptPerMm = ((availPt - 2f * padPt) / chainLenMm).coerceIn(minPtPerMm, maxPtPerMm)
    val padMm = padPt / ptPerMm
    val hi = oalMm.coerceAtLeast(0f)
    return UndercutStripDrawRangeMm(
        startMm = minOf(drawStartMm, chainStartMm - padMm).coerceIn(0f, hi),
        endMm = maxOf(drawEndMm, chainEndMm + padMm).coerceIn(0f, hi),
    )
}

/**
 * One undercut strip's vertical split: the total-span rail line at the very top, the
 * chained rail below it, the profile band in the middle, and the strip title at the bottom
 * (with the measured-Ø callout band between profile and title).
 *
 * Guarantees `stripTop ≤ totalRailY ≤ chainRailY ≤ cylTop ≤ cylBottom ≤ stripBottom` for
 * any input, including pathological ones — see [computeWearStripInnerLayout], which owns
 * everything from [chainRailY] down.
 */
data class UndercutStripInnerLayout(
    /** Y of the second (total-span) rail line; equals [chainRailY] when there is no total span. */
    val totalRailY: Float,
    /** Y of the chained dimension rail line (window pads, undercut lengths, inter-cut gaps). */
    val chainRailY: Float,
    val cylTop: Float,
    val cylBottom: Float,
    /** Stacked label rows that fit between [chainRailY] and [cylTop] — see [WEAR_RAIL_MAX_LABEL_ROWS]. */
    val railLabelRows: Int,
)

/**
 * Where the chained rail's fallback-label rows go, and how many to reserve on each side of the
 * line — feeds [computeUndercutStripInnerLayout] (`maxLabelRows` = [belowRows],
 * `chainAboveBandPt` = [aboveRows] × [UNDERCUT_RAIL_ROW_HEIGHT_PT]) and `drawUndercutRail`'s
 * `fallbackLabelAbove` flag ([aboveRows] > 0).
 */
data class UndercutRailRowPlan(
    /** Rows reserved between the rail line and the cylinder top (never 0 — clear air). */
    val belowRows: Int,
    /** Fallback rows stacked ABOVE the rail line; 0 when fallbacks tuck below instead. */
    val aboveRows: Int,
)

/**
 * Plans the chained rail's fallback-label rows. Since [layoutWearStripRail] is pure horizontal
 * geometry, the chain is resolved BEFORE the vertical split and the reservation sized to the
 * rows its labels actually landed on (only spans whose label does NOT seat in the line's break
 * count — a break-seated label sits on the rail line itself and needs no row). The wear-shaped
 * fixed budget ([WEAR_RAIL_MAX_LABEL_ROWS] rows, always, below) reserved crowding air most
 * strips never used, floating the rail figures far above the surface they dimension (on-device
 * report).
 *
 * Which side of the line the fallbacks land on follows what sits above the chain:
 * - **Total rail present** → fallbacks tuck BELOW the line (the total's figure owns the space
 *   above), reserving the rows used, never less than 1.
 * - **Chain is the only label level** → fallbacks go ABOVE the line (on-device report: a small
 *   value pushed under the span line read as orphaned with nothing above the rail), and below
 *   keeps just the 1-row clear air off the shaft.
 * - **Started strip** → no chain at all; the full [WEAR_RAIL_MAX_LABEL_ROWS] budget stays below,
 *   the machinist's band to hand-draw a chain into.
 */
fun planUndercutRailRows(
    chainLayout: List<WearRailSpanLayout>,
    startedStrip: Boolean,
    hasTotalRail: Boolean,
): UndercutRailRowPlan {
    if (startedStrip) return UndercutRailRowPlan(belowRows = WEAR_RAIL_MAX_LABEL_ROWS, aboveRows = 0)
    val used = (chainLayout.filter { !it.seatsInBreak }.maxOfOrNull { it.labelRow + 1 } ?: 0)
        .coerceAtMost(WEAR_RAIL_MAX_LABEL_ROWS)
    return if (hasTotalRail) UndercutRailRowPlan(belowRows = used.coerceAtLeast(1), aboveRows = 0)
    else UndercutRailRowPlan(belowRows = 1, aboveRows = used)
}

/**
 * Splits one strip's vertical band, reserving [totalRailBandPt] at the top for the
 * cluster's total-span rail when [hasTotalRail] (or [chainAboveBandPt] for a single-level
 * chain's above-the-line fallback rows — see [planUndercutRailRows]; the two are mutually
 * exclusive), then delegating everything below it to
 * [computeWearStripInnerLayout] — so the chained rail, the profile band, the measured-Ø
 * band ([diaBandPt]) and the title degrade in exactly the same order the wear strips do
 * (the drawn cylinder shrinks first, then label rows drop out; nothing ever overflows the
 * strip).
 *
 * On a **tall** band — the strips-own-page layout, where dropping the main profile hands a
 * lone strip most of the sheet — that delegation alone would pour every reclaimed point into
 * the cylinder. So the cylinder is capped at
 * `max(cylMaxFloorPt, min(bandHeight × cylMaxFrac, cylMaxAbsPt))` — a fraction of the band, but
 * never past an absolute height — and the surplus is spent on legibility instead, in a fixed
 * order:
 *  1. up to [UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT] between the chained rail's label rows and the
 *     cylinder top,
 *  2. the remainder split evenly — half between the Ø callout band and the title (the cylinder
 *     bottom rises and the callouts hang off it, capped at [UNDERCUT_CYL_BELOW_EXTRA_MAX_PT]),
 *     half as air above the rails, which drifts the whole figure down the band.
 *
 * A band short enough that the cylinder never reaches the cap comes out bit-identical to the
 * plain delegation, so grid cells and the wear-shaped strips are unaffected.
 */
fun computeUndercutStripInnerLayout(
    stripTop: Float,
    stripBottom: Float,
    titleHeightPt: Float,
    hasTotalRail: Boolean,
    totalRailBandPt: Float = UNDERCUT_TOTAL_RAIL_BAND_PT,
    totalRailAbovePt: Float = UNDERCUT_TOTAL_RAIL_ABOVE_PT,
    rowHeightPt: Float = UNDERCUT_RAIL_ROW_HEIGHT_PT,
    labelHeadroomPt: Float = WEAR_STRIP_LABEL_HEADROOM_PT,
    maxLabelRows: Int = WEAR_RAIL_MAX_LABEL_ROWS,
    diaBandPt: Float = 0f,
    chainAboveBandPt: Float = 0f,
    cylMaxFrac: Float = UNDERCUT_CYL_MAX_HEIGHT_FRAC,
    cylMaxFloorPt: Float = UNDERCUT_CYL_MAX_FLOOR_PT,
    cylMaxAbsPt: Float = UNDERCUT_CYL_MAX_ABS_PT,
): UndercutStripInnerLayout {
    val bottom = stripBottom.coerceAtLeast(stripTop)
    val band = if (hasTotalRail) totalRailBandPt.coerceAtLeast(0f) else 0f
    // Above-the-line fallback rows (chain is the sheet's only label level — see
    // [planUndercutRailRows]) reserve their band at the top exactly as the total rail's does;
    // the two are mutually exclusive, so at most one is nonzero.
    val aboveBand = chainAboveBandPt.coerceAtLeast(0f)
    val innerTop = (stripTop + band + aboveBand).coerceAtMost(bottom)
    val inner = computeWearStripInnerLayout(
        stripTop = innerTop,
        stripBottom = bottom,
        titleHeightPt = titleHeightPt,
        rowHeightPt = rowHeightPt,
        labelHeadroomPt = labelHeadroomPt,
        maxLabelRows = maxLabelRows,
        diaBandPt = diaBandPt,
        // No witness run reserved here: this layout places both its rail lines itself, off
        // [UndercutStripInnerLayout.cylTop], and `belowRows` (never 0 — see [planUndercutRailRows])
        // already holds a full label row of clear air between the chained rail and the cylinder.
        // Reserving the wear strip's run on top of it would only widen that gap.
        witnessRunPt = 0f,
    )

    // Cylinder cap + surplus spend (see the KDoc): the drawn cylinder keeps `cylH - surplus`,
    // and the freed points become air the reader can see between the bands.
    val cylH = (inner.cylBottom - inner.cylTop).coerceAtLeast(0f)
    val cylCap = maxOf(cylMaxFloorPt, minOf((bottom - stripTop) * cylMaxFrac, cylMaxAbsPt))
        .coerceAtLeast(0f)
    val surplus = (cylH - cylCap).coerceAtLeast(0f)
    val extraRailHeadroom = minOf(surplus * UNDERCUT_SURPLUS_RAIL_SHARE, UNDERCUT_RAIL_EXTRA_HEADROOM_MAX_PT)
    val rest = surplus - extraRailHeadroom
    val belowExtra = minOf(rest * 0.5f, UNDERCUT_CYL_BELOW_EXTRA_MAX_PT)

    val cylBottom = inner.cylBottom - belowExtra
    val cylTop = cylBottom - (cylH - surplus)
    // Both coercions only matter on a pathologically short band, where every band has already
    // collapsed onto the same Y — they preserve the ordering guarantee rather than shifting a
    // real layout.
    // The floor keeps the above-band clear: labels stacked above the line must stay inside the
    // strip even when a short band pins the rail high.
    val railFloor = (stripTop + aboveBand).coerceAtMost(cylTop)
    val chainRailY = (cylTop - inner.railLabelRows * rowHeightPt - extraRailHeadroom)
        .coerceIn(railFloor, cylTop)
    val totalRailY = if (band > 0f) {
        (chainRailY - (band - totalRailAbovePt).coerceAtLeast(0f)).coerceIn(stripTop, chainRailY)
    } else {
        chainRailY
    }
    return UndercutStripInnerLayout(
        totalRailY = totalRailY,
        chainRailY = chainRailY,
        cylTop = cylTop,
        cylBottom = cylBottom,
        railLabelRows = inner.railLabelRows,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Dimension rails — the chained chain, plus the cluster total
// ──────────────────────────────────────────────────────────────────────────────

private const val UNDERCUT_RAIL_SPAN_EPS_MM = 1e-3f

/**
 * The chained dimension rail for one detail strip, in **shaft-space mm** (the strip draws in
 * shaft coordinates, so callers map with the strip's `xAt` directly): chain AFT datum →
 * first shoulder, each undercut's own length, the gap between consecutive undercuts, and the
 * trailing remainder to the chain FWD datum.
 *
 * The chain runs over `[chainStartMm, chainEndMm]` — the strip's **chain bounds**, NOT its
 * draw range (`geom/UndercutMath.kt`'s `UndercutStrip`). On a `FreeStrip` the two coincide,
 * so this is the original window-edge chain. On a `LinerStrip` the chain datums are the
 * liner's own edges (extended only by a cut overhanging one of them), while the draw range
 * additionally carries a pad of neighbouring stock each side: the rail's outer witness lines
 * then land on real datums the machinist can measure to, and the pad between a break edge
 * and the liner edge is deliberately left **undimensioned** — an arbitrary zoom margin is
 * not a figure worth printing.
 *
 * [spans] must already be render-clamped (`clampUndercutSpan`) and sorted aft → fwd; this
 * only walks the chain. The algorithm is [buildWearStripRailSpans]' — chained over an
 * arbitrary shaft-space range rather than a liner-local one, which is why it is a separate
 * function rather than a call: the wear version starts at 0 and takes wear-band clamps.
 *
 * Zero-length spans are OMITTED rather than drawn as degenerate zero-width dimension lines
 * (an undercut starting exactly at a chain datum produces no leading span, two back-to-back
 * undercuts produce no gap span between them). That never leaves a hole in the chain: the
 * running cursor still advances from [chainStartMm] to [chainEndMm] exactly, since an
 * omitted span had zero mm to contribute — so the returned spans' lengths always sum to the
 * chain length.
 *
 * An undercut whose span starts at or before the cursor (overlapping cuts — legal, since
 * only the in-shaft bounds check is enforced at entry) has its effective start pulled
 * forward to the cursor, so the chain never runs backward or double-counts the overlap.
 */
fun buildUndercutRailSpans(
    chainStartMm: Float,
    chainEndMm: Float,
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
): List<WearRailSpan> {
    val out = mutableListOf<WearRailSpan>()
    var cursor = chainStartMm
    spans.forEach { s ->
        if (s.endMm - s.startMm <= UNDERCUT_RAIL_SPAN_EPS_MM) return@forEach
        val effStart = maxOf(s.startMm, cursor)
        if (effStart - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) {
            out += WearRailSpan(cursor, effStart, formatLenDim((effStart - cursor).toDouble(), unit))
        }
        val end = maxOf(s.endMm, effStart)
        if (end - effStart > UNDERCUT_RAIL_SPAN_EPS_MM) {
            out += WearRailSpan(effStart, end, formatLenDim((end - effStart).toDouble(), unit))
        }
        cursor = maxOf(cursor, end)
    }
    if (chainEndMm - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) {
        out += WearRailSpan(cursor, chainEndMm, formatLenDim((chainEndMm - cursor).toDouble(), unit))
    }
    return out
}

/**
 * The strip's **total** span — first shoulder → last shoulder, drawn on a second rail
 * line above the chain (the sketch's overall figure across all the sections).
 *
 * Returns `null` for a strip with fewer than two drawable undercuts: with one cut the total
 * would just re-state that cut's own length, already dimensioned on the chain below — the
 * same "don't re-state a figure the rail already carries" rule that suppresses a wear
 * strip's band-less span line.
 */
fun buildUndercutTotalSpan(spans: List<UndercutSpanMm>, unit: UnitSystem): WearRailSpan? {
    val live = spans.filter { it.endMm - it.startMm > UNDERCUT_RAIL_SPAN_EPS_MM }
    if (live.size < 2) return null
    val first = live.minOf { it.startMm }
    val last = live.maxOf { it.endMm }
    if (last - first <= UNDERCUT_RAIL_SPAN_EPS_MM) return null
    return WearRailSpan(first, last, formatLenDim((last - first).toDouble(), unit))
}

// ──────────────────────────────────────────────────────────────────────────────
// Measured-Ø stations
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Measured-Ø callout stations for one strip: one per undercut, positioned at its clamped
 * span's axial centre, labelled with [formatDiaWithUnit] (no "Ø" prefix — the footer/
 * callout convention).
 *
 * An undercut with no entered Ø (`diaMm == 0` — placed but not yet measured) is SKIPPED:
 * the printed sheet never carries a placeholder for a value nobody recorded, the same rule
 * as `WearDiaReading`. Its notch is still drawn (at the symbolic floor `effectiveNotchDiaMm`
 * substitutes) and still dimensioned on the rail, so the section is not lost from the sheet
 * — only its Ø value is absent.
 *
 * [clampedById] supplies the render-clamped span per undercut id; an undercut with no
 * (or an empty) clamped span is skipped. [xAtMm] maps shaft-space mm to strip x, and
 * [labelWidthPt] measures the label on-page (`Paint.measureText` at the call site — kept
 * as a function so this file stays android-free).
 */
fun buildUndercutDiaStations(
    undercuts: List<Undercut>,
    clampedById: Map<String, ClampedUndercutSpanMm>,
    xAtMm: (Float) -> Float,
    unit: UnitSystem,
    labelWidthPt: (String) -> Float,
): List<DiaCalloutStation> = undercuts.mapNotNull { u ->
    if (u.diaMm <= 0f) return@mapNotNull null
    val span = clampedById[u.id] ?: return@mapNotNull null
    if (span.isEmpty) return@mapNotNull null
    val label = formatDiaWithUnit(u.diaMm.toDouble(), unit)
    DiaCalloutStation(
        key = u.id,
        stationX = xAtMm((span.startMm + span.endMm) * 0.5f),
        label = label,
        labelWidth = labelWidthPt(label),
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Anchor-from-SET title
// ──────────────────────────────────────────────────────────────────────────────

/** Which S.E.T. a strip's anchor dimension is measured from — see [undercutAnchorFor]. */
enum class UndercutAnchorSide { AFT_SET, FWD_SET }

/**
 * A strip's anchor dimension: how far [side]'s S.E.T. is from the near shoulder of the
 * strip's cuts, and which way the strip title should be aligned ([alignRight] = toward the
 * FWD end drawn on the right, the wear strip title's direction cue).
 */
data class UndercutAnchor(val side: UndercutAnchorSide, val distanceMm: Float) {
    val alignRight: Boolean get() = side == UndercutAnchorSide.FWD_SET
}

/**
 * Picks the S.E.T. a **bare-shaft** strip's cuts are dimensioned from and measures to the
 * **near** shoulder — the shop sketch's long anchor dimension. The choice is by proximity:
 * cuts whose midpoint falls in the AFT half of the SET-to-SET span anchor to the AFT SET and
 * are measured from the first shoulder; otherwise they anchor to the FWD SET and are measured
 * from the last shoulder (`linerAnchorForPdf`'s idea, applied to the cut run instead of a
 * liner).
 *
 * Bare-shaft (`FreeStrip`) titles only. A liner strip's title anchor is the LINER's own
 * edge-to-SET datum (`buildLinerAnchorLabel` — the schematic's and wear sheet's figure),
 * never a cut's shoulder: measuring to the cut while the title names the liner reads as the
 * liner sitting at the cut's position (on-device report — a cut 11.5 in into a liner 20 in
 * from the AFT SET printed the liner as 31.5 in out). Cuts inside a liner are located by the
 * chain rail, from the liner's edges.
 *
 * The distance is reported as a magnitude: cuts sitting outboard of their SET (possible —
 * an undercut may be recorded anywhere on the shaft, while the SETs sit at the tapers)
 * still print a sensible "so far from the S.E.T." figure rather than a negative dimension.
 */
fun undercutAnchorFor(
    firstShoulderMm: Float,
    lastShoulderMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): UndercutAnchor {
    // Side choice delegates to the shared proximity rule (`geom/UndercutMath.kt`), the same
    // one that seeds a bare-shaft cut's default Distance reference in the editor — so the
    // printed anchor and the card's default always read from the same SET.
    val side = nearestSetReference(firstShoulderMm, lastShoulderMm, aftSetXMm, fwdSetXMm)
    return if (side == UndercutReference.AFT_SET) {
        UndercutAnchor(UndercutAnchorSide.AFT_SET, abs(firstShoulderMm - aftSetXMm))
    } else {
        UndercutAnchor(UndercutAnchorSide.FWD_SET, abs(fwdSetXMm - lastShoulderMm))
    }
}

/**
 * The printed anchor title for a strip — "<distance> FROM AFT S.E.T." — reusing
 * [linerAnchorSuffix] for the wording so the undercut sheet and the wear sheet phrase their
 * SET references identically.
 */
fun buildUndercutAnchorLabel(anchor: UndercutAnchor, unit: UnitSystem): String {
    val suffix = linerAnchorSuffix(
        if (anchor.side == UndercutAnchorSide.AFT_SET) LinerAnchor.AFT_SET else LinerAnchor.FWD_SET,
    )
    return "${formatLenDim(anchor.distanceMm.toDouble(), unit)} $suffix"
}

/**
 * A strip's printed title row. A liner-anchored strip is named — the liner's shared display
 * title (`util/LinerTitles.kt`'s `buildLinerTitleById`: custom label, else the positional
 * AFT/MID/FWD default) — so the sheet reads "AFT Liner — 250.0 FROM AFT S.E.T.", exactly
 * the wear strip's `name — anchor` construction and exactly the name the carousel, the wear
 * sheet, and the runout sheet already show for that liner. A bare-shaft strip has nothing to
 * name (an auto-body span carries no shop identity), so it prints the anchor alone.
 *
 * [linerTitle] null/blank ⇒ anchor only; likewise a blank [anchorLabel] leaves just the name,
 * so neither side ever prints a dangling separator.
 */
fun buildUndercutStripTitle(linerTitle: String?, anchorLabel: String): String {
    val name = linerTitle?.trim().orEmpty()
    val anchor = anchorLabel.trim()
    return when {
        name.isEmpty() -> anchor
        anchor.isEmpty() -> name
        else -> "$name — $anchor"
    }
}
