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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.keywayCount
import com.android.shaftschematic.model.suggestedBodyKeywayEnd
import com.android.shaftschematic.ui.input.classifyTaperSideByMidpoint
import com.android.shaftschematic.ui.input.oalAfterTaperAddMm
import com.android.shaftschematic.ui.input.taperAddDiameterOrder
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.util.collectAddWarnings
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.manualTaperRateBlockingMessage
import com.android.shaftschematic.util.manualTaperRateWarning
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.ThreadDesignation
import com.android.shaftschematic.util.toMmOrNull
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

/**
 * Title row shared by all five Add dialogs: the dialog's title text plus a launcher icon for
 * the standalone [UnitConverterDialog] tool (a quick mm ↔ in read-only calculator). The
 * launcher writes no component value, so it sits OUTSIDE the add-dialog-parity invariant — it
 * never has to appear on a carousel card.
 *
 * Each host dialog keeps its own `converterOpen` state and shows [UnitConverterDialog] as a
 * SIBLING `AlertDialog`, following this file's existing dialog-over-dialog pattern (the
 * collision-warning `AlertDialog`s each Add dialog already emits above its main one) — never
 * nested inside this title slot.
 */
@Composable
private fun addDialogTitleWithConverter(title: String, onOpenConverter: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        IconButton(onClick = onOpenConverter, modifier = Modifier.testTag("add_dialog_converter")) {
            Icon(Icons.Filled.Calculate, contentDescription = "Unit converter")
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Body — Start, Length, Diameter (unit-aware)
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * The keyway's unit, as an ENTRY control: it decides what the KW numbers typed under it mean.
 *
 * Distinct from the carousel's card-foot "Prints in" chip, which is display-only and card-only;
 * this one changes a value, so it appears in the Add dialogs as well as on the cards (the
 * dialog/card parity rule). Choosing the document unit clears the override.
 */
@Composable
internal fun KeywayUnitEntryChips(
    selected: UnitSystem,
    documentUnit: UnitSystem,
    onChoose: (UnitSystem?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Keyway in:", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DirectionChip("in", selected = selected == UnitSystem.INCHES) {
            onChoose(if (documentUnit == UnitSystem.INCHES) null else UnitSystem.INCHES)
        }
        DirectionChip("mm", selected = selected == UnitSystem.MILLIMETERS) {
            onChoose(if (documentUnit == UnitSystem.MILLIMETERS) null else UnitSystem.MILLIMETERS)
        }
    }
}

@Composable
fun AddBodyDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    /** Settings → Drawing → "Per-component units": gates the keyway's own unit chip. */
    perComponentUnitsEnabled: Boolean = false,
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
    onSubmit: (startMm: Float, lengthMm: Float, diaMm: Float,
               keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
               keywayOffsetFromEndMm: Float, keywayEnd: LinerAuthoredReference,
               keywaySpooned: Boolean, keyways180Apart: Boolean, keyways90Apart: Boolean,
               keyways90Cw: Boolean, keywayUnit: UnitSystem?,
               blendAftMm: Float, blendFwdMm: Float, blendProfile: BlendProfile,
               blendAftSeal: Boolean, blendFwdSeal: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    var start by remember(unit, effectiveStartMm) { mutableStateOf(toDisplayString(effectiveStartMm, unit)) }
    var length by remember(unit, effectiveLengthMm) { mutableStateOf(toDisplayString(effectiveLengthMm, unit)) }
    var dia by remember(unit, d.bodyDiaMm) { mutableStateOf(toDisplayString(max(1f, d.bodyDiaMm), unit)) }

    // Blend — mirrors the body card by contract (it changes drawn geometry, so it is under
    // the add-dialog-parity rule, not the card-only carve-out). Same shared BlendSection.
    var blendAftMode by remember { mutableStateOf(BlendFaceMode.SQUARE) }
    var blendFwdMode by remember { mutableStateOf(BlendFaceMode.SQUARE) }
    var blendAft by remember { mutableStateOf("") }
    var blendFwd by remember { mutableStateOf("") }
    var blendProfile by remember { mutableStateOf(BlendProfile.OGEE) }

    // Keyway — gated behind a checkbox (fields hidden until turned on); mirrors the body card.
    var kwEnabled by remember { mutableStateOf(false) }
    var kwWidth   by remember { mutableStateOf("") }
    var kwDepth   by remember { mutableStateOf("") }
    var kwLength  by remember { mutableStateOf("") }
    var kwOffset  by remember { mutableStateOf("") }
    // Default end = opposite the shaft's existing keyway when one side is taken
    // (`suggestedBodyKeywayEnd`) — a new body keyway defaulting onto the side an aft taper
    // keyway already holds reads as a second aft keyway (on-device report). The chips
    // always win; this only seeds them. Same seed on the carousel card (parity rule).
    var kwFwd     by remember { mutableStateOf(spec.suggestedBodyKeywayEnd() == LinerAuthoredReference.FWD) }
    var kwSpooned by remember { mutableStateOf(false) }
    // 180°/90° clocking are mutually exclusive; enforced locally here (mirrors the
    // ViewModel's clearing behavior) so the two switches never show both checked
    // before the value round-trips through onSubmit.
    var clock180  by remember { mutableStateOf(spec.keyways180Apart) }
    var clock90   by remember { mutableStateOf(spec.keyways90Apart) }
    var cw90      by remember { mutableStateOf(spec.keyways90Cw) }

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val diaMm = toMmOrNull(dia, unit) ?: -1f

    // Keyway values only count when the Keyway checkbox is on.
    // Null = the keyway follows the document unit, the default and the common case.
    var kwUnitOverride by remember { mutableStateOf<UnitSystem?>(null) }
    val kwUnit = kwUnitOverride ?: unit
    val kwW = if (kwEnabled) toMmOrNull(kwWidth,  kwUnit) ?: 0f else 0f
    val kwD = if (kwEnabled) toMmOrNull(kwDepth,  kwUnit) ?: 0f else 0f
    val kwL = if (kwEnabled) toMmOrNull(kwLength, kwUnit) ?: 0f else 0f
    val kwO = if (kwEnabled) toMmOrNull(kwOffset, kwUnit) ?: 0f else 0f
    val isFloating = kwO > 0f
    // Same condition as the carousel card's switch: it appears once the shaft will
    // have ≥ 2 keyways (≥ 1 existing plus the one being defined here).
    val showClockingToggle = kwEnabled && spec.keywayCount() >= 1 && kwW > 0f && kwD > 0f && kwL > 0f

    val scroll = rememberScrollState()

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Body") { converterOpen = true }
            } else {
                Text("Add Body")
            }
        },
        text = {
            Column(Modifier.padding(top = 4.dp).verticalScroll(scroll)) {
                CommitNumField("Start (${abbr(unit)})", start) { start = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length) { length = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Diameter (${abbr(unit)})", dia) { dia = it }
                Spacer(Modifier.height(12.dp))
                BlendSection(
                    aftMode = blendAftMode,
                    fwdMode = blendFwdMode,
                    profile = blendProfile,
                    onSetAftMode = { m ->
                        blendAftMode = m
                        if (m != BlendFaceMode.SQUARE && blendAft.isBlank()) {
                            blendAft = toDisplayString(defaultBlendMm(lengthMm), unit)
                        }
                    },
                    onSetFwdMode = { m ->
                        blendFwdMode = m
                        if (m != BlendFaceMode.SQUARE && blendFwd.isBlank()) {
                            blendFwd = toDisplayString(defaultBlendMm(lengthMm), unit)
                        }
                    },
                    onProfile = { blendProfile = it },
                    aftLengthField = {
                        CommitNumField("Blend AFT (${abbr(unit)})", blendAft) { blendAft = it }
                    },
                    fwdLengthField = {
                        CommitNumField("Blend FWD (${abbr(unit)})", blendFwd) { blendFwd = it }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keyway", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(
                        checked = kwEnabled,
                        onCheckedChange = { kwEnabled = it }
                    )
                }
                if (kwEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("KW from:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DirectionChip("AFT", selected = !kwFwd) { kwFwd = false }
                        DirectionChip("FWD", selected =  kwFwd) { kwFwd = true  }
                    }
                    // The keyway's own unit, offered here as well as on the card: it changes what
                    // the numbers below MEAN, so it is value entry and lives under the parity rule
                    // (unlike the card-only "Prints in" chip, which is display-only). A European
                    // keyway is whole millimetres on an otherwise imperial shaft.
                    if (perComponentUnitsEnabled) {
                        KeywayUnitEntryChips(kwUnit, unit) { kwUnitOverride = it }
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CommitNumField("KW W (${abbr(kwUnit)})", kwWidth,
                            modifier = Modifier.weight(1f)) { kwWidth = it }
                        Text("×", style = MaterialTheme.typography.titleMedium)
                        CommitNumField("KW D (${abbr(kwUnit)})", kwDepth,
                            modifier = Modifier.weight(1f)) { kwDepth = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("KW L (${abbr(kwUnit)})", kwLength) { kwLength = it }
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("KW Offset from ${if (kwFwd) "FWD" else "AFT"} (${abbr(kwUnit)})", kwOffset) { kwOffset = it }
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
                if (showClockingToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 180° apart", modifier = Modifier.weight(1f))
                        Switch(checked = clock180, onCheckedChange = { checked ->
                            clock180 = checked
                            if (checked) clock90 = false
                        })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 90° apart", modifier = Modifier.weight(1f))
                        Switch(checked = clock90, onCheckedChange = { checked ->
                            clock90 = checked
                            if (checked) clock180 = false
                        })
                    }
                    if (clock90) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From AFT keyway, viewed from aft:", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DirectionChip("CW", selected = cw90) { cw90 = true }
                            DirectionChip("CCW", selected = !cw90) { cw90 = false }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ok = bodyAddEnabled(startMm, lengthMm, diaMm)
            Button(enabled = ok, onClick = {
                onSubmit(
                    startMm, lengthMm, diaMm,
                    kwW, kwD, kwL, kwO,
                    if (kwFwd) LinerAuthoredReference.FWD else LinerAuthoredReference.AFT,
                    kwSpooned && !isFloating,
                    if (showClockingToggle) clock180 else spec.keyways180Apart,
                    if (showClockingToggle) clock90 else spec.keyways90Apart,
                    if (showClockingToggle) cw90 else spec.keyways90Cw,
                    if (kwEnabled) kwUnitOverride else null,
                    if (blendAftMode != BlendFaceMode.SQUARE) toMmOrNull(blendAft, unit) ?: 0f else 0f,
                    if (blendFwdMode != BlendFaceMode.SQUARE) toMmOrNull(blendFwd, unit) ?: 0f else 0f,
                    blendProfile,
                    blendAftMode == BlendFaceMode.SEAL,
                    blendFwdMode == BlendFaceMode.SEAL,
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Liner — Start, Length, Outer Diameter
 * ──────────────────────────────────────────────────────────────────────────── */

/** One end's shoulder values a new liner is created with. */
data class ShoulderEndDraft(val lenMm: Float, val odMm: Float, val radiusMm: Float)

/** Shoulders for a new liner; a null end has none. */
data class LinerShoulderDraft(val aft: ShoulderEndDraft? = null, val fwd: ShoulderEndDraft? = null)

@Composable
fun AddLinerDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    overallIsManual: Boolean = false,
    initialStartMm: Float? = null,
    initialLengthMm: Float? = null,
    /** The "Liner shoulders" Settings capability — parity with the liner card's gate. */
    linerShouldersEnabled: Boolean = false,
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
    onSubmit: (
        startMm: Float, lengthMm: Float, odMm: Float, reference: LinerAuthoredReference,
        shoulders: LinerShoulderDraft,
    ) -> Unit,
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

    // Shoulders (add-dialog-parity rule: they change drawn geometry, so the same section the
    // liner card shows appears here, under the same capability gate).
    var shAftOn by remember { mutableStateOf(false) }
    var shFwdOn by remember { mutableStateOf(false) }
    var shAftLen by remember(unit) { mutableStateOf("") }
    var shAftOd by remember(unit) { mutableStateOf("") }
    var shAftRadiusMm by remember { mutableStateOf(0f) }
    var shFwdLen by remember(unit) { mutableStateOf("") }
    var shFwdOd by remember(unit) { mutableStateOf("") }
    var shFwdRadiusMm by remember { mutableStateOf(0f) }

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

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Liner") { converterOpen = true }
            } else {
                Text("Add Liner")
            }
        },
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

                if (linerShouldersEnabled) {
                    Spacer(Modifier.height(8.dp))
                    LinerShoulderSection(
                        aftOn = shAftOn,
                        fwdOn = shFwdOn,
                        aftRadiusMm = shAftRadiusMm,
                        fwdRadiusMm = shFwdRadiusMm,
                        unit = unit,
                        onSetAftOn = { shAftOn = it },
                        onSetFwdOn = { shFwdOn = it },
                        onSetAftRadiusMm = { shAftRadiusMm = it },
                        onSetFwdRadiusMm = { shFwdRadiusMm = it },
                        aftFields = {
                            CommitNumField("Shoulder length (${abbr(unit)})", shAftLen) { shAftLen = it }
                            Spacer(Modifier.height(4.dp))
                            CommitNumField("Shoulder Ø (${abbr(unit)})", shAftOd) { shAftOd = it }
                        },
                        fwdFields = {
                            CommitNumField("Shoulder length (${abbr(unit)})", shFwdLen) { shFwdLen = it }
                            Spacer(Modifier.height(4.dp))
                            CommitNumField("Shoulder Ø (${abbr(unit)})", shFwdOd) { shFwdOd = it }
                        },
                    )
                }
            }
        },
        confirmButton = {
            val ok = linerAddEnabled(physStartMm, lengthMm, odMm, startError)
            Button(enabled = ok, onClick = {
                val ref = if (isFwd) LinerAuthoredReference.FWD else LinerAuthoredReference.AFT
                fun end(on: Boolean, len: String, sOd: String, rMm: Float): ShoulderEndDraft? {
                    if (!on) return null
                    val l = toMmOrNull(len, unit) ?: return null
                    val o = toMmOrNull(sOd, unit) ?: return null
                    if (l <= 0f || o <= 0f) return null
                    return ShoulderEndDraft(lenMm = l, odMm = o, radiusMm = rMm)
                }
                val shoulders = LinerShoulderDraft(
                    aft = end(shAftOn, shAftLen, shAftOd, shAftRadiusMm),
                    fwd = end(shFwdOn, shFwdLen, shFwdOd, shFwdRadiusMm),
                )
                val action = { onSubmit(physStartMm, lengthMm, odMm, ref, shoulders) }
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
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
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

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Coupler Bolt Slot") { converterOpen = true }
            } else {
                Text("Add Coupler Bolt Slot")
            }
        },
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
            val ok = couplerBoltSlotAddEnabled(
                physStartMm, holeDiaMm, count, spacingMm, through, depthMm, boundsError
            )
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

// CONTRACT (docs/contracts/AddComponentDialogs.md): dialog/card parity.
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
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
    onSubmit: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean,
               isAftEnd: Boolean, metricDesignation: String?) -> Unit,
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

    // Imperial (TPI, entered in the session unit) vs Metric (a self-declaring M-designation
    // that always names its own mm values — see `ThreadDesignation`).
    var metricMode by remember { mutableStateOf(false) }
    var designationText by remember { mutableStateOf("") }
    val parsedDesignation = if (metricMode) ThreadDesignation.parse(designationText) else null

    val startMm = toMmOrNull(start, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val majorMmImperial = toMmOrNull(major, unit) ?: -1f
    val majorMm = if (metricMode) (parsedDesignation?.majorDiaMm ?: -1f) else majorMmImperial
    val tpi = parseFractionOrDecimal(tpiText)?.toFloat() ?: -1f   // allow e.g., "20", "10", "32"
    // Pitch omitted from a coarse designation (e.g. "M20") reads as 0 — not "unset" — so a
    // metric thread never blocks on a pitch the designation deliberately left out.
    val pitchMm = if (metricMode) (parsedDesignation?.pitchMm ?: 0f) else tpiToPitchMm(tpi)
    // Metric gate: a real tpi-shaped value only when the designation parses, so the shared
    // gate's `tpi > 0f` check reads the same pass/fail without a second gate function.
    val tpiGate = if (metricMode) (if (parsedDesignation != null) 1f else -1f) else tpi

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

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Thread") { converterOpen = true }
            } else {
                Text("Add Thread")
            }
        },
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
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectionChip("Imperial (TPI)", selected = !metricMode) { metricMode = false }
                    DirectionChip("Metric (M-designation)", selected = metricMode) { metricMode = true }
                }
                if (metricMode) {
                    CommitNumField(
                        "Thread designation",
                        designationText,
                        errorText = if (designationText.isNotBlank() && parsedDesignation == null)
                            "e.g. M20×2.5" else null,
                        supportingText = if (designationText.isBlank()) "e.g. M20×2.5" else null,
                        keyboardType = KeyboardType.Ascii,
                    ) { designationText = it }
                    Spacer(Modifier.height(8.dp))
                } else {
                    CommitNumField("Major Ø (${abbr(unit)})", major) { major = it }
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("TPI", tpiText) { tpiText = it }
                    Spacer(Modifier.height(8.dp))
                }
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
            val ok = threadAddEnabled(countInOal, startMm, lengthMm, majorMm, tpiGate, startError)
            Button(enabled = ok, onClick = {
                val excludeFromOAL = !countInOal
                val designation = if (metricMode) parsedDesignation?.format() else null
                val action = { onSubmit(startMm, lengthMm, majorMm, pitchMm, excludeFromOAL, isAftEnd, designation) }
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
    /** Settings → Drawing → "Per-component units": gates the keyway's own unit chip. */
    perComponentUnitsEnabled: Boolean = false,
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
    onSubmit: (startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float, rateText: String,
               reference: LinerAuthoredReference,
               keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
               keywayOffsetFromSetMm: Float, keywaySpooned: Boolean, keyways180Apart: Boolean,
               keyways90Apart: Boolean, keyways90Cw: Boolean, keywayUnit: UnitSystem?) -> Unit,
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
    var rateText by remember { mutableStateOf("1:12") }   // manual-mode text; shop default
    var autoRate by remember { mutableStateOf(true) }

    // Keyway — all optional (blank = 0)
    var kwWidth   by remember { mutableStateOf("") }
    var kwDepth   by remember { mutableStateOf("") }
    var kwLength  by remember { mutableStateOf("") }
    var kwOffset  by remember { mutableStateOf("") }
    var kwSpooned by remember { mutableStateOf(false) }
    // 180°/90° clocking are mutually exclusive; enforced locally here (mirrors the
    // ViewModel's clearing behavior) so the two switches never show both checked
    // before the value round-trips through onSubmit.
    var clock180  by remember { mutableStateOf(spec.keyways180Apart) }
    var clock90   by remember { mutableStateOf(spec.keyways90Apart) }
    var cw90      by remember { mutableStateOf(spec.keyways90Cw) }

    val startEntered = toMmOrNull(if (isFwd) startFwd else startAft, unit) ?: -1f
    val lengthMm = toMmOrNull(length, unit) ?: -1f
    val setMm = toMmOrNull(setText, unit) ?: -1f   // -1 means "not provided"
    val letMm = toMmOrNull(letText, unit) ?: -1f
    val hasSet = setMm > 0f
    val hasLet = letMm > 0f
    val hasBothEnds = hasSet && hasLet
    val hasExactlyOneEnd = hasSet.xor(hasLet)
    // autoTaperRateText guards against the -1 sentinels (returns null unless both
    // diameters are real), so Auto never fabricates a rate from a missing end.
    val computedRateText = remember(lengthMm, setMm, letMm) {
        autoTaperRateText(
            lengthMm = lengthMm,
            setDiaMm = setMm,
            letDiaMm = letMm,
            exactDecimals = 3
        )
    }
    val manualRateBlock = if (!autoRate) {
        remember(rateText, lengthMm, setMm, letMm) {
            manualTaperRateBlockingMessage(rateText, lengthMm, setMm, letMm)
        }
    } else null
    val manualRateWarn = if (!autoRate) {
        remember(rateText, lengthMm, setMm, letMm) {
            manualTaperRateWarning(rateText, lengthMm, setMm, letMm)
        }
    } else null
    val autoRateIssue = if (autoRate && hasExactlyOneEnd) {
        "Auto needs Length + SET + LET. Switch to Manual to derive the missing end"
    } else null
    val rateIssueText = if (autoRate) autoRateIssue else (manualRateBlock ?: manualRateWarn)

    // The rate field's display is derived (computed in Auto, typed text in Manual);
    // rateText itself is never overwritten by Auto, so a typed manual rate survives
    // toggling Auto and back.
    val rateDisplayText = if (autoRate) computedRateText.orEmpty() else rateText

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

    // Null = the keyway follows the document unit, the default and the common case.
    var kwUnitOverride by remember { mutableStateOf<UnitSystem?>(null) }
    val kwUnit = kwUnitOverride ?: unit
    val keywayOffsetMm = toMmOrNull(kwOffset, kwUnit) ?: 0f
    val isFloating = keywayOffsetMm > 0f
    // Same condition as the carousel card's switch: it appears once the shaft will
    // have ≥ 2 keyways (≥ 1 existing plus the one being defined here).
    val kwDefined = (toMmOrNull(kwWidth, kwUnit) ?: 0f) > 0f &&
        (toMmOrNull(kwDepth, kwUnit) ?: 0f) > 0f &&
        (toMmOrNull(kwLength, kwUnit) ?: 0f) > 0f
    val showClockingToggle = spec.keywayCount() >= 1 && kwDefined

    val scroll = rememberScrollState()

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Taper") { converterOpen = true }
            } else {
                Text("Add Taper")
            }
        },
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
                // SET is always Small End; LET is always Large End, whichever end the start
                // was measured from. The model stores AFT→FWD diameters and SET faces the
                // nearer shaft end, so the submit handler orders them by the taper's physical
                // half — not by this dialog's direction chip.
                CommitNumField("S.E.T. Ø (${abbr(unit)})", setText) { setText = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("L.E.T. Ø (${abbr(unit)})", letText) { letText = it }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rate mode:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DirectionChip("Auto", selected = autoRate) { autoRate = true }
                    DirectionChip("Manual", selected = !autoRate) { autoRate = false }
                }
                CommitNumField(
                    "Taper Rate (1:12, 3/4, decimal)",
                    rateDisplayText,
                    keyboardType = KeyboardType.Ascii,
                    enabled = !autoRate,
                    supportingText = rateIssueText,
                    highlight = rateIssueText != null
                ) { rateText = it }
                Spacer(Modifier.height(12.dp))
                Text("Keyway (optional)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                // The keyway's own unit — value entry, so it appears here as well as on the card
                // (the parity rule); a European keyway is whole millimetres on an imperial shaft.
                if (perComponentUnitsEnabled) {
                    KeywayUnitEntryChips(kwUnit, unit) { kwUnitOverride = it }
                    Spacer(Modifier.height(4.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CommitNumField("KW W (${abbr(kwUnit)})", kwWidth,
                        modifier = Modifier.weight(1f)) { kwWidth = it }
                    Text("×", style = MaterialTheme.typography.titleMedium)
                    CommitNumField("KW D (${abbr(kwUnit)})", kwDepth,
                        modifier = Modifier.weight(1f)) { kwDepth = it }
                }
                Spacer(Modifier.height(8.dp))
                CommitNumField("KW L (${abbr(kwUnit)})", kwLength) { kwLength = it }
                Spacer(Modifier.height(8.dp))
                CommitNumField("KW Offset from SET (${abbr(kwUnit)})", kwOffset) { kwOffset = it }
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
                if (showClockingToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 180° apart", modifier = Modifier.weight(1f))
                        Switch(checked = clock180, onCheckedChange = { checked ->
                            clock180 = checked
                            if (checked) clock90 = false
                        })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 90° apart", modifier = Modifier.weight(1f))
                        Switch(checked = clock90, onCheckedChange = { checked ->
                            clock90 = checked
                            if (checked) clock180 = false
                        })
                    }
                    if (clock90) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From AFT keyway, viewed from aft:", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DirectionChip("CW", selected = cw90) { cw90 = true }
                            DirectionChip("CCW", selected = !cw90) { cw90 = false }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ok = taperAddEnabled(
                physStartMm = physStartMm,
                lengthMm = lengthMm,
                autoRate = autoRate,
                autoRateIssue = autoRateIssue,
                manualRateBlock = manualRateBlock,
                hasBothEnds = hasBothEnds,
                hasExactlyOneEnd = hasExactlyOneEnd,
                manualRateParses =
                    parseTaperRateText(rateText, allowAmbiguousBareOne = false) != null
            )
            Button(
                enabled = ok,
                onClick = {
                    // Which face SET lands on follows the taper's PHYSICAL half — the chip
                    // above only says which end the start was measured from, and a taper
                    // measured from one end can be placed in the other half. The half is
                    // judged against the OAL the shaft will have once this taper exists.
                    val addSide = classifyTaperSideByMidpoint(
                        startFromAftMm = physStartMm,
                        lengthMm = lengthMm,
                        overallLengthMm = oalAfterTaperAddMm(
                            currentOalMm = spec.overallLengthMm,
                            overallIsManual = overallIsManual,
                            startFromAftMm = physStartMm,
                            lengthMm = lengthMm,
                        ),
                    )
                    val (startDia, endDia) = taperAddDiameterOrder(
                        setDiaMm = if (setMm > 0f) setMm else -1f,
                        letDiaMm = if (letMm > 0f) letMm else -1f,
                        side = addSide,
                    )
                    val reference = if (isFwd) LinerAuthoredReference.FWD
                                    else       LinerAuthoredReference.AFT
                    // All four keyway numbers parse in `kwUnit` — the keyway-unit chip governs
                    // what they MEAN; parsing W/D/L in the document unit stored a metric keyway
                    // as inches while the offset (and the card) read it as millimetres.
                    val kwW = toMmOrNull(kwWidth,  kwUnit) ?: 0f
                    val kwD = toMmOrNull(kwDepth,  kwUnit) ?: 0f
                    val kwL = toMmOrNull(kwLength, kwUnit) ?: 0f
                    val kwO = toMmOrNull(kwOffset, kwUnit) ?: 0f
                    val submitRateText = if (autoRate) computedRateText.orEmpty() else rateText
                    val action = {
                        onSubmit(physStartMm, lengthMm, startDia, endDia, submitRateText,
                                 reference,
                                 kwW, kwD, kwL, kwO, kwSpooned && !isFloating,
                                 if (showClockingToggle) clock180 else spec.keyways180Apart,
                                 if (showClockingToggle) clock90 else spec.keyways90Apart,
                                 if (showClockingToggle) cw90 else spec.keyways90Cw,
                                 if (kwDefined) kwUnitOverride else null)
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
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    enabled: Boolean = true,
    highlight: Boolean = false,
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
        enabled = enabled,
        singleLine = true,
        isError = highlight || errorText != null,
        supportingText = if (errorText != null || supportingText != null) {
            {
                Text(
                    errorText ?: supportingText.orEmpty(),
                    color = if (highlight || errorText != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}
