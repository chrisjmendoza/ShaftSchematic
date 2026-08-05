package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutBubblePlan
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.geom.canonicalToUndercutStartMm
import com.android.shaftschematic.geom.clockTickRimOffset
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.geom.pickBubbleAt
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.geom.undercutStartToCanonicalMm
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.pdf.drawDiaReadingsInProfile
import com.android.shaftschematic.pdf.drawWearMarksOnRunoutProfile
import com.android.shaftschematic.pdf.drawWornSections
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.theme.SheetInk
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class RunoutComponentEntry(
    val id: String,
    val label: String,
    val defaultCount: Int,
    val startMm: Float,
)

/** Worn-section editor dialog target — [section] null means "add a new one". */
private data class WornSectionDialogTarget(val section: WornSection?)

@OptIn(ExperimentalTextApi::class)
@Composable
fun RunoutRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
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
    val runoutReadings     by vm.runoutReadings.collectAsState()
    val wearRecord         by vm.wearRecord.collectAsState()

    // Which bubble's editor dialog is open, if any (component id + station index + display title).
    var editingBubble by remember { mutableStateOf<EditingRunoutBubble?>(null) }

    // Worn-section editor dialog target: null = closed; a target with a null section = adding.
    var wornSectionDialog by remember { mutableStateOf<WornSectionDialogTarget?>(null) }

    val ctx = LocalContext.current
    var showPreview    by rememberSaveable { mutableStateOf(false) }
    // Blank-draft (write-in) copy: geometry + form layout, all values blanked for handwriting.
    var blankDraft     by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap  by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                        val page = doc.startPage(pageInfo)
                        composeRunoutPdf(
                            page = page, spec = spec, config = runoutConfig,
                            project = ProjectInfo(customer = customer, vessel = vessel,
                                jobNumber = jobNumber, side = shaftPosition),
                            unit = unit,
                            pdfPrefs = vm.currentPdfPrefs,
                            resolvedComponents = resolvedComponents,
                            lineThicknessScale = lineThicknessScale,
                            runoutReadings = runoutReadings,
                            wearRecord = wearRecord,
                            blankValues = blankDraft,
                        )
                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
                if (openAfterExport) openRunoutPdf(ctx, uri)
            }
        }
    }

    LaunchedEffect(showPreview, spec, runoutConfig, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   runoutReadings, wearRecord, blankDraft) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot  = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val bmp = withContext(Dispatchers.IO) {
            renderRunoutBitmap(
                context = ctx, spec = spec, config = runoutConfig,
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                pdfPrefs = prefsSnapshot,
                resolvedComponents = resolvedComponents,
                lineThicknessScale = thicknessSnapshot,
                runoutReadings = runoutReadings,
                wearRecord = wearRecord,
                blankValues = blankDraft,
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture colors before the Canvas block (DrawScope is not composable). Sheet ink is
    // FIXED (SheetInk), never theme colors: the canvas is a paper-white sheet in every
    // theme, and dark theme's near-white onSurface would print invisible ink on it.
    val outlineArgb   = SheetInk.Outline.toArgb()
    val bodyFillArgb  = SheetInk.Outline.copy(alpha = 0.08f).toArgb()
    val linerFillArgb = SheetInk.LinerTint.copy(alpha = 0.16f).toArgb()
    val hatchArgb     = SheetInk.Outline.copy(alpha = 0.55f).toArgb()
    val previewShape  = MaterialTheme.shapes.medium
    val textMeasurer  = rememberTextMeasurer()

    val transparentArgb = Color.Transparent.toArgb()
    val previewOpts = remember(outlineArgb, bodyFillArgb, linerFillArgb, hatchArgb,
                               pdfShadedBodies, pdfShadedTapers, pdfShadedLiners) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = if (pdfShadedBodies) bodyFillArgb  else transparentArgb,
            taperFillColor      = if (pdfShadedTapers) bodyFillArgb  else transparentArgb,
            linerFillColor      = if (pdfShadedLiners) linerFillArgb else transparentArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
        )
    }

    // Bodies, tapers, liners in axial order for the station count selector.
    // Built from RESOLVED components — the same list the drawn profile uses — so bodies
    // appear as their drawable segments (subtracted against tapers/liners, auto-fill
    // included), never at their raw spec positions. A body split into several fragments
    // by liners keeps one row (one id) controlling all its fragments.
    val entries: List<RunoutComponentEntry> = remember(spec, resolvedComponents) {
        val bodyTitles  = buildBodyTitleById(spec)
        val taperTitles = buildTaperTitleById(spec)
        val linerTitles = buildLinerTitleById(spec)
        buildList {
            resolvedComponents.forEach { rc ->
                when (rc) {
                    is ResolvedBody -> {
                        // Fragments of one stored body carry suffixed ids ("<id>#2", …);
                        // key off the base id so they collapse into a single row below.
                        val baseId = resolvedBodyBaseId(rc.id)
                        val label = if (rc.source == ResolvedComponentSource.AUTO) "Body (auto)"
                                    else bodyTitles[baseId] ?: "Body"
                        add(RunoutComponentEntry(baseId, label, RunoutConfig.BODY_DEFAULT_COUNT, rc.startMmPhysical))
                    }
                    is ResolvedTaper ->
                        add(RunoutComponentEntry(rc.id, taperTitles[rc.id] ?: "Taper", RunoutConfig.TAPER_DEFAULT_COUNT, rc.startMmPhysical))
                    is ResolvedLiner ->
                        add(RunoutComponentEntry(rc.id, linerTitles[rc.id] ?: "Liner", RunoutConfig.LINER_DEFAULT_COUNT, rc.startMmPhysical))
                    else -> {}
                }
            }
        }.distinctBy { it.id }.sortedBy { it.startMm }
    }

    // S.E.T. positions in shaft space — the worn-section Distance field's datums, and the
    // list rows' displayed distances. Same source as the composer's SET-to-SET window.
    val setPositions = remember(spec) { computeSetPositionsInMeasureSpace(computeOalWindow(spec), spec) }
    val aftSetXMm = setPositions.aftSETxMm.toFloat()
    val fwdSetXMm = setPositions.fwdSETxMm.toFloat()

    // Surface envelope for the worn-section boundary heights on the preview canvas.
    val segs = remember(resolvedComponents) { surfaceSegsFrom(resolvedComponents) }

    // Sheet-ink paints for the shared worn-section draw (pdf/RunoutPdfComposer's
    // drawWornSections runs on this canvas's nativeCanvas — one construction, both sites).
    val wornOutlinePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f
            color = android.graphics.Color.BLACK
        }
    }
    val wornTextPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            textSize = 26f
            color = android.graphics.Color.BLACK
        }
    }

    // Zoom state for the shaft preview (hoisted so it survives spec updates)
    var previewScale  by remember { mutableFloatStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    val previewTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        previewScale  = (previewScale * zoomChange).coerceIn(0.5f, 5f)
        previewOffset += panChange
    }

    // ── Screen ────────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

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
                text = "Runout Sheet",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
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

            // ── Live shaft + bubble preview (pinch-to-zoom, tap a bubble to edit) ──
            if (spec.overallLengthMm > 0f) {
                // Read live inside the (non-restarting) tap pointerInput without re-keying it.
                val scaleForTap  = rememberUpdatedState(previewScale)
                val offsetForTap = rememberUpdatedState(previewOffset)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .transformable(state = previewTransformState)
                        .pointerInput(spec, resolvedComponents, runoutConfig) {
                            detectTapGestures { tap ->
                                val preview = computeRunoutPreview(
                                    size.width.toFloat(), size.height.toFloat(),
                                    spec, resolvedComponents, runoutConfig.componentOverrides,
                                )
                                // Invert the Canvas graphicsLayer transform (scale about centre
                                // pivot, then translate) to map the tap into plan space.
                                val pivotX = size.width / 2f
                                val pivotY = size.height / 2f
                                val sc = scaleForTap.value
                                val lx = (tap.x - offsetForTap.value.x - pivotX) / sc + pivotX
                                val ly = (tap.y - offsetForTap.value.y - pivotY) / sc + pivotY
                                pickBubbleAt(
                                    preview.bubbles, preview.geom.radius, lx, ly,
                                    tolerance = preview.geom.radius * 2f,
                                )?.let { b ->
                                    editingBubble = EditingRunoutBubble(
                                        componentId = b.componentId,
                                        stationIndex = b.stationIndex,
                                        title = runoutBubbleTitle(b, entries),
                                    )
                                }
                            }
                        },
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX       = previewScale,
                                scaleY       = previewScale,
                                translationX = previewOffset.x,
                                translationY = previewOffset.y,
                            ),
                    ) {
                        val preview = computeRunoutPreview(
                            size.width, size.height,
                            spec, resolvedComponents, runoutConfig.componentOverrides,
                        )
                        with(ShaftRenderer) {
                            draw(spec, preview.layout, previewOpts, resolvedComponents)
                        }
                        // Consolidated wear rendering BEFORE the bubbles, in the printed
                        // sheet's z-order: marks first (wear bands, pit X's), then the
                        // worn-section boundaries, then ALL value text last over knockout
                        // halos — same single implementations as the PDF, so preview and
                        // print cannot diverge. Halos never erase a bubble leader (those
                        // draw after).
                        drawIntoCanvas { canvas ->
                            val nc = canvas.nativeCanvas
                            val xAtPx: (Float) -> Float = { mm -> preview.layout.xPx(mm) }
                            val cyPx = preview.layout.centerlineYPx
                            drawWearMarksOnRunoutProfile(
                                nc, wearRecord, spec.liners, resolvedComponents,
                                cy = cyPx, xAt = xAtPx,
                                rPx = { dia -> preview.layout.rPx(dia) },
                                outline = wornOutlinePaint,
                                pitSmallHalf = 3f,
                            )
                            drawWornSections(
                                nc, wearRecord.wornSections,
                                oalMm = spec.overallLengthMm,
                                windowStartMm = 0f, windowEndMm = spec.overallLengthMm,
                                unit = unit,
                                xAt = xAtPx,
                                surfaceRAt = { mm -> preview.layout.rPx(outerDiaAt(segs, mm)) },
                                cy = cyPx,
                                outline = wornOutlinePaint, text = wornTextPaint,
                                includeValues = true,
                            )
                            drawDiaReadingsInProfile(
                                nc, wearRecord.diaReadings, resolvedComponents,
                                cy = cyPx, xAt = xAtPx, unit = unit, text = wornTextPaint,
                            )
                        }
                        drawRunoutMarkers(preview.bubbles, preview.geom, runoutReadings, unit, textMeasurer)
                    }

                    // Compact reset control (top-right) — mirrors the schematic preview.
                    IconButton(
                        onClick = {
                            previewScale  = 1f
                            previewOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset view",
                            tint = Color.Black.copy(alpha = 0.8f),
                        )
                    }
                }
                Text(
                    text = "Tap a bubble to enter its TIR reading and high spot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── TIR orientation selector ──────────────────────────────────────
            Text("TIR orientation", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TirButton("Looking AFT",     runoutConfig.tirDirection == TirDirection.AFT)     { vm.setTirDirection(TirDirection.AFT) }
                TirButton("Looking FORWARD", runoutConfig.tirDirection == TirDirection.FORWARD) { vm.setTirDirection(TirDirection.FORWARD) }
                TirButton("Not set",         runoutConfig.tirDirection == TirDirection.UNSET)   { vm.setTirDirection(TirDirection.UNSET) }
            }

            // ── Measurement station selector ──────────────────────────────────
            if (entries.isNotEmpty()) {
                Text("Measurement stations", style = MaterialTheme.typography.titleSmall)
                entries.forEach { entry ->
                    val currentCount = runoutConfig.componentOverrides[entry.id] ?: entry.defaultCount
                    RunoutStationRow(
                        label        = entry.label,
                        currentCount = currentCount,
                        onDecrement  = { vm.setRunoutBubbleCount(entry.id, currentCount - 1) },
                        onIncrement  = { vm.setRunoutBubbleCount(entry.id, currentCount + 1) },
                    )
                }
            }

            // ── Worn sections — wear readings consolidated onto this sheet ────
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
                    onClick = { wornSectionDialog = WornSectionDialogTarget(section) },
                )
            }
            OutlinedButton(onClick = { wornSectionDialog = WornSectionDialogTarget(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add worn section")
            }

            Spacer(Modifier.height(4.dp))

            // ── Blank draft toggle ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info, OAL, and TIR values are blanked for handwriting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Preview button ────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Runout Sheet")
            }

            // ── Print button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val jobName = buildRunoutFilename(customer, vessel, jobNumber, blankDraft)
                        .removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
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
                        composeRunoutPdf(
                            page = page, spec = specSnapshot, config = configSnapshot,
                            project = projectSnapshot, unit = unitSnapshot,
                            pdfPrefs = prefsSnapshot,
                            resolvedComponents = resolvedSnapshot,
                            lineThicknessScale = thicknessSnapshot,
                            runoutReadings = readingsSnapshot,
                            wearRecord = wearSnapshot,
                            blankValues = blankSnapshot,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Runout Sheet")
            }

            // ── Export button ─────────────────────────────────────────────────
            Button(
                onClick = { launcher.launch(buildRunoutFilename(customer, vessel, jobNumber, blankDraft)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Runout Sheet PDF")
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
            title = "Runout Sheet Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(buildRunoutFilename(customer, vessel, jobNumber, blankDraft))
            },
            optionsSheet = {
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    vm = vm,
                )
            },
        )
    }

    // ── Runout bubble editor dialog ───────────────────────────────────────────
    editingBubble?.let { editing ->
        val existing = runoutReadings.find(editing.componentId, editing.stationIndex)
        RunoutBubbleDialog(
            title = editing.title,
            unit = unit,
            initialValueMm = existing?.valueMm,
            initialHighSpotHalfHours = existing?.highSpotHalfHours,
            onSave = { valueMm, tick ->
                vm.setRunoutReading(editing.componentId, editing.stationIndex, valueMm, tick)
                editingBubble = null
            },
            onDismiss = { editingBubble = null },
        )
    }
}

