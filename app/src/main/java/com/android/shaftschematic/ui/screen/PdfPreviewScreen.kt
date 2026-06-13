// file: app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt
package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import com.android.shaftschematic.settings.PdfTieringMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlin.math.roundToInt

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

    // Unlock rotation for this screen only; restore portrait when leaving.
    val activity = ctx as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val spec by vm.spec.collectAsState()
    val unit by vm.unit.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val pdfShowComponentTitles by vm.pdfShowComponentTitles.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()

    val project = remember(customer, vessel, shaftPosition, jobNumber) {
        ProjectInfo(customer = customer, vessel = vessel, side = shaftPosition, jobNumber = jobNumber)
    }
    val options = remember(pdfExportMode) { PdfExportOptions(mode = pdfExportMode) }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(spec, unit, project, options, resolvedComponents, lineThicknessScale,
                   pdfShowComponentTitles, pdfTieringMode,
                   pdfShadedBodies, pdfShadedTapers, pdfShadedLiners) {
        isLoading = true
        errorMessage = null
        // Snapshot on the main thread before switching to IO.
        val pdfPrefsSnapshot = vm.currentPdfPrefs
        val thicknessScaleSnapshot = lineThicknessScale
        val bmp = withContext(Dispatchers.IO) {
            renderPdfPreviewBitmap(
                context = ctx,
                spec = spec,
                unit = unit,
                project = project,
                appVersion = appVersionName(ctx),
                pdfPrefs = pdfPrefsSnapshot,
                options = options,
                resolvedComponents = resolvedComponents,
                lineThicknessScale = thicknessScaleSnapshot,
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
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options")
                    }
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
        val currentError = errorMessage
        val currentBitmap = previewBitmap
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

                currentError != null -> {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                currentBitmap != null -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestures)
                    ) {
                        val imgW = currentBitmap.width.toFloat()
                        val imgH = currentBitmap.height.toFloat()

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
                                image = currentBitmap,
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

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState,
        ) {
            PdfOptionsSheet(
                vm = vm,
                pdfShowComponentTitles = pdfShowComponentTitles,
                pdfTieringMode = pdfTieringMode,
                lineThicknessScale = lineThicknessScale,
                pdfShadedBodies = pdfShadedBodies,
                pdfShadedTapers = pdfShadedTapers,
                pdfShadedLiners = pdfShadedLiners,
            )
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
    lineThicknessScale: Float = 1.0f,
): Bitmap? = runCatching {
    // Step 1 – compose the PDF into a temp file.
    // Use createTempFile so concurrent preview renders don't collide on the same path.
    val tempFile = File.createTempFile("shaft_preview_", ".pdf", context.cacheDir)
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
            lineThicknessScale = lineThicknessScale,
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

@Composable
private fun PdfOptionsSheet(
    vm: ShaftViewModel,
    pdfShowComponentTitles: Boolean,
    pdfTieringMode: PdfTieringMode,
    lineThicknessScale: Float,
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text("PDF Options", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // ── Labels ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pdfShowComponentTitles,
                onCheckedChange = { vm.setPdfShowComponentTitles(it) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Component labels", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Line thickness ───────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Line thickness  ${(lineThicknessScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = lineThicknessScale,
                onValueChange = { vm.setLineThicknessScale(it) },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("200%", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Measurement reference ────────────────────────────────────────────
        Text("Measurement reference", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        listOf(
            PdfTieringMode.AUTO to "Auto (closest end)",
            PdfTieringMode.AFT  to "AFT",
            PdfTieringMode.FWD  to "FWD",
        ).forEach { (mode, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = pdfTieringMode == mode,
                    onClick = { vm.setPdfTieringMode(mode) },
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
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
