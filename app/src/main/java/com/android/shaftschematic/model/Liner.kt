package com.android.shaftschematic.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

enum class LinerAuthoredReference { AFT, FWD }

/**
 * Cylindrical liner/sleeve on the shaft (outer diameter only).
 *
 * Units: **mm** (millimeters).
 *
 * Geometry is stored as physical shaft coordinates in mm.
 * Authoring reference only affects the UI display of the start value.
 *
 * @property startMmPhysical Physical start position from AFT.
 * @property endMmPhysical Physical end position from AFT.
 * @property lengthMm Axial length of the liner.
 * @property odMm Outside diameter of the liner.
 * @property authoredReference AFT or FWD reference used for authoring display.
 * @property showDiaOnDrawing Whether this liner's OD prints as a below-shaft callout on the
 *   schematic. Draw-only flag — mirrors [com.android.shaftschematic.model.Body.showDiaOnDrawing];
 *   it never rewrites [odMm] and never touches geometry. Defaults true for back-compat.
 *
 * Shoulders: a machined step at a liner end — the OD drops to a reduced diameter over the
 * outermost [shoulderAftLenMm]/[shoulderFwdLenMm] of the liner's OWN span (cut into the liner,
 * the blend posture: no other component's span moves, drawn or stored). A shoulder exists on an
 * end when its length AND reduced OD are both > 0; every value is typed and stored verbatim
 * (golden rule). The radius is the fillet where the shoulder's step face meets the liner OD —
 * picked from a standard list, drawn as a small arc, and printed only as a footer note.
 * All fields default 0 so documents that never touch shoulders decode byte-identical.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Liner(
    override val id: String = UUID.randomUUID().toString(),
    @kotlinx.serialization.SerialName("startMmPhysical")
    @JsonNames("startFromAftMm", "startMmPhysical")
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val odMm: Float = 0f,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
    val authoredReference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    @JsonNames("endFromAftMm", "endMmPhysical")
    val endMmPhysical: Float = 0f,
    val showDiaOnDrawing: Boolean = true,
    val shoulderAftLenMm: Float = 0f,
    val shoulderAftOdMm: Float = 0f,
    val shoulderAftRadiusMm: Float = 0f,
    val shoulderFwdLenMm: Float = 0f,
    val shoulderFwdOdMm: Float = 0f,
    val shoulderFwdRadiusMm: Float = 0f,
) : Segment

/** One end's shoulder, or null when that end has none (needs BOTH length and OD > 0). */
data class LinerShoulder(val lenMm: Float, val odMm: Float, val radiusMm: Float)

fun Liner.shoulderOn(end: LinerAuthoredReference): LinerShoulder? {
    val (len, od, r) = when (end) {
        LinerAuthoredReference.AFT -> Triple(shoulderAftLenMm, shoulderAftOdMm, shoulderAftRadiusMm)
        LinerAuthoredReference.FWD -> Triple(shoulderFwdLenMm, shoulderFwdOdMm, shoulderFwdRadiusMm)
    }
    if (len <= 0f || od <= 0f) return null
    return LinerShoulder(lenMm = len, odMm = od, radiusMm = r)
}

/** True when either end carries a shoulder — what keeps the card's controls visible with the capability gate off. */
fun Liner.hasShoulder(): Boolean =
    shoulderOn(LinerAuthoredReference.AFT) != null || shoulderOn(LinerAuthoredReference.FWD) != null

/** Normalize to ensure endMmPhysical matches start + length. */
fun Liner.normalized(): Liner {
    val computedEnd = startFromAftMm + lengthMm
    val end = if (endMmPhysical <= 0f && computedEnd > 0f) computedEnd else endMmPhysical
    return if (kotlin.math.abs(end - computedEnd) > 1e-3f) {
        copy(endMmPhysical = computedEnd)
    } else {
        copy(endMmPhysical = end)
    }
}

/** Copy with updated physical geometry (end is derived from start + length). */
fun Liner.withPhysical(startMmPhysical: Float, lengthMm: Float, odMm: Float): Liner =
    copy(
        startFromAftMm = startMmPhysical,
        lengthMm = lengthMm,
        odMm = odMm,
        endMmPhysical = startMmPhysical + lengthMm
    )

/** Basic invariants for a Liner. */
fun Liner.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) && odMm >= 0f
