package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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
import com.android.shaftschematic.geom.exaggeratedProfileScale
import com.android.shaftschematic.geom.heightFracForDrawnHeight
import com.android.shaftschematic.geom.ProfileFeatureSpan
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN
import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.PROFILE_MIN_LINER_PT
import com.android.shaftschematic.geom.PROFILE_MIN_TAPER_PT
import com.android.shaftschematic.geom.PROFILE_MIN_THREAD_PT
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.maxOuterDiaMm
import kotlin.math.abs
import kotlin.math.roundToInt

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

/**
 * The per-job "Liner compression" control, shared by the same two surfaces as
 * [ShaftHeightSlider] (one `RunoutConfig` pair behind both). The measured components —
 * tapers and liners — are what the sheet is about, so liners can be held proportional:
 *
 * - Checkbox "Keep liners proportional lengthwise" (`linersProportional`): liners demand
 *   full true-scale width; the drawn height yields when the page can't fit them (the
 *   keyway-body posture). While checked the slider is disabled — it has no effect.
 * - Slider "Liner compression" (`linerCompression`, 0–100%): how far liners may
 *   foreshorten when the page needs the room — 100% = down to the writable floor (the
 *   default, the historical behavior), 0% = not at all (same drawing as the checkbox).
 *
 * [estimateHeightIn] maps a liner width-floor fraction to the drawn shaft height (paper
 * inches) it produces — see [estimatedShaftHeightIn]. The readout under the slider shows
 * that height LIVE during the drag, because the height cost is the whole trade this
 * control makes (on-device report: the slider "gives no indication on how it's changing
 * the height of the schematic").
 *
 * Drag-local value, committed once on release, same posture as the height slider.
 */
@Composable
internal fun LinerCompressionControl(
    linersProportional: Boolean,
    linerCompression: Float,
    estimateHeightIn: (linerMinFracOfTrue: Float) -> Float,
    onSetProportional: (Boolean) -> Unit,
    onSetCompression: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = linersProportional, onCheckedChange = onSetProportional)
            Spacer(Modifier.width(8.dp))
            Text("Keep liners proportional lengthwise", style = MaterialTheme.typography.bodyLarge)
        }
        var compressionDrag by remember { mutableStateOf<Float?>(null) }
        val shown = compressionDrag ?: linerCompression
        Text(
            "Liner compression  ${(shown * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall,
            color = if (linersProportional) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("0%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = { compressionDrag = it },
                onValueChangeFinished = {
                    compressionDrag?.let(onSetCompression)
                    compressionDrag = null
                },
                valueRange = 0f..1f,
                enabled = !linersProportional,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("100%", style = MaterialTheme.typography.bodySmall)
        }
        val fracShown = if (linersProportional) 1f else 1f - shown
        val atIn = estimateHeightIn(fracShown)
        val freeIn = estimateHeightIn(0f)
        val costsHeight = atIn < freeIn - 0.005f
        Text(
            when {
                linersProportional && costsHeight ->
                    "Liners hold true scale — the shaft draws ~${fmtIn(atIn)} tall " +
                        "(${fmtIn(freeIn)} with compression)."
                linersProportional ->
                    "Liners hold true scale — full height keeps (~${fmtIn(atIn)})."
                costsHeight ->
                    "At this setting the shaft draws ~${fmtIn(atIn)} tall " +
                        "(${fmtIn(freeIn)} at 100%). Wider liners trade drawn height."
                else ->
                    "Full height keeps at this setting (~${fmtIn(atIn)}). 0% holds " +
                        "liners fully proportional."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Estimated drawn shaft height (paper inches) at a given liner width-floor fraction —
 * the same `solveMaxProfileScale` arithmetic the composers run, over an approximate
 * window (0..OAL, standard content width): tapers/threads/liners at their kind floors,
 * liners raised by [linerMinFracOfTrue], keyway-bearing bodies pinned. An estimate — the
 * composers' exact windows and budgets differ by hairs — but it moves precisely when the
 * liner demand starts costing height, which is what the readout is for.
 */
internal fun estimatedShaftHeightIn(
    spec: ShaftSpec,
    baseScale: Float,
    heightScale: Float,
    linerMinFracOfTrue: Float,
    contentWidthPt: Float = 720f,
): Float {
    val maxDia = spec.maxOuterDiaMm().coerceAtLeast(10f)
    val desired = exaggeratedProfileScale(baseScale, heightScale, Float.MAX_VALUE, maxDia)
    val features = buildList {
        spec.tapers.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_TAPER_PT))
        }
        spec.liners.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_LINER_PT,
                    minWidthFracOfTrue = linerMinFracOfTrue,
                )
            )
        }
        spec.threads.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_THREAD_PT))
        }
        spec.bodies.filter { it.hasKeyway }.forEach {
            add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, Float.MAX_VALUE))
        }
    }
    val solved = solveMaxProfileScale(
        windowStartMm = 0f, windowEndMm = spec.overallLengthMm.coerceAtLeast(1f),
        features = features, contentWidth = contentWidthPt, scaleHi = desired,
    )
    return maxDia * solved / 72f
}
