// file: com/android/shaftschematic/ui/screen/AddComponentDialogs.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.ui.text.input.TextFieldValue
import com.android.shaftschematic.ui.input.rememberFieldCursor
import com.android.shaftschematic.ui.input.insertAtCursor
import com.android.shaftschematic.ui.input.LocalFractionKeyEnabled
import com.android.shaftschematic.ui.input.FractionKey
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.keywayCount
import com.android.shaftschematic.model.suggestedBodyKeywayEnd
import com.android.shaftschematic.model.suggestedThreadReliefAtAft
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.ui.input.classifyTaperSideByMidpoint
import com.android.shaftschematic.ui.input.taperAddDiameterOrder
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.util.MUST_BE_POSITIVE_MSG
import com.android.shaftschematic.ui.util.collectAddWarnings
import com.android.shaftschematic.ui.util.proposedSpanOverlapMessage
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.filterTaperRateInput
import com.android.shaftschematic.util.manualTaperRateBlockingMessage
import com.android.shaftschematic.util.manualTaperRateWarning
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.isUnitlessNumberText
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.parseLengthMmOrNull
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.ThreadDesignation
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.max
import java.util.Locale

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
 * - startMm = see [addDialogStartMm]
 * - lastDiaMm = see [addDialogLastDiaMm]
 * - bodyDiaMm = see [addDialogBodyDiaMm]
 * - linerOdMm = first liner OD when present, else lastDiaMm
 */
@Composable
private fun rememberAddDialogDefaults(spec: ShaftSpec): AddDialogDefaults {
    // Fallback only — the editor's chooser passes its own Start, from the same rule.
    val startMm = remember(spec) { addDialogStartMm(spec) }
    val lastDia = remember(spec) { addDialogLastDiaMm(spec) }
    val bodyDia = remember(spec) { addDialogBodyDiaMm(spec, lastDia) }
    val linerOd = remember(spec) { spec.liners.firstOrNull()?.odMm ?: lastDia }
    return AddDialogDefaults(startMm = startMm, lastDiaMm = lastDia, bodyDiaMm = bodyDia, linerOdMm = linerOd)
}

/**
 * The "last known diameter" the Add dialogs seed from (mm) — the Add Taper S.E.T. Ø, the thread
 * major Ø and the liner-OD fallback: the last explicit body's Ø, else the last taper's end Ø,
 * else the shaft-wide bare-shaft Ø (`ShaftSpec.autoBodyDiaMm` — the new-shaft prompt), else
 * [ADD_DIALOG_DIA_FALLBACK_MM]. Without the bare-shaft step a new 8″ drawing offered a 25 mm
 * (0.984″) taper (on-device report).
 */
internal fun addDialogLastDiaMm(spec: ShaftSpec): Float =
    spec.bodies.lastOrNull()?.diaMm
        ?: spec.tapers.lastOrNull()?.endDiaMm
        ?: spec.autoBodyDiaMm.takeIf { it > 0f }
        ?: ADD_DIALOG_DIA_FALLBACK_MM

/** The diameter an Add dialog seeds when the drawing offers nothing to copy (mm). */
internal const val ADD_DIALOG_DIA_FALLBACK_MM = 25f

/**
 * The Ø `AddBodyDialog` opens with (mm): the first explicit body's Ø, else the shaft-wide
 * bare-shaft Ø (`ShaftSpec.autoBodyDiaMm` — the new-shaft prompt and the auto-body card's "All
 * auto sections"), else [fallbackDiaMm]. An authored body wins over the bare-shaft Ø; without the
 * bare-shaft step a new 8″ drawing offered a 25 mm (0.984″) body (on-device report).
 */
internal fun addDialogBodyDiaMm(spec: ShaftSpec, fallbackDiaMm: Float): Float =
    spec.bodies.firstOrNull()?.diaMm
        ?: spec.autoBodyDiaMm.takeIf { it > 0f }
        ?: fallbackDiaMm

/* ────────────────────────────────────────────────────────────────────────────
 * Dialog-local utilities
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * An Add-dialog seed's text. Formatted in [Locale.US] whatever the device locale: a comma-decimal
 * locale formats 12.0000 as `12,0000`, which trims to `12,` — text the parser refuses, so every
 * whole-number seed would open as "Not a number".
 */
internal fun toDisplayString(mm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) mm else mm / 25.4f
    val s = "%.${d}f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    return if (s.isEmpty()) "0" else s
}

/** An Add-dialog field seeded with [seedMm]: exact until edited (see [SeededMmField]). */
@Composable
private fun rememberSeededMm(seedMm: Float, unit: UnitSystem): SeededMmField =
    remember(unit, seedMm) { SeededMmField(seedMm, toDisplayString(seedMm, unit)) }

