// app/src/main/java/com/android/shaftschematic/model/ShaftSpec.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * Root aggregate for a shaft specification.
 *
 * Units: **mm** (millimeters).
 *
 * All axial positions are measured AFT → FWD. Overlaps are permitted; call [validate] for
 * basic bounds checks. Helper methods provide coverage, free length, and max OD summaries.
 *
 * @property overallLengthMm Total shaft length AFT → FWD.
 * @property bodies Constant-diameter cylindrical segments.
 * @property tapers Linear transitions between diameters.
 * @property threads External threaded segments.
 * @property liners Outer sleeves/liners.
 * @property couplerBoltSlots Radial muff-coupler bolt cutouts (reference features; never
 *           affect OAL/coverage). Optional — defaults empty for back-compat.
 * @property keyways180Apart Annotation that the shaft's keyways are clocked 180° apart
 *           from each other (e.g. body keyway opposite the taper keyway on an
 *           intermediate shaft). Pure drawing note — no geometric effect. Only
 *           meaningful when the shaft has ≥ 2 keyways. Defaults false for back-compat.
 * @property keyways90Apart Annotation that the shaft's keyways are clocked 90° apart from
 *           each other. Pure drawing note — no geometric effect on OAL, resolve, collision,
 *           or footer geometry. Mutually exclusive with [keyways180Apart]; only meaningful
 *           when the shaft has ≥ 2 keyways. Defaults false for back-compat.
 * @property keyways90Cw Direction of the 90° clocking **viewed from aft**: true = clockwise,
 *           false = counter-clockwise. Only meaningful while [keyways90Apart] is set.
 *           Defaults true for back-compat.
 * @property autoBodyDiaMm User-set bare-shaft diameter applied to ALL auto-body spans (mm).
 *           The shaft between explicit components is one piece of stock, so a single value
 *           covers every auto span. 0 = unset → each span derives its Ø from neighbors as
 *           before. Affects drawn diameter only — auto-span positioning stays derived.
 *           Defaults 0 for back-compat.
 * @property showAutoBodyDia Whether the bare-shaft Ø prints as a below-shaft callout on the
 *           schematic. One flag for ALL auto spans, matching the single [autoBodyDiaMm] —
 *           the shaft between explicit components is one piece of stock, so it carries one
 *           visibility. Draw-only: never affects OAL, resolve, collision, or footer geometry.
 *           Defaults true for back-compat.
 */
@Serializable
data class ShaftSpec(
    val overallLengthMm: Float = 0f,
    val bodies: List<Body> = emptyList(),
    val tapers: List<Taper> = emptyList(),
    val threads: List<Threads> = emptyList(),
    val liners: List<Liner> = emptyList(),
    val couplerBoltSlots: List<CouplerBoltSlot> = emptyList(),
    val keyways180Apart: Boolean = false,
    val keyways90Apart: Boolean = false,
    val keyways90Cw: Boolean = true,
    val autoBodyDiaMm: Float = 0f,
    val showAutoBodyDia: Boolean = true,
)

/**
 * How a shaft's **secondary** keyways are clocked relative to the aft-most (primary) one.
 *
 * Pure drawing note in every mode — nothing here affects OAL, resolve, collision, or footer
 * geometry; it only changes how the secondary keyways are *drawn* and which footer note prints.
 *
 * Drawing convention (load-bearing — both draw sites depend on it):
 * the profile draws AFT at x = 0 on the **left**, and the primary (aft-most) keyway is drawn
 * face-on, i.e. on the near side facing the page viewer. An observer standing at the AFT end
 * looking forward therefore has the primary keyway at their **3 o'clock**, so:
 * - [DEG_90_CW] (90° clockwise viewed from aft) → observer's 6 o'clock → the **bottom**
 *   silhouette edge of the drawing,
 * - [DEG_90_CCW] → observer's 12 o'clock → the **top** silhouette edge.
 *
 * At 90° the keyway is seen edge-on, so it is drawn as a notch cut into that silhouette edge
 * (its **depth** is what shows in profile) rather than as a plan-view slot. At [DEG_180] the
 * secondary keyway faces directly away from the viewer and is drawn as a hidden (dashed)
 * plan-view slot instead — see [hiddenKeywayHostIds].
 *
 * The `keywaySpooned` flag is ignored at 90°: the spoon bowl is a face-view construct with no
 * edge-on projection, so a spooned secondary draws as a plain notch.
 */
