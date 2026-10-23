// app/src/main/java/com/android/shaftschematic/pdf/UndercutStripLayout.kt
package com.android.shaftschematic.pdf

import android.graphics.Paint

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.UNDERCUT_TOUCH_EPS_MM
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.nearestSetReference
import com.android.shaftschematic.geom.undercutCalloutAnchorMm
import com.android.shaftschematic.geom.undercutCalloutAnchorsMm
import com.android.shaftschematic.geom.undercutNestingForest
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.dualStackMetrics
import kotlin.math.abs

/**
 * Pure-math layout helpers for the undercut-PDF detail strips
 * (`docs/archive/UndercutDrawing_PLAN.md` §8). Deliberately free of any `android.graphics` import
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
 * single-column cap below that; on a [portrait] page, [UNDERCUT_PORTRAIT_MAX_PER_PAGE]. Strips
 * past this print as a "+N more" text note (the single-page constraint documented on
 * [selectWearStripWindowsForPage]).
 */
fun undercutStripsPerPage(mode: WearPdfMode, portrait: Boolean = false): Int = when {
    portrait -> UNDERCUT_PORTRAIT_MAX_PER_PAGE
    mode == WearPdfMode.GRID -> WEAR_STRIP_GRID_MAX_PER_PAGE
    else -> WEAR_STRIP_MAX_PER_PAGE
}

/**
 * Detail strips stacked on one PORTRAIT undercut page — the landscape grid's cap, so choosing
 * portrait never pushes a strip into the "+N more" note that landscape would have printed.
 */
const val UNDERCUT_PORTRAIT_MAX_PER_PAGE = WEAR_STRIP_GRID_MAX_PER_PAGE

/** The section count from which an AUTO undercut sheet prints portrait. */
const val UNDERCUT_PORTRAIT_AUTO_MIN_STRIPS = 2

/**
 * The page the undercut sheet prints on, from the job's [choice] and the number of detail strips
 * the sheet draws ([stripCount] — cut strips, or the started liner strips when nothing is
 * recorded; `undercutSheetStrips` in `UndercutPdfComposer.kt`). AUTO stacks two or more sections
 * on a portrait page and prints one section — or none, the whole-shaft profile fallback, which
 * wants the long page edge — landscape (on-device request). LANDSCAPE and PORTRAIT win outright.
 */
fun resolveUndercutPageOrientation(choice: UndercutPageOrientation, stripCount: Int): PdfPageOrientation =
    when (choice) {
        UndercutPageOrientation.LANDSCAPE -> PdfPageOrientation.LANDSCAPE
        UndercutPageOrientation.PORTRAIT -> PdfPageOrientation.PORTRAIT
        UndercutPageOrientation.AUTO ->
            if (stripCount >= UNDERCUT_PORTRAIT_AUTO_MIN_STRIPS) PdfPageOrientation.PORTRAIT
            else PdfPageOrientation.LANDSCAPE
    }

/**
 * The on-page rectangle of each of the [stripCount] detail strips on an undercut sheet, inside the
 * strip band `[bandTop, bandBottom]` less [reservedBottomPt] (the overflow note's band).
 *
 * - **Landscape** — one strip spans the content width; two or more take the 2-column grid
 *   ([computeWearStripGridLayout]).
 * - **[portrait]** — ONE column: every strip spans the full content width, stacked top → bottom in
 *   the order given (AFT → FWD). With [overheadsPt] (rails, label rows, Ø callouts, title per
 *   strip), a page with room takes [undercutPortraitNaturalCells] — each strip at its natural
 *   height, spare height spread as air between them, so a title stays under its own figure. A
 *   crowded page splits evenly ([computeWearVerticalLayout], the single-strip path's banding),
 *   [WEAR_STRIP_GAP_PT] apart, unless that would squeeze a strip's drawn shaft below
 *   [UNDERCUT_CYL_MAX_FLOOR_PT]: then [undercutPortraitStripHeights] gives each strip its own
 *   overhead plus ONE common cylinder height, so a busy section takes height from a plain one
 *   instead of drawing a sliver of a shaft under three rail rows. [overheadsPt] null (or the
 *   wrong size) keeps the even split.
 *
 * [liftedOverheadsPt] are the same strips measured with their chain values LIFTED above the line
 * (`UndercutStripPlan.verticalOverheadPt(lifted = true)`): the natural-cell page, roomy by
 * definition, sizes from them first so each strip then has the row its lifted values take; when
 * those do not fit, the natural cells are tried with [overheadsPt], and the crowded paths always
 * read [overheadsPt] — each strip then decides at draw time whether its cell affords the lift.
 * Null (or the wrong size) measures the natural cells with [overheadsPt] alone.
 *
 * The profile parameters are zeroed on every path: on an undercut sheet the strips own the page,
 * so the whole band goes to them.
 */
fun undercutStripCells(
    portrait: Boolean,
    stripCount: Int,
    bandTop: Float,
    bandBottom: Float,
    contentLeft: Float,
    contentRight: Float,
    reservedBottomPt: Float = 0f,
    overheadsPt: List<Float>? = null,
    liftedOverheadsPt: List<Float>? = null,
): List<WearStripCell> {
    if (stripCount <= 0) return emptyList()
    if (!portrait && determineUndercutPdfMode(stripCount) == WearPdfMode.GRID) {
        return computeWearStripGridLayout(
            bandTop, bandBottom, contentLeft, contentRight, stripCount,
            reservedBottomPt = reservedBottomPt,
            minProfileHeightPt = 0f, preferredProfileHeightPt = 0f, profileToStripsGapPt = 0f,
        ).cells
    }
    if (portrait && overheadsPt != null && overheadsPt.size == stripCount) {
        val gap = WEAR_STRIP_GAP_PT
        val usable = bandBottom - reservedBottomPt - bandTop
        val natural = liftedOverheadsPt?.takeIf { it.size == stripCount }
            ?.let { undercutPortraitNaturalCells(usable, it, gap) }
            ?: undercutPortraitNaturalCells(usable, overheadsPt, gap)
        natural?.let { offsets ->
            return offsets.map { (top, h) ->
                WearStripCell(bandTop + top, bandTop + top + h, contentLeft, contentRight)
            }
        }
        val available = usable - (stripCount - 1) * gap
        val heights = undercutPortraitStripHeights(available, overheadsPt)
        if (heights != null) {
            var top = bandTop
            return heights.map { h ->
                WearStripCell(top, top + h, contentLeft, contentRight).also { top += h + gap }
            }
        }
    }
    val v = computeWearVerticalLayout(
        bandTop, bandBottom, stripCount,
        reservedBottomPt = reservedBottomPt,
        minProfileHeightPt = 0f, preferredProfileHeightPt = 0f, profileToStripsGapPt = 0f,
    )
    return (0 until stripCount).map { i ->
        WearStripCell(v.stripTops[i], v.stripBottoms[i], contentLeft, contentRight)
    }
}

