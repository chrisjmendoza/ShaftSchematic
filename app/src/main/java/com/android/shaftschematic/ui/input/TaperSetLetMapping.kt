package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Taper

/**
 * UI-only mapping for taper end labels.
 *
 * Shop semantics:
 * - SET = Small End of Taper
 * - LET = Large End of Taper
 *
 * Model semantics (unchanged): startDiaMm/endDiaMm are the left→right diameters along the taper.
 * Therefore the UI must flip labels for FWD tapers, without swapping stored values.
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

internal fun classifyTaperSideByMidpoint(taper: Taper, overallLengthMm: Float): TaperSide {
    if (overallLengthMm <= 0f) return TaperSide.AFT
    val midMm = taper.startFromAftMm + taper.lengthMm * 0.5f
    return if (midMm <= overallLengthMm * 0.5f) TaperSide.AFT else TaperSide.FWD
}
