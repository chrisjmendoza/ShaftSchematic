package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.effectiveWearTraceDepthFrac
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.pdf.composeWearPdf
import com.android.shaftschematic.pdf.buildWearStripTitleById
import com.android.shaftschematic.pdf.defaultWearStripComponentIds
import com.android.shaftschematic.pdf.wearStripComponentsFor
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.maxDiaMm
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import com.android.shaftschematic.util.writeShaftPdfToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WearRoute
 *
 * Screen for the Shaft Wear / Inspection Document tab.
 *
 * ## Purpose
 * The authoring surface for wear data — wear spots, pits, and measured-Ø readings are
 * placed in-app here and print at their true position on the document. Blank draft mode
 * prints the same drawing as a write-in form, for marking damage, pitting, and
 * dye-penetrant results by hand on the page.
 *
 * ## Layout
 * - **Interactive shaft canvas** (`docs/LinerWearAreas_Proposal.md`) — same pattern as
 *   `RunoutRoute`'s preview canvas: `ShaftLayout.compute` + `ShaftRenderer.draw` against
 *   `resolvedComponents` (never raw spec). Liners are tap targets (faint tint affordance); a
 *   tap hit-tests in mm space via [ShaftLayout.Result.xMmFromPx] + [pickLinerIdAtMm] and opens
 *   [LinerWearDetailOverlay] for the tapped liner. Liners with recorded wear spots show a small
 *   count badge above them.
 * - **Preview PDF** — verify layout before saving.
 * - **Export PDF** — SAF file picker to save the file.
 *
 * ## Liner detail overlay
 * [LinerWearDetailOverlay] — full-screen "zoom in" on one liner: broken-out liner with neighbor
 * stubs, wear bands, and editable spot cards. Not a nav destination; dismissed via its own
 * `BackHandler` or back-arrow button.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun WearRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
    /** Quick-save the document (prompts for a name when it has never been saved). */
    onSave: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
    val currentDocumentName by vm.currentDocumentName.collectAsState()
    val hasUnsavedChanges  by vm.hasUnsavedChanges.collectAsState()
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
    val pdfFractionStyle   by vm.pdfFractionStyle.collectAsState()
    val wearRecord         by vm.wearRecord.collectAsState()
    val wearTraceDefault   by vm.pdfWearTraceDepthFrac.collectAsState()
    val wearBandShadeFrac  by vm.pdfWearBandShadeFrac.collectAsState()

    // The ONE resolution of this job's trace-depth override against the Settings default. Every
    // consumer on this tab — the slider, the detail overlay's canvas, and every composeWearPdf
    // call below — reads this same value, which is what keeps the two draw sites identical.
    val traceDepthFrac = effectiveWearTraceDepthFrac(wearRecord.traceDepthFrac, wearTraceDefault)

    // Strip election rows for the PDF options sheet: the eligible components in AFT→FWD order and
    // the default election (every drawable liner) the first toggle materializes.
    val stripOptions = remember(spec, resolvedComponents) {
        buildWearStripComponentOptions(spec, resolvedComponents)
    }
    val stripDefaultIds = remember(spec.liners) { defaultWearStripComponentIds(spec.liners) }

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    // Blank-draft (write-in) copy: outline + form layout, all values blanked for handwriting.
    var blankDraft by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // Which component's wear-detail overlay is open, if any. Doubles as the overlay's visibility
    // flag. A body, taper, or liner id (all pit-eligible); see ComponentWearDetailOverlay.
    var selectedComponentId by rememberSaveable { mutableStateOf<String?>(null) }

    // Collisions corrupt any drawing, so the shared export gate guards this surface too —
    // the same posture as the runout and schematic surfaces.
    val collidingIds = remember(spec) { spec.collidingIds() }
    val gate = remember(spec, collidingIds) { exportPdfGate(spec, collidingIds) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            // Hardened write: a composer throw yields a valid error page, never a
            // truncated file (util/PdfSafExport.kt — one implementation for every tab).
            val wrote = writeShaftPdfToUri(ctx, uri) { page ->
                composeWearPdf(
                    page = page, spec = spec,
                    project = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition),
                    unit = unit,
                    pdfPrefs = vm.currentPdfPrefs,
                    resolvedComponents = resolvedComponents,
                    lineThicknessScale = lineThicknessScale,
                    wearRecord = wearRecord,
                    blankValues = blankDraft,
                    traceDepthFrac = traceDepthFrac,
                )
            }
            if (wrote && openAfterExport) openWearPdf(ctx, uri)
        }
    }

    // pdfFractionStyle is a key only: the style reaches the ink through
    // FractionTypography.active, which is not snapshot state — without the key a style
    // change would leave the rasterized preview drawing the old construction. traceDepthFrac
    // is a key for the other half of its pair: a job's own override rides `wearRecord`, but a
    // change to the Settings default reaches an un-overridden document only through this.
    // wearBandShadeFrac is a key for the same reason: the composer reads it off the PdfPrefs
    // snapshot taken inside this effect, which is not snapshot state either.
    LaunchedEffect(showPreview, spec, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   wearRecord, blankDraft, pdfFractionStyle, traceDepthFrac, wearBandShadeFrac) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot     = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
            jobNumber = jobNumber, side = shaftPosition)
        val bmp = withContext(Dispatchers.IO) {
            renderPdfPageBitmap(ctx) { page ->
                composeWearPdf(
                    page = page, spec = spec, project = projectSnapshot, unit = unit,
                    pdfPrefs = prefsSnapshot, resolvedComponents = resolvedComponents,
                    lineThicknessScale = thicknessSnapshot, wearRecord = wearRecord,
                    blankValues = blankDraft, traceDepthFrac = traceDepthFrac,
                )
            }
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture colors before the Canvas block (DrawScope is not composable) — same technique
    // as RunoutRoute's live preview. Sheet ink is FIXED (SheetInk), never theme onSurface:
    // the canvas is a paper-white sheet in every theme, and dark theme's near-white
    // onSurface would print invisible ink on it.
    val outlineArgb    = SheetInk.Outline.toArgb()
    val bodyFillArgb   = SheetInk.Outline.copy(alpha = 0.08f).toArgb()
    val hatchArgb      = SheetInk.Outline.copy(alpha = 0.55f).toArgb()
    val tapTintColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val tapBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val badgeColor     = MaterialTheme.colorScheme.primary
    val badgeTextArgb  = MaterialTheme.colorScheme.onPrimary.toArgb()
    val previewShape   = MaterialTheme.shapes.medium

    val transparentArgb = Color.Transparent.toArgb()
    val previewOpts = remember(outlineArgb, bodyFillArgb, hatchArgb) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = bodyFillArgb,
            taperFillColor      = bodyFillArgb,
            linerFillColor      = transparentArgb, // liner tint drawn separately as tap affordance
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
        )
    }

    // ── Main UI ─────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        // ── Document title strip ─────────────────────────────────────────────
        // Placing a wear spot, pit, or Ø reading here is unsaved work exactly like a
        // spec edit, so the asterisk belongs on this tab too.
        EditorDocumentTitle(
            documentName = currentDocumentName,
            hasUnsavedChanges = hasUnsavedChanges,
        )

        // ── Toolbar ──────────────────────────────────────────────────────────
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
                text = "Wear Document",
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

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Prints a blank shaft outline for field use. Mark damage, pitting, and " +
                    "dye-penetrant inspection results directly on the printed form.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Interactive shaft canvas — tap a liner to inspect wear (Phase 2) ─────
            if (spec.overallLengthMm > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .pointerInput(spec, resolvedComponents) {
                            detectTapGestures { tapOffset ->
                                val layout = ShaftLayout.compute(
                                    spec               = spec,
                                    leftPx             = 0f,
                                    topPx              = 0f,
                                    rightPx            = size.width.toFloat(),
                                    bottomPx           = size.height.toFloat(),
                                    marginPx           = 12.dp.toPx(),
                                    resolvedComponents = resolvedComponents,
                                )
                                val tapMm = layout.xMmFromPx(tapOffset.x)
                                // Pit-eligible resolved components (body / taper / liner) have
                                // disjoint spans, so the first one containing the tap wins.
                                resolvedComponents.firstOrNull { rc ->
                                    (rc is ResolvedBody || rc is ResolvedTaper || rc is ResolvedLiner) &&
                                        tapMm >= rc.startMmPhysical && tapMm <= rc.endMmPhysical
                                }?.let { selectedComponentId = it.id }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val marginPx = 12.dp.toPx()
                        val layout = ShaftLayout.compute(
                            spec               = spec,
                            leftPx             = 0f,
                            topPx              = 0f,
                            rightPx            = size.width,
                            bottomPx           = size.height,
                            marginPx           = marginPx,
                            resolvedComponents = resolvedComponents,
                        )
                        with(ShaftRenderer) {
                            draw(spec, layout, previewOpts, resolvedComponents)
                        }
                        drawWearAffordances(
                            layout = layout,
                            components = resolvedComponents,
                            wearRecord = wearRecord,
                            tapTintColor = tapTintColor,
                            tapBorderColor = tapBorderColor,
                            badgeColor = badgeColor,
                            badgeTextArgb = badgeTextArgb,
                        )
                    }
                }
                Text(
                    text = "Tap a body, taper, or liner to inspect wear and mark pits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Worn-profile trace depth ──────────────────────────────────────
            // Sits under the shaft it restyles (the undercut sheet's exaggeration slider
            // posture). The wear preview's PDF options sheet carries this same row, from
            // the one construction, so the two surfaces can never drift.
            WearTraceDepthControlRow(
                vm = vm,
                effectiveFrac = traceDepthFrac,
                globalDefault = wearTraceDefault,
            )

            // ── Dye pen inspection result ─────────────────────────────────────
            // Selecting a chip prints an "X" inside that PASS/FAIL checkbox on the sheet's
            // notes row; the other box stays present and blank. Tapping the selected chip
            // deselects it, returning both boxes to blank for hand-marking (the original
            // form posture, and what a blank draft always prints).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Dye pen inspection:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val result = wearRecord.dyePenResult
                WearChip("Pass", result == DyePenResult.PASS) {
                    vm.setDyePenResult(if (result == DyePenResult.PASS) null else DyePenResult.PASS)
                }
                WearChip("Fail", result == DyePenResult.FAIL) {
                    vm.setDyePenResult(if (result == DyePenResult.FAIL) null else DyePenResult.FAIL)
                }
            }

            // ── Blank draft toggle ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info and OAL are blanked; recorded wear is omitted — a fresh form.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Export gate ───────────────────────────────────────────────────
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
                Text("Preview Wear Document")
            }

            // ── Print button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val jobName = buildWearFilename(customer, vessel, jobNumber, blankDraft)
                        .removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
                    val specSnapshot = spec
                    val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition)
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = resolvedComponents
                    val thicknessSnapshot = lineThicknessScale
                    val recordSnapshot = wearRecord
                    val blankSnapshot = blankDraft
                    val traceDepthSnapshot = traceDepthFrac
                    printShaftPdfPage(ctx, jobName) { page ->
                        composeWearPdf(
                            page = page, spec = specSnapshot,
                            project = projectSnapshot, unit = unitSnapshot,
                            pdfPrefs = prefsSnapshot,
                            resolvedComponents = resolvedSnapshot,
                            lineThicknessScale = thicknessSnapshot,
                            wearRecord = recordSnapshot,
                            blankValues = blankSnapshot,
                            traceDepthFrac = traceDepthSnapshot,
                        )
                    }
                },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Wear Document")
            }

            Button(
                onClick = { launcher.launch(buildWearFilename(customer, vessel, jobNumber, blankDraft)) },
                enabled = gate.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Wear Document PDF")
            }
        }
    }

    BackHandler(enabled = showPreview) { showPreview = false }
    if (showPreview) {
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Wear Document Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(buildWearFilename(customer, vessel, jobNumber, blankDraft))
            },
            optionsSheet = {
                // The sheet tunes the drawing being looked at (on-device request): the same
                // blank-draft switch as the tab body (ONE state, so the two always agree) and
                // the wear block — trace depth + wear-area shade.
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    vm = vm,
                    fractionStyle = pdfFractionStyle,
                    blankDraft = blankDraft,
                    onSetBlankDraft = { blankDraft = it },
                    showWearControls = true,
                    traceDepthFrac = traceDepthFrac,
                    traceDepthDefault = wearTraceDefault,
                    wearBandShadeFrac = wearBandShadeFrac,
                    wearStripOptions = stripOptions,
                    wearStripSelection = wearRecord.stripComponentIds,
                    wearStripDefaultIds = stripDefaultIds,
                    wearShowShaftProfile = wearRecord.showShaftProfile,
                )
            },
        )
    }

    // ── Component wear detail overlay ─────────────────────────────────────────
    // ComponentWearDetailOverlay hosts its own BackHandler; only compose it while selected.
    selectedComponentId?.let { componentId ->
        ComponentWearDetailOverlay(
            componentId = componentId,
            spec = spec,
            resolvedComponents = resolvedComponents,
            unit = unit,
            wearRecord = wearRecord,
            onAddSpot = vm::addWearSpot,
            onUpdateSpot = vm::updateWearSpot,
            onUpdateSpotReference = vm::updateWearSpotReference,
            onRemoveSpot = vm::removeWearSpot,
            onAddPit = vm::addWearPit,
            onRemovePit = vm::removeWearPit,
            onAddDiaReading = vm::addWearDiaReading,
            onUpdateDiaReading = vm::updateWearDiaReading,
            onRemoveDiaReading = vm::removeWearDiaReading,
            onClose = { selectedComponentId = null },
            traceDepthFrac = traceDepthFrac,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Strip election — which components print a broken-out detail strip
// ─────────────────────────────────────────────────────────────────────────────

/** One strip-eligible component as the options sheet lists it: resolved id + printed title. */
internal data class WearStripComponentOption(val id: String, val label: String)

/**
 * Strip-eligible resolved components (liner, taper, body — explicit or auto) in AFT→FWD order,
 * labelled by the SAME shared builders the printed strips use (`pdf/WearStripComponents.kt`),
 * so a checkbox and the strip it elects always read the same name. Degenerate components are
 * left out: they can't be drawn, so electing one would only claim an empty cell.
 */
internal fun buildWearStripComponentOptions(
    spec: com.android.shaftschematic.model.ShaftSpec,
    components: List<ResolvedComponent>,
): List<WearStripComponentOption> {
    val eligible = wearStripComponentsFor(spec, components).filter { it.drawable }
    val titles = buildWearStripTitleById(spec, eligible)
    return eligible.map { WearStripComponentOption(it.id, titles[it.id] ?: "Component") }
}

/**
 * The wear sheet's "Components" section: the whole-shaft profile toggle plus one checkbox per
 * strip-eligible component. [selection] is the job's authored election
 * (`WearRecord.stripComponentIds`); `null` shows the default — every drawable liner ticked,
 * everything else clear. The first component toggle materializes [defaultIds] and then applies
 * the change, so a liner added later can never silently rewrite an authored sheet.
 *
 * Ids the current geometry no longer offers stay in the stored list untouched (they simply have
 * no row here and are skipped when the sheet draws) — the render-layer orphan rule.
 */
@Composable
internal fun WearStripComponentChecks(
    options: List<WearStripComponentOption>,
    selection: List<String>?,
    defaultIds: List<String>,
    showShaftProfile: Boolean,
    onSetShowShaftProfile: (Boolean) -> Unit,
    onSetSelection: (List<String>) -> Unit,
) {
    Column {
        Text("Components", style = MaterialTheme.typography.titleSmall)
        Text(
            "What this sheet draws. Hiding the complete shaft gives its height to the " +
                "detail strips.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = showShaftProfile,
                onCheckedChange = onSetShowShaftProfile,
                modifier = Modifier.testTag("wear_strip_complete_shaft"),
            )
            Spacer(Modifier.width(8.dp))
            Text("Complete shaft", style = MaterialTheme.typography.bodyLarge)
        }

        val elected = (selection ?: defaultIds).toSet()
        options.forEach { opt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = opt.id in elected,
                    onCheckedChange = { checked ->
                        val base = selection ?: defaultIds
                        val next = if (checked) (base + opt.id).toSet() else (base - opt.id).toSet()
                        // Store in sheet order (AFT→FWD), keeping any elected id this geometry
                        // no longer offers rather than pruning it.
                        val known = options.map { it.id }
                        onSetSelection(
                            known.filter { it in next } + base.filter { it in next && it !in known }
                        )
                    },
                    modifier = Modifier.testTag("wear_strip_component_${opt.id}"),
                )
                Spacer(Modifier.width(8.dp))
                Text(opt.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun buildWearFilename(
    customer: String,
    vessel: String,
    jobNumber: String,
    blankDraft: Boolean = false,
): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "WearDocument"}_wear$blankSuffix.pdf"
}

private fun openWearPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching { context.grantUriPermission(ri.activityInfo?.packageName ?: return@forEach, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Wear tap-affordance overlay — drawn after ShaftRenderer.draw()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw a faint tint + border over every pit-eligible component (body, taper, liner — the tap
 * affordance) and a small count badge above any that already has recorded wear (spots + pits).
 * Purely a rendering overlay — reads [wearRecord] but never mutates it. Generalized
 * from the liner-only version so bodies/tapers are tappable for pits too.
 */
private fun DrawScope.drawWearAffordances(
    layout: ShaftLayout.Result,
    components: List<ResolvedComponent>,
    wearRecord: WearRecord,
    tapTintColor: Color,
    tapBorderColor: Color,
    badgeColor: Color,
    badgeTextArgb: Int,
) {
    val targets = components.filter { it is ResolvedBody || it is ResolvedTaper || it is ResolvedLiner }
    if (targets.isEmpty()) return

    val badgePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = badgeTextArgb
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 24f
    }
    val badgeRadiusPx = 11.dp.toPx()
    val badgeGapPx = 4.dp.toPx()

    targets.forEach { rc ->
        val x0 = layout.xPx(rc.startMmPhysical)
        val x1 = layout.xPx(rc.endMmPhysical)
        val r  = layout.rPx(rc.maxDiaMm())
        val top = layout.centerlineYPx - r
        val bot = layout.centerlineYPx + r

        drawRect(color = tapTintColor, topLeft = Offset(x0, top), size = Size(x1 - x0, bot - top))
        drawRect(
            color = tapBorderColor,
            topLeft = Offset(x0, top),
            size = Size(x1 - x0, bot - top),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // Spots are liner-only; pits attach to any pit-eligible component. Badge shows the total.
        val spotCount = if (rc is ResolvedLiner) wearRecord.spots.count { it.linerId == rc.id } else 0
        val pitCount = wearRecord.pits.count { it.componentId == rc.id }
        val total = spotCount + pitCount
        if (total > 0) {
            val cx = (x0 + x1) / 2f
            val cy = (top - badgeGapPx - badgeRadiusPx).coerceAtLeast(badgeRadiusPx)
            drawCircle(color = badgeColor, radius = badgeRadiusPx, center = Offset(cx, cy))
            drawContext.canvas.nativeCanvas.drawText(
                "$total", cx, cy + badgeRadiusPx * 0.35f, badgePaint,
            )
        }
    }
}
