// file: app/src/main/java/com/android/shaftschematic/ui/screen/BoreKeywayCalcDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.BoreKeywayIssue
import com.android.shaftschematic.geom.BoreKeywayResult
import com.android.shaftschematic.geom.nearestFractionLabel
import com.android.shaftschematic.geom.roughCutterTargetDepth
import com.android.shaftschematic.geom.validateBoreKeyway
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseFractionOrDecimal
import java.util.Locale

/**
 * BoreKeywayCalcDialog — shop-floor calculator for rough-cutting a keyway in a bore.
 *
 * Given the bore Ø, the finished keyway (width × depth-at-edges), and up to two narrower
 * rough cutters, shows the depth to measure at each cutter's OUTER EDGES so its flat bottom
 * lands on the finished keyway's plane. Pure math in `geom/BoreKeywayMath.kt`.
 *
 * Deliberately a tool, not a document surface: it reads nothing from the shaft, stores
 * nothing (fields start blank every open — on-device: "it's just a tool I plan to use while
 * working"), marks nothing dirty, and prints on no sheet. That standalone posture is also why
 * it recomputes live per keystroke instead of committing on blur — there is no model write
 * for the commit-on-blur contract to guard.
 *
 * The in | mm chips label the ENTRY unit and are unit-reinterpreting, not converting: the
 * geometry is unit-independent, so typed numbers are simply read in the selected unit
 * (defaulted from the document, on-device preference). The scale-check fraction line — a
 * companion, never a substitute for the decimal — only prints in inches; a fraction of a
 * millimeter is not a thing any scale reads. The Scale chip (64 | 32 | 16, default 64) picks
 * the snap denominator for that line only; it never touches the decimal.
 */
