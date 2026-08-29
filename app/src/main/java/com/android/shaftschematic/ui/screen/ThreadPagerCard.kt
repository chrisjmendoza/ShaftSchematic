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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.ui.util.threadWarningMessages
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.ThreadDesignation
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem

// ─────────────────────────────────────────────────────────────────────────────
// ThreadPagerCard — the `ResolvedThread` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a thread: the "Include thread in OAL" switch, the imperial
 * Major Ø/TPI pair or the metric designation field, and Length.
 *
 * The `!includeInOal` block is the one `AddThreadDialog` must mirror by the add-dialog-parity
 * invariant — an excluded thread swaps its Start field for the "Thread end: AFT | FWD" chips,
 * and the dialog has to do the same. "Show name on drawing" and the "Prints in: in | mm" chip at
 * the card's foot are the documented card-only carve-outs.
 *
 * [f1] and [startValidator] are supplied by [ComponentPagerCard] because the body card shares
 * them; [startValidator] closes over the spec and document unit that validate an overlap.
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
    threadTitleById: Map<String, String>,
    f1: (Float) -> String,
    startValidator: (String, ComponentKind, Float) -> (String) -> String?,
    onUpdateThread: (Int, Float, Float, Float, Float, String?) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateThreadShowLabel: (Int, Boolean) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
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
                ?: if (th.id in collidingComponentIds) "Overlaps another component" else null
        ),
        warningMessage = threadWarningMessages(th).joinToString("; ").ifEmpty { null },
        componentId = th.id, componentKind = ComponentKind.THREAD,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = {
            Log.d("ShaftUI", "Thread delete clicked: id=${th.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
            onRemoveThread(th.id)
        }
    ) {
        val includeInOal = !th.excludeFromOAL
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(
                    value = includeInOal,
                    role = androidx.compose.ui.semantics.Role.Switch,
                    onValueChange = { checked -> onSetThreadExcludeFromOal(th.id, !checked) }
                ).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Include thread in OAL", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = includeInOal, onCheckedChange = null)
        }
        if (!includeInOal) {
            // AFT / FWD end selector — replaces the start input for excluded threads
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Thread end:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                    containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
                )
                FilterChip(selected = th.isAftEnd,
                    onClick = { onSetThreadEndPosition(th.id, true) },
                    label = { Text("AFT") }, colors = chipColors,
                    border = if (th.isAftEnd) BorderStroke(1.dp, Color.Black) else null)
                FilterChip(selected = !th.isAftEnd,
                    onClick = { onSetThreadEndPosition(th.id, false) },
                    label = { Text("FWD") }, colors = chipColors,
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
            CommitNum("Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit)) { s ->
                toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, th.lengthMm, it, th.pitchMm, th.metricDesignation) }
            }
            CommitNum("TPI", tpiDisplay) { s ->
                parseFractionOrDecimal(s)?.toFloat()?.takeIf { it > 0f }?.let { tpi ->
                    onUpdateThread(idx, th.startFromAftMm, th.lengthMm, th.majorDiaMm, tpiToPitchMm(tpi), th.metricDesignation)
                }
            }
        }
        CommitNum("Length (${abbr(unit)})", disp(th.lengthMm, unit)) { s ->
            toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, it, th.majorDiaMm, th.pitchMm, th.metricDesignation) }
        }

        ShowDiaToggleRow(
            label = "Show name on drawing",
            checked = th.showLabelOnDrawing,
            testTag = "thread_show_label_toggle",
            onCheckedChange = { onUpdateThreadShowLabel(idx, it) },
        )

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(th.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}
