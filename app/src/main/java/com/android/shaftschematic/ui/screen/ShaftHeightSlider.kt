package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.android.shaftschematic.geom.fracFitFactor
import com.android.shaftschematic.geom.heightFracForDrawnHeight
import com.android.shaftschematic.geom.ProfileFeatureSpan
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN
import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.PROFILE_MIN_LINER_PT
import com.android.shaftschematic.geom.PROFILE_MIN_THREAD_PT
import com.android.shaftschematic.geom.PROFILE_TAPER_MIN_FRAC_OF_TRUE
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
 * Line-thickness slider commits within this distance of the default multiplier (1.0)
 * snap to exactly 1.0 — the shaft-height detent applied to thickness, so 100% never has
 * to be fished for by pixel ("had some trouble landing on 100%" — on-device report).
 * Typed values are never snapped; the Default button is the guaranteed path.
 */
internal const val LINE_THICKNESS_SNAP_TOLERANCE = 0.05f

/** [rawCommit] with the 100% detent applied — one snap rule for every thickness site. */
internal fun snappedLineThickness(rawCommit: Float): Float =
    if (abs(rawCommit - 1f) <= LINE_THICKNESS_SNAP_TOLERANCE) 1f else rawCommit

/**
 * The "Line thickness" slider block shared by the schematic PDF options sheet and the
 * runout/wear options sheet. (Settings keeps its own layout — it adds a typed % field —
 * but shares [snappedLineThickness] and the Default button posture.) Drag is tracked
 * locally and committed once on release so drag frames don't write DataStore or
 * re-render an open PDF preview.
 */
