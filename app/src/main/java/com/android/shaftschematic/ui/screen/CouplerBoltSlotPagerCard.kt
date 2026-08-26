package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.util.UnitSystem

// ─────────────────────────────────────────────────────────────────────────────
// CouplerBoltSlotPagerCard — the `ResolvedCouplerBoltSlot` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a coupler bolt slot — a reference-only radial cutout, so nothing
 * here reaches OAL, body splitting, or collision.
 *
 * Measure From, hole Ø, count, spacing (only above one hole) and the through/blind pair are
 * mirrored in `AddCouplerBoltSlotDialog` by the add-dialog-parity invariant; "Show dimension
 * rail" is the documented card-only carve-out.
 *
 * [f1] is supplied by [ComponentPagerCard] because every card's debug line uses it.
 */
@Composable
internal fun CouplerBoltSlotPagerCard(
    component: ResolvedCouplerBoltSlot,
    explicitIndex: Int?,
    spec: ShaftSpec,
    unit: UnitSystem,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    f1: (Float) -> String,
    onUpdateCouplerBoltSlot: (index: Int, startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float) -> Unit,
    onUpdateCouplerBoltSlotReference: (Int, SlotAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,
    onRemoveCouplerBoltSlot: (String) -> Unit,
) {
    val idx = explicitIndex ?: return
    val cs  = spec.couplerBoltSlots.getOrNull(idx) ?: return
    val isFwdRef = cs.authoredReference == SlotAuthoredReference.FWD
    // Row span from aft-most (i=0) to fwd-most center.
    val rowSpanMm = (cs.count - 1).coerceAtLeast(0) * cs.spacingMm
    // Displayed authored start: distance from the reference face to the nearest cutout.
    val authoredStartMm = if (isFwdRef) {
        spec.overallLengthMm - (cs.startFromAftMm + rowSpanMm)
    } else {
        cs.startFromAftMm
    }
    // Recompute the aft-most physical start from an authored value.
    fun physFromAuthored(authoredMm: Float, count: Int, spacingMm: Float): Float {
        val span = (count - 1).coerceAtLeast(0) * spacingMm
        return if (isFwdRef) (spec.overallLengthMm - authoredMm - span).coerceAtLeast(0f) else authoredMm
    }

    ComponentCard(
        title = cs.label ?: "Coupler Bolt Slot",
        debugText = if (showComponentDebugLabels) "id=${cs.id} • startMm=${f1(cs.startFromAftMm)} • count=${cs.count}" else null,
        componentId = cs.id, componentKind = ComponentKind.COUPLER_BOLT_SLOT,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = { onRemoveCouplerBoltSlot(cs.id) }
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
            FilterChip(selected = !isFwdRef, onClick = { onUpdateCouplerBoltSlotReference(idx, SlotAuthoredReference.AFT) },
                label = { Text("AFT") }, colors = selectedColors,
                border = if (!isFwdRef) BorderStroke(1.dp, Color.Black) else null)
            FilterChip(selected = isFwdRef, onClick = { onUpdateCouplerBoltSlotReference(idx, SlotAuthoredReference.FWD) },
                label = { Text("FWD") }, colors = selectedColors,
                border = if (isFwdRef) BorderStroke(1.dp, Color.Black) else null)
        }

        CommitNum(
            label = "First slot from ${if (isFwdRef) "FWD" else "AFT"} (${abbr(unit)})",
            initialDisplay = disp(authoredStartMm, unit)
        ) { s ->
            val authoredMm = toMmOrNull(s, unit) ?: return@CommitNum
            onUpdateCouplerBoltSlot(idx, physFromAuthored(authoredMm, cs.count, cs.spacingMm), cs.holeDiaMm, cs.count, cs.spacingMm, cs.through, cs.depthMm)
        }
        CommitNum("Hole Ø (${abbr(unit)})", disp(cs.holeDiaMm, unit)) { s ->
            toMmOrNull(s, unit)?.let { onUpdateCouplerBoltSlot(idx, cs.startFromAftMm, it, cs.count, cs.spacingMm, cs.through, cs.depthMm) }
        }
        CommitNum("Count", cs.count.toString()) { s ->
            val newCount = s.trim().toIntOrNull()?.coerceAtLeast(1) ?: return@CommitNum
            // Keep the authored (referenced) end fixed as count changes.
            val newPhys = physFromAuthored(authoredStartMm, newCount, cs.spacingMm)
            onUpdateCouplerBoltSlot(idx, newPhys, cs.holeDiaMm, newCount, cs.spacingMm, cs.through, cs.depthMm)
        }
        if (cs.count > 1) {
            CommitNum("Spacing (${abbr(unit)})", disp(cs.spacingMm, unit)) { s ->
                val newSpacing = toMmOrNull(s, unit) ?: return@CommitNum
                val newPhys = physFromAuthored(authoredStartMm, cs.count, newSpacing)
                onUpdateCouplerBoltSlot(idx, newPhys, cs.holeDiaMm, cs.count, newSpacing, cs.through, cs.depthMm)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Through hole", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = cs.through,
                onCheckedChange = { checked ->
                    onUpdateCouplerBoltSlot(idx, cs.startFromAftMm, cs.holeDiaMm, cs.count, cs.spacingMm, checked, cs.depthMm)
                }
            )
        }
        if (!cs.through) {
            CommitNum("Depth (${abbr(unit)})", disp(cs.depthMm, unit)) { s ->
                toMmOrNull(s, unit)?.let { onUpdateCouplerBoltSlot(idx, cs.startFromAftMm, cs.holeDiaMm, cs.count, cs.spacingMm, cs.through, it) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show dimension rail", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = cs.showDimensionRail,
                onCheckedChange = { onUpdateCouplerBoltSlotShowRail(idx, it) }
            )
        }
    }
}
