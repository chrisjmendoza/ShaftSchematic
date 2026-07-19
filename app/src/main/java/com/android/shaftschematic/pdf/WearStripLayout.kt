// app/src/main/java/com/android/shaftschematic/pdf/WearStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.pdf.dim.buildLinerSpans
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Pure-math layout helpers for the wear-PDF per-liner detail strips
 * (`docs/LinerWearAreas_Proposal.md` §6.2, Phase 4). Deliberately free of any
 * `android.graphics`/`android.graphics.pdf` import so selection, clamping, and
 * vertical/horizontal banding can be unit-tested directly on the JVM (this repo's
 * unit tests are plain JUnit, no Robolectric) — see `WearStripLayoutTest`.
 *
 * `WearPdfComposer.kt` calls these functions and does the actual `Canvas` drawing;
 * this file only computes *where things go*.
 */

// ──────────────────────────────────────────────────────────────────────────────
// Selection: which liners get a strip, aft → fwd, max N per page
// ──────────────────────────────────────────────────────────────────────────────

/** One liner and the wear spots recorded against it (always non-empty). */
data class WearLinerGroup(val liner: Liner, val spots: List<WearSpot>)

/**
 * Groups [wearRecord]'s spots by liner, keeping only liners that are present in
 * [liners] AND have at least one spot. Sorted aft → fwd by the liner's physical
 * start position, matching the proposal's "order aft→fwd" rule (§10.4).
 *
 * Spots whose `linerId` doesn't match any liner in [liners] are silently dropped
 * here. The authoritative orphan-drop already happens at decode time
 * (`ShaftDocCodec`); this is a defensive second filter so a stale in-memory
 * [WearRecord] (e.g. liner deleted after load, before next save) can never crash
 * PDF layout.
 */
fun collectWearLinerGroups(liners: List<Liner>, wearRecord: WearRecord): List<WearLinerGroup> {
    if (wearRecord.spots.isEmpty() || liners.isEmpty()) return emptyList()
    val byLiner = wearRecord.spots.groupBy { it.linerId }
    return liners
        .mapNotNull { ln -> byLiner[ln.id]?.takeIf { it.isNotEmpty() }?.let { WearLinerGroup(ln, it) } }
        .sortedBy { it.liner.startFromAftMm }
}

/** Result of paginating strips: what fits on this page vs. what overflows. */
data class WearStripSelection(val onPage: List<WearLinerGroup>, val overflow: List<WearLinerGroup>)

/** Max detail strips per page (proposal §10.4: "auto ... max 3 strips/page with overflow page"). */
const val WEAR_STRIP_MAX_PER_PAGE = 3

/**
 * Minimum number of liners with recorded wear (i.e. `collectWearLinerGroups(...).size`, so
 * orphaned spots on a since-deleted liner are already excluded) that switches the wear PDF to
 * **strips-only** mode (2026-07-18 spec, `docs/LinerWearAreas_BuildLog_2026-07-18.md` "either/or"
 * rule): shop practice shows EITHER the per-liner detail strips OR the combined
 * shaft-profile-plus-strips page, never both stacked — but only once there are enough wear
 * liners that the combined page's profile-shrink starts genuinely crowding the page. Below this
 * threshold (1-2 wear liners) the existing combined layout is unchanged; see [WearPdfMode].
 * Coincidentally equal to [WEAR_STRIP_MAX_PER_PAGE] today (both are "3"), but they express
 * different constraints (page capacity vs. mode-switch threshold) and are kept as separate named
 * constants since either could change independently later.
 */
const val WEAR_STRIPS_ONLY_MIN_LINERS = 3

/** Which of the wear PDF's three rendering modes applies — see [determineWearPdfMode]. */
enum class WearPdfMode {
    /** No recorded wear spots (or every spot's liner was deleted): the blank hand-marking form. */
    PROFILE_FORM,
    /** 1-2 wear liners: today's combined page — shaft profile + bands, shrunk to fit strips below. */
    COMBINED,
    /** 3+ wear liners: strips-only page — no profile, no OAL line; strips fill the freed page. */
    STRIPS_ONLY,
}

/**
 * Resolves the wear PDF's rendering mode from how many liners have recorded wear
 * ([collectWearLinerGroups]'s result size — already excludes liners with zero spots and orphaned
 * spots on a since-deleted liner). Pure three-way rule so `WearPdfComposer` never has to
 * re-derive the threshold inline: `0` → [WearPdfMode.PROFILE_FORM], `1` or `2` →
 * [WearPdfMode.COMBINED], `[WEAR_STRIPS_ONLY_MIN_LINERS]` or more → [WearPdfMode.STRIPS_ONLY].
 */
