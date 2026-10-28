package com.android.shaftschematic.util

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
    allowFraction: Boolean,
    allowColon: Boolean = false
): String {
    var dotSeen = false
    var slashSeen = false
    var colonSeen = false
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
            ch == ':' && allowColon && !colonSeen -> {
                sb.append(ch)
                colonSeen = true
                sawNonSpace = true
            }
            ch == ' ' && allowFraction -> {
                sb.append(ch)
            }
        }
    }

    return sb.toString()
}

/**
 * Input filter for a taper-rate field — the ONE acceptance rule shared by the taper card and
 * `AddTaperDialog`, so the two surfaces can receive the same spellings.
 *
 * It keeps every character [parseTaperRateText] can read — digits, `.`, `/`, `:`, spaces, the
 * inch marks `"`, `″`, `'`, and the letters of `in` and `ft` — and drops the rest. It never
 * judges the shape: whether the text is a readable rate (`1:12`, `3/4"/ft`, `3/4in/ft`,
 * `.0625`) is the parser's call, and a bare `3/4` stays typeable so the field can explain why
 * it is ambiguous. [filterNumericInput] cannot serve here: it drops letters, inch marks and
 * every slash after the first, which leaves exactly the bare fraction the parser refuses.
 */
fun filterTaperRateInput(raw: String): String =
    raw.filter { ch -> ch.isDigit() || ch in TAPER_RATE_INPUT_CHARS }

private const val TAPER_RATE_INPUT_CHARS = "./: \"″'inftINFT"
