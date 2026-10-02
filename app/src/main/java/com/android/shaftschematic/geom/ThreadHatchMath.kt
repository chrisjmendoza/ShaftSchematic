// file: app/src/main/java/com/android/shaftschematic/geom/ThreadHatchMath.kt
package com.android.shaftschematic.geom

import kotlin.math.min

/*
 * Thread-hatch geometry — the ONE stroke slant and spacing behind every thread hatch, canvas
 * and PDF alike (`ShaftRenderer.drawThreadHatch`, `pdf/SimpleShaftProfile.drawThreadHatch`, and
 * both thread-end stub hatches).
 *
 * A thread seen from the side shows its crests as steep lines leaning a little off the
 * vertical; a 45° hatch is the drafting glyph for cut material and read as a section cut
 * rather than a thread (on-device request). The lean comes from the thread's OWN geometry: a
 * crest drifts half a pitch axially while it crosses the whole major diameter, so its true
 * run/rise is `(pitch / 2) / majorDia`. True geometry is nearly vertical on shaft threads
 * (87°–89.5°), so a user-set slant factor ([THREAD_SLANT_MIN]..[THREAD_SLANT_MAX], Settings →
 * Drawing → "Thread slant" and both PDF options sheets — `PdfPrefs.threadSlant`) multiplies it
 * into a readable lean (on-device request: base the angle on the actual pitch, with a slider).
 *
 * The lean is DIMENSIONLESS and taken from the model, never from a drawn scale, so one thread
 * stands at the same angle on every sheet and on the preview whatever its axial compression.
 */

/** Slant factor at true thread geometry — the slider's low end. */
const val THREAD_SLANT_MIN = 1f

/** Highest slant factor the slider offers. */
const val THREAD_SLANT_MAX = 10f

/** Slider step for the slant factor. */
const val THREAD_SLANT_STEP = 0.5f

/**
 * Shipped slant factor. At 3× a 4 TPI thread on a 4.5″ diameter stands at ≈ 85.2° to the axis —
 * the angle picked on-device from a fixed-slant preview before the lean was tied to the pitch.
 */
const val THREAD_SLANT_DEFAULT = 3f

/**
 * Pitch a thread with no stored pitch is hatched at (≈ 10 TPI) — both its stroke spacing
 * ([threadHatchSpacing]) and its lean ([threadHatchLean]).
 */
const val THREAD_HATCH_FALLBACK_PITCH_MM = 2.5f

/** Cap on the lean: tan 30°, so a stroke never lies flatter than 60° to the shaft axis. */
const val THREAD_HATCH_MAX_LEAN = 0.57735026f

/**
 * Reference thread (4 TPI on a 4.5″ major diameter) for a hatch whose thread carries no
 * diameter — [threadHatchReferenceLean].
 */
const val THREAD_HATCH_REFERENCE_PITCH_MM = 25.4f / 4f
const val THREAD_HATCH_REFERENCE_DIA_MM = 4.5f * 25.4f

/** Hatch stroke spacing bounds, in the draw site's own units (pt or px). */
const val THREAD_HATCH_SPACING_MIN = 4f
const val THREAD_HATCH_SPACING_MAX = 18f

/** A stored slant factor made safe to draw with: non-finite → default, then clamped to range. */
fun sanitizeThreadSlant(slant: Float): Float =
    if (!slant.isFinite()) THREAD_SLANT_DEFAULT else slant.coerceIn(THREAD_SLANT_MIN, THREAD_SLANT_MAX)

/**
 * Run/rise of one hatch stroke for a thread of [pitchMm] on [majorDiaMm], at slant factor
 * [slant]: `slant × (pitch / 2) / majorDia`, capped at [THREAD_HATCH_MAX_LEAN] so a small coarse
 * thread at a high factor never falls back toward the 45° section-cut glyph.
 *
 * A pitch ≤ 0 (legacy saves) leans at [THREAD_HATCH_FALLBACK_PITCH_MM]; a diameter ≤ 0 has no
 * true lean, so it takes the reference thread's lean ([threadHatchReferenceLean]) at [slant].
 */
fun threadHatchLean(pitchMm: Float, majorDiaMm: Float, slant: Float): Float {
    if (!(majorDiaMm > 0f)) return threadHatchReferenceLean(slant)
    val pitch = if (pitchMm > 0f) pitchMm else THREAD_HATCH_FALLBACK_PITCH_MM
    return min(THREAD_HATCH_MAX_LEAN, sanitizeThreadSlant(slant) * (pitch / 2f) / majorDiaMm)
}

/** The reference thread's lean ([THREAD_HATCH_REFERENCE_PITCH_MM] on [THREAD_HATCH_REFERENCE_DIA_MM]). */
fun threadHatchReferenceLean(slant: Float): Float =
    threadHatchLean(THREAD_HATCH_REFERENCE_PITCH_MM, THREAD_HATCH_REFERENCE_DIA_MM, slant)

/** Axial drift of one hatch stroke across a band [bandH] tall at [lean] — the stroke's run for its rise. */
fun threadHatchRun(bandH: Float, lean: Float): Float = bandH * lean

/**
 * Stroke spacing for a thread of [pitchMm] drawn at [unitsPerMm]: the thread's own pitch
 * (fallback [THREAD_HATCH_FALLBACK_PITCH_MM]) capped to
 * [THREAD_HATCH_SPACING_MIN]..[THREAD_HATCH_SPACING_MAX] — the ONE recipe every to-scale thread
 * hatch spaces its strokes by.
 */
fun threadHatchSpacing(pitchMm: Float?, unitsPerMm: Float): Float =
    ((pitchMm?.takeIf { it > 0f } ?: THREAD_HATCH_FALLBACK_PITCH_MM) * unitsPerMm)
        .coerceIn(THREAD_HATCH_SPACING_MIN, THREAD_HATCH_SPACING_MAX)

