package com.android.shaftschematic.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Relative slope error a geometry may carry and still be called a common rate — a confirmed
 * product decision, not a tuning knob. `internal` because the standalone taper calculator
 * checks a typed rate against typed geometry with the SAME tolerance; a second literal 3%
 * would be a second decision to keep in step.
 */
internal const val DEFAULT_SLOPE_ERROR_TOLERANCE = 0.03f

/** 6 in expressed in canonical mm; unit conversion stays out of this layer. */
private const val BORE_BREAK_MM = 152.4f

/**
 * Two within-tolerance candidates whose relative errors differ by no more than
 * this are "comparably close": shop preference decides between them. A clearly
 * closer candidate (larger error gap) always wins on geometry.
 */
private const val COMPARABLY_CLOSE_MARGIN = 0.01f

val DEFAULT_COMMON_TAPER_ONE_TO_N: List<Float> = listOf(20f, 16f, 14f, 12f, 10f, 8f)

data class AutoTaperRateResult(
    val text: String,
    val matchedCommonOneToN: Float?,
    val exactOneToN: Float,
)

/**
 * Computes a display-friendly taper rate text from length/diameters.
 *
 * Returns either:
 * - a snapped common taper (for example, "1:16") when within tolerance, or
 * - an exact one-to-N form with fixed decimals (for example, "1:15.875").
 *
 * Both diameters must be real, positive values. UI callers use 0 / -1 as
 * "not provided" sentinels; treating those as geometry would fabricate a rate.
 *
 * When more than one common rate is within tolerance and their errors are
 * comparably close, the bore-preferred rate wins (1:16 at or under the 6 in
 * bore break, 1:12 above). A clearly closer candidate wins on geometry.
 */
fun autoTaperRate(
    lengthMm: Float,
    setDiaMm: Float,
    letDiaMm: Float,
    referenceDiaMm: Float = max(setDiaMm, letDiaMm),
    commonOneToN: List<Float> = DEFAULT_COMMON_TAPER_ONE_TO_N,
    maxRelativeSlopeError: Float = DEFAULT_SLOPE_ERROR_TOLERANCE,
    exactDecimals: Int = 3,
): AutoTaperRateResult? {
    if (lengthMm <= 0f) return null
    if (setDiaMm <= 0f || letDiaMm <= 0f) return null

    val diaDelta = abs(letDiaMm - setDiaMm)
    if (diaDelta <= 0f) return null

    val exactSlope = diaDelta / lengthMm
    val exactN = lengthMm / diaDelta

    val preferredOrder = preferredCommonOneToN(referenceDiaMm, commonOneToN)

    val withinTolerance = commonOneToN
        .filter { it > 0f }
        .map { n ->
            val slope = 1f / n
            CommonCandidate(
                oneToN = n,
                relativeError = abs(slope - exactSlope) / exactSlope,
                preferenceRank = preferredOrder.indexOf(n).takeIf { it >= 0 } ?: Int.MAX_VALUE,
            )
        }
        .filter { it.relativeError <= maxRelativeSlopeError }

    val bestError = withinTolerance.minOfOrNull { it.relativeError }
    val snapped = withinTolerance
        .filter { bestError != null && it.relativeError - bestError <= COMPARABLY_CLOSE_MARGIN }
        .minWithOrNull(compareBy<CommonCandidate> { it.preferenceRank }.thenBy { it.relativeError })

    if (snapped != null) {
        return AutoTaperRateResult(
            text = "1:${formatOneToN(snapped.oneToN, decimals = 3, trimTrailingZeros = true)}",
            matchedCommonOneToN = snapped.oneToN,
            exactOneToN = exactN,
        )
    }

    return AutoTaperRateResult(
        text = "1:${formatOneToN(exactN, decimals = exactDecimals, trimTrailingZeros = false)}",
        matchedCommonOneToN = null,
        exactOneToN = exactN,
    )
}

fun autoTaperRateText(
    lengthMm: Float,
    setDiaMm: Float,
    letDiaMm: Float,
    referenceDiaMm: Float = max(setDiaMm, letDiaMm),
    commonOneToN: List<Float> = DEFAULT_COMMON_TAPER_ONE_TO_N,
    maxRelativeSlopeError: Float = DEFAULT_SLOPE_ERROR_TOLERANCE,
    exactDecimals: Int = 3,
): String? = autoTaperRate(
    lengthMm = lengthMm,
    setDiaMm = setDiaMm,
    letDiaMm = letDiaMm,
    referenceDiaMm = referenceDiaMm,
    commonOneToN = commonOneToN,
    maxRelativeSlopeError = maxRelativeSlopeError,
    exactDecimals = exactDecimals,
)?.text

/**
 * The inches-per-foot spelling: a value followed by `/ft`, with an optional inch mark or `in`
 * between — `3/4"/ft`, `3/4″/ft`, `3/4 in/ft`, `3/4/ft`, `1 1/4"/ft`, `0.75"/ft`. Case-insensitive.
 */
private val PER_FOOT_FORM = Regex("""^(.+?)\s*(?:"|″|''|in\.?)?\s*/\s*ft\.?$""", RegexOption.IGNORE_CASE)

