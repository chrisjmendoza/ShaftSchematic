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
 *  • Keep names literal to what they return (end positions).
 *
 * Functions
 *  • lastOccupiedEndMm(): Float
 *      Returns the furthest axial end (mm from aft face, x=0) occupied by any component.
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
fun ShaftSpec.collidingIds(): Set<String> =
    collisionParticipants().let { parts ->
        val result = mutableSetOf<String>()
        for (i in parts.indices) for (j in i + 1 until parts.size) {
            val a = parts[i]; val b = parts[j]
            if (spansOverlapMm(a.startFromAftMm, a.lengthMm, b.startFromAftMm, b.lengthMm)) {
                result += a.id; result += b.id
            }
        }
        result
    }

/**
 * The components [id] overlaps, by the same rule as [collidingIds] — every taper, OAL-counted
 * thread and liner against every other — ordered AFT → FWD. Empty when [id] collides with
 * nothing (or is a body, an excluded thread or not a component). The card's overlap chip and
 * the Add dialogs name these partners by their card titles, so "overlaps" always says WHAT.
 */
fun ShaftSpec.collisionPartnerIds(id: String): List<String> {
    val parts = collisionParticipants()
    val self = parts.firstOrNull { it.id == id } ?: return emptyList()
    return collisionPartnerIdsForSpan(self.startFromAftMm, self.lengthMm, excludeId = id)
}

/**
 * The components a span [startMm] … [startMm] + [lengthMm] would overlap if it were a taper,
 * OAL-counted thread or liner — [collidingIds]' rule applied to a span that is not stored yet
 * (an Add dialog's proposal, a card's pending edit). [excludeId] is the span's own component,
 * if it has one. Ordered AFT → FWD.
 */
fun ShaftSpec.collisionPartnerIdsForSpan(
    startMm: Float,
    lengthMm: Float,
    excludeId: String? = null,
): List<String> =
    collisionParticipants()
        .filter { it.id != excludeId && spansOverlapMm(startMm, lengthMm, it.startFromAftMm, it.lengthMm) }
        .sortedWith(compareBy<Segment>({ it.startFromAftMm }, { it.id }))
        .map { it.id }

/** Tapers, OAL-counted threads and liners — the kinds that collide. Bodies are fillers. */
private fun ShaftSpec.collisionParticipants(): List<Segment> =
    tapers + threads.filter { !it.excludeFromOAL } + liners

