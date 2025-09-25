package shaftschematic.ui.input

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Numeric input with commit-on-blur/Done and:
 * - Tap-to-clear when the committed value is exactly 0 (only on focus gain).
 * - No VM mutation while typing; value is committed only on blur/Done.
 * - Simple min/max and negativity checks.
 *
 * Contract: pass the currently committed [value] and an [onCommit] that updates model/VM.
 */
@Composable
fun NumberField(
    label: String,
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float? = null,
    max: Float? = null,
    allowNegative: Boolean = false,
) {
    // Always re-seed local text from the committed value when it changes upstream.
    val initial = remember(value) { value.toString() }
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }

    fun validateAndCommit() {
        val raw = text.trim().ifEmpty { "0" }
        val num = raw.toFloatOrNull()
        val err = when {
            num == null -> "Enter a number"
            !allowNegative && num < 0f -> "Must be ≥ 0"
            min != null && num < min   -> "Min $min"
            max != null && num > max   -> "Max $max"
            else -> null
        }
        error = err
        if (err == null) {
            onCommit(num!!)
            // sync local text to the committed value
            text = num.toString()
        }
    }

    OutlinedTextField(
        modifier = modifier.onFocusChanged { f ->
            val was = focused
            focused = f.isFocused
            if (focused && !was) {
                // Tap-to-clear rule: only if the committed value equals zero
                if (value == 0f && (text == "0" || text.startsWith("0."))) {
                    text = ""
                }
            } else if (!focused && was) {
                validateAndCommit()
            }
        },
        value = text,
        onValueChange = { s ->
            // Filter to a numeric-looking string while typing (no commit here).
            var dot = false
            val filtered = buildString(s.length) {
                s.forEachIndexed { i, c ->
                    when {
                        c == '-' && allowNegative && i == 0 -> append(c)
                        c.isDigit() -> append(c)
                        c == '.' && !dot -> { append(c); dot = true }
                    }
                }
            }
            text = filtered
        },
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { validateAndCommit() })
    )
}