/**
 * The cylinder a portrait strip draws at its natural size: the height
 * [computeUndercutStripInnerLayout]'s cylinder cap allows a strip of height `overhead + cylinder`,
 * solved for the cylinder — so a strip given exactly [overheadPt] + this has no surplus height to
 * spend between its figure and its title. The cap is
 * `max(floor, min(h × frac, abs))`; with `h = overhead + c`, the `h × frac` branch gives
 * `c = frac × overhead / (1 − frac)`, clamped into `[floor, abs]` (each clamp is the branch that
 * then binds, so the result always satisfies the cap).
 */
fun undercutNaturalCylinderPt(
    overheadPt: Float,
    cylMaxFrac: Float = UNDERCUT_CYL_MAX_HEIGHT_FRAC,
    cylMaxFloorPt: Float = UNDERCUT_CYL_MAX_FLOOR_PT,
    cylMaxAbsPt: Float = UNDERCUT_CYL_MAX_ABS_PT,
): Float =
    (cylMaxFrac * overheadPt.coerceAtLeast(0f) / (1f - cylMaxFrac))
        .coerceIn(cylMaxFloorPt, cylMaxAbsPt)

/**
 * A roomy portrait page's strips at their NATURAL heights — each its own [overheadsPt] plus ONE
 * common cylinder, the smallest [undercutNaturalCylinderPt] on the page — as `(top offset from
 * the band top, height)` pairs, with the band's leftover height spread evenly above, between and
 * below them. Null when those heights (plus the [gapPt] between strips) do not fit [usablePt]:
 * the page is crowded and [undercutPortraitStripHeights] or the even split decides instead.
 *
 * Why natural heights: an even split hands a short stack more height than its strips can draw
 * with — the cylinder cap then spends the surplus INSIDE each strip, between its Ø callouts and
 * its title, and on a two-section page the first title floated halfway to the next strip, reading
 * as belonging to neither (render review). Sized to what it draws, every strip keeps
 * its title under its own figure and the spare height becomes air between sections. The common
 * cylinder is the page's smallest natural one, which is at or under every strip's own cap (the
 * natural cylinder rises with the overhead), so no strip is left with surplus; and one shaft
 * height down the page reads as the shop's ruled sheet.
 */
fun undercutPortraitNaturalCells(
    usablePt: Float,
    overheadsPt: List<Float>,
    gapPt: Float = WEAR_STRIP_GAP_PT,
): List<Pair<Float, Float>>? {
    val n = overheadsPt.size
    if (n == 0 || usablePt <= 0f) return null
    val cyl = overheadsPt.minOf { undercutNaturalCylinderPt(it) }
    val heights = overheadsPt.map { it.coerceAtLeast(0f) + cyl }
    val slack = usablePt - heights.sum() - (n - 1) * gapPt
    if (slack < 0f) return null
    val pad = slack / (n + 1)
    var top = pad
    return heights.map { h -> (top to h).also { top += h + gapPt + pad } }
}

/**
 * Portrait strip heights that give every strip the SAME drawn-shaft height: strip i takes its own
 * [overheadsPt] (everything it stacks around the cylinder) plus a common share of what is left of
 * [availablePt] (the band less the gaps between strips). Null — keep the even split — when the
 * even split already leaves every strip at least [cylFloorPt] of cylinder (each then draws at the
 * capped size anyway, and an even page reads as the shop's ruled sheet), when there are fewer than
 * two strips, or when the common share would not be positive.
 *
 * The common share is the MEAN of the even split's cylinder budgets, so it is never below the
 * smallest of them: equalizing only ever raises the most-squeezed strip's shaft.
 */
fun undercutPortraitStripHeights(
    availablePt: Float,
    overheadsPt: List<Float>,
    cylFloorPt: Float = UNDERCUT_CYL_MAX_FLOOR_PT,
): List<Float>? {
    val n = overheadsPt.size
    if (n < 2 || availablePt <= 0f) return null
    val even = availablePt / n
    if (overheadsPt.all { even - it >= cylFloorPt }) return null
    val common = (availablePt - overheadsPt.sum()) / n
    if (common <= 0f) return null
    return overheadsPt.map { it + common }
}

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
 * One rail label row's height: the shipped [UNDERCUT_RAIL_ROW_HEIGHT_PT], or the whole two-line
 * stack plus the same air when dual values are set stacked.
 *
 * ONE definition, read by the reserved band ([computeUndercutStripInnerLayout] via its
 * `rowHeightPt`, and the composer's `chainAboveBandPt`) and by `drawUndercutRail`'s stepping — the
 * budgeted row and the drawn row must never drift apart.
 *
 * [scale] converts the fixed point sizes to a canvas drawing the same rail at another size (the
 * undercut detail canvas, in px per pt); [dimText] is already in that canvas's unit. The page
 * passes 1.
 */
fun undercutRailRowHeightPt(dimText: Paint, dualStacked: Boolean, scale: Float = 1f): Float =
    if (!dualStacked) UNDERCUT_RAIL_ROW_HEIGHT_PT * scale
    else maxOf(UNDERCUT_RAIL_ROW_HEIGHT_PT * scale, dimText.dualStackMetrics().height + 3f * scale)

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
    /**
     * The side a S.E.T. lead runs out of ([undercutStripSetSide]) and the drawn room it needs
     * there ([undercutSetLeadRoomPt]); that side only is widened further
     * ([undercutWidenForSetLead]) so the lead's value seats in its line. Null / 0 — no lead —
     * leaves the range exactly as the pad rule alone decides it.
     */
    leadSide: UndercutAnchorSide? = null,
    leadRoomPt: Float = 0f,
): UndercutStripDrawRangeMm {
    val base = computeUndercutStripPadRange(
        drawStartMm, drawEndMm, chainStartMm, chainEndMm, stripLeftPt, stripRightPt, oalMm,
        edgeInsetPt, minPadPt, minPtPerMm, maxPtPerMm,
    )
    if (leadSide == null || leadRoomPt <= 0f || drawEndMm - drawStartMm <= UNDERCUT_RAIL_SPAN_EPS_MM) return base
    val availPt = (stripRightPt - stripLeftPt - 2f * edgeInsetPt).coerceAtLeast(1f)
    return undercutWidenForSetLead(
        base.startMm, base.endMm, chainStartMm, chainEndMm, leadSide, leadRoomPt,
        otherMinPadPt = minPadPt.coerceIn(0f, availPt * UNDERCUT_STRIP_MAX_PAD_FRAC),
        availPt = availPt, oalMm = oalMm.coerceAtLeast(0f),
        minPtPerMm = minPtPerMm, maxPtPerMm = maxPtPerMm,
    )
}

