package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Taper
import kotlin.math.max

/**
 * UI-only mapping for taper end labels.
 *
 * Shop semantics:
 * - SET = Small End of Taper
 * - LET = Large End of Taper
 *
 * Model semantics (unchanged): startDiaMm/endDiaMm are the left→right diameters along the taper.
 * Therefore the UI must flip labels for FWD tapers, without swapping stored values.
 *
 * ONE convention decides which face is the Small End: the taper's **physical half** of the
 * shaft (midpoint ≤ OAL/2 → AFT taper, SET at its AFT face). Labels, rate derivation and the
 * add path all read [classifyTaperSideByMidpoint]; keying any of them on something else (the
 * Add dialog's measure-from chip, say) makes writer and reader disagree and the taper stores
 * backwards.
 */

enum class TaperSide { AFT, FWD }

enum class TaperEndProp { START_DIA, END_DIA }

data class TaperSetLetMapping(
    val side: TaperSide,
    val leftCode: String,
    val rightCode: String,
    val leftBindsTo: TaperEndProp,
    val rightBindsTo: TaperEndProp,
)

fun taperSetLetMapping(taper: Taper, overallLengthMm: Float): TaperSetLetMapping {
    val side = classifyTaperSideByMidpoint(taper, overallLengthMm)

    // Binding stays x-ordered: left edits startDiaMm, right edits endDiaMm.
    // Only the labels swap for FWD tapers.
    return when (side) {
        TaperSide.AFT -> TaperSetLetMapping(
            side = side,
            leftCode = "S.E.T.",
            rightCode = "L.E.T.",
            leftBindsTo = TaperEndProp.START_DIA,
            rightBindsTo = TaperEndProp.END_DIA
        )
        TaperSide.FWD -> TaperSetLetMapping(
            side = side,
            leftCode = "L.E.T.",
            rightCode = "S.E.T.",
            leftBindsTo = TaperEndProp.START_DIA,
            rightBindsTo = TaperEndProp.END_DIA
        )
    }
}

internal fun classifyTaperSideByMidpoint(taper: Taper, overallLengthMm: Float): TaperSide =
    classifyTaperSideByMidpoint(taper.startFromAftMm, taper.lengthMm, overallLengthMm)

/**
 * Side classification from a raw placement, for callers that hold a span rather than a
 * [Taper] — the Add dialog decides which face SET lands on before the taper exists.
 * Falls back to AFT when the OAL is unknown (0).
 */
internal fun classifyTaperSideByMidpoint(
    startFromAftMm: Float,
    lengthMm: Float,
    overallLengthMm: Float,
): TaperSide {
    if (overallLengthMm <= 0f) return TaperSide.AFT
    val midMm = startFromAftMm + lengthMm * 0.5f
    return if (midMm <= overallLengthMm * 0.5f) TaperSide.AFT else TaperSide.FWD
}

/**
 * The OAL the shaft carries once a taper spanning [startFromAftMm] … + [lengthMm] exists —
 * the frame the carousel labels, the renderer and the keyway math read for that taper.
 *
 * In auto-OAL mode the shaft grows to cover a component that runs past the current end; a
 * manual OAL stands as authored. Classifying the taper's half against the *pre-add* OAL
 * would seat SET on the wrong face whenever the add itself grows the shaft (on a blank
 * shaft the pre-add OAL is 0, so every taper would classify AFT).
 */
internal fun oalAfterTaperAddMm(
    currentOalMm: Float,
    overallIsManual: Boolean,
    startFromAftMm: Float,
    lengthMm: Float,
): Float =
    if (overallIsManual) currentOalMm
    else max(currentOalMm, startFromAftMm + max(0f, lengthMm))

/**
 * The diameter pair to STORE for a taper being added, x-ordered AFT → FWD, from the semantic
 * S.E.T./L.E.T. the user typed.
 *
 * SET faces the nearer shaft end, so a taper whose midpoint lands in the FWD half stores LET
 * at its AFT (start) face. Keying this on the dialog's measure-from chip instead of [side]
 * would store SET at the wrong face whenever the two disagree — a taper measured from AFT but
 * placed in the FWD half draws small-end-inboard, its card labels read swapped against the
 * typed values, and a keyway referenced "from SET" starts at the far face.
 *
 * "Not entered" sentinels (≤ 0) pass through untouched so rate derivation downstream still
 * sees which end is missing.
 */
internal fun taperAddDiameterOrder(
    setDiaMm: Float,
    letDiaMm: Float,
    side: TaperSide,
): Pair<Float, Float> =
    if (side == TaperSide.AFT) setDiaMm to letDiaMm else letDiaMm to setDiaMm