enum class KeywayClocking { NONE, DEG_180, DEG_90_CW, DEG_90_CCW }

/**
 * Resolved keyway clocking note for this shaft.
 *
 * [ShaftSpec.keyways180Apart] and [ShaftSpec.keyways90Apart] are mutually exclusive through the
 * ViewModel setters; 180° wins if a hand-edited document sets both, so a stale file can never
 * produce two clocking notes.
 */
fun ShaftSpec.keywayClocking(): KeywayClocking = when {
    keyways180Apart -> KeywayClocking.DEG_180
    keyways90Apart  -> if (keyways90Cw) KeywayClocking.DEG_90_CW else KeywayClocking.DEG_90_CCW
    else            -> KeywayClocking.NONE
}

/**
 * Host component IDs whose keyway is a **secondary** (clocked away from the datum) keyway.
 *
 * The keyway nearest the AFT face (smallest absolute [keywayAbsSpanMm] center) is the shop
 * measurement datum and stays primary; every other keyway is secondary. Empty when there is no
 * clocking note ([keywayClocking] == [KeywayClocking.NONE]) or fewer than 2 keyways — too few
 * for "apart from each other" to mean anything.
 *
 * How a secondary renders depends on the mode: [KeywayClocking.DEG_180] draws it as a hidden
 * dashed plan-view slot ([hiddenKeywayHostIds]); the 90° modes draw it as a silhouette-edge
 * notch (`geom/KeywaySilhouetteMath.kt`).
 */
fun ShaftSpec.secondaryKeywayHostIds(): Set<String> {
    if (keywayClocking() == KeywayClocking.NONE) return emptySet()
    val centers = buildList {
        tapers.forEach { t -> t.keywayAbsSpanMm()?.let { add(t.id to it.centerMm) } }
        bodies.forEach { b -> b.keywayAbsSpanMm()?.let { add(b.id to it.centerMm) } }
    }
    if (centers.size < 2) return emptySet()
    val nearId = centers.minByOrNull { it.second }!!.first
    return centers.asSequence().map { it.first }.filterNot { it == nearId }.toSet()
}

/**
 * Returns a copy with the 180°-apart drawing note set to [enabled], clearing the 90° note when
 * enabling (the two clocking notes are mutually exclusive). Returns `this` unchanged when
 * nothing moves, so a no-op set never emits new state or marks the document dirty.
 */
fun ShaftSpec.withKeyways180Apart(enabled: Boolean): ShaftSpec {
    val clear90 = enabled && keyways90Apart
    if (keyways180Apart == enabled && !clear90) return this
    return copy(keyways180Apart = enabled, keyways90Apart = if (enabled) false else keyways90Apart)
}

/**
 * Returns a copy with the 90°-apart drawing note set to [enabled], clearing the 180° note when
 * enabling (the two clocking notes are mutually exclusive). Returns `this` unchanged when
 * nothing moves. [keyways90Cw] is left alone — the chosen direction survives toggling the note
 * off and back on.
 */
fun ShaftSpec.withKeyways90Apart(enabled: Boolean): ShaftSpec {
    val clear180 = enabled && keyways180Apart
    if (keyways90Apart == enabled && !clear180) return this
    return copy(keyways90Apart = enabled, keyways180Apart = if (enabled) false else keyways180Apart)
}

/**
 * Returns a copy with the 90° clocking direction set to [cw] (clockwise viewed from aft).
 * Returns `this` unchanged when the direction already matches.
 */
fun ShaftSpec.withKeyways90Cw(cw: Boolean): ShaftSpec =
    if (keyways90Cw == cw) this else copy(keyways90Cw = cw)

