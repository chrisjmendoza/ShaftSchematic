package com.android.shaftschematic.model

import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import kotlin.math.abs
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
    threads.filter { !it.excludeFromOAL }
           .forEach { maxEnd = max(maxEnd, it.startFromAftMm + it.lengthMm) }
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
    threads.filter { !it.excludeFromOAL }.forEach { th ->
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

/**
 * Returns the IDs of all components involved in at least one axial overlap.
 *
 * Checked pairs:
 *  Taper–Taper, Taper–Thread, Taper–Liner,
 *  Thread–Thread, Thread–Liner, Liner–Liner.
 *
 * Bodies are intentionally **excluded** from collision. A body legitimately runs under a
 * liner (a sleeve over the shaft) or up against a taper — the resolve layer trims the drawn
 * body around those components, so the *stored* body can span across them without being a
 * real conflict. Flagging that (from the raw stored spans) produced false "Overlaps Body N"
 * warnings against bodies that aren't even drawn there. Excluded threads
 * ([Threads.excludeFromOAL] = true) are skipped.
 */
fun ShaftSpec.collidingIds(): Set<String> {
    val eps = 1e-3f

    fun overlaps(aStart: Float, aLen: Float, bStart: Float, bLen: Float): Boolean {
        val aEnd = aStart + aLen
        val bEnd = bStart + bLen
        return (aStart < bEnd - eps) && (aEnd > bStart + eps)
    }

    val result = mutableSetOf<String>()
    val activeThreads = threads.filter { !it.excludeFromOAL }

    for (i in tapers.indices) for (j in i + 1 until tapers.size) {
        val a = tapers[i]; val b = tapers[j]
        if (overlaps(a.startFromAftMm, a.lengthMm, b.startFromAftMm, b.lengthMm)) {
            result += a.id; result += b.id
        }
    }
    for (t in tapers) for (th in activeThreads) {
        if (overlaps(t.startFromAftMm, t.lengthMm, th.startFromAftMm, th.lengthMm)) {
            result += t.id; result += th.id
        }
    }
    for (t in tapers) for (ln in liners) {
        if (overlaps(t.startFromAftMm, t.lengthMm, ln.startFromAftMm, ln.lengthMm)) {
            result += t.id; result += ln.id
        }
    }
    for (i in activeThreads.indices) for (j in i + 1 until activeThreads.size) {
        val a = activeThreads[i]; val b = activeThreads[j]
        if (overlaps(a.startFromAftMm, a.lengthMm, b.startFromAftMm, b.lengthMm)) {
            result += a.id; result += b.id
        }
    }
    for (th in activeThreads) for (ln in liners) {
        if (overlaps(th.startFromAftMm, th.lengthMm, ln.startFromAftMm, ln.lengthMm)) {
            result += th.id; result += ln.id
        }
    }
    for (i in liners.indices) for (j in i + 1 until liners.size) {
        val a = liners[i]; val b = liners[j]
        if (overlaps(a.startFromAftMm, a.lengthMm, b.startFromAftMm, b.lengthMm)) {
            result += a.id; result += b.id
        }
    }

    return result
}

/**
 * Returns a copy of this [ShaftSpec] with [overallLengthMm] set to [newOal], reanchoring
 * any FWD-referenced tapers and liners so their authored distance from the FWD face is
 * preserved.  AFT-referenced components keep their [startFromAftMm] unchanged.
 *
 * After reanchoring, excluded end threads are repositioned via [syncExcludedThreadPositions].
 *
 * This is the single authoritative OAL-change helper; every mutation that changes the shaft
 * length should go through here rather than calling `.copy(overallLengthMm = …)` directly.
 */
fun ShaftSpec.withNewOal(newOal: Float): ShaftSpec {
    val clampedOal = newOal.coerceAtLeast(0f)
    val oldOal = overallLengthMm

    val newTapers = tapers.map { t ->
        if (t.authoredReference == LinerAuthoredReference.FWD) {
            val authoredFromFwd = oldOal - t.startFromAftMm - t.lengthMm
            t.copy(startFromAftMm = clampedOal - authoredFromFwd - t.lengthMm)
        } else {
            t
        }
    }

    val newLiners = liners.map { ln ->
        if (ln.authoredReference == LinerAuthoredReference.FWD) {
            val authoredFromFwd = oldOal - ln.startFromAftMm - ln.lengthMm
            ln.withPhysical(
                startMmPhysical = clampedOal - authoredFromFwd - ln.lengthMm,
                lengthMm = ln.lengthMm,
                odMm = ln.odMm,
            )
        } else {
            ln
        }
    }

    // Coupler bolt slots default to FWD authoring; preserve the authored distance from the
    // FWD face to the fwd-most cutout center (row span excludes the hole-radius padding).
    val newSlots = couplerBoltSlots.map { cs ->
        if (cs.authoredReference == SlotAuthoredReference.FWD) {
            val rowSpan = (cs.count - 1).coerceAtLeast(0) * cs.spacingMm
            val authoredFromFwd = oldOal - (cs.startFromAftMm + rowSpan)
            cs.copy(startFromAftMm = clampedOal - authoredFromFwd - rowSpan)
        } else {
            cs
        }
    }

    return copy(
        overallLengthMm = clampedOal,
        tapers = newTapers,
        liners = newLiners,
        couplerBoltSlots = newSlots,
    ).syncExcludedThreadPositions()
}

/** Shift every component's start position by [delta] millimeters (clamped at 0). */
fun ShaftSpec.shiftAllBy(delta: Float): ShaftSpec {
    if (delta == 0f) return this
    fun Float.shift(): Float = (this + delta).coerceAtLeast(0f)

    return copy(
        bodies = bodies.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        tapers = tapers.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        threads = threads.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        liners = liners.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) },
        couplerBoltSlots = couplerBoltSlots.map { it.copy(startFromAftMm = it.startFromAftMm.shift()) }
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
        // Reference feature: never part of physical snap ordering.
        ComponentKind.COUPLER_BOLT_SLOT -> couplerBoltSlots.firstOrNull { it.id == key.id }
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
        ComponentKind.COUPLER_BOLT_SLOT -> {
            val idx = couplerBoltSlots.indexOfFirst { it.id == key.id }
            if (idx < 0) this else copy(
                couplerBoltSlots = couplerBoltSlots.toMutableList().also { list ->
                    val cs = list[idx]
                    list[idx] = cs.copy(startFromAftMm = newStartMm)
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

// ---- Body split / merge --------------------------------------------------------

/**
 * Carries the spec mutated by a split or merge, plus the IDs that were removed from
 * and added to the bodies list so the ViewModel can keep [_componentOrder] in sync.
 */
data class BodySplitResult(
    val spec: ShaftSpec,
    val removedIds: List<String>,
    val addedIds: List<String>,
)

/**
 * Carries [from]'s keyway onto [to] (a fragment/merge/expansion of the same physical
 * material) when the keyway's **absolute** axial span still fits inside [to].
 *
 * The keyway stays where the metal was cut: its absolute span is computed from [from]'s
 * geometry and re-expressed as an offset from [to]'s referenced end face. If the span no
 * longer fits inside [to] (the fragment cut through the keyway), [to] is returned without
 * a keyway rather than inventing a shifted one.
 */
internal fun carryBodyKeyway(from: Body, to: Body): Body {
    val (absLo, absHi) = from.keywayAbsSpanMm() ?: return to
    val eps = 1e-3f
    val toStart = to.startFromAftMm
    val toEnd   = toStart + to.lengthMm
    if (absLo < toStart - eps || absHi > toEnd + eps) return to

    val newOffset = when (from.keywayEnd) {
        LinerAuthoredReference.AFT -> (absLo - toStart).coerceAtLeast(0f)
        LinerAuthoredReference.FWD -> (toEnd - absHi).coerceAtLeast(0f)
    }
    return to.copy(
        keywayWidthMm = from.keywayWidthMm,
        keywayDepthMm = from.keywayDepthMm,
        keywayLengthMm = from.keywayLengthMm,
        keywayOffsetFromEndMm = newOffset,
        keywayEnd = from.keywayEnd,
        keywaySpooned = from.keywaySpooned,
    )
}

/**
 * Splits any body whose axial span overlaps [compStart, compEnd] into up to two
 * fragments, one on each side of the component.  Call this before inserting a new
 * taper / liner / thread so the carousel reflects independent body sections.
 *
 * [genId] is called once per new fragment to produce a unique ID (typically
 * `UUID.randomUUID().toString()`).  Fragments inherit the parent's [Body.diaMm].
 */
fun ShaftSpec.splitBodiesAround(
    compStart: Float,
    compEnd: Float,
    genId: () -> String,
): BodySplitResult {
    val eps = 1e-3f
    val removedIds = mutableListOf<String>()
    val addedIds   = mutableListOf<String>()
    val newBodies  = mutableListOf<Body>()

    for (body in bodies) {
        val bodyStart = body.startFromAftMm
        val bodyEnd   = bodyStart + body.lengthMm
        val overlaps  = compStart < bodyEnd - eps && compEnd > bodyStart + eps
        if (!overlaps) {
            newBodies += body
            continue
        }
        // Light protection: a keyed body is never fragmented — it stays one whole card (its
        // keyway needs the full body). The resolve layer still trims it around the component
        // for drawing, so this only affects the stored representation, not the picture.
        if (body.hasKeyway) {
            newBodies += body
            continue
        }
        removedIds += body.id
        // Left fragment: from body start up to where the component begins
        if (compStart > bodyStart + eps) {
            val id = genId()
            addedIds  += id
            newBodies += carryBodyKeyway(
                from = body,
                to = Body(id = id, startFromAftMm = bodyStart, lengthMm = compStart - bodyStart, diaMm = body.diaMm)
            )
        }
        // Right fragment: from where the component ends to body end
        if (compEnd < bodyEnd - eps) {
            val id = genId()
            addedIds  += id
            newBodies += carryBodyKeyway(
                from = body,
                to = Body(id = id, startFromAftMm = compEnd, lengthMm = bodyEnd - compEnd, diaMm = body.diaMm)
            )
        }
    }

    return BodySplitResult(copy(bodies = newBodies), removedIds, addedIds)
}

/**
 * After a component spanning [compStart, compEnd] has been removed, look for body
 * fragments that were created by splitting around it and merge them back into one.
 *
 * Merge conditions (both must hold):
 *  - Body A whose right edge is within [MERGE_EPS] of [compStart]
 *  - Body B whose left  edge is within [MERGE_EPS] of [compEnd]
 *
 * The merged body spans A.start → B.end with diameter = max(A.diaMm, B.diaMm).
 * If only one side is found (component was at a shaft boundary), that body expands
 * to fill the freed span.
 */
fun ShaftSpec.mergeBodiesAround(
    compStart: Float,
    compEnd: Float,
    genId: () -> String,
): BodySplitResult {
    val eps = 0.5f   // looser than split-eps to absorb float drift across add/remove cycles

    val bodyA = bodies.firstOrNull { b -> abs(b.startFromAftMm + b.lengthMm - compStart) < eps }
    val bodyB = bodies.firstOrNull { b -> abs(b.startFromAftMm - compEnd)                < eps }

    if (bodyA == null && bodyB == null) return BodySplitResult(this, emptyList(), emptyList())

    val removedIds = mutableListOf<String>()
    val addedIds   = mutableListOf<String>()
    val newBodies  = bodies.toMutableList()

    if (bodyA != null && bodyB != null) {
        // Only merge A→B across the freed span if nothing else still occupies it. If another
        // component overlapped the removed one (stacked), merging straight across would span
        // the survivor and manufacture a long phantom body — so skip the merge and let
        // auto-bodies fill whatever is actually free.
        val gapEps = 1e-3f
        val gapStart = bodyA.startFromAftMm + bodyA.lengthMm
        val gapEnd = bodyB.startFromAftMm
        fun occupiesGap(s: Float, l: Float): Boolean = s < gapEnd - gapEps && s + l > gapStart + gapEps
        val gapClear =
            tapers.none { occupiesGap(it.startFromAftMm, it.lengthMm) } &&
            threads.filter { !it.excludeFromOAL }.none { occupiesGap(it.startFromAftMm, it.lengthMm) } &&
            liners.none { occupiesGap(it.startFromAftMm, it.lengthMm) } &&
            bodies.none { it.id != bodyA.id && it.id != bodyB.id && occupiesGap(it.startFromAftMm, it.lengthMm) }
        if (!gapClear) return BodySplitResult(this, emptyList(), emptyList())

        newBodies -= bodyA
        newBodies -= bodyB
        removedIds += listOf(bodyA.id, bodyB.id)
        val id = genId()
        addedIds += id
        var merged = Body(
            id             = id,
            startFromAftMm = bodyA.startFromAftMm,
            lengthMm       = (bodyB.startFromAftMm + bodyB.lengthMm) - bodyA.startFromAftMm,
            diaMm          = maxOf(bodyA.diaMm, bodyB.diaMm),
        )
        // A merged body has one keyway slot; prefer A's, fall back to B's.
        merged = carryBodyKeyway(bodyA, merged)
        if (!merged.hasKeyway) merged = carryBodyKeyway(bodyB, merged)
        newBodies += merged
    } else if (bodyA != null) {
        // Component was at the FWD end; expand A to fill the freed span.
        // Re-anchor the keyway to the new span so a FWD-referenced offset doesn't drift.
        newBodies -= bodyA
        removedIds += bodyA.id
        val id = genId()
        addedIds += id
        newBodies += carryBodyKeyway(
            from = bodyA,
            to = bodyA.withoutKeyway().copy(id = id, lengthMm = compEnd - bodyA.startFromAftMm)
        )
    } else if (bodyB != null) {
        // Component was at the AFT end; expand B back to fill the freed span
        newBodies -= bodyB
        removedIds += bodyB.id
        val id = genId()
        addedIds += id
        newBodies += carryBodyKeyway(
            from = bodyB,
            to = bodyB.withoutKeyway().copy(id = id, startFromAftMm = compStart, lengthMm = bodyB.startFromAftMm + bodyB.lengthMm - compStart)
        )
    }

    return BodySplitResult(copy(bodies = newBodies), removedIds, addedIds)
}
