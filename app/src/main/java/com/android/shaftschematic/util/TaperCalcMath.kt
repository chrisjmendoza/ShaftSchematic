// file: app/src/main/java/com/android/shaftschematic/util/TaperCalcMath.kt
package com.android.shaftschematic.util

import com.android.shaftschematic.model.MM_PER_IN
import java.util.Locale
import kotlin.math.abs

/**
 * Solve core for the standalone taper calculator — no Compose, no Android, unit-tested
 * directly (the [autoTaperRate] posture).
 *
 * The whole calculator is one identity, `slope = (L.E.T. − S.E.T.) / length`, read four ways:
 * any three of the four values pin the fourth. The rate convention — which end is which, the
 * `1:N` form, the 3% common-rate snap, the bore tie-break — is NOT restated here; every rate
 * display goes through [autoTaperRate], so the calculator and a taper card can never disagree
 * about what a given geometry is called.
 *
 * Inputs and outputs are canonical millimeters, the same rule as `TaperRateAuto.kt`; the
 * entry-unit reinterpretation happens at the dialog edge. The slope is dimensionless either
 * way, which is why a rate needs no unit at all.
 */

/** Which value the solve derived. `null` on a [TaperCalcResult.Solved] means all four were typed. */
enum class TaperCalcUnknown { RATE, LARGE_DIA, SMALL_DIA, LENGTH }

/** Why a set of otherwise-complete entries cannot produce a taper. */
enum class TaperCalcIssue {
    NON_POSITIVE_LENGTH,
    NON_POSITIVE_DIA,
    NON_POSITIVE_RATE,

    /** S.E.T. at or above L.E.T. — a straight shaft has no rate, and a swapped pair is a typo. */
    SET_NOT_SMALLER,

    /** The typed rate over the typed length eats the whole large end: the small end lands at or below 0. */
    RATE_CONSUMES_DIA,
}

/**
 * A computed rate, in every form the calculator prints it.
 *
 * [exactOneToN] is the true `N` for the geometry; [commonOneToN] is the shop rate it snaps to,
 * decided by [autoTaperRate] so the tolerance and the bore tie-break stay in one place. The
 * calculator leads with the exact value and names the common one beside it — a taper card
 * prints the snapped name because that is what goes on a drawing, but a calculator is asked
 * precisely because the answer is not already known.
 */
data class TaperCalcRate(
    val exactOneToN: Double,
    val commonOneToN: Double?,
) {
    val exactText: String get() = oneToNText(exactOneToN)

    val commonText: String? get() = commonOneToN?.let { oneToNText(it) }

    /**
     * Shop notation for inch drawings: inches of taper per foot of length, `12 / N`. This is
     * the general form of the footer's two hand-written cases (1:12 → 1"/ft, 1:16 → 3/4"/ft).
     * Meaningless on a metric sheet, so only the inch entry mode shows it. Derived from the
     * EXACT N — the per-foot line states the geometry, never the rounded name for it.
     */
    val inchesPerFoot: Double get() = 12.0 / exactOneToN

    /** [inchesPerFoot] set the way the shop writes it — a fraction where one lands, else decimal. */
    val inchesPerFootText: String get() = LengthFormat.formatInchesSmart(inchesPerFoot)
}

/** One spelling of a ratio, through `TaperRateAuto`'s formatter. */
private fun oneToNText(n: Double): String =
    "1:" + formatOneToN(n.toFloat(), decimals = 3, trimTrailingZeros = true)

sealed interface TaperCalcResult {
    /** Fewer than three readable values — nothing to solve, and nothing to complain about yet. */
    object Incomplete : TaperCalcResult

    data class Invalid(val issue: TaperCalcIssue) : TaperCalcResult

    /**
     * A full taper. [unknown] names the derived value, or is `null` when all four were typed —
     * in which case [typedSlopeAgrees] reports whether the typed rate matches the geometry
     * within the common-rate tolerance. That disagreement is information, not an error: the
     * typed values are never rewritten to reconcile them.
     */
    data class Solved(
        val unknown: TaperCalcUnknown?,
        val largeDiaMm: Double,
        val smallDiaMm: Double,
        val lengthMm: Double,
        val rate: TaperCalcRate,
        val typedSlopeAgrees: Boolean? = null,
    ) : TaperCalcResult {
        /** The derived value in mm, for the solve directions that produce a length or a Ø. */
        val solvedValueMm: Double?
            get() = when (unknown) {
                TaperCalcUnknown.LARGE_DIA -> largeDiaMm
                TaperCalcUnknown.SMALL_DIA -> smallDiaMm
                TaperCalcUnknown.LENGTH -> lengthMm
                TaperCalcUnknown.RATE, null -> null
            }
    }
}