/** [computeUndercutStripDrawRange]'s per-side minimum-pad rule alone (no S.E.T. lead). */
private fun computeUndercutStripPadRange(
    drawStartMm: Float,
    drawEndMm: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    stripLeftPt: Float,
    stripRightPt: Float,
    oalMm: Float,
    edgeInsetPt: Float,
    minPadPt: Float,
    minPtPerMm: Float,
    maxPtPerMm: Float,
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
 *
 * The below band reads, top → bottom: the chain's own below-line fallback rows
 * ([chainBelowRows]), the nested chain rows ([nestedRows]), then — unless the chain's own rows
 * already fill that place — one row of CLEAR AIR, the LOWEST row, between the last rail line and
 * the shaft. So `belowRows == chainBelowRows + nestedRows + clear`, with `clear` = 1 when the
 * chain draws no rows below its line (lifted, no total rail, or a total with no fallbacks) and 0
 * in the compressed layout (total rail, chain fallbacks below the line). A started strip keeps
 * the whole [WEAR_RAIL_MAX_LABEL_ROWS] budget below with [chainBelowRows] = 0.
 */
data class UndercutRailRowPlan(
    /** Rows reserved between the rail line and the cylinder top (never 0), the chain's own
     *  below-line rows ([chainBelowRows]), the nested chain rows ([nestedRows]) and the clear row
     *  included. */
    val belowRows: Int,
    /** Fallback rows stacked ABOVE the rail line; 0 when fallbacks tuck below instead. */
    val aboveRows: Int,
    /** The fallback-label rows the level-0 chain itself draws BELOW its line: 0 when its
     *  fallbacks go above it (lifted, or no total rail) or it has none, otherwise the rows used.
     *  The nested rows stack directly under these (`undercutNestedRailLineYs`). */
    val chainBelowRows: Int,
    /** How many of [belowRows] belong to the nested chain rows ([buildNestedUndercutRailRows]) —
     *  one for each level's rail line plus that line's own fallback label rows. */
    val nestedRows: Int = 0,
)

/**
 * Fallback label rows one resolved rail uses — only spans whose value does NOT seat in the
 * line's break need a row. Shared by [planUndercutRailRows]'s reservation and the composer's
 * nested-row stepping, so the budgeted rows and the drawn rows cannot drift apart.
 */
fun undercutRailFallbackRows(layout: List<WearRailSpanLayout>): Int =
    (layout.filter { !it.seatsInBreak }.maxOfOrNull { it.labelRow + 1 } ?: 0)
        .coerceAtMost(WEAR_RAIL_MAX_LABEL_ROWS)

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
 *   above), reserving the rows used, never less than 1 — the compressed layout.
 * - **Total rail present, [lifted]** → fallbacks go ABOVE the line, in rows between the chain and
 *   the total rail, and below keeps just the 1-row clear air (on-device request: a strip with room
 *   above its rails reads better with the short values over the chain than crammed between the
 *   chain and the shaft). Whether a strip can afford it is the caller's decision
 *   ([undercutRailsLift]); this only plans the rows.
 * - **Chain is the only label level** → fallbacks go ABOVE the line (on-device report: a small
 *   value pushed under the span line read as orphaned with nothing above the rail), and below
 *   keeps just the 1-row clear air off the shaft.
 * - **Started strip** → no chain at all; the full [WEAR_RAIL_MAX_LABEL_ROWS] budget stays below,
 *   the machinist's band to hand-draw a chain into.
 *
 * [nestedLayouts] are the resolved nested chain rows ([buildNestedUndercutRailRows], one per
 * nesting level) that stack UNDER the level-0 chain, directly below the chain's own below-line
 * rows ([UndercutRailRowPlan.chainBelowRows]). Each claims a row for its own rail line plus the
 * rows its fallback labels land on — ABOVE that line (`undercutNestedRailLineYs`) — so the strip
 * reserves the height the extra physical rails actually need instead of drawing them into the
 * cylinder. The clear-air row, where the plan keeps one, is the LOWEST row of the below band,
 * between the last nested line and the shaft.
 */
fun planUndercutRailRows(
    chainLayout: List<WearRailSpanLayout>,
    startedStrip: Boolean,
    hasTotalRail: Boolean,
    nestedLayouts: List<List<WearRailSpanLayout>> = emptyList(),
    lifted: Boolean = false,
): UndercutRailRowPlan {
    if (startedStrip) {
        return UndercutRailRowPlan(belowRows = WEAR_RAIL_MAX_LABEL_ROWS, aboveRows = 0, chainBelowRows = 0)
    }
    val used = undercutRailFallbackRows(chainLayout)
    val nestedRows = nestedLayouts.sumOf { 1 + undercutRailFallbackRows(it) }
    return if (hasTotalRail && lifted && used > 0) {
        // Lifting costs exactly ONE row of strip height: `used` rows are added above the line and
        // `used - 1` freed below it (the below band keeps its 1 row of clear air off the shaft).
        UndercutRailRowPlan(belowRows = 1 + nestedRows, aboveRows = used, chainBelowRows = 0, nestedRows = nestedRows)
    } else if (hasTotalRail) {
        // The chain's fallbacks tuck below its line; with none, the one reserved row is clear air.
        UndercutRailRowPlan(
            belowRows = used.coerceAtLeast(1) + nestedRows, aboveRows = 0, chainBelowRows = used,
            nestedRows = nestedRows,
        )
    } else {
        UndercutRailRowPlan(belowRows = 1 + nestedRows, aboveRows = used, chainBelowRows = 0, nestedRows = nestedRows)
    }
}

/**
 * One strip's dimension rails, planned: the raw spans (strip mm) and their resolved layouts for
 * the level-0 chain, each nesting level's row, and the total, plus the fallback-row reservation
 * ([planUndercutRailRows]) the vertical split is sized from. Built only by
 * [planUndercutStripRails].
 */
