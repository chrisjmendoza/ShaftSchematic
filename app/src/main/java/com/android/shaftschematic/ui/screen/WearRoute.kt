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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.pdf.composeWearPdf
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
 * - Brief explanation of the document.
 * - **Preview PDF** — verify layout before saving.
 * - **Export PDF** — SAF file picker to save the file.
 *
 * ## Phase 2
 * Digital damage annotation: tap zones on the shaft outline, severity selector,
 * dye-pen pass/fail toggle, export with coloured overlays.
 */
@Composable
fun WearRoute(
    vm: ShaftViewModel,
    onExportWear: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
) {
    val spec          by vm.spec.collectAsState()
    val unit          by vm.unit.collectAsState()
    val customer      by vm.customer.collectAsState()
    val vessel        by vm.vessel.collectAsState()
    val jobNumber     by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val openAfterExport by vm.openPdfAfterExport.collectAsState()

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewBitmap by rememberSaveable { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(showPreview, spec, unit) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val bmp = withContext(Dispatchers.IO) {
            renderWearBitmap(
                context = ctx, spec = spec,
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // ── Main UI ─────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Wear / Inspection Record", style = MaterialTheme.typography.headlineSmall)

        Text(
            text = "Prints a blank shaft outline for field use. Mark damage, pitting, and " +
                "dye-penetrant inspection results directly on the printed form.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Phase 2 will add digital damage annotation — tap zones, severity rating, " +
                "and dye-pen pass/fail toggle directly in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

private fun renderWearBitmap(
    context: Context,
    spec: com.android.shaftschematic.model.ShaftSpec,
    project: ProjectInfo,
    unit: com.android.shaftschematic.util.UnitSystem,
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("wear_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeWearPdf(page = page, spec = spec, project = project, unit = unit)
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
