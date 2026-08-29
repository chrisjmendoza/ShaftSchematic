package com.android.shaftschematic.ui.input

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.android.shaftschematic.util.filterNumericInput

/**
 * Shared numeric input with filtering, last-valid tracking, and commit/revert behavior.
 *
 * NOTE:
 * - Numeric input is filtered and reverted at the UI boundary.
 * - Future numeric fields must use this composable (filter + parser) before committing.
 *
 * [onDirtyChange] reports whether the field currently holds an **uncommitted** edit — its text
 * against the baseline a blur-with-no-edit would keep. It fires `true` the moment the text
 * diverges and `false` again on a successful commit, on a revert, and when an external model
 * update refreshes the text; it is edge-triggered, so typing does not spam it. It reports
 * state and never drives a commit — the blur/IME pipeline above is still the only writer. The
 * carousel card's Save button is enabled off the aggregate of these reports.
 */
@Composable
fun NumericInputField(
    label: String,
    initialText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externalIssueText: String? = null,
    allowNegative: Boolean = false,
    allowFraction: Boolean = true,
    allowColon: Boolean = false,
    showValidationErrors: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    validator: ((String) -> String?)? = null,
    onDirtyChange: ((Boolean) -> Unit)? = null,
    parseValid: (String) -> Boolean,
    onCommit: (String) -> Unit
) {
    val initialIsValid = remember(initialText) { parseValid(initialText) }
    var text by remember(initialText) { mutableStateOf(TextFieldValue(initialText)) }
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

    // Null when not focused; set to the text value at the moment focus was gained.
    // Used to skip commits when the user tapped a field but didn't change its value.
    var textWhenFocused by remember(initialText) { mutableStateOf<String?>(null) }

    // The text the field would be left holding if the user walked away right now: the
    // incoming value, then whatever a commit or a revert settled on. Anything else in the
    // box is an edit that has not landed yet.
    var settledText by remember(initialText) { mutableStateOf(initialText) }

    fun commitOrRevert() {
        val ok = validate(text.text, updateError = true)
        if (ok) {
            showError = false
            settledText = text.text
            onCommit(text.text)
        } else {
            if (showValidationErrors) showError = true
            text = TextFieldValue(lastValidText)
            settledText = lastValidText
        }
    }

    // Edge-triggered: LaunchedEffect only re-runs when the flag itself flips, so a run of
    // keystrokes past the first reports nothing. The listener is read through
    // rememberUpdatedState so a caller that hands over a fresh lambda (or a fresh dirty
    // registry) is not left writing into the previous one.
    val dirtyListener by rememberUpdatedState(onDirtyChange)
    val isDirty = text.text != settledText
    LaunchedEffect(isDirty) { dirtyListener?.invoke(isDirty) }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = filterNumericInput(
                raw = raw.text,
                allowNegative = allowNegative,
                allowFraction = allowFraction,
                allowColon = allowColon
            )
            text = if (filtered == raw.text) raw else raw.copy(text = filtered)

            if (parseValid(filtered)) {
                lastValidText = filtered
                if (showError && validate(filtered, updateError = false)) {
                    showError = false
                }
            }
        },
        label = { Text(label) },
        enabled = enabled,
        isError = (showError && errorText != null) || externalIssueText != null,
        supportingText = {
            if (showError && errorText != null) {
                Text(errorText!!)
            } else if (externalIssueText != null) {
                Text(externalIssueText)
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitOrRevert() }),
        modifier = modifier.onFocusChanged { f ->
            if (f.isFocused) {
                textWhenFocused = text.text
                text = text.copy(selection = TextRange(0, text.text.length))
            }
            if (!f.isFocused) {
                val captured = textWhenFocused
                textWhenFocused = null
                if (shouldCommitOnBlur(captured, text.text)) {
                    commitOrRevert()
                }
            }
        }
    )
}
