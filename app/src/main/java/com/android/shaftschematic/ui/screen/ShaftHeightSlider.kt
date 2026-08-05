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
import com.android.shaftschematic.geom.drawnShaftHeightPt
import com.android.shaftschematic.geom.heightFracForDrawnHeight
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN
import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import kotlin.math.abs

/**
 * Commits within this distance of the standard multiplier (1.0) snap to exactly 1.0 — a
 * magnetic detent so the default never has to be fished for by pixel ("don't want to
 * have to fight the slider" — on-device request). The Reset button is the guaranteed path.
 */
internal const val HEIGHT_SCALE_SNAP_TOLERANCE = 0.05f

/** [rawCommit] with the standard-height detent applied — one snap rule for every site. */
internal fun snappedHeightScale(rawCommit: Float): Float =
    if (abs(rawCommit - 1f) <= HEIGHT_SCALE_SNAP_TOLERANCE) 1f else rawCommit

/**
 * The per-job "Shaft height" slider, shared by the Consolidated Output tab and the
 * schematic PDF options sheet (one `RunoutConfig.heightScale` value behind both).
 *
 * The slider selects the drawn shaft height by VALUE, in inches on paper (on-device
 * request: "the end of the slider would be 1.5\" and I can select the height by value,
 * not percentage") — paper inches regardless of the document's display unit, because the
 * cap is a paper measure. The track runs from the 50% height to 1.5"
 * ([PROFILE_MAX_SHAFT_HEIGHT_PT]) — or to the most this shaft can reach at 300% when
 * that is less — and the picked value converts back to the stored per-job multiplier
 * ([heightFracForDrawnHeight]). [baseScale] is the surface's conventional solve (pt/mm):
 * the fixed visual scale on the schematic; max(width-fit, visual scale) on the
 * runout/consolidated sheets.
 *
 * Drag-local value, committed once on release (per-frame commits would re-render the PDF
 * preview every frame); commits near the standard height snap exactly to it; Reset
 * returns to standard.
 */
@Composable
internal fun ShaftHeightSlider(
    heightScale: Float,
    baseScale: Float,
    maxDiaMm: Float,
    onCommit: (Float) -> Unit,
) {
    fun heightIn(frac: Float) = drawnShaftHeightPt(baseScale, frac, maxDiaMm) / 72f
    val minIn = heightIn(PROFILE_HEIGHT_SCALE_MIN)
    val maxIn = heightIn(PROFILE_HEIGHT_SCALE_MAX)
    val standardIn = heightIn(1f)

    var heightDrag by remember { mutableStateOf<Float?>(null) }
    val shownIn = (heightDrag ?: heightIn(heightScale)).coerceIn(minIn, maxIn)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Shaft height  ${fmtIn(shownIn)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { heightDrag = null; onCommit(1f) },
                enabled = heightScale != 1f || heightDrag != null,
            ) { Text("Standard (${fmtIn(standardIn)})") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtIn(minIn), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shownIn,
                onValueChange = { heightDrag = it },
                onValueChangeFinished = {
                    heightDrag?.let {
                        onCommit(snappedHeightScale(heightFracForDrawnHeight(baseScale, it * 72f, maxDiaMm)))
                    }
                    heightDrag = null
                },
                valueRange = minIn..maxIn,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(fmtIn(maxIn), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (maxIn >= PROFILE_MAX_SHAFT_HEIGHT_PT / 72f - 1e-3f) {
                "Drawn height of the shaft on paper. 1.5 in is the cap; the drawing keeps " +
                    "true proportion and narrows instead of overflowing."
            } else {
                "Drawn height of the shaft on paper. This shaft reaches ${fmtIn(maxIn)} at most."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Paper-inch label, two decimals: 1.13″, 1.50″. */
private fun fmtIn(inches: Float): String = "%.2f″".format(inches)