fun determineWearPdfMode(wearLinerGroupCount: Int): WearPdfMode = when {
    wearLinerGroupCount <= 0 -> WearPdfMode.PROFILE_FORM
    wearLinerGroupCount < WEAR_STRIPS_ONLY_MIN_LINERS -> WearPdfMode.COMBINED
    else -> WearPdfMode.STRIPS_ONLY
}

/**
 * Splits [groups] into what fits on one page vs. overflow.
 *
 * NOTE on overflow handling: the current `composeWearPdf` signature draws into a
 * single caller-supplied `PdfDocument.Page` (see `WearRoute.kt` — it calls
 * `doc.startPage` once, passes that one `Page` in, then `doc.finishPage`). Growing
 * that into true multi-page output would require changing the function's
 * signature to accept the `PdfDocument` itself (or return a list of draw
 * callbacks) and updating every call site — out of scope for this pass per the
 * file-ownership split (`ui/` call sites are owned by a concurrent agent). Until
 * that lands, overflow beyond [WEAR_STRIP_MAX_PER_PAGE] is rendered as a single
 * text note line instead of a second page (see `drawWearOverflowNote` in
 * `WearPdfComposer.kt`).
 */
fun selectWearStripsForPage(
    groups: List<WearLinerGroup>,
    maxPerPage: Int = WEAR_STRIP_MAX_PER_PAGE,
): WearStripSelection {
    if (groups.size <= maxPerPage) return WearStripSelection(groups, emptyList())
    return WearStripSelection(groups.take(maxPerPage), groups.drop(maxPerPage))
}

// ──────────────────────────────────────────────────────────────────────────────
// Band clamping — a spot may overrun the liner only if the liner was shortened
// after the spot was recorded; render clamped, keep the stored data untouched.
// ──────────────────────────────────────────────────────────────────────────────

data class WearBandClamp(val startMm: Float, val lengthMm: Float)

/**
 * Clamps a wear spot's `[start, start+length)` span to the liner's own
 * `[0, linerLengthMm]` range for RENDERING only — the underlying [WearSpot] is
 * never mutated (contract: `docs/LinerWearAreas_Proposal.md` §3, "Clamp rendering
 * (not data) when a spot extends past the liner end").
 */
fun clampWearBandToLiner(spotStartMm: Float, spotLengthMm: Float, linerLengthMm: Float): WearBandClamp {
    val lenClamp = linerLengthMm.coerceAtLeast(0f)
    val start = spotStartMm.coerceIn(0f, lenClamp)
    val end = (spotStartMm + spotLengthMm).coerceIn(0f, lenClamp)
    return WearBandClamp(start, (end - start).coerceAtLeast(0f))
}

// ──────────────────────────────────────────────────────────────────────────────
// Vertical page layout — shrink the main profile to make room for N strips
// ──────────────────────────────────────────────────────────────────────────────

const val WEAR_STRIP_HEIGHT_PT = 108f
const val WEAR_STRIP_GAP_PT = 14f
const val WEAR_STRIP_TOP_GAP_PT = 18f
const val WEAR_MIN_PROFILE_HEIGHT_PT = 70f

data class WearVerticalLayout(
    val profileTop: Float,
    val profileBottom: Float,
    val stripTops: List<Float>,
    val stripBottoms: List<Float>,
)

/**
 * Splits the vertical band `[areaTop, areaBottom]` into a (possibly shrunk)
 * main-profile region followed by [stripCount] evenly-sized detail strips,
 * stacked with [stripGapPt] between them, reserving [reservedBottomPt] of
 * untouched space at the very bottom (used by the caller for the overflow note —
 * zero when there is no overflow).
 *
 * The main profile never shrinks below [minProfileHeightPt]; if the preferred
 * strip height would violate that floor, every strip shrinks together instead.
 * By construction the last strip's bottom always equals `areaBottom -
 * reservedBottomPt` exactly, so nothing downstream needs its own bounds check to
 * stay inside `[areaTop, areaBottom]`.
 */
