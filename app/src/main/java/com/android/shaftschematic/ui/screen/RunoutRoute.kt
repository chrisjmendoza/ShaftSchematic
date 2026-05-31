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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.buildOpenPdfIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RunoutRoute
 *
 * Screen for the Runout Sheet document tab.
 *
 * ## Layout
 * - Brief description of the document's purpose.
 * - **TIR orientation** selector: "Looking AFT" or "Looking FORWARD".
 *   This controls the clock-position reference used when reading the dial indicator —
 *   e.g. "high at 3 o'clock looking aft" has the opposite physical meaning from
 *   "high at 3 o'clock looking forward". Printed at the bottom of the runout sheet.
 * - **Preview PDF** button — renders the PDF in memory and displays it full-screen
 *   so you can verify the layout before committing to an export.
 * - **Export PDF** button — opens the SAF file picker to save the file.
 *
 * ## Phase 2
 * This screen will host TIR value entry (typing readings into each bubble) and the
 * high-spot direction line selector once that feature is implemented.
 */
@Composable
fun RunoutRoute(
    vm: ShaftViewModel,
    onExportRunout: () -> Unit = {},
) {
    val spec          by vm.spec.collectAsState()
    val runoutConfig  by vm.runoutConfig.collectAsState()
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
                        composeRunoutPdf(
                            page = page, spec = spec, config = runoutConfig,
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
                if (openAfterExport) openRunoutPdf(ctx, uri)
            }
        }
    }

    // Render preview bitmap when the preview overlay is requested
    LaunchedEffect(showPreview, spec, runoutConfig, unit) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val bmp = withContext(Dispatchers.IO) {
            renderRunoutBitmap(
                context = ctx, spec = spec, config = runoutConfig,
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
        Text("Runout Sheet", style = MaterialTheme.typography.headlineSmall)

        Text(
            text = "A blank measurement form with reading stations marked at each component. " +
                "Print it, bring it to the ship, and record TIR readings by hand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── TIR orientation selector ─────────────────────────────────────────
        Text("TIR orientation", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Specifies the direction you face when reading the indicator. " +
                "This sets the clock-position reference — 3 o'clock looking aft is the opposite " +
                "physical location from 3 o'clock looking forward.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TirButton(label = "Looking AFT",     selected = runoutConfig.tirDirection == TirDirection.AFT,
                onClick = { vm.setTirDirection(TirDirection.AFT) })
            TirButton(label = "Looking FORWARD", selected = runoutConfig.tirDirection == TirDirection.FORWARD,
                onClick = { vm.setTirDirection(TirDirection.FORWARD) })
            TirButton(label = "Not set",         selected = runoutConfig.tirDirection == TirDirection.UNSET,
                onClick = { vm.setTirDirection(TirDirection.UNSET) })
        }

        Spacer(Modifier.height(4.dp))

        // ── Preview button ───────────────────────────────────────────────────
        OutlinedButton(
            onClick = { showPreview = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Preview, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Preview Runout Sheet")
        }

        // ── Export button ────────────────────────────────────────────────────
        Button(
            onClick = { launcher.launch(buildRunoutFilename(customer, vessel, jobNumber)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Export Runout Sheet PDF")
        }
    }

    // ── Full-screen preview overlay ──────────────────────────────────────────
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
        )
    }
}

@Composable
private fun TirButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else TextButton(onClick = onClick) { Text(label) }
}

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

/**
 * Renders the runout PDF to a [Bitmap] using PdfDocument + PdfRenderer.
 * Must be called on a background thread (Dispatchers.IO).
 */
private fun renderRunoutBitmap(
    context: Context,
    spec: com.android.shaftschematic.model.ShaftSpec,
    config: com.android.shaftschematic.settings.RunoutConfig,
    project: ProjectInfo,
    unit: com.android.shaftschematic.util.UnitSystem,
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("runout_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeRunoutPdf(page = page, spec = spec, config = config, project = project, unit = unit)
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
 * @param bitmap   The rendered PDF page (null while rendering or on error).
 * @param loading  Whether the bitmap is still being generated.
 * @param title    Title shown in the top bar of the overlay.
 * @param onClose  Called when the user taps × or navigates back.
 * @param onExport Called when the user taps the Export button.
 */
@Composable
internal fun PdfPreviewOverlay(
    bitmap: ImageBitmap?,
    loading: Boolean,
    title: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
) {
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
                        // Zoom / pan state — reset when bitmap changes
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
}
