package com.android.shaftschematic.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.util.ShaftPdfComposer
import kotlinx.coroutines.launch
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(
    viewModel: ShaftViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // ----- Observe VM state -----
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    val referenceEnd by viewModel.referenceEnd.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val vessel by viewModel.vessel.collectAsStateWithLifecycle()
    val jobNumber by viewModel.jobNumber.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    // Render options for preview
    val opts = remember(referenceEnd, showGrid) {
        RenderOptions(
            targetWidthInches = null, // fit screen
            maxHeightInches = 2f,
            paddingPx = (16 * context.resources.displayMetrics.density).toInt(),
            referenceEnd = referenceEnd,
            lineWidthPx = 4f,
            dimLineWidthPx = 3f,
            textSizePx = 34f,
            showGrid = showGrid
        )
    }

    // Export launcher
    val savePdf = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                ShaftPdfComposer.write(
                    context = context,
                    target = uri,
                    spec = spec,
                    pageWidthIn = 11f,
                    pageHeightIn = 8.5f,
                    drawingWidthIn = 10f,
                    drawingMaxHeightIn = 2f,
                    optsBase = opts.copy(showGrid = false, targetWidthInches = 10f)
                )
                // (Optional) snackbar/Toast on success
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Drawing") },
                actions = {
                    // AFT/FWD toggle
                    var refMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { refMenu = true }) { Text("Ref: ${referenceEnd.name}") }
                    DropdownMenu(expanded = refMenu, onDismissRequest = { refMenu = false }) {
                        DropdownMenuItem(text = { Text("AFT") }, onClick = {
                            viewModel.setReferenceEnd(ReferenceEnd.AFT); refMenu = false
                        })
                        DropdownMenuItem(text = { Text("FWD") }, onClick = {
                            viewModel.setReferenceEnd(ReferenceEnd.FWD); refMenu = false
                        })
                    }

                    // Grid toggle
                    TextButton(onClick = { viewModel.setShowGrid(!showGrid) }) {
                        Text(if (showGrid) "Grid On" else "Grid Off")
                    }

                    // Export
                    TextButton(onClick = { savePdf.launch(defaultPdfName(spec, jobNumber)) }) {
                        Text("Export PDF")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Preview
            ShaftDrawing(
                spec = spec,
                opts = opts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp)
            )

            Divider()

            // Taper editor block (covers aft/forward & list tapers)
            TaperEditorBlock(
                spec = spec,
                onChange = { viewModel.setSpec(it) }
            )

            Divider()

            // Meta section (stored in VM separate from spec)
            MetaSection(
                customer = customer, onCustomerChange = viewModel::setCustomer,
                vessel = vessel, onVesselChange = viewModel::setVessel,
                jobNumber = jobNumber, onJobNumberChange = viewModel::setJobNumber,
                notes = notes, onNotesChange = viewModel::setNotes
            )
        }
    }
}

/* ==============================  UI Blocks  ============================== */

@Composable
private fun MetaSection(
    customer: String, onCustomerChange: (String) -> Unit,
    vessel: String, onVesselChange: (String) -> Unit,
    jobNumber: String, onJobNumberChange: (String) -> Unit,
    notes: String, onNotesChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Job / Notes", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = customer, onValueChange = onCustomerChange,
            label = { Text("Customer") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = vessel, onValueChange = onVesselChange,
            label = { Text("Vessel") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = jobNumber, onValueChange = onJobNumberChange,
            label = { Text("Job Number") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { if (it.length <= 2000) onNotesChange(it) },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 8
        )
        AssistChip(onClick = {}, label = { Text("Notes length: ${notes.length} / 2000") })
    }
}

@Composable
private fun TaperEditorBlock(
    spec: ShaftSpecMm,
    onChange: (ShaftSpecMm) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tapers", style = MaterialTheme.typography.titleMedium)

        val items = remember(spec) {
            buildList {
                spec.aftTaper?.let { add(IndexedTaper(-1, it, "Aft Taper")) }
                spec.forwardTaper?.let { add(IndexedTaper(-2, it, "Forward Taper")) }
                spec.tapers.forEachIndexed { idx, t -> add(IndexedTaper(idx, t, "Taper ${idx + 1}")) }
            }
        }

        items.forEach { it ->
            TaperRow(
                label = it.label,
                taper = it.taper,
                onApply = { updated ->
                    when (it.index) {
                        -1 -> onChange(spec.copy(aftTaper = updated))
                        -2 -> onChange(spec.copy(forwardTaper = updated))
                        else -> {
                            val list = spec.tapers.toMutableList()
                            if (it.index in list.indices) list[it.index] = updated
                            onChange(spec.copy(tapers = list))
                        }
                    }
                }
            )
        }
    }
}

