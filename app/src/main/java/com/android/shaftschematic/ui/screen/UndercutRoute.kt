// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutRoute.kt
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.clusterUndercuts
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.pickUndercutWindowAt
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.pdf.composeUndercutPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UndercutRoute
 *
 * Screen for the Undercut Drawing tab — the shop's record of machined-below-surface sections
 * (weld-repair undercuts, cleanup cuts). See `docs/UndercutDrawing_PLAN.md`.
 *
 * ## Layout
 * - **Overview canvas** — `ShaftLayout.compute` + `ShaftRenderer.draw` over
 *   `resolvedComponents` (never raw spec), then an affordance pass: every undercut's notch cut
 *   into the profile, a faint tint + count badge over each **cluster window**. The selectable
 *   area is the window, not a component — undercuts are not component-bound. A tap hit-tests in
 *   mm space via [ShaftLayout.Result.xMmFromPx] + `pickUndercutWindowAt` and opens
 *   [UndercutWindowDetailOverlay] on that window. No pinch-zoom here; the overlay owns zoom.
 * - **Add undercut** — records a 1 in section just FWD of the AFT S.E.T. and opens its window.
 *   Precision comes from the overlay's numeric fields, never from the tap (wear posture).
 * - Blank-draft toggle, Preview, Print, Export — straight ports of the wear document's flows,
 *   all calling `composeUndercutPdf`.
 *
 * Undercuts are reference-only data: nothing on this screen touches geometry, OAL, or
 * collision.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun UndercutRoute(
    vm: ShaftViewModel,
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
    val undercutRecord     by vm.undercutRecord.collectAsState()

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    // Blank-draft (write-in) copy: outline + form layout, all values blanked for handwriting.
    var blankDraft by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // Which undercut anchors the open detail overlay. Windows are derived state (they re-cluster
    // on every record edit), so the anchor is an undercut ID rather than a window index — the
    // window is looked up fresh below and the overlay closes if the anchor stops resolving.
    var anchorUndercutId by rememberSaveable { mutableStateOf<String?>(null) }

    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents) { surfaceSegsFrom(resolvedComponents) }
    val notches = remember(undercutRecord, segs, oalMm) {
        buildUndercutNotches(undercutRecord.undercuts, segs, oalMm)
    }
    val windows = remember(undercutRecord, oalMm) {
        clusterUndercuts(
            undercutRecord.undercuts.map { u ->
                val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
                UndercutSpanMm(u.id, c.startMm, c.endMm)
            },
            oalMm,
        )
    }
    // An anchor whose undercut no longer clusters into any window (it was deleted, or the shaft
    // shrank past it) simply yields no overlay — the anchor is never auto-cleared, because the
    // record and the anchor can update in either order within a frame and clearing on the stale
    // pass would close an overlay that was just opened.
    val activeWindow = anchorUndercutId?.let { id -> windows.firstOrNull { id in it.undercutIds } }

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
                        composeUndercutPdf(
                            page = page, spec = spec,
                            project = ProjectInfo(customer = customer, vessel = vessel,
                                jobNumber = jobNumber, side = shaftPosition),
                            unit = unit,
                            pdfPrefs = vm.currentPdfPrefs,
                            resolvedComponents = resolvedComponents,
                            undercutRecord = undercutRecord,
                            lineThicknessScale = lineThicknessScale,
                            blankValues = blankDraft,
                        )
                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
                if (openAfterExport) openUndercutPdf(ctx, uri)
            }
        }
    }

    LaunchedEffect(showPreview, spec, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   undercutRecord, blankDraft) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot     = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val bmp = withContext(Dispatchers.IO) {
            renderUndercutBitmap(
                context = ctx, spec = spec,
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                pdfPrefs = prefsSnapshot,
                resolvedComponents = resolvedComponents,
                lineThicknessScale = thicknessSnapshot,
                undercutRecord = undercutRecord,
                blankValues = blankDraft,
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture theme colors before the Canvas block (DrawScope is not composable) — same
    // technique as WearRoute's overview canvas.
    val outlineArgb    = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyFillArgb   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f).toArgb()
    val hatchArgb      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f).toArgb()
    val outlineColor   = MaterialTheme.colorScheme.onSurface
    val tapTintColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val tapBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val badgeColor     = MaterialTheme.colorScheme.primary
    val badgeTextArgb  = MaterialTheme.colorScheme.onPrimary.toArgb()
    val previewShape   = MaterialTheme.shapes.medium

    val previewOpts = remember(outlineArgb, bodyFillArgb, hatchArgb) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = bodyFillArgb,
            taperFillColor      = bodyFillArgb,
            linerFillColor      = bodyFillArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
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
                text = "Undercut Drawing",
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
                text = "Records undercut sections — distance from a S.E.T., length, and measured " +
                    "diameter — and prints them as zoomed detail views with chained dimensions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Overview canvas — tap a cluster window to open its detail view ────
            if (oalMm > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .pointerInput(spec, resolvedComponents, windows) {
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
                                pickUndercutWindowAt(tapMm, windows)?.let { w ->
                                    anchorUndercutId = w.undercutIds.firstOrNull()
                                }
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
                        // Notches first (they erase profile strokes inside each cut), then the
                        // window tint + badge on top as the tap affordance.
                        drawUndercutNotches(
                            notches = notches,
                            xPx = { mm -> layout.xPx(mm) },
                            rPx = { dia -> layout.rPx(dia) },
                            cy = layout.centerlineYPx,
                            voidColor = Color.White,
                            outlineColor = outlineColor,
                            strokeWidthPx = 1.5f,
                        )
                        drawUndercutWindowAffordances(
                            layout = layout,
                            windows = windows,
                            segs = segs,
                            tapTintColor = tapTintColor,
                            tapBorderColor = tapBorderColor,
                            badgeColor = badgeColor,
                            badgeTextArgb = badgeTextArgb,
                        )
                    }
                }
                Text(
                    text = if (windows.isEmpty())
                        "No undercuts recorded yet — add one to start the drawing."
                    else
                        "Tap a highlighted section to open its zoomed detail view.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    val (aftSetXMm, _) = undercutSetPositions(spec)
                    val start = aftSetXMm.coerceIn(0f, oalMm)
                    val length = DEFAULT_UNDERCUT_LENGTH_MM
                        .coerceAtMost((oalMm - start).coerceAtLeast(0.1f))
                    anchorUndercutId = vm.addUndercut(start, length)
                },
                enabled = oalMm > 0f,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add undercut")
            }

            HorizontalDivider()

            // ── Blank draft toggle ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info and OAL are blanked; recorded undercuts are omitted — a fresh form.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Undercut Drawing")
            }

            // ── Print button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val jobName = buildUndercutFilename(customer, vessel, jobNumber, blankDraft)
                        .removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
                    val specSnapshot = spec
                    val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition)
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = resolvedComponents
                    val thicknessSnapshot = lineThicknessScale
                    val recordSnapshot = undercutRecord
                    val blankSnapshot = blankDraft
                    printShaftPdfPage(ctx, jobName) { page ->
                        composeUndercutPdf(
                            page = page, spec = specSnapshot,
                            project = projectSnapshot, unit = unitSnapshot,
                            pdfPrefs = prefsSnapshot,
                            resolvedComponents = resolvedSnapshot,
                            undercutRecord = recordSnapshot,
                            lineThicknessScale = thicknessSnapshot,
                            blankValues = blankSnapshot,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Undercut Drawing")
            }

            Button(
                onClick = { launcher.launch(buildUndercutFilename(customer, vessel, jobNumber, blankDraft)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Undercut Drawing PDF")
            }
        }
    }

    BackHandler(enabled = showPreview) { showPreview = false }
    if (showPreview) {
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Undercut Drawing Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(buildUndercutFilename(customer, vessel, jobNumber, blankDraft))
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

    // ── Undercut window detail overlay ────────────────────────────────────────
    // UndercutWindowDetailOverlay hosts its own BackHandler; only compose it while a window
    // is active.
    activeWindow?.let { w ->
        UndercutWindowDetailOverlay(
            window = w,
            spec = spec,
            resolvedComponents = resolvedComponents,
            unit = unit,
            undercutRecord = undercutRecord,
            onUpdateUndercut = vm::updateUndercut,
            onUpdateReference = vm::updateUndercutReference,
            onRemoveUndercut = { id ->
                // Keep the overlay anchored on the window when a sibling is left behind;
                // deleting the last member closes it via the anchor-resolution effect above.
                if (id == anchorUndercutId) {
                    anchorUndercutId = w.undercutIds.firstOrNull { it != id }
                }
                vm.removeUndercut(id)
            },
            onClose = { anchorUndercutId = null },
        )
    }
}

/** Default length of a newly added undercut (1 in), clamped to the remaining shaft extent. */
private const val DEFAULT_UNDERCUT_LENGTH_MM = 25.4f

private fun buildUndercutFilename(
    customer: String,
    vessel: String,
    jobNumber: String,
    blankDraft: Boolean = false,
): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "Undercuts"}_undercuts$blankSuffix.pdf"
}

