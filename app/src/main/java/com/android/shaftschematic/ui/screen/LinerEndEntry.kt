package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem

/**
 * A liner's "End from AFT | FWD" entry — the card and `AddLinerDialog` both carry it beside Start
 * and Length (add-dialog parity). A measured liner is located by where it starts and where it
 * ends, both from the same end of the shaft, and the length is their difference — so the shop
 * never subtracts (on-device request). The End is not stored: it sets the length, and the field
 * reads Start + Length back. Under FWD the Start is the liner's FWD edge from the FWD face and
 * the End its AFT edge, so the same subtraction holds.
 */
internal fun linerEndFieldLabel(isFwd: Boolean, unit: UnitSystem): String =
    "End from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})"

/** The Length field's note while a typed End decides it. */
internal const val LINER_LENGTH_FROM_END_NOTE = "Start to End"

/** An End that does not lie past the Start (a liner has a length). */
internal const val LINER_END_BEFORE_START_MSG = "Must be past the start"

/** The length a typed End makes, or the reason it makes none. */
internal data class LinerEndSpan(val lengthMm: Float?, val error: String?)

/**
 * The length [endMm] (typed as [endText]) makes with [startMm], both measured from the same end:
 * `end − start`. Blank, unreadable (the field says so itself) or a Start not entered yet make no
 * length and no error; an End at or before the Start makes [LINER_END_BEFORE_START_MSG].
 */
internal fun linerSpanFromEnd(startMm: Float?, endText: String?, endMm: Float?): LinerEndSpan {
    if (endText.isNullOrBlank() || endMm == null || startMm == null || startMm < 0f) {
        return LinerEndSpan(null, null)
    }
    if (endMm <= startMm) return LinerEndSpan(null, LINER_END_BEFORE_START_MSG)
    return LinerEndSpan(endMm - startMm, null)
}