/**
 * Solves the fourth value from any three, or checks all four against each other.
 *
 * A `null` argument is "not entered" (blank or unreadable — the dialog flags unreadable text at
 * the field). Fewer than three present is [TaperCalcResult.Incomplete], never an error.
 */
fun solveTaperCalc(
    largeDiaMm: Double?,
    smallDiaMm: Double?,
    lengthMm: Double?,
    slope: Double?,
    maxRelativeSlopeError: Double = DEFAULT_SLOPE_ERROR_TOLERANCE.toDouble(),
): TaperCalcResult {
    val present = listOfNotNull(largeDiaMm, smallDiaMm, lengthMm, slope).size
    if (present < 3) return TaperCalcResult.Incomplete

    // Every entered value has to be a real measurement before any of them can imply another.
    if (lengthMm != null && lengthMm <= 0.0) return TaperCalcResult.Invalid(TaperCalcIssue.NON_POSITIVE_LENGTH)
    if ((largeDiaMm != null && largeDiaMm <= 0.0) || (smallDiaMm != null && smallDiaMm <= 0.0)) {
        return TaperCalcResult.Invalid(TaperCalcIssue.NON_POSITIVE_DIA)
    }
    if (slope != null && slope <= 0.0) return TaperCalcResult.Invalid(TaperCalcIssue.NON_POSITIVE_RATE)
    if (largeDiaMm != null && smallDiaMm != null && smallDiaMm >= largeDiaMm) {
        return TaperCalcResult.Invalid(TaperCalcIssue.SET_NOT_SMALLER)
    }

    val unknown = when {
        slope == null -> TaperCalcUnknown.RATE
        largeDiaMm == null -> TaperCalcUnknown.LARGE_DIA
        smallDiaMm == null -> TaperCalcUnknown.SMALL_DIA
        lengthMm == null -> TaperCalcUnknown.LENGTH
        else -> null
    }

    // Complete the geometry, then let autoTaperRate name it — after any solve all four values
    // exist, so there is exactly one path to a rate whichever value was missing.
    val len: Double
    val let: Double
    val set: Double
    when (unknown) {
        TaperCalcUnknown.RATE -> {
            len = lengthMm!!; let = largeDiaMm!!; set = smallDiaMm!!
        }
        TaperCalcUnknown.LARGE_DIA -> {
            len = lengthMm!!; set = smallDiaMm!!; let = set + slope!! * len
        }
        TaperCalcUnknown.SMALL_DIA -> {
            len = lengthMm!!; let = largeDiaMm!!; set = let - slope!! * len
            if (set <= 0.0) return TaperCalcResult.Invalid(TaperCalcIssue.RATE_CONSUMES_DIA)
        }
        TaperCalcUnknown.LENGTH -> {
            let = largeDiaMm!!; set = smallDiaMm!!; len = (let - set) / slope!!
        }
        null -> {
            len = lengthMm!!; let = largeDiaMm!!; set = smallDiaMm!!
        }
    }

    val rate = taperCalcRate(lengthMm = len, smallDiaMm = set, largeDiaMm = let)
        ?: return TaperCalcResult.Invalid(TaperCalcIssue.SET_NOT_SMALLER)

    val agrees = if (unknown == null) {
        val exactSlope = (let - set) / len
        abs(slope!! - exactSlope) / exactSlope <= maxRelativeSlopeError
    } else null

    return TaperCalcResult.Solved(
        unknown = unknown,
        largeDiaMm = let,
        smallDiaMm = set,
        lengthMm = len,
        rate = rate,
        typedSlopeAgrees = agrees,
    )
}

/**
 * Names a completed geometry through the app's one rate formatter. `null` only where
 * [autoTaperRate] itself declines (non-positive input, or no slope at all).
 */
fun taperCalcRate(lengthMm: Double, smallDiaMm: Double, largeDiaMm: Double): TaperCalcRate? {
    val delta = largeDiaMm - smallDiaMm
    if (delta <= 0.0 || lengthMm <= 0.0) return null
    val auto = autoTaperRate(
        lengthMm = lengthMm.toFloat(),
        setDiaMm = smallDiaMm.toFloat(),
        letDiaMm = largeDiaMm.toFloat(),
    ) ?: return null
    return TaperCalcRate(
        // Double precision from the entered numbers, not the Float round trip through autoTaperRate.
        exactOneToN = lengthMm / delta,
        commonOneToN = auto.matchedCommonOneToN?.toDouble(),
    )
}

/**
 * A computed length or Ø, in the entry unit with its suffix. Inches are fraction-smart (the
 * shop reads a scale), millimeters take the app's three-decimal print convention.
 */
fun taperCalcValueText(mm: Double, unit: UnitSystem): String =
    if (unit == UnitSystem.INCHES) {
        "${LengthFormat.formatInchesSmart(mm / MM_PER_IN)} in"
    } else {
        "${String.format(Locale.US, "%.3f", mm)} mm"
    }