/** Number of keyways defined across all components (tapers + bodies). */
fun ShaftSpec.keywayCount(): Int =
    tapers.count { it.hasKeyway } + bodies.count { it.hasKeyway }

/**
 * Host component IDs whose keyway should render as **hidden lines** (far-side, dashed).
 *
 * The [KeywayClocking.DEG_180] slice of [secondaryKeywayHostIds]: a keyway clocked 180° away
 * faces directly off the back of the shaft, so it is drawn as a dashed plan-view slot with no
 * void fill. Empty in the 90° modes — those secondaries are drawn as silhouette-edge notches
 * instead, never hidden-dashed — and empty when there is no clocking note or < 2 keyways.
 */
fun ShaftSpec.hiddenKeywayHostIds(): Set<String> =
    if (keywayClocking() == KeywayClocking.DEG_180) secondaryKeywayHostIds() else emptySet()

/**
 * Basic non-overlap validation: checks non-negative fields and that each segment is within
 * [overallLengthMm]. Does not test for intersections or sequencing constraints.
 */
fun ShaftSpec.validate(): Boolean {
    if (overallLengthMm < 0f) return false
    if (!bodies.all { it.isValid(overallLengthMm) }) return false
    if (!tapers.all { it.isValid(overallLengthMm) }) return false
    if (!threads.all { it.isValid(overallLengthMm) }) return false
    if (!liners.all { it.isValid(overallLengthMm) }) return false
    if (!couplerBoltSlots.all { it.isValid(overallLengthMm) }) return false
    return true
}

/** Farthest occupied axial end across all components (mm). */
fun ShaftSpec.coverageEndMm(): Float {
    val bodyEnd   = bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val taperEnd  = tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val linerEnd  = liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm } ?: 0f
    val threadEnd = threads.filter { !it.excludeFromOAL }
                          .maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: 0f
    return maxOf(bodyEnd, taperEnd, linerEnd, threadEnd)
}

/** Remaining free axial distance from coverage end to [overallLengthMm] (≥ 0). */
fun ShaftSpec.freeToEndMm(): Float =
    (overallLengthMm - coverageEndMm()).coerceAtLeast(0f)

/**
 * Returns a copy where every excluded thread's [Threads.startFromAftMm] is placed
 * immediately outside the shaft span so it renders adjacent to — but never overlapping —
 * any shaft feature:
 *  - AFT end → startFromAftMm = −lengthMm   (thread ends flush with the AFT face at 0)
 *  - FWD end → startFromAftMm = overallLengthMm  (thread starts flush with the FWD face)
 *
 * [ShaftLayout] expands its coordinate window to accommodate these negative / over-OAL
 * positions, so both the preview and PDF show the threads outside the dimensioned span.
 *
 * Called whenever OAL or the excluded-thread topology changes.
 */
fun ShaftSpec.syncExcludedThreadPositions(): ShaftSpec {
    val oal = overallLengthMm
    val synced = threads.map { th ->
        if (!th.excludeFromOAL) th
        else {
            val newStart = if (th.isAftEnd) -th.lengthMm else oal
            if (th.startFromAftMm == newStart) th else th.copy(startFromAftMm = newStart)
        }
    }
    return if (synced == threads) this else copy(threads = synced)
}

/** Maximum outer diameter observed across all components (mm). */
fun ShaftSpec.maxOuterDiaMm(): Float {
    var maxDia = 0f
    bodies.forEach  { maxDia = maxOf(maxDia, it.diaMm) }
    tapers.forEach  { maxDia = maxOf(maxDia, it.maxDiaMm) }
    threads.forEach { maxDia = maxOf(maxDia, it.majorDiaMm) }
    liners.forEach  { maxDia = maxOf(maxDia, it.odMm) }
    return maxDia
}

/**
 * Back-compat normalization:
 * returns a copy where each [Threads] has both [Threads.pitchMm] and [Threads.tpi]
 * populated when either one is present and > 0. Call this after JSON decode.
 */
fun ShaftSpec.normalized(): ShaftSpec =
    copy(
        threads = threads.map { it.normalized() },
        liners = liners.map { it.normalized() }
    )
