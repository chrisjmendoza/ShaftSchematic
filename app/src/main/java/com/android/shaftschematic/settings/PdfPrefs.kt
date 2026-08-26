package com.android.shaftschematic.settings

import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle

/**
 * Controls how liner dimension rails are anchored in the PDF export.
 */
enum class PdfTieringMode {
    AUTO, // Let the PDF composer choose the anchor (default)
    AFT,  // Always anchor liner rails to the aft (leftmost) liner
    FWD;  // Always anchor liner rails to the fwd (rightmost) liner

    companion object {
        /** Tolerant decode for a persisted name; an unknown value falls back to [AUTO]. */
        fun fromName(raw: String?): PdfTieringMode =
            if (raw.isNullOrBlank()) AUTO else runCatching { valueOf(raw) }.getOrDefault(AUTO)
    }
}

// Bounds for the sizing-curve anchor heights (paper inches). The ceiling matches the
// absolute drawn-height cap (PROFILE_MAX_SHAFT_HEIGHT_PT = 108 pt = 1.5").
const val PDF_CURVE_HEIGHT_MIN_IN = 0.25f
const val PDF_CURVE_HEIGHT_MAX_IN = 1.5f

/**
 * Default body S-break threshold: a body run breaks once it draws below HALF its true
 * width. The single source for the shipped value — the pref field, the Settings slider's
 * reset button, and the tests all read it from here.
 */
const val PDF_SBREAK_THRESHOLD_DEFAULT = 0.5f

/**
 * Wear-band shade: the settable range and the shipped value for the grey wash filling a wear
 * area in the wear document's detail strips (`PdfPrefs.wearBandShadeFrac`).
 *
 * The default is the historical fixed alpha (40/255), so an untouched install draws exactly the
 * shipped look. The cap is deliberate and load-bearing: the band is what pit "X"s land in —
 * printed by the composer and hand-drawn by the machinist on the sheet — and a fill any heavier
 * buries both, which is why the diagonal hatch was retired. Raising [PDF_WEAR_BAND_SHADE_MAX]
 * would take that legibility back.
 */
const val PDF_WEAR_BAND_SHADE_DEFAULT = 40f / 255f
const val PDF_WEAR_BAND_SHADE_MIN = 0.05f
const val PDF_WEAR_BAND_SHADE_MAX = 0.35f

/**
 * Taper–liner join threshold: how much bare shaft may sit between two components in the same
 * wear detail strip before the run is compressed to an S-break instead of drawn true
 * (`PdfPrefs.wearJoinGapMaxMm`, consumed as `wearStripGapDrawsTrue`'s `trueGapMaxMm`).
 *
 * Canonical millimetres — the Settings row and the options sheet convert at the UI edge only.
 * The default is the shipped 3", which `pdf/WearStripLayout.WEAR_STRIP_TRUE_GAP_MAX_MM` reads
 * as its pure-API default so there is one number behind both. `0` breaks on any positive gap
 * (touching components still draw contiguous — they produce no gap segment at all); the 12" cap
 * is a foot of shaft drawn true between a taper and its liner, past which the strip stops being
 * a detail view.
 */
const val PDF_WEAR_JOIN_GAP_DEFAULT_MM = 76.2f   // 3"
const val PDF_WEAR_JOIN_GAP_MIN_MM = 0f
const val PDF_WEAR_JOIN_GAP_MAX_MM = 304.8f      // 12"

/**
 * Dimension-rail arrowhead sizes (pt, length along the line — the barb spread is half of it).
 * Three fixed choices, not a range: an arrowhead reads correct or it doesn't. Small is the
 * shipped size; Large restores the historical head.
 */
const val PDF_ARROW_SIZE_SMALL_PT = 3f
const val PDF_ARROW_SIZE_MEDIUM_PT = 4f
const val PDF_ARROW_SIZE_LARGE_PT = 5f

/** The three choices, small → large — the single source for the picker and the tests. */
val PDF_ARROW_SIZES_PT = listOf(PDF_ARROW_SIZE_SMALL_PT, PDF_ARROW_SIZE_MEDIUM_PT, PDF_ARROW_SIZE_LARGE_PT)

/**
 * Shipped arrowhead size. Small on an on-device verdict — it does the job with the least paper
 * spent, and a shorter head also widens inline eligibility, since a break's stubs must each be
 * at least `arrowSize` long.
 */
const val PDF_ARROW_SIZE_DEFAULT_PT = PDF_ARROW_SIZE_SMALL_PT

/**
 * PDF-only preferences. Add more knobs here as you grow the exporter.
 */
