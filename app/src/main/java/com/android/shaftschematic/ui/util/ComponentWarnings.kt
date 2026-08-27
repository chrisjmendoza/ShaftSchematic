package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.maxDiaMm
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

private const val SHORT_SEGMENT_MSG = "Very short segment (< 1 mm)"
private const val LENGTH_SANITY_MSG = "Length exceeds 15 m — check for a typo"
private const val DIA_SANITY_MSG = "Diameter exceeds 1 m — check for a typo"

/** True for a positive length at or below the short-segment threshold (0 excluded). */
private fun isShortSegment(lengthMm: Float): Boolean =
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
 * implausible length/diameter sanity checks (§2.1), plus §3.2 Ø-discontinuity vs an
 * adjacent stored body.
 */
fun bodyWarningMessages(spec: ShaftSpec, body: Body): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(body.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(body.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(body.diaMm)) out += DIA_SANITY_MSG
    if (hasAdjacentBodyStep(spec, body)) out += "Large Ø step vs adjacent body"
    return out
}

/**
 * Advisory warnings for a stored [taper]: the very-short-segment check plus the implausible
 * length/diameter sanity checks (§2.1), checked against the larger of SET/LET diameter.
 *
 * A taper-vs-body Ø mismatch advisory is intentionally not included: the mismatch is
 * already visible in the drawing itself, and the two storage paths (Add dialog vs carousel
 * edit) disagree on SET/LET ordering for FWD tapers, which would make such a check misfire.
 * Do not add one without resolving that discrepancy first (`TODO.md` §2.3).
 */
fun taperWarningMessages(spec: ShaftSpec, taper: Taper): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(taper.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(taper.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(taper.maxDiaMm)) out += DIA_SANITY_MSG
    return out
}

/**
 * Advisory warnings for a stored [liner]. Includes the very-short-segment check, the
 * implausible length/diameter sanity checks (§2.1), plus §3.5 liner OD below the diameter
 * of a body it overlaps.
 */
fun linerWarningMessages(spec: ShaftSpec, liner: Liner): List<String> {
    val out = mutableListOf<String>()
    if (isShortSegment(liner.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(liner.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(liner.odMm)) out += DIA_SANITY_MSG
    if (linerOdBelowUnderlyingBody(spec, liner)) out += "Liner OD smaller than shaft Ø beneath it"
    return out
}

/**
 * Advisory warnings for a [thread]: zero pitch, very short segment, or the implausible
 * length/major-diameter sanity checks (§2.1). No spec context needed.
 */
fun threadWarningMessages(thread: Threads): List<String> {
    val out = mutableListOf<String>()
    if (thread.pitchMm == 0f) out += "Zero pitch — thread renders flat"
    if (isShortSegment(thread.lengthMm)) out += SHORT_SEGMENT_MSG
    if (isImplausiblyLong(thread.lengthMm)) out += LENGTH_SANITY_MSG
    if (isImplausiblyLargeDia(thread.majorDiaMm)) out += DIA_SANITY_MSG
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
 * Spec-scoped warnings (§4.3 — computed + tested; not yet wired to any UI surface)
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Whole-spec advisory warnings (§4.3): a count of tiny segments (length in
 * (0, [SHORT_SEGMENT_MM]] mm) across bodies, tapers, liners, and non-excluded threads.
 *
 * **Every message here must be a PROBLEM the user can act on.** The banner that surfaces these
 * is styled as an advisory, so anything routed through it reads as something being wrong; a line
 * describing normal behaviour spends that attention for nothing and makes the real anomalies
 * easier to ignore. A "no explicit bodies" note lived here and was removed on that ground —
 * auto-fill IS the design, not a deviation from it, the carousel card underneath already reads
 * "Body (auto)", and the note fired on every perfectly ordinary taper-and-liner shaft
 * (on-device report: it "looks like an error message rather than a notice").
 *
 * Pure — not consulted for export gating.
 */
fun specWarningMessages(spec: ShaftSpec): List<String> {
    val out = mutableListOf<String>()
    val tiny = countTinySegments(spec)
    if (tiny > 0) out += "$tiny segments shorter than 1 mm"
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
