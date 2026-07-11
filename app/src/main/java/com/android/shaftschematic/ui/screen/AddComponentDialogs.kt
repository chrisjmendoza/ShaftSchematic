// file: com/android/shaftschematic/ui/screen/AddComponentDialogs.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.util.collectAddWarnings
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
        // Bodies are fillers; excluded threads sit outside the shaft envelope.
        listOfNotNull(
            spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
            spec.threads.filter { !it.excludeFromOAL }.maxOfOrNull { it.startFromAftMm + it.lengthMm },
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
    overallIsManual: Boolean = false,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    onSubmit: (startMm: Float, lengthMm: Float, odMm: Float, reference: LinerAuthoredReference) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveLengthMm = initialLengthMm ?: 100f

    var isFwd by remember { mutableStateOf(false) }

    // Independent start strings so toggling direction doesn't overwrite user input.
    val defaultAftStartMm = initialStartMm ?: d.startMm
    var startAft by remember(unit, defaultAftStartMm) { mutableStateOf(toDisplayString(defaultAftStartMm, unit)) }
    var startFwd by remember(unit) { mutableStateOf("0") }

    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var od by remember(unit, d.linerOdMm) { mutableStateOf(toDisplayString(max(1f, d.linerOdMm), unit)) }

    val startEntered = toMmOrNull(if (isFwd) startFwd else startAft, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val odMm = toMmOrNull(od, unit) ?: -1f

    // Physical start from AFT.
    val physStartMm = if (isFwd) {
        if (startEntered >= 0f && lengthMm > 0f)
            (spec.overallLengthMm - startEntered - lengthMm).coerceAtLeast(0f)
        else -1f
    } else {
        startEntered
    }

    val startError = if (physStartMm >= 0f && lengthMm > 0f)
        startOverlapErrorMm(spec, "", ComponentKind.LINER, lengthMm, physStartMm)
    else null

    // Pre-submit collision / bounds warning state.
    var warningLines by remember { mutableStateOf(emptyList<String>()) }
    var warningAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (warningAction != null) {
        AlertDialog(
            onDismissRequest = { warningAction = null },
            title = { Text("Add Anyway?") },
            text = {
                Column {
                    warningLines.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                Button(onClick = { warningAction?.invoke(); warningAction = null }) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { warningAction = null }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Liner") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Measure From:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DirectionChip("AFT", selected = !isFwd) { isFwd = false }
                    DirectionChip("FWD", selected =  isFwd) { isFwd = true  }
                }
                CommitNumField(
                    label = "Start from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})",
                    initial = if (isFwd) startFwd else startAft,
                    errorText = startError
                ) { if (isFwd) startFwd = it else startAft = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Outer Ø (${abbr(unit)})", od) { od = it }
            }
        },
        confirmButton = {
            val ok = physStartMm >= 0f && lengthMm > 0f && odMm > 0f && startError == null
            Button(enabled = ok, onClick = {
                val ref = if (isFwd) LinerAuthoredReference.FWD else LinerAuthoredReference.AFT
                val action = { onSubmit(physStartMm, lengthMm, odMm, ref) }
                val warnings = collectAddWarnings(spec, physStartMm, lengthMm, overallIsManual)
                if (warnings.isEmpty()) action() else { warningLines = warnings; warningAction = action }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Coupler Bolt Slot — one axial row of radial cutouts.
 * Start, Hole Ø, Count, Spacing, Through/Blind (+ Depth). Reference defaults FWD.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddCouplerBoltSlotDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float,
    initialHoleDiaMm: Float,
    initialCount: Int,
    initialSpacingMm: Float,
    initialDepthMm: Float,
    onSubmit: (
        startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float,
        through: Boolean, depthMm: Float, reference: SlotAuthoredReference,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    // Default reference is FWD per spec.
    var isFwd by remember { mutableStateOf(true) }
    var through by remember { mutableStateOf(true) }

    var startAft by remember(unit, initialStartMm) { mutableStateOf(toDisplayString(initialStartMm, unit)) }
    var startFwd by remember(unit) { mutableStateOf("0") }
    var holeDia by remember(unit, initialHoleDiaMm) { mutableStateOf(toDisplayString(max(1f, initialHoleDiaMm), unit)) }
    var countText by remember(initialCount) { mutableStateOf(initialCount.coerceAtLeast(1).toString()) }
    var spacing by remember(unit, initialSpacingMm) { mutableStateOf(toDisplayString(initialSpacingMm, unit)) }
    var depth by remember(unit, initialDepthMm) { mutableStateOf(toDisplayString(initialDepthMm, unit)) }

    val holeDiaMm = toMmOrNull(holeDia, unit) ?: -1f
    val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 0
    val spacingMm = toMmOrNull(spacing, unit) ?: 0f
    val depthMm = toMmOrNull(depth, unit) ?: -1f
    val startEntered = toMmOrNull(if (isFwd) startFwd else startAft, unit) ?: -1f

    // Axial span from the aft-most cutout (i = 0) to the fwd-most, used for FWD anchoring.
    val rowSpanMm = (count - 1).coerceAtLeast(0) * spacingMm.coerceAtLeast(0f)

    // Physical position (from AFT) of the aft-most cutout center. When measuring from FWD, the
    // entered value locates the fwd-most cutout; the row then extends aft.
    val physStartMm = if (isFwd) {
        if (startEntered >= 0f) (spec.overallLengthMm - startEntered - rowSpanMm).coerceAtLeast(0f) else -1f
    } else {
        startEntered
    }

    // Bounds check: every cutout (center ± hole radius) must lie on the shaft. Mirrors
    // CouplerBoltSlot.isValid(); only enforced once an OAL exists to check against.
    val boundsError: String? =
        if (spec.overallLengthMm > 0f && physStartMm >= 0f && holeDiaMm > 0f && count >= 1) {
            val lastCenterMm = physStartMm + rowSpanMm
            val eps = 1e-3f
            when {
                physStartMm - holeDiaMm * 0.5f < -eps -> "Row extends past the AFT end of the shaft"
                lastCenterMm + holeDiaMm * 0.5f > spec.overallLengthMm + eps ->
                    "Row extends past the end of the shaft"
                else -> null
            }
        } else null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Coupler Bolt Slot") },
        text = {
            Column(Modifier.padding(top = 4.dp).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Measure From:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DirectionChip("AFT", selected = !isFwd) { isFwd = false }
                    DirectionChip("FWD", selected =  isFwd) { isFwd = true  }
                }
                CommitNumField(
                    label = "First slot from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})",
                    initial = if (isFwd) startFwd else startAft,
                    errorText = boundsError,
                ) { if (isFwd) startFwd = it else startAft = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Hole Ø (${abbr(unit)})", holeDia) { holeDia = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Count", countText) { countText = it }
                if (count > 1 || countText.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("Spacing (${abbr(unit)})", spacing) { spacing = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Through hole", Modifier.weight(1f))
                    Switch(checked = through, onCheckedChange = { through = it })
                }
                if (!through) {
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("Depth (${abbr(unit)})", depth) { depth = it }
                }
            }
        },
        confirmButton = {
            val ok = physStartMm >= 0f && holeDiaMm > 0f && count >= 1 &&
                (count == 1 || spacingMm > 0f) && (through || depthMm > 0f) &&
                boundsError == null
            Button(enabled = ok, onClick = {
                val ref = if (isFwd) SlotAuthoredReference.FWD else SlotAuthoredReference.AFT
                onSubmit(
                    physStartMm, holeDiaMm, count, spacingMm.coerceAtLeast(0f),
                    through, if (through) 0f else depthMm, ref,
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Thread — Start, Length, Major Ø, TPI (always TPI; caller converts to pitch mm)
 * ──────────────────────────────────────────────────────────────────────────── */

// CONTRACT (AddComponentDialogs.md): dialog/card parity.
// When countInOal=false, show "Thread end: AFT | FWD" chips and hide the Start field —
// mirroring ComponentCarousel.kt ResolvedThread !includeInOal block.
// Do not remove the AFT/FWD branch; isAftEnd must be passed through to addThreadAt().
@Composable
fun AddThreadDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    overallIsManual: Boolean = false,
    initialStartMm: Float,
    initialLengthMm: Float,
    initialMajorDiaMm: Float,
    initialPitchMm: Float,
    onSubmit: (startMm: Float, lengthMm: Float, majorDiaMm: Float, tpi: Float, excludeFromOAL: Boolean, isAftEnd: Boolean) -> Unit,
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
    var isAftEnd by remember { mutableStateOf(true) }

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val majorMm = toMmOrNull(major, unit) ?: -1f
    val tpi = parseFractionOrDecimal(tpiText) ?: -1f   // allow e.g., "20", "10", "32"

    val startError = if (!countInOal) null
                     else if (startMm >= 0f && lengthMm > 0f)
                         startOverlapErrorMm(spec, "", ComponentKind.THREAD, lengthMm, startMm)
                     else null

    // Pre-submit collision / bounds warning state.
    var warningLines by remember { mutableStateOf(emptyList<String>()) }
    var warningAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (warningAction != null) {
        AlertDialog(
            onDismissRequest = { warningAction = null },
            title = { Text("Add Anyway?") },
            text = {
                Column {
                    warningLines.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                Button(onClick = { warningAction?.invoke(); warningAction = null }) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { warningAction = null }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Thread") },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                if (countInOal) {
                    CommitNumField("Start (${abbr(unit)})", start, errorText = startError) { start = it }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thread end:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DirectionChip("AFT", selected =  isAftEnd) { isAftEnd = true  }
                        DirectionChip("FWD", selected = !isAftEnd) { isAftEnd = false }
                    }
                    Spacer(Modifier.height(4.dp))
                }
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
            val ok = (countInOal && startMm >= 0f || !countInOal) &&
                     lengthMm > 0f && majorMm > 0f && tpi > 0f && startError == null
            Button(enabled = ok, onClick = {
                val excludeFromOAL = !countInOal
                val action = { onSubmit(startMm, lengthMm, majorMm, tpi, excludeFromOAL, isAftEnd) }
                // Excluded threads don't live on the shaft span, so skip collision for them.
                val warnings = if (excludeFromOAL) emptyList()
                               else collectAddWarnings(spec, startMm, lengthMm, overallIsManual)
                if (warnings.isEmpty()) action() else { warningLines = warnings; warningAction = action }
            }) { Text("Add") }
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
    overallIsManual: Boolean = false,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    onSubmit: (startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float, rateText: String,
               keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
               keywayOffsetFromSetMm: Float, keywaySpooned: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)

    val defaultAftStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    // True = measuring from the FWD shaft face; false = from the AFT face.
    var isFwd by remember { mutableStateOf(false) }

    // Keep independent start strings per direction so toggling doesn't clobber user input.
    var startAft by remember(unit, defaultAftStartMm) { mutableStateOf(toDisplayString(defaultAftStartMm, unit)) }
    var startFwd by remember(unit) { mutableStateOf("0") }

    var length  by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var setText by remember(unit, d.lastDiaMm)       { mutableStateOf(toDisplayString(max(1f, d.lastDiaMm), unit)) }
    var letText by remember(unit) { mutableStateOf("") }  // allow deriving via rate
    var rateText by remember { mutableStateOf("1:12") }   // legacy default; bare "1" means 1:12

    // Keyway — all optional (blank = 0)
    var kwWidth   by remember { mutableStateOf("") }
    var kwDepth   by remember { mutableStateOf("") }
    var kwLength  by remember { mutableStateOf("") }
    var kwOffset  by remember { mutableStateOf("") }
    var kwSpooned by remember { mutableStateOf(false) }

    val startEntered = toMmOrNull(if (isFwd) startFwd else startAft, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val setMm = toMmOrNull(setText, unit) ?: -1f   // -1 means "not provided"
    val letMm = toMmOrNull(letText, unit) ?: -1f

    // Resolve physical AFT-origin start from the entered value.
    val physStartMm = if (isFwd) {
        if (startEntered >= 0f && lengthMm > 0f)
            (spec.overallLengthMm - startEntered - lengthMm).coerceAtLeast(0f)
        else -1f
    } else {
        startEntered
    }

    // Pre-submit collision / bounds warning state.
    var warningLines by remember { mutableStateOf(emptyList<String>()) }
    var warningAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (warningAction != null) {
        AlertDialog(
            onDismissRequest = { warningAction = null },
            title = { Text("Add Anyway?") },
            text = {
                Column {
                    warningLines.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                Button(onClick = { warningAction?.invoke(); warningAction = null }) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { warningAction = null }) { Text("Cancel") }
            }
        )
    }

    val keywayOffsetMm = toMmOrNull(kwOffset, unit) ?: 0f
    val isFloating = keywayOffsetMm > 0f

    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Taper") },
        text = {
            Column(Modifier.padding(top = 4.dp).verticalScroll(scroll)) {
                // Direction selector
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Direction:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DirectionChip("AFT", selected = !isFwd) { isFwd = false }
                    DirectionChip("FWD", selected =  isFwd) { isFwd = true  }
                }
                CommitNumField(
                    "Start from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})",
                    if (isFwd) startFwd else startAft
                ) { if (isFwd) startFwd = it else startAft = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                // SET is always Small End; LET is always Large End.
                // For AFT taper: SET is at the AFT (start) face.
                // For FWD taper: SET is at the FWD (end) face — the model stores AFT→FWD diameters,
                // so the submit handler swaps them.
                CommitNumField("S.E.T. Ø (${abbr(unit)})", setText) { setText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("L.E.T. Ø (${abbr(unit)})", letText) { letText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Taper Rate (1:12, 3/4, 1)", rateText) { rateText = it }
                Spacer(Modifier.height(12.dp))
                Text("Keyway (optional)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CommitNumField("KW W (${abbr(unit)})", kwWidth,
                        modifier = Modifier.weight(1f)) { kwWidth = it }
                    Text("×", style = MaterialTheme.typography.titleMedium)
                    CommitNumField("KW D (${abbr(unit)})", kwDepth,
                        modifier = Modifier.weight(1f)) { kwDepth = it }
                }
                Spacer(Modifier.height(8.dp))
                CommitNumField("KW L (${abbr(unit)})", kwLength) { kwLength = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("KW Offset from SET (${abbr(unit)})", kwOffset) { kwOffset = it }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isFloating) "Keyway spooned (N/A — floating)" else "Keyway spooned",
                        modifier = Modifier.weight(1f),
                        color = if (isFloating) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = kwSpooned && !isFloating,
                        enabled = !isFloating,
                        onCheckedChange = { if (!isFloating) kwSpooned = it }
                    )
                }
            }
        },
        confirmButton = {
            val ok = physStartMm >= 0f && lengthMm > 0f && (setMm > 0f || letMm > 0f)
            Button(
                enabled = ok,
                onClick = {
                    // For FWD taper the model stores diameters AFT→FWD, so LET is at the AFT
                    // (start) end and SET is at the FWD (end) end — swap them on submit.
                    val startDia = if (isFwd) (if (letMm > 0f) letMm else -1f)
                                  else        (if (setMm > 0f) setMm else -1f)
                    val endDia   = if (isFwd) (if (setMm > 0f) setMm else -1f)
                                  else        (if (letMm > 0f) letMm else -1f)
                    val kwW = toMmOrNull(kwWidth,  unit) ?: 0f
                    val kwD = toMmOrNull(kwDepth,  unit) ?: 0f
                    val kwL = toMmOrNull(kwLength, unit) ?: 0f
                    val kwO = toMmOrNull(kwOffset, unit) ?: 0f
                    val action = {
                        onSubmit(physStartMm, lengthMm, startDia, endDia, rateText,
                                 kwW, kwD, kwL, kwO, kwSpooned && !isFloating)
                    }
                    val warnings = collectAddWarnings(spec, physStartMm, lengthMm, overallIsManual)
                    if (warnings.isEmpty()) action() else { warningLines = warnings; warningAction = action }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Direction toggle chip (AFT / FWD)
 * Selected = 2dp primary border + tinted fill. Unselected = no border.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(0.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
            contentColor   = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Field composable that keeps local text; caller decides when to consume.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun CommitNumField(
    label: String,
    initial: String,
    errorText: String? = null,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit
) {
    // text is the live value; initial only resets it when the parent externally
    // changes it (e.g., unit toggle). Using LaunchedEffect instead of remember(initial)
    // avoids a cursor-to-end jump on every keystroke echo-back.
    var text by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) {
        if (text != initial) text = initial
    }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            onCommit(newText)   // commit on every keystroke so Add always has the current value
        },
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = if (errorText != null) {
            { Text(errorText, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}