data class UndercutStripRails(
    /** The level-0 chain over the strip's TOP-LEVEL cuts ([buildUndercutRailSpans]). */
    val chainSpans: List<WearRailSpan>,
    /** One row per nesting level ≥ 1, located against the parents' edges ([buildNestedUndercutRailRows]). */
    val nestedSpans: List<List<WearRailSpan>>,
    /** The run across the top-level cuts ([buildUndercutTotalSpan]); null with fewer than two. */
    val totalSpan: WearRailSpan?,
    val chain: List<WearRailSpanLayout>,
    val nested: List<List<WearRailSpanLayout>>,
    /** The total's one-span layout; empty when [totalSpan] is null. */
    val total: List<WearRailSpanLayout>,
    val rows: UndercutRailRowPlan,
    /** The S.E.T. lead continuing the level-0 chain outward ([buildUndercutSetLeadSpan]); null when none. */
    val leadSpan: UndercutSetLeadSpan? = null,
    /** [leadSpan]'s layout, planned TOGETHER with [chain] (one row assignment); null when none. */
    val lead: WearRailSpanLayout? = null,
) {
    /**
     * The level-0 rail line as drawn: [chain] with the [lead] at its outboard end, AFT → FWD —
     * the list the vertical plan reserved rows for and `drawUndercutStripRails` draws.
     */
    val chainWithLead: List<WearRailSpanLayout>
        get() = when {
            lead == null -> chain
            leadSpan?.side == UndercutAnchorSide.AFT_SET -> listOf(lead) + chain
            else -> chain + lead
        }

    /**
     * Whether lifting would move anything: a total rail above the chain, and at least one level-0
     * value (the S.E.T. lead's included) that falls off its line and is still planned below it.
     */
    val canLiftFallbacks: Boolean
        get() = totalSpan != null && rows.aboveRows == 0 && undercutRailFallbackRows(chainWithLead) > 0

    /**
     * These rails with the level-0 chain's fallback values planned ABOVE the line, under the total
     * rail ([planUndercutRailRows] `lifted`). Rows only: every span keeps the horizontal layout
     * (x, seat, label row) it was planned with. Unchanged when [canLiftFallbacks] is false.
     */
    fun withFallbacksLifted(): UndercutStripRails =
        if (!canLiftFallbacks) this
        else copy(
            rows = planUndercutRailRows(
                chainWithLead, startedStrip = false, hasTotalRail = true, nestedLayouts = nested, lifted = true,
            ),
        )
}

/**
 * Whether a strip lifts its chain's fallback values above the line
 * ([UndercutStripRails.withFallbacksLifted]): yes when the strip, with the lifted rows reserved,
 * still draws a shaft at least [cylFloorPt] tall. On a roomy strip the extra row comes out of the
 * surplus the cylinder cap would otherwise have spent as air; on a cramped one the lifted cylinder
 * would drop under the floor, so the values stay below the line — the compressed layout a busy
 * sheet keeps (on-device request: use the room above the rails when the sheet has it, compress
 * when it does not). [cylLiftedPt] is the cylinder height of the strip's LIFTED inner layout.
 */
fun undercutRailsLift(cylLiftedPt: Float, cylFloorPt: Float = UNDERCUT_CYL_MAX_FLOOR_PT): Boolean =
    cylLiftedPt >= cylFloorPt

/**
 * The ONE rail plan behind both undercut strip draw sites — the printed strip
 * (`UndercutPdfComposer`) and the Undercut tab's detail canvas (`UndercutWindowDetailOverlay`) —
 * so the canvas rail is the printed rail by construction.
 *
 * Nesting decides what the level-0 chain carries: a cut machined INSIDE another one is
 * dimensioned on its own row against its PARENT's edges ([buildNestedUndercutRailRows]), never in
 * the top chain's forward-cursor walk, where a fully nested cut collapses to zero width and
 * vanishes. The total is the run across the TOP-LEVEL cuts: a lone parent holding one child would
 * otherwise re-state its own length. Every rail is resolved before the vertical split (it is pure
 * horizontal geometry), so the split reserves only the fallback rows these labels actually use,
 * on the side of the line the plan puts them.
 *
 * [spans] are the strip's render-clamped spans, every nesting level, in any order. A
 * [startedStrip] dimensions nothing: with no cuts the chain would degenerate to a single
 * liner-length span nobody measured. [xAt] maps strip mm to the caller's drawing x and
 * [labelWidth] measures a value as that caller draws it — measure and draw must convert together.
 * [scale] multiplies the layout's fixed point sizes (text pad, arrowhead, label gap, arrow
 * clearance) for a caller drawing in another unit; the page passes 1, and at 1 every value is the
 * page's own.
 *
 * [arrowSizePt] is the app-wide "Dimension arrows" head (`PdfPrefs.arrowSizePt`, in points before
 * [scale]): whether a value seats in its break and which way the heads point both depend on it, so
 * the plan and `drawUndercutRail` must be handed the same value.
 */
fun planUndercutStripRails(
    chainStartMm: Float,
    chainEndMm: Float,
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
    dual: Boolean,
    splitAtMm: List<Float>,
    startedStrip: Boolean,
    xAt: (Float) -> Float,
    labelWidth: (DualLabel) -> Float,
    scale: Float = 1f,
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /**
     * The S.E.T. this strip's chain continues to ([undercutStripSetSide]) with the strip's DRAWN
     * range — the lead span ([buildUndercutSetLeadSpan]) is laid out with the level-0 chain, so its
     * value takes a row in the same assignment and the vertical plan reserves it. Null = no lead.
     */
    setLead: UndercutSetLead? = null,
    /**
     * Whether the strip prints its total rail ([buildUndercutTotalSpan]) — the job's resolved
     * "Total length of cuts" choice (`effectiveUndercutTotalRail`). Off, the plan carries no total,
     * so the vertical split reserves no total band and the chain's fallbacks go above its line,
     * exactly as on a strip with a single cut.
     */
    showTotal: Boolean = true,
): UndercutStripRails {
    val sorted = spans.sortedBy { it.startMm }
    val nestingById = undercutNestingForest(sorted).associateBy { it.id }
    val topSpans = sorted.filter { (nestingById[it.id]?.level ?: 0) == 0 }
    val chainSpans = if (startedStrip) emptyList()
    else buildUndercutRailSpans(chainStartMm, chainEndMm, topSpans, unit, dual, splitAtMm = splitAtMm)
    val totalSpan = if (startedStrip || !showTotal) null else buildUndercutTotalSpan(topSpans, unit, dual)
    val nestedSpans = if (startedStrip) emptyList() else buildNestedUndercutRailRows(sorted, unit, dual)

    fun layout(rail: List<WearRailSpan>) = layoutWearStripRail(
        rail,
        xAtStripMm = xAt,
        labelWidthPt = labelWidth,
        textPadPt = DIM_BREAK_TEXT_PAD_PT * scale,
        arrowSizePt = arrowSizePt * scale,
        minLabelGapPt = UNDERCUT_RAIL_MIN_LABEL_GAP_PT * scale,
        arrowClearPt = DimensionRailLayout.ARROW_CLEAR * scale,
    )
    // The lead rides the level-0 line, so it is laid out WITH the chain: one row assignment, one
    // collision space for every value on that line.
    val leadSpan = if (startedStrip || chainSpans.isEmpty()) null
    else setLead?.let { buildUndercutSetLeadSpan(chainStartMm, chainEndMm, it, unit, dual) }
    val leadFirst = leadSpan?.side == UndercutAnchorSide.AFT_SET
    val line = when {
        leadSpan == null -> chainSpans
        leadFirst -> listOf(leadSpan.span) + chainSpans
        else -> chainSpans + leadSpan.span
    }
    val lineLayout = layout(line)
    val leadIndex = when {
        leadSpan == null -> -1
        leadFirst -> 0
        else -> lineLayout.lastIndex
    }
    val chain = if (leadIndex < 0) lineLayout else lineLayout.filterIndexed { i, _ -> i != leadIndex }
    val lead = if (leadSpan == null) null else lineLayout.getOrNull(leadIndex)?.let { l ->
        if (!leadSpan.openOutboard) l
        else {
            // A half dimension has ONE head, at the datum: it points in from the open side unless
            // the line is too short to hold even that one head.
            val inward = (l.x1Pt - l.x0Pt) >= (arrowSizePt + DimensionRailLayout.ARROW_CLEAR) * scale
            l.copy(arrowInward = inward, openStart = leadFirst, openEnd = !leadFirst)
        }
    }
    val nested = nestedSpans.map { layout(it) }
    val total = totalSpan?.let { layout(listOf(it)) } ?: emptyList()
    val rows = planUndercutRailRows(lineLayout, startedStrip, hasTotalRail = totalSpan != null, nestedLayouts = nested)
    return UndercutStripRails(chainSpans, nestedSpans, totalSpan, chain, nested, total, rows, leadSpan, lead)
}

