// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperCalcApplyDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.input.TaperCalcValues
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.util.UnitSystem

internal const val TAPER_CALC_APPLY_DIALOG_TAG = "taper_calc_apply_dialog"
internal const val TAPER_CALC_APPLY_CONFIRM_TAG = "taper_calc_apply_confirm"
internal const val TAPER_CALC_APPLY_BACK_TAG = "taper_calc_apply_back"

/**
 * Asks where a solved taper goes and shows what the write would change, before it happens: a
 * calculator writing into a job is a new thing for it to do, so nothing lands on the first tap.
 * The list is every taper on the drawing plus a new taper at each end that has none, so a shaft
 * with no taper is asked only which end. Back returns to the calculator with its entries intact.
 *
 * All content comes from the pure plan in [planTaperCalcApply].
 */
@Composable
internal fun TaperCalcApplyDialog(
    spec: ShaftSpec,
    values: TaperCalcValues,
    entryUnit: UnitSystem,
    documentUnit: UnitSystem,
    selectedComponentId: String?,
    /** Names the drawing the write lands on, once the document has two; `null` while it has one. */
    drawingLabel: String?,
    onConfirm: (TaperCalcChoice) -> Unit,
    onBack: () -> Unit,
) {
    val choices = remember(spec) { taperCalcChoices(spec) }
    val titles = remember(spec) { buildTaperTitleById(spec) }
    var chosenKey by rememberSaveable {
        mutableStateOf(defaultTaperCalcChoice(choices, selectedComponentId)?.key)
    }
    val chosen = choices.firstOrNull { it.key == chosenKey }
    val plan = remember(spec, chosen, values, entryUnit, documentUnit) {
        chosen?.let { planTaperCalcApply(spec, it, values, entryUnit, documentUnit) }
    }
    val hasTaper = spec.tapers.isNotEmpty()

    AlertDialog(
        onDismissRequest = onBack,
        modifier = Modifier.testTag(TAPER_CALC_APPLY_DIALOG_TAG),
        title = { Text("Apply to shaft") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                drawingLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    if (hasTaper) "Which taper gets these values?"
                    else "This shaft has no taper yet. Add one at which end?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (spec.overallLengthMm <= 0f) {
                    Text(
                        "Enter the overall length to add a taper at the FWD end.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    choices.forEach { choice ->
                        ChoiceRow(
                            label = when (choice) {
                                is TaperCalcChoice.Existing -> titles[choice.taperId] ?: "Taper"
                                is TaperCalcChoice.NewAtEnd -> "New taper at the ${choice.side.name} end"
                            },
                            selected = choice.key == chosenKey,
                            tag = "taper_calc_choice_${choice.key}",
                            onSelect = { chosenKey = choice.key },
                        )
                    }
                }
                plan?.let { p ->
                    HorizontalDivider()
                    p.rows.forEach { ChangeRow(it) }
                    Text(
                        p.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    p.warnings.forEach { warning ->
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("taper_calc_apply_warning"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { chosen?.let(onConfirm) },
                enabled = plan != null,
                modifier = Modifier.testTag(TAPER_CALC_APPLY_CONFIRM_TAG),
            ) {
                Text(if (chosen is TaperCalcChoice.NewAtEnd) "Add taper" else "Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onBack, modifier = Modifier.testTag(TAPER_CALC_APPLY_BACK_TAG)) {
                Text("Back")
            }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The row owns the click and the semantics, so the button itself takes neither.
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** `Length (in)   11 1/2 → 12`; a value the write does not change reads once, not as `12 → 12`. */
@Composable
private fun ChangeRow(row: TaperCalcChangeRow) {
    val changed = row.before != null && row.before != row.after
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (changed) "${row.before} → ${row.after}" else row.after,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
