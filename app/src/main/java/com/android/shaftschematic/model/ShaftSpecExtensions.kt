package com.android.shaftschematic.model

import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import kotlin.math.max

/**
 * File: ShaftSpecExtensions.kt
 * Layer: Model (pure domain helpers)
 * Purpose: Provide small, **unit-correct** derived computations for ShaftSpec.
 *
 * Design Principles
 *  • All inputs/outputs are **millimeters** only; never depend on UI/layout/pixels.
 *  • No side effects; these are safe to use from ViewModel, UI, or tests.
 *  • Keep names literal to what they return (end positions and free length).
 *
 * Functions
 *  • lastOccupiedEndMm(): Float
 *      Returns the furthest axial end (mm from aft face, x=0) occupied by any component.
 *  • freeToEndMm(): Float
 *      Returns remaining length (≥ 0) from the last occupied end to overallLengthMm.
 */

/** Returns max of all component end positions in mm (≥ 0). */
fun ShaftSpec.lastOccupiedEndMm(): Float {
    var maxEnd = 0f
    bodies.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    tapers.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    threads.forEach  { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    liners.forEach   { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
    return maxEnd
}

/**
 * Computes the physical (AFT → FWD) order of all components by startFromAftMm.
 * Ties are broken by kind + id to keep ordering deterministic.
 */
fun ShaftSpec.buildPhysicalKeyOrder(): List<ComponentKey> {
    val keysWithStart = mutableListOf<Pair<ComponentKey, Float>>()

    bodies.forEach { b ->
        keysWithStart += ComponentKey(b.id, ComponentKind.BODY) to b.startFromAftMm
    }
    tapers.forEach { t ->
        keysWithStart += ComponentKey(t.id, ComponentKind.TAPER) to t.startFromAftMm
    }
    threads.forEach { th ->
        keysWithStart += ComponentKey(th.id, ComponentKind.THREAD) to th.startFromAftMm
    }
    liners.forEach { ln ->
        keysWithStart += ComponentKey(ln.id, ComponentKind.LINER) to ln.startFromAftMm
    }

    return keysWithStart
        .sortedWith(
            compareBy<Pair<ComponentKey, Float>> { it.second }
                .thenBy { it.first.kind.ordinal }
                .thenBy { it.first.id }
        )
        .map { it.first }
}

/**
 * Find the immediate physical neighbor to the **left** of [anchor] in this spec,
 * based on [buildPhysicalKeyOrder].
 */
fun ShaftSpec.findLeftNeighbor(anchor: ComponentKey): ComponentKey? {
    val ordered = buildPhysicalKeyOrder()
    val idx = ordered.indexOf(anchor)
    return if (idx > 0) ordered[idx - 1] else null
}

/**
 * Find the immediate physical neighbor to the **right** of [anchor], if any.
 */
fun ShaftSpec.findRightNeighbor(anchor: ComponentKey): ComponentKey? {
    val ordered = buildPhysicalKeyOrder()
    val idx = ordered.indexOf(anchor)
    return if (idx >= 0 && idx + 1 < ordered.size) ordered[idx + 1] else null
}

/** Shift every component's start position by [delta] millimeters (clamped at 0). */
fun ShaftSpec.shiftAllBy(delta: Float): ShaftSpec {
    if (delta == 0f) return this
    fun Float.shift(): Float = (this + delta).coerceAtLeast(0f)

    return copy(
        bodies = bodies.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        tapers = tapers.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        threads = threads.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        liners = liners.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) }
    )
}

/**
 * When no left neighbor exists (deleting the first component), align the leading component to 0
 * and snap all following components end-to-start.
 */
fun ShaftSpec.snapFromOrigin(): ShaftSpec {
    val ordered = buildPhysicalKeyOrder()
    val first = ordered.firstOrNull() ?: return this
    val firstSegment = segmentFor(first) ?: return this

    val shifted = if (firstSegment.startFromAftMm == 0f) this
    else shiftAllBy(-firstSegment.startFromAftMm)

    return shifted.snapForwardFrom(first)
}

