package com.android.shaftschematic.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasAnyKeywayValue
import com.android.shaftschematic.ui.input.taperPhysStartForNewLength
import com.android.shaftschematic.ui.input.taperSetLetMapping
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.util.positiveLengthErrorMm
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.ui.util.taperWarningMessages
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.manualTaperRateBlockingMessage
import com.android.shaftschematic.util.manualTaperRateWarning
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.toMmOrNull
import kotlin.math.max

// ─────────────────────────────────────────────────────────────────────────────
// TaperPagerCard — the `ResolvedTaper` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a taper: the AFT/FWD reference chips, Start/Length, the SET/LET
 * diameters, the Auto/Manual rate pair, and the keyway section. That section is gated by the
 * shared "Keyway" checkbox ([KeywayGateRow]), seeded open whenever any of W/D/L is typed;
 * unticking it with values present confirms through [RemoveKeywayConfirmDialog] before clearing
 * them. Inside the gate: the keyway's own unit chip, W × D, the standard-size picker, KW L, the
 * "Captured keyway" toggle with its "KW Inset from SET" field shown only while captured,
 * "Keyway spooned", and the clocking section.
 *
 * Rate mode is user-owned state seeded once per taper — it must not be re-derived from the
 * stored text, which would discard an explicit Auto/Manual choice. Every control that changes
 * geometry, position, or a value is mirrored in `AddTaperDialog` by the add-dialog-parity
 * invariant; "Show name on drawing" and the "Prints in: in | mm" chip at the card's foot are the
 * documented carve-outs.
 *
 * [f1] is supplied by [ComponentPagerCard] because every card's debug line uses it.
 */