/** Identifies the bubble whose editor dialog is open. */
private data class EditingRunoutBubble(
    val componentId: String,
    val stationIndex: Int,
    val title: String,
)

/** Display title for the editor dialog, e.g. "Body 1 · Station 2". */
private fun runoutBubbleTitle(bubble: PlacedRunoutBubble, entries: List<RunoutComponentEntry>): String {
    val label = entries.firstOrNull { it.id == bubble.componentId }?.label ?: "Component"
    return "$label · Station ${bubble.stationIndex + 1}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Local composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RunoutStationRow(
    label: String,
    currentCount: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stations:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onDecrement, enabled = currentCount > 1) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "$currentCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = onIncrement) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TirButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else TextButton(onClick = onClick) { Text(label) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Component spans eligible for runout stations, from the resolved component list. */
private fun runoutSpans(components: List<ResolvedComponent>): List<RunoutComponentSpan> =
    components.mapNotNull { rc ->
        val lengthMm = rc.endMmPhysical - rc.startMmPhysical
        when (rc) {
            // Body fragments keep the stored body's id here (suffix stripped) so runout
            // station keys stay exactly as they were before fragment ids became unique.
            is ResolvedBody  -> RunoutComponentSpan(resolvedBodyBaseId(rc.id), RunoutComponentKind.BODY,  rc.startMmPhysical, lengthMm)
            is ResolvedTaper -> RunoutComponentSpan(rc.id, RunoutComponentKind.TAPER, rc.startMmPhysical, lengthMm)
            is ResolvedLiner -> RunoutComponentSpan(rc.id, RunoutComponentKind.LINER, rc.startMmPhysical, lengthMm)
            else -> null
        }
    }

