// app/src/main/java/com/android/shaftschematic/pdf/WearStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.DimensionRailLayout
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

/** One liner and the wear spots recorded against it (possibly empty — every liner
 *  gets a detail strip whether or not wear was recorded, per shop procedure). */
data class WearLinerGroup(val liner: Liner, val spots: List<WearSpot>)

/**
 * Builds one group per drawable liner (positive length AND OD — a degenerate liner
 * would only leave a blank grid cell), attaching whatever wear spots [wearRecord]
 * holds against it — **including liners with zero spots** (every liner
 * appears on the wear sheet regardless of recorded wear; that's the shop's normal
 * operating procedure, and the blank write-in template needs the strips too).
 * Sorted aft → fwd by the liner's physical start position, matching the proposal's
 * "order aft→fwd" rule (§10.4).
 *
 * [stripComponentIds] is the document's per-job strip election
 * ([WearRecord.stripComponentIds]): `null` (the default) means the default election —
 * every drawable liner, exactly the historical sheet — and a non-null list is the
 * machinist's authored set of **resolved component ids**. An empty list therefore yields no
 * strips at all. Ids that name a taper or a body are accepted and simply produce no group
 * here (those components have no strip yet); ids that resolve to nothing are skipped the
 * same way — orphan handling at the render layer, never at decode.
 *
 * Spots whose `linerId` doesn't match any liner in [liners] are silently dropped
 * here. The authoritative orphan-drop already happens at decode time
 * (`ShaftDocCodec`); this is a defensive second filter so a stale in-memory
 * [WearRecord] (e.g. liner deleted after load, before next save) can never crash
 * PDF layout.
 */
fun collectWearLinerGroups(
    liners: List<Liner>,
    wearRecord: WearRecord,
    stripComponentIds: List<String>? = null,
): List<WearLinerGroup> {
    if (liners.isEmpty()) return emptyList()
    val byLiner = wearRecord.spots.groupBy { it.linerId }
    val elected = stripComponentIds?.toSet()
    return liners
        .filter { it.lengthMm > 0f && it.odMm > 0f }
        .filter { elected == null || it.id in elected }
        .map { ln -> WearLinerGroup(ln, byLiner[ln.id].orEmpty()) }
        .sortedBy { it.liner.startFromAftMm }
}

/**
 * The default strip election — every drawable liner, aft → fwd. `null`
 * ([WearRecord.stripComponentIds] unset) renders exactly this set; the options sheet
 * materializes it into an explicit list on the first component toggle, so a later liner add
 * can't silently change an authored sheet.
 */
fun defaultWearStripComponentIds(liners: List<Liner>): List<String> =
    liners.filter { it.lengthMm > 0f && it.odMm > 0f }
        .sortedBy { it.startFromAftMm }
        .map { it.id }

/** Max detail strips per page (proposal §10.4: "auto ... max 3 strips/page with overflow page"). */
const val WEAR_STRIP_MAX_PER_PAGE = 3

/**
 * Minimum number of liners (i.e. `collectWearLinerGroups(...).size` — every drawable liner,
 * with or without recorded wear) that switches the wear PDF from single-column stacked
 * strips to the **2-column grid** (by design): with two or more strips the shaft
 * profile must stay on top, and stacking them full-width would push it out — so from two strips up
 * they lay out side by side (two per row), keeping the profile above and its body/taper pit "X"s
 * visible. Below this (one liner) the single full-width strip below the profile is unchanged; see
 * [WearPdfMode].
 */
const val WEAR_STRIP_GRID_MIN_LINERS = 2

/** Which of the wear PDF's rendering modes applies — see [determineWearPdfMode]. */
enum class WearPdfMode {
    /** The shaft has no liners at all: profile-only hand-marking form (still prints any pits). */
    PROFILE_FORM,
    /** 1 liner: shaft profile + bands, with one full-width detail strip below. */
    COMBINED,
    /**
     * 2+ liners: shaft profile on top (with body/taper/liner pits + liner bands) and the
     * detail strips laid out in a **2-column grid** below — two side by side, the third on the
     * next row, so the strips occupy only ~2 rows and the profile is always kept.
     */
    GRID,
}

/**
 * Resolves the wear PDF's rendering mode from how many detail strips the sheet carries
 * ([collectWearStripWindows]'s result size — one per elected component, except that an elected
 * taper joins its nearest elected liner in a combined window; under the default election that is
 * EVERY drawable liner, with or without recorded wear). Pure rule so `WearPdfComposer` never has
 * to re-derive the threshold inline:
 * `0` → [WearPdfMode.PROFILE_FORM], `1` → [WearPdfMode.COMBINED],
 * `[WEAR_STRIP_GRID_MIN_LINERS]` or more → [WearPdfMode.GRID].
 */
