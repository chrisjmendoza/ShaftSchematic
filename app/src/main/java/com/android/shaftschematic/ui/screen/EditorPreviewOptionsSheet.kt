package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.PreviewDimTextSize

/** Test tags on the editor's Preview options sheet. */
internal const val PREVIEW_OPTIONS_SHEET_TAG = "preview_options_sheet"
/** The preview box's corner button that opens the sheet. */
internal const val PREVIEW_OPTIONS_BUTTON_TAG = "preview_options_button"
internal const val PREVIEW_OPT_DIMENSIONS_TAG = "preview_opt_dimensions"
/** One chip per [PreviewDimTextSize]: `preview_dim_text_SMALL|MEDIUM|LARGE`. */
internal fun previewDimTextChipTag(size: PreviewDimTextSize): String = "preview_dim_text_${size.name}"
internal const val PREVIEW_OPT_SHEET_LAYOUT_TAG = "preview_opt_sheet_layout"
internal const val PREVIEW_OPT_GRID_TAG = "preview_opt_grid"
internal const val PREVIEW_OPT_HIGHLIGHT_TAG = "preview_opt_highlight"
internal const val PREVIEW_OPT_SHARED_TAG = "preview_opt_shared"
internal const val PREVIEW_OPT_LINER_COMPRESSION_TAG = "preview_opt_liner_compression"
internal const val PREVIEW_OPT_SHADING_TAG = "preview_opt_shading"

/** Scrim behind the Preview options sheet — light, so the preview it restyles still reads. */
private const val PREVIEW_OPTIONS_SCRIM_ALPHA = 0.12f

/**
 * Largest share of the window the Preview options sheet takes. The editor's preview box sits in
 * the top third of a phone screen (under the title and tool bars), so a sheet capped a little
 * over half the window leaves it in view.
 */
private const val PREVIEW_OPTIONS_MAX_HEIGHT_FRAC = 0.55f

/**
 * What the editor's Preview options sheet shows and edits — every value is one the app already
 * stores: the preview's device preferences (Settings → Editor Screen), the app-wide sheet look
 * (`PdfPrefs`, Settings → Drawing) and the per-job "Shaft height" (`RunoutConfig.heightScale`) and
 * "Liner & taper compression" pair (`RunoutConfig.linersProportional` / `linerCompression`). The
 * sheet owns no storage of its own.
 */
internal data class EditorPreviewOptionsState(
    val showDimensions: Boolean,
    val sheetLayout: Boolean,
    val showGrid: Boolean,
    val highlightSelection: Boolean,
    val fractionStyle: FractionStyle,
    val arrowSizePt: Float,
    /** The document prints dual units — the dual-unit layout chips show only then. */
    val dualUnits: Boolean,
    val dualUnitLayout: DualUnitLayout,
    val sBreakThresholdFrac: Float,
    val heightScale: Float,
    /** [ShaftHeightSlider]'s base and sizing diameter — the schematic's, as its PDF options sheet passes. */
    val heightSliderBase: Float,
    val heightSliderMaxDiaMm: Float,
    val linersProportional: Boolean = false,
    val linerCompression: Float = 1f,
    /** [LinerCompressionControl]'s live readout — the fraction of true length liners keep at this height. */
    val estimateLinerKeptFrac: (requestedFracOfTrue: Float) -> Float = { it },
    /** The preview rails' value size — a device preference (Settings → Editor Screen). */
    val dimTextSize: PreviewDimTextSize = PreviewDimTextSize.Default,
    /**
     * The "Shade in Components" prefs — the preview fills a component exactly when the sheet
     * shades it (`RenderOptions.shadedComponentIds`), so these restyle the box as well as the PDF.
     */
    val shadedBodies: Boolean = false,
    val shadedTapers: Boolean = true,
    val shadedLiners: Boolean = true,
    val shadeExplicitBodiesOnly: Boolean = false,
)

