// app/src/main/java/com/android/shaftschematic/pdf/WearStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_DEFAULT_MM
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
 * Max detail strips shown on a fixed-grid [WearPdfMode.GRID] page: [WEAR_STRIP_GRID_COLUMNS]
 * columns × 2 rows = 4, so the strips never grow past ~2 rows (by design — "only take 2 rows") and
 * the shaft profile always keeps the top of the page. Strips beyond this render as a "+N more"
 * overflow note, same as [WEAR_STRIP_MAX_PER_PAGE] does for the single-column path.
 *
 * This is the **undercut** sheet's cap (`pdf/UndercutStripLayout.kt`); the wear sheet's `GRID`
 * pages pack dynamically instead and paginate off [packWearStripWindows]'s `placedCount`.
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
 *
 * This fixed grid is the **undercut** sheet's layout (`pdf/UndercutStripLayout.kt`). The wear
 * sheet's `GRID` pages fill their rows by the strips' actual drawn width instead — see
 * [packWearStripWindows].
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

/**
 * Shipped default for the taper–liner join threshold: a gap up to this wide draws at the sheet's
 * true scale, anything longer compresses. It is the SAME number as the user-set pref's default
 * ([PDF_WEAR_JOIN_GAP_DEFAULT_MM]) — one constant behind both, so the pure API's default and
 * an untouched install can never disagree.
 */
const val WEAR_STRIP_TRUE_GAP_MAX_MM = PDF_WEAR_JOIN_GAP_DEFAULT_MM      // 3"

/** Fixed drawn width of a compressed gap — the open run between the S-break pair. */
const val WEAR_STRIP_BREAK_GAP_PT = 40f

/**
 * Short run of TRUE shaft outline drawn from each component edge into a compressed gap, before
 * that side's S-break edge. Without it the break sits hard against the component edge with no
 * connecting shaft between the two (on-device report); with it the strip reads
 * "edge cap → a bit of real shaft → break → removed length → break → a bit of real shaft".
 *
 * Constraint: must stay below [WEAR_STRIP_BREAK_GAP_PT] / 2, or the two lead-ins meet and the
 * break pair loses the open space that marks the removed length.
 */
const val WEAR_STRIP_BREAK_LEAD_PT = 10f

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
 *
 * The compressed mode is a **defensive** posture under the current builder: since the join
 * threshold decides window MEMBERSHIP too ([collectWearStripWindows]), every gap
 * [collectWearStripWindows] emits is inside the threshold and therefore true-scale. A window
 * constructed directly with a compressed gap still draws and clusters
 * ([wearStripClusters]) correctly — the type is a general model of a strip window, not only of
 * what that one builder produces.
 */
data class WearStripGapSeg(
    override val startMm: Float,
    override val endMm: Float,
    val trueScale: Boolean,
) : WearStripSegment()

/**
 * Whether a gap of [gapMm] draws at true scale rather than compressing to a break.
 *
 * [trueGapMaxMm] is the user-set join threshold (`PdfPrefs.wearJoinGapMaxMm`, canonical mm),
 * defaulting to the shipped [WEAR_STRIP_TRUE_GAP_MAX_MM]. At `0` every positive gap compresses;
 * touching components produce no gap segment at all, so they stay contiguous at any setting.
 * [collectWearStripWindows] applies the same threshold to window membership, so a gap it emits
 * always answers `true` here; this predicate remains the general rule for any window.
 */
fun wearStripGapDrawsTrue(
    gapMm: Float,
    trueGapMaxMm: Float = WEAR_STRIP_TRUE_GAP_MAX_MM,
): Boolean = gapMm <= trueGapMaxMm

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

    /** The window's liner, if it has one — it owns the wear rail and its cluster's anchor label. */
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

/**
 * One run of a window's components that the drawing shows as physically ATTACHED — everything
 * joined by true-scale gaps, or touching outright. A cluster is what one strip title names.
 *
 * A compressed gap means the components either side of it are NOT adjacent, so they land in
 * different clusters: one joined "A + B — dist FROM SET" title across a break reads as one
 * continuous area and misleads (on-device request).
 */
data class WearStripCluster(val components: List<WearStripComponent>) {
    val startMm: Float get() = components.firstOrNull()?.startMm ?: 0f
    val endMm: Float get() = components.lastOrNull()?.endMm ?: 0f
}

/**
 * Splits [window]'s component run at every COMPRESSED gap segment, AFT→FWD; components joined by
 * true-scale gaps (or touching) stay in one cluster. A window with no compressed gap — every
 * single-component window included — is one cluster, so the historical single-title strip is
 * unchanged.
 */
fun wearStripClusters(window: WearStripWindow): List<WearStripCluster> {
    val out = mutableListOf<WearStripCluster>()
    var run = mutableListOf<WearStripComponent>()
    window.segments.forEach { seg ->
        when (seg) {
            is WearStripComponentSeg -> run += seg.component
            is WearStripGapSeg -> if (!seg.trueScale && run.isNotEmpty()) {
                out += WearStripCluster(run)
                run = mutableListOf()
            }
        }
    }
    if (run.isNotEmpty()) out += WearStripCluster(run)
    return out
}