/** The shaft layout + planned bubbles for the runout preview canvas, computed by [computeRunoutPreview]. */
private class RunoutPreview(
    val layout: ShaftLayout.Result,
    val geom: RunoutBubbleGeometry,
    val bubbles: List<PlacedRunoutBubble>,
)

/**
 * Compute the shaft layout and fully-placed runout bubbles for a canvas of [widthPx]×[heightPx].
 * Uses the SAME shared engine (`geom/RunoutBubbleLayout.kt`) as the PDF, so the preview matches the
 * export. Hoisted out of the Canvas draw lambda so the tap handler and the renderer plan identical
 * geometry from the same inputs. Runs on a [Density] scope (both `DrawScope` and `PointerInputScope`
 * qualify) so it can resolve dp sizes.
 */
private fun Density.computeRunoutPreview(
    widthPx: Float,
    heightPx: Float,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    overrides: Map<String, Int>,
): RunoutPreview {
    val marginPx = 12.dp.toPx()
    val bubbleGeom = RunoutBubbleGeometry(
        radius = 7.dp.toPx(),
        minGap = 5.dp.toPx(),
        shortLeader = 5.dp.toPx(),
        contentLeft = 0f,
        contentRight = widthPx,
    )
    val spans = runoutSpans(resolvedComponents)

    // Reserve vertical space for the planned bubble rows so shaft + bubbles are centred together.
    // First pass assumes the typical two-row layout; re-plan once if the actual row count differs.
    fun planFor(reservedH: Float): Pair<ShaftLayout.Result, RunoutBubblePlan> {
        val layout = ShaftLayout.compute(
            spec = spec, leftPx = 0f, topPx = 0f, rightPx = widthPx,
            bottomPx = heightPx - reservedH, marginPx = marginPx,
            resolvedComponents = resolvedComponents,
        )
        val stations = collectRunoutStations(spans, overrides) { mm -> layout.xPx(mm) }
        return layout to planRunoutBubbles(stations, bubbleGeom)
    }

    val twoRowH = bubbleGeom.shortLeader + 2f * bubbleGeom.radius + bubbleGeom.rowStep
    var (layout, plan) = planFor(twoRowH)
    val neededH = plan.sectionHeight(0f)
    if (abs(neededH - twoRowH) > 0.5f) {
        val replanned = planFor(neededH)
        layout = replanned.first
        plan = replanned.second
    }

    val maxOdMm = runoutMaxOdMm(resolvedComponents)
    val result = plan.finish(
        anchorY = layout.centerlineYPx + layout.rPx(maxOdMm),
        surfaceYAtMm = { mm -> layout.centerlineYPx + layout.rPx(runoutOdMmAt(mm, resolvedComponents)) },
    )
    return RunoutPreview(layout, bubbleGeom, result.bubbles)
}

