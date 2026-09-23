// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperCalcDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.TaperCalcEntries
import com.android.shaftschematic.util.TaperCalcField
import com.android.shaftschematic.util.TaperCalcFieldState
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.presentTaperCalc

/**
 * TaperCalcDialog — standalone taper solver: enter any three of L.E.T. / S.E.T. / length /
 * rate, tap Calculate, and read the fourth IN its own field.
 *
 * Same tool posture as [BoreKeywayCalcDialog] and [UnitConverterDialog]: it reads nothing from
 * the shaft, stores nothing (fields start blank every open), marks nothing dirty, and prints on
 * no sheet.
 *
 * Button-driven, by on-device request: a value appearing at the bottom while typing read as
 * the calculator answering a question that had not been asked yet. Calculate is the one
 * trigger. The derived value shows as an italic preview inside the EMPTY field — a
 * placeholder, never text, so the field still holds only what the user typed (golden rule)
 * and typing over it needs no clearing. The ✓ beside it KEEPS the value as an input, an
 * explicit act that lets one answer feed the next question (find the rate, keep it, clear the
 * length, find the length for a new small end).
 *
 * A calculated state is a snapshot of the entries it was computed from: the moment any entry
 * or the unit changes, the snapshot no longer matches and the preview, the red outlines and the
 * message all drop — a number that no longer follows from the fields is never left on screen.
 * The display rules themselves are pure (`util/TaperCalcPresentation.kt`) and tested there,
 * because this dialog cannot be hosted under the Robolectric harness.
 *
 * The `in | mm` chips label the ENTRY unit and are unit-reinterpreting, not converting: typed
 * numbers are simply read in the selected unit (defaulted from the document). The rate itself
 * is dimensionless, so it means the same thing under either chip; only the inches-per-foot
 * line under it, which is inch-drawing shop notation, is unit-specific.
 *
 * Pure solve and formatting in `util/TaperCalcMath.kt`; the rate convention and the 3%
 * common-rate tolerance stay in `util/TaperRateAuto.kt`.
 */
@Composable
fun TaperCalcDialog(
    defaultUnit: UnitSystem,
    onDismiss: () -> Unit,
) {
    var entries by rememberSaveable(stateSaver = TaperCalcEntriesNonNullSaver) {
        mutableStateOf(TaperCalcEntries(unit = defaultUnit))
    }
    // The entries Calculate was last tapped on. Equality with the live entries is what "has
    // been calculated" means, so an edit invalidates the result without any clearing code.
    var calculatedFor by rememberSaveable(stateSaver = TaperCalcEntriesSaver) {
        mutableStateOf<TaperCalcEntries?>(null)
    }
    val calculated = calculatedFor == entries
    val shown = presentTaperCalc(entries, calculated)
    val calculate = { calculatedFor = entries }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("taper_calc_dialog"),
        title = { Text("Taper calculator") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Enter any three of the four values and tap Calculate; the fourth appears " +
                        "in its field. Enter all four to check them against each other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                UnitChipRow(unit = entries.unit, onSelect = { entries = entries.copy(unit = it) })

                TaperEntryField(
                    field = TaperCalcField.LARGE_DIA,
                    label = "Large end Ø (L.E.T.)",
                    entries = entries,
                    state = shown.field(TaperCalcField.LARGE_DIA),
                    onChange = { entries = entries.with(TaperCalcField.LARGE_DIA, it) },
                    onCalculate = calculate,
                    tag = "taper_calc_let",
                )
                TaperEntryField(
                    field = TaperCalcField.SMALL_DIA,
                    label = "Small end Ø (S.E.T.)",
                    entries = entries,
                    state = shown.field(TaperCalcField.SMALL_DIA),
                    onChange = { entries = entries.with(TaperCalcField.SMALL_DIA, it) },
                    onCalculate = calculate,
                    tag = "taper_calc_set",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        TaperEntryField(
                            field = TaperCalcField.LENGTH,
                            label = "Length",
                            entries = entries,
                            state = shown.field(TaperCalcField.LENGTH),
                            onChange = { entries = entries.with(TaperCalcField.LENGTH, it) },
                            onCalculate = calculate,
                            tag = "taper_calc_len",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        TaperEntryField(
                            field = TaperCalcField.RATE,
                            label = "Taper rate",
                            entries = entries,
                            state = shown.field(TaperCalcField.RATE),
                            onChange = { entries = entries.with(TaperCalcField.RATE, it) },
                            onCalculate = calculate,
                            tag = "taper_calc_rate",
                        )
                    }
                }

                Button(
                    onClick = calculate,
                    modifier = Modifier.fillMaxWidth().testTag("taper_calc_calculate"),
                ) { Text("Calculate") }

                shown.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shown.messageIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(
                            if (shown.messageIsError) "taper_calc_issue" else "taper_calc_result",
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { entries = TaperCalcEntries(unit = entries.unit); calculatedFor = null },
                modifier = Modifier.testTag("taper_calc_clear"),
            ) { Text("Clear") }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("taper_calc_close")) {
                Text("Close")
            }
        },
    )
}

