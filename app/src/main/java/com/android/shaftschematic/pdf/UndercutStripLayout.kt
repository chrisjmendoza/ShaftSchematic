// app/src/main/java/com/android/shaftschematic/pdf/UndercutStripLayout.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.ClampedUndercutSpanMm
import com.android.shaftschematic.geom.DiaCalloutStation
import com.android.shaftschematic.geom.UndercutSpanMm
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
 * single-page constraint documented on [selectWearStripsForPage]).
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
 * Vertical band reserved at the top of a strip for the strip's **total** dimension rail —
 * the second rail line above the chained one (the reference sketch's overall figure across
 * all the sections). Only reserved when a total span exists ([buildUndercutTotalSpan]).
 */
const val UNDERCUT_TOTAL_RAIL_BAND_PT = 14f

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
 * Picks the S.E.T. a strip's cuts are dimensioned from and measures to the **near**
 * shoulder — the shop sketch's long anchor dimension. The choice is by proximity: cuts
 * whose midpoint falls in the AFT half of the SET-to-SET span anchor to the AFT SET and are
 * measured from the first shoulder; otherwise they anchor to the FWD SET and are measured
 * from the last shoulder (`linerAnchorForPdf`'s idea, applied to the cut run instead of a
 * liner).
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