private data class IndexedTaper(val index: Int, val taper: TaperSpec, val label: String)

@Composable
private fun TaperRow(
    label: String,
    taper: TaperSpec,
    onApply: (TaperSpec) -> Unit
) {
    var startDia by remember(taper) { mutableStateOf(taper.startDiaMm.toTrim()) }
    var length by remember(taper) { mutableStateOf(taper.lengthMm.toTrim()) }
    var rateText by remember(taper) { mutableStateOf(deriveRateText(taper)) }

    val endDiaComputed by remember(startDia, length, rateText) {
        mutableStateOf(runCatching {
            val s = startDia.toFloat()
            val l = length.toFloat()
            val slope = parseTaperRate(rateText) // ΔD per L
            (s - l * slope).coerceAtLeast(0f)
        }.getOrElse { taper.endDiaMm })
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startDia,
                onValueChange = { startDia = it.onlyNumber() },
                label = { Text("Start Ø (mm)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = length,
                onValueChange = { length = it.onlyNumber() },
                label = { Text("Length (mm)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it.trim() },
                label = { Text("Taper rate (1:N or dec)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = endDiaComputed.toTrim(),
            onValueChange = {},
            label = { Text("End Ø (computed mm)") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                runCatching {
                    onApply(
                        taper.copy(
                            startDiaMm = startDia.toFloat(),
                            endDiaMm = endDiaComputed,
                            lengthMm = length.toFloat()
                        )
                    )
                }
            }) { Text("Apply") }
        }
        Divider(Modifier.padding(vertical = 8.dp))
    }
}

/* ==============================  Helpers  ============================== */

private fun defaultPdfName(spec: ShaftSpecMm, job: String): String {
    val j = if (job.isBlank()) "Job" else job
    return "Shaft_${j}_${spec.overallLengthMm.toInt()}mm.pdf"
}

private fun parseTaperRate(raw: String): Float {
    val s = raw.trim().lowercase()
    if (s.isBlank()) return 0f
    val colon = s.split(":")
    if (colon.size == 2) {
        val a = colon[0].toFloatOrNull()
        val b = colon[1].toFloatOrNull()
        if (a != null && b != null && a > 0 && b > 0) return a / b // 1:10 -> 0.1
    }
    val slash = s.split("/")
    if (slash.size == 2) {
        val a = slash[0].toFloatOrNull()
        val b = slash[1].toFloatOrNull()
        if (a != null && b != null && a > 0 && b > 0) return a / b
    }
    return s.toFloatOrNull() ?: 0f
}

private fun deriveRateText(t: TaperSpec): String {
    val len = t.lengthMm
    if (len <= 0f) return ""
    val delta = (t.startDiaMm - t.endDiaMm).coerceAtLeast(0f)
    val slope = delta / len
    if (slope <= 0f) return ""
    val n = 1f / slope
    return "1:${round(n).toInt().coerceAtLeast(1)}"
}

private fun String.onlyNumber(): String {
    val out = StringBuilder()
    var dot = false
    for (c in this) {
        if (c.isDigit()) out.append(c)
        else if (c == '.' && !dot) { out.append('.'); dot = true }
    }
    return out.toString()
}
private fun Float.toTrim(): String = if (this % 1f == 0f) this.toInt().toString() else this.toString()