/**
 * One entry field. A derived value rides the PLACEHOLDER slot — italic, in the primary
 * colour so it reads as an answer rather than a hint — and only ever appears while the field
 * is empty, which is exactly when there is a value to derive. The ✓ trailing the preview
 * copies it into the field as typed text; from then on it is the user's number.
 */
@Composable
private fun TaperEntryField(
    field: TaperCalcField,
    label: String,
    entries: TaperCalcEntries,
    state: TaperCalcFieldState,
    onChange: (String) -> Unit,
    onCalculate: () -> Unit,
    tag: String,
) {
    val derived = state.derivedText
    val isRate = field == TaperCalcField.RATE
    OutlinedTextField(
        value = entries.text(field),
        onValueChange = onChange,
        label = { Text(if (derived != null) "$label — calculated" else label) },
        isError = state.isError,
        // The rate is dimensionless: a ratio is the same number on either drawing.
        suffix = if (isRate) null else {
            { Text(if (entries.unit == UnitSystem.INCHES) "in" else "mm") }
        },
        placeholder = {
            when {
                derived != null -> Text(
                    derived,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${tag}_derived"),
                )
                isRate -> Text("e.g. 1:12")
            }
        },
        trailingIcon = if (derived == null) null else {
            {
                IconButton(
                    onClick = { onChange(derived) },
                    modifier = Modifier.testTag("${tag}_keep"),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Keep calculated value as an input")
                }
            }
        },
        supportingText = state.supportingText?.let { { Text(it) } },
        singleLine = true,
        // Text keyboard, not decimal: fraction entry ("19/32", "1 1/2") and ratios ("1:12")
        // need '/', ':' and space. Done on the keyboard is the same as tapping Calculate.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCalculate() }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

private fun entriesFromSaved(l: List<String?>): TaperCalcEntries? {
    if (l.size < 5 || l[0] == null) return null
    return TaperCalcEntries(
        largeDia = l[0] ?: "", smallDia = l[1] ?: "", length = l[2] ?: "", rate = l[3] ?: "",
        unit = runCatching { UnitSystem.valueOf(l[4] ?: "") }.getOrDefault(UnitSystem.INCHES),
    )
}

private fun entriesToSaved(e: TaperCalcEntries?): List<String?> =
    if (e == null) listOf(null) else listOf(e.largeDia, e.smallDia, e.length, e.rate, e.unit.name)

/** Survives rotation: five strings, the unit by name; the snapshot may be absent. */
private val TaperCalcEntriesSaver = listSaver<TaperCalcEntries?, String?>(
    save = { entriesToSaved(it) },
    restore = { entriesFromSaved(it) },
)

private val TaperCalcEntriesNonNullSaver = listSaver<TaperCalcEntries, String?>(
    save = { entriesToSaved(it) },
    restore = { entriesFromSaved(it) ?: TaperCalcEntries() },
)
