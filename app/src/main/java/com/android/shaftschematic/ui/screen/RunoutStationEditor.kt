package com.android.shaftschematic.ui.screen

/**
 * RunoutStationEditor — the per-component measurement-station count control.
 *
 * Extracted from `RunoutRoute` so the Consolidated Output tab can host the SAME control
 * (on-device report: the consolidated sheet offered no way to change a bubble count, which
 * meant leaving the tab to fix a count for a sheet you were looking at). One composable,
 * two hosts — the two surfaces cannot drift.
 *
 * The editor is a thin view over `RunoutConfig.componentOverrides`: a row shows the override
 * when there is one and the derived default otherwise, and +/− writes an override. Nothing
 * here computes station positions — that is `geom/RunoutBubbleLayout.kt`, shared by the PDF
 * composer and the canvas preview.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.defaultStationCount
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById

/**
 * One row of the station editor: a component that can carry runout bubbles.
 *
 * @property id Station key — the BASE body id for bodies (all fragments of one stored body
 *   share a single row and a single override), the component id otherwise.
 * @property kind Determines the derived default count.
 * @property lengthMm Total drawn length across every run of this component — a body split by
 *   a liner contributes each fragment, so the length-derived default reflects what is
 *   actually drawn.
 * @property startMm Aft-most run's start, used only for axial ordering of the rows.
 */
internal data class RunoutComponentEntry(
    val id: String,
    val label: String,
    val kind: RunoutComponentKind,
    val lengthMm: Float,
    val startMm: Float,
) {
    /** Station count when the user has set no override. */
    val defaultCount: Int get() = defaultStationCount(kind, lengthMm)
}

/**
 * Bodies, tapers and liners in axial order, one row per component the user names.
 *
 * Spans come from the ONE resolved→runout mapping (`ui/resolved/RunoutSpans.kt`) — the same
 * builder the canvas and the PDF draw stations from — so a row here can never disagree with
 * the drawn bubbles about identity, eligibility, or length. This function only folds a
 * component's runs into a single row (lengths add up, the row anchors at the aft-most run)
 * and attaches display labels.
 */
internal fun buildRunoutStationEntries(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
): List<RunoutComponentEntry> {
    val bodyTitles = buildBodyTitleById(spec)
    val taperTitles = buildTaperTitleById(spec)
    val linerTitles = buildLinerTitleById(spec)

    // Labels only — identity comes from the shared span mapping below.
    val autoBodyIds = resolvedComponents
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.AUTO }
        .mapTo(mutableSetOf()) { resolvedBodyBaseId(it.id) }

    fun label(id: String, kind: RunoutComponentKind): String = when (kind) {
        RunoutComponentKind.BODY ->
            if (id in autoBodyIds) "Body (auto)" else bodyTitles[id] ?: "Body"
        RunoutComponentKind.TAPER -> taperTitles[id] ?: "Taper"
        RunoutComponentKind.LINER -> linerTitles[id] ?: "Liner"
    }

    return runoutComponentSpans(resolvedComponents)
        .groupBy { it.id }
        .map { (id, runs) ->
            RunoutComponentEntry(
                id = id,
                label = label(id, runs.first().kind),
                kind = runs.first().kind,
                lengthMm = runs.map { it.lengthMm }.sum(),
                startMm = runs.minOf { it.startMm },
            )
        }
        .sortedBy { it.startMm }
}

/**
 * The "Measurement stations" block: one +/− row per component. Renders nothing when there
 * are no eligible components.
 *
 * @param onSetCount Called with the component's station key and the requested count; the
 *   ViewModel clamps and stores it in `RunoutConfig.componentOverrides`.
 */
@Composable
internal fun RunoutStationCountEditor(
    entries: List<RunoutComponentEntry>,
    overrides: Map<String, Int>,
    onSetCount: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
) {
    if (entries.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showHeading) {
            Text("Measurement stations", style = MaterialTheme.typography.titleSmall)
        }
        entries.forEach { entry ->
            val currentCount = overrides[entry.id] ?: entry.defaultCount
            RunoutStationRow(
                label = entry.label,
                currentCount = currentCount,
                onDecrement = { onSetCount(entry.id, currentCount - 1) },
                onIncrement = { onSetCount(entry.id, currentCount + 1) },
            )
        }
    }
}

@Composable
private fun RunoutStationRow(
    label: String,
    currentCount: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Stations:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            // 0 is a legal floor — a component not being measured carries no bubbles.
            IconButton(onClick = onDecrement, enabled = currentCount > 0) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "$currentCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = onIncrement) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