@Composable
internal fun LineThicknessSlider(
    scale: Float,
    onCommit: (Float) -> Unit,
) {
    var thicknessDrag by remember { mutableStateOf<Float?>(null) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Line thickness  ${((thicknessDrag ?: scale) * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { thicknessDrag = null; onCommit(1f) },
                enabled = scale != 1f || thicknessDrag != null,
            ) { Text("Default (100%)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = thicknessDrag ?: scale,
                onValueChange = { thicknessDrag = it },
                onValueChangeFinished = {
                    thicknessDrag?.let { onCommit(snappedLineThickness(it)) }
                    thicknessDrag = null
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("200%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The "Shade in PDF" heading + Bodies / Tapers / Liners checkbox group shared by the two
 * PDF options sheets (`PdfOptionsSheet` on the schematic preview, `RunoutWearOptionsSheet`
 * on the runout / wear / undercut / consolidated tabs). Settings → PDF Export keeps its own
 * copy: its rows sit in a `spacedBy(12.dp)` column with a padded heading, so sharing this
 * block there would retighten that page's spacing.
 *
 * [linerShadeLocked] locks the "Liners" row on a document that prints measured Ø values
 * inside the profile: their halos are sheet-white, so the composer draws liners unfilled
 * there (`consolidatedSheetHasInProfileValues`). The row then reads unchecked and disabled
 * — **display only**; the stored pref is never rewritten, so the user's choice returns as
 * soon as the document stops printing in-profile values.
 */
@Composable
internal fun ShadeInPdfChecks(
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
    onSetShadedBodies: (Boolean) -> Unit,
    onSetShadedTapers: (Boolean) -> Unit,
    onSetShadedLiners: (Boolean) -> Unit,
    linerShadeLocked: Boolean = false,
) {
    Column {
        Text("Shade in PDF", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pdfShadedBodies, onCheckedChange = onSetShadedBodies)
            Spacer(Modifier.width(8.dp))
            Text("Bodies", style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pdfShadedTapers, onCheckedChange = onSetShadedTapers)
            Spacer(Modifier.width(8.dp))
            Text("Tapers", style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = pdfShadedLiners && !linerShadeLocked,
                onCheckedChange = onSetShadedLiners,
                enabled = !linerShadeLocked,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Liners", style = MaterialTheme.typography.bodyLarge)
                if (linerShadeLocked) {
                    Text(
                        "Ø values print inside the profile on this sheet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

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
 * the sizing-curve scale at the configured anchor heights on the schematic;
 * max(width-fit, curve scale) on the runout/consolidated sheets.
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
 * - Checkbox "Keep liners proportional lengthwise" (`linersProportional`): liners
 *   request full true-scale width. Best-effort — the request never enters the scale
 *   solve, so the drawn height does not yield; the floors λ-shrink instead. While
 *   checked the slider is disabled.
 * - Slider "Liner compression" (`linerCompression`, 0–100%): how far liners may
 *   foreshorten when the page needs the room — 100% = down to the writable floor (the
 *   default), 0% = not at all (same drawing as the checkbox).
 *
 * The drawing height takes PRECEDENCE (on-device direction): this control never changes
 * the drawn shaft height — liner floors take only the room the page has at the selected
 * height, shrinking themselves (never the shaft) when the full request doesn't fit.
 * [estimateKeptFrac] maps a requested width-floor fraction to the fraction liners
 * actually keep at this height — see [estimatedLinerKeptFracOfTrue]; the readout under
 * the slider shows it LIVE during the drag (on-device report: the slider "gives no
 * indication" of its effect).
 *
 * Drag-local value, committed once on release, same posture as the height slider.
 */
@Composable
internal fun LinerCompressionControl(
    linersProportional: Boolean,
    linerCompression: Float,
    estimateKeptFrac: (requestedFracOfTrue: Float) -> Float,
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
        val requested = if (linersProportional) 1f else 1f - shown
        val kept = estimateKeptFrac(requested)
        val keptPct = (kept * 100).roundToInt()
        val shortfall = kept < requested - 0.005f
        Text(
            when {
                requested <= 0.005f ->
                    "Liners may compress to the writable floor. The drawn height " +
                        "never changes."
                !shortfall && requested >= 0.995f ->
                    "Liners draw fully proportional at this height. The drawn height " +
                        "never changes."
                !shortfall ->
                    "Liners keep at least ~$keptPct% of true length. The drawn height " +
                        "never changes."
                else ->
                    "The page affords liners ~$keptPct% of true length at this height " +
                        "(of the ${(requested * 100).roundToInt()}% asked). The drawn " +
                        "height never changes."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Estimated fraction of TRUE length liners actually keep for a requested width-floor
 * fraction — requested × the λ fit ([fracFitFactor]) at the height-precedence scale
 * (the solve ignores the liner raises entirely, so the drawn height never moves with
 * this control). Same feature construction the composers use, over an approximate
 * window (0..OAL, standard content width): tapers/threads/liners at their kind floors,
 * keyway-bearing bodies pinned. An estimate — the composers' exact windows differ by
 * hairs — but it moves exactly when the page starts shorting the request, which is what
 * the readout is for.
 */
internal fun estimatedLinerKeptFracOfTrue(
    spec: ShaftSpec,
    baseScale: Float,
    heightScale: Float,
    requestedFracOfTrue: Float,
    contentWidthPt: Float = 720f,
): Float {
    if (requestedFracOfTrue <= 0f) return 0f
    val maxDia = spec.maxOuterDiaMm().coerceAtLeast(10f)
    val desired = exaggeratedProfileScale(baseScale, heightScale, Float.MAX_VALUE, maxDia)
    val windowEnd = spec.overallLengthMm.coerceAtLeast(1f)
    val features = buildList {
        // Tapers: ratio-preserving frac floor, no flat floor — same as the composers.
        spec.tapers.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, 0f,
                    minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE,
                )
            )
        }
        spec.liners.forEach {
            add(
                ProfileFeatureSpan(
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, PROFILE_MIN_LINER_PT,
                    minWidthFracOfTrue = requestedFracOfTrue,
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
        windowStartMm = 0f, windowEndMm = windowEnd,
        features = features, contentWidth = contentWidthPt, scaleHi = desired,
    )
    val lambda = fracFitFactor(
        windowStartMm = 0f, windowEndMm = windowEnd,
        features = features, contentWidthPt = contentWidthPt, diaPtPerMm = solved,
    )
    return requestedFracOfTrue * lambda
}
