// file: app/src/main/java/com/android/shaftschematic/ui/input/TaperCalcValues.kt
package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.maxDiaMm
import com.android.shaftschematic.util.TaperCalcEntries
import com.android.shaftschematic.util.TaperCalcResult
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.taperRateModeIsAuto

/**
 * A solved taper calculator's values in canonical mm, as Apply writes them. A derived value is the
 * EXACT solve, not the text on screen: the field shows inches rounded to three places, and storing
 * that rounding would give the taper a geometry that no longer matches its own rate.
 *
 * @param typedRateText The Taper rate field as typed (a bare number under "Rate as: in/ft" spelled
 *   out as `n"/ft`, the digits kept), or `null` when the rate was derived.
 */
internal data class TaperCalcValues(
    val largeDiaMm: Float,
    val smallDiaMm: Float,
    val lengthMm: Float,
    val typedRateText: String?,
)

internal fun TaperCalcResult.Solved.toApplyValues(entries: TaperCalcEntries): TaperCalcValues =
    TaperCalcValues(
        largeDiaMm = largeDiaMm.toFloat(),
        smallDiaMm = smallDiaMm.toFloat(),
        lengthMm = lengthMm.toFloat(),
        typedRateText = entries.rateRead.takeIf { it.isNotEmpty() },
    )

/**
 * A taper's effective rate mode. Only a taper saved before the mode was stored has none, and that
 * one reads it from whether its text matches the computed rate.
 */
internal fun taperRateIsAuto(taper: Taper, computedRateText: String?): Boolean =
    taperRateModeIsAuto(taper.taperRateManual, taper.taperRateText, computedRateText)

/** The Auto rate text for a geometry — the card's call, in one place for Apply's two paths. */
internal fun taperCalcAutoRateText(lengthMm: Float, smallDiaMm: Float, largeDiaMm: Float): String? =
    autoTaperRateText(lengthMm = lengthMm, setDiaMm = smallDiaMm, letDiaMm = largeDiaMm, exactDecimals = 3)

/**
 * This taper with the calculator's values — both diameters, the length and the rate, and nothing
 * else. The position follows the rule a Length edit on the card follows
 * ([taperPhysStartForNewLength]); the keyway, name and reference are not the calculator's to change.
 *
 * The stored facing is kept: only a taper with no facing to keep (a missing or equal diameter)
 * takes the small end from its half of the shaft, the rule the Add dialog uses.
 *
 * The rate mode is kept and written explicitly, so a taper saved before the mode was stored cannot
 * flip when its text changes. Auto recomputes; Manual takes the typed rate verbatim, or the
 * computed one when the calculator derived it, since leaving the old text would print a rate the
 * new geometry contradicts.
 */
internal fun Taper.withTaperCalcValues(values: TaperCalcValues, overallLengthMm: Float): Taper {
    val newStart = taperPhysStartForNewLength(this, values.lengthMm, overallLengthMm)
    val hasFacing = startDiaMm > 0f && endDiaMm > 0f && startDiaMm != endDiaMm
    val largeAtStart = if (hasFacing) {
        startDiaMm > endDiaMm
    } else {
        classifyTaperSideByMidpoint(newStart, values.lengthMm, overallLengthMm) == TaperSide.FWD
    }
    val computed = taperCalcAutoRateText(values.lengthMm, values.smallDiaMm, values.largeDiaMm)
    val auto = taperRateIsAuto(this, taperCalcAutoRateText(lengthMm, minOf(startDiaMm, endDiaMm), maxDiaMm))
    val rateText = if (auto) computed else values.typedRateText ?: computed
    return copy(
        startFromAftMm = newStart,
        lengthMm = values.lengthMm,
        startDiaMm = if (largeAtStart) values.largeDiaMm else values.smallDiaMm,
        endDiaMm = if (largeAtStart) values.smallDiaMm else values.largeDiaMm,
        taperRateText = rateText ?: taperRateText,
        taperRateManual = !auto,
    )
}
