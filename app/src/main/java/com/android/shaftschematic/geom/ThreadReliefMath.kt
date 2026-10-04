// file: app/src/main/java/com/android/shaftschematic/geom/ThreadReliefMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Threads

/*
 * Thread-relief geometry — the ONE split of a thread's stored span into its hatched thread and
 * its relief groove, behind every thread draw site (the preview canvas, the schematic, the
 * runout/consolidated sheet, the wear/undercut simple profile and the undercut detail strips)
 * and the surface envelope (`ui/resolved/SurfaceSegs.kt`, the undercut composer's spec fallback).
 *
 * A relief is the plain neck turned below the thread's root at the end where the thread runs up
 * to its shoulder (on a propeller shaft, the taper's S.E.T.). It is PART of the thread's stored
 * span, so nothing moves when one is added. The stored values are never rewritten: an authored
 * length longer than the thread, or a diameter at or above the major diameter, is clamped here,
 * where it is DRAWN, and flagged by an advisory on the card.
 *
 * Everything is in shaft-space millimeters and pure (no Android/Compose), unit-tested on a plain
 * JVM (`ThreadReliefMathTest`).
 */

/**
 * Fraction of the major diameter a relief draws at when its stored diameter is at or above the
 * major diameter — an invalid value the card flags; the groove still reads as a groove.
 */
const val THREAD_RELIEF_INVALID_DIA_FRAC = 0.9f

/** The relief groove of one thread, in shaft space: `[startMm, endMm]` at [diaMm]. */
data class ThreadReliefSpan(val startMm: Float, val endMm: Float, val diaMm: Float)

/** The hatched (threaded) part of one thread's span, in shaft space. */
data class ThreadHatchSpan(val startMm: Float, val endMm: Float)

/**
 * The drawn relief of a thread spanning `[startFromAftMm, startFromAftMm + lengthMm]` on
 * [majorDiaMm], or null when it has none (a relief exists exactly when [reliefLenMm] and
 * [reliefDiaMm] are both > 0) or the thread has no length or diameter to carry one.
 *
 * Drawn clamps, values never rewritten: the length is clamped to the thread's length, and a
 * diameter at or above [majorDiaMm] draws at [THREAD_RELIEF_INVALID_DIA_FRAC] of it. At
 * [reliefAtAft] the span is `[start, start + len]`, otherwise `[end − len, end]`.
 */
fun threadReliefSpan(
    startFromAftMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    reliefLenMm: Float,
    reliefDiaMm: Float,
    reliefAtAft: Boolean,
): ThreadReliefSpan? {
    if (!(reliefLenMm > 0f) || !(reliefDiaMm > 0f)) return null
    if (!(majorDiaMm > 0f) || !(lengthMm > 0f)) return null
    val len = reliefLenMm.coerceAtMost(lengthMm)
    val dia = if (reliefDiaMm < majorDiaMm) reliefDiaMm else majorDiaMm * THREAD_RELIEF_INVALID_DIA_FRAC
    val end = startFromAftMm + lengthMm
    return if (reliefAtAft) ThreadReliefSpan(startFromAftMm, startFromAftMm + len, dia)
    else ThreadReliefSpan(end - len, end, dia)
}

/**
 * The hatched part of the same thread: its whole span minus [threadReliefSpan]. The whole span
 * when there is no relief; null when the thread has no length, or when the relief takes the
 * whole thread — then nothing hatches and only the relief draws.
 */
fun threadHatchSpan(
    startFromAftMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    reliefLenMm: Float,
    reliefDiaMm: Float,
    reliefAtAft: Boolean,
): ThreadHatchSpan? {
    if (!(lengthMm > 0f)) return null
    val end = startFromAftMm + lengthMm
    val relief = threadReliefSpan(startFromAftMm, lengthMm, majorDiaMm, reliefLenMm, reliefDiaMm, reliefAtAft)
        ?: return ThreadHatchSpan(startFromAftMm, end)
    val span = if (reliefAtAft) ThreadHatchSpan(relief.endMm, end)
    else ThreadHatchSpan(startFromAftMm, relief.startMm)
    return span.takeIf { it.endMm > it.startMm }
}

/**
 * Where the relief meets its shoulder — the thread's own end at the relief side. The shoulder
 * (the neighbouring body or taper) draws its own larger face there; the thread's face at this
 * end spans the relief diameter only.
 */
fun ThreadReliefSpan.shoulderEndMm(reliefAtAft: Boolean): Float = if (reliefAtAft) startMm else endMm

/** [threadReliefSpan] of a stored thread. */
fun Threads.reliefSpan(): ThreadReliefSpan? =
    threadReliefSpan(startFromAftMm, lengthMm, majorDiaMm, reliefLenMm, reliefDiaMm, reliefAtAft)

/** [threadHatchSpan] of a stored thread. */
fun Threads.hatchSpan(): ThreadHatchSpan? =
    threadHatchSpan(startFromAftMm, lengthMm, majorDiaMm, reliefLenMm, reliefDiaMm, reliefAtAft)

/**
 * One thread's outer-surface contribution:the major-Ø envelope over the hatched span and the
 * relief diameter over the groove — at TRUE stored mm, clamped exactly as drawn — so an undercut
 * or a wear reading landing in the groove sees the diameter actually there.
 */
fun threadSurfaceSegs(
    startFromAftMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    reliefLenMm: Float,
    reliefDiaMm: Float,
    reliefAtAft: Boolean,
): List<SurfaceSeg> {
    val relief = threadReliefSpan(startFromAftMm, lengthMm, majorDiaMm, reliefLenMm, reliefDiaMm, reliefAtAft)
        ?: return listOf(SurfaceSeg(startFromAftMm, startFromAftMm + lengthMm, majorDiaMm, majorDiaMm))
    val hatch = threadHatchSpan(startFromAftMm, lengthMm, majorDiaMm, reliefLenMm, reliefDiaMm, reliefAtAft)
    return buildList {
        if (reliefAtAft) add(SurfaceSeg(relief.startMm, relief.endMm, relief.diaMm, relief.diaMm))
        hatch?.let { add(SurfaceSeg(it.startMm, it.endMm, majorDiaMm, majorDiaMm)) }
        if (!reliefAtAft) add(SurfaceSeg(relief.startMm, relief.endMm, relief.diaMm, relief.diaMm))
    }
}
