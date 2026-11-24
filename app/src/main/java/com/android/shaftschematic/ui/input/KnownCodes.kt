package com.android.shaftschematic.ui.input

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Shop codes a name field always writes in capitals, matched as WHOLE words in any case: "acme
 * bc" → "acme BC", while "bcd" or "abc" is left alone. Add a code here and every name field
 * raises it.
 */
val KNOWN_CODES: Set<String> = setOf(
    "NC", "NF", "UNC", "UNF", "NPT", // thread series
    "BC", // bolt circle
    "OD", "ID",
    "SKF",
    "LLC",
)

/**
 * [value] with every [KNOWN_CODES] word in capitals. Nothing else changes, and the length never
 * changes, so the cursor or selection stays where it was.
 *
 * While the user is typing ([finished] false) a word that runs to the end of the text is left as
 * it is — it may be the start of a longer word ("unc" on the way to "uncle") — and is raised once a
 * space or punctuation follows it. A field calls this with [finished] true where it saves, so a
 * code typed last is raised too.
 */
fun raiseKnownCodes(value: TextFieldValue, finished: Boolean = false): TextFieldValue =
    raiseKnownCodes(value.text, finished).let { if (it == value.text) value else value.copy(text = it) }

/** See the [TextFieldValue] overload; the same rule for a plain-text field. */
fun raiseKnownCodes(text: String, finished: Boolean = false): String {
    if (text.isEmpty()) return text
    val out = StringBuilder(text)
    var i = 0
    while (i < text.length) {
        if (!text[i].isLetterOrDigit()) { i++; continue }
        val start = i
        while (i < text.length && text[i].isLetterOrDigit()) i++
        if (i == text.length && !finished) break
        val upper = text.substring(start, i).uppercase()
        if (upper.length == i - start && upper in KNOWN_CODES) out.replace(start, i, upper)
    }
    return out.toString()
}