fun computeWearVerticalLayout(
    areaTop: Float,
    areaBottom: Float,
    stripCount: Int,
    reservedBottomPt: Float = 0f,
    minProfileHeightPt: Float = WEAR_MIN_PROFILE_HEIGHT_PT,
    preferredStripHeightPt: Float = WEAR_STRIP_HEIGHT_PT,
    stripGapPt: Float = WEAR_STRIP_GAP_PT,
    profileToStripsGapPt: Float = WEAR_STRIP_TOP_GAP_PT,
): WearVerticalLayout {
    if (stripCount <= 0) return WearVerticalLayout(areaTop, areaBottom, emptyList(), emptyList())

    val usableBottom = areaBottom - reservedBottomPt
    val totalH = (usableBottom - areaTop).coerceAtLeast(0f)
    val gapsTotal = profileToStripsGapPt + (stripCount - 1).coerceAtLeast(0) * stripGapPt
    val desiredStripsH = stripCount * preferredStripHeightPt + gapsTotal
    val maxStripsH = (totalH - minProfileHeightPt).coerceAtLeast(0f)
    val stripsH = desiredStripsH.coerceAtMost(maxStripsH)
    val perStripH = ((stripsH - gapsTotal) / stripCount).coerceAtLeast(0f)

    val profileBottom = usableBottom - stripsH
    val stripsTop = profileBottom + profileToStripsGapPt
    val tops = (0 until stripCount).map { i -> stripsTop + i * (perStripH + stripGapPt) }
    val bottoms = tops.map { it + perStripH }
    return WearVerticalLayout(areaTop, profileBottom, tops, bottoms)
}

// ──────────────────────────────────────────────────────────────────────────────
// Strips-only vertical layout — no main profile at all (WearPdfMode.STRIPS_ONLY)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Per-strip height cap used ONLY in [WearPdfMode.STRIPS_ONLY] mode (see
 * [computeWearStripsOnlyVerticalLayout]) — 2.0× the combined-page preferred strip height
 * ([WEAR_STRIP_HEIGHT_PT], 108pt), i.e. **216pt**. Chosen per Chris's 2026-07-18 spec ("~1.8-2x
 * the old strip height") at the top of that range: with no profile competing for space, a
 * strips-only page (3+ wear liners, but still capped at [WEAR_STRIP_MAX_PER_PAGE] shown) has
 * roughly 3-4x the old strip band's worth of vertical room per strip, and letting one strip claim
 * all of it would look absurd — a cylinder dwarfing its own title/rail. 216pt keeps each strip
 * proportionate to the page while still giving noticeably more room for the cylinder, rail
 * spacing, and label breathing room than the combined page's 108pt band.
 */
const val WEAR_STRIP_MAX_HEIGHT_STRIPS_ONLY_PT = 216f

/** Result of [computeWearStripsOnlyVerticalLayout]: strip bands only, no profile region. */
data class WearStripsOnlyVerticalLayout(val stripTops: List<Float>, val stripBottoms: List<Float>)

/**
 * Strips-only sibling of [computeWearVerticalLayout] for [WearPdfMode.STRIPS_ONLY] (2026-07-18
 * spec, `docs/LinerWearAreas_BuildLog_2026-07-18.md`): the wear PDF drops the main shaft profile
 * and OAL dimension line entirely in this mode (shop practice — strips OR profile, never both
 * stacked), so [stripCount] strips get the *entire* freed vertical band `[areaTop, areaBottom]`
 * (minus [reservedBottomPt] for the overflow note) instead of whatever the profile left over.
 *
 * Splitting that band evenly among [stripCount] strips would let a strip on a mostly-empty page
 * balloon far past any strip drawn today. [maxStripHeightPt] (default
 * [WEAR_STRIP_MAX_HEIGHT_STRIPS_ONLY_PT]) caps each strip's height; space freed by that cap is
 * redistributed as EXTRA inter-strip gap (and, when there is only one strip and therefore no gap
 * to grow, as extra top gap) so the strip band as a whole still spans the full available area
 * edge-to-edge — the same "nothing wasted, nothing overflows" guarantee
 * [computeWearVerticalLayout] documents, just with the freed space going to spacing instead of
 * profile height.
 *
 * Guarantees, for any input including pathological ones (more strips than the area can hold at
 * any positive height, or [reservedBottomPt] exceeding the area): every returned top/bottom stays
 * within `[areaTop, areaBottom - reservedBottomPt]` (clamped so that lower bound never exceeds
 * the upper one) and strips are ordered top-to-bottom, non-decreasing — degenerate cases favor
 * staying in bounds over the edge-to-edge/per-cap guarantees above, matching
 * [computeWearVerticalLayout]'s own pathological-input behavior.
 */