@Composable
internal fun TaperPagerCard(
    component: ResolvedTaper,
    explicitIndex: Int?,
    spec: ShaftSpec,
    unit: UnitSystem,
    physicalIndex: Int,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    componentTitlesDefault: Boolean = true,
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
    taperTitleById: Map<String, String>,
    f1: (Float) -> String,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperShowLabel: (Int, Boolean) -> Unit,
    onUpdateTaperShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    onRemoveTaper: (String) -> Unit,
    collidingComponentIds: Set<String>,
    perComponentUnitsEnabled: Boolean,
    unitOverrides: Map<String, UnitSystem>,
    onSetComponentUnit: (String, UnitSystem?) -> Unit,
    onSetKeywayUnit: (String, UnitSystem?) -> Unit,
) {
    val idx    = explicitIndex ?: return
    val t      = spec.tapers.getOrNull(idx) ?: return
    val endMap = taperSetLetMapping(t, spec.overallLengthMm)
    val isFwdRef = t.authoredReference == LinerAuthoredReference.FWD
    val authoredStartMm = if (isFwdRef) {
        spec.overallLengthMm - t.startFromAftMm - t.lengthMm
    } else {
        t.startFromAftMm
    }
    val computedTaperTitle = taperTitleById[t.id] ?: "Taper"
    ComponentCard(
        title = computedTaperTitle,
        titleContent = {
            EditableCardTitle(
                componentId = t.id,
                title = computedTaperTitle,
                label = t.label,
                onCommitLabel = { onUpdateTaperLabel(idx, it) },
            )
        },
        debugText = if (showComponentDebugLabels) "id=${t.id} • startMm=${f1(t.startFromAftMm)} • endMm=${f1(t.startFromAftMm + t.lengthMm)}" else null,
        errorMessage = if (t.id in collidingComponentIds) "Overlaps another component" else null,
        warningMessage = taperWarningMessages(spec, t).joinToString("; ").ifEmpty { null },
        componentId = t.id, componentKind = ComponentKind.TAPER,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = {
            Log.d("ShaftUI", "Taper delete clicked: id=${t.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
            onRemoveTaper(t.id)
        }
    ) {
        val computedRateText = remember(t.lengthMm, t.startDiaMm, t.endDiaMm) {
            autoTaperRateText(
                lengthMm = t.lengthMm,
                setDiaMm = t.startDiaMm,
                letDiaMm = t.endDiaMm,
                exactDecimals = 3
            )
        }
        // Mode is user-owned state, seeded once per taper from whether the
        // stored text already matches the computed auto text. It must not be
        // re-derived on text/geometry changes — that silently discards an
        // explicit Auto/Manual choice.
        var autoRate by rememberSaveable(t.id) {
            mutableStateOf(t.taperRateText.isBlank() || t.taperRateText == computedRateText)
        }
        val hasExactlyOneEnd = (t.startDiaMm > 0f).xor(t.endDiaMm > 0f)
        val autoRateIssue = if (autoRate && hasExactlyOneEnd) {
            "Auto needs Length + SET + LET. Switch to Manual to derive the missing end"
        } else null
        val manualRateIssue = if (!autoRate) {
            remember(t.taperRateText, t.lengthMm, t.startDiaMm, t.endDiaMm) {
                manualTaperRateBlockingMessage(t.taperRateText, t.lengthMm, t.startDiaMm, t.endDiaMm)
                    ?: manualTaperRateWarning(t.taperRateText, t.lengthMm, t.startDiaMm, t.endDiaMm)
            }
        } else null
        val rateIssue = if (autoRate) autoRateIssue else manualRateIssue
        // In Auto mode, geometry edits carry the recomputed rate with them;
        // the model is only ever written from an explicit user commit.
        val nextRateText: (Float, Float, Float) -> String = { lengthMm, startDiaMm, endDiaMm ->
            if (autoRate) {
                autoTaperRateText(
                    lengthMm = lengthMm,
                    setDiaMm = startDiaMm,
                    letDiaMm = endDiaMm,
                    exactDecimals = 3
                ) ?: t.taperRateText
            } else {
                t.taperRateText
            }
        }

        val selectedColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.Black,
            selectedLabelColor = Color.White,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface
        )

        // AFT / FWD reference toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Measure From:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(selected = !isFwdRef, onClick = { onUpdateTaperReference(idx, LinerAuthoredReference.AFT) },
                label = { Text("AFT") }, colors = selectedColors,
                border = if (!isFwdRef) BorderStroke(1.dp, Color.Black) else null)
            FilterChip(selected = isFwdRef, onClick = { onUpdateTaperReference(idx, LinerAuthoredReference.FWD) },
                label = { Text("FWD") }, colors = selectedColors,
                border = if (isFwdRef) BorderStroke(1.dp, Color.Black) else null)
        }

        CommitNum(
            label = "Start from ${if (isFwdRef) "FWD" else "AFT"} (${abbr(unit)})",
            initialDisplay = disp(authoredStartMm, unit),
            validator = { raw ->
                val authoredMm = toMmOrNull(raw, unit) ?: return@CommitNum "Enter a number"
                val physStart = if (isFwdRef) spec.overallLengthMm - authoredMm - t.lengthMm else authoredMm
                startOverlapErrorMm(spec, t.id, ComponentKind.TAPER, t.lengthMm, physStart)
            }
        ) { s ->
            val authoredMm = toMmOrNull(s, unit) ?: return@CommitNum
            val physStart = if (isFwdRef) spec.overallLengthMm - authoredMm - t.lengthMm else authoredMm
            onUpdateTaper(idx, physStart, t.lengthMm, t.startDiaMm, t.endDiaMm, nextRateText(t.lengthMm, t.startDiaMm, t.endDiaMm))
        }
        CommitNum(
            "Length (${abbr(unit)})", disp(t.lengthMm, unit),
            validator = { raw -> positiveLengthErrorMm(toMmOrNull(raw, unit)) },
        ) { s ->
            val newLen = toMmOrNull(s, unit) ?: return@CommitNum
            val physStart = taperPhysStartForNewLength(t, newLen, spec.overallLengthMm)
            onUpdateTaper(idx, physStart, newLen, t.startDiaMm, t.endDiaMm, nextRateText(newLen, t.startDiaMm, t.endDiaMm))
        }
        CommitNum("${endMap.leftCode} Ø (${abbr(unit)})", disp(t.startDiaMm, unit)) { s ->
            toMmOrNull(s, unit)?.let {
                onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, it, t.endDiaMm, nextRateText(t.lengthMm, it, t.endDiaMm))
            }
        }
        CommitNum("${endMap.rightCode} Ø (${abbr(unit)})", disp(t.endDiaMm, unit)) { s ->
            toMmOrNull(s, unit)?.let {
                onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, t.startDiaMm, it, nextRateText(t.lengthMm, t.startDiaMm, it))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rate mode:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(selected = autoRate, onClick = {
                autoRate = true
                // Explicit user action: sync the stored text to the computed
                // rate so model/PDF match what the card now shows.
                val autoText = computedRateText
                if (autoText != null && autoText != t.taperRateText) {
                    onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, t.startDiaMm, t.endDiaMm, autoText)
                }
            },
                label = { Text("Auto") }, colors = selectedColors,
                border = if (autoRate) BorderStroke(1.dp, Color.Black) else null)
            FilterChip(selected = !autoRate, onClick = { autoRate = false },
                label = { Text("Manual") }, colors = selectedColors,
                border = if (!autoRate) BorderStroke(1.dp, Color.Black) else null)
        }
        CommitNum(
            label = "Rate (1:12, 3/4, or decimal)",
            initialDisplay = if (autoRate) computedRateText.orEmpty() else t.taperRateText.ifBlank { "" },
            keyboardType = KeyboardType.Ascii,
            allowColon = true,
            enabled = !autoRate,
            externalIssueText = rateIssue,
            // Bare "1" passes parse so the validator can explain the ambiguity;
            // blank must NOT pass — updateTaper keeps the old rate on blank, so
            // committing "" would leave the field empty while the model retains it.
            parseValid = { parseTaperRateText(it, allowAmbiguousBareOne = false) != null || it.trim() == "1" },
            validator = { raw -> manualTaperRateBlockingMessage(raw, t.lengthMm, t.startDiaMm, t.endDiaMm) }
        ) { s ->
            onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, t.startDiaMm, t.endDiaMm, s.trim())
        }

        // Keyway — the whole section is gated behind the shared "Keyway" checkbox, the same gate
        // the body card carries (on-device request: a taper without a keyway shows no keyway
        // fields). The gate is derived, never stored: it seeds open for ANY typed W/D/L, so a
        // half-typed keyway keeps its fields on screen. Ticking only reveals the fields — the
        // model is untouched until a value commits.
        var kwEnabled by remember(t.id) { mutableStateOf(t.hasAnyKeywayValue) }
        // Stored values ALWAYS hold the section open, whatever the local tick says: an undo of a
        // confirmed removal brings the values back after the tick was cleared, and the only way
        // to close a section that holds values is the confirmed removal below.
        val kwOpen = kwEnabled || t.hasAnyKeywayValue
        var showRemoveKwDialog by remember(t.id) { mutableStateOf(false) }
        KeywayGateRow(checked = kwOpen, testTag = "taper_kw_gate") { checked ->
            // Unticking with typed keyway values confirms first: one tap would otherwise erase
            // up to four typed values — the same reason the body card's demote-to-auto
            // checkbox confirms.
            when (keywayGateAction(checked, t.hasAnyKeywayValue)) {
                KeywayGateAction.REVEAL -> kwEnabled = true
                KeywayGateAction.CONFIRM_REMOVE -> showRemoveKwDialog = true
                KeywayGateAction.HIDE -> kwEnabled = false
            }
        }
        if (showRemoveKwDialog) {
            RemoveKeywayConfirmDialog(
                onConfirm = {
                    showRemoveKwDialog = false
                    onUpdateTaperKeyway(idx, 0f, 0f, 0f, 0f, false)
                    kwEnabled = false
                },
                onDismiss = { showRemoveKwDialog = false },
            )
        }

        if (kwOpen) {
            // Keyway fields, in the KEYWAY's own unit (see the body card's note).
            val kwUnit = DisplayUnits(unit, unitOverrides).keywayUnitFor(t.id)
            if (perComponentUnitsEnabled) {
                KeywayUnitChip(t.id, unit, unitOverrides, onSetKeywayUnit)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CommitNum("KW W (${abbr(kwUnit)})", dispKw(t.keywayWidthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateTaperKeyway(idx, v, t.keywayDepthMm, t.keywayLengthMm, t.keywayOffsetFromSetMm, t.keywaySpooned)
                }
                Text("×", style = MaterialTheme.typography.titleMedium)
                CommitNum("KW D (${abbr(kwUnit)})", dispKw(t.keywayDepthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateTaperKeyway(idx, t.keywayWidthMm, v, t.keywayLengthMm, t.keywayOffsetFromSetMm, t.keywaySpooned)
                }
            }
            // Standard key stock for the W × D pair. The keyway is sized to the taper's LARGE end —
            // a key is specified for the section it seats in, and that is the L.E.T. A pick rides the
            // same update callback typing the fields does, so the values are authored from then on.
            KeywayStdSizePicker(
                unit = kwUnit,
                hostDiaMm = max(t.startDiaMm, t.endDiaMm),
            ) { w, d ->
                onUpdateTaperKeyway(idx, w, d, t.keywayLengthMm, t.keywayOffsetFromSetMm, t.keywaySpooned)
            }
            // KW L / Inset parse in `kwUnit` like KW W/D — the keyway-unit chip governs what
            // EVERY keyway number means; parsing these two in the document unit under a kwUnit
            // label read a metric keyway's length as inches.
            CommitNum("KW L (${abbr(kwUnit)})", dispKw(t.keywayLengthMm, kwUnit)) { s ->
                val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, v, t.keywayOffsetFromSetMm, t.keywaySpooned)
            }

            // Captured ⇔ inset > 0: the toggle is derived from the stored offset, never stored itself.
            // The last inset cleared or typed is remembered for this taper so re-capturing restores it.
            val isCaptured = t.keywayOffsetFromSetMm > 0f
            var rememberedInsetMm by remember(t.id) { mutableStateOf(0f) }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("taper_kw_captured")
                    .toggleable(
                        value = isCaptured,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { on ->
                            if (!on && t.keywayOffsetFromSetMm > 0f) rememberedInsetMm = t.keywayOffsetFromSetMm
                            val inset = keywayInsetForCaptured(on, t.keywayOffsetFromSetMm, rememberedInsetMm)
                            onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, t.keywayLengthMm, inset, t.keywaySpooned)
                        }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Captured keyway", modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = isCaptured, onCheckedChange = null)
            }
            if (isCaptured) {
                // A blank or ≤ 0 inset is not a way to un-capture: the toggle is the one control
                // that clears the inset, so such a commit is a no-op.
                CommitNum("KW Inset from SET (${abbr(kwUnit)})", dispKw(t.keywayOffsetFromSetMm, kwUnit)) { s ->
                    if (s.isBlank()) return@CommitNum
                    val v = toMmOrNull(s, kwUnit) ?: return@CommitNum
                    if (v <= 0f) return@CommitNum
                    rememberedInsetMm = v
                    onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, t.keywayLengthMm, v, t.keywaySpooned)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(
                        value = t.keywaySpooned, enabled = !isCaptured,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { checked ->
                            onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, t.keywayLengthMm, t.keywayOffsetFromSetMm, checked)
                        }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCaptured) "Keyway spooned (N/A — captured)" else "Keyway spooned",
                    modifier = Modifier.weight(1f),
                    color = if (isCaptured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Switch(
                    checked = t.keywaySpooned && !isCaptured,
                    enabled = !isCaptured,
                    onCheckedChange = null
                )
            }

            // Inside the gate: a taper with no keyway does not carry the clocking toggles even when
            // the shaft has two keyways elsewhere — they appear on the cards that carry the keyways,
            // which is where the control belongs.
            KeywayClockingSection(spec, onSetKeyways180Apart, onSetKeyways90Apart, onSetKeyways90Cw)
        }

        ShowDiaToggleRow(
            label = "Show name on drawing",
            checked = t.showNameOnDrawing ?: componentTitlesDefault,
            testTag = "taper_show_label_toggle",
            onCheckedChange = { onUpdateTaperShowLabel(idx, it) },
        )

        // Unset follows the kind's Settings checkbox; an explicit value overrides it either way.
        ShowDiaToggleRow(
            label = "Shade on drawing",
            checked = t.shadeOnDrawing ?: componentShadeDefaults.tapers,
            testTag = "taper_shade_toggle",
            onCheckedChange = { onUpdateTaperShade(idx, it) },
        )

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(t.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}