/** Outer-surface diameter (mm) at an axial station, across the resolved components. */
private fun runoutOdMmAt(stMm: Float, components: List<ResolvedComponent>): Float {
    var od = 10f
    components.forEach { rc ->
        val inRange = stMm >= rc.startMmPhysical - 0.1f && stMm <= rc.endMmPhysical + 0.1f
        if (!inRange) return@forEach
        when (rc) {
            is ResolvedBody  -> od = maxOf(od, rc.diaMm)
            is ResolvedTaper -> {
                val len  = rc.endMmPhysical - rc.startMmPhysical
                val frac = if (len > 0f) ((stMm - rc.startMmPhysical) / len).coerceIn(0f, 1f) else 0f
                od = maxOf(od, rc.startDiaMm + (rc.endDiaMm - rc.startDiaMm) * frac)
            }
            is ResolvedLiner -> od = maxOf(od, rc.odMm)
            else -> {}
        }
    }
    return od
}

/** Largest outer diameter (mm) across the resolved components (min 10mm). */
private fun runoutMaxOdMm(components: List<ResolvedComponent>): Float =
    components.maxOfOrNull { rc ->
        when (rc) {
            is ResolvedBody  -> rc.diaMm
            is ResolvedTaper -> maxOf(rc.startDiaMm, rc.endDiaMm)
            is ResolvedLiner -> rc.odMm
            else -> 0f
        }
    }?.coerceAtLeast(10f) ?: 10f

