package com.android.shaftschematic.util

/**
 * Splits display text into plain runs and fraction runs so a renderer can SET a real built-up
 * fraction — numerator over denominator with a bar — instead of printing an inline `3/16`.
 *
 * This is the pure half of the fraction typography system: no Android, no Paint, no Canvas.
 * `util/FractionTextRenderer.kt` turns these runs into ink; both draw families (PDF composers
 * and the on-screen sheet canvases) go through that one renderer, so a fraction looks the same
 * everywhere it prints.
 *
 * ## Why parse strings instead of formatting structured values
 * Labels are assembled all over the app — `"OAL: 12 5/8\""`, `"1/4 × 1/8 × 2 1/2\""`,
 * `"3/16\" from AFT"` — and most of them concatenate a formatted length into a sentence.
 * Parsing at the draw site means every one of those labels gets built-up fractions without
 * its producer knowing anything about typography, and a fraction that arrives from somewhere
 * unexpected (a job note, a template name) is set the same way as one from [LengthFormat].
 *
 * ## What counts as a fraction
 * A digits-slash-digits token whose neighbours are not `.` `,` `/` or another digit. That
 * guard is the whole safety story: `12/25/2026` and `1.5/2` stay plain text, while `3/16`,
 * `399/4000` and `1/2` are set. Unicode vulgar fractions (`½ ⅜ ⅞ …`) and the fraction slash
 * `⁄` are recognised too, so text that already carries them renders identically to text that
 * spells them out.
 */
object FractionText {

    /** One piece of a parsed label. */
    sealed interface Run {
        /** Literal text, set in the base font. */
        data class Plain(val text: String) : Run

        /** A built-up fraction. Both parts are digit strings, kept verbatim — never reduced here. */
        data class Frac(val numerator: String, val denominator: String) : Run

        /**
         * The tightened space between a whole number and its fraction (`12 5/8`). A fraction box
         * carries its own side bearing, so a full word-space next to one reads as a gap; this
         * stands in for any run of spaces that touches a fraction.
         */
        data object Gap : Run
    }

    /** Unicode vulgar fractions, so `½` sets exactly like `1/2`. */
    private val VULGAR: Map<Char, Pair<String, String>> = mapOf(
        '¼' to ("1" to "4"), '½' to ("1" to "2"), '¾' to ("3" to "4"),
        '⅐' to ("1" to "7"), '⅑' to ("1" to "9"), '⅒' to ("1" to "10"),
        '⅓' to ("1" to "3"), '⅔' to ("2" to "3"),
        '⅕' to ("1" to "5"), '⅖' to ("2" to "5"), '⅗' to ("3" to "5"), '⅘' to ("4" to "5"),
        '⅙' to ("1" to "6"), '⅚' to ("5" to "6"),
        '⅛' to ("1" to "8"), '⅜' to ("3" to "8"), '⅝' to ("5" to "8"), '⅞' to ("7" to "8"),
        '↉' to ("0" to "3"),
    )

    /** U+2044 FRACTION SLASH reads as a divider, same as `/`. */
    private const val FRACTION_SLASH = '⁄'

    private fun Char.isSlash() = this == '/' || this == FRACTION_SLASH

    /** Characters that, adjacent to a candidate, mean it is part of something else (a date, a decimal). */
    private fun Char.blocksFraction() = isDigit() || this == '.' || this == ',' || isSlash()

    /** True when [text] contains at least one token this parser would set as a fraction. */
    fun hasFraction(text: String): Boolean = parse(text).any { it is Run.Frac }

    /**
     * Splits [text] into runs. Concatenating the plain text of the result (with [Run.Gap] as a
     * single space and a fraction as `n/d`) reproduces [text] apart from collapsed spaces.
     */
    fun parse(text: String): List<Run> {
        if (text.isEmpty()) return emptyList()

        val out = ArrayList<Run>(4)
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                out += Run.Plain(plain.toString())
                plain.setLength(0)
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]

            val vulgar = VULGAR[c]
            if (vulgar != null) {
                flush()
                out += Run.Frac(vulgar.first, vulgar.second)
                i++
                continue
            }

            val frac = if (c.isDigit()) matchFraction(text, i) else null
            if (frac != null) {
                flush()
                out += frac.run
                i = frac.end
                continue
            }

            plain.append(c)
            i++
        }
        flush()

        return tightenSpaces(out)
    }

    /** Renders [runs] back to a flat single-line string — the fallback when fractions are off. */
    fun flatten(runs: List<Run>): String = buildString {
        for (r in runs) when (r) {
            is Run.Plain -> append(r.text)
            is Run.Frac -> append(r.numerator).append('/').append(r.denominator)
            Run.Gap -> append(' ')
        }
    }

    private class Match(val run: Run.Frac, val end: Int)

    /**
     * Matches `digits / digits` starting at [start], or null when the neighbours say this is a
     * date, a decimal, or a longer path-like token.
     */
    private fun matchFraction(text: String, start: Int): Match? {
        if (start > 0 && text[start - 1].blocksFraction()) return null

        var j = start
        while (j < text.length && text[j].isDigit()) j++
        if (j >= text.length || !text[j].isSlash()) return null

        var k = j + 1
        while (k < text.length && text[k].isDigit()) k++
        if (k == j + 1) return null                       // slash with no denominator
        if (k < text.length && text[k].blocksFraction()) return null

        val den = text.substring(j + 1, k)
        if (den.all { it == '0' }) return null            // n/0 is not a fraction, it is a typo

        return Match(Run.Frac(text.substring(start, j), den), k)
    }

    /** Replaces every run of spaces that touches a fraction with a single [Run.Gap]. */
    private fun tightenSpaces(runs: List<Run>): List<Run> {
        if (runs.none { it is Run.Frac }) return runs

        val out = ArrayList<Run>(runs.size + 2)
        for ((idx, run) in runs.withIndex()) {
            if (run !is Run.Plain) {
                out += run
                continue
            }
            val fracBefore = idx > 0 && runs[idx - 1] is Run.Frac
            val fracAfter = idx + 1 < runs.size && runs[idx + 1] is Run.Frac

            var body = run.text
            val lead = fracBefore && body.startsWith(" ")
            val trail = fracAfter && body.endsWith(" ")
            if (lead) body = body.trimStart(' ')
            if (trail) body = body.trimEnd(' ')

            if (lead) out += Run.Gap
            if (body.isNotEmpty()) out += Run.Plain(body)
            // An all-space run between two fractions collapses to the single leading gap.
            if (trail && body.isNotEmpty()) out += Run.Gap
        }
        return out
    }
}