fun computeWearStripsOnlyVerticalLayout(
    areaTop: Float,
    areaBottom: Float,
    stripCount: Int,
    reservedBottomPt: Float = 0f,
    maxStripHeightPt: Float = WEAR_STRIP_MAX_HEIGHT_STRIPS_ONLY_PT,
    stripGapPt: Float = WEAR_STRIP_GAP_PT,
    topGapPt: Float = WEAR_STRIP_TOP_GAP_PT,
): WearStripsOnlyVerticalLayout {
    if (stripCount <= 0) return WearStripsOnlyVerticalLayout(emptyList(), emptyList())

    // totalH is clamped >= 0 first (mirrors computeWearVerticalLayout), then usableBottom is
    // re-derived FROM it — never from the raw areaBottom - reservedBottomPt — so a
    // reservedBottomPt larger than the whole area can never push the upper clamp bound below
    // areaTop (which would make the final coerceIn's bounds invalid and throw).
    val totalH = (areaBottom - reservedBottomPt - areaTop).coerceAtLeast(0f)
    val usableBottom = areaTop + totalH
    val availableForStrips = (totalH - topGapPt).coerceAtLeast(0f)
    val minGapsTotal = (stripCount - 1).coerceAtLeast(0) * stripGapPt

    val perStripH = ((availableForStrips - minGapsTotal) / stripCount).coerceIn(0f, maxStripHeightPt)

    // Height freed by the cap becomes extra spacing so the group still spans edge-to-edge.
    val extraTotal = (availableForStrips - stripCount * perStripH - minGapsTotal).coerceAtLeast(0f)
    val extraGapPerSlot = if (stripCount > 1) extraTotal / (stripCount - 1) else 0f
    val effectiveGapPt = stripGapPt + extraGapPerSlot
    // stripCount == 1 has no inter-strip gap to grow, so its leftover (from a lone strip pinned
    // at the cap) goes to the top gap instead — still spans edge-to-edge, still respects the cap.
    val effectiveTopGapPt = topGapPt + (if (stripCount == 1) extraTotal else 0f)

    val firstTop = areaTop + effectiveTopGapPt
    val rawTops = (0 until stripCount).map { i -> firstTop + i * (perStripH + effectiveGapPt) }
    val rawBottoms = rawTops.map { it + perStripH }

    // Final safety clamp for pathological inputs (more strips than the area can fit even at
    // zero height) — coerceIn is monotonic, so top<=bottom ordering survives the clamp.
    val tops = rawTops.map { it.coerceIn(areaTop, usableBottom) }
    val bottoms = rawBottoms.map { it.coerceIn(areaTop, usableBottom) }
    return WearStripsOnlyVerticalLayout(tops, bottoms)
}

// ──────────────────────────────────────────────────────────────────────────────
// Horizontal layout for one strip — centered liner + two neighbor stubs
// ──────────────────────────────────────────────────────────────────────────────

const val WEAR_STRIP_STUB_WIDTH_PT = 34f
const val WEAR_STRIP_MIN_PT_PER_MM = 0.15f
const val WEAR_STRIP_MAX_PT_PER_MM = 3.0f

data class WearStripHorizontalLayout(
    val stubWidthPt: Float,
    val linerLeftPt: Float,
    val linerRightPt: Float,
    val ptPerMm: Float,
)

/**
 * Lays out one detail strip horizontally: a fixed-width neighbor stub on each
 * side, the liner itself scaled to fill the remaining width (capped so a very
 * short liner doesn't blow up the scale, floored so a very long one doesn't
 * vanish), and the whole group centered in `[stripLeftPt, stripRightPt]` when the
 * cap/floor leaves slack.
 */
fun computeWearStripHorizontalLayout(
    stripLeftPt: Float,
    stripRightPt: Float,
    linerLengthMm: Float,
    stubWidthPt: Float = WEAR_STRIP_STUB_WIDTH_PT,
    minPtPerMm: Float = WEAR_STRIP_MIN_PT_PER_MM,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
): WearStripHorizontalLayout {
    val innerWidth = (stripRightPt - stripLeftPt - 2f * stubWidthPt).coerceAtLeast(1f)
    val lenMm = linerLengthMm.coerceAtLeast(1f)
    val ptPerMm = (innerWidth / lenMm).coerceIn(minPtPerMm, maxPtPerMm)
    val linerWidthPt = lenMm * ptPerMm
    val usedWidth = linerWidthPt + 2f * stubWidthPt
    val leftPad = (((stripRightPt - stripLeftPt) - usedWidth) / 2f).coerceAtLeast(0f)
    val linerLeftPt = stripLeftPt + leftPad + stubWidthPt
    val linerRightPt = linerLeftPt + linerWidthPt
    return WearStripHorizontalLayout(stubWidthPt, linerLeftPt, linerRightPt, ptPerMm)
}

// ──────────────────────────────────────────────────────────────────────────────
// Inner strip layout — title row, liner cylinder, chained dimension rail
// ──────────────────────────────────────────────────────────────────────────────

const val WEAR_STRIP_ROW_HEIGHT_PT = 13f

