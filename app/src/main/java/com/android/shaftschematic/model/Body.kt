package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Cylindrical body section (constant diameter).
 *
 * Units: **mm** (millimeters).
 *
 * @property startFromAftMm Distance from AFT face toward FWD where this body starts.
 * @property lengthMm Axial length of the body.
 * @property diaMm Outer diameter of the body.
 * @property keywayWidthMm Optional keyway width (0 = none).
 * @property keywayDepthMm Optional keyway depth (0 = none).
 * @property keywayLengthMm Optional keyway length (0 = none).
 * @property keywayOffsetFromEndMm Axial distance from the referenced body end face
 *   ([keywayEnd]) to the near edge of the keyway slot.
 *   0 = open keyway (starts at the end face, open-ended there).
 *   > 0 = floating keyway (inset from the face, rounded at both ends).
 * @property keywayEnd Which body end face the keyway is referenced from (AFT or FWD).
 *   Intermediate shafts with fitted couplings carry keyways at a shaft end that is a
 *   plain cylindrical body — this picks the face the offset is measured from.
 * @property keywaySpooned Whether the open keyway's closed (LET) end is spooned — an enlarged
 *   circle drawn around the mill end (which stays as an inner reference line).
 *   Ignored when [keywayOffsetFromEndMm] > 0 (floating keyways have no open end to reference).
 * @property showDiaOnDrawing Whether this body's Ø prints as a below-shaft callout on the
 *   schematic. Draw-only flag: it changes nothing in the model, resolve, OAL, collision, or
 *   footer geometry, and never rewrites [diaMm]. Defaults OFF (on-device preference): body Ø
 *   callouts are opt-in per card, so the schematic stays clean unless a Ø is deliberately
 *   shown — the footer's "Body:" list still always carries every Ø. When several shown
 *   bodies share a Ø, the callout anchors at the longest of them.
 * @property blendAftMm Axial length of a machined **blend** cut into this body's AFT face
 *   (0 = a square face). The blend runs INWARD from the face, easing from the neighbouring
 *   component's diameter at the face to [diaMm] this far in, so it is machined entirely out
 *   of this body and never moves or trims any other component. Silhouette only: it carries
 *   no dimension rail and no footer row, and rails keep dimensioning the stored span — you
 *   dimension to the theoretical sharp corner and let the drawn curve show the blend.
 * @property blendFwdMm The same for this body's FWD face.
 * @property blendProfile How both faces ease. Drawing-only, like the blend lengths.
 */
@Serializable
data class Body(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    override val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
    val keywayWidthMm: Float = 0f,
    val keywayDepthMm: Float = 0f,
    val keywayLengthMm: Float = 0f,
    val keywayOffsetFromEndMm: Float = 0f,
    val keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
    val keywaySpooned: Boolean = false,
    val showDiaOnDrawing: Boolean = false,
    val blendAftMm: Float = 0f,
    val blendFwdMm: Float = 0f,
    val blendProfile: BlendProfile = BlendProfile.OGEE,
    /** Optional user-defined label for display (not used for geometry). */
    val label: String? = null,
) : Segment

/** Basic invariants for a Body. */
fun Body.isValid(overallLengthMm: Float): Boolean =
    isWithin(overallLengthMm) &&
        diaMm >= 0f &&
        keywayWidthMm >= 0f &&
        keywayDepthMm >= 0f &&
        keywayLengthMm >= 0f &&
        keywayOffsetFromEndMm >= 0f &&
        blendAftMm >= 0f &&
        blendFwdMm >= 0f &&
        (keywayOffsetFromEndMm + keywayLengthMm) <= lengthMm

/** True if this body has a keyway defined (all three dimensions non-zero). */
val Body.hasKeyway: Boolean get() = keywayWidthMm > 0f && keywayDepthMm > 0f && keywayLengthMm > 0f

/**
 * Absolute AFT-origin axial span of this body's keyway, or null when the body has no
 * keyway. Resolves the AFT/FWD end-face reference to physical space.
 */
fun Body.keywayAbsSpanMm(): KeywaySpan? {
    if (!hasKeyway) return null
    return when (keywayEnd) {
        LinerAuthoredReference.AFT -> {
            val near = startFromAftMm + keywayOffsetFromEndMm
            KeywaySpan(near, near + keywayLengthMm)
        }
        LinerAuthoredReference.FWD -> {
            val near = startFromAftMm + lengthMm - keywayOffsetFromEndMm
            KeywaySpan(near - keywayLengthMm, near)
        }
    }
}

/** Strips all keyway fields (used when a geometry change makes the keyway unplaceable). */
fun Body.withoutKeyway(): Body = copy(
    keywayWidthMm = 0f,
    keywayDepthMm = 0f,
    keywayLengthMm = 0f,
    keywayOffsetFromEndMm = 0f,
    keywaySpooned = false,
)

/**
 * True if this body asks for a blend on the given face. A stored length longer than the body
 * still reads as "blended" — the draw sites clamp it, because clamping a drawn curve is not
 * the same as rewriting what the user typed.
 */
fun Body.hasBlendOn(end: LinerAuthoredReference): Boolean = when (end) {
    LinerAuthoredReference.AFT -> blendAftMm > 0f
    LinerAuthoredReference.FWD -> blendFwdMm > 0f
}

/** Stored blend length on the given face (mm); 0 = a square face. */
fun Body.blendMmOn(end: LinerAuthoredReference): Float = when (end) {
    LinerAuthoredReference.AFT -> blendAftMm
    LinerAuthoredReference.FWD -> blendFwdMm
}