/** Strict overlap of two axial spans; endpoints touching within 1e-3 mm are allowed. */
private fun spansOverlapMm(aStart: Float, aLen: Float, bStart: Float, bLen: Float): Boolean {
    val eps = 1e-3f
    val aEnd = aStart + aLen
    val bEnd = bStart + bLen
    return (aStart < bEnd - eps) && (aEnd > bStart + eps)
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
 *
 * The authored FWD distance is recovered from the stored start, and that recovery is where a
 * long shaft picks up noise: at 9 m a `Float` resolves to about 0.001 mm, so `oldOal − start −
 * length` for a taper flush with the FWD face comes back as a few thousandths of a millimetre
 * rather than 0, and re-deriving the start from it pushed the taper past the shaft end by that
 * much — the card printed a stray offset and the "past the shaft end" warning lit (on-device
 * report, an OAL edit that passed through an intermediate value). The arithmetic therefore runs
 * in `Double` and a recovered distance within [OAL_REANCHOR_SNAP_MM] of zero is taken as the
 * zero it was authored as — a derivation cleanup, not a rewrite of a typed value: nobody authors
 * a 0.005 mm offset from a face.
 */
/**
 * A recovered FWD distance closer to zero than this (mm) is float noise from the stored start,
 * not an authored offset. Ten times the `Float` resolution at a 9 m shaft, a hundredth of the
 * finest value anyone types.
 */
const val OAL_REANCHOR_SNAP_MM = 0.01

fun ShaftSpec.withNewOal(newOal: Float): ShaftSpec {
    val clampedOal = newOal.coerceAtLeast(0f)
    val oldOal = overallLengthMm.toDouble()

    /** The new start of a FWD-anchored span whose stored start was [startMm] and whose anchored extent is [spanMm]. */
    fun reanchoredStart(startMm: Float, spanMm: Float): Float {
        val recovered = oldOal - startMm - spanMm
        val authoredFromFwd = if (abs(recovered) < OAL_REANCHOR_SNAP_MM) 0.0 else recovered
        return (clampedOal - authoredFromFwd - spanMm).toFloat()
    }

    val newTapers = tapers.map { t ->
        if (t.authoredReference == LinerAuthoredReference.FWD) {
            t.copy(startFromAftMm = reanchoredStart(t.startFromAftMm, t.lengthMm))
        } else {
            t
        }
    }

    val newLiners = liners.map { ln ->
        if (ln.authoredReference == LinerAuthoredReference.FWD) {
            ln.withPhysical(
                startMmPhysical = reanchoredStart(ln.startFromAftMm, ln.lengthMm),
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
            cs.copy(startFromAftMm = reanchoredStart(cs.startFromAftMm, rowSpan))
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

// ---- Body edit -----------------------------------------------------------------

/**
 * Returns a copy with the body at [index] set to the given geometry, or `this` unchanged
 * when [index] is out of range. Length and Ø are clamped to ≥ 0; [startMm] is kept
 * **verbatim** (authored positions are sacred — golden rule). Keyway fields, id, and
 * label are untouched.
 */
fun ShaftSpec.withBodyAt(index: Int, startMm: Float, lengthMm: Float, diaMm: Float): ShaftSpec {
    val old = bodies.getOrNull(index) ?: return this
    return copy(
        bodies = bodies.toMutableList().also { list ->
            list[index] = old.copy(
                startFromAftMm = startMm,
                lengthMm = max(0f, lengthMm),
                diaMm = max(0f, diaMm),
            )
        }
    )
}

// ---- Body split / merge --------------------------------------------------------

/**
 * Carries the spec mutated by a split or merge, plus the IDs the operation removed from and
 * added to the bodies list — the record of which stored bodies were replaced (pinned by
 * `BodySplitMergeTest`).
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
 * Carries [from]'s **draw-only** decisions — name, Ø/name visibility, the shade override, the
 * compression opt-out, and the length-rail override — onto [to] (a fragment/merge/expansion of the same material). Nothing
 * here is geometry, so unlike the keyway there is no fit test: every piece of one authored
 * body prints the way that body was authored to print.
 *
 * The one-sided expansions in [mergeBodiesAround] already `copy()` the source and so keep
 * these implicitly; the fragment and two-sided-merge paths build a fresh [Body] and need
 * this. A merge takes the AFT fragment's values — aft is authored first.
 */
internal fun carryBodyDisplay(from: Body, to: Body): Body = to.copy(
    label = from.label,
    showDiaOnDrawing = from.showDiaOnDrawing,
    showNameOnDrawing = from.showNameOnDrawing,
    shadeOnDrawing = from.shadeOnDrawing,
    compressOnDrawing = from.compressOnDrawing,
    showDimensionRail = from.showDimensionRail,
)

/**
 * [to] with the blend authored on ONE face of [from] — that face's blend length, seal area,
 * seal length and liner target — plus [from]'s blend profile. A face is a property of the body
 * END it sits on: a split hands the AFT face to the AFT fragment and the FWD face to the FWD
 * fragment (the two faces cut at the component are square, as any new face is), and a merge
 * takes its AFT face from the AFT fragment and its FWD face from the FWD one. Dropping the blends
 * on a split silently squared a face the user had authored.
 */
internal fun carryBodyBlendFace(from: Body, to: Body, face: LinerAuthoredReference): Body = when (face) {
    LinerAuthoredReference.AFT -> to.copy(
        blendAftMm = from.blendAftMm,
        blendAftSeal = from.blendAftSeal,
        blendAftSealLenMm = from.blendAftSealLenMm,
        blendAftToLinerOd = from.blendAftToLinerOd,
        blendProfile = from.blendProfile,
    )
    LinerAuthoredReference.FWD -> to.copy(
        blendFwdMm = from.blendFwdMm,
        blendFwdSeal = from.blendFwdSeal,
        blendFwdSealLenMm = from.blendFwdSealLenMm,
        blendFwdToLinerOd = from.blendFwdToLinerOd,
        blendProfile = from.blendProfile,
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
                to = carryBodyBlendFace(
                    body,
                    carryBodyDisplay(
                        body,
                        Body(id = id, startFromAftMm = bodyStart, lengthMm = compStart - bodyStart, diaMm = body.diaMm),
                    ),
                    LinerAuthoredReference.AFT,
                )
            )
        }
        // Right fragment: from where the component ends to body end
        if (compEnd < bodyEnd - eps) {
            val id = genId()
            addedIds  += id
            newBodies += carryBodyKeyway(
                from = body,
                to = carryBodyBlendFace(
                    body,
                    carryBodyDisplay(
                        body,
                        Body(id = id, startFromAftMm = compEnd, lengthMm = bodyEnd - compEnd, diaMm = body.diaMm),
                    ),
                    LinerAuthoredReference.FWD,
                )
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
 * The merged body spans A.start → B.end and keeps the diameter both fragments already
 * share — the merge is **skipped** when the two diameters differ, because inventing one
 * would silently rewrite a stored diameter the user typed. Both bodies
 * then stay put and the freed span is auto-filled by the resolve layer. The dominant
 * round-trip case still restores a single body: [splitBodiesAround] fragments inherit the
 * parent's [Body.diaMm] verbatim, so they always agree.
 *
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

        // Never invent a diameter: only fragments that already agree may be fused. Differing
        // diameters mean these are two distinct stored sections, not two halves of one body.
        if (abs(bodyA.diaMm - bodyB.diaMm) >= 1e-3f) return BodySplitResult(this, emptyList(), emptyList())

        newBodies -= bodyA
        newBodies -= bodyB
        removedIds += listOf(bodyA.id, bodyB.id)
        val id = genId()
        addedIds += id
        // Draw-only decisions come from A: aft is authored first, so its name and its
        // visibility/compression flags are the ones the user set on the original body. The
        // faces come from the fragment that holds each: A's AFT face, B's FWD face.
        var merged = carryBodyDisplay(
            bodyA,
            Body(
                id             = id,
                startFromAftMm = bodyA.startFromAftMm,
                lengthMm       = (bodyB.startFromAftMm + bodyB.lengthMm) - bodyA.startFromAftMm,
                diaMm          = bodyA.diaMm,   // == bodyB.diaMm, guaranteed by the guard above
            ),
        )
        // FWD from B first, AFT from A last, so the ONE profile is A's — authored first, like
        // the display decisions above.
        merged = carryBodyBlendFace(bodyB, merged, LinerAuthoredReference.FWD)
        merged = carryBodyBlendFace(bodyA, merged, LinerAuthoredReference.AFT)
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