/**
 * Extra vertical gap reserved between the title's own text line and the top of the
 * liner cylinder (2026-07-18 SVG review, defect 2). Before this existed, the
 * cylinder consumed the strip's whole remaining band right up against the title
 * with no headroom at all — a label pinned to the cylinder's top edge (the min-Ø
 * reading, see `WearPdfComposer.drawWearDetailStrip`) then landed only a few
 * points below the title text, reading as crowded/overlapping.
 */
const val WEAR_STRIP_LABEL_HEADROOM_PT = 11f

/**
 * Stacked label positions reserved above a strip's chained dimension rail
 * (2026-07-18 dimension-rail rework — see "Wear Detail Strips" in
 * `docs/RunoutSheet.md`) for the crowding fallback in [layoutWearStripRail]: row 0
 * is the base label position directly above the rail line, and this many rows
 * total are budgeted regardless of how many wear spots the liner has — unlike the
 * old per-spot row budget this replaces, the chain is always ONE rail line, so the
 * reserved height no longer scales with spot count. A chain crowded enough to need
 * more rows than this clamps every excess label to the last available row (see
 * `WearPdfComposer.drawWearStripRail`) rather than overflow the strip.
 */
const val WEAR_RAIL_MAX_LABEL_ROWS = 2

data class WearStripInnerLayout(
    val cylTop: Float,
    val cylBottom: Float,
    /** Y coordinate of the strip's single chained dimension-rail line. */
    val railY: Float,
    /** How many of [WEAR_RAIL_MAX_LABEL_ROWS] stacked label rows actually fit above [railY]
     *  without crossing [cylBottom] — 0 in a pathologically short strip (the rail line still
     *  draws, but no label is placed on it). */
    val railLabelRows: Int,
)

/**
 * Splits one strip's vertical band `[stripTop, stripBottom]` into a liner-cylinder
 * region and the single chained dimension rail below it — the strip-local
 * analogue of [computeWearVerticalLayout].
 *
 * [titleHeightPt] is the space the title text line itself consumes (its own
 * height from `stripTop` down to its baseline); [labelHeadroomPt] is then an
 * EXTRA, explicit gap reserved below that, before the cylinder starts, so a label
 * drawn just above the cylinder never ends up crowding the title (see
 * [WEAR_STRIP_LABEL_HEADROOM_PT]'s KDoc for the defect this fixes).
 *
 * The rail's own vertical budget is now a FIXED [maxLabelRows] × [rowHeightPt] —
 * not proportional to how many wear spots the liner has, since the rail is always
 * one chained line no matter how many spans it's divided into (2026-07-18
 * dimension-rail rework; the old contract multiplied the row budget by spot
 * count). Guarantees `cylTop <= cylBottom <= railY <= stripBottom` for ANY input,
 * including pathological ones (e.g. a very large-diameter liner on a very short
 * strip, where the preferred cylinder + rail sizes don't fit together): the
 * cylinder shrinks first, and once it hits zero height, [railLabelRows] drops
 * toward zero (labels omitted, not drawn) rather than letting anything overflow
 * the strip. This is what keeps `WearPdfComposer.drawWearDetailStrip`'s Canvas
 * calls inside the content rect without needing per-call bounds checks there.
 */
fun computeWearStripInnerLayout(
    stripTop: Float,
    stripBottom: Float,
    titleHeightPt: Float,
    rowHeightPt: Float = WEAR_STRIP_ROW_HEIGHT_PT,
    labelHeadroomPt: Float = WEAR_STRIP_LABEL_HEADROOM_PT,
    maxLabelRows: Int = WEAR_RAIL_MAX_LABEL_ROWS,
): WearStripInnerLayout {
    val cylTop = (stripTop + titleHeightPt + labelHeadroomPt)
        .coerceIn(stripTop, stripBottom.coerceAtLeast(stripTop))
    val available = (stripBottom - cylTop).coerceAtLeast(0f)
    val railBudgetH = maxLabelRows.coerceAtLeast(0) * rowHeightPt
    val cylH = (available - railBudgetH).coerceIn(0f, available)
    val cylBottom = cylTop + cylH
    val remainingForRail = (stripBottom - cylBottom).coerceAtLeast(0f)
    val railLabelRows = (remainingForRail / rowHeightPt).toInt().coerceIn(0, maxLabelRows)
    val railY = cylBottom + railLabelRows * rowHeightPt
    return WearStripInnerLayout(cylTop, cylBottom, railY, railLabelRows)
}