/** The sheet's setters — each the same ViewModel setter Settings and the PDF options panels call. */
internal class EditorPreviewOptionsActions(
    val onShowDimensions: (Boolean) -> Unit,
    val onSheetLayout: (Boolean) -> Unit,
    val onShowGrid: (Boolean) -> Unit,
    val onHighlightSelection: (Boolean) -> Unit,
    val onFractionStyle: (FractionStyle) -> Unit,
    val onArrowSizePt: (Float) -> Unit,
    val onDualUnitLayout: (DualUnitLayout) -> Unit,
    val onSBreakThresholdFrac: (Float) -> Unit,
    val onHeightScale: (Float) -> Unit,
    val onLinersProportional: (Boolean) -> Unit = {},
    val onLinerCompression: (Float) -> Unit = {},
    /** Visual-only drag channels (the [PreviewTuning] posture): the preview redraws under the finger, nothing is written. */
    val onSBreakDrag: (Float?) -> Unit = {},
    val onHeightDrag: (Float?) -> Unit = {},
    val onLinerCompressionDrag: (Float?) -> Unit = {},
    val onDimTextSize: (PreviewDimTextSize) -> Unit = {},
    val onShadedBodies: (Boolean) -> Unit = {},
    val onShadedTapers: (Boolean) -> Unit = {},
    val onShadedLiners: (Boolean) -> Unit = {},
    val onShadeExplicitBodiesOnly: (Boolean) -> Unit = {},
)