@Composable
fun BoreKeywayCalcDialog(
    defaultUnit: UnitSystem,
    onDismiss: () -> Unit,
) {
    var unit by rememberSaveable { mutableStateOf(defaultUnit) }
    var scaleDenominator by rememberSaveable { mutableStateOf(64) }
    var boreDia by rememberSaveable { mutableStateOf("") }
    var finalWidth by rememberSaveable { mutableStateOf("") }
    var finalDepth by rememberSaveable { mutableStateOf("") }
    var cutter1 by rememberSaveable { mutableStateOf("") }
    var cutter2 by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("bore_kw_calc_dialog"),
        title = { Text("Keyway rough-cutter depth") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Depth to measure at a narrower cutter's edges so its flat bottom " +
                        "reaches the finished keyway's plane in a bore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                UnitChipRow(unit = unit, onSelect = { unit = it })
                // The scale-check fraction only ever prints in inches (see class doc), so the
                // chip that picks its snap grid is noise in mm mode.
                if (unit == UnitSystem.INCHES) {
                    ScaleChipRow(denominator = scaleDenominator, onSelect = { scaleDenominator = it })
                }

                // Everything is parsed and solved BEFORE the fields are laid out so each
                // field can carry its own error state — an entry that cannot produce a
                // depth has to read wrong AT THE FIELD, not only in the results block.
                val d = parseFractionOrDecimal(boreDia)
                val w = parseFractionOrDecimal(finalWidth)
                val dep = parseFractionOrDecimal(finalDepth)
                val baseIssue = if (d != null && w != null && dep != null) {
                    validateBoreKeyway(d, w, dep)
                } else null
                val rows = listOf(cutter1, cutter2).mapIndexed { i, raw ->
                    val cw = if (raw.isBlank()) null else parseFractionOrDecimal(raw)
                    CutterRow(
                        index = i + 1,
                        raw = raw,
                        width = cw,
                        result = if (cw != null && d != null && w != null && dep != null) {
                            roughCutterTargetDepth(d, w, dep, cw)
                        } else null,
                    )
                }

                CalcField(
                    boreDia, { boreDia = it }, "Bore Ø", unit, "bore_kw_calc_dia",
                    isError = boreDia.isNotBlank() && (d == null || d <= 0.0),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        CalcField(
                            finalWidth, { finalWidth = it }, "Keyway width", unit, "bore_kw_calc_w",
                            isError = finalWidth.isNotBlank() && (w == null || w <= 0.0 ||
                                baseIssue == BoreKeywayIssue.FINAL_WIDTH_EXCEEDS_BORE),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        // Keyway depth is measured at the edges by definition on this
                        // drawing; saying so in the label was noise (on-device report).
                        CalcField(
                            finalDepth, { finalDepth = it }, "Keyway depth", unit, "bore_kw_calc_d",
                            isError = finalDepth.isNotBlank() && (dep == null || dep <= 0.0),
                            placeholder = "e.g. 29/32",
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        CalcField(
                            cutter1, { cutter1 = it }, "Cutter 1 width", unit, "bore_kw_calc_c1",
                            isError = rows[0].isError,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        CalcField(
                            cutter2, { cutter2 = it }, "Cutter 2 width", unit, "bore_kw_calc_c2",
                            isError = rows[1].isError,
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                val entered = rows.filter { it.raw.isNotBlank() }
                when {
                    baseIssue != null -> Text(
                        baseIssueMessage(baseIssue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("bore_kw_calc_base_issue"),
                    )

                    d == null || w == null || dep == null || entered.isEmpty() -> Text(
                        "Enter bore, finished keyway, and at least one cutter width.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("bore_kw_calc_waiting"),
                    )

                    else -> entered.forEach { row -> CutterResult(row, dep, unit, scaleDenominator) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("bore_kw_calc_close")) {
                Text("Close")
            }
        },
    )
}

/** One cutter field's parsed input and solve, shared by its field's error state and its row. */
private data class CutterRow(
    val index: Int,
    val raw: String,
    val width: Double?,
    val result: BoreKeywayResult?,
) {
    /** A non-blank entry that cannot produce a depth — unreadable, or a rejected solve. */
    val isError: Boolean get() = raw.isNotBlank() && (width == null || result?.issue != null)
}

private fun baseIssueMessage(issue: BoreKeywayIssue): String = when (issue) {
    BoreKeywayIssue.NON_POSITIVE_INPUT -> "Bore, keyway width, and keyway depth must all be greater than 0."
    BoreKeywayIssue.FINAL_WIDTH_EXCEEDS_BORE -> "The keyway is as wide as the bore."
    // Cutter-scoped issues never reach the base validator.
    else -> "Check the bore and keyway values."
}

@Composable
private fun CutterResult(
    row: CutterRow,
    finalDepth: Double,
    unit: UnitSystem,
    scaleDenominator: Int,
) {
    val suffix = if (unit == UnitSystem.INCHES) "in" else "mm"
    val index = row.index
    val cutterWidth = row.width
    if (cutterWidth == null) {
        Text(
            "Cutter $index: unreadable width",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("bore_kw_calc_issue_$index"),
        )
        return
    }
    val depth = row.result?.depth
    if (depth == null) {
        val msg = when (row.result?.issue) {
            BoreKeywayIssue.NON_POSITIVE_INPUT -> "must be greater than 0"
            BoreKeywayIssue.CUTTER_WIDER_THAN_KEYWAY ->
                "wider than the finished keyway — a roughing cutter cuts inside it"
            BoreKeywayIssue.CUTTER_NEVER_BREAKS_SURFACE ->
                "this narrow, it never breaks the bore surface at its edges"
            // Bore/keyway failures print once above, not per cutter.
            else -> "check the bore and keyway values"
        }
        Text(
            "Cutter $index (${fmt(cutterWidth)} $suffix): $msg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("bore_kw_calc_issue_$index"),
        )
        return
    }

    Column(Modifier.testTag("bore_kw_calc_result_$index")) {
        val scale = if (unit == UnitSystem.INCHES) {
            nearestFractionLabel(depth, scaleDenominator)?.let { "  (≈ $it)" } ?: ""
        } else ""
        Text(
            "Cutter $index (${fmt(cutterWidth)} $suffix):  ${fmt(depth)} $suffix$scale",
            style = MaterialTheme.typography.bodyMedium,
        )
        // A cutter can only be narrower or equal, so the correction is never negative.
        val diff = finalDepth - depth
        if (diff > 0.0) {
            Text(
                "${fmt(diff)} $suffix shallower than the finished depth",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Three decimals, the app's shop-print convention everywhere else (`formatDiaWithUnit`,
 * `formatLenDim`). Trailing zeros are KEPT — a machining target reads 0.500, not 0.5 — and
 * the decimal stays the authoritative value whatever fraction prints beside it.
 */
private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)

/**
 * One entry-unit numeric field, shared by the standalone calculators ([TaperCalcDialog] uses
 * the identical control) rather than duplicated — the same promotion [UnitChipRow] got.
 */
@Composable
internal fun CalcField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    unit: UnitSystem,
    tag: String,
    isError: Boolean = false,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = isError,
        suffix = { Text(if (unit == UnitSystem.INCHES) "in" else "mm") },
        placeholder = { Text(placeholder) },
        singleLine = true,
        // Text keyboard, not decimal: fraction entry ("19/32", "1 1/2") needs '/' and space.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/**
 * Snap-grid denominator for the scale-check fraction line — 64 | 32 | 16, a coarser scale
 * reads fewer, coarser ticks. Affects only that label; the decimal result is unchanged.
 * Same chip conventions as [UnitChipRow]: label above, outline on EVERY chip.
 */
@Composable
private fun ScaleChipRow(denominator: Int, onSelect: (Int) -> Unit) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
    val restingBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val chosenBorder = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Scale",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(64, 32, 16).forEach { d ->
                val on = d == denominator
                FilterChip(
                    selected = on,
                    onClick = { onSelect(d) },
                    label = {
                        Text(
                            d.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = colors,
                    border = if (on) chosenBorder else restingBorder,
                    modifier = Modifier.weight(1f).testTag("bore_kw_calc_scale_$d"),
                )
            }
        }
    }
}

/**
 * Same chip conventions as `BlendSection.ChipRow`: label above, outline on EVERY chip.
 * Shared with [UnitConverterDialog] — the calculator's `in | mm` entry-unit chip is the
 * identical control, so it is promoted to `internal` rather than duplicated.
 */
@Composable
internal fun UnitChipRow(unit: UnitSystem, onSelect: (UnitSystem) -> Unit) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
    val restingBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val chosenBorder = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Units",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(UnitSystem.INCHES to "in", UnitSystem.MILLIMETERS to "mm").forEach { (u, lbl) ->
                val on = u == unit
                FilterChip(
                    selected = on,
                    onClick = { onSelect(u) },
                    label = {
                        Text(
                            lbl,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = colors,
                    border = if (on) chosenBorder else restingBorder,
                    modifier = Modifier.weight(1f).testTag("bore_kw_calc_unit_$lbl"),
                )
            }
        }
    }
}
