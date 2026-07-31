// app/src/main/java/com/android/shaftschematic/pdf/UndercutStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.Undercut
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
 * - [computeWearStripHorizontalLayout] — the window plays the liner's role; the fixed
 *   side stub becomes the [UNDERCUT_STRIP_EDGE_INSET_PT] margin the break edges live in.
 * - [WearRailSpan] / [layoutWearStripRail] — the rail resolve/label-stacking conventions
 *   are identical; only the chain *content* differs, which is [buildUndercutRailSpans].
 * - [linerAnchorSuffix] / [WEAR_BLANK_ANCHOR_SUFFIX] — shared wording so the undercut
 *   sheet's "FROM … S.E.T." reads exactly like the wear sheet's.
 *
 * `UndercutPdfComposer.kt` calls these and does the actual `Canvas` drawing; this file
 * only computes *where things go*.
 */

// ──────────────────────────────────────────────────────────────────────────────
// Page mode — one detail strip per cluster window
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The undercut sheet's rendering mode, from the number of cluster windows
 * (`geom/UndercutMath.kt`'s `clusterUndercuts`): `0` → [WearPdfMode.PROFILE_FORM] (the
 * profile-only hand-marking form — also what blank/template mode always produces), `1` →
 * [WearPdfMode.COMBINED] (one full-width strip below the profile), `2+` →
 * [WearPdfMode.GRID] (a 2-column grid, [WEAR_STRIP_GRID_MAX_PER_PAGE] max plus a "+N more"
 * note).
 *
 * The rule is a plain count → mode mapping with no liner-specific content in it, so this
 * delegates to [determineWearPdfMode] instead of restating the thresholds — the two sheets
 * cannot drift apart in how many strips they will lay side by side.
 */
fun determineUndercutPdfMode(clusterCount: Int): WearPdfMode = determineWearPdfMode(clusterCount)

/**
 * Detail strips shown on one undercut page for [mode] — the grid's 2 × 2 cap, or the
 * single-column cap below that. Clusters past this print as a "+N more" text note (the
 * single-page constraint documented on [selectWearStripsForPage]).
 */
fun undercutStripsPerPage(mode: WearPdfMode): Int =
    if (mode == WearPdfMode.GRID) WEAR_STRIP_GRID_MAX_PER_PAGE else WEAR_STRIP_MAX_PER_PAGE

// ──────────────────────────────────────────────────────────────────────────────
// Horizontal + vertical strip banding
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Margin kept between a strip's edge and the drawn window profile, points. Unlike a wear
 * strip — where the same slot holds a neighbour *stub* with its own break edge outboard of
 * the liner — an undercut strip draws the window itself right up to its cut ends, so this
 * is pure breathing room for the break symbols' lobes (which bulge horizontally about the
 * cut line) and the outermost rail witness lines.
 */
const val UNDERCUT_STRIP_EDGE_INSET_PT = 16f

/**
 * Vertical band reserved at the top of a strip for the cluster's **total** dimension rail —
 * the second rail line above the chained one (the reference sketch's overall figure across
 * the whole cluster). Only reserved when a total span exists ([buildUndercutTotalSpan]).
 */
const val UNDERCUT_TOTAL_RAIL_BAND_PT = 14f

/**
 * One undercut strip's vertical split: the total-span rail line at the very top, the
 * chained rail below it, the window-profile band in the middle, and the anchor title at
 * the bottom (with the measured-Ø callout band between profile and title).
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
 * Splits one strip's vertical band, reserving [totalRailBandPt] at the top for the
 * cluster's total-span rail when [hasTotalRail], then delegating everything below it to
 * [computeWearStripInnerLayout] — so the chained rail, the profile band, the measured-Ø
 * band ([diaBandPt]) and the title degrade in exactly the same order the wear strips do
 * (the drawn cylinder shrinks first, then label rows drop out; nothing ever overflows the
 * strip).
 */
