// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftDiaPrompt.kt
package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.ui.input.LocalFractionKeyEnabled
import com.android.shaftschematic.ui.input.rememberFieldCursor
import com.android.shaftschematic.ui.input.FractionKey
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.toMmOrNull

/**
 * Whether committing the first overall length on [before] should ask for the bare-shaft Ø.
 *
 * True only for a brand-new shaft: no OAL yet, no shaft-wide bare-shaft Ø, and no component of
 * any kind. Components added onto the default-sized bare shaft look wrong until its Ø is set
 * (on-device request), so the first OAL commit asks for it right away. A loaded document or
 * template carries an OAL or components and never prompts; a Skip is final for the document
 * because the OAL is no longer 0 afterwards.
 */
fun shouldPromptShaftDia(before: ShaftSpec): Boolean =
    before.overallLengthMm <= 0f &&
        before.autoBodyDiaMm <= 0f &&
        before.bodies.isEmpty() &&
        before.tapers.isEmpty() &&
        before.threads.isEmpty() &&
        before.liners.isEmpty() &&
        before.couplerBoltSlots.isEmpty()

/**
 * The new-shaft diameter prompt shown after the first OAL commit ([shouldPromptShaftDia]).
 *
 * One numeric field in the DOCUMENT [unit] (fractions accepted, a unit suffix read), converted
 * to mm at this edge and handed to [onSet] verbatim; "Set" is enabled only while the entry parses
 * to a positive value, and while it does not the field says why ([shaftDiaPromptError]). "Skip"
 * (and a dismiss) set nothing.
 */
@Composable
fun ShaftDiaPromptDialog(
    unit: UnitSystem,
    onSet: (mm: Float) -> Unit,
    onSkip: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsedMm = toMmOrNull(text, unit)?.takeIf { it > 0f }
    val entryError = shaftDiaPromptError(text, unit)
    val focusRequester = remember { FocusRequester() }

    val diaCursor = rememberFieldCursor()
    AlertDialog(
        onDismissRequest = onSkip,
        modifier = Modifier.testTag("shaft_dia_prompt"),
        title = { Text("Shaft diameter") },
        text = {
            Column {
                Text(
                    "What is the bare-shaft diameter? Components you add will sit on a shaft " +
                        "of this size. You can change it later on any Body (auto) card."
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = diaCursor.valueFor(text),
                    onValueChange = { v -> diaCursor.track(v); text = v.text },
                    label = { Text("Diameter (${abbr(unit)})") },
                    trailingIcon = if (diaCursor.focused && LocalFractionKeyEnabled.current) {
                        { FractionKey(onClick = { text = diaCursor.insertBar(text).text }) }
                    } else null,
                    singleLine = true,
                    isError = entryError != null,
                    supportingText = entryError?.let { e -> { Text(e) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { parsedMm?.let(onSet) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { diaCursor.focused = it.isFocused }
                        .testTag("shaft_dia_prompt_field"),
                )
                // Inside the dialog's own composition, so the requester is attached to the
                // field before focus is asked for; the keyboard comes up with it.
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedMm?.let(onSet) },
                enabled = parsedMm != null,
                modifier = Modifier.testTag("shaft_dia_prompt_set"),
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("shaft_dia_prompt_skip"),
            ) { Text("Skip") }
        },
    )
}

/** The prompt's error line for a diameter of zero or less. */
internal const val SHAFT_DIA_NOT_POSITIVE = "Must be > 0"

/**
 * Why the prompt's "Set" is disabled for [text], or null when it is not (or nothing is typed):
 * [NOT_A_NUMBER_ERROR] for text that does not read as a length, [SHAFT_DIA_NOT_POSITIVE] for a
 * value of zero or less.
 */
internal fun shaftDiaPromptError(text: String, unit: UnitSystem): String? {
    unreadableEntryError(text, ::isLengthText)?.let { return it }
    if (text.isBlank()) return null
    val mm = toMmOrNull(text, unit) ?: return null
    return if (mm > 0f) null else SHAFT_DIA_NOT_POSITIVE
}
