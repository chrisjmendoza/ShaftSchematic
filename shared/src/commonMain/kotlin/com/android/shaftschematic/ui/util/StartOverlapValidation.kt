package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collisionPartnerIds
import com.android.shaftschematic.model.collisionPartnerIdsForSpan
import com.android.shaftschematic.ui.order.ComponentKind

enum class CollisionGroup { LINER, THREAD }

fun ComponentKind.collisionGroup(): CollisionGroup? = when (this) {
    ComponentKind.LINER -> CollisionGroup.LINER
    ComponentKind.THREAD -> CollisionGroup.THREAD
    // Coupler bolt slots are reference cutouts — they overlay other components by design.
    ComponentKind.BODY, ComponentKind.TAPER, ComponentKind.COUPLER_BOLT_SLOT -> null
}

/**
 * Field-level gate on a carousel card's **Length**: a zero or negative length is not a shape.
 *
 * Returned as a `NumericInputField` validator message, so the field reverts its text, names the
 * refused value ("0 not applied — Must be > 0"), and never calls `onCommit` — the value stops at
 * the card rather than reaching the
 * model. This is a gate on an *entry*, not a rewrite of a stored one: nothing clamps or
 * adjusts a committed value (the golden rule).
 *
 * A `null` [mm] is deferred to the field's own parse check — text that did not parse is
 * "not a number", not a too-short length. All four component cards share this one helper so
 * the rule cannot drift between them. Not applied to keyway W/D/L (the Keyway checkbox's
 * confirmed removal is what clears a keyway), and not to the Add dialogs, whose Submit gates
 * already require a positive length.
 */
fun positiveLengthErrorMm(mm: Float?): String? =
    if (mm != null && mm <= 0f) MUST_BE_POSITIVE_MSG else null

/** The reason every card's "> 0" gate gives. */
const val MUST_BE_POSITIVE_MSG = "Must be > 0"

/** The reason a negative Start is refused — on the cards and in the Add dialogs alike. */
const val MUST_BE_NON_NEGATIVE_MSG = "Must be ≥ 0"

/**
 * Field-level gate on a carousel card's **diameter** — explicit body Ø, liner OD, taper
 * S.E.T./L.E.T. Ø, thread major Ø, relief Ø, bolt-hole Ø: a zero diameter is not a shape, and
 * the Add dialogs already refuse one. Same posture as [positiveLengthErrorMm]: the field reverts
 * and names the refused value. Deliberately NOT on the auto-body Ø, where `≤ 0` clears that
 * section's override (a documented feature).
 */
fun positiveDiaErrorMm(mm: Float?): String? = positiveLengthErrorMm(mm)

/**
 * The title each component's carousel card shows, by resolved id — the card-title builders
 * themselves (a user label wins; otherwise "AFT Taper", "FWD Liner #2", …), so a message that
 * names a component names it the way the user sees it on its card.
 */
fun componentCardTitles(spec: ShaftSpec): Map<String, String> =
    buildBodyTitleById(spec) + buildTaperTitleById(spec) +
        buildThreadTitleById(spec) + buildLinerTitleById(spec)

/**
 * "Overlaps FWD Taper", or "Overlaps AFT Liner, FWD Taper" for several — the partners named by
 * their card titles ([componentCardTitles]), in [partnerIds] order. Null for no partners.
 */
fun overlapsMessage(spec: ShaftSpec, partnerIds: List<String>): String? {
    if (partnerIds.isEmpty()) return null
    val titles = componentCardTitles(spec)
    return "Overlaps " + partnerIds.map { titles[it] ?: "another component" }.distinct().joinToString(", ")
}

/**
 * The carousel card's overlap chip for component [id]: every partner it collides with by the
 * export gate's rule (`collidingIds`), named. Null when it collides with nothing.
 */
fun collisionChipMessage(spec: ShaftSpec, id: String): String? =
    overlapsMessage(spec, spec.collisionPartnerIds(id))

