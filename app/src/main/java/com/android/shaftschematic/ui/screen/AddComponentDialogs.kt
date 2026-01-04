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
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.max

/* ────────────────────────────────────────────────────────────────────────────
 * Shared defaults for dialogs (no clash with other files)
 * ──────────────────────────────────────────────────────────────────────────── */

private data class AddDialogDefaults(
    val startMm: Float,
    val lastDiaMm: Float
)

/**
 * Compute convenient dialog defaults:
 * - startMm = end of the last component (max end across all lists) or 0 if none
 * - lastDiaMm = last known diameter (body.dia, else taper.endDia, else 25 mm)
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
    return AddDialogDefaults(startMm = startMm, lastDiaMm = lastDia)
}

/* ────────────────────────────────────────────────────────────────────────────
 * Utilities (dialog-local; names differ from other files to avoid confusion)
 * ──────────────────────────────────────────────────────────────────────────── */

private fun abbrFor(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

private fun toDisplayString(mm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) mm else mm / 25.4f
    val s = "%.${d}f".format(v).trimEnd('0').trimEnd('.')
    return if (s.isEmpty()) "0" else s
}

/** Decimal/fraction/ratio parser. Accepts "12", "3/4", "1.25", "1:12". Returns numeric value. */
private fun parseFractionOrDecimalOrRatio(input: String): Float? {
    var t = input.replace(",", "").trim()
    if (t.isEmpty()) return null

    // Tolerate unit-ish suffixes like "in", "mm", or quotes.
    run {
        val allowed = "0123456789./:+- "
        var end = t.length - 1
        while (end >= 0 && !allowed.contains(t[end])) end--
        t = if (end >= 0) t.substring(0, end + 1).trim() else ""
        t = t.replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
    }

    // Mixed fraction: W N/D
    val parts = t.split(' ').filter { it.isNotBlank() }
    if (parts.size == 2 && parts[1].contains('/')) {
        val whole = parts[0].toFloatOrNull() ?: return null
        val slash = parts[1].indexOf('/')
        val a = parts[1].substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = parts[1].substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        val frac = a / b
        return if (whole < 0f) whole - frac else whole + frac
    }

    val colon = t.indexOf(':')
    if (colon >= 0) {
        val a = t.substring(0, colon).trim().toFloatOrNull() ?: return null
        val b = t.substring(colon + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    val slash = t.indexOf('/')
    if (slash >= 0) {
        val a = t.substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = t.substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    return t.toFloatOrNull()
}

/** Convert display text → millimeters, accepting decimals or simple fractions. */
private fun toMmOrNullFromDialog(text: String, unit: UnitSystem): Float? {
    val v = parseFractionOrDecimalOrRatio(text) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) v else v * 25.4f
}

/** TPI conversion helpers (Thread dialog returns TPI; caller converts to pitch mm). */
private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/* ────────────────────────────────────────────────────────────────────────────
 * Body — Start, Length, Diameter (unit-aware)
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddBodyDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    onSubmit: (startMm: Float, lengthMm: Float, diaMm: Float) -> Unit
) {
    val d = rememberAddDialogDefaults(spec)

    var start by remember(unit, d.startMm) { mutableStateOf(toDisplayString(d.startMm, unit)) }
    var length by remember(unit) { mutableStateOf(toDisplayString(100f, unit)) }
    var dia by remember(unit, d.lastDiaMm) { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }

    val startMm = toMmOrNullFromDialog(start, unit) ?: -1f
    val lengthMm = toMmOrNullFromDialog(length, unit) ?: -1f
    val diaMm = toMmOrNullFromDialog(dia, unit) ?: -1f

    AlertDialog(
        onDismissRequest = { /* modal; use Cancel */ },
        title = { Text("Add Body") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbrFor(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbrFor(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Diameter (${abbrFor(unit)})", dia) { dia = it }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && diaMm > 0f
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, diaMm) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { /* host will close dialog by setting state */ }) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Liner — Start, Length, Outer Diameter
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddLinerDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    onSubmit: (startMm: Float, lengthMm: Float, odMm: Float) -> Unit
) {
    val d = rememberAddDialogDefaults(spec)

    var start by remember(unit, d.startMm) { mutableStateOf(toDisplayString(d.startMm, unit)) }
    var length by remember(unit) { mutableStateOf(toDisplayString(100f, unit)) }
    var od by remember(unit, d.lastDiaMm) { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }

    val startMm = toMmOrNullFromDialog(start, unit) ?: -1f
    val lengthMm = toMmOrNullFromDialog(length, unit) ?: -1f
    val odMm = toMmOrNullFromDialog(od, unit) ?: -1f

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Add Liner") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbrFor(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbrFor(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Outer Ø (${abbrFor(unit)})", od) { od = it }
            }
        },
        confirmButton = {
            val ok = startMm >= 0f && lengthMm > 0f && odMm > 0f
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, odMm) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { }) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Thread — Start, Length, Major Ø, TPI (always TPI; caller converts to pitch mm)
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddThreadDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    onSubmit: (startMm: Float, lengthMm: Float, majorDiaMm: Float, tpi: Float, excludeFromOAL: Boolean) -> Unit
) {
    val d = rememberAddDialogDefaults(spec)

    var start by remember(unit, d.startMm) { mutableStateOf(toDisplayString(d.startMm, unit)) }
    var length by remember(unit) { mutableStateOf(toDisplayString(32f, unit)) }
    var major by remember(unit, d.lastDiaMm) { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }
    var tpiText by remember { mutableStateOf("4") }
    var countInOal by remember { mutableStateOf(true) }

    val startMm = toMmOrNullFromDialog(start, unit) ?: -1f
    val lengthMm = toMmOrNullFromDialog(length, unit) ?: -1f
    val majorMm = toMmOrNullFromDialog(major, unit) ?: -1f
    val tpi = parseFractionOrDecimalOrRatio(tpiText) ?: -1f   // allow e.g., "20", "10", "32"

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Add Thread") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbrFor(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Major Ø (${abbrFor(unit)})", major) { major = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("TPI", tpiText) { tpiText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbrFor(unit)})", length) { length = it }
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
            val ok = startMm >= 0f && lengthMm > 0f && majorMm > 0f && tpi > 0f
            Button(enabled = ok, onClick = { onSubmit(startMm, lengthMm, majorMm, tpi, !countInOal) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { }) { Text("Cancel") } }
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
    onSubmit: (startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float, rateText: String) -> Unit
) {
    val d = rememberAddDialogDefaults(spec)

    var start by remember(unit, d.startMm) { mutableStateOf(toDisplayString(d.startMm, unit)) }
    var length by remember(unit) { mutableStateOf(toDisplayString(100f, unit)) }
    var setText by remember(unit, d.lastDiaMm) { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }
    var letText by remember(unit) { mutableStateOf("") } // allow deriving via rate
    var rateText by remember { mutableStateOf("1:12") }  // legacy default; bare "1" means 1:12

    val startMm = toMmOrNullFromDialog(start, unit) ?: -1f
    val lengthMm = toMmOrNullFromDialog(length, unit) ?: -1f
    val setMm = toMmOrNullFromDialog(setText, unit) ?: -1f   // -1 means "not provided"
    val letMm = toMmOrNullFromDialog(letText, unit) ?: -1f

    // Allow commit-on-blur behavior (no live mutation outward)
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Add Taper") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                CommitNumField("Start (${abbrFor(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbrFor(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("S.E.T. Ø (${abbrFor(unit)})", setText) { setText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("L.E.T. Ø (${abbrFor(unit)})", letText) { letText = it }
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
        dismissButton = { TextButton(onClick = { }) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Field composable that keeps local text; caller decides when to consume.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun CommitNumField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it }, // keep local while typing
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}
