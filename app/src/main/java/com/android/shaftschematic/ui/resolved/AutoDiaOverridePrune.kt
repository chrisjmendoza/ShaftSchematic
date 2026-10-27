package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.ShaftSpec

/** Slop on the span-start comparison — the resolve layer's own body epsilon. */
private const val PRUNE_SPAN_EPS_MM = 1e-3f

/**
 * An auto-section Ø override dies with its section: returns a copy of this spec (the result of
 * ONE component add or remove applied to [before]) keeping only the
 * [ShaftSpec.autoDiaOverrides] whose section survived the edit.
 *
 * Per override, `beforeSpan` is the AUTO run of [before] whose `[start, end)` contains the
 * anchor and `afterSpan` the AUTO run of this spec that does. The override is KEPT iff both
 * exist AND `afterSpan.start >= beforeSpan.start` (within [PRUNE_SPAN_EPS_MM]); everything
 * else is dropped:
 * - removing the component AFT of a section merges the section into its AFT neighbour — the
 *   span start moves AFT-ward, so the FWD section is the one that "got deleted" and its Ø goes
 *   (aft is authored first, matching the aft-most-wins merge rule of
 *   [com.android.shaftschematic.model.autoSectionDiaMmFor]);
 * - removing the component FWD of a section leaves its start alone, so it keeps its Ø;
 * - a component added upstream inside the same span trims the section (start moves FWD-ward),
 *   so it keeps its Ø;
 * - a component added OVER the anchor, or an edit that leaves no auto run at the anchor,
 *   drops it;
 * - an override already dormant before the edit is dropped, so it never resurrects through
 *   an add or remove.
 *
 * On-device ruling: a deleted section that comes back without Undo takes the shaft's Ø rather
 * than its old value. Undo still restores it — the history holds the whole spec.
 *
 * Applied ONLY by the ViewModel's component add/remove mutators. A resize, OAL change, or any
 * other edit never prunes: an anchor a slipped neighbour length strands stays dormant and
 * applies again when the geometry gives the span back.
 *
 * Returns `this` when nothing is dropped (and for an unchanged spec, so a no-op add/remove
 * never drops an override).
 */
fun ShaftSpec.withAutoDiaOverridesSurviving(before: ShaftSpec): ShaftSpec {
    if (autoDiaOverrides.isEmpty() || this == before) return this
    val beforeAutos = autoRuns(before)
    val afterAutos = autoRuns(this)
    val kept = autoDiaOverrides.filter { o -> survives(o, beforeAutos, afterAutos) }
    return if (kept.size == autoDiaOverrides.size) this else copy(autoDiaOverrides = kept)
}

private fun autoRuns(spec: ShaftSpec): List<ResolvedBody> =
    resolveComponents(spec)
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.AUTO }

private fun List<ResolvedBody>.runContaining(anchorMm: Float): ResolvedBody? =
    firstOrNull { anchorMm >= it.startMmPhysical && anchorMm < it.endMmPhysical }

private fun survives(
    override: AutoDiaOverride,
    beforeAutos: List<ResolvedBody>,
    afterAutos: List<ResolvedBody>,
): Boolean {
    val beforeSpan = beforeAutos.runContaining(override.anchorMm) ?: return false
    val afterSpan = afterAutos.runContaining(override.anchorMm) ?: return false
    return afterSpan.startMmPhysical >= beforeSpan.startMmPhysical - PRUNE_SPAN_EPS_MM
}
