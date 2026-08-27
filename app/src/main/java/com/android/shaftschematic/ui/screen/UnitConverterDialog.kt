// file: app/src/main/java/com/android/shaftschematic/ui/screen/UnitConverterDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseFractionOrDecimal
import java.util.Locale

/**
 * UnitConverterDialog — quick inline mm ↔ in calculator.
 *
 * Same tool posture as [BoreKeywayCalcDialog]: it reads nothing from the shaft, stores nothing
 * (the field starts blank every open — a fresh scratchpad each time), marks nothing dirty, and
 * prints on no sheet. That standalone posture is also why it recomputes live per keystroke
 * instead of committing on blur — there is no model write for the commit-on-blur contract to
 * guard. Because it writes no component value, it sits OUTSIDE the add-dialog-parity invariant:
 * a launcher icon is enough — it never has to appear on a carousel card.
 *
 * The `in | mm` chip labels the ENTRY unit and is unit-reinterpreting, not converting: the typed
 * number is simply read in the selected unit (defaulted from the document, on-device
 * convention) and the OTHER unit's equivalent is computed and shown. The user reads the result
 * and types it into whatever field they actually meant it for — this dialog never writes back
 * into any other field or dialog.
 */
@Composable
fun UnitConverterDialog(
    defaultUnit: UnitSystem,
    onDismiss: () -> Unit,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var value by rememberSaveable { mutableStateOf("") }

    val result = converterResult(value, unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("unit_converter_dialog"),
        title = { Text("mm ↔ in converter") },
        text = {
            Column(
                Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnitChipRow(unit = unit, onSelect = { unit = it })
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    isError = result is ConverterResult.Invalid,
                    suffix = { Text(if (unit == UnitSystem.INCHES) "in" else "mm") },
                    placeholder = { Text("e.g. 1 1/2") },
                    singleLine = true,
                    // Text keyboard, not decimal: fraction entry ("1 1/2") needs '/' and space.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("unit_converter_value"),
                )
                when (result) {
                    is ConverterResult.Value -> result.lines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("unit_converter_result"),
                        )
                    }
                    ConverterResult.Invalid -> Text(
                        "Unreadable value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("unit_converter_error"),
                    )
                    ConverterResult.Blank -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("unit_converter_close")) {
                Text("Close")
            }
        },
    )
}

/** [UnitConverterDialog]'s live result — distinguishes "nothing typed yet" from "unreadable". */
internal sealed class ConverterResult {
    object Blank : ConverterResult()
    object Invalid : ConverterResult()
    data class Value(val lines: List<String>) : ConverterResult()
}

/**
 * Pure conversion core for [UnitConverterDialog] — no Compose, unit-tested directly.
 *
 * [entryUnit] INCHES reads [raw] as inches and shows the mm equivalent to 3 decimals (the app's
 * shop-print convention — [LengthFormat], `formatDiaWithUnit`/`formatLenDim`). [entryUnit]
 * MILLIMETERS reads [raw] as mm and shows both the decimal-inch equivalent (4 decimals) and the
 * nearest 64th via [LengthFormat.formatInchesSmart], called with a tolerance of exactly half a
 * 64th (the largest possible distance from any value to its nearest 64th) so it always resolves
 * to a whole/fraction label instead of ever falling back to its own bare-decimal branch.
 */
internal fun converterResult(raw: String, entryUnit: UnitSystem): ConverterResult {
    if (raw.isBlank()) return ConverterResult.Blank
    val value = parseFractionOrDecimal(raw) ?: return ConverterResult.Invalid
    return if (entryUnit == UnitSystem.INCHES) {
        val mm = value * MM_PER_IN
        ConverterResult.Value(listOf("${fmt(mm, 3)} mm"))
    } else {
        val inches = value / MM_PER_IN
        val decimalLine = "${fmt(inches, 4)} in"
        val fracLabel = LengthFormat.formatInchesSmart(
            inches,
            LengthFormat.InchFormatOptions(maxDenominator = 64, snapToleranceInches = HALF_64TH_TOLERANCE),
        )
        ConverterResult.Value(listOf(decimalLine, "≈ $fracLabel in"))
    }
}

/** Half the 1/64" grid spacing (plus float slop) — see [converterResult]'s KDoc. */
private const val HALF_64TH_TOLERANCE = 1.0 / 128.0 + 1e-9

private fun fmt(v: Double, places: Int): String = String.format(Locale.US, "%.${places}f", v)
