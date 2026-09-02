// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperCalcDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import com.android.shaftschematic.util.TaperCalcIssue
import com.android.shaftschematic.util.TaperCalcResult
import com.android.shaftschematic.util.TaperCalcUnknown
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.solveTaperCalc
import com.android.shaftschematic.util.taperCalcValueText

/**
 * TaperCalcDialog — standalone taper solver: enter any three of L.E.T. / S.E.T. / length /
 * rate and read the fourth, without building a shaft.
 *
 * Same tool posture as [BoreKeywayCalcDialog] and [UnitConverterDialog]: it reads nothing from
 * the shaft, stores nothing (fields start blank every open), marks nothing dirty, and prints on
 * no sheet. That standalone posture is also why it recomputes live per keystroke instead of
 * committing on blur — there is no model write for the commit-on-blur contract to guard. It
 * also never fills a field in: a derived value is shown as a result for the user to read and
 * use, never written back over anything they typed.
 *
 * The `in | mm` chips label the ENTRY unit and are unit-reinterpreting, not converting: typed
 * numbers are simply read in the selected unit (defaulted from the document). The rate itself
 * is dimensionless, so it means the same thing under either chip; only the inches-per-foot
 * line, which is inch-drawing shop notation, is unit-specific.
 *
 * Pure solve and formatting in `util/TaperCalcMath.kt`; the rate convention and the 3%
 * common-rate tolerance stay in `util/TaperRateAuto.kt`.
 */
@Composable
fun TaperCalcDialog(
    defaultUnit: UnitSystem,
    onDismiss: () -> Unit,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var largeDia by rememberSaveable { mutableStateOf("") }
    var smallDia by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("") }

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
                    "Enter any three of the four values; the fourth is computed. " +
                        "Enter all four to check them against each other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                UnitChipRow(unit = unit, onSelect = { unit = it })

                // Everything is parsed and solved BEFORE the fields are laid out so each field
                // can carry its own error state — an entry that cannot produce a taper has to
                // read wrong AT THE FIELD, not only in the results block.
                val let = entryToMm(largeDia, unit)
                val set = entryToMm(smallDia, unit)
                val len = entryToMm(length, unit)
                // The rate is dimensionless, so it is parsed by its own ratio grammar
                // ("1:12", "1/12", a bare decimal) rather than the length parser. Bare "1"
                // stays blocked as ambiguous, the same rule the taper card applies.
                val slope = parseTaperRateText(rateText, allowAmbiguousBareOne = false)?.toDouble()
                val result = solveTaperCalc(
                    largeDiaMm = let,
                    smallDiaMm = set,
                    lengthMm = len,
                    slope = slope,
                )
                val issue = (result as? TaperCalcResult.Invalid)?.issue

                CalcField(
                    largeDia, { largeDia = it }, "Large end Ø (L.E.T.)", unit, "taper_calc_let",
                    isError = largeDia.isNotBlank() &&
                        (let == null || let <= 0.0 || issue == TaperCalcIssue.SET_NOT_SMALLER),
                )
                CalcField(
                    smallDia, { smallDia = it }, "Small end Ø (S.E.T.)", unit, "taper_calc_set",
                    isError = smallDia.isNotBlank() &&
                        (set == null || set <= 0.0 || issue == TaperCalcIssue.SET_NOT_SMALLER ||
                            issue == TaperCalcIssue.RATE_CONSUMES_DIA),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        CalcField(
                            length, { length = it }, "Length", unit, "taper_calc_len",
                            isError = length.isNotBlank() && (len == null || len <= 0.0),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        RateField(
                            rateText, { rateText = it },
                            isError = rateText.isNotBlank() &&
                                (slope == null || slope <= 0.0 ||
                                    issue == TaperCalcIssue.RATE_CONSUMES_DIA),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                when (result) {
                    TaperCalcResult.Incomplete -> Text(
                        "Enter any three values.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("taper_calc_waiting"),
                    )

                    is TaperCalcResult.Invalid -> Text(
                        issueMessage(result.issue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("taper_calc_issue"),
                    )

                    is TaperCalcResult.Solved -> SolvedBlock(result, unit)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("taper_calc_close")) {
                Text("Close")
            }
        },
    )
}

/**
 * Reads one entry field in the selected unit, as canonical mm. Blank and unreadable both come
 * back `null` — "not entered" for the solve; the field's own error state is what separates them.
 */
private fun entryToMm(raw: String, unit: UnitSystem): Double? {
    if (raw.isBlank()) return null
    val v = parseFractionOrDecimal(raw) ?: return null
    return unit.toMillimeters(v)
}

@Composable
private fun SolvedBlock(result: TaperCalcResult.Solved, unit: UnitSystem) {
    val rate = result.rate
    Column(
        Modifier.testTag("taper_calc_result"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val headline = when (result.unknown) {
            TaperCalcUnknown.RATE, null -> "Taper rate: ${rate.exactText}"
            TaperCalcUnknown.LARGE_DIA ->
                "Large end Ø (L.E.T.): ${taperCalcValueText(result.largeDiaMm, unit)}"
            TaperCalcUnknown.SMALL_DIA ->
                "Small end Ø (S.E.T.): ${taperCalcValueText(result.smallDiaMm, unit)}"
            TaperCalcUnknown.LENGTH -> "Length: ${taperCalcValueText(result.lengthMm, unit)}"
        }
        Text(headline, style = MaterialTheme.typography.bodyMedium)

        // The exact ratio is the answer; the common rate it lands on is the name a drawing
        // would carry. Naming it only when it differs keeps "1:12" from being said twice.
        val common = rate.commonText
        if (common != null && common != rate.exactText) {
            Text(
                "Within 3% of common taper $common",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unit == UnitSystem.INCHES) {
            Text(
                "${rate.inchesPerFootText}\"/ft",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("taper_calc_per_foot"),
            )
        }
        // All four typed: a mismatch is information about what was entered, not a rejection.
        // Nothing is rewritten to reconcile the two.
        if (result.typedSlopeAgrees == false) {
            Text(
                "The typed rate does not match these three values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("taper_calc_mismatch"),
            )
        }
    }
}

private fun issueMessage(issue: TaperCalcIssue): String = when (issue) {
    TaperCalcIssue.NON_POSITIVE_LENGTH -> "Length must be greater than 0."
    TaperCalcIssue.NON_POSITIVE_DIA -> "Both diameters must be greater than 0."
    TaperCalcIssue.NON_POSITIVE_RATE -> "Enter a rate like 1:12, 1/12, or 12."
    TaperCalcIssue.SET_NOT_SMALLER ->
        "The small end must be smaller than the large end — equal ends are a straight shaft, " +
            "which has no taper rate."
    TaperCalcIssue.RATE_CONSUMES_DIA ->
        "That rate over that length removes the whole large end; the small end would be 0 or less."
}

/** The rate field carries no unit suffix — a ratio is the same number on either drawing. */
@Composable
private fun RateField(
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Taper rate") },
        isError = isError,
        placeholder = { Text("e.g. 1:12") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().testTag("taper_calc_rate"),
    )
}
