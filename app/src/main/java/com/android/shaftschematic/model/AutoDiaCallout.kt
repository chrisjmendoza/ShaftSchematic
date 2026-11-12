package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * The Ø-callout visibility of ONE auto-body span — a section of bare shaft shows or hides its
 * diameter on the schematic by itself, without being promoted to an explicit [Body] and so
 * without giving up its derived extent.
 *
 * Keyed in **shaft-space** by [anchorMm], the [AutoDiaOverride] posture in every respect: the
 * auto span whose half-open extent `[startMm, endMm)` contains the anchor takes [show]; the
 * aft-most anchor in a span wins; an anchor under a component is dormant; nothing is pruned at
 * decode; and a component add or remove drops the entry with its section
 * (`withAutoDiaOverridesSurviving`). A span with no anchor follows the shaft-wide default,
 * [ShaftSpec.showAutoBodyDia].
 *
 * Draw-only: it gates one callout and nothing else — never OAL, resolve, collision, or the
 * span's diameter. [anchorMm] is system-placed at the midpoint of the span the switch was
 * thrown on and carries no authored meaning.
 *
 * Units: mm.
 */
@Serializable
data class AutoDiaCallout(
    /** Shaft-space mm from the AFT face; the span containing it takes [show]. */
    val anchorMm: Float = 0f,
    /** Whether that span's Ø prints as a below-shaft callout. */
    val show: Boolean = true,
)

/**
 * Whether the auto span `[startMm, endMm)` prints its Ø callout: the aft-most
 * [AutoDiaCallout] anchored inside it, else the shaft-wide [ShaftSpec.showAutoBodyDia].
 */
fun ShaftSpec.showsAutoSectionDia(startMm: Float, endMm: Float): Boolean =
    autoDiaCallouts.asSequence()
        .filter { it.anchorMm >= startMm && it.anchorMm < endMm }
        .minByOrNull { it.anchorMm }
        ?.show
        ?: showAutoBodyDia

/**
 * Returns a copy in which the auto span `[spanStartMm, spanEndMm)` shows ([show]) or hides its
 * Ø callout, every other section keeping its own.
 *
 * Upsert: every entry anchored inside the span is dropped, and one is stored at the span
 * midpoint only when [show] differs from the shaft-wide default — a section that agrees with
 * the default needs no entry. Entries anchored outside the span, dormant ones included, are
 * left alone. Returns `this` when nothing moves, so a no-op set never emits new state or marks
 * the document dirty.
 */
fun ShaftSpec.withAutoSectionShowDia(
    spanStartMm: Float,
    spanEndMm: Float,
    show: Boolean,
): ShaftSpec {
    val kept = autoDiaCallouts.filterNot { it.anchorMm >= spanStartMm && it.anchorMm < spanEndMm }
    val next =
        if (show != showAutoBodyDia) kept + AutoDiaCallout(
            anchorMm = (spanStartMm + spanEndMm) / 2f,
            show = show,
        )
        else kept
    return if (next == autoDiaCallouts) this else copy(autoDiaCallouts = next)
}