// ──────────────────────────────────────────────────────────────────────────────
// Dimension rail — chained spans below the liner cylinder (2026-07-18 rework)
// ──────────────────────────────────────────────────────────────────────────────
//
// Replaces the old per-spot "AFT edge → band start" / "band start → band end" text rows
// with one standard chained dimension rail: liner AFT edge → first band start, each band's
// own length, the gap between consecutive bands, and the trailing remainder to the liner FWD
// edge — the same witness-line/arrowed-span/centered-label convention the main schematic PDF
// uses (`pdf/render/PdfDimensionRenderer.kt`). That renderer isn't reused directly: it's built
// around the schematic's multi-tier DATUM/LOCAL rail stacking (spans that overlap in x get
// assigned different stacked rails) and draws its rails ABOVE the shaft outline. A wear
// strip's rail is a single flat chain of never-overlapping spans BELOW the liner cylinder —
// mismatched enough on both axes (tiering model, draw direction) that reusing the class would
// mean either bending its API to a shape it wasn't designed for or duplicating most of its
// logic anyway. So the minimal shared idea — center a label on its span when it fits, let it
// overhang centered when it doesn't, flip arrows outward when cramped, and bump a colliding
// label to a fallback row — is replicated here as small pure functions instead.

private const val WEAR_RAIL_SPAN_EPS_MM = 1e-3f

/** One span in a wear-strip's chained dimension rail — liner-local mm, aft edge = 0. */
data class WearRailSpan(val startMm: Float, val endMm: Float, val label: String)

/**
 * Builds the ordered chain of dimension spans for one liner's detail-strip rail: liner AFT
 * edge → first band start, each band's own length, the gap between consecutive bands, and the
 * trailing remainder to the liner FWD edge. [clampedBands] must already be render-clamped
 * ([clampWearBandToLiner]) and sorted aft→fwd — this function doesn't re-clamp or re-sort, it
 * only walks the chain.
 *
 * Zero-length spans are OMITTED rather than drawn as degenerate zero-width dimension lines: a
 * band starting exactly at the liner AFT edge produces no leading-gap span, two back-to-back
 * bands with no gap produce no gap span between them, and a band ending exactly at the liner
 * FWD edge produces no trailing span. This never leaves a hole in the chain's coverage — the
 * running `cursor` always advances from `0` to [linerLengthMm] exactly regardless of which
 * spans get omitted, since an omitted span had zero mm to contribute in the first place (so
 * summing every returned span's length always equals [linerLengthMm] exactly).
 *
 * A band whose (clamped) start is at or before the current cursor — two wear spots recorded
 * with overlapping spans, which is legal since only the liner-bounds check is enforced at
 * entry, not inter-spot overlap — has its effective start pulled forward to the cursor so the
 * chain never runs backward or double-counts the overlapping mm; the visible effect is that
 * the overlap reads as belonging to whichever spot comes first in the chain rather than being
 * drawn twice.
 */
fun buildWearStripRailSpans(
    linerLengthMm: Float,
    clampedBands: List<WearBandClamp>,
    unit: UnitSystem,
): List<WearRailSpan> {
    val spans = mutableListOf<WearRailSpan>()
    var cursor = 0f
    clampedBands.forEach { band ->
        if (band.lengthMm <= WEAR_RAIL_SPAN_EPS_MM) return@forEach
        val effStart = maxOf(band.startMm, cursor)
        val gapLen = effStart - cursor
        if (gapLen > WEAR_RAIL_SPAN_EPS_MM) {
            spans += WearRailSpan(cursor, effStart, formatLenDim(gapLen.toDouble(), unit))
        }
        val bandEnd = maxOf(band.startMm + band.lengthMm, effStart)
        if (bandEnd - effStart > WEAR_RAIL_SPAN_EPS_MM) {
            spans += WearRailSpan(effStart, bandEnd, formatLenDim((bandEnd - effStart).toDouble(), unit))
        }
        cursor = maxOf(cursor, bandEnd)
    }
    val trailing = linerLengthMm - cursor
    if (trailing > WEAR_RAIL_SPAN_EPS_MM) {
        spans += WearRailSpan(cursor, linerLengthMm, formatLenDim(trailing.toDouble(), unit))
    }
    return spans
}

/**
 * One dimension span from a wear-strip's chained rail, resolved to on-page geometry and ready
 * to draw: the span's own x-range in points, its label, the label's horizontal center, which
 * stacked row the label landed on (`0` = directly above the rail line, see
 * [WEAR_RAIL_MAX_LABEL_ROWS]), and whether its arrowheads point inward (room for both beside
 * the label) or outward (cramped).
 */