data class PdfPrefs(
    val tieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    val showComponentTitles: Boolean = true,
    val shadedBodies: Boolean = false,
    val shadedTapers: Boolean = false,
    val shadedLiners: Boolean = false,
    /**
     * Default sizing-curve anchor heights (paper inches of drawn height at 100% on the
     * "Shaft height" slider): a 4" shaft draws [curveLoHeightIn] tall, an 8" shaft
     * [curveHiHeightIn]; sizes between and beyond follow the line
     * (`geom/ProfileCompression.defaultShaftHeightPt`). Adjustable in
     * Settings → Drawing → Default drawing size; the standard pair is PROPORTIONAL
     * (through the origin: 8" → 1", 6" → 3/4", 4" → 1/2" — the hand-sheet rule from
     * the original rulered sketches; taller defaults read "chubby" on-device).
     */
    val curveLoHeightIn: Float = 0.5f,
    val curveHiHeightIn: Float = 1.0f,
    /**
     * Fraction of true drawn width below which a compressed BODY run shows the S-break
     * pair (`pdf/BreakSymbol.breakForCompression`). Adjustable in Settings → Drawing →
     * "Body S-break": 0 = never break on compression (all foreshortening stays hidden),
     * 1 = break on any foreshortening at all. The traditional long-span trigger
     * (`COMPRESS_TRIGGER_PT`) is deliberately NOT governed by this — a run that eats
     * 220 pt of paper at true scale is not hidden compression, so it breaks regardless.
     */
    val sBreakThresholdFrac: Float = PDF_SBREAK_THRESHOLD_DEFAULT,
    /**
     * Arrowhead size on the DIMENSION RAILS (`pdf/render/PdfDimensionRenderer.kt`) — the
     * schematic's stacked rails and the CONSOLIDATED sheet's. Adjustable in either
     * PDF options sheet and in Settings → Drawing → "Dimension arrows"; one of
     * [PDF_ARROW_SIZES_PT]. The wear / undercut strip rails keep their own fixed head (they
     * are chained inside a strip, not stacked over the drawing), and so does the classic
     * standalone runout sheet's lone OAL line — it carries no dimension rails at all.
     */
    val arrowSizePt: Float = PDF_ARROW_SIZE_DEFAULT_PT,
    /**
     * How a fraction is SET wherever the app draws one — Settings → Drawing → "Fractions".
     * Unlike the other fields here this reaches the previews as well as the PDFs, because both
     * draw families go through the one renderer (`util/FractionTextRenderer.kt`); it is a
     * drawing pref, and `PdfPrefs` is where the drawing prefs live.
     *
     * `SettingsStore.updatePdfPrefs` mirrors it into `FractionTypography.active`, which is what
     * the draw sites actually read.
     */
    val fractionStyle: FractionStyle = FractionStyle.Default,
    /**
     * How a DUAL value is set on the drawing — Settings → Drawing → "Dual-unit layout" and both
     * PDF options sheets. Only ever visible on a sheet whose document has `dual_units` on.
     *
     * [DualUnitLayout.STACKED] sets the two terms on two lines. That is the app's only label form
     * which moves HEIGHT, so unlike the other drawing prefs it is threaded explicitly into the
     * composers rather than through a process-wide mirror: the vertical budgets have to see it, and
     * a sheet whose budget cannot absorb it reverts to [DualUnitLayout.INLINE] for the whole sheet.
     * See `docs/DualUnitStacking_PLAN.md`.
     */
    val dualUnitLayout: DualUnitLayout = DualUnitLayout.Default,
    /**
     * Default worn-profile trace exaggeration (`geom/WearTraceMath.kt`): how deep a record's
     * deepest liner reading draws, as a fraction of the drawn radius. Settings → Drawing →
     * "Wear depth exaggeration", [WEAR_TRACE_MIN_DEPTH_FRAC]..[WEAR_TRACE_MAX_DEPTH_FRAC].
     *
     * A job may pin its own value (`WearRecord.traceDepthFrac`); this is what a job that never
     * touched its slider follows, so changing it here restyles every such document.
     */
    val wearTraceDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
    /**
     * Grey of a wear area's fill in the wear document's detail strips, as a fraction of full
     * black. Settings → Drawing → "Wear area shade" and the wear preview's PDF options sheet,
     * [PDF_WEAR_BAND_SHADE_MIN]..[PDF_WEAR_BAND_SHADE_MAX]; the default reproduces the shipped
     * wash exactly.
     *
     * The cap keeps the band readable UNDER a pit "X" — printed and hand-drawn alike — which is
     * the whole reason the heavier diagonal hatch was retired. The MAIN profile's bands are a
     * different mark (vertical strokes, the shop convention) and are not styled by this.
     */
    val wearBandShadeFrac: Float = PDF_WEAR_BAND_SHADE_DEFAULT,
    /**
     * How much bare shaft may sit between two components sharing a wear detail strip before the
     * run compresses to an S-break rather than drawing true (canonical mm — Settings → Drawing
     * → "Taper–liner join" and the wear preview's PDF options sheet convert at the UI edge).
     * [PDF_WEAR_JOIN_GAP_MIN_MM]..[PDF_WEAR_JOIN_GAP_MAX_MM], default the shipped 3".
     *
     * App-wide, the same posture as the body S-break threshold: a document never pins its own.
     */
    val wearJoinGapMaxMm: Float = PDF_WEAR_JOIN_GAP_DEFAULT_MM,
) {
    /** The anchor heights in PDF points (72 pt per paper inch) — what the geometry consumes. */
    val curveLoHeightPt: Float get() = curveLoHeightIn * 72f
    val curveHiHeightPt: Float get() = curveHiHeightIn * 72f

    fun clamped(): PdfPrefs =
        copy(
            curveLoHeightIn = curveLoHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
            curveHiHeightIn = curveHiHeightIn.coerceIn(PDF_CURVE_HEIGHT_MIN_IN, PDF_CURVE_HEIGHT_MAX_IN),
            sBreakThresholdFrac = sBreakThresholdFrac.coerceIn(0f, 1f),
            arrowSizePt = arrowSizePt.coerceIn(PDF_ARROW_SIZE_SMALL_PT, PDF_ARROW_SIZE_LARGE_PT),
            wearTraceDepthFrac = wearTraceDepthFrac
                .coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC),
            wearBandShadeFrac = wearBandShadeFrac
                .coerceIn(PDF_WEAR_BAND_SHADE_MIN, PDF_WEAR_BAND_SHADE_MAX),
            wearJoinGapMaxMm = wearJoinGapMaxMm
                .coerceIn(PDF_WEAR_JOIN_GAP_MIN_MM, PDF_WEAR_JOIN_GAP_MAX_MM),
        )
}
