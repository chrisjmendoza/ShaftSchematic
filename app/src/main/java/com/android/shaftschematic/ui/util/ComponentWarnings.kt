package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.maxDiaMm
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Non-blocking (advisory) warning strings for shaft components.
 *
 * These are **pure** functions — they read model state and return human-readable strings.
 * They never gate export (that is `blockingExportError()` in `ui/nav/PdfExportRoute.kt`),
 * never affect geometry/OAL/collision, and never mutate the spec. See
 * `docs/VALIDATION_RULES.md` §3–4.
 *
 * The rules see only **stored** components. Auto-bodies are derived at resolve time and are
 * intentionally invisible here. Excluded threads (`excludeFromOAL`) are skipped everywhere.
 */

/** Axial slop within which two component faces are treated as abutting. */
private const val ADJACENCY_EPS_MM = 0.5f

/** Segments this short (in mm) get a "very short segment" advisory. */
private const val SHORT_SEGMENT_MM = 1f

/** Adjacent-body Ø ratio (max/min) above which a discontinuity is flagged. */
private const val BODY_STEP_WARN_RATIO = 1.5f

/**
 * Provisional sanity ceiling on a stored component's axial length (mm), above which a
 * value reads as a fat-fingered typo rather than a real shaft (~50 ft). Chosen without shop
 * input — same posture as [BODY_STEP_WARN_RATIO] and [SHORT_SEGMENT_MM], both under review
 * per `TODO.md` §2.2. Advisory only: it never clamps or rewrites the typed value.
 */
private const val SANITY_MAX_COMPONENT_LENGTH_MM = 15_000f

/**
 * Provisional sanity ceiling on any component diameter field (mm), above which a value
 * reads as a fat-fingered typo rather than a real shaft (~40 in). Same posture and caveats
 * as [SANITY_MAX_COMPONENT_LENGTH_MM].
 */
private const val SANITY_MAX_DIA_MM = 1_000f

/**
 * Axial slop absorbing float round-trip noise in the shaft-span bounds comparison. A `Float`
 * start on a 9 m shaft resolves to about 0.001 mm, so a slop of exactly that let a
 * one-unit rounding of `start + length` past the OAL light the "past the shaft end" warning on
 * a taper authored flush with the FWD face (on-device report). 0.01 mm is a hundredth of the
 * finest value anyone types and still far below anything measurable on the shaft.
 */
internal const val BOUNDS_EPS_MM = 1e-2f

private const val SHORT_SEGMENT_MSG = "Very short segment (< 1 mm)"
private const val LENGTH_SANITY_MSG = "Length exceeds 15 m — check for a typo"
private const val DIA_SANITY_MSG = "Diameter exceeds 1 m — check for a typo"
/** Advisory: a thread relief's Ø at or above the thread's major Ø. */
internal const val THREAD_RELIEF_DIA_MSG = "Relief Ø must be below the thread Ø"
/** Advisory: a thread relief as long as or longer than the thread itself. */
internal const val THREAD_RELIEF_LEN_MSG = "Relief longer than the thread"

/* ────────────────────────────────────────────────────────────────────────────
 * Shaft-span bounds — ONE comparison behind two surfaces
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * True when the span [startMm] … [startMm] + [lengthMm] leaves the authored shaft span.
 *
 * This is the **only** implementation of that comparison. The add dialogs' pre-submit warning
 * ([outsideShaftSpanMessage], reached through `collectAddWarnings`) and the carousel cards'
 * past-OAL chip ([pastShaftEndMessage]) both read it, so the two surfaces cannot drift apart
 * on what counts as outside.
 *
 * An `overallLengthMm` of 0 means "not typed yet", not "zero-length shaft" — there is no span
 * to fall outside of, so the check stays silent until a length is authored.
 *
 * Advisory only. Oversize is a legal state (`docs/contracts/OverallLength.md`): nothing here
 * clamps a stored value, blocks a commit, or gates export.
 */
fun outsideShaftSpan(spec: ShaftSpec, startMm: Float, lengthMm: Float): Boolean =
    spec.overallLengthMm > 0f &&
        (startsBeforeAftEnd(startMm) || endsPastOal(spec, startMm, lengthMm))

/**
 * The overall length rendered the way both bounds messages print it — in the document's
 * display [unit] through the footer's own length formatter, so an inch drawing reads
 * `OAL 120"` rather than the canonical `3048 mm` (on-device report: the warning quoted
 * millimeters on an inch document).
 */
