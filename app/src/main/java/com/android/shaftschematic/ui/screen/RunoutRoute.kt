package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.geom.RunoutBubblePlan
import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.geom.planRunoutBubbles
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.drawing.render.ThreadStyle
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.buildOpenPdfIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class RunoutComponentEntry(
    val id: String,
    val label: String,
    val defaultCount: Int,
    val startMm: Float,
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun RunoutRoute(
    vm: ShaftViewModel,
    onExportRunout: () -> Unit = {},
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

    val ctx = LocalContext.current
    var showPreview    by rememberSaveable { mutableStateOf(false) }
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
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners) {
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
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture theme colors before the Canvas block (DrawScope is not composable)
    val outlineArgb   = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyFillArgb  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f).toArgb()
    val linerFillArgb = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f).toArgb()
    val hatchArgb     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f).toArgb()
    val previewShape  = MaterialTheme.shapes.medium
    val textMeasurer  = rememberTextMeasurer()

    val transparentArgb = Color.Transparent.toArgb()
    val previewOpts = remember(outlineArgb, bodyFillArgb, linerFillArgb, hatchArgb,
                               pdfShadedBodies, pdfShadedTapers, pdfShadedLiners) {
        RenderOptions(
            showGrid            = false,
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = if (pdfShadedBodies) bodyFillArgb  else transparentArgb,
            taperFillColor      = if (pdfShadedTapers) bodyFillArgb  else transparentArgb,
            linerFillColor      = if (pdfShadedLiners) linerFillArgb else transparentArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
            threadStyle         = ThreadStyle.HATCH,
            threadUseHatchColor = true,
            threadStrokePx      = 0f,
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
                        val label = if (rc.source == ResolvedComponentSource.AUTO) "Body (auto)"
                                    else bodyTitles[rc.id] ?: "Body"
                        add(RunoutComponentEntry(rc.id, label, RunoutConfig.BODY_DEFAULT_COUNT, rc.startMmPhysical))
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

            // ── Live shaft + bubble preview (pinch-to-zoom) ──────────────────
            if (spec.overallLengthMm > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .transformable(state = previewTransformState),
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
                        val marginPx = 12.dp.toPx()
                        val bubbleGeom = RunoutBubbleGeometry(
                            radius = 6.dp.toPx(),
                            minGap = 5.dp.toPx(),
                            shortLeader = 5.dp.toPx(),
                            contentLeft = 0f,
                            contentRight = size.width,
                        )
                        val spans = runoutSpans(resolvedComponents)

                        // Same engine as the PDF (geom/RunoutBubbleLayout.kt): reserve
                        // vertical space for the planned bubble rows so shaft + bubbles
                        // are centred together. First pass assumes the typical two-row
                        // layout; re-plan once if the actual row count differs.
                        fun planFor(reservedH: Float): Pair<ShaftLayout.Result, RunoutBubblePlan> {
                            val layout = ShaftLayout.compute(
                                spec               = spec,
                                leftPx             = 0f,
                                topPx              = 0f,
                                rightPx            = size.width,
                                bottomPx           = size.height - reservedH,
                                marginPx           = marginPx,
                                resolvedComponents = resolvedComponents,
                            )
                            val stations = collectRunoutStations(
                                spans, runoutConfig.componentOverrides,
                            ) { mm -> layout.xPx(mm) }
                            return layout to planRunoutBubbles(stations, bubbleGeom)
                        }

                        val twoRowH = bubbleGeom.shortLeader + 2f * bubbleGeom.radius + bubbleGeom.rowStep
                        var (layout, plan) = planFor(twoRowH)
                        val neededH = plan.sectionHeight(0f)
                        if (kotlin.math.abs(neededH - twoRowH) > 0.5f) {
                            val replanned = planFor(neededH)
                            layout = replanned.first
                            plan = replanned.second
                        }

                        with(ShaftRenderer) {
                            draw(spec, layout, previewOpts, textMeasurer, resolvedComponents)
                        }
                        drawRunoutMarkers(plan, layout, resolvedComponents)
                    }
                }
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

            Spacer(Modifier.height(4.dp))

            // ── Preview button ────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Runout Sheet")
            }

            // ── Export button ─────────────────────────────────────────────────
            Button(
                onClick = { launcher.launch(buildRunoutFilename(customer, vessel, jobNumber)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Runout Sheet PDF")
            }
        }
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
                launcher.launch(buildRunoutFilename(customer, vessel, jobNumber))
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
            is ResolvedBody  -> RunoutComponentSpan(rc.id, RunoutComponentKind.BODY,  rc.startMmPhysical, lengthMm)
            is ResolvedTaper -> RunoutComponentSpan(rc.id, RunoutComponentKind.TAPER, rc.startMmPhysical, lengthMm)
            is ResolvedLiner -> RunoutComponentSpan(rc.id, RunoutComponentKind.LINER, rc.startMmPhysical, lengthMm)
            else -> null
        }
    }

/**
 * Draw the planned runout bubbles and leader polylines. Placement comes from the shared
 * engine (`geom/RunoutBubbleLayout.kt`) — the same rows, x positions, and leader routing
 * as the PDF, so the preview matches the export exactly.
 */
private fun DrawScope.drawRunoutMarkers(
    plan: RunoutBubblePlan,
    layout: ShaftLayout.Result,
    components: List<ResolvedComponent>,
) {
    val strokeW     = 1.2.dp.toPx()
    val markerColor = Color.Black.copy(alpha = 0.70f)

    fun odMmAt(stMm: Float): Float {
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

    val maxOdMm = components.maxOfOrNull { rc ->
        when (rc) {
            is ResolvedBody  -> rc.diaMm
            is ResolvedTaper -> maxOf(rc.startDiaMm, rc.endDiaMm)
            is ResolvedLiner -> rc.odMm
            else -> 0f
        }
    }?.coerceAtLeast(10f) ?: 10f

    val result = plan.finish(
        anchorY = layout.centerlineYPx + layout.rPx(maxOdMm),
        surfaceYAtMm = { mm -> layout.centerlineYPx + layout.rPx(odMmAt(mm)) },
    )

    // Keyway notch sized proportionally to the PDF's (7pt on a 40pt bubble).
    val notchSize = plan.geom.radius * 0.35f
    result.bubbles.forEach { b ->
        b.leader.zipWithNext { p, q ->
            drawLine(markerColor, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = strokeW)
        }
        drawCircle(
            markerColor,
            radius = plan.geom.radius,
            center = Offset(b.bubbleX, b.bubbleCenterY),
            style = Stroke(width = strokeW),
        )
        // Keyway reference marker: open square notch straddling the rim at 12-o'clock,
        // same as the PDF. White fill blanks the rim inside the notch first.
        val notchTopLeft = Offset(
            b.bubbleX - notchSize * 0.5f,
            b.bubbleCenterY - plan.geom.radius - notchSize * 0.6f,
        )
        drawRect(Color.White, topLeft = notchTopLeft, size = Size(notchSize, notchSize))
        drawRect(markerColor, topLeft = notchTopLeft, size = Size(notchSize, notchSize),
            style = Stroke(width = strokeW))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildRunoutFilename(customer: String, vessel: String, jobNumber: String): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "RunoutSheet"}_runout.pdf"
}

private fun openRunoutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching { context.grantUriPermission(ri.activityInfo?.packageName ?: return@forEach, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
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
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("runout_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeRunoutPdf(page = page, spec = spec, config = config, project = project, unit = unit,
            pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
            lineThicknessScale = lineThicknessScale)
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
            Box(
                modifier = Modifier.fillMaxSize(),
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
