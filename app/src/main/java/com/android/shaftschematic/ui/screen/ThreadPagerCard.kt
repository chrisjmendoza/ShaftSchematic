package com.android.shaftschematic.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.util.MUST_BE_POSITIVE_MSG
import com.android.shaftschematic.ui.util.collisionChipMessage
import com.android.shaftschematic.ui.util.positiveDiaErrorMm
import com.android.shaftschematic.ui.util.positiveLengthErrorMm
import com.android.shaftschematic.ui.util.spanOverlapErrorMm
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.ui.util.threadWarningMessages
import com.android.shaftschematic.util.isUnitlessNumberText
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.ThreadDesignation
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem

// ─────────────────────────────────────────────────────────────────────────────
// ThreadPagerCard — the `ResolvedThread` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a thread: the "Include thread in OAL" switch (which asks whether the
 * OAL follows the thread — [ThreadOalPromptDialog]), the imperial
 * Major Ø/TPI pair or the metric designation field, Length, and the thread relief
 * ([ThreadReliefCardSection]).
 *
 * The `!includeInOal` block is the one `AddThreadDialog` must mirror by the add-dialog-parity
 * invariant — an excluded thread swaps its Start field for the "Thread end: AFT | FWD" chips,
 * and the dialog has to do the same. The relief switch, its two fields and its "Relief at" chips
 * are under the same rule."Show name on drawing" and the "Prints in: in | mm" chip at
 * the card's foot are the documented card-only carve-outs.
 *
 * [f1] and [startValidator] are supplied by [ComponentPagerCard]; [f1] is shared with the body
 * card, and [startValidator] closes over the spec and document unit that validate an overlap.
 */
@Composable
internal fun ThreadPagerCard(
    component: ResolvedThread,
    explicitIndex: Int?,
    spec: ShaftSpec,
    unit: UnitSystem,
    physicalIndex: Int,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    componentTitlesDefault: Boolean = true,
    threadTitleById: Map<String, String>,
    f1: (Float) -> String,
    startValidator: (String, ComponentKind, Float) -> (String) -> String?,
    onUpdateThread: (Int, Float, Float, Float, Float, String?) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateThreadShowLabel: (Int, Boolean) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean, adjustOal: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
    onSetThreadRelief: (id: String, reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,
    onRemoveThread: (String) -> Unit,
    collidingComponentIds: Set<String>,
    perComponentUnitsEnabled: Boolean,
    unitOverrides: Map<String, UnitSystem>,
    onSetComponentUnit: (String, UnitSystem?) -> Unit,
) {
    val idx        = explicitIndex ?: return
    val th         = spec.threads.getOrNull(idx) ?: return
    val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
    val computedThreadTitle = threadTitleById[th.id] ?: "Thread"
    ComponentCard(
        title = computedThreadTitle,
        titleContent = {
            EditableCardTitle(
                componentId = th.id,
                title = computedThreadTitle,
                label = th.label,
                onCommitLabel = { onUpdateThreadLabel(idx, it) },
            )
        },
        debugText = if (showComponentDebugLabels) "id=${th.id} • startMm=${f1(th.startFromAftMm)} • endMm=${f1(th.startFromAftMm + th.lengthMm)}" else null,
        errorMessage = if (th.excludeFromOAL) null else (
            startOverlapErrorMm(spec, th.id, ComponentKind.THREAD, th.lengthMm, th.startFromAftMm)
                ?: if (th.id in collidingComponentIds) collisionChipMessage(spec, th.id) else null
        ),
        warningMessage = threadWarningMessages(spec, th, unit).joinToString("; ").ifEmpty { null },
        componentId = th.id, componentKind = ComponentKind.THREAD,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = {
            Log.d("ShaftUI", "Thread delete clicked: id=${th.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
            onRemoveThread(th.id)
        }
    ) {
        val includeInOal = !th.excludeFromOAL
        // The switch ASKS whether the shaft's overall length follows the thread (on-device
        // report: an OAL measured over the FWD thread, with the thread marked out of it). It keeps
        // showing the stored state until a choice is made. With no OAL typed or no thread length
        // there is nothing to ask, and it flips at once.
        var oalPromptOpen by remember(th.id) { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(
                    value = includeInOal,
                    role = androidx.compose.ui.semantics.Role.Switch,
                    onValueChange = { checked ->
                        if (spec.overallLengthMm > 0f && th.lengthMm > 0f) oalPromptOpen = true
                        else onSetThreadExcludeFromOal(th.id, !checked, false)
                    }
                ).padding(vertical = 4.dp)
                .testTag("thread_include_in_oal"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Include thread in OAL", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = includeInOal, onCheckedChange = null)
        }
        if (oalPromptOpen) {
            ThreadOalPromptDialog(
                excluding = includeInOal,
                overallLengthMm = spec.overallLengthMm,
                threadLengthMm = th.lengthMm,
                unit = unit,
                onChoose = { adjustOal ->
                    oalPromptOpen = false
                    onSetThreadExcludeFromOal(th.id, includeInOal, adjustOal)
                },
                onDismiss = { oalPromptOpen = false },
            )
        }
        if (!includeInOal) {
            // AFT / FWD end selector — replaces the start input for excluded threads
            ChipFlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Thread end:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                    containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
                )
                FilterChip(selected = th.isAftEnd,
                    onClick = { onSetThreadEndPosition(th.id, true) },
                    label = { ChipLabel("AFT") }, colors = chipColors,
                    border = if (th.isAftEnd) BorderStroke(1.dp, Color.Black) else null)
                FilterChip(selected = !th.isAftEnd,
                    onClick = { onSetThreadEndPosition(th.id, false) },
                    label = { ChipLabel("FWD") }, colors = chipColors,
                    border = if (!th.isAftEnd) BorderStroke(1.dp, Color.Black) else null)
            }
        } else {
            CommitNum("Start (${abbr(unit)})", disp(th.startFromAftMm, unit), validator = startValidator(th.id, ComponentKind.THREAD, th.lengthMm)) { s ->
                toMmOrNull(s, unit)?.let { onUpdateThread(idx, it, th.lengthMm, th.majorDiaMm, th.pitchMm, th.metricDesignation) }
            }
        }
        // Metric threads are self-declaring (major Ø + pitch both come off the
        // designation, see `ThreadDesignation`) so a thread stored with one shows its
        // designation field here instead of the imperial Major Ø/TPI pair — same
        // parity rule as the Add dialog's Imperial/Metric toggle.
        if (th.metricDesignation != null) {
            CommitDesignationField(
                "Thread designation",
                th.metricDesignation,
            ) { text ->
                ThreadDesignation.parse(text)?.let { d ->
                    onUpdateThread(idx, th.startFromAftMm, th.lengthMm, d.majorDiaMm, d.pitchMm ?: 0f, d.format())
                }
            }
        } else {
            FieldPair(
                first = {
                    CommitNum(
                        "Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit),
                        validator = { raw -> positiveDiaErrorMm(toMmOrNull(raw, unit)) },
                    ) { s ->
                        toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, th.lengthMm, it, th.pitchMm, th.metricDesignation) }
                    }
                },
                second = {
                    // A TPI of 0 has no pitch: refused and named, as AddThreadDialog's gate does.
                    CommitNum(
                        "TPI", tpiDisplay, parseValid = ::isUnitlessNumberText,
                        validator = { raw ->
                            parseFractionOrDecimal(raw)?.let { if (it <= 0.0) MUST_BE_POSITIVE_MSG else null }
                        },
                    ) { s ->
                        parseFractionOrDecimal(s)?.toFloat()?.let { tpi ->
                            onUpdateThread(idx, th.startFromAftMm, th.lengthMm, th.majorDiaMm, tpiToPitchMm(tpi), th.metricDesignation)
                        }
                    }
                },
            )
        }
        CommitNum(
            "Length (${abbr(unit)})", disp(th.lengthMm, unit),
            // A thread counted in the OAL is checked like its Start: running into another
            // thread is refused. An excluded thread hangs off the shaft end and never collides.
            validator = { raw ->
                val newLen = toMmOrNull(raw, unit)
                positiveLengthErrorMm(newLen) ?: if (th.excludeFromOAL || newLen == null) null
                    else spanOverlapErrorMm(spec, th.id, ComponentKind.THREAD, th.startFromAftMm, newLen)
            },
        ) { s ->
            toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, it, th.majorDiaMm, th.pitchMm, th.metricDesignation) }
        }

        // Thread relief — mirrored in AddThreadDialog under the add-dialog-parity rule.
        ThreadReliefCardSection(
            th = th,
            unit = unit,
            overallLengthMm = spec.overallLengthMm,
            onSetThreadRelief = onSetThreadRelief,
        )

        ShowDiaToggleRow(
            label = "Show name on drawing",
            checked = th.showNameOnDrawing ?: componentTitlesDefault,
            caption = if (th.showNameOnDrawing == null) COMPONENT_NAMES_DEFAULT_CAPTION else null,
            testTag = "thread_show_label_toggle",
            onCheckedChange = { onUpdateThreadShowLabel(idx, it) },
        )

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(th.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}