private fun openUndercutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching {
            context.grantUriPermission(
                ri.activityInfo?.packageName ?: return@forEach, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Cluster-window tap affordance — drawn after ShaftRenderer.draw() and the notches
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw a faint tint + border over every cluster window (the tap affordance) and a small badge
 * carrying the window's undercut count. Purely a rendering overlay — reads the windows but never
 * mutates the record. The window replaces the component as the selectable area, the one
 * difference from the wear document's affordance pass.
 */
private fun DrawScope.drawUndercutWindowAffordances(
    layout: ShaftLayout.Result,
    windows: List<UndercutWindow>,
    segs: List<SurfaceSeg>,
    tapTintColor: Color,
    tapBorderColor: Color,
    badgeColor: Color,
    badgeTextArgb: Int,
) {
    if (windows.isEmpty()) return

    val badgePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = badgeTextArgb
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 24f
    }
    val badgeRadiusPx = 11.dp.toPx()
    val badgeGapPx = 4.dp.toPx()

    windows.forEach { w ->
        val x0 = layout.xPx(w.startMm)
        val x1 = layout.xPx(w.endMm)
        val r = layout.rPx(maxOuterDiaOver(segs, w.startMm, w.endMm)).coerceAtLeast(4.dp.toPx())
        val top = layout.centerlineYPx - r
        val bot = layout.centerlineYPx + r

        drawRect(color = tapTintColor, topLeft = Offset(x0, top), size = Size(x1 - x0, bot - top))
        drawRect(
            color = tapBorderColor,
            topLeft = Offset(x0, top),
            size = Size(x1 - x0, bot - top),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        val cx = (x0 + x1) / 2f
        val cy = (top - badgeGapPx - badgeRadiusPx).coerceAtLeast(badgeRadiusPx)
        drawCircle(color = badgeColor, radius = badgeRadiusPx, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.drawText(
            "${w.undercutIds.size}", cx, cy + badgeRadiusPx * 0.35f, badgePaint,
        )
    }
}

private fun renderUndercutBitmap(
    context: Context,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    undercutRecord: UndercutRecord = UndercutRecord(),
    blankValues: Boolean = false,
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("undercut_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeUndercutPdf(page = page, spec = spec, project = project, unit = unit,
            pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
            undercutRecord = undercutRecord, lineThicknessScale = lineThicknessScale,
            blankValues = blankValues)
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