/**
 * Draw the planned runout bubbles: leader polylines, the circle with a keyway cutout at 12 o'clock,
 * and — when recorded — the TIR value (centred) and high-spot marker (radial line + rim dot). The
 * keyway cutout and marker geometry mirror the PDF (`RunoutPdfComposer.drawPlacedBubbles`) so the
 * preview matches the export exactly.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRunoutMarkers(
    bubbles: List<PlacedRunoutBubble>,
    geom: RunoutBubbleGeometry,
    readings: RunoutReadings,
    unit: com.android.shaftschematic.util.UnitSystem,
    textMeasurer: TextMeasurer,
) {
    val strokeW     = 1.2.dp.toPx()
    val markerColor = Color.Black.copy(alpha = 0.70f)
    val highSpotColor = Color(0xFFC62828) // red — the high spot, per shop convention
    val r = geom.radius

    bubbles.forEach { b ->
        val center = Offset(b.bubbleX, b.bubbleCenterY)
        b.leader.zipWithNext { p, q ->
            drawLine(markerColor, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = strokeW)
        }
        drawRunoutBubbleRing(center, r, markerColor, strokeW)

        val reading = readings.find(b.componentId, b.stationIndex)
        // TIR value, centred in the circle.
        reading?.valueMm?.let { valueMm ->
            val txt = com.android.shaftschematic.util.formatRunoutValue(valueMm, unit)
            val style = androidx.compose.ui.text.TextStyle(
                color = markerColor,
                // Small value inside the (larger) circle — lockstep with the PDF bubble.
                fontSize = with(this) { (r * 0.60f).toSp() },
            )
            val measured = textMeasurer.measure(txt, style)
            drawText(
                textMeasurer = textMeasurer, text = txt, style = style,
                topLeft = Offset(
                    b.bubbleX - measured.size.width / 2f,
                    b.bubbleCenterY - measured.size.height / 2f,
                ),
            )
        }
        // High-spot marker: a short dash straddling the rim at the clock position (no radial
        // line — it would crowd the centred value). Matches the hand-drawn shop convention.
        reading?.highSpotHalfHours?.let { tick ->
            val (ux, uy) = clockTickRimOffset(tick, 1f) // unit outward direction
            val inner = r * 0.70f
            val outer = r * 1.30f
            drawLine(
                highSpotColor,
                Offset(b.bubbleX + ux * inner, b.bubbleCenterY + uy * inner),
                Offset(b.bubbleX + ux * outer, b.bubbleCenterY + uy * outer),
                strokeWidth = strokeW * 1.7f,
            )
        }
    }
}

/**
 * Draw a runout bubble ring with a keyway cutout at 12 o'clock: the top arc is broken across the
 * slot mouth and an open-topped slot descends into the circle (matches the shop's key-at-top
 * convention). Shared geometry with the PDF renderer.
 */
