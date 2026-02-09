package com.android.shaftschematic.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.PdfPreviewResult
import com.android.shaftschematic.pdf.renderPdfPreviewPage
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: com.android.shaftschematic.settings.PdfPrefs,
    options: PdfExportOptions,
    resolvedComponents: List<ResolvedComponent>?,
    onClose: () -> Unit,
    onExportPdf: () -> Unit,
) {
    var preview: PdfPreviewResult? by remember { mutableStateOf(null) }
    var error: String? by remember { mutableStateOf(null) }
    val densityScale = LocalDensity.current.density

    LaunchedEffect(spec, unit, project, appVersion, filename, pdfPrefs, options, resolvedComponents) {
        preview = null
        error = null
        val scale = (densityScale + 0.5f).coerceIn(1f, 3f)
        runCatching {
            withContext(Dispatchers.Default) {
                renderPdfPreviewPage(
                    spec = spec,
                    unit = unit,
                    project = project,
                    appVersion = appVersion,
                    filename = filename,
                    pdfPrefs = pdfPrefs,
                    options = options,
                    resolvedComponents = resolvedComponents,
                    renderScale = scale,
                )
            }
        }.onSuccess { result ->
            preview = result
        }.onFailure { t ->
            error = "${t.javaClass.simpleName}: ${t.message ?: "Preview failed"}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Preview") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = onExportPdf) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Text("Export", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Preview failed", style = MaterialTheme.typography.titleMedium)
                        Text(error ?: "Unknown error", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onClose) { Text("Close") }
                    }
                }
                preview == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val bitmap = preview?.bitmap
                    if (bitmap != null) {
                        PdfPreviewImage(bitmap = bitmap)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewImage(bitmap: Bitmap) {
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scroll)
            .padding(PaddingValues(16.dp)),
        contentAlignment = Alignment.TopCenter
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF preview",
            modifier = Modifier.fillMaxWidth()
        )
    }
}
