package com.android.shaftschematic.ui.screen

/**
 * ShaftScreenController — add-defaults helpers backing ShaftScreen.
 *
 * Extracted from ShaftScreen.kt. Typed field commits are never snapped to component-edge
 * anchors: that would silently rewrite values the user just entered (e.g. undoing a
 * sub-tolerance taper-length edit). Typed values are exact. Nothing in the editor snaps a
 * position any more — the one coarse gesture that did, tap-to-add, is gone.
 */

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collisionPartnerIdsForSpan
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.resolveComponents

/**
 * Defaults for new components (mm).
 *
 * [bareSpanEndMm] is set only when [startMm] was moved onto a bare-shaft span (see
 * [addStartSeed]): that span's FWD end, so an Add Body can open sized to the gap it fills.
 */
internal data class AddDefaults(
    val startMm: Float,
    val lastDiaMm: Float,
    val bareSpanEndMm: Float? = null,
)

internal fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    // Bodies are fillers; excluded threads sit outside the shaft envelope.
    // Only sacred components (tapers, non-excluded threads, liners) drive the default start position.
    val end = lastComponentEndMm(spec)

    var dia = 50f
    spec.liners.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.odMm }
    spec.threads.filter { !it.excludeFromOAL }.firstOrNull { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.majorDiaMm }
    spec.tapers.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.endDiaMm }
    if (dia == 50f && spec.bodies.isNotEmpty()) dia = spec.bodies.first().diaMm

    val seed = addStartSeed(spec)
    return AddDefaults(startMm = seed.startMm, lastDiaMm = dia, bareSpanEndMm = seed.bareSpanEndMm)
}

/** The end of the last taper, liner or OAL-counted thread, or 0 on a shaft with none (mm). */
private fun lastComponentEndMm(spec: ShaftSpec): Float {
    var end = 0f
    spec.tapers.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.filter { !it.excludeFromOAL }.forEach { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    return end
}

/** Seeds closer than this to the OAL or to a component's edge count as AT it (mm). */
private const val ADD_START_SEED_EPS_MM = 0.01f

/** Where an Add dialog's Start opens, and the bare-shaft span it was moved onto, if any. */
internal data class AddStartSeed(val startMm: Float, val bareSpanEndMm: Float? = null)

/**
 * The Start every Add dialog opens with (mm) — a SEED only; the field is the user's.
 *
 * The end of the last taper, liner or OAL-counted thread. On a shaft whose components already
 * run to the OAL that end IS the OAL, and Add Body opened on a body starting at the FWD face
 * (on-device report). So when the end seed is at or past the OAL, or falls strictly inside a
 * component, the dialog opens instead at the start of the AFT-most bare-shaft (auto) span — the
 * first place on the shaft still free. With no bare shaft left the end seed stands, and a shaft
 * with a trailing gap keeps it unchanged.
 *
 * With [lengthMm] — the length a liner or taper dialog opens with — the seed must also be FREE
 * for that span: when [startMm, startMm + lengthMm] would overlap a taper, OAL-counted thread or
 * liner (the export gate's rule) it moves to the first free span that holds it, counted from the
 * chosen reference end — the AFT-most fit, or with [fromFwd] the FWD-most fit, placed flush
 * against that span's FWD end. A seed that is a guaranteed collision left the dialog opening on
 * an "Overlaps …" error on any shaft that already had a liner or a FWD taper there (emulator QA).
 * With no free span long enough the seed stands, and its overlap shows in the dialog. A FWD seed
 * starts from the FWD face (`OAL − length`, shown as 0 from FWD) before the fit.
 */
internal fun addStartSeed(
    spec: ShaftSpec,
    lengthMm: Float? = null,
    fromFwd: Boolean = false,
): AddStartSeed {
    val base = if (fromFwd && lengthMm != null && spec.overallLengthMm > 0f) {
        AddStartSeed(spec.overallLengthMm - lengthMm)
    } else {
        endSeedOrBareSpan(spec)
    }
    if (lengthMm == null || lengthMm <= 0f) return base
    if (spec.collisionPartnerIdsForSpan(base.startMm, lengthMm).isEmpty()) return base
    val fit = freeSpansMm(spec).filter { (s, e) -> e - s >= lengthMm - ADD_START_SEED_EPS_MM }
        .let { if (fromFwd) it.lastOrNull() else it.firstOrNull() }
        ?: return base
    return AddStartSeed(if (fromFwd) fit.second - lengthMm else fit.first)
}

/**
 * The stretches of [0, OAL] no taper, OAL-counted thread or liner occupies, AFT → FWD — where a
 * new liner or taper can go without colliding (bodies are fillers and do not count). Empty while
 * the shaft has no OAL.
 */
private fun freeSpansMm(spec: ShaftSpec): List<Pair<Float, Float>> {
    val oal = spec.overallLengthMm
    if (oal <= 0f) return emptyList()
    val occupied = (spec.tapers + spec.threads.filter { !it.excludeFromOAL } + spec.liners)
        .map { it.startFromAftMm to it.startFromAftMm + it.lengthMm }
        .sortedBy { it.first }
    val free = mutableListOf<Pair<Float, Float>>()
    var cursor = 0f
    for ((s, e) in occupied) {
        if (s > cursor + ADD_START_SEED_EPS_MM) free += cursor to minOf(s, oal)
        cursor = maxOf(cursor, e)
    }
    if (oal > cursor + ADD_START_SEED_EPS_MM) free += cursor to oal
    return free.filter { (s, e) -> e > s }
}

/** [addStartSeed] without a length: the end seed, or the AFT-most bare span when that is taken. */
private fun endSeedOrBareSpan(spec: ShaftSpec): AddStartSeed {
    val endSeed = lastComponentEndMm(spec)
    val resolved = resolveComponents(spec)
    val pastEnd = spec.overallLengthMm > 0f &&
        endSeed >= spec.overallLengthMm - ADD_START_SEED_EPS_MM
    val inside = resolved.any { c ->
        c.source == ResolvedComponentSource.EXPLICIT &&
            (c is ResolvedBody || c is ResolvedTaper || c is ResolvedThread || c is ResolvedLiner) &&
            endSeed > c.startMmPhysical + ADD_START_SEED_EPS_MM &&
            endSeed < c.endMmPhysical - ADD_START_SEED_EPS_MM
    }
    if (!pastEnd && !inside) return AddStartSeed(endSeed)

    val bare = resolved
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.AUTO }
        .minByOrNull { it.startMmPhysical }
        ?: return AddStartSeed(endSeed)
    return AddStartSeed(bare.startMmPhysical, bare.endMmPhysical)
}

/**
 * The Liner/Taper dialogs' "Start from FWD" seed (mm from the FWD face): [addStartSeed] from the
 * FWD reference end for [lengthMm], expressed as a FWD distance — 0 (flush with the FWD face)
 * unless that span is taken. 0 while the shaft has no OAL.
 */
internal fun addSeedFromFwdMm(spec: ShaftSpec, lengthMm: Float): Float {
    if (spec.overallLengthMm <= 0f || lengthMm <= 0f) return 0f
    val start = addStartSeed(spec, lengthMm, fromFwd = true).startMm
    return (spec.overallLengthMm - start - lengthMm).coerceAtLeast(0f)
}

/** [addStartSeed]'s Start alone — the Add dialogs' own fallback when no Start is passed in. */
internal fun addDialogStartMm(spec: ShaftSpec): Float = addStartSeed(spec).startMm
