package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
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

private const val SHORT_SEGMENT_MSG = "Very short segment (< 1 mm)"

/** True for a positive length at or below the short-segment threshold (0 excluded). */
private fun isShortSegment(lengthMm: Float): Boolean =
    lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM

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
 * Advisory warnings for a stored [body]. Includes the very-short-segment check plus
 * §3.2 Ø-discontinuity vs an adjacent stored body.
 */
fun bodyWarningMessages(spec: ShaftSpec, body: Body): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(body.lengthMm)) out += SHORT_SEGMENT_MSG
    if (hasAdjacentBodyStep(spec, body)) out += "Large Ø step vs adjacent body"
    return out
}

/**
 * Advisory warnings for a stored [taper] — currently only the very-short-segment check.
 *
 * The former §3.3 taper-vs-body Ø mismatch advisory was removed (2026-07-26) by user
 * request: the mismatch is visible in the drawing itself, and the rule kept misfiring on
 * FWD tapers because the two storage paths (Add dialog vs carousel edit) disagree on
 * SET/LET ordering. Do not reintroduce without resolving that discrepancy first
 * (`TODO.md` §2.3).
 */
fun taperWarningMessages(spec: ShaftSpec, taper: Taper): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(taper.lengthMm)) out += SHORT_SEGMENT_MSG
    return out
}

/**
 * Advisory warnings for a stored [liner]. Includes the very-short-segment check plus
 * §3.5 liner OD below the diameter of a body it overlaps.
 */
fun linerWarningMessages(spec: ShaftSpec, liner: Liner): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(liner.lengthMm)) out += SHORT_SEGMENT_MSG
    if (linerOdBelowUnderlyingBody(spec, liner)) out += "Liner OD smaller than shaft Ø beneath it"
    return out
}

/** Advisory warning for a [thread] — zero pitch or very short segment. Unchanged; no spec context. */
fun threadWarningMessage(thread: Threads): String? {
    if (thread.pitchMm == 0f) return "Zero pitch — thread renders flat"
    if (isShortSegment(thread.lengthMm)) return SHORT_SEGMENT_MSG
    return null
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
 * Spec-scoped warnings (§4.3 — computed + tested; not yet wired to any UI surface)
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Whole-spec advisory warnings (§4.3):
 * - a count of tiny segments (length in (0, [SHORT_SEGMENT_MM]] mm) across bodies, tapers,
 *   liners, and non-excluded threads;
 * - a "no explicit bodies" note when the spec has no stored bodies yet has at least one
 *   taper/liner/non-excluded thread (auto-fill covers the body region).
 *
 * Pure — not consulted for export gating and not (yet) surfaced in the UI.
 */
fun specWarningMessages(spec: ShaftSpec): List<String> {
    val out = mutableListOf<String>()
    val tiny = countTinySegments(spec)
    if (tiny > 0) out += "$tiny segments shorter than 1 mm"
    if (spec.bodies.isEmpty() && hasAnyNonBodyComponent(spec)) {
        out += "No explicit bodies — shaft body is all auto-fill"
    }
    return out
}

/** Count of stored segments with length in (0, [SHORT_SEGMENT_MM]] mm. Excluded threads skipped. */
private fun countTinySegments(spec: ShaftSpec): Int {
    var n = 0
    spec.bodies.forEach { if (isShortSegment(it.lengthMm)) n++ }
    spec.tapers.forEach { if (isShortSegment(it.lengthMm)) n++ }
    spec.liners.forEach { if (isShortSegment(it.lengthMm)) n++ }
    spec.threads.forEach { if (!it.excludeFromOAL && isShortSegment(it.lengthMm)) n++ }
    return n
}

/** True when at least one taper, liner, or non-excluded thread is present. */
private fun hasAnyNonBodyComponent(spec: ShaftSpec): Boolean =
    spec.tapers.isNotEmpty() ||
        spec.liners.isNotEmpty() ||
        spec.threads.any { !it.excludeFromOAL }
