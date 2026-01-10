package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.order.ComponentKind

enum class CollisionGroup { LINER, THREAD }

fun ComponentKind.collisionGroup(): CollisionGroup? = when (this) {
    ComponentKind.LINER -> CollisionGroup.LINER
    ComponentKind.THREAD -> CollisionGroup.THREAD
    ComponentKind.BODY, ComponentKind.TAPER -> null
}

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

    return if (overlaps) "Overlaps another component" else null
}