private fun oalText(spec: ShaftSpec, unit: UnitSystem): String =
    formatLenWithUnit(spec.overallLengthMm.toDouble(), unit)

/** Add-dialog phrasing of [outsideShaftSpan] — a component that is not placed yet. */
fun outsideShaftSpanMessage(spec: ShaftSpec, startMm: Float, lengthMm: Float, unit: UnitSystem): String? =
    if (outsideShaftSpan(spec, startMm, lengthMm)) "Falls outside shaft span (OAL ${oalText(spec, unit)})"
    else null

/**
 * Carousel-card phrasing of [outsideShaftSpan] — a component that already exists and whose
 * edit committed verbatim. The wording names the END it runs past: "past the AFT end" for a
 * span that starts before 0, "past shaft length" for one that runs beyond the OAL (both lines
 * when it does both). "Past shaft length" for a part hanging off the AFT end read as wrong —
 * nothing there is longer than the shaft (emulator QA). The comparison is the dialog's.
 */
fun pastShaftEndMessage(spec: ShaftSpec, startMm: Float, lengthMm: Float, unit: UnitSystem): String? {
    if (!outsideShaftSpan(spec, startMm, lengthMm)) return null
    val lines = buildList {
        if (startsBeforeAftEnd(startMm)) add(PAST_AFT_END_CARD_MSG)
        if (endsPastOal(spec, startMm, lengthMm)) add("Extends past shaft length (OAL ${oalText(spec, unit)})")
    }
    return lines.joinToString("; ")
}

/** The card chip for a component that starts before the AFT face. */
internal const val PAST_AFT_END_CARD_MSG = "Extends past the AFT end of the shaft"

/**
 * The AFT half of [outsideShaftSpan] — the span starts before the AFT face. The two halves are
 * the comparison itself, not copies of it: [outsideShaftSpan] is their OR, and the banner and the
 * card chip read the halves only to NAME the end.
 */
internal fun startsBeforeAftEnd(startMm: Float): Boolean = startMm < -BOUNDS_EPS_MM

/** The FWD half of [outsideShaftSpan]: the span runs beyond the OAL. */
internal fun endsPastOal(spec: ShaftSpec, startMm: Float, lengthMm: Float): Boolean =
    startMm + lengthMm > spec.overallLengthMm + BOUNDS_EPS_MM

/** True for a positive length at or below the short-segment threshold (0 excluded). */
internal fun isShortSegment(lengthMm: Float): Boolean =
    lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM

/** True when [lengthMm] exceeds the implausible-length sanity ceiling. */
private fun isImplausiblyLong(lengthMm: Float): Boolean = lengthMm > SANITY_MAX_COMPONENT_LENGTH_MM

/** True when [diaMm] exceeds the implausible-diameter sanity ceiling. */
private fun isImplausiblyLargeDia(diaMm: Float): Boolean = diaMm > SANITY_MAX_DIA_MM