private fun DrawScope.drawRunoutBubbleRing(center: Offset, r: Float, color: Color, strokeW: Float) {
    val stroke = Stroke(width = strokeW)
    // Slot half-width and the angular gap it subtends at the rim (measured from 12 o'clock).
    val slotHalf = r * 0.22f
    val slotDepth = r * 0.42f
    val gapDeg = Math.toDegrees(kotlin.math.asin((slotHalf / r).coerceIn(0f, 1f).toDouble())).toFloat()

    // Arc everywhere except the gap at the top (top = -90° in the drawArc convention).
    drawArc(
        color = color,
        startAngle = -90f + gapDeg,
        sweepAngle = 360f - 2f * gapDeg,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(2f * r, 2f * r),
        style = stroke,
    )
    // Slot: two verticals descending from the gap edges + a bottom connector.
    val leftX = center.x - slotHalf
    val rightX = center.x + slotHalf
    val topY = center.y - r * cos(Math.toRadians(gapDeg.toDouble())).toFloat()
    val botY = topY + slotDepth
    drawLine(color, Offset(leftX, topY), Offset(leftX, botY), strokeWidth = strokeW)
    drawLine(color, Offset(rightX, topY), Offset(rightX, botY), strokeWidth = strokeW)
    drawLine(color, Offset(leftX, botY), Offset(rightX, botY), strokeWidth = strokeW)
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildRunoutFilename(
    customer: String,
    vessel: String,
    jobNumber: String,
    blankDraft: Boolean = false,
): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "RunoutSheet"}_runout$blankSuffix.pdf"
}

