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