/**
 * What a strip's S.E.T. lead needs: the [side] it runs to ([undercutStripSetSide]), that S.E.T.'s
 * shaft-space station [setMm], and the strip's DRAWN range `[drawStartMm, drawEndMm]` — the lead
 * stops at the drawn edge when the S.E.T. is off the strip.
 */
data class UndercutSetLead(
    val side: UndercutAnchorSide,
    val setMm: Float,
    val drawStartMm: Float,
    val drawEndMm: Float,
)

/**
 * A strip's S.E.T. lead, in strip mm: [span] runs from the chain's outer witness on [side] outward,
 * labelled with the full datum → S.E.T. distance. [openOutboard] = the S.E.T. lies off the drawn
 * strip, so [span] stops at the drawn edge and its outboard end is OPEN (no head, no witness) — a
 * half dimension; false = the S.E.T. is on the strip and [span] is a normal two-headed dimension
 * ending on a witness line at the S.E.T.
 */
data class UndercutSetLeadSpan(
    val span: WearRailSpan,
    val side: UndercutAnchorSide,
    val openOutboard: Boolean,
    /** The datum → S.E.T. distance the label states, mm. */
    val valueMm: Float,
)

/**
 * The lead that continues a strip's level-0 chain out to its S.E.T. (on-device request: the
 * distance to the S.E.T. is in the title, but as the leading or ending dimension it reads on with
 * the chain). The datum is the chain's outer witness on [lead]'s side — [chainEndMm] for the FWD
 * S.E.T., [chainStartMm] for the AFT — and the value is datum → S.E.T., formatted like every other
 * chain value.
 *
 * No lead when the chain already ends AT that S.E.T. ("Draw to the shaft end" reached it), when
 * the S.E.T. lies on the wrong side of the datum (a cut outboard of its S.E.T.), or when the drawn
 * strip leaves no room past the datum to draw one — each within [UNDERCUT_TOUCH_EPS_MM].
 */
fun buildUndercutSetLeadSpan(
    chainStartMm: Float,
    chainEndMm: Float,
    lead: UndercutSetLead,
    unit: UnitSystem,
    dual: Boolean = false,
): UndercutSetLeadSpan? {
    val eps = UNDERCUT_TOUCH_EPS_MM
    val value = undercutSetLeadValueMm(chainStartMm, chainEndMm, lead.side, lead.setMm) ?: return null
    return if (lead.side == UndercutAnchorSide.FWD_SET) {
        val onStrip = lead.setMm <= lead.drawEndMm + eps
        val end = if (onStrip) lead.setMm else lead.drawEndMm
        if (end - chainEndMm <= eps) return null
        UndercutSetLeadSpan(
            WearRailSpan(chainEndMm, end, formatLenDimDualLabel(value.toDouble(), unit, dual)),
            lead.side, openOutboard = !onStrip, valueMm = value,
        )
    } else {
        val onStrip = lead.setMm >= lead.drawStartMm - eps
        val start = if (onStrip) lead.setMm else lead.drawStartMm
        if (chainStartMm - start <= eps) return null
        UndercutSetLeadSpan(
            WearRailSpan(start, chainStartMm, formatLenDimDualLabel(value.toDouble(), unit, dual)),
            lead.side, openOutboard = !onStrip, valueMm = value,
        )
    }
}

/**
 * The value a strip's S.E.T. lead states — chain datum on [side] → that S.E.T. at [setMm], mm — or
 * null when there is no lead to draw: the chain already reaches the S.E.T., or the S.E.T. lies on
 * the wrong side of the datum (within [UNDERCUT_TOUCH_EPS_MM]). The ONE rule behind
 * [buildUndercutSetLeadSpan] and the room the drawn range makes for it ([undercutSetLeadRoomPt]).
 */
fun undercutSetLeadValueMm(
    chainStartMm: Float,
    chainEndMm: Float,
    side: UndercutAnchorSide,
    setMm: Float,
): Float? {
    val value = if (side == UndercutAnchorSide.FWD_SET) setMm - chainEndMm else chainStartMm - setMm
    return value.takeIf { it > UNDERCUT_TOUCH_EPS_MM }
}

/**
 * The shortest stub a S.E.T. lead keeps each side of its padded value, points, before [scale]: the
 * open tail past the value and — the value being centred in its line — the same stub on the datum
 * side that carries the head. Enough line beyond the value that the half dimension reads as the
 * chain running on toward an S.E.T. off the strip; any longer only spends stock the strip has to
 * draw for nothing (on-device request: the lead's stock was longer than its text needed).
 * [undercutSetLeadRoomPt] raises it to an arrowhead plus a point when the head is larger.
 */
const val UNDERCUT_SET_LEAD_TAIL_PT = 5f

/**
 * The drawn width a strip's S.E.T. lead needs for its value to SEAT in a break in its own line
 * (on-device report: a lead value that fell off onto a leader read as a stray label, not as the
 * chain continuing to the S.E.T.): the value's measured width ([labelWidthPt], the very measure the
 * rail planner uses), a [DIM_BREAK_TEXT_PAD_PT] each side of it, and a stub each side of
 * [UNDERCUT_SET_LEAD_TAIL_PT] or one arrowhead plus a point, whichever is longer — the datum stub
 * carries the head, the other is the open tail. The planner's seat test (an arrowhead of line each
 * side of the padded value) is then met by at least a point on each side. [scale] converts the
 * fixed point sizes for a caller in another unit (the detail canvas); [labelWidthPt] is already in
 * that unit.
 */
