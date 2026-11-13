package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UNDERCUT_LONG_COMPRESS_MAX_FRAC
import com.android.shaftschematic.model.UndercutRailStyle
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.pdf.buildUndercutAnchorLabel
import com.android.shaftschematic.pdf.buildUndercutStripTitle
import com.android.shaftschematic.pdf.formatDiaWithUnit
import com.android.shaftschematic.pdf.isUndercutStripPrinted
import com.android.shaftschematic.pdf.undercutAnchorFor
import com.android.shaftschematic.pdf.undercutSheetStrips
import com.android.shaftschematic.pdf.undercutStripSetSide
import com.android.shaftschematic.pdf.withResolvedBodies
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.buildLinerTitleById

// ──────────────────────────────────────────────────────────────────────────────
// "Distance to S.E.T." — which S.E.T. the strips measure to
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The undercut sheet's per-job "Distance to S.E.T." control — Nearest | AFT S.E.T. | FWD S.E.T.,
 * writing [UndercutRecord.setDatum]. ONE composable behind the tab's Drawing settings and the
 * undercut options panel, beside the "Page" row ([UndercutPageOrientationRow]). The strip titles
 * and the lead dimension at each strip's edge follow the same choice, so the caption names both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UndercutSetDatumRow(
    choice: UndercutSetDatum,
    onChoose: (UndercutSetDatum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Distance to S.E.T.", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UndercutSetDatum.entries.forEach { d ->
                WearChip(
                    label = undercutSetDatumLabel(d),
                    selected = choice == d,
                    modifier = Modifier.testTag(undercutSetDatumTestTag(d)),
                ) { onChoose(d) }
            }
        }
        Text(
            UNDERCUT_SET_DATUM_CAPTION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The caption under the "Distance to S.E.T." chips. */
internal const val UNDERCUT_SET_DATUM_CAPTION =
    "The strip title and the dimension at the strip's edge measure to this S.E.T. Saved with this job."

/**
 * The undercut sheet's per-job "Dimensions" control — Chain | From S.E.T., writing
 * [UndercutRecord.railStyle]. ONE composable behind the tab's Drawing settings and the undercut
 * options panel, above "Liner ends from S.E.T." ([UndercutLinerEndsFromSetRow]) and "Distance to
 * S.E.T." ([UndercutSetDatumRow]), whose S.E.T. the From S.E.T. rails start at. The caption says
 * what the chosen style prints.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UndercutRailStyleRow(
    choice: UndercutRailStyle,
    onChoose: (UndercutRailStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Dimensions", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UndercutRailStyle.entries.forEach { style ->
                WearChip(
                    label = undercutRailStyleLabel(style),
                    selected = choice == style,
                    modifier = Modifier.testTag(undercutRailStyleTestTag(style)),
                ) { onChoose(style) }
            }
        }
        Text(
            undercutRailStyleCaption(choice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The per-job "Liner ends from S.E.T." switch ([UndercutRecord.linerEndsFromSet]): each liner strip
 * prints a dimension from its S.E.T. to each end of the liner, over the chain. ONE row directly
 * below "Dimensions" ([UndercutRailStyleRow]) on the Undercut tab's Drawing settings and the
 * undercut options panel. It belongs to Chain dimensions: while [enabled] is false (Dimensions is
 * From S.E.T.) the switch greys out and the caption says so; the stored choice is left as it is.
 * [testTag] is the switch's tag — each host keeps its own, as the total row does.
 */
@Composable
internal fun UndercutLinerEndsFromSetRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Liner ends from S.E.T.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (enabled) UNDERCUT_LINER_ENDS_CAPTION else UNDERCUT_LINER_ENDS_FROM_SET_CAPTION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/**
 * The per-job "Compress long sections" slider ([UndercutRecord.longCompressFrac], on-device
 * request): how far the printed strips shorten their long stretches so the short cuts draw
 * larger. 0% is to scale. ONE row on the Undercut tab's Drawing settings and the undercut options
 * panel. The value follows the finger from local state and commits once on release; on the PDF
 * preview the page redraws under the finger through [onDrag], like the other tuning sliders.
 */
@Composable
internal fun UndercutLongCompressRow(
    frac: Float,
    onCommit: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    /**
     * Live-drag sink (`PreviewTuning.undercutLongCompress`), passed only where a rendered page
     * redraws under the finger: the in-progress value while dragging, null when the drag ends.
     * Never a write — the commit stays on release.
     */
    onDrag: ((Float?) -> Unit)? = null,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: frac
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Compress long sections", style = MaterialTheme.typography.bodyLarge)
            Text(
                undercutLongCompressLabel(shown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shown,
            onValueChange = {
                drag = it
                onDrag?.invoke(undercutLongCompressSnap(it))
            },
            onValueChangeFinished = {
                drag?.let { onCommit(undercutLongCompressSnap(it)) }
                drag = null
                onDrag?.invoke(null)
            },
            valueRange = 0f..UNDERCUT_LONG_COMPRESS_MAX_FRAC,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        Text(
            UNDERCUT_LONG_COMPRESS_CAPTION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The slider's value on its 5% grid — what a release commits. */
internal fun undercutLongCompressSnap(frac: Float): Float =
    (Math.round(frac.coerceIn(0f, UNDERCUT_LONG_COMPRESS_MAX_FRAC) * 20f) / 20f)

/** "To scale" at 0, else the percentage. */
internal fun undercutLongCompressLabel(frac: Float): String {
    val pct = Math.round(undercutLongCompressSnap(frac) * 100f)
    return if (pct == 0) "To scale" else "$pct%"
}

/** The caption under "Compress long sections". */
internal const val UNDERCUT_LONG_COMPRESS_CAPTION =
    "Shortens the long stretches on the printed sheet so short cuts draw larger. Short cuts keep " +
        "their proportions to each other, the dimensions never change, and the sheet is marked " +
        "\"Not to scale\". Saved with this job."

/** The caption under "Liner ends from S.E.T." while it applies. */
internal const val UNDERCUT_LINER_ENDS_CAPTION =
    "Each liner section also dimensions both ends of the liner from the S.E.T., so the liner can be " +
        "found where cuts have run over its edges. Saved with this job."

/** The caption under "Liner ends from S.E.T." while the job's dimensions are From S.E.T. */
internal const val UNDERCUT_LINER_ENDS_FROM_SET_CAPTION =
    "Prints with Chain dimensions only. Saved with this job."

/** The chip label for [style]. */
internal fun undercutRailStyleLabel(style: UndercutRailStyle): String = when (style) {
    UndercutRailStyle.CHAIN -> "Chain"
    UndercutRailStyle.FROM_SET -> "From S.E.T."
}

/** The chip's test tag for [style]: `undercut_rail_style_CHAIN` / `undercut_rail_style_FROM_SET`. */
internal fun undercutRailStyleTestTag(style: UndercutRailStyle): String = "undercut_rail_style_" + style.name

/** The caption under the "Dimensions" chips for the chosen [style]. */
internal fun undercutRailStyleCaption(style: UndercutRailStyle): String = when (style) {
    UndercutRailStyle.CHAIN ->
        "Each cut's length and the gaps between are chained from the section's edges. Saved with this job."
    UndercutRailStyle.FROM_SET ->
        "Each cut's start is measured from the S.E.T.; cut lengths print on the line under them. Saved with this job."
}

/**
 * The app-wide "Split cuts at liner edges" switch (`PdfPrefs.undercutSplitAtLinerEdge`): a cut
 * machined past a liner edge dimensioned as two spans meeting at the edge. ONE row behind the
 * Undercut tab's Drawing settings and the undercut options panel, placed with "Distance to
 * S.E.T." on both — it decides how the chain rail DIMENSIONS an overhang, so it sits with the
 * other dimensioning choice rather than among the shading rows, where it could not be found
 * (on-device report). [testTag] is the switch's tag: the two hosts keep their own so a test
 * can tell them apart when the panel is open over the tab.
 */
@Composable
internal fun UndercutSplitAtLinerEdgeRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Split cuts at liner edges", style = MaterialTheme.typography.bodyLarge)
            Text(
                "$UNDERCUT_SPLIT_AT_LINER_EDGE_CAPTION App-wide.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

/** The chip label for [d]. */
internal fun undercutSetDatumLabel(d: UndercutSetDatum): String = when (d) {
    UndercutSetDatum.NEAREST -> "Nearest"
    UndercutSetDatum.AFT -> "AFT S.E.T."
    UndercutSetDatum.FWD -> "FWD S.E.T."
}

/** The chip's test tag for [d]: `undercut_set_datum_nearest` / `_aft` / `_fwd`. */
internal fun undercutSetDatumTestTag(d: UndercutSetDatum): String =
    "undercut_set_datum_" + d.name.lowercase()

// ──────────────────────────────────────────────────────────────────────────────
// "Sections to print"
// ──────────────────────────────────────────────────────────────────────────────

/**
 * One section the undercut sheet can print — a strip ([strip]: one liner, or one bare-shaft area),
 * labelled as its printed title names it ([label]), with its cut count and whether the job prints
 * it ([printed], [isUndercutStripPrinted]).
 */
internal data class UndercutPrintSection(
    val strip: UndercutStrip,
    val label: String,
    val cutCount: Int,
    val printed: Boolean,
) {
    /** `undercut_print_section_<liner id>` / `undercut_print_section_free_<first cut id>`. */
    val testTag: String
        get() = when (strip) {
            is UndercutStrip.LinerStrip -> "undercut_print_section_${strip.linerId}"
            is UndercutStrip.FreeStrip -> "undercut_print_section_free_${strip.undercutIds.firstOrNull().orEmpty()}"
        }

    /** The row text: the label, then `(3 cuts)` / `(1 cut)` — nothing for a strip with no cuts. */
    val rowText: String
        get() = when (cutCount) {
            0 -> label
            1 -> "$label (1 cut)"
            else -> "$label ($cutCount cuts)"
        }
}

/**
 * Every section the sheet could print, in the order it draws them and BEFORE the "Sections to
 * print" filter — the strips [undercutSheetStrips] builds for this job (a blank draft's started
 * liner strips included), so the list and the page can never disagree about what a section is.
 *
 * A liner section reads as its strip title names it — the liner's name and OD in the liner's own
 * unit (`FWD Liner Ø10.000"`); a bare-shaft section as `Bare shaft — <anchor>`, the anchor measured
 * to the S.E.T. the strip measures to ([undercutStripSetSide]), in the document unit.
 */
internal fun undercutPrintSections(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    record: UndercutRecord,
    blankDraft: Boolean,
    displayUnits: DisplayUnits,
): List<UndercutPrintSection> {
    val sheet = undercutSheetStrips(spec, resolvedComponents, record, blankDraft)
    val docSpec = spec.withResolvedBodies(resolvedComponents)
    val linerTitles = buildLinerTitleById(docSpec)
    return sheet.allStrips.map { strip ->
        val spans = strip.undercutIds.mapNotNull { id ->
            sheet.clampedById[id]?.takeIf { !it.isEmpty }?.let { UndercutSpanMm(id, it.startMm, it.endMm) }
        }.sortedBy { it.startMm }
        val label = when (strip) {
            is UndercutStrip.LinerStrip -> {
                val od = docSpec.liners.firstOrNull { it.id == strip.linerId }?.odMm?.takeIf { it > 0f }
                buildUndercutStripTitle(
                    linerTitles[strip.linerId] ?: "Liner",
                    "",
                    od?.let { "Ø" + formatDiaWithUnit(it.toDouble(), displayUnits.unitFor(strip.linerId)) },
                )
            }
            is UndercutStrip.FreeStrip -> {
                val side = undercutStripSetSide(strip, docSpec, sheet.setPositions, record.setDatum, spans)
                val anchor = undercutAnchorFor(
                    firstShoulderMm = spans.firstOrNull()?.startMm ?: strip.chainStartMm,
                    lastShoulderMm = spans.lastOrNull()?.endMm ?: strip.chainEndMm,
                    aftSetXMm = sheet.aftSetMm,
                    fwdSetXMm = sheet.fwdSetMm,
                    forcedSide = side,
                )
                "Bare shaft — " + buildUndercutAnchorLabel(anchor, displayUnits.documentUnit)
            }
        }
        UndercutPrintSection(strip, label, spans.size, isUndercutStripPrinted(strip, record))
    }
}

/**
 * The cut ids that sit in a section the job leaves off the printout — the non-blank sheet's
 * sections, so the "Recorded undercuts" list can say which cuts will not print.
 */
internal fun undercutCutIdsNotPrinted(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>?,
    record: UndercutRecord,
): Set<String> =
    undercutSheetStrips(spec, resolvedComponents, record, blankValues = false).allStrips
        .filterNot { isUndercutStripPrinted(it, record) }
        .flatMap { it.undercutIds }
        .toSet()

/** The caption under the "Sections to print" list: `2 of 3 sections print. Saved with this job.` */
internal fun undercutPrintSectionsCaption(printed: Int, total: Int): String =
    "$printed of $total sections print. Saved with this job."

/** Why Print and Export are off while every section is left off the sheet. */
internal const val UNDERCUT_NONE_SELECTED_MESSAGE =
    "No sections selected to print — tick at least one under \"Sections to print\"."

/**
 * The per-job "Sections to print" list: one checkbox row per section, AFT → FWD, labelled like the
 * strip titles the sheet prints, with a caption counting what prints. Shown only for two or more
 * sections — one section has nothing to choose. ONE composable behind the Undercut tab (beside
 * Print, never folded away: it changes what prints) and the undercut options panel.
 */
@Composable
internal fun UndercutPrintSectionsList(
    sections: List<UndercutPrintSection>,
    onSetPrinted: (UndercutStrip, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.size < 2) return
    Column(modifier.fillMaxWidth()) {
        Text("Sections to print", style = MaterialTheme.typography.bodyLarge)
        sections.forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Checkbox) { onSetPrinted(s.strip, !s.printed) }
                    .testTag(s.testTag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The row is the click target; the box only shows the state.
                Checkbox(checked = s.printed, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                Text(s.rowText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            undercutPrintSectionsCaption(sections.count { it.printed }, sections.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("undercut_print_sections_caption"),
        )
    }
}
