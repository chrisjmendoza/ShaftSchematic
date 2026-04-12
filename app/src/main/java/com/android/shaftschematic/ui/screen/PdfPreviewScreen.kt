// file: app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt
package com.android.shaftschematic.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.pm.PackageManager

/**
 * PdfPreviewScreen
 *
 * Purpose
 * Shows a full-resolution preview of the PDF that will be exported, rendered in-memory
 * via PdfDocument + PdfRenderer. Supports pinch-to-zoom (and double-tap to reset) so
 * users can inspect dimension labels before committing to an export.
 *
 * Contract
 * - Rendering runs on Dispatchers.IO; a loading indicator is shown meanwhile.
 * - If rendering fails, a plain error message is shown (no crash).
 * - The "Export PDF" action in the top bar calls [onExport] to proceed to the SAF picker.
 * - No model state is mutated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current

    val spec by vm.spec.collectAsState()
    val unit by vm.unit.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()

    val project = remember(customer, vessel, shaftPosition, jobNumber) {
        ProjectInfo(customer = customer, vessel = vessel, side = shaftPosition, jobNumber = jobNumber)
    }
    val options = remember(pdfExportMode) { PdfExportOptions(mode = pdfExportMode) }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(spec, unit, project, options, resolvedComponents) {
        isLoading = true
        errorMessage = null
        val pdfPrefs = vm.currentPdfPrefs  // read on main thread before IO switch
        val bmp = withContext(Dispatchers.IO) {
            renderPdfPreviewBitmap(
                context = ctx,
                spec = spec,
                unit = unit,
                project = project,
                appVersion = appVersionName(ctx),
                pdfPrefs = pdfPrefs,
                options = options,
                resolvedComponents = resolvedComponents,
            )
        }
        if (bmp != null) {
            previewBitmap = bmp.asImageBitmap()
        } else {
            errorMessage = "Could not render preview."
        }
        isLoading = false
    }

    // ── Pan / Zoom state ──────────────────────────────────────────────────────
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val old = scale.value
                val new = (old * zoom).coerceIn(0.5f, 8.0f)
                val z = if (old != 0f) new / old else 1f
                val newOffset = offset.value * z + centroid * (1f - z) + pan
                scope.launch {
                    if (new != old) scale.snapTo(new)
                    offset.snapTo(newOffset)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    scope.launch {
                        scale.animateTo(1f, tween(140))
                        offset.animateTo(Offset.Zero, tween(140))
                    }
                }
            )
        }

    // ── UI ───────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                scale.animateTo(1f, tween(140))
                                offset.animateTo(Offset.Zero, tween(140))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom"
                        )
                    }
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Export PDF"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage ?: "Preview unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                previewBitmap != null -> {
                    val image = previewBitmap!!
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestures)
                    ) {
                        val imgW = image.width.toFloat()
                        val imgH = image.height.toFloat()

                        // Fit the PDF page to the canvas at zoom=1, centered.
                        val fitScale = minOf(size.width / imgW, size.height / imgH)
                        val fittedW = imgW * fitScale
                        val fittedH = imgH * fitScale
                        val baseLeft = (size.width - fittedW) / 2f
                        val baseTop = (size.height - fittedH) / 2f

                        withTransform({
                            translate(offset.value.x, offset.value.y)
                            scale(scale.value, scale.value, Offset.Zero)
                        }) {
                            drawImage(
                                image = image,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    baseLeft.toInt(),
                                    baseTop.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    fittedW.toInt(),
                                    fittedH.toInt()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Renders the shaft PDF to an Android [Bitmap] using [PdfDocument] + [PdfRenderer].
 *
 * Must be called off the main thread (use [Dispatchers.IO]).
 * Returns null on any failure so the caller can show an error message.
 */
private fun renderPdfPreviewBitmap(
    context: Context,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    pdfPrefs: PdfPrefs,
    options: PdfExportOptions,
    resolvedComponents: List<ResolvedComponent>,
): Bitmap? = runCatching {
    // Step 1 – compose the PDF into a temp file.
    val tempFile = File(context.cacheDir, "shaft_preview_tmp.pdf")
    val doc = PdfDocument()
    try {
        // US Letter landscape: 792 × 612 points (matches PdfExportRoute).
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeShaftPdf(
            page = page,
            spec = spec,
            unit = unit,
            project = project,
            appVersion = appVersion,
            filename = "preview",
            pdfPrefs = pdfPrefs,
            options = options,
            resolvedComponents = resolvedComponents.takeIf { it.isNotEmpty() },
        )
        doc.finishPage(page)
        tempFile.outputStream().buffered().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }

    // Step 2 – rasterise the first page via PdfRenderer at 2× resolution.
    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        val pdfPage = renderer.openPage(0)
        try {
            val renderScale = 2
            val bitmap = Bitmap.createBitmap(
                pdfPage.width * renderScale,
                pdfPage.height * renderScale,
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            pdfPage.close()
        }
    } finally {
        renderer.close()
        pfd.close()
        tempFile.delete()
    }
}.getOrNull()

private fun appVersionName(context: Context): String = runCatching {
    val pm = context.packageManager
    val pkg = context.packageName
    if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0"
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, 0).versionName ?: "0"
    }
}.getOrDefault("0")
