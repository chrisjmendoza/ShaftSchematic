// file: ui/screen/TemplateScreen.kt
package com.android.shaftschematic.ui.screen

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.pdf.PdfExportMode
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.TEMPLATE_OAL_MM
import com.android.shaftschematic.ui.viewmodel.TemplateComponentType
import com.android.shaftschematic.ui.viewmodel.TemplateViewModel
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val DIA_MIN_MM = 10f
private const val DIA_MAX_MM = 200f
private const val LEN_MIN_MM = 10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    onBack: () -> Unit,
    vm: TemplateViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val spec by vm.spec.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val selectedType by vm.selectedType.collectAsState()

    val resolvedComponents = remember(spec) { resolveComponents(spec, overallIsManual = true) }

    // SAF launcher for PDF export
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val exportSpec = vm.spec.value
        scope.launch(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                    val page = doc.startPage(pageInfo)
                    try {
                        page.canvas.drawColor(Color.WHITE)
                        composeShaftPdf(
                            page = page,
                            spec = exportSpec,
                            unit = UnitSystem.MILLIMETERS,
                            project = ProjectInfo(),
                            appVersion = "",
                            filename = "ShaftTemplate.pdf",
                            options = PdfExportOptions(mode = PdfExportMode.BlankTemplate),
                            resolvedComponents = resolveComponents(exportSpec, overallIsManual = true),
                        )
                    } finally {
                        doc.finishPage(page)
                        doc.writeTo(out)
                        out.flush()
                        doc.close()
                    }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Template Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { pdfLauncher.launch("ShaftTemplate.pdf") }) {
                        Icon(Icons.Default.Print, contentDescription = "Print template")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ── Chip bar ──────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(onClick = { vm.addBody()   }, label = { Text("Body")   })
                AssistChip(onClick = { vm.addThread() }, label = { Text("Thread") })
                AssistChip(onClick = { vm.addTaper()  }, label = { Text("Taper")  })
                AssistChip(onClick = { vm.addLiner()  }, label = { Text("Liner")  })
            }

            // ── Drawing ───────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                ShaftDrawing(
                    spec = spec,
                    resolvedComponents = resolvedComponents,
                    unit = UnitSystem.MILLIMETERS,
                    showGrid = false,
                    showOalMarkers = false,
                    highlightEnabled = selectedId != null,
                    highlightId = selectedId,
                    onTapComponentId = { id -> vm.onTapComponent(id) },
                    onTapAtMm = { _ -> vm.clearSelection() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ── Bottom sheet for selected component ───────────────────────────
    if (selectedId != null && selectedType != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.clearSelection() },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            TemplateComponentSheet(
                spec = spec,
                selectedId = selectedId!!,
                selectedType = selectedType!!,
                oalMm = TEMPLATE_OAL_MM,
                vm = vm,
            )
        }
    }
}

@Composable
private fun TemplateComponentSheet(
    spec: com.android.shaftschematic.model.ShaftSpec,
    selectedId: String,
    selectedType: TemplateComponentType,
    oalMm: Float,
    vm: TemplateViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = selectedType.displayName(),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))

        when (selectedType) {
            TemplateComponentType.BODY -> {
                val body = spec.bodies.find { it.id == selectedId } ?: return@Column
                LabeledSlider("Length", body.lengthMm, LEN_MIN_MM, oalMm) {
                    vm.updateBodyLength(selectedId, it)
                }
                LabeledSlider("Diameter", body.diaMm, DIA_MIN_MM, DIA_MAX_MM) {
                    vm.updateBodyDia(selectedId, it)
                }
                LabeledSlider("Position (from AFT)", body.startFromAftMm, 0f, oalMm - body.lengthMm.coerceAtLeast(LEN_MIN_MM)) {
                    vm.updateBodyPosition(selectedId, it)
                }
            }
            TemplateComponentType.THREAD -> {
                val thread = spec.threads.find { it.id == selectedId } ?: return@Column
                LabeledSlider("Length", thread.lengthMm, LEN_MIN_MM, oalMm) {
                    vm.updateThreadLength(selectedId, it)
                }
                LabeledSlider("Diameter", thread.majorDiaMm, DIA_MIN_MM, DIA_MAX_MM) {
                    vm.updateThreadDia(selectedId, it)
                }
                LabeledSlider("Position (from AFT)", thread.startFromAftMm, 0f, (oalMm - thread.lengthMm).coerceAtLeast(0f)) {
                    vm.updateThreadPosition(selectedId, it)
                }
            }
            TemplateComponentType.TAPER -> {
                val taper = spec.tapers.find { it.id == selectedId } ?: return@Column
                LabeledSlider("Length", taper.lengthMm, LEN_MIN_MM, oalMm) {
                    vm.updateTaperLength(selectedId, it)
                }
                LabeledSlider("Large end diameter", taper.startDiaMm, DIA_MIN_MM, DIA_MAX_MM) {
                    vm.updateTaperStartDia(selectedId, it)
                }
                LabeledSlider("Small end diameter", taper.endDiaMm, DIA_MIN_MM, DIA_MAX_MM) {
                    vm.updateTaperEndDia(selectedId, it)
                }
                LabeledSlider("Position (from AFT)", taper.startFromAftMm, 0f, (oalMm - taper.lengthMm).coerceAtLeast(0f)) {
                    vm.updateTaperPosition(selectedId, it)
                }
            }
            TemplateComponentType.LINER -> {
                val liner = spec.liners.find { it.id == selectedId } ?: return@Column
                LabeledSlider("Length", liner.lengthMm, LEN_MIN_MM, oalMm) {
                    vm.updateLinerLength(selectedId, it)
                }
                LabeledSlider("Outer diameter", liner.odMm, DIA_MIN_MM, DIA_MAX_MM) {
                    vm.updateLinerOd(selectedId, it)
                }
                LabeledSlider("Position (from AFT)", liner.startFromAftMm, 0f, (oalMm - liner.lengthMm).coerceAtLeast(0f)) {
                    vm.updateLinerPosition(selectedId, it)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        TextButton(
            onClick = { vm.removeSelected() },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(4.dp))
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Overload to accept min/max floats directly
@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    val range = min..max.coerceAtLeast(min + 1f)
    LabeledSlider(label, value, range, onValueChange)
}

private fun TemplateComponentType.displayName(): String = when (this) {
    TemplateComponentType.BODY   -> "Body"
    TemplateComponentType.TAPER  -> "Taper"
    TemplateComponentType.THREAD -> "Thread"
    TemplateComponentType.LINER  -> "Liner"
}