private fun openRunoutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching { context.grantUriPermission(ri.activityInfo?.packageName ?: return@forEach, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Worn-section editor (list row + dialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One worn section in the list under the preview: displayed Distance is re-projected from
 * canonical against the section's authored S.E.T. reference — display only, canonical
 * never moves (the WearSpotReference pattern). Tap to edit.
 */
@Composable
private fun WornSectionRow(
    index: Int,
    section: WornSection,
    unit: UnitSystem,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onClick: () -> Unit,
) {
    val reference = wornSectionSetReference(section.authoredReference)
    val distMm = canonicalToUndercutStartMm(
        reference, section.startFromAftMm, section.lengthMm, aftSetXMm, fwdSetXMm,
    )
    val refLabel = if (reference == UndercutReference.FWD_SET) "FWD S.E.T." else "AFT S.E.T."
    val values = section.diaMm.filter { it > 0f }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Worn section $index", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${disp(distMm, unit)}${abbr(unit)} from $refLabel · " +
                    "L ${disp(section.lengthMm, unit)}${abbr(unit)} · " +
                    if (values.isEmpty()) "no readings yet" else "${values.size} Ø",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Worn sections author against the S.E.T.s only — map any non-SET value to AFT_SET. */
private fun wornSectionSetReference(reference: UndercutReference): UndercutReference =
    if (reference == UndercutReference.FWD_SET) UndercutReference.FWD_SET
    else UndercutReference.AFT_SET

/**
 * Add/edit dialog for a worn section: S.E.T. reference chips, Distance + Length, and the
 * measured Ø list. All fields are unit-aware text inputs parsed on Save — typed values are
 * stored verbatim (golden rule); canonical start is derived from the authored Distance
 * once, at commit. Switching the reference chips re-projects the DISPLAYED distance so
 * the section never moves on a reference switch.
 */
@Composable
private fun WornSectionDialog(
    existing: WornSection?,
    unit: UnitSystem,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onSave: (startFromAftMm: Float, lengthMm: Float, diaMm: List<Float>, reference: UndercutReference) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var reference by remember(existing?.id) {
        mutableStateOf(wornSectionSetReference(existing?.authoredReference ?: UndercutReference.AFT_SET))
    }
    var distanceText by remember(existing?.id) {
        mutableStateOf(
            existing?.let {
                disp(
                    canonicalToUndercutStartMm(
                        wornSectionSetReference(it.authoredReference),
                        it.startFromAftMm, it.lengthMm, aftSetXMm, fwdSetXMm,
                    ),
                    unit,
                )
            } ?: ""
        )
    }
    var lengthText by remember(existing?.id) {
        mutableStateOf(existing?.let { disp(it.lengthMm, unit) } ?: "")
    }
    val valueTexts = remember(existing?.id) {
        (existing?.diaMm?.map { disp(it, unit) } ?: emptyList())
            .ifEmpty { listOf("", "", "") }
            .toMutableStateList()
    }

    val distMm = toMmOrNull(distanceText, unit)
    val lenMm = toMmOrNull(lengthText, unit)
    val canSave = distMm != null && lenMm != null && lenMm > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add worn section" else "Edit worn section") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Measure from", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        UndercutReference.AFT_SET to "AFT S.E.T.",
                        UndercutReference.FWD_SET to "FWD S.E.T.",
                    ).forEach { (ref, label) ->
                        FilterChip(
                            selected = reference == ref,
                            onClick = {
                                if (reference != ref) {
                                    // Re-project the displayed distance under the new datum
                                    // so the section stays put — the canonical position is
                                    // what both projections share.
                                    val d = toMmOrNull(distanceText, unit)
                                    val l = toMmOrNull(lengthText, unit) ?: 0f
                                    if (d != null) {
                                        val canonical = undercutStartToCanonicalMm(
                                            reference, d, l, aftSetXMm, fwdSetXMm,
                                        )
                                        distanceText = disp(
                                            canonicalToUndercutStartMm(ref, canonical, l, aftSetXMm, fwdSetXMm),
                                            unit,
                                        )
                                    }
                                    reference = ref
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distance (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    label = { Text("Length (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Text("Measured diameters", style = MaterialTheme.typography.labelLarge)
                valueTexts.forEachIndexed { i, v ->
                    OutlinedTextField(
                        value = v,
                        onValueChange = { valueTexts[i] = it },
                        label = { Text("Ø ${i + 1} (${abbr(unit)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
                TextButton(
                    onClick = { valueTexts.add("") },
                    enabled = valueTexts.size < 6,
                ) { Text("Add another Ø") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val canonical = undercutStartToCanonicalMm(
                        reference, distMm ?: return@TextButton, lenMm ?: return@TextButton,
                        aftSetXMm, fwdSetXMm,
                    )
                    val values = valueTexts.mapNotNull { toMmOrNull(it, unit) }.filter { it > 0f }
                    onSave(canonical, lenMm, values, reference)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun renderRunoutBitmap(
    context: Context,
    spec: ShaftSpec,
    config: RunoutConfig,
    project: ProjectInfo,
    unit: com.android.shaftschematic.util.UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    runoutReadings: RunoutReadings = RunoutReadings(),
    wearRecord: WearRecord = WearRecord(),
    blankValues: Boolean = false,
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("runout_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeRunoutPdf(page = page, spec = spec, config = config, project = project, unit = unit,
            pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
            lineThicknessScale = lineThicknessScale, runoutReadings = runoutReadings,
            wearRecord = wearRecord, blankValues = blankValues)
        doc.finishPage(page)
        tempFile.outputStream().buffered().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        val pdfPage = renderer.openPage(0)
        try {
            val bmp = Bitmap.createBitmap(pdfPage.width * 2, pdfPage.height * 2, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally { pdfPage.close() }
    } finally { renderer.close(); pfd.close(); tempFile.delete() }
}.getOrNull()

// ─────────────────────────────────────────────────────────────────────────────
// Shared PDF preview overlay (also used by WearRoute)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen modal overlay that shows a PDF rendered as a bitmap.
 *
 * Displayed while [loading] is true shows a spinner. Once the [bitmap] is ready
 * it fills the overlay with pinch-to-zoom support via a standard Image composable.
 * The "Export" button in the top bar lets the user proceed to the SAF file picker
 * after verifying the layout looks correct.
 *
 * @param bitmap        The rendered PDF page (null while rendering or on error).
 * @param loading       Whether the bitmap is still being generated.
 * @param title         Title shown in the top bar of the overlay.
 * @param onClose       Called when the user taps × or navigates back.
 * @param onExport      Called when the user taps the Export button.
 * @param optionsSheet  Optional composable content shown in a bottom sheet when the user
 *                      taps the Tune icon. When null, no Tune icon is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPreviewOverlay(
    bitmap: ImageBitmap?,
    loading: Boolean,
    title: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    optionsSheet: (@Composable () -> Unit)? = null,
) {
    var showOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Unlock device rotation while the preview is open so the landscape sheet can be viewed in
    // landscape (the app is otherwise locked to portrait); restore portrait on dismiss. Same
    // pattern as the schematic `PdfPreviewScreen`.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (optionsSheet != null) {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                FilledTonalButton(onClick = onExport, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }

            // ── PDF preview area with pinch-to-zoom ────────────────────────
            // `clipToBounds` keeps the zoomed/panned page inside this Box: a `graphicsLayer`
            // scale/translate draws outside the layout node unless clipped, so a zoomed-in page
            // could slide up OVER the toolbar and hide Close/Export (on-device report). The
            // document tucks BEHIND the bar instead. Hit testing was always bounded by layout,
            // so the bar stayed tappable — this fixes what the user could see.
            Box(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    bitmap != null -> {
                        val scaleState  = remember(bitmap) { mutableFloatStateOf(1f) }
                        val offsetState = remember(bitmap) { mutableStateOf(Offset.Zero) }
                        val scale  by scaleState
                        val offset by offsetState

                        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                            scaleState.floatValue  = (scaleState.floatValue * zoomChange).coerceIn(0.5f, 8f)
                            offsetState.value = offsetState.value + panChange
                        }

                        Image(
                            bitmap = bitmap,
                            contentDescription = "PDF preview — pinch to zoom, drag to pan",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(state = transformState)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                        )
                    }
                    else -> Text(
                        "Preview unavailable",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showOptions && optionsSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState,
        ) {
            optionsSheet()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared PDF options sheet (Runout + Wear)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun RunoutWearOptionsSheet(
    lineThicknessScale: Float,
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
    vm: ShaftViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text("PDF Options", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // ── Line thickness ───────────────────────────────────────────────────
        // Track the drag locally; commit once on release. Committing per drag frame
        // writes DataStore and re-renders the whole PDF preview each frame.
        var thicknessDrag by remember { mutableStateOf<Float?>(null) }
        Text(
            "Line thickness  ${((thicknessDrag ?: lineThicknessScale) * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = thicknessDrag ?: lineThicknessScale,
                onValueChange = { thicknessDrag = it },
                onValueChangeFinished = {
                    thicknessDrag?.let { vm.setLineThicknessScale(it) }
                    thicknessDrag = null
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("200%", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Shade in PDF ─────────────────────────────────────────────────────
        Text("Shade in PDF", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pdfShadedBodies, onCheckedChange = { vm.setPdfShadedBodies(it) })
            Spacer(Modifier.width(8.dp))
            Text("Bodies", style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pdfShadedTapers, onCheckedChange = { vm.setPdfShadedTapers(it) })
            Spacer(Modifier.width(8.dp))
            Text("Tapers", style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pdfShadedLiners, onCheckedChange = { vm.setPdfShadedLiners(it) })
            Spacer(Modifier.width(8.dp))
            Text("Liners", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(24.dp))
    }
}
