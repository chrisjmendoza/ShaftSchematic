package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Commits within this distance of 100% snap to exactly 1.0 — a magnetic detent so the
 * default never has to be fished for by pixel ("don't want to have to fight the slider" —
 * on-device request). The dedicated reset button is the guaranteed path.
 */
internal const val HEIGHT_SCALE_SNAP_TOLERANCE = 0.05f

/** [rawCommit] with the 100% detent applied — the single snap rule for every slider site. */
internal fun snappedHeightScale(rawCommit: Float): Float =
    if (abs(rawCommit - 1f) <= HEIGHT_SCALE_SNAP_TOLERANCE) 1f else rawCommit

/**
 * The per-job "Shaft height" slider, shared by the Consolidated Output tab and the
 * schematic PDF options sheet (one `RunoutConfig.heightScale` value behind both).
 *
 * - Track ends at [effectiveMax] (`effectiveHeightScaleMax`): where the 1.5" ceiling
 *   engages for THIS shaft, so the limit reads on the control instead of a dead drag
 *   zone. Full 300% only when the shaft never reaches the ceiling.
 * - Drag-local value, committed once on release (committing per frame would re-render
 *   the PDF preview every frame); commits near 100% snap exactly to 1.0.
 * - The "Reset" action commits exactly 100%.
 */
@Composable
internal fun ShaftHeightSlider(
    heightScale: Float,
    effectiveMax: Float,
    onCommit: (Float) -> Unit,
) {
    var heightDrag by remember { mutableStateOf<Float?>(null) }
    val trackMax = effectiveMax.coerceIn(PROFILE_HEIGHT_SCALE_MIN, PROFILE_HEIGHT_SCALE_MAX)
    val shown = (heightDrag ?: heightScale).coerceIn(PROFILE_HEIGHT_SCALE_MIN, trackMax)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Shaft height  ${(shown * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { heightDrag = null; onCommit(1f) },
                enabled = shown != 1f,
            ) { Text("Reset") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = { heightDrag = it },
                onValueChangeFinished = {
                    heightDrag?.let { onCommit(snappedHeightScale(it)) }
                    heightDrag = null
                },
                valueRange = PROFILE_HEIGHT_SCALE_MIN..trackMax,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${(trackMax * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (trackMax < PROFILE_HEIGHT_SCALE_MAX) {
                "Exaggerate or shrink the drawn shaft as a whole. This shaft reaches the " +
                    "1.5 in paper-height cap at ${(trackMax * 100).roundToInt()}%."
            } else {
                "Exaggerate or shrink the drawn shaft as a whole. Drawn height caps at " +
                    "1.5 in on paper."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
