package com.android.shaftschematic.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How an inch value reads in an editable number field — Settings → Units → "Inch fields show".
 *
 * The choice only decides values that sit on the [INCH_FIELD_FRACTION_DENOMINATOR] grid, where
 * a fraction and a decimal are the SAME number (`0.5` is `1/2`). A value off the grid (a typed
 * `10.990`) reads as a decimal in both styles, and a value on it never reads as a rounded
 * decimal in either: `11/32` shows `11/32` or `0.34375`, never `0.3438`.
 */
enum class InchFieldStyle {
    FRACTION,
    DECIMAL;

    companion object {
        /** Fractions: a shop writes inches as fractions, and the field shows what was typed. */
        val Default: InchFieldStyle = FRACTION

        fun fromName(name: String?): InchFieldStyle =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** The finest fraction an inch field shows — the shop's finest step (on-device ruling). */
const val INCH_FIELD_FRACTION_DENOMINATOR = 32

/**
 * How far from the grid a stored value may sit and still read as its fraction, in inches.
 *
 * Covers `Float` storage: at shaft lengths a millimetre `Float` holds about 0.00002″, and a
 * FWD-referenced distance re-derived from two stored values carries the error of both. The only
 * typed decimals inside it are an odd 32nd written to four places (`0.0313` for 1/32).
 */
const val INCH_FIELD_GRID_TOLERANCE_IN = 1e-4

/**
 * The process-wide inch-field style the display helpers read. Snapshot state, so a field
 * composed from it recomposes when the setting changes. The ONE writer is the root collector
 * in `MainActivity`, which follows `SettingsStore.inchFieldStyleFlow`.
 */
object InchFieldDisplay {
    var style: InchFieldStyle by mutableStateOf(InchFieldStyle.Default)
        private set

    fun update(next: InchFieldStyle) {
        style = next
    }
}

/**
 * The editable text for [inches] in an inch field.
 *
 * - On the 1/32 grid: a reduced shop fraction (`12 11/32`, `3/4`, `12`) under
 *   [InchFieldStyle.FRACTION], or the grid value's exact decimal (`12.34375`) under
 *   [InchFieldStyle.DECIMAL].
 * - Off the grid: [decimalPlaces] decimals, trailing zeros trimmed.
 *
 * Every form parses back through `parseLengthMmOrNull`. Formatted in [Locale.US] whatever the
 * device locale (a comma-decimal locale's `12,0000` trims to `12,`, which the parser refuses),
 * and a value that rounds to zero prints `0`, never `-0`.
 */
fun inchFieldText(
    inches: Double,
    style: InchFieldStyle = InchFieldDisplay.style,
    decimalPlaces: Int = 4,
): String {
    val den = INCH_FIELD_FRACTION_DENOMINATOR
    val n = (inches * den).roundToLong()
    val onGrid = abs(inches - n.toDouble() / den) <= INCH_FIELD_GRID_TOLERANCE_IN
    val text = when {
        !onGrid -> trimmedDecimal(inches, decimalPlaces)
        style == InchFieldStyle.FRACTION -> gridFraction(n, den)
        else -> trimmedDecimal(n.toDouble() / den, EXACT_32ND_PLACES)
    }
    return if (text == "-0") "0" else text
}

/** Decimal places that write any 32nd exactly (`1/32` = `0.03125`). */
private const val EXACT_32ND_PLACES = 5

private fun trimmedDecimal(v: Double, places: Int): String =
    "%.${places}f".format(Locale.US, v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

/** `n / den` as a reduced mixed fraction; whole numbers print bare. */
private fun gridFraction(n: Long, den: Int): String {
    if (n == 0L) return "0"
    val sign = if (n < 0) "-" else ""
    val a = abs(n)
    val whole = a / den
    val rem = a % den
    if (rem == 0L) return "$sign$whole"
    var g = rem
    var b = den.toLong()
    while (b != 0L) { val t = g % b; g = b; b = t }
    val frac = "${rem / g}/${den / g}"
    return if (whole == 0L) "$sign$frac" else "$sign$whole $frac"
}
