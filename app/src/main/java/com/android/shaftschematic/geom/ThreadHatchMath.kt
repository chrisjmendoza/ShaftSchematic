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
 * The spacing follows the same posture: the thread's true crest spacing at the drawing's OWN
 * diametral scale (`bandH × pitch / majorDia`, [threadHatchTrueSpacing]), thinned by a user-set
 * density ([THREAD_DENSITY_MIN]..[THREAD_DENSITY_MAX], "Thread density" beside the slant —
 * `PdfPrefs.threadDensity`; on-device request: the angle got a slider, so should the density).
 *
 * Both are DIMENSIONLESS and taken from the model and the drawn thread height, never from a
 * site's axial scale, so one thread stands at the same angle and spacing on every sheet and on
 * the preview whatever its axial compression.
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

/** Sparsest density the slider offers: 15% of the thread's true crests. */
const val THREAD_DENSITY_MIN = 0.15f

/** Every crest at true pitch — the slider's high end. */
const val THREAD_DENSITY_MAX = 1f

/** Slider step for the density (5%). */
const val THREAD_DENSITY_STEP = 0.05f

/**
 * Shipped density: every other crest. At 50% a 4 TPI thread on a 4.5″ diameter at the default
 * sheet height (8″ → 1″, so the thread draws ≈ 40.5 pt tall) spaces its strokes ≈ 4.5 pt apart —
 * close to the fixed 4 pt nearly every hatch printed at before density was a setting.
 */
const val THREAD_DENSITY_DEFAULT = 0.5f

/**
 * Pitch a thread with no stored pitch is hatched at (≈ 10 TPI) — both its stroke spacing
 * ([threadHatchSpacing]) and its lean ([threadHatchLean]).
 */
const val THREAD_HATCH_FALLBACK_PITCH_MM = 2.5f

/** Cap on the lean: tan 30°, so a stroke never lies flatter than 60° to the shaft axis. */
const val THREAD_HATCH_MAX_LEAN = 0.57735026f

/**
 * Reference thread (4 TPI on a 4.5″ major diameter) for a hatch whose thread carries no
 * diameter, or a stub with no thread to read — [threadHatchReferenceLean],
 * [threadHatchReferenceSpacing].
 */
const val THREAD_HATCH_REFERENCE_PITCH_MM = 25.4f / 4f
const val THREAD_HATCH_REFERENCE_DIA_MM = 4.5f * 25.4f

/**
 * Hatch stroke spacing bounds, in the draw site's own units (pt or px). Strokes are ≈ 0.7 units
 * wide, so the 3-unit floor keeps ≥ ~2 units of daylight between them; any tighter and the hatch
 * prints as a grey tone instead of lines.
 */
const val THREAD_HATCH_SPACING_MIN = 3f
const val THREAD_HATCH_SPACING_MAX = 18f

/** A stored slant factor made safe to draw with: non-finite → default, then clamped to range. */
fun sanitizeThreadSlant(slant: Float): Float =
    if (!slant.isFinite()) THREAD_SLANT_DEFAULT else slant.coerceIn(THREAD_SLANT_MIN, THREAD_SLANT_MAX)

/** A stored density made safe to draw with: non-finite → default, then clamped to range. */
fun sanitizeThreadDensity(density: Float): Float =
    if (!density.isFinite()) THREAD_DENSITY_DEFAULT
    else density.coerceIn(THREAD_DENSITY_MIN, THREAD_DENSITY_MAX)

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
 * The thread's true crest spacing, unclamped, in the units [bandH] is drawn in: one pitch at the
 * drawing's OWN diametral scale, `bandH × pitch / majorDia`, [bandH] being the thread's drawn
 * height, top to bottom. Taking the scale from the drawn height rather than from a site's
 * pt-per-mm makes it the same on every sheet and canvas for the same thread at the same height.
 *
 * Same fallbacks as [threadHatchLean]: a pitch ≤ 0 spaces at [THREAD_HATCH_FALLBACK_PITCH_MM],
 * a diameter ≤ 0 takes the reference thread's pitch over its diameter.
 */
fun threadHatchTrueSpacing(bandH: Float, pitchMm: Float, majorDiaMm: Float): Float {
    if (!(majorDiaMm > 0f)) {
        return bandH * THREAD_HATCH_REFERENCE_PITCH_MM / THREAD_HATCH_REFERENCE_DIA_MM
    }
    val pitch = if (pitchMm > 0f) pitchMm else THREAD_HATCH_FALLBACK_PITCH_MM
    return bandH * pitch / majorDiaMm
}

/**
 * Stroke spacing for a thread of [pitchMm] on [majorDiaMm] drawn [bandH] tall, at [density] —
 * the ONE recipe every thread hatch spaces its strokes by:
 * `threadHatchTrueSpacing / density`, clamped to
 * [THREAD_HATCH_SPACING_MIN]..[THREAD_HATCH_SPACING_MAX] in the draw site's own units.
 *
 * [density] is the fraction of the thread's true crests drawn: 1 (100%) draws every crest at
 * true pitch, 0.5 every other one. True density only shows as separate lines on COARSE threads:
 * a 4 TPI thread on 4.5″ at the default sheet height has its crests only ≈ 2.25 pt apart, so at
 * 100% even it sits on the floor, and a fine thread sits on the 3-unit floor whatever the
 * setting. At the shipped [THREAD_DENSITY_DEFAULT] that 4 TPI thread spaces ≈ 4.5 pt.
 */
fun threadHatchSpacing(bandH: Float, pitchMm: Float, majorDiaMm: Float, density: Float): Float =
    (threadHatchTrueSpacing(bandH, pitchMm, majorDiaMm) / sanitizeThreadDensity(density))
        .coerceIn(THREAD_HATCH_SPACING_MIN, THREAD_HATCH_SPACING_MAX)

/** The reference thread's spacing at [bandH] and [density] — for a stub with no thread to read. */
fun threadHatchReferenceSpacing(bandH: Float, density: Float): Float =
    threadHatchSpacing(bandH, THREAD_HATCH_REFERENCE_PITCH_MM, THREAD_HATCH_REFERENCE_DIA_MM, density)