fun undercutSetLeadRoomPt(
    labelWidthPt: Float,
    arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    scale: Float = 1f,
): Float = labelWidthPt + 2f * DIM_BREAK_TEXT_PAD_PT * scale +
    2f * maxOf(UNDERCUT_SET_LEAD_TAIL_PT, arrowSizePt + 1f) * scale

/**
 * [drawStartMm, drawEndMm] widened OUTWARD on the lead's [side] only, so the S.E.T. lead gets
 * [leadRoomPt] of drawn line past its chain datum at the strip's FINAL scale, while the other side
 * keeps at least [otherMinPadPt] past its datum. The scale is `availPt / drawn length`, clamped to
 * `[minPtPerMm, maxPtPerMm]` — the caller's own mapping — and widening lowers it, so the two pads
 * are solved together by a short fixed-point walk (each step only widens, and it converges whenever
 * the two requirements fit in [availPt]). Clamped to `[0, oalMm]`: a lead that would run past a
 * shaft end stops there.
 *
 * Only draws more stock heading toward the S.E.T. — no datum moves, no dimension changes. A range
 * that already gives both sides their room comes back as the very same values.
 */
fun undercutWidenForSetLead(
    drawStartMm: Float,
    drawEndMm: Float,
    chainStartMm: Float,
    chainEndMm: Float,
    side: UndercutAnchorSide,
    leadRoomPt: Float,
    otherMinPadPt: Float,
    availPt: Float,
    oalMm: Float,
    minPtPerMm: Float = 0f,
    maxPtPerMm: Float = Float.MAX_VALUE,
): UndercutStripDrawRangeMm {
    val unchanged = UndercutStripDrawRangeMm(drawStartMm, drawEndMm)
    if (leadRoomPt <= 0f || availPt <= 0f) return unchanged
    val chainLen = (chainEndMm - chainStartMm).coerceAtLeast(0f)
    val reqAft = if (side == UndercutAnchorSide.AFT_SET) leadRoomPt else otherMinPadPt
    val reqFwd = if (side == UndercutAnchorSide.FWD_SET) leadRoomPt else otherMinPadPt
    val maxAft = (chainStartMm - 0f).coerceAtLeast(0f)
    val maxFwd = (oalMm - chainEndMm).coerceAtLeast(0f)
    var aft = (chainStartMm - drawStartMm).coerceAtLeast(0f)
    var fwd = (drawEndMm - chainEndMm).coerceAtLeast(0f)
    var changed = false
    repeat(64) {
        val len = (chainLen + aft + fwd).coerceAtLeast(UNDERCUT_RAIL_SPAN_EPS_MM)
        val ptPerMm = (availPt / len).coerceIn(minPtPerMm, maxPtPerMm).coerceAtLeast(1e-6f)
        val nextAft = maxOf(aft, reqAft / ptPerMm).coerceAtMost(maxOf(aft, maxAft))
        val nextFwd = maxOf(fwd, reqFwd / ptPerMm).coerceAtMost(maxOf(fwd, maxFwd))
        if (nextAft - aft <= 1e-4f && nextFwd - fwd <= 1e-4f) {
            return if (!changed) unchanged
            else UndercutStripDrawRangeMm(
                minOf(drawStartMm, chainStartMm - aft), maxOf(drawEndMm, chainEndMm + fwd),
            )
        }
        aft = nextAft
        fwd = nextFwd
        changed = true
    }
    return UndercutStripDrawRangeMm(minOf(drawStartMm, chainStartMm - aft), maxOf(drawEndMm, chainEndMm + fwd))
}

/**
 * The head length a rail's fallback label rows are spaced clear of, points
 * ([undercutRailFallbackBaselineY]). Fixed, never the "Dimension arrows" pref: every vertical budget
 * the strip reserves (the total band, the row pitch) is measured off these rows, so a head-size
 * change moves no row. Row 0 still clears the largest head the pref offers
 * ([PDF_ARROW_SIZE_LARGE_PT]) by the label gap less half a point.
 */
const val UNDERCUT_RAIL_LABEL_CLEAR_ARROW_PT = 4f

/** Least horizontal gap between two fallback values on one row, points. */
const val UNDERCUT_RAIL_MIN_LABEL_GAP_PT = 4f

/**
 * Splits one strip's vertical band, reserving [totalRailBandPt] at the top for the
 * cluster's total-span rail when [hasTotalRail] and [chainAboveBandPt] for the chain's
 * above-the-line fallback rows ([planUndercutRailRows] — a chain with no total, or a lifted one
 * under a total), then delegating everything below them to
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
 *
 * With both a total and an above band the stack reads, top → bottom: the total's value
 * ([totalRailAbovePt]), the total line, the rest of [totalRailBandPt] as the gap down to the
 * chain's lifted fallback rows ([chainAboveBandPt]), the chain line. A zero above band leaves
 * the total-only layout exactly as it was.
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
    // Above-the-line fallback rows ([planUndercutRailRows]) reserve their band at the top exactly
    // as the total rail's does; under a total they sit between the total line and the chain.
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
    // strip even when a short band pins the rail high — under a total, below the total's gap too.
    val totalGap = (band - totalRailAbovePt).coerceAtLeast(0f)
    val railFloor = (stripTop + aboveBand + if (aboveBand > 0f && band > 0f) totalGap else 0f)
        .coerceAtMost(cylTop)
    val chainRailY = (cylTop - inner.railLabelRows * rowHeightPt - extraRailHeadroom)
        .coerceIn(railFloor, cylTop)
    val totalRailY = if (band > 0f) {
        (chainRailY - aboveBand - totalGap).coerceIn(stripTop, chainRailY)
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
 * An undercut whose span starts at or before the cursor (a PARTIALLY overlapping pair, which
 * the confirm gate blocks for new entry but older records may still carry) has its effective
 * start pulled forward to the cursor, so the chain never runs backward or double-counts the
 * overlap.
 *
 * [spans] must be the strip's **TOP-LEVEL** spans: a fully nested cut would be absorbed by that
 * same cursor rule and vanish from the chain, so nested levels get their own rows instead
 * ([buildNestedUndercutRailRows], anchored on the parent's edges).
 */