fun computeUndercutStripInnerLayout(
    stripTop: Float,
    stripBottom: Float,
    titleHeightPt: Float,
    hasTotalRail: Boolean,
    totalRailBandPt: Float = UNDERCUT_TOTAL_RAIL_BAND_PT,
    rowHeightPt: Float = WEAR_STRIP_ROW_HEIGHT_PT,
    labelHeadroomPt: Float = WEAR_STRIP_LABEL_HEADROOM_PT,
    maxLabelRows: Int = WEAR_RAIL_MAX_LABEL_ROWS,
    diaBandPt: Float = 0f,
): UndercutStripInnerLayout {
    val bottom = stripBottom.coerceAtLeast(stripTop)
    val band = if (hasTotalRail) totalRailBandPt.coerceAtLeast(0f) else 0f
    val innerTop = (stripTop + band).coerceAtMost(bottom)
    val inner = computeWearStripInnerLayout(
        stripTop = innerTop,
        stripBottom = bottom,
        titleHeightPt = titleHeightPt,
        rowHeightPt = rowHeightPt,
        labelHeadroomPt = labelHeadroomPt,
        maxLabelRows = maxLabelRows,
        diaBandPt = diaBandPt,
    )
    // The total rail's value seats in a break in its own line, so it needs roughly half a
    // text height of air above; sitting it at 60% of the reserved band leaves that above and
    // keeps it clear of the chained rail below.
    val totalRailY = if (band > 0f) (stripTop + band * 0.6f).coerceAtMost(inner.railY) else inner.railY
    return UndercutStripInnerLayout(
        totalRailY = totalRailY,
        chainRailY = inner.railY,
        cylTop = inner.cylTop,
        cylBottom = inner.cylBottom,
        railLabelRows = inner.railLabelRows,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Dimension rails — the chained chain, plus the cluster total
// ──────────────────────────────────────────────────────────────────────────────

private const val UNDERCUT_RAIL_SPAN_EPS_MM = 1e-3f

/**
 * The chained dimension rail for one cluster window, in **shaft-space mm** (the window's
 * own coordinates, so callers map with the strip's `xAt` directly): window AFT edge → first
 * shoulder, each undercut's own length, the gap between consecutive undercuts, and the
 * trailing remainder to the window FWD edge.
 *
 * [spans] must already be render-clamped (`clampUndercutSpan`) and sorted aft → fwd; this
 * only walks the chain. The algorithm is [buildWearStripRailSpans]' — chained over a
 * *window* rather than a liner, which is why it is a separate function rather than a call:
 * the wear version is liner-local (aft edge = 0) and takes wear-band clamps, while a
 * window's chain starts at an arbitrary shaft-space x and is fed undercut spans.
 *
 * Zero-length spans are OMITTED rather than drawn as degenerate zero-width dimension lines
 * (an undercut starting exactly at the window edge produces no leading pad span, two
 * back-to-back undercuts produce no gap span between them). That never leaves a hole in
 * the chain: the running cursor still advances from the window's AFT edge to its FWD edge
 * exactly, since an omitted span had zero mm to contribute — so the returned spans' lengths
 * always sum to the window length.
 *
 * An undercut whose span starts at or before the cursor (overlapping cuts — legal, since
 * only the in-shaft bounds check is enforced at entry) has its effective start pulled
 * forward to the cursor, so the chain never runs backward or double-counts the overlap.
 *
 * The two pad spans (window edge → nearest shoulder) ARE labelled: they locate the cluster
 * inside its zoom window and cost nothing (`docs/UndercutDrawing_PLAN.md` §11.3).
 */
fun buildUndercutRailSpans(
    window: UndercutWindow,
    spans: List<UndercutSpanMm>,
    unit: UnitSystem,
): List<WearRailSpan> {
    val out = mutableListOf<WearRailSpan>()
    var cursor = window.startMm
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
    if (window.endMm - cursor > UNDERCUT_RAIL_SPAN_EPS_MM) {
        out += WearRailSpan(cursor, window.endMm, formatLenDim((window.endMm - cursor).toDouble(), unit))
    }
    return out
}

/**
 * The cluster's **total** span — first shoulder → last shoulder, drawn on a second rail
 * line above the chain (the sketch's overall figure across all the sections).
 *
 * Returns `null` for a cluster of fewer than two drawable undercuts: with one cut the total
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

/** Which S.E.T. a cluster's anchor dimension is measured from — see [undercutAnchorFor]. */
enum class UndercutAnchorSide { AFT_SET, FWD_SET }

/**
 * A cluster's anchor dimension: how far [side]'s S.E.T. is from the cluster's near
 * shoulder, and which way the strip title should be aligned ([alignRight] = toward the FWD
 * end drawn on the right, the wear strip title's direction cue).
 */
data class UndercutAnchor(val side: UndercutAnchorSide, val distanceMm: Float) {
    val alignRight: Boolean get() = side == UndercutAnchorSide.FWD_SET
}

/**
 * Picks the S.E.T. a cluster is dimensioned from and measures to the **near** shoulder —
 * the shop sketch's long anchor dimension. The choice is by proximity: a cluster whose
 * midpoint falls in the AFT half of the SET-to-SET span anchors to the AFT SET and is
 * measured from its first shoulder; otherwise it anchors to the FWD SET and is measured
 * from its last shoulder (`linerAnchorForPdf`'s idea, applied to a window instead of a
 * liner).
 *
 * The distance is reported as a magnitude: a cluster sitting outboard of its SET (possible
 * — an undercut may be recorded anywhere on the shaft, while the SETs sit at the tapers)
 * still prints a sensible "so far from the S.E.T." figure rather than a negative dimension.
 */
fun undercutAnchorFor(
    firstShoulderMm: Float,
    lastShoulderMm: Float,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): UndercutAnchor {
    val clusterMid = (firstShoulderMm + lastShoulderMm) * 0.5f
    val setMid = (aftSetXMm + fwdSetXMm) * 0.5f
    return if (clusterMid <= setMid) {
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
