// file: app/src/main/java/com/android/shaftschematic/util/TaperRateNotation.kt
package com.android.shaftschematic.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Taper rate NOTATION — how a rate is typed and how it prints, as opposed to what it is. The
 * grammar itself (what a spelling means as a slope) stays in `TaperRateAuto.kt`; this file only
 * moves between the two shop notations, the ratio `1:N` and inches of diameter per foot of
 * length, `12 / N` (1:12 = 1"/ft, 1:16 = 3/4"/ft, 1:3.2 = 3 3/4"/ft).
 *
 * Per foot is inch-drawing notation: a metric drawing keeps the ratio for every rate, both for
 * entry ([effectiveTaperRateEntry]) and on paper.
 */

/**
 * How a bare number typed in a rate field is read — the "Rate as: 1:N | in/ft" chips on the
 * taper calculator, the taper card and `AddTaperDialog` (one rule on all three). A spelling that
 * names its own notation (`1:12`, `3/4"/ft`) reads the same under either.
 */
enum class TaperRateEntry { RATIO, PER_FOOT }

/** The entry notation a field actually reads: per foot exists only on an inch drawing. */
fun effectiveTaperRateEntry(entry: TaperRateEntry, unit: UnitSystem): TaperRateEntry =
    if (unit == UnitSystem.INCHES) entry else TaperRateEntry.RATIO

/**
 * The notation a rate field's chips open on: a stored Manual rate keeps the notation it was
 * typed in (a `1:12` card does not flip to in/ft because the print setting says per foot), and
 * anything else — Auto, blank, a bare decimal — follows the print setting ([printPerFoot]).
 */
fun initialTaperRateEntry(storedText: String, manual: Boolean, printPerFoot: Boolean): TaperRateEntry {
    val t = storedText.trim()
    if (manual) {
        if (t.contains(':')) return TaperRateEntry.RATIO
        if (parseInchesPerFoot(t) != null) return TaperRateEntry.PER_FOOT
    }
    return if (printPerFoot) TaperRateEntry.PER_FOOT else TaperRateEntry.RATIO
}

/** A trailing inch mark typed after the number (`3 3/4"`) — the "/ft" is what is missing. */
private val TRAILING_INCH_MARK = Regex("""\s*(?:"|″|''|in\.?)$""", RegexOption.IGNORE_CASE)

/**
 * Makes a per-foot entry say so: under [TaperRateEntry.PER_FOOT] a bare number or fraction
 * (`3 3/4`, `3/4`, `.75`, `3 3/4"`) becomes `3 3/4"/ft`, which every reader of a stored rate —
 * the card, the footer, the legacy derivation — already understands. The typed digits are kept
 * exactly; only the notation is spelled out, so the value is never rewritten (golden rule).
 *
 * Text that names its notation (a colon, a `/ft`), blank text, and text that does not read as a
 * number are returned trimmed and otherwise untouched — the last so the field's own error still
 * names what was typed. Under [TaperRateEntry.RATIO] nothing changes.
 */
fun normalizeTaperRateEntry(text: String, entry: TaperRateEntry): String {
    val t = text.trim()
    if (entry != TaperRateEntry.PER_FOOT || t.isEmpty()) return t
    if (t.contains(':') || isPerFootSpelling(t)) return t
    val number = t.replace(TRAILING_INCH_MARK, "").trim()
    val v = parseFractionOrDecimal(number) ?: return t
    if (v <= 0.0) return t
    return "$number\"/ft"
}

/** An inches-per-foot figure the way the shop writes it: a fraction to 1/32 where one lands, else 3 decimals. */
fun inchesPerFootLabel(inchesPerFoot: Double): String =
    LengthFormat.formatInchesSmart(
        inchesPerFoot,
        LengthFormat.InchFormatOptions(maxDenominator = 32),
    ) + "\"/ft"

/** Step an Auto rate printed per foot rounds to — the eighths a shop names a taper in. */
internal const val PER_FOOT_AUTO_STEP_IN = 0.125

/**
 * How close an Auto rate must be to an eighth of an inch per foot to print as it: 1%. Tighter
 * than the 3% ratio snap because the eighths are close together — at 3% every taper over about
 * 2"/ft would land on one, and the printed figure would stop saying anything about the geometry.
 */
internal const val PER_FOOT_AUTO_SNAP_TOLERANCE = 0.01

/**
 * An Auto (derived) rate per foot. A geometry that already snaps to a common ratio prints that
 * ratio's per-foot value (1:12 → 1"/ft), so the two notations name the same taper; otherwise the
 * exact `12 / N` prints as the nearest eighth when within [PER_FOOT_AUTO_SNAP_TOLERANCE]
 * (3.738 → 3 3/4"/ft) and as its decimal when not. Rounding a DERIVED value is a display
 * choice; a typed rate never goes through here. `null` when the geometry has no rate.
 */
fun autoTaperRatePerFootText(lengthMm: Float, setDiaMm: Float, letDiaMm: Float): String? {
    val r = autoTaperRate(lengthMm = lengthMm, setDiaMm = setDiaMm, letDiaMm = letDiaMm) ?: return null
    r.matchedCommonOneToN?.let { return inchesPerFootLabel(12.0 / it) }
    val ipf = 12.0 / r.exactOneToN
    val snapped = (ipf / PER_FOOT_AUTO_STEP_IN).roundToLong() * PER_FOOT_AUTO_STEP_IN
    return if (snapped > 0.0 && abs(ipf - snapped) / snapped <= PER_FOOT_AUTO_SNAP_TOLERANCE) {
        inchesPerFootLabel(snapped)
    } else {
        inchesPerFootLabel(ipf)
    }
}

/**
 * A TYPED rate per foot. A per-foot spelling keeps the user's own number (`3 3/4 in/ft` →
 * `3 3/4"/ft`); a ratio or decimal converts exactly, never snapped (`1:3.2` → `3 3/4"/ft`,
 * `1:10` → `1.2"/ft`). `null` when the text is not a readable rate.
 */
fun typedTaperRatePerFootText(text: String): String? {
    val t = text.trim()
    perFootNumberText(t)?.let { return "$it\"/ft" }
    val slope = parseTaperRateText(t, allowAmbiguousBareOne = false) ?: return null
    return inchesPerFootLabel(slope.toDouble() * 12.0)
}

/**
 * A taper's effective rate mode: Auto when it says so, and for a taper saved before the mode was
 * stored, Auto when its text is blank or equals the computed rate. The ONE rule behind the card,
 * the calculator's Apply and the footer.
 */
fun taperRateModeIsAuto(manualFlag: Boolean?, rateText: String, computedRateText: String?): Boolean =
    manualFlag?.not() ?: (rateText.isBlank() || rateText == computedRateText)
