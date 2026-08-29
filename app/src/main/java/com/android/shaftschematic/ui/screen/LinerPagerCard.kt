package com.android.shaftschematic.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasShoulder
import com.android.shaftschematic.model.shoulderOn
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.util.linerWarningMessages
import com.android.shaftschematic.ui.util.positiveLengthErrorMm
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem

// ─────────────────────────────────────────────────────────────────────────────
// LinerPagerCard — the `ResolvedLiner` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a liner: the "Measure From: AFT | FWD" chips, Start/Length/Outer Ø,
 * the Ø-callout switch, and the capability-gated shoulder section.
 *
 * The shoulder controls appear when [linerShouldersEnabled] is on OR the liner already carries
 * shoulder values — a device pref may hide empty entry fields, never authored work. The
 * reference chips and every value control are mirrored in `AddLinerDialog` by the
 * add-dialog-parity invariant; "Show Ø on drawing", "Show name on drawing", and the
 * "Prints in: in | mm" chip are the documented card-only carve-outs.
 *
 * [f1] is supplied by [ComponentPagerCard] because every card's debug line uses it.
 */
@Composable
internal fun LinerPagerCard(
    component: ResolvedLiner,
    explicitIndex: Int?,
    spec: ShaftSpec,
    unit: UnitSystem,
    physicalIndex: Int,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    componentTitlesDefault: Boolean = true,
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
    linerTitleById: Map<String, String>,
    f1: (Float) -> String,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerShowDia: (Int, Boolean) -> Unit,
    onUpdateLinerShowLabel: (Int, Boolean) -> Unit,
    onUpdateLinerShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateLinerShoulder: (Int, LinerAuthoredReference, Float, Float, Float) -> Unit,
    linerShouldersEnabled: Boolean,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onRemoveLiner: (String) -> Unit,
    collidingComponentIds: Set<String>,
    perComponentUnitsEnabled: Boolean,
    unitOverrides: Map<String, UnitSystem>,
    onSetComponentUnit: (String, UnitSystem?) -> Unit,
) {
    val idx            = explicitIndex ?: return
    val ln             = spec.liners.getOrNull(idx) ?: return
    val computedTitle  = linerTitleById[ln.id] ?: "Liner"
    val isFwdRef       = ln.authoredReference == LinerAuthoredReference.FWD
    val authoredStartMm = if (isFwdRef) {
        spec.overallLengthMm - ln.startFromAftMm - ln.lengthMm
    } else {
        ln.startFromAftMm
    }

    ComponentCard(
        title = computedTitle,
        titleContent = {
            EditableCardTitle(
                componentId = ln.id,
                title = computedTitle,
                label = ln.label,
                onCommitLabel = { onUpdateLinerLabel(idx, it) },
            )
        },
        debugText = if (showComponentDebugLabels) "id=${ln.id} • startMm=${f1(ln.startFromAftMm)} • endMm=${f1(ln.startFromAftMm + ln.lengthMm)}" else null,
        errorMessage = startOverlapErrorMm(spec, ln.id, ComponentKind.LINER, ln.lengthMm, ln.startFromAftMm)
            ?: if (ln.id in collidingComponentIds) "Overlaps another component" else null,
        warningMessage = linerWarningMessages(spec, ln).joinToString("; ").ifEmpty { null },
        componentId = ln.id, componentKind = ComponentKind.LINER,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = {
            Log.d("ShaftUI", "Liner delete clicked: id=${ln.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
            onRemoveLiner(ln.id)
        }
    ) {
        // AFT / FWD reference toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Measure From:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val selectedColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
            )
            FilterChip(selected = !isFwdRef, onClick = { onUpdateLinerReference(idx, LinerAuthoredReference.AFT) },
                label = { Text("AFT") }, colors = selectedColors,
                border = if (!isFwdRef) BorderStroke(1.dp, Color.Black) else null)
            FilterChip(selected = isFwdRef, onClick = { onUpdateLinerReference(idx, LinerAuthoredReference.FWD) },
                label = { Text("FWD") }, colors = selectedColors,
                border = if (isFwdRef) BorderStroke(1.dp, Color.Black) else null)
        }

        CommitNum(
            label = "Start from ${if (isFwdRef) "FWD" else "AFT"} (${abbr(unit)})",
            initialDisplay = disp(authoredStartMm, unit),
            validator = { raw ->
                val authoredMm = toMmOrNull(raw, unit) ?: return@CommitNum "Enter a number"
                val physStart  = if (isFwdRef) spec.overallLengthMm - authoredMm - ln.lengthMm else authoredMm
                startOverlapErrorMm(spec, ln.id, ComponentKind.LINER, ln.lengthMm, physStart)
            }
        ) { s ->
            val authoredMm = toMmOrNull(s, unit) ?: return@CommitNum
            val physStart  = if (isFwdRef) spec.overallLengthMm - authoredMm - ln.lengthMm else authoredMm
            onUpdateLiner(idx, physStart, ln.lengthMm, ln.odMm)
        }
        CommitNum(
            "Length (${abbr(unit)})", disp(ln.lengthMm, unit),
            validator = { raw -> positiveLengthErrorMm(toMmOrNull(raw, unit)) },
        ) { s ->
            val newLen    = toMmOrNull(s, unit) ?: return@CommitNum
            val physStart = if (isFwdRef) {
                val authored = spec.overallLengthMm - ln.startFromAftMm - ln.lengthMm
                spec.overallLengthMm - authored - newLen
            } else {
                ln.startFromAftMm
            }
            onUpdateLiner(idx, physStart, newLen, ln.odMm)
        }
        CommitNum("Outer Ø (${abbr(unit)})", disp(ln.odMm, unit)) { s ->
            toMmOrNull(s, unit)?.let { onUpdateLiner(idx, ln.startFromAftMm, ln.lengthMm, it) }
        }
        ShowDiaToggleRow(
            label = "Show Ø on drawing",
            checked = ln.showDiaOnDrawing,
            testTag = "liner_show_dia_toggle",
            onCheckedChange = { onUpdateLinerShowDia(idx, it) },
        )
        ShowDiaToggleRow(
            label = "Show name on drawing",
            checked = ln.showNameOnDrawing ?: componentTitlesDefault,
            testTag = "liner_show_label_toggle",
            onCheckedChange = { onUpdateLinerShowLabel(idx, it) },
        )
        // Unset follows the kind's Settings checkbox. A consolidated sheet printing measured Ø
        // values inside the profile draws every liner bare whatever this says — grey under a
        // sheet-white knockout halo reads as a pasted box.
        ShowDiaToggleRow(
            label = "Shade on drawing",
            checked = ln.shadeOnDrawing ?: componentShadeDefaults.liners,
            testTag = "liner_shade_toggle",
            onCheckedChange = { onUpdateLinerShade(idx, it) },
        )

        // Shoulders: capability-gated entry, but authored work always keeps its
        // controls — a device pref may hide empty fields, never stored values.
        if (linerShouldersEnabled || ln.hasShoulder()) {
            var aftWanted by remember(ln.id) {
                mutableStateOf(ln.shoulderOn(LinerAuthoredReference.AFT) != null)
            }
            var fwdWanted by remember(ln.id) {
                mutableStateOf(ln.shoulderOn(LinerAuthoredReference.FWD) != null)
            }
            LinerShoulderSection(
                aftOn = aftWanted,
                fwdOn = fwdWanted,
                aftRadiusMm = ln.shoulderAftRadiusMm,
                fwdRadiusMm = ln.shoulderFwdRadiusMm,
                unit = unit,
                onSetAftOn = { on ->
                    aftWanted = on
                    // None zeroes the end (the blend section's Square posture).
                    if (!on) onUpdateLinerShoulder(idx, LinerAuthoredReference.AFT, 0f, 0f, 0f)
                },
                onSetFwdOn = { on ->
                    fwdWanted = on
                    if (!on) onUpdateLinerShoulder(idx, LinerAuthoredReference.FWD, 0f, 0f, 0f)
                },
                onSetAftRadiusMm = { r ->
                    onUpdateLinerShoulder(
                        idx, LinerAuthoredReference.AFT,
                        ln.shoulderAftLenMm, ln.shoulderAftOdMm, r)
                },
                onSetFwdRadiusMm = { r ->
                    onUpdateLinerShoulder(
                        idx, LinerAuthoredReference.FWD,
                        ln.shoulderFwdLenMm, ln.shoulderFwdOdMm, r)
                },
                aftFields = {
                    CommitNum("Shoulder length (${abbr(unit)})", disp(ln.shoulderAftLenMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let {
                            onUpdateLinerShoulder(
                                idx, LinerAuthoredReference.AFT,
                                it, ln.shoulderAftOdMm, ln.shoulderAftRadiusMm)
                        }
                    }
                    CommitNum("Shoulder Ø (${abbr(unit)})", disp(ln.shoulderAftOdMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let {
                            onUpdateLinerShoulder(
                                idx, LinerAuthoredReference.AFT,
                                ln.shoulderAftLenMm, it, ln.shoulderAftRadiusMm)
                        }
                    }
                },
                fwdFields = {
                    CommitNum("Shoulder length (${abbr(unit)})", disp(ln.shoulderFwdLenMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let {
                            onUpdateLinerShoulder(
                                idx, LinerAuthoredReference.FWD,
                                it, ln.shoulderFwdOdMm, ln.shoulderFwdRadiusMm)
                        }
                    }
                    CommitNum("Shoulder Ø (${abbr(unit)})", disp(ln.shoulderFwdOdMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let {
                            onUpdateLinerShoulder(
                                idx, LinerAuthoredReference.FWD,
                                ln.shoulderFwdLenMm, it, ln.shoulderFwdRadiusMm)
                        }
                    }
                },
            )
        }

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(ln.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}
