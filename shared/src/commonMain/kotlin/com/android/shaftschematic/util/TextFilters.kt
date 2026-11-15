package com.android.shaftschematic.util

/**
 * Canonical numeric input filter — permissive while typing; whether the text is a number is the
 * parser's call at commit (`util/Parsing.kt`), so this never judges the shape, only drops
 * characters no spelling the parser reads could use.
 *
 * Rules:
 * - Digits 0–9 always allowed.
 * - One optional leading '-' sign when [allowNegative] is true.
 * - One '.' decimal point; ',' anywhere before it (a decimal comma `1,5`, or thousands groups
 *   `1,234.5` — the parser decides which, and refuses the ambiguous `1,500`).
 * - When [allowFraction] is true: one '/' fraction slash, spaces (`1 1/2`), and ONE hyphen
 *   separating a whole number from its fraction (`10-1/4`) — only right after a digit and only
 *   before the slash. Dropping that hyphen would join the whole number and the numerator into a
 *   different, valid number (`101/4`).
 * - One ':' for ratio entry like `1:12` when [allowColon] is true.
 * - The trailing run of letters and inch marks (`"`, `″`, `'`) is kept at the END of the text as
 *   typed, so the parser reads it or refuses it: `260mm` in an inch drawing is 260 mm, and `12ft`
 *   or `10 inches` reaches the parser whole and is refused as not a number. A suffix eaten here
 *   would be stored in the wrong unit, and a word eaten here would turn `12ft` into an accepted
 *   12. A stock numeric keypad cannot type letters; paste and hardware keyboards can.
 * - Anything else (letters before the end, scientific notation, extra dots or slashes) is dropped.
 */
fun filterNumericInput(
    raw: String,
    allowNegative: Boolean,
    allowFraction: Boolean,
    allowColon: Boolean = false
): String {
    // The trailing run of letters and inch marks survives as typed; the head is filtered below.
    var tailStart = raw.length
    while (tailStart > 0 && raw[tailStart - 1].isTrailingWordChar()) tailStart--
    val head = raw.substring(0, tailStart)
    val tail = raw.substring(tailStart)

    var dotSeen = false
    var slashSeen = false
    var colonSeen = false
    var signSeen = false
    var hyphenSeen = false
    var sawNonSpace = false
    var lastNonSpace: Char? = null
    val sb = StringBuilder(raw.length)

    fun keep(ch: Char) {
        sb.append(ch)
        sawNonSpace = true
        lastNonSpace = ch
    }

    head.forEach { ch ->
        when {
            ch.isDigit() -> keep(ch)
            ch == '-' && allowNegative && !signSeen && !sawNonSpace -> {
                keep(ch)
                signSeen = true
            }
            ch == '-' && allowFraction && !hyphenSeen && !slashSeen &&
                lastNonSpace?.isDigit() == true -> {
                keep(ch)
                hyphenSeen = true
            }
            ch == '.' && !dotSeen -> {
                keep(ch)
                dotSeen = true
            }
            ch == ',' && !dotSeen -> keep(ch)
            ch == '/' && allowFraction && !slashSeen -> {
                keep(ch)
                slashSeen = true
            }
            ch == ':' && allowColon && !colonSeen -> {
                keep(ch)
                colonSeen = true
            }
            ch == ' ' && allowFraction -> {
                sb.append(ch)
            }
        }
    }

    return sb.append(tail).toString()
}

/** A character of the trailing run kept for the parser: any letter, or an inch mark. */
private fun Char.isTrailingWordChar(): Boolean = isLetter() || this in INCH_MARK_INPUT_CHARS

/** The inch marks the parser reads as a suffix (`"`, `″`, `''`). */
private const val INCH_MARK_INPUT_CHARS = "\"″'"

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
