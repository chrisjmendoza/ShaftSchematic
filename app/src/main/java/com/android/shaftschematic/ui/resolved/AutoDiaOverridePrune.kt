package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.showsAutoSectionDia
import com.android.shaftschematic.model.withAutoSectionShowDia
import kotlin.math.abs

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
 * A section's Ø-callout visibility ([ShaftSpec.autoDiaCallouts]) is anchored the same way and
 * dies by the same rule in the same pass — a re-created section follows the shaft's default
 * rather than a switch thrown on the section that was deleted.
 *
 * Returns `this` when nothing is dropped (and for an unchanged spec, so a no-op add/remove
 * never drops an override).
 */
fun ShaftSpec.withAutoDiaOverridesSurviving(before: ShaftSpec): ShaftSpec {
    if ((autoDiaOverrides.isEmpty() && autoDiaCallouts.isEmpty()) || this == before) return this
    val beforeAutos = autoRuns(before)
    val afterAutos = autoRuns(this)
    val kept = autoDiaOverrides.filter { o -> survives(o.anchorMm, beforeAutos, afterAutos) }
    val keptCallouts = autoDiaCallouts.filter { c -> survives(c.anchorMm, beforeAutos, afterAutos) }
    return if (kept.size == autoDiaOverrides.size && keptCallouts.size == autoDiaCallouts.size) this
    else copy(autoDiaOverrides = kept, autoDiaCallouts = keptCallouts)
}

private fun autoRuns(spec: ShaftSpec): List<ResolvedBody> =
    resolveComponents(spec)
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.AUTO }

private fun List<ResolvedBody>.runContaining(anchorMm: Float): ResolvedBody? =
    firstOrNull { anchorMm >= it.startMmPhysical && anchorMm < it.endMmPhysical }

private fun survives(
    anchorMm: Float,
    beforeAutos: List<ResolvedBody>,
    afterAutos: List<ResolvedBody>,
): Boolean {
    val beforeSpan = beforeAutos.runContaining(anchorMm) ?: return false
    val afterSpan = afterAutos.runContaining(anchorMm) ?: return false
    return afterSpan.startMmPhysical >= beforeSpan.startMmPhysical - PRUNE_SPAN_EPS_MM
}

/** The AUTO run of this spec lying on exactly `[startMm, endMm]`, or null. */
private fun ShaftSpec.autoRunOn(startMm: Float, endMm: Float): ResolvedBody? =
    autoRuns(this).firstOrNull {
        abs(it.startMmPhysical - startMm) <= PRUNE_SPAN_EPS_MM &&
            abs(it.endMmPhysical - endMm) <= PRUNE_SPAN_EPS_MM
    }

/**
 * Whether a bare-shaft section lies on exactly `[startMm, endMm]` AND prints its Ø callout —
 * the question a body created on that span asks, so promoting a section ("Explicit body")
 * carries its callout onto the stored body. A span no auto run fills exactly is false: a body
 * added anywhere else starts hidden, the opt-in default.
 */
fun ShaftSpec.autoSectionShowsDiaOn(startMm: Float, endMm: Float): Boolean {
    if (!showAutoBodyDia && autoDiaCallouts.isEmpty()) return false
    val run = autoRunOn(startMm, endMm) ?: return false
    return showsAutoSectionDia(run.startMmPhysical, run.endMmPhysical)
}

/**
 * The inverse carry: this spec is the result of removing [removed], and when a bare-shaft
 * section now fills exactly the body's stored span (a demotion — "Explicit body" unticked)
 * that section takes the body's Ø-callout visibility. A removal whose gap merges into a
 * neighbouring section, or is trimmed by a component over it, carries nothing.
 *
 * Applied AFTER [withAutoDiaOverridesSurviving], which would otherwise drop the fresh anchor
 * as one that had no section before the edit.
 */
fun ShaftSpec.withDemotedBodyDiaCallout(removed: Body): ShaftSpec {
    val run = autoRunOn(removed.startFromAftMm, removed.startFromAftMm + removed.lengthMm)
        ?: return this
    return withAutoSectionShowDia(run.startMmPhysical, run.endMmPhysical, removed.showDiaOnDrawing)
}
