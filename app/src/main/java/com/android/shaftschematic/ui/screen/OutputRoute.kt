package com.android.shaftschematic.ui.screen

import android.graphics.pdf.PdfDocument
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.defaultVisualScale
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.pdf.composeUndercutPdf
import com.android.shaftschematic.pdf.composeWearPdf
import com.android.shaftschematic.pdf.consolidatedSheetHasInProfileValues
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.nav.appVersionFromContext
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.createPdfInTree
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import com.android.shaftschematic.util.writeShaftPdfToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** Worn-section editor dialog target — [section] null means "add a new one". */
private data class OutputWornSectionTarget(val section: WornSection?)

/**
 * Everything one composed consolidated-sheet preview page depends on, in one
 * structural-equality value — the render loop's unit of work.
 *
 * The shade flags, the S-break threshold, the arrowhead size, and the tiering mode never
 * reach the composer from here: they travel inside the `PdfPrefs` snapshot taken at render
 * time. They are
 * held in this holder because the loop must RE-RENDER when they change, and a `PdfPrefs`
 * read is not snapshot state.
 */
private data class ConsolidatedRenderInputs(
    val showPreview: Boolean,
    val variant: ConsolidatedVariant,
    val spec: ShaftSpec,
    val config: RunoutConfig,
    val project: ProjectInfo,
    val unit: UnitSystem,
    val resolved: List<ResolvedComponent>,
    val lineThicknessScale: Float,
    val shadedBodies: Boolean,
    val shadedTapers: Boolean,
    val shadedLiners: Boolean,
    val sBreakThresholdFrac: Float,
    val arrowSizePt: Float,
    /** Not a composer argument — it reaches the ink via `FractionTypography.active`. Key only. */
    val fractionStyle: FractionStyle,
    val tieringMode: PdfTieringMode,
    val readings: RunoutReadings,
    val wearRecord: WearRecord,
    val blankValues: Boolean,
    /** A tuning slider is mid-drag: raster at draft resolution and hold the spinner back. */
    val draft: Boolean,
)

