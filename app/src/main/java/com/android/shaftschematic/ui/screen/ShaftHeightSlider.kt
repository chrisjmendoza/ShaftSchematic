package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
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
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.geom.solveMaxProfileScale
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.settings.PDF_ARROW_SIZES_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_LARGE_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_MEDIUM_PT
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_SMALL_PT
import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MAX
import com.android.shaftschematic.settings.PDF_WEAR_BAND_SHADE_MIN
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_MAX_MM
import com.android.shaftschematic.settings.PDF_WEAR_JOIN_GAP_MIN_MM
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.setPdfWearTraceDepthFrac
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.UnitSystem
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
 * locally and committed once on release so drag frames never write DataStore.
 *
 * [onDrag] is the visual-only channel a hosting preview opts into: the in-progress value
 * on every frame, `null` once the drag ends. It reports the SAME units as [onCommit] (a
 * thickness multiplier), and it is never a persistence path — see [PreviewTuning].
 */
@Composable
internal fun LineThicknessSlider(
    scale: Float,
    onCommit: (Float) -> Unit,
    onDrag: (Float?) -> Unit = {},
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
                onClick = { thicknessDrag = null; onDrag(null); onCommit(1f) },
                enabled = scale != 1f || thicknessDrag != null,
            ) { Text("Default (100%)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = thicknessDrag ?: scale,
                onValueChange = { thicknessDrag = it; onDrag(it) },
                onValueChangeFinished = {
                    thicknessDrag?.let { onCommit(snappedLineThickness(it)) }
                    thicknessDrag = null
                    onDrag(null)
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("200%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The "Body S-break" threshold slider, shared by Settings → Drawing and both PDF options
 * sheets (`PdfOptionsSheet` on the schematic preview, `RunoutWearOptionsSheet` on the
 * runout / consolidated previews) — ONE `PdfPrefs.sBreakThresholdFrac` behind all three, so
 * the threshold can be tuned against the drawing it changes instead of only from Settings.
 *
 * Never (0) = compression never triggers a break; Always (1) = any foreshortening breaks.
 * Steps of 5%. Drag is tracked locally and committed once on release so drag frames never
 * write DataStore. [onDrag] is the visual-only channel a hosting preview opts into — the
 * in-progress value (already stepped, same units as [onCommit]) every frame, `null` on
 * release. See [PreviewTuning].
 *
 * The long-span trigger is deliberately outside this control: a run that eats 220 pt of
 * paper at true scale is not hidden compression, so it keeps its break at every setting.
 * The explanatory caption belongs to the caller — Settings carries it, the sheets stay
 * compact.
 */
@Composable
internal fun SBreakThresholdSlider(
    frac: Float,
    onCommit: (Float) -> Unit,
    onDrag: (Float?) -> Unit = {},
) {
    var sBreakDrag by remember { mutableStateOf<Float?>(null) }
    val shown = sBreakDrag ?: frac
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Body S-break  ${fmtSBreakThreshold(shown)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { sBreakDrag = null; onDrag(null); onCommit(PDF_SBREAK_THRESHOLD_DEFAULT) },
                enabled = frac != PDF_SBREAK_THRESHOLD_DEFAULT || sBreakDrag != null,
            ) { Text("Default (${(PDF_SBREAK_THRESHOLD_DEFAULT * 100).roundToInt()}%)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Never", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = {
                    val stepped = (it * 20f).roundToInt() / 20f
                    sBreakDrag = stepped
                    onDrag(stepped)
                },
                onValueChangeFinished = {
                    sBreakDrag?.let(onCommit)
                    sBreakDrag = null
                    onDrag(null)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("Always", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The worn-profile trace exaggeration slider, shared by Settings → Drawing (where it sets the
 * global default, `PdfPrefs.wearTraceDepthFrac`) and the Wear tab (where it pins this job's
 * `WearRecord.traceDepthFrac`) — ONE construction behind both, so the two rows read alike and
 * the range can never drift apart.
 *
 * [WEAR_TRACE_MIN_DEPTH_FRAC]..[WEAR_TRACE_MAX_DEPTH_FRAC] in 1% steps; 25% is the shipped high
 * end and the default ("25% should be our high end" — on-device verdict). Drag is tracked
 * locally and committed once on release, so drag frames never write DataStore and never mark
 * the document dirty. [trailing] is the caller's header-row button — a reset in Settings, a
 * "Save as default" on the Wear tab.
 */
@Composable
internal fun WearTraceDepthSlider(
    frac: Float,
    onCommit: (Float) -> Unit,
    title: String,
    trailing: @Composable () -> Unit = {},
) {
    var depthDrag by remember { mutableStateOf<Float?>(null) }
    val shown = (depthDrag ?: frac).coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$title  ${(shown * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtWholePct(WEAR_TRACE_MIN_DEPTH_FRAC), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = {
                    val stepped = (it * 100f).roundToInt() / 100f
                    depthDrag = stepped
                },
                onValueChangeFinished = {
                    depthDrag?.let(onCommit)
                    depthDrag = null
                },
                valueRange = WEAR_TRACE_MIN_DEPTH_FRAC..WEAR_TRACE_MAX_DEPTH_FRAC,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("wear_trace_depth_slider"),
            )
            Text(fmtWholePct(WEAR_TRACE_MAX_DEPTH_FRAC), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Whole-percent label for a 0..1 drawing fraction — ONE formatter for every percentage slider
 * here (trace depth, wear-band shade) and for the Settings rows that mirror them.
 */
internal fun fmtWholePct(v: Float): String = "${(v * 100).roundToInt()}%"

/**
 * The Wear document's "Trace depth exaggeration" row — the shared [WearTraceDepthSlider] plus
 * the "Save as default" button and the caption — as BOTH the Wear tab and the wear preview's
 * PDF options sheet show it. One construction, so the tab and the sheet can never drift.
 *
 * Moving the slider pins THIS job's value (`WearRecord.traceDepthFrac`); "Save as default"
 * promotes the current value to the Settings → Drawing default (`PdfPrefs.wearTraceDepthFrac`)
 * and clears the override in the same action, so the job then follows the default it created.
 *
 * [effectiveFrac] is this job's depth already resolved against [globalDefault]
 * (`effectiveWearTraceDepthFrac`) — the same value both wear draw sites use, which is what
 * keeps them identical.
 */
@Composable
internal fun WearTraceDepthControlRow(
    vm: ShaftViewModel,
    effectiveFrac: Float,
    globalDefault: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WearTraceDepthSlider(
            frac = effectiveFrac,
            onCommit = { vm.setWearTraceDepthFrac(it) },
            title = "Trace depth exaggeration",
            trailing = {
                TextButton(
                    onClick = {
                        vm.setPdfWearTraceDepthFrac(effectiveFrac)
                        vm.setWearTraceDepthFrac(null)
                    },
                    enabled = effectiveFrac != globalDefault,
                    modifier = Modifier.testTag("wear_trace_save_default"),
                ) { Text("Save as default") }
            },
        )
        Text(
            "Deepest measured wear draws at this fraction of the liner radius. " +
                "Drawing only — printed Ø values never change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The "Wear area shade" slider, shared by Settings → Drawing and the wear preview's PDF options
 * sheet — ONE `PdfPrefs.wearBandShadeFrac` behind both, so the wash can be judged against the
 * sheet it prints on instead of only from Settings.
 *
 * [PDF_WEAR_BAND_SHADE_MIN]..[PDF_WEAR_BAND_SHADE_MAX] in 1% steps. The cap is the point of the
 * range: a wear band is what pit "X"s land in, printed and hand-drawn, and a heavier fill buries
 * them. Drag is tracked locally and committed once on release so drag frames never write
 * DataStore. [trailing] is the caller's header-row button — a reset in Settings, nothing on the
 * sheet, which stays compact.
 */
@Composable
internal fun WearBandShadeSlider(
    frac: Float,
    onCommit: (Float) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    var shadeDrag by remember { mutableStateOf<Float?>(null) }
    val shown = (shadeDrag ?: frac).coerceIn(PDF_WEAR_BAND_SHADE_MIN, PDF_WEAR_BAND_SHADE_MAX)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Wear area shade  ${fmtWholePct(shown)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtWholePct(PDF_WEAR_BAND_SHADE_MIN), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = { shadeDrag = (it * 100f).roundToInt() / 100f },
                onValueChangeFinished = {
                    shadeDrag?.let(onCommit)
                    shadeDrag = null
                },
                valueRange = PDF_WEAR_BAND_SHADE_MIN..PDF_WEAR_BAND_SHADE_MAX,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("wear_band_shade_slider"),
            )
            Text(fmtWholePct(PDF_WEAR_BAND_SHADE_MAX), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Step the "Taper–liner join" slider snaps to, in canonical mm: 1/2" in inches mode, 10 mm in
 * millimetres mode. Display granularity only — the stored value stays mm either way.
 */
private fun joinGapStepMm(unit: UnitSystem): Float = if (unit == UnitSystem.INCHES) 12.7f else 10f

/** Snaps a canonical-mm join gap to [joinGapStepMm] and back into the settable range. */
internal fun snappedJoinGapMm(mm: Float, unit: UnitSystem): Float {
    val step = joinGapStepMm(unit)
    return ((mm / step).roundToInt() * step)
        .coerceIn(PDF_WEAR_JOIN_GAP_MIN_MM, PDF_WEAR_JOIN_GAP_MAX_MM)
}

/**
 * Reads a canonical-mm join gap in the session's unit — the UI edge, and the only place this
 * value is ever anything but millimetres. Inches take the app's own smart inch format (so 12.7 mm
 * prints as `1/2"`); millimetres print whole.
 */
internal fun fmtJoinGap(mm: Float, unit: UnitSystem): String = when {
    mm <= 0f -> if (unit == UnitSystem.INCHES) "0\"" else "0 mm"
    unit == UnitSystem.INCHES -> LengthFormat.formatInchesSmart(mm / 25.4) + "\""
    else -> "${mm.roundToInt()} mm"
}

/**
 * The "Taper–liner join" slider, shared by Settings → Drawing and the wear preview's PDF options
 * sheet — ONE `PdfPrefs.wearJoinGapMaxMm` behind both, so the threshold can be judged against the
 * strips it reshapes.
 *
 * How much bare shaft two components sharing a wear detail strip may have between them before the
 * run compresses to an S-break instead of drawing true. The value is canonical **mm** at every
 * layer; the display unit and the snap step ([snappedJoinGapMm]) are a UI-edge conversion only.
 * `0` breaks on any positive gap — touching components draw contiguous at every setting, since
 * they produce no gap segment at all. Drag is tracked locally and committed once on release, so
 * drag frames never write DataStore (the wear preview has no live-tuning channel; the release
 * commit re-renders the page). [trailing] is the caller's header-row button — a reset in
 * Settings, nothing on the sheet, which stays compact.
 */
@Composable
internal fun WearJoinGapSlider(
    gapMm: Float,
    unit: UnitSystem,
    onCommit: (Float) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    var gapDrag by remember { mutableStateOf<Float?>(null) }
    val shown = (gapDrag ?: gapMm).coerceIn(PDF_WEAR_JOIN_GAP_MIN_MM, PDF_WEAR_JOIN_GAP_MAX_MM)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Taper–liner join  ${fmtJoinGap(shown, unit)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtJoinGap(PDF_WEAR_JOIN_GAP_MIN_MM, unit), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = { gapDrag = snappedJoinGapMm(it, unit) },
                onValueChangeFinished = {
                    gapDrag?.let(onCommit)
                    gapDrag = null
                },
                valueRange = PDF_WEAR_JOIN_GAP_MIN_MM..PDF_WEAR_JOIN_GAP_MAX_MM,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("wear_join_gap_slider"),
            )
            Text(fmtJoinGap(PDF_WEAR_JOIN_GAP_MAX_MM, unit), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The "Dimension arrows" size picker, shared by both PDF options sheets and
 * Settings → Drawing — ONE `PdfPrefs.arrowSizePt` behind all three, so the head can be sized
 * against the drawing it prints on.
 *
 * Three fixed sizes rather than a slider (on-device request): an arrowhead either reads or it
 * doesn't, and Large is the historical head. A tap IS the commit — there is no drag channel
 * and no [PreviewTuning] override; the preview re-renders from the stored value.
 */
@Composable
internal fun DimensionArrowSizeChips(
    arrowSizePt: Float,
    onCommit: (Float) -> Unit,
) {
    Column {
        Text("Dimension arrows", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PDF_ARROW_SIZES_PT.forEach { pt ->
                FilterChip(
                    selected = abs(arrowSizePt - pt) < 1e-3f,
                    onClick = { onCommit(pt) },
                    label = { Text(arrowSizeLabel(pt)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Text(
            "Arrowhead size on the dimension rails. Heads point inward unless the span is " +
                "too narrow to hold both.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The "Fractions" style picker, shared by both PDF options sheets and Settings → Drawing —
 * ONE `PdfPrefs.fractionStyle` behind all three.
 *
 * It sits beside the drawing controls rather than under a text setting because it changes
 * every drawn surface at once: the previews and the exported sheets both build their fractions
 * through `util/FractionTextRenderer.kt`. A tap IS the commit — there is no drag channel and no
 * [PreviewTuning] override; the preview re-renders from the stored value.
 */
@Composable
internal fun FractionStyleChips(
    fractionStyle: FractionStyle,
    onCommit: (FractionStyle) -> Unit,
) {
    Column {
        Text("Fractions", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FractionStyle.entries.forEach { style ->
                FilterChip(
                    selected = fractionStyle == style,
                    onClick = { onCommit(style) },
                    label = { Text(style.uiLabel()) },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("fraction_style_${style.name.lowercase()}"),
                )
            }
        }
        Text(
            fractionStyleHint(fractionStyle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One-line description of what the selected style draws. */
private fun fractionStyleHint(style: FractionStyle): String = when (style) {
    FractionStyle.STACKED ->
        "Numerator over denominator with a bar, the shop-ruler fraction. Applies to previews " +
            "and exported sheets alike."
    FractionStyle.DIAGONAL ->
        "Raised numerator, slanted divider, denominator on the line. Wider than stacked, and " +
            "easier to read at small print sizes."
    FractionStyle.INLINE ->
        "Plain text: 3/16 on one line, no built-up fraction."
}

/** Chip label for one of `PDF_ARROW_SIZES_PT` — nearest wins, so a stray stored value still reads. */
private fun arrowSizeLabel(pt: Float): String = when {
    pt <= (PDF_ARROW_SIZE_SMALL_PT + PDF_ARROW_SIZE_MEDIUM_PT) / 2f -> "Small"
    pt <= (PDF_ARROW_SIZE_MEDIUM_PT + PDF_ARROW_SIZE_LARGE_PT) / 2f -> "Medium"
    else -> "Large"
}

/**
 * "Never" at 0, else the threshold as "below N%" of true length — ONE formatter for every
 * site that shows `PdfPrefs.sBreakThresholdFrac`.
 */
internal fun fmtSBreakThreshold(v: Float): String =
    if (v <= 0f) "Never" else "below ${(v * 100).roundToInt()}%"

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
 * Drag-local value, committed once on release (a per-frame commit would mark the job dirty
 * on every frame); commits near the standard height snap exactly to it; Reset returns to
 * standard.
 *
 * [onDrag] is the visual-only channel a hosting preview opts into. It reports the value in
 * the SAME units as [onCommit] — the stored `heightScale` multiplier, converted from the
 * slider's drawn inches exactly as the commit does — but **without** the standard-height
 * detent: snapping is a commit rule, and applying it per frame would make the drawing jump
 * while the finger is still on the track. `null` once the drag ends. See [PreviewTuning].
 */
@Composable
internal fun ShaftHeightSlider(
    heightScale: Float,
    baseScale: Float,
    maxDiaMm: Float,
    onCommit: (Float) -> Unit,
    onDrag: (Float?) -> Unit = {},
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
                onClick = { heightDrag = null; onDrag(null); onCommit(1f) },
                enabled = heightScale != 1f || heightDrag != null,
            ) { Text("Standard (${fmtIn(standardIn)})") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtIn(minIn), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shownIn,
                onValueChange = {
                    heightDrag = it
                    onDrag(heightFracForDrawnHeight(baseScale, it * 72f, maxDiaMm))
                },
                onValueChangeFinished = {
                    heightDrag?.let {
                        onCommit(snappedHeightScale(heightFracForDrawnHeight(baseScale, it * 72f, maxDiaMm)))
                    }
                    heightDrag = null
                    onDrag(null)
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
 * Drag-local value, committed once on release, same posture as the height slider. [onDrag]
 * is the visual-only channel a hosting preview opts into — the raw in-progress compression
 * value (the SAME units as [onSetCompression]) every frame, `null` on release; the derived
 * width floor is the consumer's business ([RunoutConfig.linerMinFracOfTrue]). See
 * [PreviewTuning].
 */
@Composable
internal fun LinerCompressionControl(
    linersProportional: Boolean,
    linerCompression: Float,
    estimateKeptFrac: (requestedFracOfTrue: Float) -> Float,
    onSetProportional: (Boolean) -> Unit,
    onSetCompression: (Float) -> Unit,
    onDrag: (Float?) -> Unit = {},
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
                onValueChange = { compressionDrag = it; onDrag(it) },
                onValueChangeFinished = {
                    compressionDrag?.let(onSetCompression)
                    compressionDrag = null
                    onDrag(null)
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
