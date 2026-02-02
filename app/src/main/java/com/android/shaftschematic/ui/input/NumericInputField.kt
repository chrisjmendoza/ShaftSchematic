package com.android.shaftschematic.ui.input

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.android.shaftschematic.util.filterNumericInput

/**
 * Shared numeric input with filtering, last-valid tracking, and commit/revert behavior.
 *
 * NOTE:
 * - Numeric input is filtered and reverted at the UI boundary.
 * - Future numeric fields must use this composable (filter + parser) before committing.
 */
@Composable
fun NumericInputField(
    label: String,
    initialText: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    allowFraction: Boolean = true,
    showValidationErrors: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    validator: ((String) -> String?)? = null,
    parseValid: (String) -> Boolean,
    onCommit: (String) -> Unit
) {
    val initialIsValid = remember(initialText) { parseValid(initialText) }
    var text by remember(initialText) { mutableStateOf(initialText) }
    var lastValidText by remember(initialText) {
        mutableStateOf(if (initialIsValid) initialText else "")
    }
    var showError by remember(initialText) { mutableStateOf(false) }
    var errorText by remember(initialText) { mutableStateOf<String?>(null) }

    fun validate(raw: String, updateError: Boolean): Boolean {
        if (!parseValid(raw)) {
            if (updateError) errorText = "Invalid number"
            return false
        }
        val extra = validator?.invoke(raw)
        if (extra != null) {
            if (updateError) errorText = extra
            return false
        }
        if (updateError) errorText = null
        return true
    }

    fun commitOrRevert() {
        val ok = validate(text, updateError = true)
        if (ok) {
            showError = false
            onCommit(text)
        } else {
            if (showValidationErrors) showError = true
            text = lastValidText
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = filterNumericInput(
                raw = raw,
                allowNegative = allowNegative,
                allowFraction = allowFraction
            )
            text = filtered

            if (parseValid(filtered)) {
                lastValidText = filtered
                if (showError && validate(filtered, updateError = false)) {
                    showError = false
                }
            }
        },
        label = { Text(label) },
        isError = showError && errorText != null,
        supportingText = {
            if (showError && errorText != null) {
                Text(errorText!!)
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitOrRevert() }),
        modifier = modifier.onFocusChanged { f ->
            if (!f.isFocused) commitOrRevert()
        }
    )
}