/**
 * The "Include thread in OAL" prompt: [excluding] takes the thread out of the OAL (offering to
 * shorten it by [threadLengthMm]), otherwise into it (offering to lengthen). [onChoose] receives
 * `adjustOal` — `true` for Shorten/Lengthen, `false` for Keep OAL; Cancel and an outside tap
 * choose nothing. Lengths print in the document's [unit].
 *
 * A confirmation on a change to a STORED thread (the `body_demote_confirm` class), not a control,
 * so `AddThreadDialog` carries nothing for it.
 */
@Composable
private fun ThreadOalPromptDialog(
    excluding: Boolean,
    overallLengthMm: Float,
    threadLengthMm: Float,
    unit: UnitSystem,
    onChoose: (adjustOal: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val newOalMm = if (excluding) overallLengthMm - threadLengthMm else overallLengthMm + threadLengthMm
    val oldOal = formatLenWithUnit(overallLengthMm.toDouble(), unit)
    val newOal = formatLenWithUnit(newOalMm.coerceAtLeast(0f).toDouble(), unit)
    val verb = if (excluding) "Shorten" else "Lengthen"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (excluding) "Exclude thread from OAL" else "Include thread in OAL") },
        text = {
            Text(
                "${verb} the overall length by the thread? OAL $oldOal → $newOal. " +
                    "Parts measured from the FWD end keep their distance from it, exactly as if " +
                    "you typed the new OAL."
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onChoose(false) },
                    modifier = Modifier.testTag("thread_oal_keep"),
                ) { Text("Keep OAL") }
                TextButton(
                    onClick = { onChoose(true) },
                    modifier = Modifier.testTag("thread_oal_adjust"),
                ) { Text("$verb OAL") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("thread_oal_cancel"),
            ) { Text("Cancel") }
        },
    )
}