/**
 * An Add dialog's inline overlap line for a proposed span: every component it would collide with
 * by the export gate's rule, named — the same partners the "Add Anyway?" confirm lists. Null when
 * the span is free, or not entered yet.
 */
fun proposedSpanOverlapMessage(spec: ShaftSpec, startMm: Float, lengthMm: Float): String? =
    if (startMm < 0f || lengthMm <= 0f) null
    else overlapsMessage(spec, spec.collisionPartnerIdsForSpan(startMm, lengthMm))

/**
 * Field-level gate on a liner's or thread's START (and the Add dialogs' Start): `≥ 0`, no overlap
 * with another liner (or thread) — [spanOverlapErrorMm] — and, for a thread, at a shaft end.
 * Tapers (no [collisionGroup]) only get the `≥ 0` check: a taper's overlaps are flagged by its
 * card chip, the posture the Add dialogs' "Add Anyway?" takes for crossing kinds.
 */
fun startOverlapErrorMm(
    spec: ShaftSpec,
    selfId: String,
    selfKind: ComponentKind,
    selfLengthMm: Float,
    startMm: Float,
): String? {
    if (startMm < 0f) return MUST_BE_NON_NEGATIVE_MSG

    val group = selfKind.collisionGroup() ?: return null

    spanOverlapErrorMm(spec, selfId, selfKind, startMm, selfLengthMm)?.let { return it }

    // Threads must be at a shaft end (AFT or FWD). A thread is surrounded if there is
    // a Body or Liner that ends at-or-before the thread's start AND another that starts
    // at-or-after the thread's end. Using eps to handle floating-point adjacency.
    if (group == CollisionGroup.THREAD) {
        val threadEnd = startMm + selfLengthMm
        val eps = 1e-3f

        val hasAft = spec.bodies.any { b ->
            (b.startFromAftMm + b.lengthMm) <= startMm + eps
        } || spec.liners.any { ln ->
            ln.id != selfId && (ln.startFromAftMm + ln.lengthMm) <= startMm + eps
        }

        val hasFwd = spec.bodies.any { b ->
            b.startFromAftMm >= threadEnd - eps
        } || spec.liners.any { ln ->
            ln.id != selfId && ln.startFromAftMm >= threadEnd - eps
        }

        if (hasAft && hasFwd) return "Thread must be at a shaft end, not between components"
    }

    return null
}

/**
 * The overlap half of [startOverlapErrorMm], for a span [startMm] … [startMm] + [lengthMm]:
 * another liner (for a liner) or thread (for a thread) it would run into, named by card title.
 * The card's LENGTH field calls it with the stored start and the new length, so a span is refused
 * whichever field grew it — checking the Start alone would let a liner Length of 99999 land
 * across every liner FWD of it while the same span typed as a Start is refused (emulator QA). It
 * does not check the start
 * against 0 or the end against the OAL: a span past either end is the bounds warning's job (an OAL
 * shrink produces it legitimately). Null for kinds with no [collisionGroup].
 */
fun spanOverlapErrorMm(
    spec: ShaftSpec,
    selfId: String,
    selfKind: ComponentKind,
    startMm: Float,
    lengthMm: Float,
): String? {
    val group = selfKind.collisionGroup() ?: return null
    val eps = 1e-3f
    fun overlapsStrict(bStart: Float, bLen: Float): Boolean =
        // Endpoints touching are allowed.
        (startMm < bStart + bLen - eps) && (startMm + lengthMm > bStart + eps)

    val partners = when (group) {
        CollisionGroup.LINER -> spec.liners.filter {
            it.id != selfId && overlapsStrict(it.startFromAftMm, it.lengthMm)
        }.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).map { it.id }

        CollisionGroup.THREAD -> spec.threads.filter {
            it.id != selfId && overlapsStrict(it.startFromAftMm, it.lengthMm)
        }.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).map { it.id }
    }
    return overlapsMessage(spec, partners)
}