/** Positive-overlap length of the two axial spans (≤ 0 when they do not overlap). */
private fun overlapLenMm(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Float =
    minOf(aEnd, bEnd) - maxOf(aStart, bStart)

/** True when either end face of span A lands within [ADJACENCY_EPS_MM] of an end face of span B. */
private fun facesAbut(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Boolean =
    abs(aEnd - bStart) <= ADJACENCY_EPS_MM || abs(bEnd - aStart) <= ADJACENCY_EPS_MM

/* ────────────────────────────────────────────────────────────────────────────
 * Component-scoped warnings (wired into the carousel cards)
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Advisory warnings for a stored [body]. Includes the very-short-segment check, the
 * implausible length/diameter sanity checks (§2.1), the past-OAL bounds advisory, plus §3.2
 * Ø-discontinuity vs an adjacent stored body.
 */
fun bodyWarningMessages(spec: ShaftSpec, body: Body, unit: UnitSystem): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(body.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(body.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(body.diaMm)) out += DIA_SANITY_MSG
    pastShaftEndMessage(spec, body.startFromAftMm, body.lengthMm, unit)?.let { out += it }
    if (hasAdjacentBodyStep(spec, body)) out += "Large Ø step vs adjacent body"
    return out
}

/**
 * Advisory warnings for a stored [taper]: the very-short-segment check, the implausible
 * length/diameter sanity checks (§2.1) — checked against the larger of SET/LET diameter — and
 * the past-OAL bounds advisory.
 *
 * A taper-vs-body Ø mismatch advisory is intentionally not included: the mismatch is
 * already visible in the drawing itself, and the two storage paths (Add dialog vs carousel
 * edit) disagree on SET/LET ordering for FWD tapers, which would make such a check misfire.
 * Do not add one without resolving that discrepancy first (`TODO.md` §2.3).
 */
fun taperWarningMessages(spec: ShaftSpec, taper: Taper, unit: UnitSystem): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(taper.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(taper.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(taper.maxDiaMm)) out += DIA_SANITY_MSG
    pastShaftEndMessage(spec, taper.startFromAftMm, taper.lengthMm, unit)?.let { out += it }
    return out
}

/**
 * Advisory warnings for a stored [liner]. Includes the very-short-segment check, the
 * implausible length/diameter sanity checks (§2.1), the past-OAL bounds advisory, plus §3.5
 * liner OD below the diameter of a body it overlaps.
 */
fun linerWarningMessages(spec: ShaftSpec, liner: Liner, unit: UnitSystem): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(liner.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(liner.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(liner.odMm)) out += DIA_SANITY_MSG
    pastShaftEndMessage(spec, liner.startFromAftMm, liner.lengthMm, unit)?.let { out += it }
    if (linerOdBelowUnderlyingBody(spec, liner)) out += "Liner OD smaller than shaft Ø beneath it"
    return out
}

/**
 * Advisory warnings for a [thread]: zero pitch, very short segment, the implausible
 * length/major-diameter sanity checks (§2.1), the past-OAL bounds advisory, and the two relief
 * advisories (relief Ø at or above the major Ø, relief as long as the thread).
 *
 * An **excluded** thread (`excludeFromOAL`) is skipped by the bounds advisory: it sits outside
 * the shaft envelope by design, at a negative or past-OAL start the resolve layer derives, so
 * flagging it would fire on every correctly authored one.
 */
fun threadWarningMessages(spec: ShaftSpec, thread: Threads, unit: UnitSystem): List<String> {
    val out = mutableListOf<String>()
    if (thread.pitchMm == 0f) out += "Zero pitch — thread renders flat"
    if (isShortSegment(thread.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(thread.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(thread.majorDiaMm)) out += DIA_SANITY_MSG
    if (!thread.excludeFromOAL) {
        pastShaftEndMessage(spec, thread.startFromAftMm, thread.lengthMm, unit)?.let { out += it }
    }
    // Relief advisories: the typed values are kept verbatim and the drawing clamps them where it
    // draws (`geom/ThreadReliefMath.kt`); these say why the groove does not read as typed.
    if (thread.reliefDiaMm > 0f && thread.majorDiaMm > 0f && thread.reliefDiaMm >= thread.majorDiaMm) {
        out += THREAD_RELIEF_DIA_MSG
    }
    if (thread.reliefLenMm > 0f && thread.lengthMm > 0f && thread.reliefLenMm >= thread.lengthMm) {
        out += THREAD_RELIEF_LEN_MSG
    }
    return out
}

/** §3.2 — a stored body adjacent to [body] whose Ø differs by more than [BODY_STEP_WARN_RATIO]. */
private fun hasAdjacentBodyStep(spec: ShaftSpec, body: Body): Boolean {
    if (body.diaMm <= 0f) return false
    val start = body.startFromAftMm
    val end = body.startFromAftMm + body.lengthMm
    return spec.bodies.any { other ->
        other.id != body.id &&
            other.diaMm > 0f &&
            facesAbut(start, end, other.startFromAftMm, other.startFromAftMm + other.lengthMm) &&
            maxOf(body.diaMm, other.diaMm) / minOf(body.diaMm, other.diaMm) > BODY_STEP_WARN_RATIO
    }
}

/** §3.5 — [liner] overlaps a stored body whose Ø is larger than the liner OD. */
private fun linerOdBelowUnderlyingBody(spec: ShaftSpec, liner: Liner): Boolean {
    if (liner.odMm <= 0f) return false
    val start = liner.startFromAftMm
    val end = liner.startFromAftMm + liner.lengthMm
    return spec.bodies.any { body ->
        body.diaMm > 0f &&
            overlapLenMm(start, end, body.startFromAftMm, body.startFromAftMm + body.lengthMm) > 0f &&
            liner.odMm < body.diaMm - 0.001f
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Spec-scoped warnings (§4.3) — `specIssues` in `ui/util/SpecIssues.kt`, read by the Schematic
 * tab's `SpecWarningBanner` and the preview's problem marks through the predicates above.
 * ──────────────────────────────────────────────────────────────────────────── */