fun determineWearPdfMode(wearLinerGroupCount: Int): WearPdfMode = when {
    wearLinerGroupCount <= 0 -> WearPdfMode.PROFILE_FORM
    wearLinerGroupCount < WEAR_STRIP_GRID_MIN_LINERS -> WearPdfMode.COMBINED
    else -> WearPdfMode.GRID
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

/**
 * Profile→strips gap on the BLANK write-in template (device feedback): the blank
 * header grows to [WEAR_HEADER_HEIGHT_BLANK_PT in WearPdfComposer] for handwriting room, and
 * most of that space is reclaimed here, between the shaft profile and the liner detail
 * strips, rather than from the strips themselves. Normal (printed) sheets keep
 * [WEAR_STRIP_TOP_GAP_PT].
 */
const val WEAR_STRIP_TOP_GAP_BLANK_PT = 8f

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
 *
 * [preferredProfileHeightPt] is the caller's content-derived target for the profile band (OAL
 * region + shaft diameter + names row, in `WearPdfComposer`): the band shrinks TOWARD it — never
 * below [minProfileHeightPt] — and the strips absorb whatever height that frees, so the drawn
 * liners get bigger instead of the band hoarding dead white (device feedback).
 * [maxStripHeightPt] caps how far one strip may grow on that surplus; any height past the cap
 * flows back to the profile band (whose shaft the composer slack-centers, so it degrades
 * gracefully). Both default to [Float.MAX_VALUE], so with no explicit caps the profile band
 * absorbs all leftover height.
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
    preferredProfileHeightPt: Float = Float.MAX_VALUE,
    maxStripHeightPt: Float = Float.MAX_VALUE,
): WearVerticalLayout {
    if (stripCount <= 0) return WearVerticalLayout(areaTop, areaBottom, emptyList(), emptyList())

    val usableBottom = areaBottom - reservedBottomPt
    val totalH = (usableBottom - areaTop).coerceAtLeast(0f)
    val gapsTotal = profileToStripsGapPt + (stripCount - 1).coerceAtLeast(0) * stripGapPt
    val desiredStripsH = stripCount * preferredStripHeightPt + gapsTotal
    val maxStripsH = (totalH - minProfileHeightPt).coerceAtLeast(0f)
    val stripsHPreferred = desiredStripsH.coerceAtMost(maxStripsH)
    // Profile-height preference (device feedback): the profile band shrinks toward
    // preferredProfileHeightPt (never below minProfileHeightPt) rather than absorbing all
    // leftover height, so it doesn't strand dead white between the shaft and the strips —
    // the strips absorb the surplus instead, for bigger liner drawings.
    val profileH = (totalH - stripsHPreferred)
        .coerceAtMost(preferredProfileHeightPt)
        .coerceAtLeast(minProfileHeightPt)
    // Per-strip cap: a lone COMBINED strip must not balloon; anything past the cap flows back
    // to the profile (whose shaft is slack-centered by the composer, so it degrades gracefully).
    val stripsHUncapped = (totalH - profileH).coerceAtLeast(0f)
    val perStripH = ((stripsHUncapped - gapsTotal) / stripCount)
        .coerceAtLeast(0f)
        .coerceAtMost(maxStripHeightPt)
    // Tiny-page guard: gaps alone must never exceed the band (keeps profileBottom >= areaTop).
    val stripsH = (perStripH * stripCount + gapsTotal).coerceAtMost(totalH)

    val profileBottom = usableBottom - stripsH
    val stripsTop = profileBottom + profileToStripsGapPt
    val tops = (0 until stripCount).map { i -> stripsTop + i * (perStripH + stripGapPt) }
    val bottoms = tops.map { it + perStripH }
    return WearVerticalLayout(areaTop, profileBottom, tops, bottoms)
}

// ──────────────────────────────────────────────────────────────────────────────
// 2-column grid layout — profile on top, strips side by side below (WearPdfMode.GRID)
// ──────────────────────────────────────────────────────────────────────────────

/** Columns in the wear-PDF detail-strip grid (by design: two side by side). */
const val WEAR_STRIP_GRID_COLUMNS = 2

/** Horizontal gutter between the two strip columns, points. */
const val WEAR_STRIP_COL_GAP_PT = 22f

/**
 * Max detail strips shown on a [WearPdfMode.GRID] page: [WEAR_STRIP_GRID_COLUMNS] columns × 2 rows
 * = 4, so the strips never grow past ~2 rows (by design — "only take 2 rows") and the shaft profile
 * always keeps the top of the page. Liners beyond this render as a "+N more" overflow note, same
 * as [WEAR_STRIP_MAX_PER_PAGE] does for the single-column path.
 */
const val WEAR_STRIP_GRID_MAX_PER_PAGE = WEAR_STRIP_GRID_COLUMNS * 2

/** One strip's on-page rectangle within the grid (points). */
data class WearStripCell(val top: Float, val bottom: Float, val left: Float, val right: Float)

/** Result of [computeWearStripGridLayout]: the (shrunk) profile band + one cell per strip. */
data class WearStripGridLayout(
    val profileTop: Float,
    val profileBottom: Float,
    val cells: List<WearStripCell>,
)

/**
 * Lays [stripCount] detail strips into a [columns]-wide grid below a (shrunk) shaft-profile band,
 * so the profile is always kept on top and the strips occupy only `ceil(stripCount / columns)`
 * rows (by design — see [WearPdfMode.GRID]). The vertical banding reuses
 * [computeWearVerticalLayout] with the ROW count (not the strip count), so the profile still never
 * shrinks below [minProfileHeightPt] and the "nothing wasted / nothing overflows" guarantee
 * carries over unchanged; each strip then takes its row's band and one column slot.
 * [preferredProfileHeightPt] and [maxStripHeightPt] are forwarded straight through (per ROW, like
 * the row height), so the grid shrinks its profile band toward the caller's content-derived target
 * and lets the rows absorb the surplus exactly as the single-column path does — see
 * [computeWearVerticalLayout]'s KDoc; with no explicit caps, the defaults make the profile
 * band absorb all leftover height.
 *
 * Columns are equal-width across `[contentLeft, contentRight]` with a [colGapPt] gutter. A row
 * that isn't full (e.g. the lone third strip of three) is **centered**: its strips keep the same
 * column width as a full row and sit centred in the content width, so a 2+1 layout reads as two
 * over one centred rather than one hugging the left margin.
 */