/**
 * OutputRoute — the Consolidated Output tab.
 *
 * The one-stop surface for the consolidated sheet: pick which information it carries
 * (`ConsolidatedVariant` — schematic rails + footer always on; runout bubbles and wear
 * info each electable), set the per-job "Shaft height" exaggeration, author worn
 * sections (they print here), and preview/print/export. The original tabs keep their own
 * single-document buttons; batch export of the full document set also lives on this tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
    /** Switch the editor to the Runout tab — the full bubble authoring surface. */
    onOpenRunoutTab: () -> Unit = {},
    /** Quick-save the document (prompts for a name when it has never been saved). */
    onSave: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
    val currentDocumentName by vm.currentDocumentName.collectAsState()
    val hasUnsavedChanges  by vm.hasUnsavedChanges.collectAsState()
    val runoutConfig       by vm.runoutConfig.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val unit               by vm.unit.collectAsState()
    val customer           by vm.customer.collectAsState()
    val vessel             by vm.vessel.collectAsState()
    val jobNumber          by vm.jobNumber.collectAsState()
    val shaftPosition      by vm.shaftPosition.collectAsState()
    val openAfterExport    by vm.openPdfAfterExport.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfShadedBodies    by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers    by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners    by vm.pdfShadedLiners.collectAsState()
    val curveLoHeightIn    by vm.pdfCurveLoHeightIn.collectAsState()
    val curveHiHeightIn    by vm.pdfCurveHiHeightIn.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    // Dimension-rail arrowhead size: a chip tap commits straight to PdfPrefs, so the render
    // loop needs it as an input key or the sheet would keep the old heads.
    val pdfArrowSizePt     by vm.pdfArrowSizePt.collectAsState()
    // Fraction style: same posture — it reaches the ink through the renderer's active style,
    // which the loop cannot observe, so it rides along as an input key.
    val pdfFractionStyle   by vm.pdfFractionStyle.collectAsState()
    val pdfTieringMode     by vm.pdfTieringMode.collectAsState()
    val runoutReadings     by vm.runoutReadings.collectAsState()
    val wearRecord         by vm.wearRecord.collectAsState()
    val undercutRecord     by vm.undercutRecord.collectAsState()
    val exportMode         by vm.pdfExportMode.collectAsState()
    // Whether a blank schematic in the batch carries Ø leaders — one preference for every
    // surface that prints a blank schematic (see PdfExportOptions.showDiaCallouts).
    val pdfBlankDiaCallouts by vm.pdfBlankDiaCallouts.collectAsState()

    val ctx = LocalContext.current
    // Footer stamp for the schematic document in the batch — the same version string the
    // Schematic tab's own export prints.
    val appVersion = remember(ctx) { appVersionFromContext(ctx) }

    // Station-count rows for the in-place bubble editor — same builder the Runout tab uses.
    val stationEntries = remember(spec, resolvedComponents) {
        buildRunoutStationEntries(spec, resolvedComponents)
    }

    // Sheet-content election. Session-scoped, resets to the full sheet — an inspection
    // normally wants everything; a sticky partial pick would silently print less later.
    var variant by rememberSaveable { mutableStateOf(ConsolidatedVariant.ALL) }
    var blankDraft by rememberSaveable { mutableStateOf(false) }
    // Collapsed by default — the per-component station rows are an occasional tweak.
    var bubblesExpanded by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // Where the composed sheet carries ink — the tuning strip crops to it so blank paper
    // never takes room from the drawing. Measured on sharp passes only (see the loop).
    var previewInkBand by remember { mutableStateOf<InkBand?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var wornSectionDialog by remember { mutableStateOf<OutputWornSectionTarget?>(null) }
    // Live slider drags — on this tab's own Shaft height / Liner compression controls and
    // on the preview's options sheet. Visual-only overrides, never a write, so a drag
    // frame can't mark the job dirty. See [PreviewTuning].
    val tuning = rememberPreviewTuning()

    // "Export all" election, one flag per OutputDoc in enum order. Everything on by default:
    // the normal hand-off is the complete document package, and an unchecked box is a
    // deliberate omission the user re-makes each session.
    var batchChecked by rememberSaveable { mutableStateOf(List(OutputDoc.entries.size) { true }) }
    var batchResult by remember { mutableStateOf<String?>(null) }

    // The consolidated sheet embeds the schematic's dimensions — the schematic's export
    // gate (components exist, no collisions) governs this whole surface.
    val collidingIds = remember(spec) { spec.collidingIds() }
    val gate = remember(spec, collidingIds) { exportPdfGate(spec, collidingIds) }

    // S.E.T. positions for the worn-section editor's Distance datums — the same source as
    // the composer's SET-to-SET window.
    val setPositions = remember(spec) { computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec) }
    val aftSetXMm = setPositions.aftSETxMm.toFloat()
    val fwdSetXMm = setPositions.fwdSETxMm.toFloat()

    // Height slider inputs: the base here approximates the composer's solve (width-fit
    // vs the default sizing curve at the configured anchor heights; the in-profile value
    // demand and the page budget are ignored — both only ever LOWER the drawn height a
    // hair in extreme layouts, never raise it).
    val heightSliderDiaMm = remember(spec) { spec.maxOuterDiaMm().coerceAtLeast(10f) }
    val heightSliderBase = remember(spec, curveLoHeightIn, curveHiHeightIn) {
        val spanMm = (setPositions.fwdSETxMm - setPositions.aftSETxMm).toFloat().coerceAtLeast(1f)
        val contentW = 792f - 72f
        maxOf(contentW / spanMm, defaultVisualScale(heightSliderDiaMm, curveLoHeightIn * 72f, curveHiHeightIn * 72f))
    }

    // Liner shading is unavailable exactly when this sheet prints measured Ø values inside
    // the profile — the composer draws liners unfilled there so the sheet-white value halos
    // never read as pasted boxes. Same predicate, same inputs the composer gets (elected
    // wear info, blank-draft flag, the resolved ids a reading must key to), so the checkbox
    // always matches what gets drawn.
    val linerShadeLocked = remember(wearRecord, resolvedComponents, variant, blankDraft) {
        consolidatedSheetHasInProfileValues(
            wornSections = wearRecord.wornSections,
            diaReadings = wearRecord.diaReadings,
            resolvedComponentIds = resolvedComponents.map { it.id }.toSet(),
            includeWearInfo = variant.includeWearInfo,
            blankValues = blankDraft,
        )
    }

    val outputFilename = buildOutputFilename(customer, vessel, jobNumber, OutputDoc.CONSOLIDATED, blankDraft)

    /**
     * The consolidated sheet with the elected content. Everything arrives as a parameter
     * because the print adapter's onWrite runs on a binder thread and its caller must
     * snapshot state on the UI thread first.
     */
    fun composeConsolidated(
        page: PdfDocument.Page,
        variantSnap: ConsolidatedVariant,
        specSnap: ShaftSpec,
        configSnap: RunoutConfig,
        projectSnap: ProjectInfo,
        unitSnap: UnitSystem,
        prefsSnap: PdfPrefs,
        resolvedSnap: List<ResolvedComponent>,
        thicknessSnap: Float,
        readingsSnap: RunoutReadings,
        wearSnap: WearRecord,
        blankSnap: Boolean,
    ) = composeRunoutPdf(
        page = page, spec = specSnap, config = configSnap, project = projectSnap,
        unit = unitSnap,
        pdfPrefs = prefsSnap,
        resolvedComponents = resolvedSnap,
        lineThicknessScale = thicknessSnap,
        runoutReadings = readingsSnap,
        wearRecord = wearSnap,
        blankValues = blankSnap,
        consolidated = true,
        includeBubbles = variantSnap.includeBubbles,
        includeWearInfo = variantSnap.includeWearInfo,
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            // Hardened write (util/PdfSafExport.kt): a composer throw yields a valid
            // error page, never a truncated file.
            val wrote = writeShaftPdfToUri(ctx, uri) { page ->
                composeConsolidated(
                    page, variant, spec, runoutConfig,
                    ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition),
                    unit, vm.currentPdfPrefs, resolvedComponents,
                    lineThicknessScale, runoutReadings, wearRecord, blankDraft,
                )
            }
            if (wrote && openAfterExport) openRunoutPdf(ctx, uri)
        }
    }

    /**
     * "Export all" — write every checked document into one folder picked via
     * OpenDocumentTree. Each file goes through the same hardened write as the single-document
     * buttons, so a composer throw costs one document (an error page inside that file), never
     * the batch. Nothing auto-opens: a batch drops a folder of files, not one thing to read.
     */
    val batchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val project = ProjectInfo(customer = customer, vessel = vessel,
                jobNumber = jobNumber, side = shaftPosition)
            val prefs = vm.currentPdfPrefs
            var attempted = 0
            var wroteCount = 0
            OutputDoc.entries.forEachIndexed { i, doc ->
                if (!batchChecked.getOrElse(i) { true }) return@forEachIndexed
                attempted++
                val name = buildOutputFilename(customer, vessel, jobNumber, doc, blankDraft)
                // A null uri means the provider refused to create the file — counted as a
                // failure, but there is no document to carry an error page.
                val docUri = createPdfInTree(ctx, treeUri, name) ?: return@forEachIndexed
                val ok = writeShaftPdfToUri(ctx, docUri) { page ->
                    when (doc) {
                        OutputDoc.CONSOLIDATED -> composeConsolidated(
                            page, variant, spec, runoutConfig, project, unit, prefs,
                            resolvedComponents, lineThicknessScale, runoutReadings,
                            wearRecord, blankDraft,
                        )
                        OutputDoc.RUNOUT -> composeRunoutPdf(
                            page = page, spec = spec, config = runoutConfig, project = project,
                            unit = unit, pdfPrefs = prefs,
                            resolvedComponents = resolvedComponents,
                            lineThicknessScale = lineThicknessScale,
                            runoutReadings = runoutReadings,
                            blankValues = blankDraft,
                            consolidated = false,
                        )
                        OutputDoc.SCHEMATIC -> composeShaftPdf(
                            page = page, spec = spec, unit = unit, project = project,
                            appVersion = appVersion, filename = name, pdfPrefs = prefs,
                            options = PdfExportOptions(
                                mode = exportMode,
                                blankValues = blankDraft,
                                blankDiaCallouts = pdfBlankDiaCallouts,
                            ),
                            resolvedComponents = resolvedComponents,
                            lineThicknessScale = lineThicknessScale,
                            heightScale = runoutConfig.heightScale,
                            linerMinFracOfTrue = runoutConfig.linerMinFracOfTrue,
                        )
                        OutputDoc.WEAR -> composeWearPdf(
                            page = page, spec = spec, project = project, unit = unit,
                            pdfPrefs = prefs, resolvedComponents = resolvedComponents,
                            wearRecord = wearRecord,
                            lineThicknessScale = lineThicknessScale,
                            blankValues = blankDraft,
                        )
                        OutputDoc.UNDERCUT -> composeUndercutPdf(
                            page = page, spec = spec, project = project, unit = unit,
                            pdfPrefs = prefs, resolvedComponents = resolvedComponents,
                            undercutRecord = undercutRecord,
                            lineThicknessScale = lineThicknessScale,
                            blankValues = blankDraft,
                        )
                    }
                }
                if (ok) wroteCount++
            }
            val failed = attempted - wroteCount
            batchResult = if (failed == 0) {
                "$wroteCount of $attempted PDFs written."
            } else {
                "$wroteCount of $attempted written — $failed failed " +
                    "(see the error page inside each failed file)."
            }
        }
    }

    // The render loop. One RenderInputs value carries EVERYTHING the composed sheet reads
    // (an omission here is a stale-preview bug); `snapshotFlow { … }.conflate()` renders
    // the newest inputs and drops the intermediate frames a slider drag produces while a
    // render is in flight. When a drag ends the overrides go null, the inputs change once
    // more, and that final pass rasterizes at full resolution.
    LaunchedEffect(Unit) {
        var previousWasDraft = false
        snapshotFlow {
            ConsolidatedRenderInputs(
                showPreview = showPreview,
                variant = variant,
                spec = spec,
                config = tunedRunoutConfig(runoutConfig, tuning.heightScale, tuning.linerCompression),
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                resolved = resolvedComponents,
                lineThicknessScale = tuning.lineThickness ?: lineThicknessScale,
                shadedBodies = pdfShadedBodies,
                shadedTapers = pdfShadedTapers,
                shadedLiners = pdfShadedLiners,
                sBreakThresholdFrac = tuning.sBreakFrac ?: pdfSBreakThresholdFrac,
                arrowSizePt = pdfArrowSizePt,
                fractionStyle = pdfFractionStyle,
                tieringMode = pdfTieringMode,
                readings = runoutReadings,
                wearRecord = wearRecord,
                blankValues = blankDraft,
                draft = tuning.active,
            )
        }.conflate().collect { inputs ->
            if (!inputs.showPreview) {
                previewBitmap = null
                previousWasDraft = false
                return@collect
            }
            // A drag frame — and the sharp pass right after one — keeps the current page on
            // screen: swapping in the spinner between frames would strobe the preview.
            val quiet = inputs.draft || previousWasDraft
            previousWasDraft = inputs.draft
            if (!quiet) previewLoading = true
            val prefsSnapshot = tunedPdfPrefs(vm.currentPdfPrefs, inputs.sBreakThresholdFrac)
            // The ink band is measured on the raw raster, and only on a sharp (non-draft)
            // pass: a drag frame that resized the strip — and with it the sheet cap —
            // would shuffle the layout under a moving finger.
            val (bmp, band) = withContext(Dispatchers.IO) {
                val raster = renderPdfPageBitmap(
                    ctx,
                    renderScale = previewRenderScale(inputs.draft),
                ) { page ->
                    composeConsolidated(
                        page, inputs.variant, inputs.spec, inputs.config, inputs.project,
                        inputs.unit, prefsSnapshot, inputs.resolved,
                        inputs.lineThicknessScale, inputs.readings, inputs.wearRecord,
                        inputs.blankValues,
                    )
                }
                raster to raster?.takeIf { !inputs.draft }?.inkBand()
            }
            previewBitmap = bmp?.asImageBitmap()
            if (!inputs.draft) previewInkBand = band
            previewLoading = false
        }
    }

    // ── Screen ────────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // ── Document title strip ─────────────────────────────────────────────
        // Worn sections, the height slider, and the content election are all per-job
        // state, so the asterisk belongs on this tab too.
        EditorDocumentTitle(
            documentName = currentDocumentName,
            hasUnsavedChanges = hasUnsavedChanges,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSidebar) {
                Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
            }
            Text(
                text = "Consolidated Output",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSave,
                modifier = Modifier.testTag("toolbar_save"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Sheet content ────────────────────────────────────────────────
            Text("Sheet content", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConsolidatedVariant.entries.forEach { v ->
                    FilterChip(
                        selected = variant == v,
                        onClick = { variant = v },
                        label = { Text(v.label) },
                    )
                }
            }
            Text(
                "The consolidated sheet always carries the schematic's dimensions and spec " +
                    "footer; pick whether it features the runouts, the wear record, or both. " +
                    "The Schematic, Runout, Wear, and Undercut tabs still export their own " +
                    "standalone documents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Blank draft + Preview / Print / Export — right under the election ──
            // The output actions are the tab's first go-to (on-device report: "everything
            // else is tweaking the output"), so the group sits directly under the content
            // election it acts on; the sliders, station rows and worn sections follow.
            // The blank-draft toggle stays with the buttons it modifies.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info, dimensions, and recorded values are blanked for handwriting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!gate.enabled) {
                Text(
                    gate.disabledMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = { showPreview = true },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Consolidated Sheet")
            }

            OutlinedButton(
                onClick = {
                    val jobName = outputFilename.removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
                    val variantSnapshot = variant
                    val specSnapshot = spec
                    val configSnapshot = runoutConfig
                    val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition)
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = resolvedComponents
                    val thicknessSnapshot = lineThicknessScale
                    val readingsSnapshot = runoutReadings
                    val wearSnapshot = wearRecord
                    val blankSnapshot = blankDraft
                    printShaftPdfPage(ctx, jobName) { page ->
                        composeConsolidated(
                            page, variantSnapshot, specSnapshot, configSnapshot,
                            projectSnapshot, unitSnapshot, prefsSnapshot, resolvedSnapshot,
                            thicknessSnapshot, readingsSnapshot, wearSnapshot, blankSnapshot,
                        )
                    }
                },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Consolidated Sheet")
            }

            Button(
                onClick = { launcher.launch(outputFilename) },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Consolidated Sheet PDF")
            }

            HorizontalDivider()

            // ── Runout bubbles ───────────────────────────────────────────────
            // The bubbles print on THIS sheet, so their counts are editable here (on-device
            // report: adjusting one meant leaving the tab). Same rows, same overrides, same
            // composable as the Runout tab — the two surfaces cannot drift. COLLAPSED by
            // default: the per-component rows are an occasional tweak, and a wall of them at
            // the top of the tab buried everything else (on-device report). The header row
            // toggles the rows; the button beside it still opens the Runout tab for the full
            // authoring surface (TIR values, high spots, the standalone sheet).
            if (variant.includeBubbles && stationEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { bubblesExpanded = !bubblesExpanded }
                            .testTag("output_bubbles_expander"),
                    ) {
                        Icon(
                            if (bubblesExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (bubblesExpanded) "Collapse" else "Expand",
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Runout bubbles", style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(
                        onClick = onOpenRunoutTab,
                        modifier = Modifier.testTag("output_open_runout_tab"),
                    ) { Text("Runout sheet →") }
                }
                if (bubblesExpanded) {
                    RunoutStationCountEditor(
                        entries = stationEntries,
                        overrides = runoutConfig.componentOverrides,
                        onSetCount = { id, count -> vm.setRunoutBubbleCount(id, count) },
                        showHeading = false,
                    )
                    Text(
                        "One station per 20\" by default. Tap a bubble on the Runout sheet " +
                            "to enter its TIR reading and high spot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Shaft height (per-job, shared with the schematic PDF) ────────
            ShaftHeightSlider(
                heightScale = runoutConfig.heightScale,
                baseScale = heightSliderBase,
                maxDiaMm = heightSliderDiaMm,
                onCommit = { vm.setRunoutHeightScale(it) },
                onDrag = { tuning.heightScale = it },
            )

            LinerCompressionControl(
                linersProportional = runoutConfig.linersProportional,
                linerCompression = runoutConfig.linerCompression,
                estimateKeptFrac = { frac ->
                    estimatedLinerKeptFracOfTrue(spec, heightSliderBase, runoutConfig.heightScale, frac)
                },
                onSetProportional = { vm.setLinersProportional(it) },
                onSetCompression = { vm.setLinerCompression(it) },
                onDrag = { tuning.linerCompression = it },
            )

            // ── Worn sections — authored here, printed on this sheet ─────────
            Text("Worn sections", style = MaterialTheme.typography.titleSmall)
            Text(
                "Designate a measured area — its Ø readings print inside the shaft profile, " +
                    "and no lines draw through the numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wearRecord.wornSections.forEachIndexed { i, section ->
                WornSectionRow(
                    index = i + 1,
                    section = section,
                    unit = unit,
                    aftSetXMm = aftSetXMm,
                    fwdSetXMm = fwdSetXMm,
                    onClick = { wornSectionDialog = OutputWornSectionTarget(section) },
                )
            }
            OutlinedButton(onClick = { wornSectionDialog = OutputWornSectionTarget(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add worn section")
            }

            // ── Export all ───────────────────────────────────────────────────
            HorizontalDivider()

            Text("Export all", style = MaterialTheme.typography.titleSmall)
            Text(
                "Write every checked document to a folder in one step. The consolidated " +
                    "sheet uses the content selection above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutputDoc.entries.forEachIndexed { i, doc ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = batchChecked.getOrElse(i) { true },
                        onCheckedChange = { on ->
                            batchChecked = batchChecked.toMutableList().also { it[i] = on }
                            batchResult = null
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(doc.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            doc.docName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = { batchResult = null; batchLauncher.launch(null) },
                enabled = gate.enabled && batchChecked.any { it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export all to folder…")
            }

            batchResult?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ── Worn-section editor dialog ────────────────────────────────────────────
    wornSectionDialog?.let { target ->
        WornSectionDialog(
            existing = target.section,
            unit = unit,
            aftSetXMm = aftSetXMm,
            fwdSetXMm = fwdSetXMm,
            onSave = { startFromAftMm, lengthMm, values, reference ->
                val ex = target.section
                if (ex == null) {
                    vm.addWornSection(startFromAftMm, lengthMm, values, reference)
                } else {
                    vm.updateWornSection(ex.id, startFromAftMm, lengthMm, values)
                    if (ex.authoredReference != reference) {
                        vm.updateWornSectionReference(ex.id, reference)
                    }
                }
                wornSectionDialog = null
            },
            onDelete = target.section?.let { s ->
                { vm.removeWornSection(s.id); wornSectionDialog = null }
            },
            onDismiss = { wornSectionDialog = null },
        )
    }

    // ── Full-screen preview overlay ───────────────────────────────────────────
    BackHandler(enabled = showPreview) { showPreview = false }
    if (showPreview) {
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Consolidated Sheet Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(outputFilename)
            },
            optionsSheet = {
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    vm = vm,
                    linerShadeLocked = linerShadeLocked,
                    showSBreak = true,
                    sBreakThresholdFrac = pdfSBreakThresholdFrac,
                    showDimensionArrows = true,
                    arrowSizePt = pdfArrowSizePt,
                    fractionStyle = pdfFractionStyle,
                    tuning = tuning,
                    blankDraft = blankDraft,
                    onSetBlankDraft = { blankDraft = it },
                    showHeightControls = true,
                    heightScale = runoutConfig.heightScale,
                    heightSliderBase = heightSliderBase,
                    heightSliderMaxDiaMm = heightSliderDiaMm,
                    linersProportional = runoutConfig.linersProportional,
                    linerCompression = runoutConfig.linerCompression,
                    estimateKeptFrac = { frac ->
                        estimatedLinerKeptFracOfTrue(spec, heightSliderBase, runoutConfig.heightScale, frac)
                    },
                    showMeasurementReference = true,
                    pdfTieringMode = pdfTieringMode,
                )
            },
            sheetTunesPage = true,
            inkBand = previewInkBand,
        )
    }
}
