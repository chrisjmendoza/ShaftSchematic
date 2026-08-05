package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.canonicalToUndercutStartMm
import com.android.shaftschematic.geom.undercutStartToCanonicalMm
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.util.UnitSystem

/**
 * Worn-section editor (list row + add/edit dialog) — authored on the Consolidated Output
 * tab, where the sections print (inside the consolidated sheet's profile). Extracted from
 * the Runout tab when that tab returned to runouts-only.
 */

/**
 * One worn section in the list: displayed Distance is re-projected from canonical against
 * the section's authored S.E.T. reference — display only, canonical never moves (the
 * WearSpotReference pattern). Tap to edit.
 */
@Composable
internal fun WornSectionRow(
    index: Int,
    section: WornSection,
    unit: UnitSystem,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onClick: () -> Unit,
) {
    val reference = wornSectionSetReference(section.authoredReference)
    val distMm = canonicalToUndercutStartMm(
        reference, section.startFromAftMm, section.lengthMm, aftSetXMm, fwdSetXMm,
    )
    val refLabel = if (reference == UndercutReference.FWD_SET) "FWD S.E.T." else "AFT S.E.T."
    val values = section.diaMm.filter { it > 0f }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Worn section $index", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${disp(distMm, unit)}${abbr(unit)} from $refLabel · " +
                    "L ${disp(section.lengthMm, unit)}${abbr(unit)} · " +
                    if (values.isEmpty()) "no readings yet" else "${values.size} Ø",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Worn sections author against the S.E.T.s only — map any non-SET value to AFT_SET. */
internal fun wornSectionSetReference(reference: UndercutReference): UndercutReference =
    if (reference == UndercutReference.FWD_SET) UndercutReference.FWD_SET
    else UndercutReference.AFT_SET

/**
 * Add/edit dialog for a worn section: S.E.T. reference chips, Distance + Length, and the
 * measured Ø list. All fields are unit-aware text inputs parsed on Save — typed values are
 * stored verbatim (golden rule); canonical start is derived from the authored Distance
 * once, at commit. Switching the reference chips re-projects the DISPLAYED distance so
 * the section never moves on a reference switch.
 */
@Composable
internal fun WornSectionDialog(
    existing: WornSection?,
    unit: UnitSystem,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onSave: (startFromAftMm: Float, lengthMm: Float, diaMm: List<Float>, reference: UndercutReference) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var reference by remember(existing?.id) {
        mutableStateOf(wornSectionSetReference(existing?.authoredReference ?: UndercutReference.AFT_SET))
    }
    var distanceText by remember(existing?.id) {
        mutableStateOf(
            existing?.let {
                disp(
                    canonicalToUndercutStartMm(
                        wornSectionSetReference(it.authoredReference),
                        it.startFromAftMm, it.lengthMm, aftSetXMm, fwdSetXMm,
                    ),
                    unit,
                )
            } ?: ""
        )
    }
    var lengthText by remember(existing?.id) {
        mutableStateOf(existing?.let { disp(it.lengthMm, unit) } ?: "")
    }
    val valueTexts = remember(existing?.id) {
        (existing?.diaMm?.map { disp(it, unit) } ?: emptyList())
            .ifEmpty { listOf("", "", "") }
            .toMutableStateList()
    }

    val distMm = toMmOrNull(distanceText, unit)
    val lenMm = toMmOrNull(lengthText, unit)
    val canSave = distMm != null && lenMm != null && lenMm > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add worn section" else "Edit worn section") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Measure from", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        UndercutReference.AFT_SET to "AFT S.E.T.",
                        UndercutReference.FWD_SET to "FWD S.E.T.",
                    ).forEach { (ref, label) ->
                        FilterChip(
                            selected = reference == ref,
                            onClick = {
                                if (reference != ref) {
                                    // Re-project the displayed distance under the new datum
                                    // so the section stays put — the canonical position is
                                    // what both projections share.
                                    val d = toMmOrNull(distanceText, unit)
                                    val l = toMmOrNull(lengthText, unit) ?: 0f
                                    if (d != null) {
                                        val canonical = undercutStartToCanonicalMm(
                                            reference, d, l, aftSetXMm, fwdSetXMm,
                                        )
                                        distanceText = disp(
                                            canonicalToUndercutStartMm(ref, canonical, l, aftSetXMm, fwdSetXMm),
                                            unit,
                                        )
                                    }
                                    reference = ref
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distance (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    label = { Text("Length (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Text("Measured diameters", style = MaterialTheme.typography.labelLarge)
                valueTexts.forEachIndexed { i, v ->
                    OutlinedTextField(
                        value = v,
                        onValueChange = { valueTexts[i] = it },
                        label = { Text("Ø ${i + 1} (${abbr(unit)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
                TextButton(
                    onClick = { valueTexts.add("") },
                    enabled = valueTexts.size < 6,
                ) { Text("Add another Ø") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val canonical = undercutStartToCanonicalMm(
                        reference, distMm ?: return@TextButton, lenMm ?: return@TextButton,
                        aftSetXMm, fwdSetXMm,
                    )
                    val values = valueTexts.mapNotNull { toMmOrNull(it, unit) }.filter { it > 0f }
                    onSave(canonical, lenMm, values, reference)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
