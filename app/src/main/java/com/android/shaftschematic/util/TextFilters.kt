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
