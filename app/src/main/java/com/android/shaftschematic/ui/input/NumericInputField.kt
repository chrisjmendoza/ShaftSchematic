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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.android.shaftschematic.util.filterNumericInput

/**
 * Shared numeric input with filtering, settled-value tracking, and commit/revert behavior.
 *
 * NOTE:
 * - Numeric input is filtered and reverted at the UI boundary.
 * - Future numeric fields must use this composable (filter + parser) before committing.
 *
 * **A rejected value is never swapped out silently.** When a commit fails — the text does not
 * parse, or [validator] returns a reason — the box goes back to the value the caller still holds
 * (the last committed text, or the incoming [initialText]) and the field states what it threw
 * away: `"<typed> not applied — <reason>"` as error supporting text, until the text in the box is
 * EDITED. Focus does not clear it: a focus change can arrive without the machinist touching the
 * field (a cleared focus is handed straight back to the first focusable in non-touch mode —
 * hardware keyboards, ChromeOS), and a notice wiped that way is a notice never read. A revert without that notice would leave the old value in the box under an
 * error naming the rule but not the value, which reads as if the OLD value were at fault — a
 * typed value gone with no word of it (on-device report). A
 * caller that wants a value to survive an error must not reject it here: let it commit and
 * gate it downstream instead.
 *
 * [onDirtyChange] reports whether the field currently holds an **uncommitted** edit — its text
 * against the baseline a blur-with-no-edit would keep. It fires `true` the moment the text
 * diverges and `false` again on a successful commit, on a revert, and when an external model
 * update refreshes the text; it is edge-triggered, so typing does not spam it. It reports
 * state and never drives a commit — the blur/IME pipeline above is still the only writer. The
 * carousel card's Save button is enabled off the aggregate of these reports.
 *
 * [placeholder] shows inside an empty box (an example of what to type); [hint] is supporting
 * text shown only while the field has nothing to report — a rejection notice or an
 * [externalIssueText] always takes the line instead, since an error outranks a tip. Both
 * default null, which leaves the field exactly as it was without them.
 *
 * [inputFilter], when given, replaces the numeric keystroke filter for a field whose accepted
 * spellings are not plain numbers (the taper rate's `3/4"/ft`); null keeps [filterNumericInput]
 * with the allow-flags above.
 *
 * [landRestoredEdit] is for a caller whose commits land in state that does NOT outlive an
 * activity recreation's teardown. The state is saved BEFORE the teardown, so the teardown's own
 * blur commits into the composition being thrown away, and the restored field then shows a typed
 * value its caller never received — unfocused, so nothing leaves it on the next leave (on-device
 * report: an undercut card's typed Ø, shown after a rotation, dropped on Back). When true, a field
 * restored holding such an edit commits it once, as that blur would have; a rejected one reverts
 * with its notice, as a blur's would. False (the default) leaves a restored edit uncommitted until
 * the next blur — right for a caller whose state the teardown blur already reached (the
 * ViewModel), where the restore finds the moved value and shows it clean.
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
    inputFilter: ((String) -> String)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    validator: ((String) -> String?)? = null,
    onDirtyChange: ((Boolean) -> Unit)? = null,
    placeholder: String? = null,
    hint: String? = null,
    landRestoredEdit: Boolean = false,
    parseValid: (String) -> Boolean,
    onCommit: (String) -> Unit
) {
    // The box's text, the settled text and the notice are SAVEABLE, keyed on [initialText] like
    // the plain state they replace: a typed value nobody has committed yet survives an activity
    // recreation (a tablet rotation) instead of coming back blank (on-device report — a typed
    // undercut Ø lost on rotation). A restore commits nothing unless the caller asks for it
    // ([landRestoredEdit]): the focus baseline below stays plain state, so the attach-time
    // unfocused callback still finds no baseline.
    var text by rememberSaveable(initialText, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText))
    }

    // Why the last commit was refused, naming the value that was not applied; null when there is
    // nothing to report. Cleared only by an edit to the text, never by focus.
    var rejectedNotice by rememberSaveable(initialText) { mutableStateOf<String?>(null) }

    /** The reason [raw] cannot be committed, or null when it can. */
    fun rejectionReason(raw: String): String? =
        if (!parseValid(raw)) NOT_A_NUMBER_REASON else validator?.invoke(raw)

    // Null when not focused; set on each focus gain to the SETTLED text (below), the value a
    // blur with no edit leaves in place. Used to skip commits when the user tapped a field but
    // didn't change its value. Deliberately not saveable: focus does not survive a recreation.
    var textWhenFocused by remember(initialText) { mutableStateOf<String?>(null) }

    // The text the field would be left holding if the user walked away right now: the
    // incoming value, then whatever a commit or a revert settled on. Anything else in the
    // box is an edit that has not landed yet. It is also the revert target: the value the
    // caller actually holds, never an intermediate the caller was never handed.
    var settledText by rememberSaveable(initialText) { mutableStateOf(initialText) }

    // The [initialText] the saved state above was seeded from. Restoration does not compare
    // `rememberSaveable` inputs, so a restore over a model value that moved meanwhile (a blur
    // during the teardown committed the edit) would bring back text and a settled value for the
    // OLD model value and report a phantom dirty edit; the stale state is dropped instead.
    var seededFrom by rememberSaveable(initialText) { mutableStateOf(initialText) }
    if (seededFrom != initialText) {
        seededFrom = initialText
        text = TextFieldValue(initialText)
        settledText = initialText
        rejectedNotice = null
    }

    fun commitOrRevert() {
        val typed = text.text
        val reason = rejectionReason(typed)
        if (reason == null) {
            rejectedNotice = null
            settledText = typed
            onCommit(typed)
        } else {
            rejectedNotice = rejectedValueNotice(typed, reason)
            text = TextFieldValue(settledText)
        }
    }

    // Edge-triggered: LaunchedEffect only re-runs when the flag itself flips, so a run of
    // keystrokes past the first reports nothing. The listener is read through
    // rememberUpdatedState so a caller that hands over a fresh lambda (or a fresh dirty
    // registry) is not left writing into the previous one.
    val dirtyListener by rememberUpdatedState(onDirtyChange)
    val isDirty = text.text != settledText
    LaunchedEffect(isDirty) { dirtyListener?.invoke(isDirty) }

    // A field enters composition dirty only when restored holding an edit that never landed —
    // a fresh field's text IS its settled text. Run once, after the composition that restored it,
    // so the caller's own restored state is in place to take the commit. See [landRestoredEdit].
    if (landRestoredEdit) {
        LaunchedEffect(Unit) {
            if (textWhenFocused == null && text.text != settledText) commitOrRevert()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = inputFilter?.invoke(raw.text) ?: filterNumericInput(
                raw = raw.text,
                allowNegative = allowNegative,
                allowFraction = allowFraction,
                allowColon = allowColon
            )
            // A new edit supersedes the report about the old one; a cursor move is not an edit.
            if (filtered != text.text) rejectedNotice = null
            text = if (filtered == raw.text) raw else raw.copy(text = filtered)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        enabled = enabled,
        isError = rejectedNotice != null || externalIssueText != null,
        supportingText = {
            val notice = rejectedNotice
            if (notice != null) {
                Text(notice)
            } else if (externalIssueText != null) {
                Text(externalIssueText)
            } else if (hint != null) {
                Text(hint)
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitOrRevert() }),
        modifier = modifier.onFocusChanged { f ->
            if (f.isFocused) {
                // The settled text equals the box's text on every focus gain but one: a restored
                // edit that never landed. Against the settled text a blur lands that edit, where
                // the box's own text would read it as unchanged and leave it on screen, unapplied.
                textWhenFocused = settledText
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

/** The reason a [NumericInputField] notice gives for text that does not parse. */
internal const val NOT_A_NUMBER_REASON = "not a number"

/**
 * The supporting text a [NumericInputField] shows after refusing [typed] for [reason] — the
 * value that was NOT applied, then why. A blank entry is named as such, since an empty quote
 * says nothing.
 */
internal fun rejectedValueNotice(typed: String, reason: String): String {
    val shown = typed.trim().ifEmpty { "Blank entry" }
    return "$shown not applied — $reason"
}
