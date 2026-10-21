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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.model.ShaftSpec
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