data class WearRailSpanLayout(
    val x0Pt: Float,
    val x1Pt: Float,
    val label: String,
    val labelCxPt: Float,
    val labelRow: Int,
    val arrowInward: Boolean,
)

/**
 * Resolves [spans] (liner-local mm, from [buildWearStripRailSpans]) to on-page geometry for one
 * strip's chained dimension rail:
 * - A label is centered on its own span when it fits with [textPadPt] to spare on both sides;
 *   otherwise it's centered on the span's midpoint and allowed to overhang — a label is never
 *   dropped, matching `PdfDimensionRenderer`'s "always draw the label somewhere" rule.
 * - Arrowheads point inward when there's room for both beside the label, outward when cramped
 *   (same test as `PdfDimensionRenderer.canFitInwardArrows`).
 * - Labels are assigned to the lowest-numbered row (`0` first) whose already-placed labels
 *   don't overlap it horizontally — the crowding fallback for short bands/gaps whose label is
 *   wider than the span itself, where row 0 alone would overlap a neighboring label
 *   (`PdfDimensionRenderer`'s per-rail label-bump collision avoidance, replicated for this
 *   rail's single chain).
 *
 * [labelWidthPt] measures a label's on-page width (`Paint.measureText` in the composer) — kept
 * as a caller-supplied function rather than an Android import so this stays pure/JVM-testable
 * like the rest of this file.
 */
