package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.pdf.composeWearPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.drawing.render.ThreadStyle
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.maxDiaMm
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.buildOpenPdfIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WearRoute
 *
 * Screen for the Shaft Wear / Inspection Document tab.
 *
 * ## Purpose
 * Produces a blank shaft outline form for field use — the machinist marks damage,
 * pitting, and dye-penetrant inspection results by hand on the printed page.
 *
 * ## Layout
 * - **Interactive shaft canvas** (Phase 2, `docs/LinerWearAreas_Proposal.md`) — same pattern as
 *   `RunoutRoute`'s preview canvas: `ShaftLayout.compute` + `ShaftRenderer.draw` against
 *   `resolvedComponents` (never raw spec). Liners are tap targets (faint tint affordance); a
 *   tap hit-tests in mm space via [ShaftLayout.Result.xMmFromPx] + [pickLinerIdAtMm] and opens
 *   [LinerWearDetailOverlay] for the tapped liner. Liners with recorded wear spots show a small
 *   count badge above them.
 * - **Preview PDF** — verify layout before saving.
 * - **Export PDF** — SAF file picker to save the file.
 *
 * ## Phase 3
 * [LinerWearDetailOverlay] — full-screen "zoom in" on one liner: broken-out liner with neighbor
 * stubs, wear bands, and editable spot cards. Not a nav destination; dismissed via its own
 * `BackHandler` or back-arrow button.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun WearRoute(
    vm: ShaftViewModel,
    onExportWear: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
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
    val wearRecord         by vm.wearRecord.collectAsState()

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // Which component's wear-detail overlay is open, if any. Doubles as the overlay's visibility
    // flag. A body, taper, or liner id (all pit-eligible); see ComponentWearDetailOverlay.
    var selectedComponentId by rememberSaveable { mutableStateOf<String?>(null) }

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
                        composeWearPdf(
                            page = page, spec = spec,
                            project = ProjectInfo(customer = customer, vessel = vessel,
                                jobNumber = jobNumber, side = shaftPosition),
                            unit = unit,
                            pdfPrefs = vm.currentPdfPrefs,
                            resolvedComponents = resolvedComponents,
                            lineThicknessScale = lineThicknessScale,
                            wearRecord = wearRecord,
                        )
                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
                if (openAfterExport) openWearPdf(ctx, uri)
            }
        }
    }

    LaunchedEffect(showPreview, spec, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   wearRecord) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot     = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val bmp = withContext(Dispatchers.IO) {
            renderWearBitmap(
                context = ctx, spec = spec,
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                pdfPrefs = prefsSnapshot,
                resolvedComponents = resolvedComponents,
                lineThicknessScale = thicknessSnapshot,
                wearRecord = wearRecord,
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture theme colors before the Canvas block (DrawScope is not composable) — same
    // technique as RunoutRoute's live preview.
    val outlineArgb    = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyFillArgb   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f).toArgb()
    val hatchArgb      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f).toArgb()
    val tapTintColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val tapBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val badgeColor     = MaterialTheme.colorScheme.primary
    val badgeTextArgb  = MaterialTheme.colorScheme.onPrimary.toArgb()
    val previewShape   = MaterialTheme.shapes.medium
    val textMeasurer   = rememberTextMeasurer()

    val transparentArgb = Color.Transparent.toArgb()
    val previewOpts = remember(outlineArgb, bodyFillArgb, hatchArgb) {
        RenderOptions(
            showGrid            = false,
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = bodyFillArgb,
            taperFillColor      = bodyFillArgb,
            linerFillColor      = transparentArgb, // liner tint drawn separately as tap affordance
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
            threadStyle         = ThreadStyle.HATCH,
            threadUseHatchColor = true,
            threadStrokePx      = 0f,
        )
    }

    // ── Main UI ─────────────────────────────────────────────────────────────
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
                text = "Wear Document",
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
                            draw(spec, layout, previewOpts, textMeasurer, resolvedComponents)
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

            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Wear Document")
            }

            Button(
                onClick = { launcher.launch(buildWearFilename(customer, vessel, jobNumber)) },
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
                launcher.launch(buildWearFilename(customer, vessel, jobNumber))
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
            onClose = { selectedComponentId = null },
        )
    }
}

private fun buildWearFilename(customer: String, vessel: String, jobNumber: String): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "WearDocument"}_wear.pdf"
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
 * Purely a rendering overlay — reads [wearRecord] but never mutates it. Generalized 2026-07-21
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

private fun renderWearBitmap(
    context: Context,
    spec: com.android.shaftschematic.model.ShaftSpec,
    project: ProjectInfo,
    unit: com.android.shaftschematic.util.UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<com.android.shaftschematic.ui.resolved.ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    wearRecord: WearRecord = WearRecord(),
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("wear_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeWearPdf(page = page, spec = spec, project = project, unit = unit,
            pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
            lineThicknessScale = lineThicknessScale, wearRecord = wearRecord)
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