fun computeWearStripGridLayout(
    areaTop: Float,
    areaBottom: Float,
    contentLeft: Float,
    contentRight: Float,
    stripCount: Int,
    columns: Int = WEAR_STRIP_GRID_COLUMNS,
    reservedBottomPt: Float = 0f,
    minProfileHeightPt: Float = WEAR_MIN_PROFILE_HEIGHT_PT,
    preferredRowHeightPt: Float = WEAR_STRIP_HEIGHT_PT,
    rowGapPt: Float = WEAR_STRIP_GAP_PT,
    colGapPt: Float = WEAR_STRIP_COL_GAP_PT,
    profileToStripsGapPt: Float = WEAR_STRIP_TOP_GAP_PT,
    preferredProfileHeightPt: Float = Float.MAX_VALUE,
    maxStripHeightPt: Float = Float.MAX_VALUE,
): WearStripGridLayout {
    if (stripCount <= 0) return WearStripGridLayout(areaTop, areaBottom, emptyList())
    val cols = columns.coerceAtLeast(1)
    val rowCount = (stripCount + cols - 1) / cols

    val v = computeWearVerticalLayout(
        areaTop, areaBottom, rowCount,
        reservedBottomPt = reservedBottomPt,
        minProfileHeightPt = minProfileHeightPt,
        preferredStripHeightPt = preferredRowHeightPt,
        stripGapPt = rowGapPt,
        profileToStripsGapPt = profileToStripsGapPt,
        preferredProfileHeightPt = preferredProfileHeightPt,
        maxStripHeightPt = maxStripHeightPt,
    )

    val contentW = (contentRight - contentLeft).coerceAtLeast(1f)
    val colW = ((contentW - (cols - 1) * colGapPt) / cols).coerceAtLeast(1f)

    val cells = (0 until stripCount).map { i ->
        val row = i / cols
        val colIdx = i % cols
        val inThisRow = (stripCount - row * cols).coerceAtMost(cols)
        val rowContentW = inThisRow * colW + (inThisRow - 1) * colGapPt
        val rowLeft = contentLeft + (contentW - rowContentW) / 2f
        val left = rowLeft + colIdx * (colW + colGapPt)
        WearStripCell(top = v.stripTops[row], bottom = v.stripBottoms[row], left = left, right = left + colW)
    }
    return WearStripGridLayout(v.profileTop, v.profileBottom, cells)
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
 * ONE mm→pt scale for every detail strip on a wear sheet, so relative component lengths read
 * true across the page: a 22" liner draws half the width of a 44" one (on-device report — each
 * strip scaled to fill its own cell, which made a short liner look as long as its siblings).
 *
 * The scale is the largest that still fits every strip inside its own cell —
 * `min(innerWidth_i / lengthMm_i)` — capped at [maxPtPerMm] so a page of only short components
 * doesn't blow up. It is deliberately **NOT** floored to [WEAR_STRIP_MIN_PT_PER_MM]: flooring a
 * shared scale would overflow the longest strip's cell. When one long component forces a small
 * scale, everything shrinks together — proportion wins.
 *
 * [innerWidthsPt] is each strip's usable width (its cell minus both neighbor stubs); a list
 * shorter than [lengthsMm] reuses its last entry (grid cells are equal-width by construction).
 * An empty page returns [maxPtPerMm] — there is nothing to fit.
 */
fun sharedWearStripPtPerMm(
    lengthsMm: List<Float>,
    innerWidthsPt: List<Float>,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
): Float {
    if (lengthsMm.isEmpty()) return maxPtPerMm
    var scale = maxPtPerMm
    lengthsMm.forEachIndexed { i, lenMm ->
        val inner = (innerWidthsPt.getOrNull(i) ?: innerWidthsPt.lastOrNull() ?: 1f).coerceAtLeast(1f)
        val len = lenMm.coerceAtLeast(1f)
        val fit = inner / len
        if (fit < scale) scale = fit
    }
    return scale
}

/**
 * Lays out one detail strip horizontally: a fixed-width neighbor stub on each
 * side, the liner itself scaled to fill the remaining width (capped so a very
 * short liner doesn't blow up the scale, floored so a very long one doesn't
 * vanish), and the whole group centered in `[stripLeftPt, stripRightPt]` when the
 * cap/floor leaves slack.
 *
 * [ptPerMmOverride] (the wear sheet's shared scale, [sharedWearStripPtPerMm]) replaces that
 * per-strip fit: the liner draws `lengthMm × override` wide and the group centers in the cell,
 * so a shorter component keeps its true proportion instead of filling the cell. `null` — the
 * default, and what the undercut strips pass — keeps the per-strip fit exactly as it was.
 */
fun computeWearStripHorizontalLayout(
    stripLeftPt: Float,
    stripRightPt: Float,
    linerLengthMm: Float,
    stubWidthPt: Float = WEAR_STRIP_STUB_WIDTH_PT,
    minPtPerMm: Float = WEAR_STRIP_MIN_PT_PER_MM,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
    ptPerMmOverride: Float? = null,
): WearStripHorizontalLayout {
    val innerWidth = (stripRightPt - stripLeftPt - 2f * stubWidthPt).coerceAtLeast(1f)
    val lenMm = linerLengthMm.coerceAtLeast(1f)
    val ptPerMm = ptPerMmOverride?.takeIf { it > 0f }
        ?: (innerWidth / lenMm).coerceIn(minPtPerMm, maxPtPerMm)
    val linerWidthPt = lenMm * ptPerMm
    val usedWidth = linerWidthPt + 2f * stubWidthPt
    val leftPad = (((stripRightPt - stripLeftPt) - usedWidth) / 2f).coerceAtLeast(0f)
    val linerLeftPt = stripLeftPt + leftPad + stubWidthPt
    val linerRightPt = linerLeftPt + linerWidthPt
    return WearStripHorizontalLayout(stubWidthPt, linerLeftPt, linerRightPt, ptPerMm)
}

// ──────────────────────────────────────────────────────────────────────────────
// Strip windows — one strip may hold several components with the gaps between them
// ──────────────────────────────────────────────────────────────────────────────
//
// A strip is a WINDOW onto the shaft: an ordered run of component spans and the gaps
// between them, all drawn through ONE piecewise mm→pt mapping (the same one-mapping posture
// as `geom/ProfileCompression.kt`). A window with a single component is exactly the historical
// per-liner strip, so a liner-only sheet's geometry is unchanged.

/** A gap up to this wide draws at the sheet's true scale; anything longer compresses. */
const val WEAR_STRIP_TRUE_GAP_MAX_MM = 76.2f      // 3"

/** Fixed drawn width of a compressed gap — the open run between the S-break pair. */
const val WEAR_STRIP_BREAK_GAP_PT = 40f

/** Which kind of component a strip segment holds; each draws its own silhouette. */
enum class WearStripComponentKind { LINER, TAPER, BODY }

/**
 * One strip-eligible component flattened to what strip layout needs: its span, the diameters at
 * its two edges (equal for a liner/body, the taper's start/end pair for a taper), and whether it
 * is an auto (bare-shaft) body. Keyed by **resolved component id**, the same key wear pits and
 * measured-Ø readings use.
 *
 * Deliberately a layout-local type rather than `ResolvedComponent`: this file stays pure
 * (no `ui` import) so every rule below is directly unit-testable.
 */
data class WearStripComponent(
    val id: String,
    val kind: WearStripComponentKind,
    val startMm: Float,
    val endMm: Float,
    val aftDiaMm: Float,
    val fwdDiaMm: Float,
    val auto: Boolean = false,
) {
    val lengthMm: Float get() = (endMm - startMm).coerceAtLeast(0f)
    val maxDiaMm: Float get() = maxOf(aftDiaMm, fwdDiaMm)

    /** Drawable at all — a degenerate span or diameter would only claim an empty cell. */
    val drawable: Boolean get() = lengthMm > 0f && maxDiaMm > 0f

    /**
     * Diameter at a component-local position, interpolated linearly for a taper — the same
     * local-radius math the detail overlay and `drawWearPitsOnProfile` use, so a pit or a
     * measured-Ø leader lands on the sloped surface.
     */
    fun diaAtLocalMm(localMm: Float): Float {
        if (lengthMm <= 0f) return aftDiaMm
        val t = (localMm / lengthMm).coerceIn(0f, 1f)
        return aftDiaMm + (fwdDiaMm - aftDiaMm) * t
    }
}

/** One ordered piece of a [WearStripWindow] — a component span or the gap before the next one. */
sealed class WearStripSegment {
    abstract val startMm: Float
    abstract val endMm: Float
    val lengthMm: Float get() = (endMm - startMm).coerceAtLeast(0f)
}

/** A component's own span, drawn at the sheet's shared scale. */
data class WearStripComponentSeg(val component: WearStripComponent) : WearStripSegment() {
    override val startMm: Float get() = component.startMm
    override val endMm: Float get() = component.endMm
}

/**
 * The shaft between two components in the same window. [trueScale] gaps draw at the sheet scale
 * with the connecting shaft outline; the rest compress to [WEAR_STRIP_BREAK_GAP_PT] and print the
 * S-break pair instead. The mode is decided from **mm alone** ([wearStripGapDrawsTrue]) so the
 * shared-scale solve stays non-circular.
 */
data class WearStripGapSeg(
    override val startMm: Float,
    override val endMm: Float,
    val trueScale: Boolean,
) : WearStripSegment()

/** Whether a gap of [gapMm] draws at true scale rather than compressing to a break. */
fun wearStripGapDrawsTrue(gapMm: Float): Boolean = gapMm <= WEAR_STRIP_TRUE_GAP_MAX_MM

/**
 * One detail strip's window onto the shaft: an ordered, contiguous run of component and gap
 * segments. Everything in the strip draws through [xAt], the window's single piecewise mm→pt
 * mapping — true-scale segments run at the sheet's shared scale and a compressed gap maps
 * linearly across its fixed drawn width, so the mapping is monotone and exact at every segment
 * boundary.
 */
data class WearStripWindow(val segments: List<WearStripSegment>) {
    val startMm: Float get() = segments.firstOrNull()?.startMm ?: 0f
    val endMm: Float get() = segments.lastOrNull()?.endMm ?: 0f

    /** The window's components, AFT→FWD. */
    val components: List<WearStripComponent>
        get() = segments.filterIsInstance<WearStripComponentSeg>().map { it.component }

    /** The window's liner, if it has one — it owns the title's anchor label and the wear rail. */
    val liner: WearStripComponent? get() = components.firstOrNull { it.kind == WearStripComponentKind.LINER }

    /** Largest diameter drawn in this window — the reference the strip's radii scale against. */
    val refDiaMm: Float get() = components.maxOfOrNull { it.maxDiaMm } ?: 0f

    /** One segment's drawn width: mm at [ptPerMm], except a compressed gap's fixed run. */
    fun segmentWidthPt(seg: WearStripSegment, ptPerMm: Float): Float = when {
        seg is WearStripGapSeg && !seg.trueScale -> WEAR_STRIP_BREAK_GAP_PT
        else -> seg.lengthMm * ptPerMm
    }

    /** Total drawn width: component spans + true gaps at scale + each break gap's fixed run. */
    fun drawnWidthPt(ptPerMm: Float): Float =
        segments.fold(0f) { acc, seg -> acc + segmentWidthPt(seg, ptPerMm) }

    /**
     * The window's piecewise mm→pt mapping, with the window's AFT edge at [leftPt]. Positions
     * outside the window extrapolate at [ptPerMm] (the neighbor stubs' own space), so callers
     * never need a bounds check.
     */
    fun xAt(mm: Float, leftPt: Float, ptPerMm: Float): Float {
        if (segments.isEmpty()) return leftPt
        if (mm <= startMm) return leftPt + (mm - startMm) * ptPerMm
        var x = leftPt
        segments.forEach { seg ->
            val w = segmentWidthPt(seg, ptPerMm)
            if (mm <= seg.endMm) {
                val len = seg.lengthMm
                val f = if (len <= 0f) 0f else ((mm - seg.startMm) / len).coerceIn(0f, 1f)
                return x + w * f
            }
            x += w
        }
        return x + (mm - endMm) * ptPerMm
    }
}

/** Which side of a liner a taper attaches on — at most one taper joins from each. */
private enum class TaperSide { AFT, FWD }

/**
 * Groups the elected components into strip windows, AFT→FWD.
 *
 * Each elected **taper** attaches to the NEAREST elected liner (smallest gap; ties go AFT, i.e.
 * the more aftward liner wins), forming one combined window with that liner — the machinist reads
 * a taper and the liner beside it as one area. At most one taper joins a liner from each side; a
 * taper that loses the contest, a taper elected with no liners on the sheet, and every elected
 * **body** get their own single-component window. Bodies never join a liner: they are the shaft's
 * fluid base, so a body window is its own run.
 *
 * [stripComponentIds] is the job's election ([WearRecord.stripComponentIds]): `null` is the
 * default election — every drawable liner, exactly the historical sheet, which yields one
 * single-component window per liner. Ids that resolve to nothing are simply absent from
 * [components] and skipped here — orphan handling at the render layer, never at decode.
 */
fun collectWearStripWindows(
    components: List<WearStripComponent>,
    stripComponentIds: List<String>? = null,
): List<WearStripWindow> {
    val elected = stripComponentIds?.toSet()
    val selected = components
        .filter { it.drawable }
        .filter { c -> if (elected == null) c.kind == WearStripComponentKind.LINER else c.id in elected }
        .sortedBy { it.startMm }
    if (selected.isEmpty()) return emptyList()

    val liners = selected.filter { it.kind == WearStripComponentKind.LINER }
    val tapers = selected.filter { it.kind == WearStripComponentKind.TAPER }
    val loners = selected.filter { it.kind == WearStripComponentKind.BODY }.toMutableList()

    // Each taper's claim on its nearest liner. Ties go AFT: candidates sort by (gap, liner start),
    // so an equidistant pair hands the taper to the more aftward liner.
    data class Claim(val taper: WearStripComponent, val liner: WearStripComponent, val side: TaperSide, val gapMm: Float)
    val claims = tapers.mapNotNull { t ->
        liners
            .map { ln ->
                val side = if (t.startMm < ln.startMm) TaperSide.AFT else TaperSide.FWD
                val gap = if (side == TaperSide.AFT) ln.startMm - t.endMm else t.startMm - ln.endMm
                Claim(t, ln, side, gap.coerceAtLeast(0f))
            }
            .minWithOrNull(compareBy({ it.gapMm }, { it.liner.startMm }))
    }
    // At most one taper per liner side — the nearest wins (ties go AFT again, by taper start);
    // the rest fall back to their own windows.
    val winners = claims
        .groupBy { it.liner.id to it.side }
        .mapValues { (_, group) -> group.minWithOrNull(compareBy({ it.gapMm }, { it.taper.startMm }))!! }
        .values
    val wonTaperIds = winners.map { it.taper.id }.toSet()
    loners += tapers.filter { it.id !in wonTaperIds }

    val aftOf = winners.filter { it.side == TaperSide.AFT }.associateBy { it.liner.id }
    val fwdOf = winners.filter { it.side == TaperSide.FWD }.associateBy { it.liner.id }

    val windows = mutableListOf<WearStripWindow>()
    liners.forEach { ln ->
        val run = listOfNotNull(aftOf[ln.id]?.taper, ln, fwdOf[ln.id]?.taper).sortedBy { it.startMm }
        windows += buildWearStripWindow(run)
    }
    loners.forEach { windows += buildWearStripWindow(listOf(it)) }
    return windows.sortedBy { it.startMm }
}

/**
 * One window over [run] (already AFT→FWD): each component's span, with a [WearStripGapSeg]
 * between consecutive components whose mode follows [wearStripGapDrawsTrue]. Touching or
 * overlapping components get no gap segment at all.
 */
private fun buildWearStripWindow(run: List<WearStripComponent>): WearStripWindow {
    val segs = mutableListOf<WearStripSegment>()
    run.forEachIndexed { i, comp ->
        if (i > 0) {
            val prevEnd = run[i - 1].endMm
            val gapMm = comp.startMm - prevEnd
            if (gapMm > 0f) segs += WearStripGapSeg(prevEnd, comp.startMm, wearStripGapDrawsTrue(gapMm))
        }
        segs += WearStripComponentSeg(comp)
    }
    return WearStripWindow(segs)
}

/** Result of paginating strip windows: what fits on this page vs. what overflows. */
data class WearStripWindowSelection(val onPage: List<WearStripWindow>, val overflow: List<WearStripWindow>)

/**
 * Splits [windows] into what fits on one page vs. overflow.
 *
 * NOTE on overflow handling: `composeWearPdf` draws into a single caller-supplied
 * `PdfDocument.Page` (see `WearRoute.kt` — one `doc.startPage`, one `doc.finishPage`), so
 * overflow beyond [maxPerPage] is rendered as a single text note line instead of a second
 * page (`drawWearOverflowNote` in `WearPdfComposer.kt`). True multi-page output would need
 * the composer to accept the `PdfDocument` itself and every call site to change with it.
 */
fun selectWearStripWindowsForPage(
    windows: List<WearStripWindow>,
    maxPerPage: Int = WEAR_STRIP_MAX_PER_PAGE,
): WearStripWindowSelection {
    if (windows.size <= maxPerPage) return WearStripWindowSelection(windows, emptyList())
    return WearStripWindowSelection(windows.take(maxPerPage), windows.drop(maxPerPage))
}

/**
 * The shared mm→pt scale for a page of windows: [sharedWearStripPtPerMm] with each window's
 * fixed break-gap runs taken off its cell first, since those points are spent no matter what the
 * scale is. A window whose breaks alone fill its cell keeps 1 pt to solve against rather than
 * driving the scale to zero.
 */
fun sharedWearStripWindowPtPerMm(
    windows: List<WearStripWindow>,
    innerWidthsPt: List<Float>,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
): Float {
    if (windows.isEmpty()) return maxPtPerMm
    val scaledLengths = windows.map { w ->
        w.segments.sumOf { seg -> if (seg is WearStripGapSeg && !seg.trueScale) 0.0 else seg.lengthMm.toDouble() }
            .toFloat()
    }
    val budgets = windows.mapIndexed { i, w ->
        val inner = innerWidthsPt.getOrNull(i) ?: innerWidthsPt.lastOrNull() ?: 1f
        val breaks = w.segments.count { it is WearStripGapSeg && !it.trueScale } * WEAR_STRIP_BREAK_GAP_PT
        (inner - breaks).coerceAtLeast(1f)
    }
    return sharedWearStripPtPerMm(scaledLengths, budgets, maxPtPerMm)
}

/**
 * Horizontal placement of one window in its cell: a fixed-width neighbor stub on each side and
 * the window's drawn run centered in whatever slack is left — the multi-component form of
 * [computeWearStripHorizontalLayout] with the shared scale, and geometrically identical to it for
 * a single-component window.
 */
fun computeWearStripWindowLayout(
    stripLeftPt: Float,
    stripRightPt: Float,
    drawnWidthPt: Float,
    ptPerMm: Float,
    stubWidthPt: Float = WEAR_STRIP_STUB_WIDTH_PT,
): WearStripHorizontalLayout {
    // Floor of one mm's worth of width, matching the legacy per-liner path's `lenMm >= 1`
    // clamp, so a sub-millimetre component still has something to draw.
    val widthPt = drawnWidthPt.coerceAtLeast(ptPerMm)
    val usedWidth = widthPt + 2f * stubWidthPt
    val leftPad = (((stripRightPt - stripLeftPt) - usedWidth) / 2f).coerceAtLeast(0f)
    val leftPt = stripLeftPt + leftPad + stubWidthPt
    return WearStripHorizontalLayout(stubWidthPt, leftPt, leftPt + widthPt, ptPerMm)
}

// ──────────────────────────────────────────────────────────────────────────────
// Gap profile — the connecting shaft outline drawn across a true-scale gap
// ──────────────────────────────────────────────────────────────────────────────

/** One vertex of the outer-surface polyline drawn across a strip's true-scale gap. */
data class WearStripProfileVertex(val mm: Float, val diaMm: Float)

/**
 * Outer diameter of the drawn shaft at [mm] — the largest of whatever covers that station
 * (bodies, tapers interpolated, threads, liners). `0` where nothing does. Callers must pass a
 * spec whose bodies are already resolved (`ShaftSpec.withResolvedBodies`) so auto-fill spans
 * count too.
 */
fun outerDiaMmAt(spec: ShaftSpec, mm: Float): Float {
    var dia = 0f
    spec.bodies.forEach {
        if (mm >= it.startFromAftMm && mm <= it.startFromAftMm + it.lengthMm) dia = maxOf(dia, it.diaMm)
    }
    spec.tapers.forEach {
        val len = it.lengthMm
        if (len > 0f && mm >= it.startFromAftMm && mm <= it.startFromAftMm + len) {
            val t = ((mm - it.startFromAftMm) / len).coerceIn(0f, 1f)
            dia = maxOf(dia, it.startDiaMm + (it.endDiaMm - it.startDiaMm) * t)
        }
    }
    spec.threads.forEach {
        if (mm >= it.startFromAftMm && mm <= it.startFromAftMm + it.lengthMm) dia = maxOf(dia, it.majorDiaMm)
    }
    spec.liners.forEach {
        if (mm >= it.startFromAftMm && mm <= it.startFromAftMm + it.lengthMm) dia = maxOf(dia, it.odMm)
    }
    return dia
}

/**
 * The outer-surface polyline across `[fromMm, toMm]` for a strip's true-scale gap: [samples]
 * evenly-spaced stations plus a pair either side of every component edge inside the run, so a
 * step in diameter draws as a step rather than a long diagonal. Returns an empty list when
 * nothing covers the run (a true void) — the caller then bridges its two neighbors directly.
 */
fun wearStripGapProfile(
    spec: ShaftSpec,
    fromMm: Float,
    toMm: Float,
    samples: Int = 24,
): List<WearStripProfileVertex> {
    if (toMm <= fromMm) return emptyList()
    val eps = 0.01f
    val stations = sortedSetOf(fromMm, toMm)
    val n = samples.coerceIn(2, 256)
    for (i in 1 until n) stations += fromMm + (toMm - fromMm) * i / n
    fun edge(mm: Float) {
        if (mm > fromMm + eps && mm < toMm - eps) { stations += mm - eps; stations += mm + eps }
    }
    spec.bodies.forEach { edge(it.startFromAftMm); edge(it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach { edge(it.startFromAftMm); edge(it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { edge(it.startFromAftMm); edge(it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach { edge(it.startFromAftMm); edge(it.startFromAftMm + it.lengthMm) }
    val verts = stations.map { WearStripProfileVertex(it, outerDiaMmAt(spec, it)) }
    return if (verts.all { it.diaMm <= 0f }) emptyList() else verts
}

// ──────────────────────────────────────────────────────────────────────────────
// Inner strip layout — title row, liner cylinder, chained dimension rail
// ──────────────────────────────────────────────────────────────────────────────

const val WEAR_STRIP_ROW_HEIGHT_PT = 13f

/**
 * Extra vertical gap reserved between the title's own text line and the top of the
 * liner cylinder. Without this gap, the cylinder would consume the strip's whole
 * remaining band right up against the title with no headroom at all — the measured-Ø
 * callout leaders departing the cylinder bottom (see
 * `WearPdfComposer.drawWearStripWindow`) would then land only a few points above the
 * title text, reading as crowded/overlapping.
 */
const val WEAR_STRIP_LABEL_HEADROOM_PT = 11f

/**
 * Stacked label positions reserved above a strip's chained dimension rail
 * (dimension-rail — see "Wear Detail Strips" in
 * `docs/RunoutSheet.md`) for the crowding fallback in [layoutWearStripRail]: row 0
 * is the base label position directly above the rail line, and this many rows
 * total are budgeted regardless of how many wear spots the liner has — the chain is
 * always ONE rail line, so the reserved height does not scale with spot count. A
 * chain crowded enough to need more rows than this clamps every excess label to the
 * last available row (see `WearPdfComposer.drawWearStripRail`) rather than overflow
 * the strip.
 */
const val WEAR_RAIL_MAX_LABEL_ROWS = 2

/**
 * Clear run left between the chained rail line and the cylinder top — the band the rail's
 * witness lines cross, and nothing else. Fallback label rows stack on the FAR side of the rail
 * (above it): a value parked between the rail and the cylinder prints across those witness
 * lines (on-device report), and every other rail in the app puts a value that cannot seat in
 * the line above it.
 */
const val WEAR_RAIL_WITNESS_RUN_PT = 9f

data class WearStripInnerLayout(
    val cylTop: Float,
    val cylBottom: Float,
    /** Y coordinate of the strip's single chained dimension-rail line, sitting one
     *  [WEAR_RAIL_WITNESS_RUN_PT] above [cylTop]. */
    val railY: Float,
    /** How many of [WEAR_RAIL_MAX_LABEL_ROWS] stacked label rows actually fit between the strip's
     *  top and [railY] — i.e. ABOVE the rail line — 0 in a pathologically short strip (the rail
     *  line still draws, but no fallback label is placed on it). */
    val railLabelRows: Int,
)

/**
 * Splits one strip's vertical band `[stripTop, stripBottom]` into the fallback label rows and
 * the single chained dimension rail (at the TOP), the liner-cylinder region (middle), and the
 * title (at the BOTTOM) — the strip-local analogue of [computeWearVerticalLayout].
 * (The rail sits above the cylinder and the title below it, matching how the shop
 * marks the sheet by hand: dimensions above the shaft, the liner title/anchor below it.)
 *
 * Top to bottom the band holds: [maxLabelRows] × [rowHeightPt] of stacked label rows, the rail
 * line, a [witnessRunPt] run of nothing but the rail's witness lines, the cylinder, the
 * [labelHeadroomPt] gap, any [diaBandPt] callout band, and the title.
 *
 * The label rows sit ABOVE the rail line, not between it and the cylinder: the band under the
 * rail is where the witness lines run, so a value parked there prints across them (on-device
 * report) — and above the line is where every other dimension rail in the app puts a value too
 * wide to seat in its own span. `drawWearStripRail` stacks its fallback rows upward to match.
 *
 * [titleHeightPt] is the space the title text line itself consumes (its own line
 * height); [labelHeadroomPt] is then an EXTRA, explicit gap reserved above the title,
 * just below the cylinder — the measured-Ø callout leaders' departure region — so the
 * title never crowds the cylinder (see [WEAR_STRIP_LABEL_HEADROOM_PT]'s KDoc).
 *
 * The rail's own vertical budget is a FIXED [maxLabelRows] × [rowHeightPt] —
 * not proportional to how many wear spots the liner has, since the rail is always
 * one chained line no matter how many spans it's divided into. Guarantees
 * `stripTop <= railY - railLabelRows × rowHeightPt` and
 * `railY <= cylTop <= cylBottom <= stripBottom` for ANY
 * input, including pathological ones (e.g. a very large-diameter liner on a very short
 * strip, where the preferred cylinder + rail sizes don't fit together): the
 * cylinder shrinks first, and once it hits zero height, [railLabelRows] drops
 * toward zero (labels omitted, not drawn) rather than letting anything overflow
 * the strip. This is what keeps `WearPdfComposer.drawWearStripWindow`'s Canvas
 * calls inside the content rect without needing per-call bounds checks there.
 *
 * [diaBandPt] reserves an extra band between the label headroom and the title for the
 * strip's measured-diameter callout rows (`geom/WearDiaCalloutLayout.kt` — value labels
 * with leaders below the cylinder). `0` (the default) reproduces the pre-callout layout
 * exactly; the cylinder shrinks first when the band doesn't fit, same degradation order
 * as everything else here.
 *
 * [witnessRunPt] is `0` for the undercut strips (`computeUndercutStripInnerLayout`), which
 * place their own rail lines off [cylTop] and already keep a full label row of clear air
 * between the chained rail and the cylinder.
 */
fun computeWearStripInnerLayout(
    stripTop: Float,
    stripBottom: Float,
    titleHeightPt: Float,
    rowHeightPt: Float = WEAR_STRIP_ROW_HEIGHT_PT,
    labelHeadroomPt: Float = WEAR_STRIP_LABEL_HEADROOM_PT,
    maxLabelRows: Int = WEAR_RAIL_MAX_LABEL_ROWS,
    diaBandPt: Float = 0f,
    witnessRunPt: Float = WEAR_RAIL_WITNESS_RUN_PT,
): WearStripInnerLayout {
    // Title sits at the BOTTOM (its own height + an explicit headroom gap reserved just below the
    // cylinder, then any measured-Ø callout band); the chained rail sits just above the cylinder
    // with its fallback label rows stacked over it (fixed maxLabelRows budget at the top).
    val cylBottom = (stripBottom - titleHeightPt - labelHeadroomPt - diaBandPt.coerceAtLeast(0f))
        .coerceIn(stripTop, stripBottom.coerceAtLeast(stripTop))
    val available = (cylBottom - stripTop).coerceAtLeast(0f)
    val witnessRun = witnessRunPt.coerceAtLeast(0f)
    val railBudgetH = maxLabelRows.coerceAtLeast(0) * rowHeightPt + witnessRun
    val cylH = (available - railBudgetH).coerceIn(0f, available)
    val cylTop = cylBottom - cylH
    val railY = (cylTop - witnessRun).coerceAtLeast(stripTop)
    val remainingForRail = (railY - stripTop).coerceAtLeast(0f)
    val railLabelRows = (remainingForRail / rowHeightPt).toInt().coerceIn(0, maxLabelRows)
    return WearStripInnerLayout(cylTop, cylBottom, railY, railLabelRows)
}

// ──────────────────────────────────────────────────────────────────────────────
// Dimension rail — chained spans below the liner cylinder
// ──────────────────────────────────────────────────────────────────────────────
//
// One standard chained dimension rail: liner AFT edge → first band start, each band's
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
 * [WEAR_RAIL_MAX_LABEL_ROWS]), whether the value seats in a break in the line (room for it with
 * arrow-wide stubs to spare) or falls back to a stacked row, and which way the arrowheads point.
 *
 * The two flags are independent: a value pushed to a fallback row leaves the span's full width
 * to the arrowheads, so only a span too narrow to hold both prints them outward.
 */
data class WearRailSpanLayout(
    val x0Pt: Float,
    val x1Pt: Float,
    val label: String,
    val labelCxPt: Float,
    val labelRow: Int,
    val seatsInBreak: Boolean,
    val arrowInward: Boolean,
)

/**
 * Resolves [spans] (liner-local mm, from [buildWearStripRailSpans]) to on-page geometry for one
 * strip's chained dimension rail:
 * - A label is centered on its own span when it fits with [textPadPt] to spare on both sides;
 *   otherwise it's centered on the span's midpoint and allowed to overhang — a label is never
 *   dropped, matching `PdfDimensionRenderer`'s "always draw the label somewhere" rule.
 * - The value seats in a break in the line when there's room for both arrowheads beside it (same
 *   test as `DimensionRailLayout.canFitInwardArrows`); the heads themselves point inward unless
 *   the span is too narrow to hold both at all (`DimensionRailLayout.arrowsPointInward`).
 * - Labels are assigned to the lowest-numbered row (`0` first) whose already-placed labels
 *   don't overlap it horizontally — the crowding fallback for short bands/gaps whose label is
 *   wider than the span itself, where row 0 alone would overlap a neighboring label
 *   (the label-bump collision avoidance of `geom/DimensionRailLayout.kt`, replicated for this
 *   rail's single chain; that engine's cross-tier lifts have no analogue here because a wear
 *   strip has exactly one rail).
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
        val seatsInBreak = leftRoom >= arrowSizePt && rightRoom >= arrowSizePt
        val inward = DimensionRailLayout.arrowsPointInward(xa, xb, seatsInBreak, arrowSizePt)

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
        WearRailSpanLayout(xa, xb, span.label, cx, row, seatsInBreak, inward)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Strip radii — liner fills the strip's vertical budget; stubs scale to it
// ──────────────────────────────────────────────────────────────────────────────

/** Scaled on-page radii (points) for one strip's liner cylinder and its two neighbor stubs. */
data class WearStripRadii(val linerRPt: Float, val aftRPt: Float, val fwdRPt: Float)

/**
 * Strip-local radii (points): the liner cylinder always fills the strip's vertical budget
 * ([maxRadiusPt]), so every strip on the page draws its liner at the SAME height — the
 * strip's horizontal scale must never leak into cylinder height (on-device report: liners
 * of different lengths rendered at visibly different heights). Length differences stay
 * horizontal-only; liner OD differences are deliberately not height-encoded either (product
 * decision — a shaft's liners don't differ enough in OD to warrant it).
 *
 * Neighbor stubs scale by their true diameter ratio to the liner, clamped to the liner's
 * own radius so an oversized neighbor cannot overflow the cylinder band (liners are sleeves
 * over the shaft, so neighbors are effectively always smaller in practice).
 *
 * Zero/negative [maxRadiusPt] or [linerOdMm] collapses every radius to zero rather than
 * throwing, matching [computeWearStripInnerLayout]'s pathological-input guarantees.
 */
fun computeWearStripRadii(
    linerOdMm: Float,
    aftDiaMm: Float,
    fwdDiaMm: Float,
    maxRadiusPt: Float,
): WearStripRadii {
    val cap = maxRadiusPt.coerceAtLeast(0f)
    if (linerOdMm <= 0f || cap <= 0f) return WearStripRadii(0f, 0f, 0f)
    fun stub(diaMm: Float) = (cap * (diaMm / linerOdMm)).coerceIn(0f, cap)
    return WearStripRadii(cap, stub(aftDiaMm), stub(fwdDiaMm))
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
    return "${datum.labelTop} ${linerAnchorSuffix(dim.anchor)}"
}

/**
 * The anchor label's suffix ("FROM AFT S.E.T." / "FROM FWD S.E.T.") without the measured value —
 * the blank write-in template prints a writing rule where [buildLinerAnchorLabel]'s number would
 * go, followed by this suffix, so the two modes always use identical SET wording.
 */
fun linerAnchorSuffix(anchor: LinerAnchor): String =
    "FROM " + if (anchor == LinerAnchor.AFT_SET) "AFT S.E.T." else "FWD S.E.T."

/**
 * Anchor suffix for the blank write-in template's strip titles: the measurement value is a
 * writing rule and the direction is the machinist's call, so BOTH directions print with
 * breathing room for circling one by hand ("circle one"), instead of presuming
 * [linerAnchorSuffix]'s resolved AFT/FWD. Double spaces are deliberate circling room.
 */
const val WEAR_BLANK_ANCHOR_SUFFIX = "FROM  AFT / FWD  S.E.T."

/**
 * Which SET a liner's wear-strip anchor dimension is measured from ([LinerAnchor.AFT_SET] vs
 * [LinerAnchor.FWD_SET]), or `null` if the liner has no resolvable dimension. Used to align the
 * strip title (left for AFT-referenced, right for FWD-referenced) as a direction cue — same source
 * (`mapToLinerDimsForPdf`) as [buildLinerAnchorLabel]'s `FROM ... S.E.T.` text, so the two agree.
 */
fun linerAnchorForPdf(spec: ShaftSpec, liner: Liner): LinerAnchor? =
    mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO).firstOrNull { it.id == liner.id }?.anchor