fun buildUndercutRailSpans(
    chainStartMm: Float,
    chainEndMm: Float,
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
    /** The strip's resolved unit (liner override, else the document unit for a bare-shaft
     *  strip) is already picked by the caller; this only carries the sheet-wide dual flag. */
    dual: Boolean = false,
    /**
     * Extra chain NODES: every chained span strictly containing one of these stations (more than
     * the rail epsilon inside it on both sides) is dimensioned as two spans meeting there, so the
     * lengths still sum to the chain. A liner strip passes its BURIED edges
     * (`geom/UndercutMath.kt`'s `buriedUndercutLinerEdgesMm`) only while
     * `PdfPrefs.undercutSplitAtLinerEdge` is on ("Split cuts at liner edges"): split at the
     * edge, a cut machined past the liner's end into the body reads against the liner edge's
     * position, and the strip title's liner-to-S.E.T. figure stays the locating datum. The pref
     * is off by default because the shop measures the length of each cut, so a cut prints whole
     * (on-device direction). A station outside every span, or on an existing node, changes
     * nothing; the default empty list leaves the chain exactly as it was.
     */
    splitAtMm: List<Float> = emptyList(),
): List<WearRailSpan> {
    val out = mutableListOf<WearRailSpan>()
    val nodes = splitAtMm.sorted()
    fun emit(a: Float, b: Float) {
        var from = a
        nodes.forEach { n ->
            if (n - from > UNDERCUT_RAIL_SPAN_EPS_MM && b - n > UNDERCUT_RAIL_SPAN_EPS_MM) {
                out += WearRailSpan(from, n, formatLenDimDualLabel((n - from).toDouble(), unit, dual))
                from = n
            }
        }
        out += WearRailSpan(from, b, formatLenDimDualLabel((b - from).toDouble(), unit, dual))
    }
    var cursor = chainStartMm
    spans.forEach { s ->
        if (s.endMm - s.startMm <= UNDERCUT_RAIL_SPAN_EPS_MM) return@forEach
        val effStart = maxOf(s.startMm, cursor)
        if (effStart - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) emit(cursor, effStart)
        val end = maxOf(s.endMm, effStart)
        if (end - effStart > UNDERCUT_RAIL_SPAN_EPS_MM) emit(effStart, end)
        cursor = maxOf(cursor, end)
    }
    if (chainEndMm - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) emit(cursor, chainEndMm)
    return out
}

/**
 * The extra chain rows a strip's NESTED cuts need — one per nesting level ≥ 1, in ascending
 * level order, each a complete chain over the parents at the level above.
 *
 * A cut machined inside another is located against **its parent's own edges**, not the strip's
 * datums: for every level-k cut the row carries `[parent start → child start]`, `[child span]`,
 * `[child end → parent end]`, and several children of one parent chain in sequence
 * (`parentStart → c1, c1, c1 → c2, c2, …, cn → parentEnd`). Parents at one level are disjoint by
 * construction, so a level always lays out on ONE row.
 *
 * This is why [buildUndercutRailSpans] must be fed the TOP-LEVEL spans only: its forward cursor
 * walks aft → fwd and pulls an overlapping span's start up to the cursor, which collapses a fully
 * nested cut to zero width — it silently vanishes from the chain. That absorb rule is deliberate
 * for LEGACY partially-overlapping data and is unchanged; it simply never sees a nested cut now.
 *
 * [spans] are the strip's render-clamped spans (any order — the forest and the chains sort for
 * themselves). Zero-length spans are omitted exactly as the level-0 chain omits them, so each
 * parent's row still sums to that parent's span length.
 */
