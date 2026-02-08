package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation

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

enum class TaperEndProp { START_DIA, END_DIA }

data class TaperSetLetMapping(
    val orientation: TaperOrientation,
    val leftCode: String,
    val rightCode: String,
    val leftBindsTo: TaperEndProp,
    val rightBindsTo: TaperEndProp,
)

fun taperSetLetMapping(taper: Taper): TaperSetLetMapping {
    // Binding stays x-ordered: left edits startDiaMm, right edits endDiaMm.
    // Only the labels swap for FWD tapers.
    return when (taper.orientation) {
        TaperOrientation.AFT -> TaperSetLetMapping(
            orientation = taper.orientation,
            leftCode = "S.E.T.",
            rightCode = "L.E.T.",
            leftBindsTo = TaperEndProp.START_DIA,
            rightBindsTo = TaperEndProp.END_DIA
        )
        TaperOrientation.FWD -> TaperSetLetMapping(
            orientation = taper.orientation,
            leftCode = "L.E.T.",
            rightCode = "S.E.T.",
            leftBindsTo = TaperEndProp.START_DIA,
            rightBindsTo = TaperEndProp.END_DIA
        )
    }
}