/** Look up the Segment for a ComponentKey in this spec. */
private fun ShaftSpec.segmentFor(key: ComponentKey): Segment? =
    when (key.kind) {
        ComponentKind.BODY   -> bodies.firstOrNull { it.id == key.id }
        ComponentKind.TAPER  -> tapers.firstOrNull { it.id == key.id }
        ComponentKind.THREAD -> threads.firstOrNull { it.id == key.id }
        ComponentKind.LINER  -> liners.firstOrNull { it.id == key.id }
    }

/** Return a copy where exactly the addressed segment has a new startFromAftMm. */
private fun ShaftSpec.withSegmentStart(key: ComponentKey, newStartMm: Float): ShaftSpec =
    when (key.kind) {
        ComponentKind.BODY -> {
            val idx = bodies.indexOfFirst { it.id == key.id }
            if (idx < 0) this else copy(
                bodies = bodies.toMutableList().also { list ->
                    val b = list[idx]
                    list[idx] = b.copy(startFromAftMm = newStartMm)
                }
            )
        }
        ComponentKind.TAPER -> {
            val idx = tapers.indexOfFirst { it.id == key.id }
            if (idx < 0) this else copy(
                tapers = tapers.toMutableList().also { list ->
                    val t = list[idx]
                    list[idx] = t.copy(startFromAftMm = newStartMm)
                }
            )
        }
        ComponentKind.THREAD -> {
            val idx = threads.indexOfFirst { it.id == key.id }
            if (idx < 0) this else copy(
                threads = threads.toMutableList().also { list ->
                    val th = list[idx]
                    list[idx] = th.copy(startFromAftMm = newStartMm)
                }
            )
        }
        ComponentKind.LINER -> {
            val idx = liners.indexOfFirst { it.id == key.id }
            if (idx < 0) this else copy(
                liners = liners.toMutableList().also { list ->
                    val ln = list[idx]
                    list[idx] = ln.copy(startFromAftMm = newStartMm)
                }
            )
        }
    }

/**
 * Returns a copy of this [ShaftSpec] where all components to the **right** of [anchor]
 * are snapped so that `start = previous.end`, in physical order.
 */
fun ShaftSpec.snapForwardFrom(anchor: ComponentKey): ShaftSpec {
    val ordered = buildPhysicalKeyOrder()
    val startIndex = ordered.indexOf(anchor)
    if (startIndex == -1) return this

    var working = this

    for (i in startIndex until ordered.lastIndex) {
        val leftKey = ordered[i]
        val rightKey = ordered[i + 1]

        val left = working.segmentFor(leftKey) ?: continue
        val newStart = left.startFromAftMm + left.lengthMm
        val right = working.segmentFor(rightKey) ?: continue

        if (right.startFromAftMm != newStart) {
            working = working.withSegmentStart(rightKey, newStart)
        }
    }

    return working
}

/**
 * Variant of [snapForwardFrom] that uses the supplied [order] (typically UI order)
 * to determine left/right relationships rather than recomputing by start position.
 *
 * Useful when components were previously shifted (e.g., delete snap) and we want to
 * push a restored component's followers back to the right based on their stable order.
 * Currently used by JVM tests and debugging helpers; no harm keeping it around for
 * future ViewModel flows that need deterministic order-driven snapping.
 */
fun ShaftSpec.snapForwardFromOrdered(
    anchor: ComponentKey,
    order: List<ComponentKey>
): ShaftSpec {
    if (order.isEmpty()) return this
    val filtered = order.filter { segmentFor(it) != null }
    val startIndex = filtered.indexOf(anchor)
    if (startIndex == -1) return this

    var working = this
    for (i in startIndex until filtered.lastIndex) {
        val leftKey = filtered[i]
        val rightKey = filtered[i + 1]

        val left = working.segmentFor(leftKey) ?: continue
        val newStart = left.startFromAftMm + left.lengthMm
        val right = working.segmentFor(rightKey) ?: continue

        if (right.startFromAftMm != newStart) {
            working = working.withSegmentStart(rightKey, newStart)
        }
    }

    return working
}