fun buildNestedUndercutRailRows(
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
    /** The strip's resolved unit is already picked by the caller; this carries the dual flag. */
    dual: Boolean = false,
): List<List<WearRailSpan>> {
    val nestingById = undercutNestingForest(spans).associateBy { it.id }
    val byId = spans.associateBy { it.id }
    val maxLevel = nestingById.values.maxOfOrNull { it.level } ?: 0
    if (maxLevel < 1) return emptyList()

    return (1..maxLevel).mapNotNull { level ->
        val childrenByParent = spans
            .filter { nestingById[it.id]?.level == level }
            .groupBy { nestingById.getValue(it.id).parentId }
        val out = mutableListOf<WearRailSpan>()
        childrenByParent.entries
            .sortedBy { (parentId, _) -> byId[parentId]?.startMm ?: 0f }
            .forEach { (parentId, children) ->
                val parent = byId[parentId] ?: return@forEach
                var cursor = parent.startMm
                children.sortedBy { it.startMm }.forEach { child ->
                    val start = maxOf(child.startMm, cursor)
                    if (start - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) {
                        out += WearRailSpan(
                            cursor, start, formatLenDimDualLabel((start - cursor).toDouble(), unit, dual),
                        )
                    }
                    val end = maxOf(child.endMm, start)
                    if (end - start > UNDERCUT_RAIL_SPAN_EPS_MM) {
                        out += WearRailSpan(
                            start, end, formatLenDimDualLabel((end - start).toDouble(), unit, dual),
                        )
                    }
                    cursor = maxOf(cursor, end)
                }
                if (parent.endMm - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) {
                    out += WearRailSpan(
                        cursor, parent.endMm,
                        formatLenDimDualLabel((parent.endMm - cursor).toDouble(), unit, dual),
                    )
                }
            }
        out.takeIf { it.isNotEmpty() }
    }
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
fun buildUndercutTotalSpan(spans: List<UndercutSpanMm>, unit: UnitSystem, dual: Boolean = false): WearRailSpan? {
    val live = spans.filter { it.endMm - it.startMm > UNDERCUT_RAIL_SPAN_EPS_MM }
    if (live.size < 2) return null
    val first = live.minOf { it.startMm }
    val last = live.maxOf { it.endMm }
    if (last - first <= UNDERCUT_RAIL_SPAN_EPS_MM) return null
    return WearRailSpan(first, last, formatLenDimDualLabel((last - first).toDouble(), unit, dual))
}

// ──────────────────────────────────────────────────────────────────────────────
// Measured-Ø stations
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Measured-Ø callout stations for one strip: one per visible PIECE of each undercut's own floor
 * ([undercutCalloutAnchorsMm] — the clamped span's centre for a cut with nothing machined inside
 * it; a parent split by a nested cut gets a station on every piece wide enough, each carrying the
 * same value, so the two sides read as ONE diameter), labelled with [formatDiaWithUnit] (no "Ø"
 * prefix — the footer/callout convention). Station keys therefore repeat for a split cut.
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
    labelWidthPt: (DualLabel) -> Float,
    dual: Boolean = false,
): List<DiaCalloutStation> {
    // The anchors are derived from EVERY drawable cut on the strip, not just the ones carrying a
    // Ø: an unmeasured child still replaces its parent's floor over its own span, so it still has
    // to be subtracted or the parent's leader lands on it.
    val spans = undercuts.mapNotNull { u ->
        val s = clampedById[u.id] ?: return@mapNotNull null
        if (s.isEmpty) null else UndercutSpanMm(u.id, s.startMm, s.endMm)
    }
    // One station per labelled piece of a cut's floor: a parent split by a nested cut prints its
    // Ø on EVERY piece wide enough, so the keys repeat and consumers look up by id.
    val anchorsById = undercutCalloutAnchorsMm(spans)
    return undercuts.flatMap { u ->
        if (u.diaMm <= 0f) return@flatMap emptyList()
        val anchors = anchorsById[u.id] ?: return@flatMap emptyList()
        val label = formatDiaWithUnitDualLabel(u.diaMm.toDouble(), unit, dual)
        val width = labelWidthPt(label)
        anchors.map { anchorMm ->
            DiaCalloutStation(
                key = u.id,
                stationX = xAtMm(anchorMm),
                label = label,
                labelWidth = width,
            )
        }
    }
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
    /**
     * The side already chosen ([undercutStripSetSide] — the job's "Distance to S.E.T." choice):
     * AFT measures the first shoulder from the AFT S.E.T., FWD the last shoulder from the FWD.
     * Null keeps the proximity rule.
     */
    forcedSide: UndercutAnchorSide? = null,
): UndercutAnchor {
    // Side choice delegates to the shared proximity rule (`geom/UndercutMath.kt`), the same
    // one that seeds a bare-shaft cut's default Distance reference in the editor — so the
    // printed anchor and the card's default always read from the same SET.
    val side = when (forcedSide) {
        UndercutAnchorSide.AFT_SET -> UndercutReference.AFT_SET
        UndercutAnchorSide.FWD_SET -> UndercutReference.FWD_SET
        null -> nearestSetReference(firstShoulderMm, lastShoulderMm, aftSetXMm, fwdSetXMm)
    }
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
fun buildUndercutAnchorLabel(anchor: UndercutAnchor, unit: UnitSystem, dual: Boolean = false): String {
    val suffix = linerAnchorSuffix(
        if (anchor.side == UndercutAnchorSide.AFT_SET) LinerAnchor.AFT_SET else LinerAnchor.FWD_SET,
    )
    return "${formatLenDimDual(anchor.distanceMm.toDouble(), unit, dual)} $suffix"
}

/**
 * A strip's printed title row. A liner-anchored strip is named — the liner's shared display
 * title (`util/LinerTitles.kt`'s `buildLinerTitleById`: custom label, else the positional
 * AFT/MID/FWD default) — so the sheet reads "AFT Liner — 250.0 FROM AFT S.E.T.", exactly
 * the wear strip's `name — anchor` construction and exactly the name the carousel, the wear
 * sheet, and the runout sheet already show for that liner. A bare-shaft strip has nothing to
 * name (an auto-body span carries no shop identity), so it prints the anchor alone.
 *
 * [linerOdLabel] is the strip liner's OD as printed (`Ø10.000"`, formatted in the liner's own
 * display unit — the unit the strip's values print in), set after the name with a space:
 * `FWD Liner Ø10.000" — 18 1/4" FROM FWD S.E.T.`, so the machinist reads the stock the cuts
 * were taken from without turning to the schematic. Null/blank on a bare-shaft strip, which
 * prints as before.
 *
 * [linerTitle] null/blank ⇒ anchor only; likewise a blank [anchorLabel] leaves just the name,
 * so neither side ever prints a dangling separator.
 */
fun buildUndercutStripTitle(linerTitle: String?, anchorLabel: String, linerOdLabel: String? = null): String {
    val name = listOf(linerTitle?.trim().orEmpty(), linerOdLabel?.trim().orEmpty())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    val anchor = anchorLabel.trim()
    return when {
        name.isEmpty() -> anchor
        anchor.isEmpty() -> name
        else -> "$name — $anchor"
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// The S.E.T. a strip measures to, and which strips print
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The S.E.T. [strip] measures to — the ONE side every consumer reads: the printed strip title's
 * anchor, the printed S.E.T. lead, and the detail canvas's lead. The job's [datum] AFT / FWD wins;
 * NEAREST is the title rule every strip printed before the choice existed:
 * - a liner strip: the liner's nearer edge-to-S.E.T. ([wearStripAnchorForSpan] over the liner span,
 *   ties AFT — `linerAnchorForPdf`'s rule);
 * - a bare-shaft strip: the cut run's proximity rule ([nearestSetReference] over [stripSpans]' first
 *   start and last end — `undercutAnchorFor`'s). [stripSpans] are the strip's own render-clamped
 *   spans, sorted AFT → FWD; empty falls back to the chain bounds.
 *
 * [sets] are the sheet's own S.E.T.s (`computeSetPositionsInMeasureSpace`).
 */
fun undercutStripSetSide(
    strip: UndercutStrip,
    spec: ShaftSpec,
    sets: SetPositions,
    datum: UndercutSetDatum,
    stripSpans: List<UndercutSpanMm> = emptyList(),
): UndercutAnchorSide = when (datum) {
    UndercutSetDatum.AFT -> UndercutAnchorSide.AFT_SET
    UndercutSetDatum.FWD -> UndercutAnchorSide.FWD_SET
    UndercutSetDatum.NEAREST -> when (strip) {
        is UndercutStrip.LinerStrip ->
            if (wearStripAnchorForSpan(spec, strip.linerStartMm, strip.linerEndMm, sets).anchor == LinerAnchor.FWD_SET) {
                UndercutAnchorSide.FWD_SET
            } else {
                UndercutAnchorSide.AFT_SET
            }
        is UndercutStrip.FreeStrip -> {
            val first = stripSpans.firstOrNull()?.startMm ?: strip.chainStartMm
            val last = stripSpans.lastOrNull()?.endMm ?: strip.chainEndMm
            if (nearestSetReference(first, last, sets.aftSETxMm.toFloat(), sets.fwdSETxMm.toFloat()) ==
                UndercutReference.AFT_SET
            ) {
                UndercutAnchorSide.AFT_SET
            } else {
                UndercutAnchorSide.FWD_SET
            }
        }
    }
}

/** This side as the shared liner-anchor wording's enum. */
fun UndercutAnchorSide.toLinerAnchor(): LinerAnchor =
    if (this == UndercutAnchorSide.AFT_SET) LinerAnchor.AFT_SET else LinerAnchor.FWD_SET

/**
 * Whether [strip] prints on this job's sheet ("Sections to print"): a liner strip unless its liner
 * id is in [UndercutRecord.excludedLinerIds] (its started strip on a blank draft included); a
 * bare-shaft strip unless EVERY one of its member cuts is in [UndercutRecord.excludedFreeCutIds] —
 * so a cut added to an excluded window brings the whole window back, never a partial chain. A strip
 * with no member cuts always prints.
 */
fun isUndercutStripPrinted(strip: UndercutStrip, record: UndercutRecord): Boolean = when (strip) {
    is UndercutStrip.LinerStrip -> strip.linerId !in record.excludedLinerIds
    is UndercutStrip.FreeStrip ->
        strip.undercutIds.isEmpty() || !strip.undercutIds.all { it in record.excludedFreeCutIds }
}