/**
 * The editor's "Preview options…" sheet (on-device request: "Is there a place to adjust the render
 * preview options like how fractions are drawn?"), opened from the corner button on the Schematic and Final tabs'
 * preview box.
 *
 * Two groups, in the scope order the PDF options panels use:
 *  1. **The preview's own switches** (no heading under the sheet title), the same device preferences Settings →
 *     Editor Screen keeps (dimension lines and their "Dimension text size" chips, draw like the
 *     sheet, grid, selection highlight). The text-size chips grey while dimension lines are off,
 *     and the grid stands down while the preview draws like the sheet, so its switch greys with
 *     the reason.
 *  2. **Shared with the printed sheets** — the look settings the preview actually reads, each the
 *     exact control the PDF options panels and Settings → Drawing use, wired to the same setters,
 *     under the shared app-wide [OptionsScopeNote]: fractions, dimension arrows, the dual-unit
 *     layout (a dual-unit document only), Body S-break, the per-job Shaft height and the per-job
 *     Liner & taper compression (its "Keep liners and tapers proportional lengthwise" checkbox —
 *     both captions end "Saved with this job"). Remote controls for the one stored value — no new
 *     storage.
 *
 * The sheet is capped at [PREVIEW_OPTIONS_MAX_HEIGHT_FRAC] of the window and scrolls inside that,
 * over a light scrim, so the preview box at the top of the editor stays in view while its options
 * change — a full-height sheet hid the very drawing it restyles, and a half-expanded one grew to
 * full height on the first scroll (emulator QA). At EXPANDED width it opens as the Material
 * sheet's centred column under the same cap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPreviewOptionsSheet(
    state: EditorPreviewOptionsState,
    actions: EditorPreviewOptionsActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * PREVIEW_OPTIONS_MAX_HEIGHT_FRAC).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = PREVIEW_OPTIONS_SCRIM_ALPHA),
    ) {
        Box(Modifier.heightIn(max = maxHeight)) {
            EditorPreviewOptionsContent(state, actions)
        }
    }
}

/** The sheet's body — split out so a test can compose it without the modal window. */
@Composable
internal fun EditorPreviewOptionsContent(
    state: EditorPreviewOptionsState,
    actions: EditorPreviewOptionsActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PREVIEW_OPTIONS_SHEET_TAG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The title heads the preview's own switches; the "Shared with the printed sheets"
        // heading below is what marks the change of scope, so this group needs no heading.
        Text("Preview options", style = MaterialTheme.typography.titleLarge)

        SettingSwitchRow(
            checked = state.showDimensions,
            onCheckedChange = actions.onShowDimensions,
            modifier = Modifier.testTag(PREVIEW_OPT_DIMENSIONS_TAG),
        ) { Text("Dimension lines") }
        PreviewDimTextSizeChips(
            size = state.dimTextSize,
            onCommit = actions.onDimTextSize,
            enabled = state.showDimensions,
        )
        SettingSwitchRow(
            checked = state.sheetLayout,
            onCheckedChange = actions.onSheetLayout,
            modifier = Modifier.testTag(PREVIEW_OPT_SHEET_LAYOUT_TAG),
        ) {
            Column {
                Text("Draw like the sheet")
                Text(
                    PREVIEW_SHEET_LAYOUT_BLURB,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingSwitchRow(
            checked = state.showGrid,
            onCheckedChange = actions.onShowGrid,
            enabled = !state.sheetLayout,
            modifier = Modifier.testTag(PREVIEW_OPT_GRID_TAG),
        ) {
            Column {
                Text("Grid")
                if (state.sheetLayout) {
                    Text(
                        PREVIEW_GRID_OFF_REASON,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SettingSwitchRow(
            checked = state.highlightSelection,
            onCheckedChange = actions.onHighlightSelection,
            modifier = Modifier.testTag(PREVIEW_OPT_HIGHLIGHT_TAG),
        ) { Text("Highlight selected component") }

        HorizontalDivider()

        Column(
            modifier = Modifier.testTag(PREVIEW_OPT_SHARED_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Shared with the printed sheets", style = MaterialTheme.typography.titleMedium)
            OptionsScopeNote(hasJobControls = true)
            FractionStyleChips(
                fractionStyle = state.fractionStyle,
                onCommit = actions.onFractionStyle,
            )
            DimensionArrowSizeChips(
                arrowSizePt = state.arrowSizePt,
                onCommit = actions.onArrowSizePt,
            )
            if (state.dualUnits) {
                DualUnitLayoutChips(
                    layout = state.dualUnitLayout,
                    onCommit = actions.onDualUnitLayout,
                )
            }
            // The same rows the PDF options panels carry (on-device request: shading could be
            // switched from the PDF preview but not from here).
            Column(Modifier.testTag(PREVIEW_OPT_SHADING_TAG)) {
                Text("Shade in Components", style = MaterialTheme.typography.titleSmall)
                ShadeInPdfCheckRows(
                    pdfShadedBodies = state.shadedBodies,
                    pdfShadedTapers = state.shadedTapers,
                    pdfShadedLiners = state.shadedLiners,
                    shadeExplicitBodiesOnly = state.shadeExplicitBodiesOnly,
                    onSetShadedBodies = actions.onShadedBodies,
                    onSetShadedTapers = actions.onShadedTapers,
                    onSetShadedLiners = actions.onShadedLiners,
                    onSetShadeExplicitBodiesOnly = actions.onShadeExplicitBodiesOnly,
                )
            }
            if (!state.sheetLayout) {
                Text(
                    "Body S-break, Shaft height and Liner & taper compression show in the preview " +
                        "while it draws like the sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SBreakThresholdSlider(
                frac = state.sBreakThresholdFrac,
                onCommit = actions.onSBreakThresholdFrac,
                onDrag = actions.onSBreakDrag,
            )
            ShaftHeightSlider(
                heightScale = state.heightScale,
                baseScale = state.heightSliderBase,
                maxDiaMm = state.heightSliderMaxDiaMm,
                onCommit = actions.onHeightScale,
                onDrag = actions.onHeightDrag,
            )
            Box(Modifier.testTag(PREVIEW_OPT_LINER_COMPRESSION_TAG)) {
                LinerCompressionControl(
                    linersProportional = state.linersProportional,
                    linerCompression = state.linerCompression,
                    estimateKeptFrac = state.estimateLinerKeptFrac,
                    onSetProportional = actions.onLinersProportional,
                    onSetCompression = actions.onLinerCompression,
                    onDrag = actions.onLinerCompressionDrag,
                )
            }
        }
    }
}

/**
 * The editor preview's "Dimension text size: Small | Medium | Large" chips — ONE composable behind
 * the Preview options sheet and Settings → Editor Screen, both writing the one device preference
 * ([PreviewDimTextSize]). The preview's rails only: the printed sheets keep their own size.
 * [enabled] is false while the preview draws no dimension lines — the chips still show the stored
 * size, but there is nothing for them to restyle.
 */
@Composable
internal fun PreviewDimTextSizeChips(
    size: PreviewDimTextSize,
    onCommit: (PreviewDimTextSize) -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Text("Dimension text size", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        ChipFlowRow(spacing = 0.dp) {
            PreviewDimTextSize.entries.forEach { option ->
                FilterChip(
                    selected = size == option,
                    enabled = enabled,
                    onClick = { onCommit(option) },
                    label = { ChipLabel(option.label) },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag(previewDimTextChipTag(option)),
                )
            }
        }
        Text(
            "Size of the values on the preview's dimension lines. The printed sheets are not affected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
