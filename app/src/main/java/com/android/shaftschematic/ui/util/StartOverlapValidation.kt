package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
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
 * Returned as a `NumericInputField` validator message, so the field shows the error, reverts
 * its text, and never calls `onCommit` — the value stops at the card rather than reaching the
 * model. This is a gate on an *entry*, not a rewrite of a stored one: nothing clamps or
 * adjusts a committed value (the golden rule).
 *
 * A `null` [mm] is deferred to the field's own parse check — text that did not parse is an
 * "Invalid number", not a too-short length. All four component cards share this one helper so
 * the rule cannot drift between them. Deliberately NOT applied to diameter fields (an
 * auto-body Ø of `≤ 0` clears that section's override, a documented feature) nor to keyway
 * fields (blank/0 clears a keyway), and not to the Add dialogs, whose Submit gates already
 * require a positive length.
 */
fun positiveLengthErrorMm(mm: Float?): String? =
    if (mm != null && mm <= 0f) "Must be > 0" else null

fun startOverlapErrorMm(
    spec: ShaftSpec,
    selfId: String,
    selfKind: ComponentKind,
    selfLengthMm: Float,
    startMm: Float,
): String? {
    if (startMm < 0f) return "Must be ≥ 0"

    val group = selfKind.collisionGroup() ?: return null

    fun overlapsStrict(aStart: Float, aLen: Float, bStart: Float, bLen: Float): Boolean {
        // Endpoints touching are allowed.
        val eps = 1e-3f
        val aEnd = aStart + aLen
        val bEnd = bStart + bLen
        return (aStart < bEnd - eps) && (aEnd > bStart + eps)
    }

    val overlaps = when (group) {
        CollisionGroup.LINER -> spec.liners.any {
            it.id != selfId && overlapsStrict(startMm, selfLengthMm, it.startFromAftMm, it.lengthMm)
        }

        CollisionGroup.THREAD -> spec.threads.any {
            it.id != selfId && overlapsStrict(startMm, selfLengthMm, it.startFromAftMm, it.lengthMm)
        }
    }

    if (overlaps) return "Overlaps another component"

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