/**
 * Whether a cluster prints an anchor-from-SET dimension under its name: it does unless the
 * cluster holds a **taper** — i.e. only a lone liner or a lone body run carries one.
 *
 * An attached taper + liner needs no from-SET measurement (on-device request): the strip's own
 * dimension rail is the measuring surface, and a taper sitting at the shaft end is
 * self-evidently placed. A lone liner or body keeps the anchor dimension the strip has always
 * printed — the "110 FROM CPLG S.E.T." line of the shop sketch.
 */
fun wearStripClusterShowsAnchor(cluster: WearStripCluster): Boolean =
    cluster.components.none { it.kind == WearStripComponentKind.TAPER }

/** Which side of a liner a taper attaches on — at most one taper joins from each. */
private enum class TaperSide { AFT, FWD }

/**
 * Groups the elected components into strip windows, AFT→FWD.
 *
 * Each elected **taper** attaches to the NEAREST elected liner (smallest gap; ties go AFT, i.e.
 * the more aftward liner wins) **when that gap is within [trueGapMaxMm]**, forming one combined
 * window with that liner — the machinist reads a taper and the liner beside it as one area. At
 * most one taper joins a liner from each side; a taper that loses the contest, a taper farther
 * from its nearest liner than the threshold, a taper elected with no liners on the sheet, and
 * every elected **body** get their own single-component window. Bodies never join a liner: they
 * are the shaft's fluid base, so a body window is its own run.
 *
 * [stripComponentIds] is the job's election ([WearRecord.stripComponentIds]): `null` is the
 * default election — every drawable liner, exactly the historical sheet, which yields one
 * single-component window per liner. Ids that resolve to nothing are simply absent from
 * [components] and skipped here — orphan handling at the render layer, never at decode.
 *
 * [trueGapMaxMm] is the user-set join threshold (`PdfPrefs.wearJoinGapMaxMm`), and it governs
 * BOTH which components share a window and how a gap inside one draws
 * ([wearStripGapDrawsTrue]). A taper past the threshold is a separate strip, drawn like any
 * other lone component: sharing a window with a distant liner pushed the liner off-center by the
 * window's total width, crowded the taper against it, and pressed the break hard against the
 * taper's large end (on-device report). Consequently every gap inside a window built here draws
 * at TRUE scale; at `0` only touching tapers join at all. The nearest-liner contest itself is
 * still decided on true mm — the threshold only says whether the winner's claim stands.
 */
fun collectWearStripWindows(
    components: List<WearStripComponent>,
    stripComponentIds: List<String>? = null,
    trueGapMaxMm: Float = WEAR_STRIP_TRUE_GAP_MAX_MM,
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
        // Membership follows the join threshold: a taper farther than it from its nearest liner
        // does not attach at all and gets its own strip. Nearest-liner selection above stays on
        // true mm — the threshold only decides whether that claim stands.
        .filter { it.gapMm <= trueGapMaxMm }
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
        windows += buildWearStripWindow(run, trueGapMaxMm)
    }
    loners.forEach { windows += buildWearStripWindow(listOf(it), trueGapMaxMm) }
    return windows.sortedBy { it.startMm }
}

/**
 * One window over [run] (already AFT→FWD): each component's span, with a [WearStripGapSeg]
 * between consecutive components whose mode follows [wearStripGapDrawsTrue]. Touching or
 * overlapping components get no gap segment at all.
 */