/** [rememberSeededMm] that survives a configuration change. */
@Composable
private fun rememberSaveableSeededMm(seedMm: Float, unit: UnitSystem): SeededMmField =
    rememberSaveable(unit, seedMm, saver = SeededMmField.Saver) {
        SeededMmField(seedMm, toDisplayString(seedMm, unit))
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
               blendAftSeal: Boolean, blendFwdSeal: Boolean,
               blendAftSealLenMm: Float, blendFwdSealLenMm: Float,
               blendAftToLinerOd: Boolean, blendFwdToLinerOd: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)
    val effectiveStartMm = initialStartMm ?: d.startMm
    val effectiveLengthMm = initialLengthMm ?: 100f

    val start = rememberSeededMm(effectiveStartMm, unit)
    val length = rememberSeededMm(effectiveLengthMm, unit)
    val dia = rememberSeededMm(max(1f, d.bodyDiaMm), unit)

    // Component blend + seal areas — mirror the body card by contract (they change drawn
    // geometry, so they are under the add-dialog-parity rule, not the card-only carve-out). Same
    // shared BlendSection. The seal flags are independent of the blend.
    var blendAftMode by remember { mutableStateOf(BlendFaceMode.SQUARE) }
    var blendFwdMode by remember { mutableStateOf(BlendFaceMode.SQUARE) }
    val blendAft = remember { SeededMmField.empty() }
    val blendFwd = remember { SeededMmField.empty() }
    var sealAft by remember { mutableStateOf(false) }
    var sealFwd by remember { mutableStateOf(false) }
    val blendAftSealLen = remember { SeededMmField.empty() }
    val blendFwdSealLen = remember { SeededMmField.empty() }
    var blendProfile by remember { mutableStateOf(BlendProfile.OGEE) }
    // "At a liner": Seat (the derived half-way shoulder) unless the user picks Liner OD.
    var blendAftLinerTarget by remember { mutableStateOf(BlendLinerTarget.SEAT) }
    var blendFwdLinerTarget by remember { mutableStateOf(BlendLinerTarget.SEAT) }

    // Keyway — gated behind a checkbox (fields hidden until turned on); mirrors the body card.
    var kwEnabled by remember { mutableStateOf(false) }
    var kwWidth   by remember { mutableStateOf("") }
    var kwDepth   by remember { mutableStateOf("") }
    var kwLength  by remember { mutableStateOf("") }
    // Captured keyway: local toggle; `kwOffset` holds the inset text it reveals.
    var kwCaptured by remember { mutableStateOf(false) }
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

    val startMm = start.mm(unit) ?: -1f
    val lengthMm = length.mm(unit) ?: -1f
    val diaMm = dia.mm(unit) ?: -1f

    // Keyway values only count when the Keyway checkbox is on.
    // Null = the keyway follows the document unit, the default and the common case.
    var kwUnitOverride by remember { mutableStateOf<UnitSystem?>(null) }
    val kwUnit = kwUnitOverride ?: unit
    val kwW = if (kwEnabled) toMmOrNull(kwWidth,  kwUnit) ?: 0f else 0f
    val kwD = if (kwEnabled) toMmOrNull(kwDepth,  kwUnit) ?: 0f else 0f
    val kwL = if (kwEnabled) toMmOrNull(kwLength, kwUnit) ?: 0f else 0f
    // The inset text only counts while "Captured keyway" is on; switching it off holds the
    // text until submit but commits an open keyway (inset 0).
    val kwO = if (kwEnabled && kwCaptured) toMmOrNull(kwOffset, kwUnit) ?: 0f else 0f
    val isCaptured = kwCaptured && kwO > 0f
    // Same condition as the carousel card's switch: it appears once the shaft will
    // have ≥ 2 keyways (≥ 1 existing plus the one being defined here).
    val showClockingToggle = kwEnabled && spec.keywayCount() >= 1 && kwW > 0f && kwD > 0f && kwL > 0f

    val scroll = rememberScrollState()

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    DiscardGuardedDialog(
        discardTitle = "Discard this body?",
        onDismiss = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Body") { converterOpen = true }
            } else {
                Text("Add Body")
            }
        },
        text = {
            Column(Modifier.padding(top = 4.dp).verticalScroll(scroll)) {
                CommitNumField(
                    "Start (${abbr(unit)})", start.text,
                    errorText = addDialogNegativeStartError(start.mm(unit)),
                ) { start.onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length.text, requirePositive = true) { length.onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Diameter (${abbr(unit)})", dia.text, requirePositive = true) { dia.onText(it) }
                Spacer(Modifier.height(12.dp))
                BlendSection(
                    aftMode = blendAftMode,
                    fwdMode = blendFwdMode,
                    profile = blendProfile,
                    aftSeal = sealAft,
                    fwdSeal = sealFwd,
                    aftLinerTarget = blendAftLinerTarget,
                    fwdLinerTarget = blendFwdLinerTarget,
                    onSetAftLinerTarget = { blendAftLinerTarget = it },
                    onSetFwdLinerTarget = { blendFwdLinerTarget = it },
                    onSetAftMode = { m ->
                        blendAftMode = m
                        if (m == BlendFaceMode.BLEND && blendAft.text.isBlank()) {
                            defaultBlendMm(lengthMm).let { blendAft.reseed(it, toDisplayString(it, unit)) }
                        }
                    },
                    onSetFwdMode = { m ->
                        blendFwdMode = m
                        if (m == BlendFaceMode.BLEND && blendFwd.text.isBlank()) {
                            defaultBlendMm(lengthMm).let { blendFwd.reseed(it, toDisplayString(it, unit)) }
                        }
                    },
                    onProfile = { blendProfile = it },
                    onSetAftSeal = { on ->
                        sealAft = on
                        if (on && blendAftSealLen.text.isBlank()) {
                            defaultSealLenMm(lengthMm).let { blendAftSealLen.reseed(it, toDisplayString(it, unit)) }
                        }
                    },
                    onSetFwdSeal = { on ->
                        sealFwd = on
                        if (on && blendFwdSealLen.text.isBlank()) {
                            defaultSealLenMm(lengthMm).let { blendFwdSealLen.reseed(it, toDisplayString(it, unit)) }
                        }
                    },
                    aftLengthField = {
                        CommitNumField("Blend AFT (${abbr(unit)})", blendAft.text) { blendAft.onText(it) }
                    },
                    fwdLengthField = {
                        CommitNumField("Blend FWD (${abbr(unit)})", blendFwd.text) { blendFwd.onText(it) }
                    },
                    aftSealLengthField = {
                        CommitNumField("Seal area AFT (${abbr(unit)})", blendAftSealLen.text) { blendAftSealLen.onText(it) }
                    },
                    fwdSealLengthField = {
                        CommitNumField("Seal area FWD (${abbr(unit)})", blendFwdSealLen.text) { blendFwdSealLen.onText(it) }
                    },
                )
                Spacer(Modifier.height(12.dp))
                // The shared gate row the cards carry. No confirm on untick: nothing is stored
                // until submit, and the hidden fields submit zeros while the gate is off.
                KeywayGateRow(checked = kwEnabled, testTag = "add_body_kw_gate") { kwEnabled = it }
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
                    Spacer(Modifier.height(4.dp))
                    CapturedKeywayToggleRow(
                        checked = kwCaptured,
                        testTag = "add_body_kw_captured",
                    ) { on ->
                        kwCaptured = on
                        if (on && kwOffset.isBlank()) kwOffset = dispKw(defaultKeywayInsetMm(), kwUnit)
                    }
                    if (kwCaptured) {
                        CommitNumField("KW Inset from ${if (kwFwd) "FWD" else "AFT"} (${abbr(kwUnit)})", kwOffset) { kwOffset = it }
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isCaptured) "Keyway spooned (N/A — captured)" else "Keyway spooned",
                            modifier = Modifier.weight(1f),
                            color = if (isCaptured) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        DialogSwitch(
                            checked = kwSpooned && !isCaptured,
                            enabled = !isCaptured,
                            onCheckedChange = { if (!isCaptured) kwSpooned = it }
                        )
                    }
                }
                if (showClockingToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 180° apart", modifier = Modifier.weight(1f))
                        DialogSwitch(checked = clock180, onCheckedChange = { checked ->
                            clock180 = checked
                            if (checked) clock90 = false
                        })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keyways 90° apart", modifier = Modifier.weight(1f))
                        DialogSwitch(checked = clock90, onCheckedChange = { checked ->
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
            // Every visible optional field must read too: one showing "Not a number" would
            // submit 0 in place of the typed value.
            val ok = bodyAddEnabled(startMm, lengthMm, diaMm) && optionalEntriesReadable(
                keywayEntryTexts(kwEnabled, kwCaptured, kwWidth, kwDepth, kwLength, kwOffset) + listOf(
                    blendAft.text.takeIf { blendAftMode == BlendFaceMode.BLEND },
                    blendFwd.text.takeIf { blendFwdMode == BlendFaceMode.BLEND },
                    blendAftSealLen.text.takeIf { sealAft },
                    blendFwdSealLen.text.takeIf { sealFwd },
                )
            )
            Button(enabled = ok, onClick = {
                onSubmit(
                    startMm, lengthMm, diaMm,
                    kwW, kwD, kwL, kwO,
                    if (kwFwd) LinerAuthoredReference.FWD else LinerAuthoredReference.AFT,
                    kwEnabled && kwSpooned && !isCaptured,
                    if (showClockingToggle) clock180 else spec.keyways180Apart,
                    if (showClockingToggle) clock90 else spec.keyways90Apart,
                    if (showClockingToggle) cw90 else spec.keyways90Cw,
                    if (kwEnabled) kwUnitOverride else null,
                    if (blendAftMode == BlendFaceMode.BLEND) blendAft.mm(unit) ?: 0f else 0f,
                    if (blendFwdMode == BlendFaceMode.BLEND) blendFwd.mm(unit) ?: 0f else 0f,
                    blendProfile,
                    sealAft,
                    sealFwd,
                    if (sealAft) blendAftSealLen.mm(unit) ?: 0f else 0f,
                    if (sealFwd) blendFwdSealLen.mm(unit) ?: 0f else 0f,
                    blendAftMode == BlendFaceMode.BLEND && blendAftLinerTarget.toLinerOd,
                    blendFwdMode == BlendFaceMode.BLEND && blendFwdLinerTarget.toLinerOd,
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

    // Independent start strings so toggling direction doesn't overwrite user input. Both seeds
    // come from the ONE seeder, each from its own reference end, so neither opens on an overlap
    // while free shaft exists (addStartSeed).
    val defaultAftStartMm = initialStartMm ?: addStartSeed(spec, effectiveLengthMm).startMm
    val defaultFwdStartMm = remember(spec, effectiveLengthMm) { addSeedFromFwdMm(spec, effectiveLengthMm) }
    val startAft = rememberSeededMm(defaultAftStartMm, unit)
    val startFwd = rememberSeededMm(defaultFwdStartMm, unit)

    val length = rememberSeededMm(effectiveLengthMm, unit)
    val od = rememberSeededMm(max(1f, d.linerOdMm), unit)

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

    val startEnteredOrNull = (if (isFwd) startFwd else startAft).mm(unit)
    val startEntered = startEnteredOrNull ?: -1f
    val lengthMm = length.mm(unit) ?: -1f
    val odMm = od.mm(unit) ?: -1f

    // Physical start from AFT. A FWD-measured liner that would reach past the AFT face is NOT
    // clamped onto it — that would store a different FWD distance than the one typed; it is
    // refused below, as the card refuses a negative start.
    val fwdStartPastAft = addDialogFwdStartPastAft(isFwd, startEntered, lengthMm, spec.overallLengthMm)
    val physStartMm = addDialogPhysStartMm(isFwd, startEntered, lengthMm, spec.overallLengthMm)

    // The inline line names every overlap BEFORE Add, the same way from either reference end:
    // another liner blocks (startError — the card refuses the same span), a taper or thread is
    // shown here and confirmed through "Add Anyway?" (overlapNote). Surfacing a crossing-kind
    // overlap only in the confirm would read as the FWD path skipping the check (emulator QA —
    // the FWD seed happened to land on a taper, the AFT one on a liner).
    val startError = addDialogNegativeStartError(startEnteredOrNull) ?: when {
        fwdStartPastAft -> ADD_PAST_AFT_END_MSG
        physStartMm >= 0f && lengthMm > 0f ->
            startOverlapErrorMm(spec, "", ComponentKind.LINER, lengthMm, physStartMm)
        else -> null
    }
    val overlapNote = if (startError == null) proposedSpanOverlapMessage(spec, physStartMm, lengthMm) else null

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

    DiscardGuardedDialog(
        discardTitle = "Discard this liner?",
        onDismiss = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Liner") { converterOpen = true }
            } else {
                Text("Add Liner")
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
                    label = "Start from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})",
                    initial = (if (isFwd) startFwd else startAft).text,
                    errorText = startError,
                    supportingText = overlapNote,
                    highlight = overlapNote != null,
                ) { (if (isFwd) startFwd else startAft).onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length.text, requirePositive = true) { length.onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Outer Ø (${abbr(unit)})", od.text, requirePositive = true) { od.onText(it) }

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
            val ok = linerAddEnabled(physStartMm, lengthMm, odMm, startError) && optionalEntriesReadable(
                if (!linerShouldersEnabled) emptyList() else listOf(
                    shAftLen.takeIf { shAftOn }, shAftOd.takeIf { shAftOn },
                    shFwdLen.takeIf { shFwdOn }, shFwdOd.takeIf { shFwdOn },
                )
            )
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
                val warnings = collectAddWarnings(spec, physStartMm, lengthMm, unit)
                if (warnings.isEmpty()) action() else { warningLines = warnings; warningAction = action }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Coupler Bolt Slot — one axial row of radial holes.
 * Hole style (Seam | Cross-drilled), Start, Hole Ø, Count, Spacing, Through/Blind (+ Depth).
 * Reference defaults FWD. The style is an add-time choice (it decides what the row draws
 * as), so it sits under the parity rule and is mirrored on the card.
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
        holeStyle: BoltHoleStyle, clocking: BoltHoleClocking,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    // Default reference is FWD per spec.
    var isFwd by remember { mutableStateOf(true) }
    var through by remember { mutableStateOf(true) }
    var holeStyle by remember { mutableStateOf(BoltHoleStyle.SEAM) }
    // 90° is the norm for a coupling bolt (in line, it would pass through the key).
    var clocking by remember { mutableStateOf(BoltHoleClocking.DEG_90) }
    val isCross = holeStyle == BoltHoleStyle.CROSS

    // Both position seeds put the row ON the shaft, so the dialog opens valid with Add live
    // (see couplerBoltSlotSeedMm). The seeds use the same hole Ø / count / spacing the fields open with.
    val seedHoleDiaMm = max(1f, initialHoleDiaMm)
    val seedCount = initialCount.coerceAtLeast(1)
    fun seed(fwd: Boolean): Float = couplerBoltSlotSeedMm(
        oalMm = spec.overallLengthMm,
        isFwd = fwd,
        suggestedAftStartMm = initialStartMm,
        holeDiaMm = seedHoleDiaMm,
        count = seedCount,
        spacingMm = initialSpacingMm,
        fromFaceMm = AddDefaultsConfig.SLOT_FROM_END_IN * 25.4f,
    )
    val startAft = rememberSeededMm(seed(fwd = false), unit)
    val startFwd = rememberSeededMm(seed(fwd = true), unit)
    val holeDia = rememberSeededMm(seedHoleDiaMm, unit)
    var countText by remember(initialCount) { mutableStateOf(seedCount.toString()) }
    val spacing = rememberSeededMm(initialSpacingMm, unit)
    val depth = rememberSeededMm(initialDepthMm, unit)

    val holeDiaMm = holeDia.mm(unit) ?: -1f
    // Never coerced: a typed 0 is refused and named (boltSlotCountError), not stored as 1.
    val count = boltSlotCountOrNull(countText) ?: 0
    val spacingMm = spacing.mm(unit) ?: 0f
    val depthMm = depth.mm(unit) ?: -1f
    val startEntered = (if (isFwd) startFwd else startAft).mm(unit) ?: -1f

    // Physical position (from AFT) of the aft-most cutout center. When measuring from FWD, the
    // entered value locates the fwd-most cutout; the row then extends aft.
    val physStartMm = couplerBoltSlotPhysStartMm(spec.overallLengthMm, isFwd, startEntered, count, spacingMm)

    // Bounds check: every cutout (center ± hole radius) must lie on the shaft. Mirrors
    // CouplerBoltSlot.isValid(); only enforced once an OAL exists to check against.
    val boundsError: String? =
        couplerBoltSlotBoundsError(spec.overallLengthMm, physStartMm, holeDiaMm, count, spacingMm)

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    DiscardGuardedDialog(
        discardTitle = "Discard this coupler bolt slot?",
        onDismiss = onCancel,
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
                    Text("Hole:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DirectionChip("Seam", selected = !isCross) { holeStyle = BoltHoleStyle.SEAM }
                    DirectionChip("Cross-drilled", selected = isCross) { holeStyle = BoltHoleStyle.CROSS }
                }
                if (isCross) {
                    val is90 = clocking == BoltHoleClocking.DEG_90
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("From keyway:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DirectionChip("90°", selected = is90) { clocking = BoltHoleClocking.DEG_90 }
                        DirectionChip("In line", selected = !is90) { clocking = BoltHoleClocking.IN_LINE }
                    }
                }
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
                    label = slotStartFieldLabel(isFwd = isFwd, isCross = isCross, unit = unit),
                    initial = (if (isFwd) startFwd else startAft).text,
                    errorText = boundsError,
                ) { (if (isFwd) startFwd else startAft).onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Hole Ø (${abbr(unit)})", holeDia.text, requirePositive = true) { holeDia.onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField(
                    "Count", countText,
                    errorText = boltSlotCountError(countText),
                    parseValid = ::isUnitlessNumberText,
                    fractionKey = false,
                ) { countText = it }
                if (count > 1 || countText.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("Spacing (${abbr(unit)})", spacing.text, requirePositive = true) { spacing.onText(it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Through hole", Modifier.weight(1f))
                    DialogSwitch(checked = through, onCheckedChange = { through = it })
                }
                if (!through) {
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("Depth (${abbr(unit)})", depth.text, requirePositive = true) { depth.onText(it) }
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
                    through, if (through) 0f else depthMm, ref, holeStyle, clocking,
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/**
 * Label of the coupler bolt row's position field — ONE source for the Add dialog and the
 * card, so the two surfaces name the same measurement the same way. The value is always the
 * distance from the chosen face to the nearest hole CENTER; a cross-drilled hole says so
 * outright because that is how the shop quotes it ("from the end of the shaft to the center
 * of the hole"), while a seam row keeps its "first slot" wording.
 */
internal fun slotStartFieldLabel(isFwd: Boolean, isCross: Boolean, unit: UnitSystem): String {
    val face = if (isFwd) "FWD" else "AFT"
    val what = if (isCross) "Hole center" else "First slot"
    return "$what from $face (${abbr(unit)})"
}

/* ────────────────────────────────────────────────────────────────────────────
 * Thread — Start, Length, Major Ø, TPI (always TPI; caller converts to pitch mm)
 * ──────────────────────────────────────────────────────────────────────────── */

// CONTRACT (docs/contracts/AddComponentDialogs.md): dialog/card parity.
// When countInOal=false, show "Thread end: AFT | FWD" chips and hide the Start field —
// mirroring ComponentCarousel.kt ResolvedThread !includeInOal block.
// Do not remove the AFT/FWD branch; isAftEnd must be passed through to addThreadAt().
// The "Thread relief" switch + Relief length / Relief Ø + "Relief at" chips mirror the thread
// card's ThreadReliefCardSection; the relief must be passed through to addThreadAt() too.
@Composable
fun AddThreadDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
    initialStartMm: Float,
    initialLengthMm: Float,
    initialMajorDiaMm: Float,
    initialPitchMm: Float,
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the title-row calculator icon. */
    dialogUnitConverterEnabled: Boolean = false,
    onSubmit: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean,
               isAftEnd: Boolean, metricDesignation: String?,
               reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)

    val effectiveStartMm = if (initialStartMm >= 0f) initialStartMm else d.startMm
    val effectiveLengthMm = if (initialLengthMm > 0f) initialLengthMm else 0f
    val effectiveMajorMm = if (initialMajorDiaMm > 0f) initialMajorDiaMm else d.lastDiaMm
    val initialTpi = pitchMmToTpi(initialPitchMm).takeIf { it > 0f } ?: 4f

    fun formatTpi(v: Float): String = "%1.3f".format(Locale.US, v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    val start = rememberSaveableSeededMm(effectiveStartMm, unit)
    val length = rememberSaveableSeededMm(effectiveLengthMm, unit)
    val major = rememberSaveableSeededMm(max(1f, effectiveMajorMm), unit)
    var tpiText by rememberSaveable(initialTpi) { mutableStateOf(formatTpi(initialTpi)) }
    // Saveable: a configuration change (a system font-scale change recreates the activity) would
    // otherwise reset every switch and chip while the dialog itself stays open (on-device report).
    var countInOal by rememberSaveable { mutableStateOf(true) }
    var isAftEnd by rememberSaveable { mutableStateOf(true) }

    // Thread relief: local switch (default OFF) revealing the two fields and the end chips. While
    // it is off the dialog submits relief 0/0 whatever the hidden fields hold. The end FOLLOWS the
    // thread's position ([suggestedThreadReliefAtAft], read live) until the end chips are tapped:
    // seeding it once left it on the shaft-end side after "Thread end" or "Include thread in OAL"
    // changed (on-device report). A tapped chip is the user's choice and is kept.
    var reliefOn by rememberSaveable { mutableStateOf(false) }
    val reliefLen = rememberSaveable(saver = SeededMmField.Saver) { SeededMmField.empty() }
    val reliefDia = rememberSaveable(saver = SeededMmField.Saver) { SeededMmField.empty() }
    var reliefEndChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Imperial (TPI, entered in the session unit) vs Metric (a self-declaring M-designation
    // that always names its own mm values — see `ThreadDesignation`).
    var metricMode by rememberSaveable { mutableStateOf(false) }
    var designationText by rememberSaveable { mutableStateOf("") }
    val parsedDesignation = if (metricMode) ThreadDesignation.parse(designationText) else null

    val startMm = start.mm(unit) ?: -1f
    val lengthMm = length.mm(unit) ?: -1f
    val majorMmImperial = major.mm(unit) ?: -1f
    val majorMm = if (metricMode) (parsedDesignation?.majorDiaMm ?: -1f) else majorMmImperial
    val tpi = parseFractionOrDecimal(tpiText)?.toFloat() ?: -1f   // allow e.g., "20", "10", "32"
    // Pitch omitted from a coarse designation (e.g. "M20") reads as 0 — not "unset" — so a
    // metric thread never blocks on a pitch the designation deliberately left out.
    val pitchMm = if (metricMode) (parsedDesignation?.pitchMm ?: 0f) else tpiToPitchMm(tpi)
    // Metric gate: a real tpi-shaped value only when the designation parses, so the shared
    // gate's `tpi > 0f` check reads the same pass/fail without a second gate function.
    val tpiGate = if (metricMode) (if (parsedDesignation != null) 1f else -1f) else tpi
    val reliefAtAft = reliefEndChoice ?: suggestedThreadReliefAtAft(
        startFromAftMm = startMm, lengthMm = lengthMm, excludeFromOAL = !countInOal,
        isAftEnd = isAftEnd, overallLengthMm = spec.overallLengthMm,
    )

    val startError = if (!countInOal) null
                     else if (startMm < 0f) addDialogNegativeStartError(start.mm(unit))
                     else if (startMm >= 0f && lengthMm > 0f)
                         startOverlapErrorMm(spec, "", ComponentKind.THREAD, lengthMm, startMm)
                     else null
    // A taper or liner the thread would run into: named before Add, confirmed by "Add Anyway?"
    // (the Liner and Taper dialogs' posture). An excluded thread never collides.
    val overlapNote = if (!countInOal || startError != null) null
                      else proposedSpanOverlapMessage(spec, startMm, lengthMm)

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

    DiscardGuardedDialog(
        discardTitle = "Discard this thread?",
        onDismiss = onCancel,
        // Saveable like the dialog's own fields, so a configuration change keeps the guard.
        edits = rememberSaveableDialogEdits(),
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Thread") { converterOpen = true }
            } else {
                Text("Add Thread")
            }
        },
        text = {
            // Scrollable like every other add dialog: with Thread relief on, its three rows push
            // "Include thread in OAL" and the buttons below the dialog's bounds, and under the
            // keyboard the bottom fields were unreachable (on-device report).
            Column(Modifier.padding(top = 4.dp).verticalScroll(rememberScrollState())) {
                if (countInOal) {
                    CommitNumField(
                        "Start (${abbr(unit)})", start.text,
                        errorText = startError,
                        supportingText = overlapNote,
                        highlight = overlapNote != null,
                    ) { start.onText(it) }
                    Spacer(Modifier.height(8.dp))
                } else {
                    ChipFlowRow(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Thread end:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DirectionChip("AFT", selected =  isAftEnd) { isAftEnd = true  }
                        DirectionChip("FWD", selected = !isAftEnd) { isAftEnd = false }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                ChipFlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    DirectionChip("Imperial (TPI)", selected = !metricMode) { metricMode = false }
                    DirectionChip("Metric (mm)", selected = metricMode) { metricMode = true }
                }
                if (metricMode) {
                    CommitNumField(
                        "Thread designation",
                        designationText,
                        errorText = if (designationText.isNotBlank() && parsedDesignation == null)
                            ThreadDesignation.INVALID_MESSAGE else null,
                        supportingText = if (designationText.isBlank()) "e.g. M20×2.5" else null,
                        keyboardType = KeyboardType.Ascii,
                        parseValid = null,
                    ) { designationText = it }
                    Spacer(Modifier.height(8.dp))
                } else {
                    CommitNumField("Major Ø (${abbr(unit)})", major.text, requirePositive = true) { major.onText(it) }
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("TPI", tpiText, parseValid = ::isUnitlessNumberText, requirePositive = true) { tpiText = it }
                    Spacer(Modifier.height(8.dp))
                }
                CommitNumField("Length (${abbr(unit)})", length.text, requirePositive = true) { length.onText(it) }
                Spacer(Modifier.height(4.dp))
                // Thread relief — mirrors the thread card's `ThreadReliefCardSection` (parity rule).
                ThreadReliefSwitchRow(checked = reliefOn, testTag = "add_thread_relief") { on ->
                    reliefOn = on
                    if (on) {
                        val seed = threadReliefForSwitch(
                            on = true,
                            currentLenMm = reliefLen.mm(unit) ?: 0f,
                            currentDiaMm = reliefDia.mm(unit) ?: 0f,
                            rememberedLenMm = 0f,
                            rememberedDiaMm = 0f,
                            majorDiaMm = majorMm,
                            pitchMm = pitchMm,
                        )
                        if (reliefLen.text.isBlank()) reliefLen.reseed(seed.lenMm, toDisplayString(seed.lenMm, unit))
                        if (reliefDia.text.isBlank() && seed.diaMm > 0f) reliefDia.reseed(seed.diaMm, toDisplayString(seed.diaMm, unit))
                    }
                }
                if (reliefOn) {
                    CommitNumField("Relief length (${abbr(unit)})", reliefLen.text) { reliefLen.onText(it) }
                    Spacer(Modifier.height(8.dp))
                    CommitNumField("Relief Ø (${abbr(unit)})", reliefDia.text) { reliefDia.onText(it) }
                    Spacer(Modifier.height(8.dp))
                    ThreadReliefEndChips(
                        atAft = reliefAtAft,
                        testTag = "add_thread_relief_end",
                        chip = { label, selected, modifier, onClick ->
                            DirectionChip(label, selected, modifier, onClick)
                        },
                    ) { reliefEndChoice = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Include thread in OAL")
                    DialogSwitch(
                        checked = countInOal,
                        onCheckedChange = { countInOal = it }
                    )
                }
            }
        },
        confirmButton = {
            val ok = threadAddEnabled(countInOal, startMm, lengthMm, majorMm, tpiGate, startError) &&
                optionalEntriesReadable(threadReliefEntryTexts(reliefOn, reliefLen.text, reliefDia.text))
            Button(enabled = ok, onClick = {
                val excludeFromOAL = !countInOal
                val designation = if (metricMode) parsedDesignation?.format() else null
                val relief = threadReliefSubmitValues(
                    reliefOn,
                    reliefLen.mm(unit),
                    reliefDia.mm(unit),
                )
                val action = {
                    onSubmit(
                        startMm, lengthMm, majorMm, pitchMm, excludeFromOAL, isAftEnd, designation,
                        relief.lenMm, relief.diaMm, reliefAtAft,
                    )
                }
                // Excluded threads don't live on the shaft span, so skip collision for them.
                val warnings = if (excludeFromOAL) emptyList()
                               else collectAddWarnings(spec, startMm, lengthMm, unit)
                if (warnings.isEmpty()) action() else { warningLines = warnings; warningAction = action }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Taper — Start, Length, S.E.T., L.E.T., Rate (a ratio like "1:12", inches per foot like
 * "3/4\"/ft", or a decimal; a bare fraction is ambiguous and blocked — see `parseTaperRateText`)
 * Caller computes the missing diameter from the rate to keep logic centralized.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun AddTaperDialog(
    unit: UnitSystem,
    spec: ShaftSpec,
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
               keyways90Apart: Boolean, keyways90Cw: Boolean, keywayUnit: UnitSystem?,
               /** The Rate mode chips' choice (`true` = Manual), stored on the taper. */
               rateManual: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val d = rememberAddDialogDefaults(spec)

    val effectiveLengthMm = initialLengthMm ?: 100f
    // Both seeds from the ONE seeder, each from its own reference end (see AddLinerDialog).
    val defaultAftStartMm = initialStartMm ?: addStartSeed(spec, effectiveLengthMm).startMm
    val defaultFwdStartMm = remember(spec, effectiveLengthMm) { addSeedFromFwdMm(spec, effectiveLengthMm) }

    // True = measuring from the FWD shaft face; false = from the AFT face.
    var isFwd by remember { mutableStateOf(false) }

    // Keep independent start strings per direction so toggling doesn't clobber user input.
    val startAft = rememberSeededMm(defaultAftStartMm, unit)
    val startFwd = rememberSeededMm(defaultFwdStartMm, unit)

    val length  = rememberSeededMm(effectiveLengthMm, unit)
    val setDia  = rememberSeededMm(max(1f, d.lastDiaMm), unit)
    var letText by remember(unit) { mutableStateOf("") }  // allow deriving via rate
    var rateText by remember { mutableStateOf("1:12") }   // manual-mode text; shop default
    var autoRate by remember { mutableStateOf(true) }

    // Keyway — gated behind the shared Keyway checkbox (fields hidden until turned on), the
    // same gate the taper card carries. While it is off the keyway submits zeros; typed text in
    // the hidden fields is held until submit but never counts.
    var kwEnabled by remember { mutableStateOf(false) }
    var kwWidth   by remember { mutableStateOf("") }
    var kwDepth   by remember { mutableStateOf("") }
    var kwLength  by remember { mutableStateOf("") }
    // Captured keyway: local toggle; `kwOffset` holds the inset text it reveals.
    var kwCaptured by remember { mutableStateOf(false) }
    var kwOffset  by remember { mutableStateOf("") }
    var kwSpooned by remember { mutableStateOf(false) }
    // 180°/90° clocking are mutually exclusive; enforced locally here (mirrors the
    // ViewModel's clearing behavior) so the two switches never show both checked
    // before the value round-trips through onSubmit.
    var clock180  by remember { mutableStateOf(spec.keyways180Apart) }
    var clock90   by remember { mutableStateOf(spec.keyways90Apart) }
    var cw90      by remember { mutableStateOf(spec.keyways90Cw) }

    val startEnteredOrNull = (if (isFwd) startFwd else startAft).mm(unit)
    val startEntered = startEnteredOrNull ?: -1f
    val lengthMm = length.mm(unit) ?: -1f
    val setMm = setDia.mm(unit) ?: -1f   // -1 means "not provided"
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

    // Resolve physical AFT-origin start from the entered value — a FWD entry past the AFT face
    // is refused, not clamped (addDialogPhysStartMm).
    val physStartMm = addDialogPhysStartMm(isFwd, startEntered, lengthMm, spec.overallLengthMm)
    val startError = addDialogNegativeStartError(startEnteredOrNull)
        ?: if (addDialogFwdStartPastAft(isFwd, startEntered, lengthMm, spec.overallLengthMm))
            ADD_PAST_AFT_END_MSG else null
    // Tapers do not block on an overlap (no card refuses one either); the line names it before
    // Add and "Add Anyway?" confirms it — the same from either reference end.
    val overlapNote = if (startError == null) proposedSpanOverlapMessage(spec, physStartMm, lengthMm) else null

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
    // The inset text only counts while "Captured keyway" is on; switching it off holds the
    // text until submit but commits an open keyway (inset 0).
    val keywayOffsetMm = if (kwEnabled && kwCaptured) toMmOrNull(kwOffset, kwUnit) ?: 0f else 0f
    val isCaptured = kwCaptured && keywayOffsetMm > 0f
    // Same condition as the carousel card's switch: it appears once the shaft will
    // have ≥ 2 keyways (≥ 1 existing plus the one being defined here).
    val kwDefined = kwEnabled &&
        (toMmOrNull(kwWidth, kwUnit) ?: 0f) > 0f &&
        (toMmOrNull(kwDepth, kwUnit) ?: 0f) > 0f &&
        (toMmOrNull(kwLength, kwUnit) ?: 0f) > 0f
    val showClockingToggle = kwEnabled && spec.keywayCount() >= 1 && kwDefined

    val scroll = rememberScrollState()

    // Unit converter launcher — a pure read-only tool, see addDialogTitleWithConverter's KDoc.
    var converterOpen by remember { mutableStateOf(false) }
    if (converterOpen) {
        UnitConverterDialog(defaultUnit = unit, onDismiss = { converterOpen = false })
    }

    DiscardGuardedDialog(
        discardTitle = "Discard this taper?",
        onDismiss = onCancel,
        title = {
            if (dialogUnitConverterEnabled) {
                addDialogTitleWithConverter("Add Taper") { converterOpen = true }
            } else {
                Text("Add Taper")
            }
        },
        text = {
            Column(Modifier.padding(top = 4.dp).verticalScroll(scroll)) {
                // Measure-from selector — the card's wording, so the same control reads the
                // same on both surfaces.
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
                    "Start from ${if (isFwd) "FWD" else "AFT"} (${abbr(unit)})",
                    (if (isFwd) startFwd else startAft).text,
                    errorText = startError,
                    supportingText = overlapNote,
                    highlight = overlapNote != null,
                ) { (if (isFwd) startFwd else startAft).onText(it) }
                Spacer(Modifier.height(8.dp))
                CommitNumField("Length (${abbr(unit)})", length.text, requirePositive = true) { length.onText(it) }
                Spacer(Modifier.height(8.dp))
                // SET is always Small End; LET is always Large End, whichever end the start
                // was measured from. The model stores AFT→FWD diameters and SET faces the
                // nearer shaft end, so the submit handler orders them by the taper's physical
                // half — not by this dialog's direction chip.
                CommitNumField("S.E.T. Ø (${abbr(unit)})", setDia.text) { setDia.onText(it) }
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
                    "Rate (1:12, 3/4\"/ft, or decimal)",
                    rateDisplayText,
                    keyboardType = KeyboardType.Ascii,
                    enabled = !autoRate,
                    supportingText = rateIssueText,
                    highlight = rateIssueText != null,
                    // The taper card's rate field takes the same filter: one acceptance rule.
                    inputFilter = ::filterTaperRateInput,
                    // The rate explains its own text (rateIssueText), ambiguity included.
                    parseValid = null,
                ) { rateText = it }
                Spacer(Modifier.height(12.dp))
                // The shared gate row the cards carry. No confirm on untick: nothing is stored
                // until submit, and the hidden fields submit zeros while the gate is off.
                KeywayGateRow(checked = kwEnabled, testTag = "add_taper_kw_gate") { kwEnabled = it }
                if (kwEnabled) {
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
                    Spacer(Modifier.height(4.dp))
                    CapturedKeywayToggleRow(
                        checked = kwCaptured,
                        testTag = "add_taper_kw_captured",
                    ) { on ->
                        kwCaptured = on
                        if (on && kwOffset.isBlank()) kwOffset = dispKw(defaultKeywayInsetMm(), kwUnit)
                    }
                    if (kwCaptured) {
                        CommitNumField("KW Inset from SET (${abbr(kwUnit)})", kwOffset) { kwOffset = it }
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isCaptured) "Keyway spooned (N/A — captured)" else "Keyway spooned",
                            modifier = Modifier.weight(1f),
                            color = if (isCaptured) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        DialogSwitch(
                            checked = kwSpooned && !isCaptured,
                            enabled = !isCaptured,
                            onCheckedChange = { if (!isCaptured) kwSpooned = it }
                        )
                    }
                    if (showClockingToggle) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Keyways 180° apart", modifier = Modifier.weight(1f))
                            DialogSwitch(checked = clock180, onCheckedChange = { checked ->
                                clock180 = checked
                                if (checked) clock90 = false
                            })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Keyways 90° apart", modifier = Modifier.weight(1f))
                            DialogSwitch(checked = clock90, onCheckedChange = { checked ->
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
            ) && optionalEntriesReadable(
                listOf(setDia.text, letText) +
                    keywayEntryTexts(kwEnabled, kwCaptured, kwWidth, kwDepth, kwLength, kwOffset)
            )
            Button(
                enabled = ok,
                onClick = {
                    // Which face SET lands on follows the taper's PHYSICAL half — the chip
                    // above only says which end the start was measured from, and a taper
                    // measured from one end can be placed in the other half. The half is
                    // judged against the shaft's authored OAL.
                    val addSide = classifyTaperSideByMidpoint(
                        startFromAftMm = physStartMm,
                        lengthMm = lengthMm,
                        overallLengthMm = spec.overallLengthMm,
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
                    // Keyway values only count while the Keyway checkbox is on.
                    val kwW = if (kwEnabled) toMmOrNull(kwWidth,  kwUnit) ?: 0f else 0f
                    val kwD = if (kwEnabled) toMmOrNull(kwDepth,  kwUnit) ?: 0f else 0f
                    val kwL = if (kwEnabled) toMmOrNull(kwLength, kwUnit) ?: 0f else 0f
                    val kwO = keywayOffsetMm
                    val submitRateText = if (autoRate) computedRateText.orEmpty() else rateText
                    val action = {
                        onSubmit(physStartMm, lengthMm, startDia, endDia, submitRateText,
                                 reference,
                                 kwW, kwD, kwL, kwO, kwEnabled && kwSpooned && !isCaptured,
                                 if (showClockingToggle) clock180 else spec.keyways180Apart,
                                 if (showClockingToggle) clock90 else spec.keyways90Apart,
                                 if (showClockingToggle) cw90 else spec.keyways90Cw,
                                 if (kwDefined) kwUnitOverride else null,
                                 !autoRate)
                    }
                    val warnings = collectAddWarnings(spec, physStartMm, lengthMm, unit)
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
private fun DirectionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Moving to another option is an edit for the discard guard; re-tapping the selected one is not.
    val edits = LocalDialogEdits.current
    OutlinedButton(
        onClick = {
            if (!selected) edits?.mark()
            onClick()
        },
        modifier = modifier,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(0.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
            contentColor   = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        ChipLabel(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The "Captured keyway" switch row shared by `AddBodyDialog` and `AddTaperDialog`, styled like
 * the carousel cards' row (whole-row toggle, 48 dp, the switch itself not separately clickable).
 * A captured keyway is inset from the S.E.T. / referenced face, with mill arcs at both ends;
 * the host dialog reveals the inset field while it is on.
 */
@Composable
private fun CapturedKeywayToggleRow(
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val edits = LocalDialogEdits.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag(testTag)
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = { on ->
                    edits?.mark()
                    onCheckedChange(on)
                },
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Captured keyway", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Field composable that keeps local text; caller decides when to consume.
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * A dialog field's text box. [parseValid] says whether the text reads as this field's kind of
 * value — a length or diameter (unit suffix read) by default, the caller's own rule for a
 * unitless field, null for a field that explains its own text ([errorText] / [supportingText]).
 * Non-blank text that fails it shows [unreadableEntryError] under the field, so a disabled Add
 * button always has a visible reason; the caller's own [errorText] takes the line first.
 * [requirePositive] marks a field the Add gate needs > 0 (a length, a diameter, a count's pitch):
 * a typed `0` then shows "Must be > 0" — the card's own wording for the same refusal.
 */
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
    inputFilter: ((String) -> String)? = null,
    parseValid: ((String) -> Boolean)? = ::isLengthText,
    requirePositive: Boolean = false,
    /** The in-field "/" key (inch documents, while focused); off for whole-number fields. */
    fractionKey: Boolean = keyboardType == KeyboardType.Decimal,
    onCommit: (String) -> Unit
) {
    // text is the live value; initial only resets it when the parent externally
    // changes it (e.g., unit toggle). Using LaunchedEffect instead of remember(initial)
    // avoids a cursor-to-end jump on every keystroke echo-back.
    var text by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) {
        if (text != initial) text = initial
    }
    val shownError = errorText
        ?: parseValid?.let { unreadableEntryError(text, it) }
        ?: if (requirePositive) nonPositiveEntryError(text) else null
    // A keystroke is an edit for the discard guard the moment it lands, before any commit.
    val edits = LocalDialogEdits.current
    val cursor = rememberFieldCursor()
    fun applyEdit(raw: TextFieldValue) {
        val newText = inputFilter?.invoke(raw.text) ?: raw.text
        if (newText != text) edits?.mark()
        cursor.track(if (newText == raw.text) raw else raw.copy(text = newText))
        text = newText
        onCommit(newText)   // commit on every keystroke so Add always has the current value
    }
    OutlinedTextField(
        value = cursor.valueFor(text),
        onValueChange = { raw -> applyEdit(raw) },
        trailingIcon = if (fractionKey && enabled && cursor.focused && LocalFractionKeyEnabled.current) {
            { FractionKey(onClick = { applyEdit(cursor.valueFor(text).insertAtCursor("/")) }) }
        } else null,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        isError = highlight || shownError != null,
        supportingText = if (shownError != null || supportingText != null) {
            {
                Text(
                    shownError ?: supportingText.orEmpty(),
                    color = if (highlight || shownError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                cursor.focused = f.isFocused
                if (!f.isFocused) onCommit(text)
            }
    )
}

/** The error line a numeric field shows under text that does not read as a number. */
internal const val NOT_A_NUMBER_ERROR = "Not a number"

/**
 * The error a numeric dialog field shows for [text]: [NOT_A_NUMBER_ERROR] when it is non-blank
 * and fails [parseValid], null otherwise (blank is "not entered yet", which the dialog's own
 * gate handles). Shared by the Add dialogs and the new-shaft "Shaft diameter" prompt, so a
 * disabled confirm button always has a visible reason.
 */
internal fun unreadableEntryError(text: String, parseValid: (String) -> Boolean): String? =
    if (text.isNotBlank() && !parseValid(text)) NOT_A_NUMBER_ERROR else null

/**
 * "Must be > 0" for text that reads as a number at or below zero (a length or Ø, suffix read,
 * or a unitless value such as TPI); null for anything else — blank and unreadable text have
 * their own handling. The dialogs' wording for the refusal the cards make ([MUST_BE_POSITIVE_MSG]).
 */
internal fun nonPositiveEntryError(text: String): String? {
    val v = parseLengthMmOrNull(text, UnitSystem.MILLIMETERS) ?: parseFractionOrDecimal(text) ?: return null
    return if (v <= 0.0) MUST_BE_POSITIVE_MSG else null
}
