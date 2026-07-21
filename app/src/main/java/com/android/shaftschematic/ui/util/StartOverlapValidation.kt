package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.bodyOverlapErrorMm
import com.android.shaftschematic.model.nonBodyOverlapErrorMm
import com.android.shaftschematic.ui.order.ComponentKind

enum class CollisionGroup { LINER, THREAD }

fun ComponentKind.collisionGroup(): CollisionGroup? = when (this) {
    ComponentKind.LINER -> CollisionGroup.LINER
    ComponentKind.THREAD -> CollisionGroup.THREAD
    // Coupler bolt slots are reference cutouts — they overlay other components by design.
    ComponentKind.BODY, ComponentKind.TAPER, ComponentKind.COUPLER_BOLT_SLOT -> null
}

fun startOverlapErrorMm(
    spec: ShaftSpec,
    selfId: String,
    selfKind: ComponentKind,
    selfLengthMm: Float,
    startMm: Float,
): String? {
    if (startMm < 0f) return "Must be ≥ 0"

    // Explicit bodies are non-negotiable walls: nothing may move onto one (coupler bolt
    // slots are reference cutouts and are exempt). A body being moved also may not cross
    // any other component. Liner *length* edits negotiate at a shared body edge instead —
    // that path does not run this start validator.
    if (selfKind != ComponentKind.COUPLER_BOLT_SLOT) {
        spec.bodyOverlapErrorMm(selfId, startMm, selfLengthMm)?.let { return it }
        if (selfKind == ComponentKind.BODY) {
            spec.nonBodyOverlapErrorMm(selfId, startMm, selfLengthMm)?.let { return it }
        }
    }

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