private fun buildWearStripWindow(
    run: List<WearStripComponent>,
    trueGapMaxMm: Float = WEAR_STRIP_TRUE_GAP_MAX_MM,
): WearStripWindow {
    val segs = mutableListOf<WearStripSegment>()
    run.forEachIndexed { i, comp ->
        if (i > 0) {
            val prevEnd = run[i - 1].endMm
            val gapMm = comp.startMm - prevEnd
            if (gapMm > 0f) {
                segs += WearStripGapSeg(prevEnd, comp.startMm, wearStripGapDrawsTrue(gapMm, trueGapMaxMm))
            }
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
// Dynamic row packing — rows filled by ACTUAL drawn width (WearPdfMode.GRID)
// ──────────────────────────────────────────────────────────────────────────────
//
// The fixed 2-column grid above ([computeWearStripGridLayout]) gives every strip half the page
// whatever it draws, so two short components hog a row a third could have shared. The packer
// below fills each row by the windows' real drawn widths instead: whitespace (the neighbor stubs
// and the gutter between windows) is squeezed to its floor FIRST, and only a page that still
// doesn't fit shrinks the shared scale. The fixed grid stays as it is — the undercut sheet
// (`pdf/UndercutStripLayout.kt`) keeps using it.

/** Most windows one packed row may hold, whatever the widths allow — beyond three side by side a
 *  strip's rail values and title have no room left to read. */
const val WEAR_STRIP_MAX_PER_ROW = 3

/**
 * Floor the neighbor stub may be squeezed to before the shared scale gives way.
 *
 * The stub is where the window's end style draws ([wearStripEndStyle]): a flat cap, a thread-end
 * hatch, or the S-break glyph, which sits at the stub's OUTER end and reaches back inward roughly
 * `√3/6 × 0.6 × r` (≈ 0.17 × the stub radius, `BREAK_PAIR_REACH_FRAC`'s geometry). At the tallest
 * strip this sheet draws that is ~10 pt, so a 20 pt stub still leaves a clear run of shaft between
 * the glyph and the component's edge cap — the same daylight [WEAR_STRIP_BREAK_LEAD_PT] gives a
 * compressed gap's break. Narrower than this the glyph would sit on the edge cap and the end would
 * stop reading as a break-out.
 */
const val WEAR_STRIP_STUB_MIN_PT = 20f

/**
 * Floor the gutter between two packed windows may be squeezed to — a PACKING floor only.
 *
 * A packed cell is exactly its window's footprint, so a BREAK end's S glyph sits ON the cell edge
 * and bulges OUTWARD into the gutter by [BREAK_EDGE_OUTWARD_REACH_FRAC] of its amplitude
 * (≈ 0.26 × the stub radius — the return sweep's reach, see `BreakSymbol.kt`). Two neighbors both
 * bulge, and at the radii a tall page draws the pair needs far more than any fixed floor — so the
 * no-crossing guarantee does NOT live here: after packing, [spreadWearStripRowGutters] widens the
 * gutters whose facing ends bulge (slack-funded, radius-aware) and [wearStripBreakAmplitudePt]
 * flattens whatever a slack-less row still can't host.
 */
const val WEAR_STRIP_COL_GAP_MIN_PT = 16f

/** Packed strip rows when the shaft profile band keeps the top of the page. */
const val WEAR_STRIP_MAX_ROWS_WITH_PROFILE = 2

/** Packed strip rows when the profile is elected out and its whole band goes to the strips. */
const val WEAR_STRIP_MAX_ROWS_NO_PROFILE = 3

/**
 * How many rows of detail strips the page allows: the sheet holds three bands of content either
 * way, and with the shaft profile on ([WearRecord.showShaftProfile]) one of them IS the shaft.
 * Hiding it frees a whole band, so the strips get the third row rather than pushing a component
 * into the "+N more" note (on-device request). ONE rule, so the composer never re-derives the row
 * budget inline.
 */
fun wearStripMaxRows(showShaftProfile: Boolean): Int =
    if (showShaftProfile) WEAR_STRIP_MAX_ROWS_WITH_PROFILE else WEAR_STRIP_MAX_ROWS_NO_PROFILE

/**
 * Horizontal whitespace a packed page spends: the neighbor stub on each side of a window, and the
 * gutter between two windows in a row. UNIFORM page-wide — a sheet whose strips had different stub
 * widths would read as a mistake.
 */
data class WearStripSpacing(val stubWidthPt: Float, val colGapPt: Float)

/** One packed window: which row it landed in and the exact cell it owns (points). */
data class WearStripPackedCell(val windowIndex: Int, val row: Int, val left: Float, val right: Float)

/**
 * Result of [packWearStripWindows].
 *
 * **ONE shared mm→pt scale across the whole page** ([ptPerMm]), the same invariant
 * [sharedWearStripWindowPtPerMm] carries: relative component lengths must read true, so a 22"
 * liner draws half the width of a 44" one. The packer only decides how the page's WIDTH is
 * divided; it never gives a window its own scale.
 *
 * [cells] holds the windows that fit, in window order (AFT→FWD reading order is never reordered) —
 * windows `[0, placedCount)`. Anything past [placedCount] overflows to the "+N more" note.
 */
data class WearStripPacking(
    val rowCount: Int,
    val cells: List<WearStripPackedCell>,
    val ptPerMm: Float,
    val spacing: WearStripSpacing,
    val placedCount: Int,
)

/** Binary-search budget for both solves below — 40 halvings, or an interval too small to matter. */
private const val WEAR_PACK_SOLVE_ITERS = 40
private const val WEAR_PACK_SOLVE_EPS = 1e-4f
private const val WEAR_PACK_WIDTH_EPS_PT = 1e-3f

/**
 * How much larger a deeper row count's solved shared scale must be before the packer spends the
 * extra row(s) on it ([packWearStripWindows] step 1) — 1.25 = at least 25% more pt-per-mm. Keeps
 * the two poles of the trade honest: an election crammed into one row at a fraction of the page's
 * possible size takes the deeper layout (on-device report: three liners in one cramped row over a
 * half-empty page), while strips already at or near the scale cap stay side by side, tall,
 * because stacking them would buy almost nothing.
 */
const val WEAR_PACK_ROW_SCALE_GAIN = 1.25f

/**
 * One window's footprint at [ptPerMm]: its drawn run plus a stub on each side. The run keeps
 * [computeWearStripWindowLayout]'s one-millimetre floor so a packed cell is exactly the width that
 * function then uses — otherwise a sub-millimetre component would draw wider than its own cell.
 */
private fun wearStripFootprintPt(window: WearStripWindow, ptPerMm: Float, stubWidthPt: Float): Float =
    window.drawnWidthPt(ptPerMm).coerceAtLeast(ptPerMm) + 2f * stubWidthPt

/** Total drawn width of one packed row: every footprint plus a gutter between neighbors. */
private fun wearStripRowWidthPt(
    windows: List<WearStripWindow>,
    row: List<Int>,
    ptPerMm: Float,
    spacing: WearStripSpacing,
): Float = row.fold(0f) { acc, i -> acc + wearStripFootprintPt(windows[i], ptPerMm, spacing.stubWidthPt) } +
    (row.size - 1).coerceAtLeast(0) * spacing.colGapPt

/**
 * Greedy first-fit over [windows] **in order**: open a row, keep adding while the row still fits
 * inside [widthPt] (counting one gutter per neighbor) and holds fewer than [maxPerRow], otherwise
 * start a new row. Order is never changed — the strips read AFT→FWD down the page. First-fit is
 * optimal in row count for a fixed order, which is what makes the scale solve's monotonicity
 * argument hold.
 *
 * A window too wide for the page on its own still claims a row (it has to go somewhere); the
 * caller's fit test checks row WIDTH separately, so such a page shrinks its scale instead of
 * declaring a row-count success with a cell hanging off the paper.
 */
private fun packWearStripRows(
    windows: List<WearStripWindow>,
    ptPerMm: Float,
    spacing: WearStripSpacing,
    widthPt: Float,
    maxPerRow: Int,
): List<List<Int>> {
    val rows = mutableListOf<List<Int>>()
    var cur = mutableListOf<Int>()
    var curW = 0f
    windows.indices.forEach { i ->
        val fp = wearStripFootprintPt(windows[i], ptPerMm, spacing.stubWidthPt)
        if (cur.isNotEmpty() &&
            (cur.size >= maxPerRow || curW + spacing.colGapPt + fp > widthPt + WEAR_PACK_WIDTH_EPS_PT)
        ) {
            rows += cur
            cur = mutableListOf()
            curW = 0f
        }
        curW = if (cur.isEmpty()) fp else curW + spacing.colGapPt + fp
        cur += i
    }
    if (cur.isNotEmpty()) rows += cur
    return rows
}

/**
 * Packs [windows] into at most [maxRows] rows across `[contentLeftPt, contentRightPt]`, spending
 * WHITESPACE before drawn size (on-device request: two short strips each hogging half the page,
 * with no room left for a third).
 *
 * The objective is **lexicographic — the row count whose scale earns it, then the largest scale
 * within it, then re-expand whitespace**:
 *
 * 1. **Row count: fewest by default, more when the scale earns them.** The fewest rows any scale
 *    can reach (taken at the scale floor, where every footprint is smallest) is the baseline —
 *    but the page's rows are only scarce when the sheet actually uses them, and an election that
 *    happens to FIT one row can be forced there at a fraction of the size the page could print
 *    (on-device report: three liners packed into one cramped row over a half-empty page). So
 *    every deeper row count up to [maxRows] is auditioned, and a deeper one is taken when its
 *    solved shared scale beats the current choice by at least [WEAR_PACK_ROW_SCALE_GAIN] — a row
 *    must buy a meaningfully bigger drawing, so a pair of short strips already at (or near) the
 *    scale cap still sits side by side, tall, rather than stacking for a sliver of width.
 * 2. **Largest scale within exactly that row count** — binary search the largest `ptPerMm` in
 *    `[minPtPerMm, maxPtPerMm]` at which the windows still pack into that many rows AND no row
 *    overruns the content width, tested at **tight** spacing (the most permissive test, so
 *    whitespace is always given up first). A page that fits at [maxPtPerMm] shrinks not at all.
 * 3. **Whitespace re-expansion** — with the scale and the row assignment fixed, binary search one
 *    blend factor lerping tight→full spacing and take the largest that still fits. Spacing stays
 *    uniform page-wide ([WearStripSpacing]).
 * 4. **Cells** — each window gets a cell exactly its own footprint wide, laid left to right a
 *    gutter apart, with the whole row **centered** in the content width (the fixed grid's
 *    "a partial row is centered" convention, now applied to every row). Leftover slack sits at the
 *    page margins — except what [spreadWearStripRowGutters] then spends widening the gutters whose
 *    facing ends draw S-break curls (the caller runs that pass once row heights, and so the drawn
 *    radii, are known).
 *
 * **Overflow**: when even the scale floor can't pack the election into [maxRows] rows, the longest
 * prefix that fits is fixed there (the most permissive packing, so it is the longest prefix any
 * scale reaches) and steps 2–4 then run on that prefix alone — the surviving strips draw as large
 * as the page allows instead of staying pinned at the floor. The tail overflows to the caller's
 * "+N more" note; window order is never rearranged to fit more.
 *
 * Degenerate inputs — no windows, non-positive content width, `maxRows ≤ 0` — return an empty
 * packing rather than throwing.
 */
fun packWearStripWindows(
    windows: List<WearStripWindow>,
    contentLeftPt: Float,
    contentRightPt: Float,
    maxRows: Int,
    maxPerRow: Int = WEAR_STRIP_MAX_PER_ROW,
    maxPtPerMm: Float = WEAR_STRIP_MAX_PT_PER_MM,
    minPtPerMm: Float = WEAR_STRIP_MIN_PT_PER_MM,
    fullSpacing: WearStripSpacing = WearStripSpacing(WEAR_STRIP_STUB_WIDTH_PT, WEAR_STRIP_COL_GAP_PT),
    tightSpacing: WearStripSpacing = WearStripSpacing(WEAR_STRIP_STUB_MIN_PT, WEAR_STRIP_COL_GAP_MIN_PT),
): WearStripPacking {
    val widthPt = contentRightPt - contentLeftPt
    if (windows.isEmpty() || widthPt <= 0f || maxRows <= 0) {
        return WearStripPacking(0, emptyList(), maxPtPerMm, fullSpacing, 0)
    }
    val perRow = maxPerRow.coerceAtLeast(1)
    val hiScale = maxOf(minPtPerMm, maxPtPerMm)
    val loScale = minOf(minPtPerMm, maxPtPerMm)

    fun rowsOf(list: List<WearStripWindow>, s: Float) =
        packWearStripRows(list, s, tightSpacing, widthPt, perRow)
    fun rowsWithinWidth(list: List<WearStripWindow>, rows: List<List<Int>>, s: Float): Boolean =
        rows.all { wearStripRowWidthPt(list, it, s, tightSpacing) <= widthPt + WEAR_PACK_WIDTH_EPS_PT }
    // Both halves of the test are monotone in the scale (every footprint grows with it), so the
    // conjunction is too — which is what makes the binary search valid.
    fun fits(list: List<WearStripWindow>, s: Float, rowBudget: Int): Boolean {
        val rows = rowsOf(list, s)
        return rows.size <= rowBudget && rowsWithinWidth(list, rows, s)
    }
    fun solveScale(list: List<WearStripWindow>, rowBudget: Int): Float = when {
        fits(list, hiScale, rowBudget) -> hiScale
        !fits(list, loScale, rowBudget) -> loScale
        else -> {
            var lo = loScale
            var hi = hiScale
            var i = 0
            while (i < WEAR_PACK_SOLVE_ITERS && hi - lo > WEAR_PACK_SOLVE_EPS) {
                val mid = (lo + hi) * 0.5f
                if (fits(list, mid, rowBudget)) lo = mid else hi = mid
                i++
            }
            lo
        }
    }

    // Step 1 — row count. The fewest rows any scale can reach (the greedy packing at the scale
    // floor, where every footprint is smallest) is the baseline; every deeper count up to maxRows
    // is then auditioned, and a deeper one wins only when its solved scale beats the current
    // choice by WEAR_PACK_ROW_SCALE_GAIN — the extra row must buy a meaningfully bigger drawing,
    // never a sliver.
    val floorRows = rowsOf(windows, loScale)
    val floorWithinWidth = rowsWithinWidth(windows, floorRows, loScale)
    val layoutList: List<WearStripWindow>
    val ptPerMm: Float
    val rows: List<List<Int>>
    when {
        floorRows.size <= maxRows && floorWithinWidth -> {
            layoutList = windows
            var chosenRowCount = floorRows.size
            var chosenScale = solveScale(windows, chosenRowCount)
            for (r in chosenRowCount + 1..maxRows) {
                val s = solveScale(windows, r)
                if (s >= chosenScale * WEAR_PACK_ROW_SCALE_GAIN) {
                    chosenRowCount = r
                    chosenScale = s
                }
            }
            ptPerMm = chosenScale
            rows = rowsOf(windows, ptPerMm).take(chosenRowCount)
        }
        !floorWithinWidth -> {
            // One window is wider than the whole page even at the scale floor: no scale rescues it,
            // so draw at the floor and let the row budget decide what prints.
            layoutList = windows
            ptPerMm = loScale
            rows = floorRows.take(maxRows)
        }
        else -> {
            // Capacity overflow: the row budget can't hold the election at any scale. The longest
            // prefix that fits is settled at the floor, then solved on its own so the surviving
            // strips aren't left pinned there.
            val prefixCount = floorRows.take(maxRows).sumOf { it.size }
            layoutList = windows.take(prefixCount)
            ptPerMm = solveScale(layoutList, maxRows)
            rows = rowsOf(layoutList, ptPerMm).take(maxRows)
        }
    }
    val placedCount = rows.sumOf { it.size }

    fun spacingAt(t: Float) = WearStripSpacing(
        stubWidthPt = tightSpacing.stubWidthPt + (fullSpacing.stubWidthPt - tightSpacing.stubWidthPt) * t,
        colGapPt = tightSpacing.colGapPt + (fullSpacing.colGapPt - tightSpacing.colGapPt) * t,
    )
    // A row already over the width at TIGHT spacing (one window longer than the whole page, at the
    // scale floor) can never be satisfied, so it must not drag every other row down to tight.
    val hopeless = rows.map {
        wearStripRowWidthPt(layoutList, it, ptPerMm, tightSpacing) > widthPt + WEAR_PACK_WIDTH_EPS_PT
    }
    fun spacingFits(t: Float): Boolean = rows.indices.all { r ->
        hopeless[r] ||
            wearStripRowWidthPt(layoutList, rows[r], ptPerMm, spacingAt(t)) <= widthPt + WEAR_PACK_WIDTH_EPS_PT
    }
    val spacing = if (spacingFits(1f)) fullSpacing else {
        var lo = 0f
        var hi = 1f
        var i = 0
        while (i < WEAR_PACK_SOLVE_ITERS && hi - lo > WEAR_PACK_SOLVE_EPS) {
            val mid = (lo + hi) * 0.5f
            if (spacingFits(mid)) lo = mid else hi = mid
            i++
        }
        spacingAt(lo)
    }

    val cells = mutableListOf<WearStripPackedCell>()
    rows.forEachIndexed { r, row ->
        val rowW = wearStripRowWidthPt(layoutList, row, ptPerMm, spacing)
        var x = contentLeftPt + ((widthPt - rowW) / 2f).coerceAtLeast(0f)
        row.forEach { idx ->
            val fp = wearStripFootprintPt(layoutList[idx], ptPerMm, spacing.stubWidthPt)
            cells += WearStripPackedCell(idx, r, x, x + fp)
            x += fp + spacing.colGapPt
        }
    }
    return WearStripPacking(rows.size, cells, ptPerMm, spacing, placedCount)
}

// ──────────────────────────────────────────────────────────────────────────────
// Gutter spreading — facing S-break curls must never cross a packed gutter
// ──────────────────────────────────────────────────────────────────────────────
//
// A packed cell is exactly its window's footprint, so a BREAK end's glyph sits ON the cell
// edge and its return sweep bulges OUTWARD into the gutter by BREAK_EDGE_OUTWARD_REACH_FRAC
// of the amplitude (`BreakSymbol.kt`). The packer's uniform gutter is sized for packing, not
// for the drawn radius — on a page whose strips draw tall, two facing curls interweave across
// it (on-device report, exported sheet: every strip-to-strip gutter read as one woven knot).
// The pass below spends each row's leftover slack — the width a centered row parks at the
// margins — widening exactly the gutters whose facing ends bulge, then re-centers the row.
// The amplitude clamp (`wearStripBreakAmplitudePt`) is the backstop for a row with no slack
// to give: the curl flattens rather than ever cross into its neighbor.

/** Amplitude of a stub-end / gap-pair break glyph relative to its radius — the draw sites' 0.6·r. */
const val WEAR_STRIP_BREAK_AMP_FRAC = 0.6f

/** Daylight kept between two facing break curls across a packed gutter. */
const val WEAR_STRIP_GUTTER_DAYLIGHT_PT = 6f

/**
 * Widens each packed row's gutters to [requiredGutterPt] (whatever that pair of facing ends
 * needs — `0` for a pair with no break curls, which therefore never widens), funded by the
 * row's leftover slack, and re-centers the row in the content width. Cells keep their exact
 * footprint widths — only the gaps between them and the row's placement move, so the shared
 * scale and every drawn run are untouched.
 *
 * A row without enough slack widens every deficient gutter by the same fraction of its need
 * (never past the content width, never below the packed gutter); the amplitude clamp then
 * keeps the curls apart. Single-cell rows and rows already over the width return unchanged.
 * Cells are returned in the input order.
 */
fun spreadWearStripRowGutters(
    cells: List<WearStripPackedCell>,
    contentLeftPt: Float,
    contentRightPt: Float,
    requiredGutterPt: (leftWindowIndex: Int, rightWindowIndex: Int) -> Float,
): List<WearStripPackedCell> {
    val contentW = contentRightPt - contentLeftPt
    if (cells.isEmpty() || contentW <= 0f) return cells
    val moved = mutableMapOf<Int, WearStripPackedCell>()
    cells.groupBy { it.row }.forEach { (_, row) ->
        if (row.size < 2) return@forEach
        val ordered = row.sortedBy { it.left }
        val fps = ordered.map { it.right - it.left }
        val g0 = (0 until ordered.size - 1).map { ordered[it + 1].left - ordered[it].right }
        val want = (0 until ordered.size - 1).map { k ->
            maxOf(g0[k], requiredGutterPt(ordered[k].windowIndex, ordered[k + 1].windowIndex))
        }
        val extra = want.indices.fold(0f) { a, k -> a + (want[k] - g0[k]) }
        if (extra <= 0f) return@forEach
        // Slack the row can spend; a row already at (or over) the width stays put.
        val budget = (contentW - fps.sum() - g0.sum()).coerceAtLeast(0f)
        val s = (budget / extra).coerceAtMost(1f)
        if (s <= 0f) return@forEach
        val gaps = want.indices.map { g0[it] + (want[it] - g0[it]) * s }
        val rowW = fps.sum() + gaps.sum()
        var x = contentLeftPt + ((contentW - rowW) / 2f).coerceAtLeast(0f)
        ordered.forEachIndexed { k, cell ->
            moved[cell.windowIndex] = cell.copy(left = x, right = x + fps[k])
            x += fps[k] + (gaps.getOrNull(k) ?: 0f)
        }
    }
    return cells.map { moved[it.windowIndex] ?: it }
}

/**
 * Drawn amplitude for a strip end's break glyph: the full [WEAR_STRIP_BREAK_AMP_FRAC] of the
 * stub radius, clamped so the curl's outward reach ([BREAK_EDGE_OUTWARD_REACH_FRAC] ×
 * amplitude, plus the stroke) stays inside [outwardRoomPt] — this end's share of the gutter
 * beside it. The backstop behind [spreadWearStripRowGutters]: on a row too full to widen,
 * the S flattens rather than ever cross into the neighboring strip — the same degrade-not-
 * overlap posture as `breakPairLayout`. Pass [outwardRoomPt] = `Float.MAX_VALUE` where
 * nothing bounds the void side (a page margin, the single-column paths).
 */
fun wearStripBreakAmplitudePt(
    stubRPt: Float,
    outwardRoomPt: Float = Float.MAX_VALUE,
    strokeWidthPt: Float = 0f,
): Float {
    val full = WEAR_STRIP_BREAK_AMP_FRAC * stubRPt.coerceAtLeast(0f)
    if (outwardRoomPt == Float.MAX_VALUE) return full
    val cap = ((outwardRoomPt - strokeWidthPt) / BREAK_EDGE_OUTWARD_REACH_FRAC).coerceAtLeast(0f)
    return minOf(full, cap)
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
 * Strip-local radii (points): the window's reference component fills [maxRadiusPt] — the
 * strip's horizontal scale must never leak into cylinder height (on-device report: liners
 * of different lengths rendered at visibly different heights). Length differences stay
 * horizontal-only. ACROSS strips, heights are made proportional by the caller scaling
 * [maxRadiusPt] itself with [wearStripHeightFrac]: the page's largest reference diameter
 * fills its band and every other strip draws at its true ratio to it. (This supersedes the
 * earlier every-strip-fills-its-band rule — a body strip drew at the same height as a liner
 * almost an inch larger in OD, on-device report.)
 *
 * Neighbor stubs scale by their true diameter ratio to the liner, clamped to the liner's
 * own radius so an oversized neighbor cannot overflow the cylinder band (liners are sleeves
 * over the shaft, so neighbors are effectively always smaller in practice).
 *
 * Zero/negative [maxRadiusPt] or [linerOdMm] collapses every radius to zero rather than
 * throwing, matching [computeWearStripInnerLayout]'s pathological-input guarantees.
 */
/**
 * One strip's vertical scale relative to the page — the fraction of its band the strip's
 * drawn reference actually uses: the window with the page's LARGEST reference diameter
 * ([pageMaxRefDiaMm], `max` of every on-page [WearStripWindow.refDiaMm]) fills its band,
 * and every other strip draws at its true diameter ratio to it, so component heights read
 * proportional on paper — the vertical analogue of the one shared mm→pt width scale
 * (on-device report: a body strip drew at the same height as a liner almost an inch larger
 * in OD). Degenerate inputs fall back to full height rather than collapsing the strip.
 */
fun wearStripHeightFrac(refDiaMm: Float, pageMaxRefDiaMm: Float): Float =
    if (refDiaMm <= 0f || pageMaxRefDiaMm <= 0f) 1f
    else (refDiaMm / pageMaxRefDiaMm).coerceIn(0f, 1f)

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

/** How a strip window's outer end is drawn — see [wearStripEndStyle]. */
enum class WearStripEndStyle {
    /** Shaft continues past the window: the fixed-width stub truncates it, so it ends in an S-break. */
    BREAK,
    /** All that remains beyond is threaded: a flat outer edge + thread hatch, no break. */
    THREAD_END,
    /** Nothing lies beyond: the shaft physically ends at the window edge, so no stub is drawn. */
    FLAT,
}

/**
 * How the strip window's end at [edgeMm] should be drawn — [aftSide] picks which end
 * ([WearStripWindow.startMm] vs [WearStripWindow.endMm]).
 *
 * Every component span in [spec] (bodies, tapers, liners, threads) that extends beyond the edge
 * by more than [epsMm] is considered:
 * - none → [WearStripEndStyle.FLAT],
 * - all of them threads → [WearStripEndStyle.THREAD_END],
 * - otherwise → [WearStripEndStyle.BREAK].
 *
 * The stub's S-break says "the shaft continues past here", so it may only be drawn where it
 * does. Where the remaining shaft is nothing but the threaded end, the stub shows the WHOLE
 * remainder rather than a truncation, and so gets a flat outer edge + thread hatch instead of the
 * S-curve — the wear detail overlay's `leftIsEndThread`/`rightIsEndThread` convention
 * (`ui/screen/LinerWearDetail.kt`), mirrored here. Where nothing at all lies beyond, the shaft
 * simply ends and no stub belongs there.
 *
 * [spec] must have its bodies already resolved (`ShaftSpec.withResolvedBodies`), the same
 * contract [neighborDiaMmAtAft] carries: raw spec bodies may legally run under a liner and would
 * otherwise report a continuation that the drawn shaft doesn't have.
 */
fun wearStripEndStyle(
    spec: ShaftSpec,
    edgeMm: Float,
    aftSide: Boolean,
    epsMm: Float = NEIGHBOR_EPS_MM,
): WearStripEndStyle {
    fun beyond(startMm: Float, lengthMm: Float): Boolean =
        if (aftSide) startMm < edgeMm - epsMm else startMm + lengthMm > edgeMm + epsMm

    spec.bodies.forEach { if (beyond(it.startFromAftMm, it.lengthMm)) return WearStripEndStyle.BREAK }
    spec.tapers.forEach { if (beyond(it.startFromAftMm, it.lengthMm)) return WearStripEndStyle.BREAK }
    spec.liners.forEach { if (beyond(it.startFromAftMm, it.lengthMm)) return WearStripEndStyle.BREAK }
    val thread = spec.threads.any { beyond(it.startFromAftMm, it.lengthMm) }
    return if (thread) WearStripEndStyle.THREAD_END else WearStripEndStyle.FLAT
}

/**
 * Major diameter of the threaded shaft end beyond [edgeMm] — the largest of the threads that
 * extend past it, `0` when none do. Sizes the [WearStripEndStyle.THREAD_END] stub, which draws
 * the real remaining shaft rather than a truncation and so must carry the thread's own diameter.
 * Same side convention and resolved-bodies contract as [wearStripEndStyle].
 */
fun wearStripEndThreadDiaMm(
    spec: ShaftSpec,
    edgeMm: Float,
    aftSide: Boolean,
    epsMm: Float = NEIGHBOR_EPS_MM,
): Float {
    var dia = 0f
    spec.threads.forEach {
        val out = if (aftSide) it.startFromAftMm < edgeMm - epsMm
        else it.startFromAftMm + it.lengthMm > edgeMm + epsMm
        if (out) dia = maxOf(dia, it.majorDiaMm)
    }
    return dia
}

// ──────────────────────────────────────────────────────────────────────────────
// Labels
// ──────────────────────────────────────────────────────────────────────────────

/**
 * One strip title's anchor dimension: which SET it is measured from and how far, in
 * measurement-space mm. See [wearStripAnchorForSpan].
 */
data class WearStripAnchorDim(val anchor: LinerAnchor, val distanceMm: Double)

/**
 * The anchor-from-SET dimension for ANY shaft-space span `[startMm, endMm]` — a liner, a taper,
 * or a body run. Wear is measured from a S.E.T. or a liner edge, so a taper/body strip needs the
 * same dimension a liner strip has always printed (on-device answer).
 *
 * The rule is `mapToLinerDimsForPdf`'s, applied to a span instead of a `Liner`: edges are rebased
 * into measurement space (`computeOalWindow`), the AFT SET → span-start and span-end → FWD SET
 * distances are compared, and the NEARER edge wins with **ties going AFT**. [sets] must be the
 * measurement-space SET pair for [spec] (`computeSetPositionsInMeasureSpace`) — the same pair the
 * composer already holds.
 */
fun wearStripAnchorForSpan(
    spec: ShaftSpec,
    startMm: Float,
    endMm: Float,
    sets: SetPositions,
): WearStripAnchorDim {
    val win = computeOalWindow(spec)
    val aftEdge = win.toMeasureX(startMm.toDouble())
    val fwdEdge = aftEdge + (endMm - startMm).toDouble().coerceAtLeast(0.0)
    val distAft = (aftEdge - sets.aftSETxMm).coerceAtLeast(0.0)      // AFT SET → AFT edge
    val distFwd = (sets.fwdSETxMm - fwdEdge).coerceAtLeast(0.0)      // FWD SET → FWD edge
    return if (distFwd < distAft) WearStripAnchorDim(LinerAnchor.FWD_SET, distFwd)
    else WearStripAnchorDim(LinerAnchor.AFT_SET, distAft)
}

/**
 * The printed anchor callout for a shaft-space span — the digital equivalent of the shop
 * sketch's "110 FROM CPLG S.E.T." line: [wearStripAnchorForSpan]'s distance formatted with the
 * sheet's own length format, then [linerAnchorSuffix].
 *
 * ONE construction for every strip title, so a taper/body strip's anchor reads exactly like a
 * liner's — see [buildLinerAnchorLabel], which is this function applied to the liner's span.
 */
fun buildSpanAnchorLabel(
    spec: ShaftSpec,
    startMm: Float,
    endMm: Float,
    sets: SetPositions,
    unit: UnitSystem,
): String {
    val dim = wearStripAnchorForSpan(spec, startMm, endMm, sets)
    return "${formatLenDim(dim.distanceMm, unit)} ${linerAnchorSuffix(dim.anchor)}"
}

/**
 * The strip's anchor-from-SET callout for a liner — [buildSpanAnchorLabel] over the liner's own
 * span, which reproduces the `mapToLinerDimsForPdf` + `buildLinerSpans` DATUM label the main
 * schematic PDF prints, so the number here is always identical to the one on the schematic page.
 * Returns "" if [liner] isn't found (should not happen — defensive only).
 */
fun buildLinerAnchorLabel(spec: ShaftSpec, liner: Liner, sets: SetPositions, unit: UnitSystem): String {
    val ln = spec.liners.firstOrNull { it.id == liner.id } ?: return ""
    return buildSpanAnchorLabel(spec, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm, sets, unit)
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
 * strip title (left for AFT-referenced, right for FWD-referenced) as a direction cue — the same
 * [wearStripAnchorForSpan] rule that produces [buildLinerAnchorLabel]'s `FROM ... S.E.T.` text,
 * so the alignment and the wording can never disagree.
 */
fun linerAnchorForPdf(spec: ShaftSpec, liner: Liner): LinerAnchor? {
    val ln = spec.liners.firstOrNull { it.id == liner.id } ?: return null
    val sets = computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec)
    return wearStripAnchorForSpan(spec, ln.startFromAftMm, ln.startFromAftMm + ln.lengthMm, sets).anchor
}

