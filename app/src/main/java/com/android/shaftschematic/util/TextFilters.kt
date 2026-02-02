package com.android.shaftschematic.util

/**
 * Permissive decimal filter that avoids rewriting while the user types.
 * Let transient states like ".", "1.", "1..2" be corrected gently,
 * but without formatting/rewriting the user's in-progress text.
 */
fun filterDecimalPermissive(input: String, allowSign: Boolean = false): String {
    var dotSeen = false
    val sb = StringBuilder()
    input.forEachIndexed { idx, ch ->
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !dotSeen -> { sb.append('.'); dotSeen = true }
            allowSign && idx == 0 && (ch == '-' || ch == '+') -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * Canonical numeric input filter.
 *
 * Rules:
 * - Digits 0–9 always allowed.
 * - One optional leading '-' when [allowNegative] is true.
 * - One '.' decimal point.
 * - One '/' fraction slash when [allowFraction] is true.
 * - Spaces allowed only when [allowFraction] is true (e.g., "1 1/2").
 * - Disallows alpha chars, scientific notation, units, multiple dots/slashes.
 */
fun filterNumericInput(
    raw: String,
    allowNegative: Boolean,
    allowFraction: Boolean
): String {
    var dotSeen = false
    var slashSeen = false
    var signSeen = false
    var sawNonSpace = false
    val sb = StringBuilder(raw.length)

    raw.forEach { ch ->
        when {
            ch.isDigit() -> {
                sb.append(ch)
                sawNonSpace = true
            }
            ch == '-' && allowNegative && !signSeen && !sawNonSpace -> {
                sb.append(ch)
                signSeen = true
                sawNonSpace = true
            }
            ch == '.' && !dotSeen -> {
                sb.append(ch)
                dotSeen = true
                sawNonSpace = true
            }
            ch == '/' && allowFraction && !slashSeen -> {
                sb.append(ch)
                slashSeen = true
                sawNonSpace = true
            }
            ch == ' ' && allowFraction -> {
                sb.append(ch)
            }
        }
    }

    return sb.toString()
}
