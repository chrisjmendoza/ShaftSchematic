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
 * @property showNameOnDrawing Whether this body's name prints as a component label under the
 *   schematic. Tri-state, draw-only (never rewrites [label]): `null` — the default — follows
 *   the Settings switch ("Show component titles in PDF"), so a document saved before the flag
 *   existed prints exactly as its setting says; an explicit `true` prints THIS name even with
 *   that switch off (on-device report: a freshly checked card toggle did nothing under a
 *   global switch turned off long before); an explicit `false` hides this name even with it
 *   on. The per-sheet export option (template mode) still gates the whole pass. The field
 *   name is deliberately fresh (`showNameOnDrawing`; the retired `showLabelOnDrawing` key is
 *   ignored at decode): the flag's first build blanket-serialized `true` under the old key on
 *   every component of every saved document, and honoring those stamps as authored overrides
 *   made one checked toggle appear to turn every label on (on-device report). A stored value
 *   under THIS key is always an authored choice.
 * @property compressOnDrawing Whether this body may foreshorten on a sheet. Draw-only: it
 *   changes nothing in the model, resolve, OAL, collision, or footer geometry, and never
 *   rewrites a stored span. `false` pins the body's stored span at true scale in the
 *   compression solve — the keyway-window posture, so the drawn HEIGHT yields around it —
 *   and suppresses its S-break, the long-span trigger included. The **serialization**
 *   default is `true`: a document saved before this flag existed keeps compressing exactly
 *   as it does today, since re-pinning a saved long shaft could leave it unrenderable.
 *   Authoring surfaces create explicit bodies with `false` — an authored section reads at
 *   true proportion unless its author re-enables compression (on-device request), and the
 *   card's "Compress on drawing" checkbox is that escape hatch for a body big enough that
 *   pinning it would starve the rest of the shaft.
 * @property blendAftMm Axial length of a machined **blend** cut into this body's AFT face
 *   (0 = a square face). The blend runs INWARD from the face, easing from the neighbouring
 *   component's diameter at the face to [diaMm] this far in, so it is machined entirely out
 *   of this body and never moves or trims any other component. Silhouette only: it carries
 *   no dimension rail and no footer row, and rails keep dimensioning the stored span — you
 *   dimension to the theoretical sharp corner and let the drawn curve show the blend.
 * @property blendFwdMm The same for this body's FWD face.
 * @property blendAftSeal Whether the AFT blend carries a **seal area** — the radius cuts the
 *   fiberglass seats into, drawn as [com.android.shaftschematic.geom.SEAL_GROOVE_COUNT] lines
 *   across the curve. A schematic cue, not a machining count; ignored when that face has no
 *   blend, since the grooves are cut INTO the blended section.
 * @property blendFwdSeal The same for the FWD blend.
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
    val showNameOnDrawing: Boolean? = null,
    val compressOnDrawing: Boolean = true,
    val blendAftMm: Float = 0f,
    val blendFwdMm: Float = 0f,
    val blendAftSeal: Boolean = false,
    val blendFwdSeal: Boolean = false,
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

/** Whether the blend on the given face carries seal grooves. */
fun Body.blendSealOn(end: LinerAuthoredReference): Boolean = when (end) {
    LinerAuthoredReference.AFT -> blendAftSeal
    LinerAuthoredReference.FWD -> blendFwdSeal
}