/** Reads a `/ft` spelling as inches of diameter change per foot of length, or null when it is not one. */
internal fun parseInchesPerFoot(text: String): Float? {
    val m = PER_FOOT_FORM.matchEntire(text.trim()) ?: return null
    val inches = parseFractionOrDecimal(m.groupValues[1]) ?: return null
    if (inches <= 0.0) return null
    return (inches / 12.0).toFloat()
}

/** True for a bare `n/d` spelling — no colon, no `/ft` — which is ambiguous as a taper rate. */
internal fun isBareFractionRateText(text: String): Boolean {
    val t = text.trim()
    return t.contains('/') && !t.contains(':') && PER_FOOT_FORM.matchEntire(t) == null
}

/**
 * Reads a typed taper rate as a slope (diameter change per unit length).
 *
 * Accepted, deterministically:
 * - a ratio `1:12` → 1/12 (any `N:D`, `D ≠ 0`);
 * - inches per foot `3/4"/ft` (see [parseInchesPerFoot]) → value / 12, so `3/4"/ft` = 1:16;
 * - a decimal: `12` reads as 1:12, a value under 1 as the slope itself (`0.0833`).
 *
 * A BARE slash fraction (`3/4`, `1/16`) is **never** read: a machinist writing 3/4 means 3/4
 * inch per foot (1:16), while a slope reader would make it 0.75 — an absurd taper — so the only
 * honest answer is to ask for the explicit form. [manualTaperRateBlockingMessage] names it.
 *
 * [allowAmbiguousBareOne] keeps a bare `1` readable on the legacy derivation path only; strict
 * callers (every UI gate) reject it.
 */
fun parseTaperRateText(text: String, allowAmbiguousBareOne: Boolean = true): Float? {
    val t = text.trim()
    if (t.isEmpty()) return null

    val colon = t.indexOf(':')
    if (colon >= 0) {
        val num = t.substring(0, colon).trim().toFloatOrNull() ?: return null
        val den = t.substring(colon + 1).trim().toFloatOrNull() ?: return null
        if (den == 0f) return null
        return num / den
    }

    parseInchesPerFoot(t)?.let { return it }

    // A bare fraction is ambiguous (see the KDoc); a per-foot spelling that failed to parse
    // (e.g. "0/ft") is simply unreadable. Both end here.
    if (t.contains('/')) return null

    val v = t.toFloatOrNull() ?: return null
    if (!allowAmbiguousBareOne && v == 1f) return null
    return if (v >= 1f) 1f / v else v
}

fun manualTaperRateBlockingMessage(
    rateText: String,
    lengthMm: Float,
    setDiaMm: Float,
    letDiaMm: Float,
): String? {
    val raw = rateText.trim()
    val hasSet = setDiaMm > 0f
    val hasLet = letDiaMm > 0f
    val needsRateToDerive = lengthMm > 0f && (hasSet.xor(hasLet))

    if (raw.isEmpty()) {
        return if (needsRateToDerive) {
            "Enter a taper rate to derive the missing end"
        } else null
    }

    if (raw == "1") {
        return "Use a full ratio or inches per foot; `1` is ambiguous"
    }

    if (isBareFractionRateText(raw)) {
        return "Write $raw\"/ft or a ratio like 1:16; a bare fraction is ambiguous"
    }

    return if (parseTaperRateText(raw, allowAmbiguousBareOne = false) == null) {
        "Enter a ratio (1:12), inches per foot (3/4\"/ft), or a decimal"
    } else null
}

fun manualTaperRateWarning(
    rateText: String,
    lengthMm: Float,
    setDiaMm: Float,
    letDiaMm: Float,
    maxRelativeSlopeError: Float = DEFAULT_SLOPE_ERROR_TOLERANCE,
): String? {
    val raw = rateText.trim()
    if (raw.isEmpty()) return null
    if (manualTaperRateBlockingMessage(raw, lengthMm, setDiaMm, letDiaMm) != null) return null
    if (!(lengthMm > 0f && setDiaMm > 0f && letDiaMm > 0f)) return null

    val parsedRate = parseTaperRateText(raw, allowAmbiguousBareOne = false) ?: return null
    val exactSlope = abs(letDiaMm - setDiaMm) / lengthMm
    if (exactSlope <= 0f) return null

    val relErr = abs(parsedRate - exactSlope) / exactSlope
    return if (relErr > maxRelativeSlopeError) {
        "Rate does not match Length + SET + LET"
    } else null
}

private data class CommonCandidate(
    val oneToN: Float,
    val relativeError: Float,
    val preferenceRank: Int,
)

private fun preferredCommonOneToN(referenceDiaMm: Float, commonOneToN: List<Float>): List<Float> {
    val preferred = if (referenceDiaMm <= BORE_BREAK_MM) {
        listOf(16f, 12f)
    } else {
        listOf(12f, 16f)
    }
    return preferred.filter { it in commonOneToN } + commonOneToN.filterNot { it in preferred }
}

/** `internal` so the standalone taper calculator sets its `1:N` values the one way. */
internal fun formatOneToN(value: Float, decimals: Int, trimTrailingZeros: Boolean): String {
    val fmt = "%.${decimals}f"
    val out = String.format(Locale.US, fmt, value.toDouble())
    // Only strip zeros in the fractional part; trimming an integer like "20"
    // would corrupt it to "2".
    return if (trimTrailingZeros && out.contains('.')) out.trimEnd('0').trimEnd('.') else out
}
