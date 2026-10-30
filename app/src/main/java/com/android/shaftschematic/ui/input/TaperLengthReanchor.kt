package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.Taper

/**
 * The canonical AFT-origin start a taper takes when only its LENGTH changes.
 *
 * A taper's Start field is authored against one end of the shaft. Under an **AFT** reference
 * that field IS `startFromAftMm`, so a length edit leaves it alone and the FWD end moves.
 * Under a **FWD** reference the authored number is the distance from the FWD face, so the FWD
 * end is the anchor: the start slides by exactly the length change and the authored distance
 * comes back out unchanged. Passing `startFromAftMm` straight through for a FWD taper drifts
 * its authored distance on every length edit.
 *
 * The authored distance itself is never rewritten — only the derived canonical start moves —
 * so the golden rule holds.
 */
fun taperPhysStartForNewLength(
    taper: Taper,
    newLengthMm: Float,
    overallLengthMm: Float,
): Float =
    if (taper.authoredReference == LinerAuthoredReference.FWD) {
        val authoredFromFwd = overallLengthMm - taper.startFromAftMm - taper.lengthMm
        overallLengthMm - authoredFromFwd - newLengthMm
    } else {
        taper.startFromAftMm
    }

/**
 * The canonical AFT-origin start an explicit body takes when only its LENGTH changes — the
 * [taperPhysStartForNewLength] rule for a body's own "Measure From" reference: under AFT the
 * start stays and the FWD end moves; under FWD the FWD end is the anchor and the start slides
 * by exactly the length change, so the authored distance from the FWD face comes back out
 * unchanged. The card's Length validator and its commit both call this, so the two can never
 * disagree about where the body lands.
 */
fun bodyPhysStartForNewLength(
    body: Body,
    newLengthMm: Float,
    overallLengthMm: Float,
): Float =
    if (body.authoredReference == LinerAuthoredReference.FWD) {
        val authoredFromFwd = overallLengthMm - body.startFromAftMm - body.lengthMm
        overallLengthMm - authoredFromFwd - newLengthMm
    } else {
        body.startFromAftMm
    }
