// file: com/android/shaftschematic/ui/screen/AddComponentDialogs.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.max

/* ────────────────────────────────────────────────────────────────────────────
 * Shared defaults for dialogs (no clash with other files)
 * ──────────────────────────────────────────────────────────────────────────── */

private data class AddDialogDefaults(
    val startMm: Float,
    val lastDiaMm: Float,
    val bodyDiaMm: Float,
    val linerOdMm: Float
)

/**
 * Compute convenient dialog defaults:
 * - startMm = end of the last component (max end across all lists) or 0 if none
 * - lastDiaMm = last known diameter (body.dia, else taper.endDia, else 25 mm)
 * - bodyDiaMm = first body diameter when present, else lastDiaMm
 * - linerOdMm = first liner OD when present, else lastDiaMm
 */
@Composable
private fun rememberAddDialogDefaults(spec: ShaftSpec): AddDialogDefaults {
    val startMm = remember(spec) {
        listOfNotNull(
            spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        ).maxOrNull() ?: 0f
    }
    val lastDia = remember(spec) {
        spec.bodies.lastOrNull()?.diaMm
            ?: spec.tapers.lastOrNull()?.endDiaMm
            ?: 25f
    }
    val bodyDia = remember(spec) { spec.bodies.firstOrNull()?.diaMm ?: lastDia }
    val linerOd = remember(spec) { spec.liners.firstOrNull()?.odMm ?: lastDia }
    return AddDialogDefaults(startMm = startMm, lastDiaMm = lastDia, bodyDiaMm = bodyDia, linerOdMm = linerOd)
}

/* ────────────────────────────────────────────────────────────────────────────
 * Dialog-local utilities
 * ──────────────────────────────────────────────────────────────────────────── */

private fun toDisplayString(mm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) mm else mm / 25.4f
    val s = "%.${d}f".format(v).trimEnd('0').trimEnd('.')
    return if (s.isEmpty()) "0" else s
}


/* ────────────────────────────────────────────────────────────────────────────
 * Body — Start, Length, Diameter (unit-aware)
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddBodyDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    onSubmit: (startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    var start by remember(unit, effectiveStartMm) { mutableStateOf(toDisplayString(effectiveStartMm, unit)) }
    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var dia by remember(unit, d.bodyDiaMm) { mutableStateOf(toDisplayString(max(1f, d.bodyDiaMm), unit)) }

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val diaMm = toMmOrNull(dia, unit) ?: -1f

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Body") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbr(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Diameter (${abbr(unit)})", dia) { dia = it }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && diaMm > 0f
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, diaMm) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Liner — Start, Length, Outer Diameter
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddLinerDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    onSubmit: (startMm: Float, lengthMm: Float, odMm: Float) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    var start by remember(unit, effectiveStartMm) { mutableStateOf(toDisplayString(effectiveStartMm, unit)) }
    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var od by remember(unit, d.linerOdMm) { mutableStateOf(toDisplayString(max(1f, d.linerOdMm), unit)) }

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val odMm = toMmOrNull(od, unit) ?: -1f

    val startError = if (startMm >= 0f && lengthMm > 0f)
        startOverlapErrorMm(spec, "", ComponentKind.LINER, lengthMm, startMm)
    else null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Liner") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbr(unit)})", start, errorText = startError) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Outer Ø (${abbr(unit)})", od) { od = it }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && odMm > 0f && startError == null
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, odMm) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Thread — Start, Length, Major Ø, TPI (always TPI; caller converts to pitch mm)
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddThreadDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float,
    initialLengthMm: Float,
    initialMajorDiaMm: Float,
    initialPitchMm: Float,
    onSubmit: (startMm: Float, lengthMm: Float, majorDiaMm: Float, tpi: Float, excludeFromOAL: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)

    val effectiveStartMm = if (initialStartMm >= 0f) initialStartMm else d.startMm
    val effectiveLengthMm = if (initialLengthMm > 0f) initialLengthMm else 0f
    val effectiveMajorMm = if (initialMajorDiaMm > 0f) initialMajorDiaMm else d.lastDiaMm
    val initialTpi = pitchMmToTpi(initialPitchMm).takeIf { it > 0f } ?: 4f

    fun formatTpi(v: Float): String = "%1.3f".format(v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    var start by remember(unit, effectiveStartMm) { mutableStateOf(toDisplayString(effectiveStartMm, unit)) }
    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var major by remember(unit, effectiveMajorMm) { mutableStateOf(toDisplayString(max(1f, effectiveMajorMm), unit)) }
    var tpiText by remember(initialTpi) { mutableStateOf(formatTpi(initialTpi)) }
    var countInOal by remember { mutableStateOf(true) }

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val majorMm = toMmOrNull(major, unit) ?: -1f
    val tpi = parseFractionOrDecimal(tpiText) ?: -1f   // allow e.g., "20", "10", "32"

    val startError = if (startMm >= 0f && lengthMm > 0f)
        startOverlapErrorMm(spec, "", ComponentKind.THREAD, lengthMm, startMm)
    else null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Thread") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbr(unit)})", start, errorText = startError) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Major Ø (${abbr(unit)})", major) { major = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("TPI", tpiText) { tpiText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Count in OAL")
                    androidx.compose.material3.Switch(
                        checked = countInOal,
                        onCheckedChange = { countInOal = it }
                    )
                }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && majorMm > 0f && tpi > 0f && startError == null
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, majorMm, tpi, !countInOal) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Taper — Start, Length, S.E.T., L.E.T., Rate (ratio like "1:12", or "3/4", or "1")
 * Caller computes the missing diameter from the rate to keep logic centralized.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddTaperDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    onSubmit: (startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float, rateText: String) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    var start by remember(unit, effectiveStartMm) { mutableStateOf(toDisplayString(effectiveStartMm, unit)) }
    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var setText by remember(unit, d.lastDiaMm) { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }
    var letText by remember(unit) { mutableStateOf("") } // allow deriving via rate
    var rateText by remember { mutableStateOf("1:12") }  // legacy default; bare "1" means 1:12

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val setMm = toMmOrNull(setText, unit) ?: -1f   // -1 means "not provided"
    val letMm = toMmOrNull(letText, unit) ?: -1f

    // Allow commit-on-blur behavior (no live mutation outward)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Taper") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbr(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("S.E.T. Ø (${abbr(unit)})", setText) { setText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("L.E.T. Ø (${abbr(unit)})", letText) { letText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Taper Rate (1:12, 3/4, 1)", rateText) { rateText = it }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && (setMm > 0f || letMm > 0f)
            Button(
                enabled = ok,
                onClick = {
                    onSubmit(
                        startMm,
                        lengthMm,
                        if (setMm > 0f) setMm else -1f,
                        if (letMm > 0f) letMm else -1f,
                        rateText
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Field composable that keeps local text; caller decides when to consume.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun CommitNumField(
    label: String,
    initial: String,
    errorText: String? = null,
    onCommit: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = if (errorText != null) {
            { Text(errorText, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}
