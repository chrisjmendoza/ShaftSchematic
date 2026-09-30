package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * A blended face on ONE auto-body span — the bare shaft between explicit components can step
 * to its neighbour through a machined curve without being promoted to an explicit [Body].
 *
 * ## Why this exists rather than "promote it first"
 * Promoting pins a span's start and length. A saved layout whose liner positions, coupling
 * length or overall length later move then grows a fresh auto gap beside the promoted body,
 * and the blend that was authored against the old boundary describes a face that has shifted
 * (on-device: a template with seal areas where the liners and the shaft both change). An auto
 * span re-derives its extent from whatever surrounds it, so a blend anchored to the SPAN
 * survives the edit that promotion would not.
 *
 * ## Coordinate rule
 * Keyed in **shaft-space** by [anchorMm], the [AutoDiaOverride] posture: auto spans have no
 * stored row and their ids are position-derived (`auto_body_<start>_<end>`), so they
 * regenerate on every edit. An auto span whose extent contains the anchor — the half-open
 * interval `[startMm, endMm)` — blends the face named by [end].
 *
 * ## Dormancy — nothing is ever pruned
 * A blend whose anchor lands inside a component is **dormant**: not drawn, but kept. (An
 * explicit body never absorbs the auto gap beside it, so a gap's anchors stay live for as
 * long as the gap exists.) No orphans by construction and
 * nothing pruned at decode — the rule shared with runout readings, wear pits and
 * [AutoDiaOverride]. A dormant blend resurrects unchanged when its span reappears.
 *
 * ## Merge rule
 * When several anchors for the SAME [end] fall inside one span the **aft-most wins** and the
 * rest stay dormant, matching [AutoDiaOverride]: aft is authored first. The two ends are
 * independent, so one span can carry both an aft and a fwd blend.
 *
 * [lengthMm] is user-typed and stored **verbatim** (golden rule); the draw sites clamp it.
 * [anchorMm] is system-placed — the midpoint of the span the value was committed on — and
 * carries no authored meaning.
 *
 * Draw-only: it changes a span's drawn silhouette and nothing else. Auto-span positioning,
 * OAL/coverage, resolve, and collision are untouched.
 *
 * Units: mm.
 */
@Serializable
data class AutoBlend(
    /** Shaft-space mm from the AFT face; the span containing it blends [end]. */
    val anchorMm: Float = 0f,
    /** Which face of that span curves into its neighbour. */
    val end: LinerAuthoredReference = LinerAuthoredReference.AFT,
    /** Axial length of the curve, canonical mm, stored verbatim. */
    val lengthMm: Float = 0f,
    /** How the curve eases; drawing-only, like [lengthMm]. */
    val profile: BlendProfile = BlendProfile.OGEE,
    /**
     * Whether this face carries a seal area — radius cuts measured from the face, in the same
     * place whether [lengthMm] is 0 (a square face) or not. Independent of [lengthMm]: a seal
     * area is a section property, not a face finish.
     */
    val seal: Boolean = false,
    /**
     * Axial length of the seal area (the grooved span, measured from the face), canonical mm,
     * stored verbatim. `0` = follow [lengthMm], which is what every anchor saved before the
     * field existed carries.
     */
    val sealLenMm: Float = 0f,
)

/**
 * The blend that applies to face [end] of the auto span `[startMm, endMm)`, or null when none
 * anchors inside it. Aft-most anchor wins; the rest stay dormant. An anchor with a
 * non-positive [AutoBlend.lengthMm] counts only when it carries a seal (a seal area on a square
 * face); one with neither is ignored — a cleared face is a removal, so such a value only
 * reaches here from a hand-edited document.
 */
fun List<AutoBlend>.autoBlendFor(
    startMm: Float,
    endMm: Float,
    end: LinerAuthoredReference,
): AutoBlend? =
    asSequence()
        .filter {
            it.end == end && (it.lengthMm > 0f || it.seal) &&
                it.anchorMm >= startMm && it.anchorMm < endMm
        }
        .minByOrNull { it.anchorMm }

/**
 * Returns a copy carrying [lengthMm], [profile] and the seal pair ([seal], [sealLenMm]) on face
 * [end] of the auto span `[spanStartMm, spanEndMm)`.
 *
 * Upsert: every blend for that face anchored inside the span is dropped, then the value is
 * stored **verbatim** against a fresh anchor at the span midpoint. [lengthMm] ≤ 0 with
 * [seal] = false is a clear; a seal on a square face stores an anchor with `lengthMm = 0`.
 * The other face, and anchors outside the span — including dormant ones — are left alone, so
 * they resurrect unchanged if their span reappears. Returns `this` when nothing moves, so a
 * no-op set never emits new state or marks the document dirty.
 */
fun ShaftSpec.withAutoBlend(
    spanStartMm: Float,
    spanEndMm: Float,
    end: LinerAuthoredReference,
    lengthMm: Float,
    profile: BlendProfile = BlendProfile.OGEE,
    seal: Boolean = false,
    sealLenMm: Float = 0f,
): ShaftSpec {
    val kept = autoBlends.filterNot {
        it.end == end && it.anchorMm >= spanStartMm && it.anchorMm < spanEndMm
    }
    val next =
        if (lengthMm > 0f || seal) kept + AutoBlend(
            anchorMm = (spanStartMm + spanEndMm) / 2f,
            end = end,
            lengthMm = lengthMm.coerceAtLeast(0f),
            profile = profile,
            seal = seal,
            sealLenMm = sealLenMm.coerceAtLeast(0f),
        )
        else kept
    return if (next == autoBlends) this else copy(autoBlends = next)
}
