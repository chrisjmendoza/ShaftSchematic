package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * A bare-shaft section wrapped in fiberglass — the auto-body twin of [Body.fiberglass], so the
 * shaft between two liners can be marked as wrapped without being promoted to an explicit body
 * (and so without giving up its derived extent).
 *
 * Keyed in **shaft-space** by [anchorMm], the [AutoBlend] posture in every respect: the auto span
 * whose half-open extent `[startMm, endMm)` contains an anchor is wrapped; an anchor under a
 * component is **dormant**, never pruned — at decode or by a component add or remove — so a wrap
 * stays on its section when the liners or the overall length move, exactly as that section's seal
 * areas ([AutoBlend.seal]) do. Only sections that ARE wrapped carry an entry; "not wrapped" is the
 * absence of one.
 *
 * Draw-only: it colours one section on the Final tab's Full length sheet and nothing else —
 * never OAL, resolve, collision, a diameter, or the footer. [anchorMm] is system-placed at the
 * midpoint of the span the box was ticked on and carries no authored meaning.
 *
 * Units: mm.
 */
@Serializable
data class AutoFiberglass(
    /** Shaft-space mm from the AFT face; the auto span containing it is wrapped. */
    val anchorMm: Float = 0f,
)

/** Whether the auto span `[startMm, endMm)` is wrapped in fiberglass: any anchor inside it. */
fun ShaftSpec.autoSectionFiberglass(startMm: Float, endMm: Float): Boolean =
    autoFiberglass.any { it.anchorMm >= startMm && it.anchorMm < endMm }

/**
 * Returns a copy in which the auto span `[spanStartMm, spanEndMm)` is wrapped ([on]) or not.
 * Every entry anchored inside the span is dropped, and one is stored at the span midpoint when
 * [on]; entries anchored outside it, dormant ones included, are left alone. Returns `this` when
 * nothing moves, so a no-op set never emits new state or marks the document dirty.
 */
fun ShaftSpec.withAutoSectionFiberglass(spanStartMm: Float, spanEndMm: Float, on: Boolean): ShaftSpec {
    val kept = autoFiberglass.filterNot { it.anchorMm >= spanStartMm && it.anchorMm < spanEndMm }
    val next = if (on) kept + AutoFiberglass((spanStartMm + spanEndMm) / 2f) else kept
    return if (next == autoFiberglass) this else copy(autoFiberglass = next)
}

/**
 * Ticks or unticks fiberglass on the auto span `[spanStartMm, spanEndMm)` in ONE edit: ticking it
 * also switches on a seal area at each face that has none, at [aftSealLenMm] / [fwdSealLenMm] (the
 * card's own seal-toggle seed, so a typed length is kept); unticking leaves the seal areas as they
 * are — they are their own controls and real machining. Every other field of each face's blend
 * anchor rides through untouched ([withAutoBlend] keeps the face's Angle entry).
 */
fun ShaftSpec.withAutoSectionFiberglassAndSeals(
    spanStartMm: Float,
    spanEndMm: Float,
    on: Boolean,
    aftSealLenMm: Float,
    fwdSealLenMm: Float,
): ShaftSpec {
    var s = withAutoSectionFiberglass(spanStartMm, spanEndMm, on)
    if (!on) return s
    for ((end, len) in listOf(
        LinerAuthoredReference.AFT to aftSealLenMm,
        LinerAuthoredReference.FWD to fwdSealLenMm,
    )) {
        val cur = s.autoBlends.autoBlendFor(spanStartMm, spanEndMm, end)
        if (cur?.seal == true) continue
        s = s.withAutoBlend(
            spanStartMm, spanEndMm, end,
            lengthMm = cur?.lengthMm ?: 0f,
            profile = cur?.profile ?: BlendProfile.OGEE,
            seal = true,
            sealLenMm = len,
            toLinerOd = cur?.toLinerOd ?: false,
        )
    }
    return s
}

/**
 * Ticks or unticks fiberglass on a stored body in ONE edit, the [withAutoSectionFiberglassAndSeals]
 * rule: ticking also switches on each face's seal area that is off, at [aftSealLenMm] /
 * [fwdSealLenMm]; unticking touches only the flag.
 */
fun Body.withFiberglass(on: Boolean, aftSealLenMm: Float, fwdSealLenMm: Float): Body =
    if (!on) copy(fiberglass = false)
    else copy(
        fiberglass = true,
        blendAftSeal = true,
        blendAftSealLenMm = if (blendAftSeal) blendAftSealLenMm else aftSealLenMm,
        blendFwdSeal = true,
        blendFwdSealLenMm = if (blendFwdSeal) blendFwdSealLenMm else fwdSealLenMm,
    )
