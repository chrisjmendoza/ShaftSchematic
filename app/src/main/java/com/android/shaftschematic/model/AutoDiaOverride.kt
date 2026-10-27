package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * A per-section diameter for ONE auto-body span — the bare-shaft stock between explicit
 * components does not have to be a single diameter, so an individual auto section can carry
 * its own Ø without being promoted to an explicit [Body].
 *
 * ## Coordinate rule
 * Keyed in **shaft-space** by [anchorMm], the [Undercut]/[WornSection] posture rather than a
 * component id: auto spans have no stored row and their ids are position-derived
 * (`auto_body_<start>_<end>`), so they regenerate on every edit. An auto span whose extent
 * contains the anchor — the half-open interval `[startMm, endMm)` — draws at [diaMm].
 *
 * ## Dormancy and pruning
 * An override whose anchor lands inside a component is **dormant**: not applied, but kept in
 * the list. A resize that strands an anchor (a liner lengthened over it, an OAL change) leaves
 * it dormant, and it applies again when the geometry gives the span back — a typed value
 * survives a slipped neighbour length. (An explicit body never absorbs the auto gap beside it,
 * so a gap's anchors stay live for as long as the gap exists.) Nothing is pruned at decode.
 *
 * A component **add or remove** is different: an override dies with its section. When the
 * edit merges the section into its AFT neighbour (the component AFT of it was removed), covers
 * the anchor with the new component, or leaves no auto span at the anchor, the override is
 * dropped — the section that "got deleted" takes the shaft's Ø when it comes back, rather than
 * resurrecting a value from before the delete (on-device ruling). An override already dormant
 * before the add/remove is dropped by it too. Undo restores the dropped override with the
 * rest of the document. The rule lives in `withAutoDiaOverridesSurviving`
 * (`ui/resolved/AutoDiaOverridePrune.kt`), applied by the ViewModel's add/remove mutators only.
 *
 * ## Merge rule
 * When several anchors fall inside one span the **aft-most wins** and the rest stay dormant
 * (hand-edited or legacy data; the add/remove seam leaves at most the aft section's anchor
 * after a merge). Aft is authored first, so a joined run takes the Ø of the more aftward
 * section.
 *
 * [diaMm] is a user-typed value stored **verbatim** (golden rule: authored values are never
 * rounded, snapped, or derived). [anchorMm] is system-placed — the midpoint of the span the
 * value was committed on — and carries no authored meaning.
 *
 * Draw-only: an override changes a span's drawn diameter and nothing else. Auto-span
 * positioning, OAL/coverage, and collision are untouched.
 *
 * Units: mm.
 */
@Serializable
data class AutoDiaOverride(
    /** Shaft-space mm from the AFT face; the span containing it takes [diaMm]. */
    val anchorMm: Float = 0f,
    /** The section's diameter, canonical mm, stored verbatim. */
    val diaMm: Float = 0f,
)

/**
 * The section diameter that applies to the auto span `[startMm, endMm)`, or null when no
 * override anchors inside it (caller falls back to the global bare-shaft Ø, then to neighbor
 * derivation).
 *
 * Aft-most anchor wins; every other anchor in the span stays dormant. Overrides with a
 * non-positive [AutoDiaOverride.diaMm] are ignored — a cleared section is a removal, so such a
 * value only reaches here from a hand-edited document.
 */
fun List<AutoDiaOverride>.autoSectionDiaMmFor(startMm: Float, endMm: Float): Float? =
    asSequence()
        .filter { it.diaMm > 0f && it.anchorMm >= startMm && it.anchorMm < endMm }
        .minByOrNull { it.anchorMm }
        ?.diaMm

/** Whether any override anchors inside the auto span `[startMm, endMm)`. */
fun List<AutoDiaOverride>.hasAutoSectionDia(startMm: Float, endMm: Float): Boolean =
    autoSectionDiaMmFor(startMm, endMm) != null

/**
 * Returns a copy carrying [valueMm] as the diameter of the auto span
 * `[spanStartMm, spanEndMm)`.
 *
 * Upsert: every override anchored inside the span is dropped, then [valueMm] is stored
 * **verbatim** (golden rule — never rounded or snapped) against a fresh anchor at the span
 * midpoint. [valueMm] ≤ 0 is a clear: removal only, so the section falls back to
 * [ShaftSpec.autoBodyDiaMm] and then to neighbor derivation.
 *
 * Overrides anchored outside the span — including dormant ones under a component — are left
 * alone. Returns `this` when nothing moves, so a no-op set never emits new state or marks the
 * document dirty.
 */
fun ShaftSpec.withAutoSectionDia(
    spanStartMm: Float,
    spanEndMm: Float,
    valueMm: Float
): ShaftSpec {
    val kept = autoDiaOverrides.filterNot { it.anchorMm >= spanStartMm && it.anchorMm < spanEndMm }
    val next =
        if (valueMm > 0f) kept + AutoDiaOverride(
            anchorMm = (spanStartMm + spanEndMm) / 2f,
            diaMm = valueMm
        )
        else kept
    return if (next == autoDiaOverrides) this else copy(autoDiaOverrides = next)
}

/**
 * Returns a copy carrying [valueMm] as the bare-shaft Ø of EVERY auto section together — the
 * auto-body card's "All auto sections" choice, and the new-shaft diameter prompt.
 *
 * [valueMm] is stored **verbatim** in [ShaftSpec.autoBodyDiaMm] (golden rule) and every
 * [ShaftSpec.autoDiaOverrides] entry is cleared, dormant ones included: "every section
 * together" means no section keeps a value of its own. [valueMm] ≤ 0 is ignored. Returns
 * `this` when nothing changes, so a no-op set never emits new state or marks the document
 * dirty.
 */
fun ShaftSpec.withAutoBodyDia(valueMm: Float): ShaftSpec {
    if (valueMm <= 0f) return this
    if (autoBodyDiaMm == valueMm && autoDiaOverrides.isEmpty()) return this
    return copy(autoBodyDiaMm = valueMm, autoDiaOverrides = emptyList())
}