fun layoutWearStripRail(
    spans: List<WearRailSpan>,
    xAtStripMm: (Float) -> Float,
    labelWidthPt: (String) -> Float,
    textPadPt: Float = 4f,
    arrowSizePt: Float = 4f,
    minLabelGapPt: Float = 4f,
): List<WearRailSpanLayout> {
    val rowIntervals = mutableListOf<MutableList<Pair<Float, Float>>>()
    return spans.map { span ->
        val p0 = xAtStripMm(span.startMm)
        val p1 = xAtStripMm(span.endMm)
        val xa = minOf(p0, p1)
        val xb = maxOf(p0, p1)
        val w = labelWidthPt(span.label)
        val half = w * 0.5f
        val mid = (xa + xb) * 0.5f
        val leftBoundCenter = xa + textPadPt + half
        val rightBoundCenter = xb - textPadPt - half
        val cx = if (leftBoundCenter > rightBoundCenter) {
            mid
        } else {
            mid.coerceIn(minOf(leftBoundCenter, rightBoundCenter), maxOf(leftBoundCenter, rightBoundCenter))
        }
        val leftRoom = (cx - half - textPadPt) - xa
        val rightRoom = xb - (cx + half + textPadPt)
        val inward = leftRoom >= arrowSizePt && rightRoom >= arrowSizePt

        val left = cx - half
        val right = cx + half
        var row = 0
        while (true) {
            if (row >= rowIntervals.size) rowIntervals.add(mutableListOf())
            val free = rowIntervals[row].none { (oL, oR) -> left < oR + minLabelGapPt && right + minLabelGapPt > oL }
            if (free) {
                rowIntervals[row].add(left to right)
                break
            }
            row++
        }
        WearRailSpanLayout(xa, xb, span.label, cx, row, inward)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Strip radii — one common scale factor for the liner + both neighbor stubs
// ──────────────────────────────────────────────────────────────────────────────

/** Scaled on-page radii (points) for one strip's liner cylinder and its two neighbor stubs. */
data class WearStripRadii(val linerRPt: Float, val aftRPt: Float, val fwdRPt: Float)

/**
 * Converts the liner's OD and its two neighbor diameters (mm) to strip-local radii
 * (points, at [ptPerMm]), then — if the LARGEST of the three would exceed
 * [maxRadiusPt] (the strip's vertical budget, typically half of
 * [computeWearStripInnerLayout]'s `cylBottom - cylTop`) — scales ALL THREE by the
 * SAME factor (`maxRadiusPt / largestRawRadius`) rather than capping each
 * independently (2026-07-18 SVG review, defect 1).
 *
 * Independent capping (`min(raw, maxRadiusPt)` applied to each diameter on its
 * own) is wrong here: whenever the liner AND a neighbor both exceed the budget —
 * the common case for a large-bore liner fitted over a much smaller shaft body —
 * both get flattened to the identical capped radius, erasing the visible diameter
 * step between them (e.g. an 8.00in liner OD over a 7.00in shaft would render
 * with the SAME radius as the shaft it sits on, looking like a constant-diameter
 * cylinder). Scaling every radius in the strip by one common factor instead keeps
 * their ratios intact — the whole strip "zooms out" together, so the step stays
 * proportionally visible even though every radius shrank to fit.
 *
 * When the largest raw radius already fits within [maxRadiusPt], the factor is 1
 * (a no-op) — strips that never needed capping render exactly as they did before
 * this function existed. Radii are floored at zero only (no separate
 * minimum-visibility floor exists elsewhere in this file to preserve); a
 * zero/negative [maxRadiusPt] collapses every radius to zero rather than
 * throwing, matching [computeWearStripInnerLayout]'s pathological-input
 * guarantees.
 */
fun computeWearStripRadii(
    linerOdMm: Float,
    aftDiaMm: Float,
    fwdDiaMm: Float,
    ptPerMm: Float,
    maxRadiusPt: Float,
): WearStripRadii {
    val cap = maxRadiusPt.coerceAtLeast(0f)
    val rawLiner = (linerOdMm * 0.5f) * ptPerMm
    val rawAft = (aftDiaMm * 0.5f) * ptPerMm
    val rawFwd = (fwdDiaMm * 0.5f) * ptPerMm
    val maxRaw = maxOf(rawLiner, rawAft, rawFwd, 0f)
    val scale = if (maxRaw > cap && maxRaw > 0f) cap / maxRaw else 1f
    fun scaled(rawR: Float) = (rawR * scale).coerceAtLeast(0f)
    return WearStripRadii(scaled(rawLiner), scaled(rawAft), scaled(rawFwd))
}

// ──────────────────────────────────────────────────────────────────────────────
// Neighbor lookup — diameter of whatever abuts the liner, for the break-out stubs
// ──────────────────────────────────────────────────────────────────────────────

private const val NEIGHBOR_EPS_MM = 0.5f

/**
 * Diameter of the resolved component ending at (approximately) [linerAftMm], if
 * any — the aft neighbor whose stub is drawn on the left of the detail strip.
 * Callers must pass a spec whose `bodies` are already resolved (see
 * `ShaftSpec.withResolvedBodies`) so auto-fill body gaps are found here too.
 */
fun neighborDiaMmAtAft(spec: ShaftSpec, linerAftMm: Float, epsMm: Float = NEIGHBOR_EPS_MM): Float? {
    spec.bodies.forEach { if (abs((it.startFromAftMm + it.lengthMm) - linerAftMm) <= epsMm) return it.diaMm }
    spec.tapers.forEach { if (abs((it.startFromAftMm + it.lengthMm) - linerAftMm) <= epsMm) return it.endDiaMm }
    spec.threads.forEach { if (abs((it.startFromAftMm + it.lengthMm) - linerAftMm) <= epsMm) return it.majorDiaMm }
    return null
}

/** Diameter of the resolved component starting at (approximately) [linerFwdMm], if any. */
fun neighborDiaMmAtFwd(spec: ShaftSpec, linerFwdMm: Float, epsMm: Float = NEIGHBOR_EPS_MM): Float? {
    spec.bodies.forEach { if (abs(it.startFromAftMm - linerFwdMm) <= epsMm) return it.diaMm }
    spec.tapers.forEach { if (abs(it.startFromAftMm - linerFwdMm) <= epsMm) return it.startDiaMm }
    spec.threads.forEach { if (abs(it.startFromAftMm - linerFwdMm) <= epsMm) return it.majorDiaMm }
    return null
}

// ──────────────────────────────────────────────────────────────────────────────
// Labels
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Builds the strip's anchor-from-SET callout — the digital equivalent of the shop
 * sketch's "110 FROM CPLG S.E.T." line — by reusing the exact same math the main
 * schematic PDF uses for liner dimensions (`mapToLinerDimsForPdf` +
 * `buildLinerSpans`), so the number printed here is always identical to the one
 * on the schematic page. Returns "" if [liner] isn't found (should not happen —
 * defensive only).
 */
fun buildLinerAnchorLabel(spec: ShaftSpec, liner: Liner, sets: SetPositions, unit: UnitSystem): String {
    val dim = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO).firstOrNull { it.id == liner.id } ?: return ""
    val datum = buildLinerSpans(listOf(dim), sets, unit, PdfTieringMode.AUTO)
        .firstOrNull { it.kind == SpanKind.DATUM } ?: return ""
    val setWord = if (dim.anchor == LinerAnchor.AFT_SET) "AFT S.E.T." else "FWD S.E.T."
    return "${datum.labelTop} FROM $setWord"
}

/** Min-Ø reading label, or `null` when unrecorded (`minDiaMm == 0`) — never printed in that case. */
fun formatMinDiaLabelOrNull(minDiaMm: Float, unit: UnitSystem): String? =
    if (minDiaMm > 0f) "⌀${formatDiaWithUnit(minDiaMm.toDouble(), unit)}" else null
