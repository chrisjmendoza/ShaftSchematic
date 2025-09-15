package com.android.shaftschematic.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.data.*
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.Units
import com.android.shaftschematic.util.ShaftPdfComposer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val spec by viewModel.spec.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val referenceEnd by viewModel.referenceEnd.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val vessel by viewModel.vessel.collectAsStateWithLifecycle()
    val jobNumber by viewModel.jobNumber.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val side by viewModel.side.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()

    val opts = remember(referenceEnd, showGrid) {
        RenderOptions(
            targetWidthInches = null,
            maxHeightInches = 2f,
            paddingPx = (16 * context.resources.displayMetrics.density).toInt(),
            referenceEnd = referenceEnd,
            lineWidthPx = 4f,
            dimLineWidthPx = 3f,
            textSizePx = 34f,
            showGrid = showGrid
        )
    }

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
                    optsBase = opts.copy(showGrid = false, targetWidthInches = 10f),
                    leftEnd = EndInfo(),
                    middle = MetaBlock(customer, vessel, jobNumber, side, date),
                    rightEnd = EndInfo()
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Drawing") },
                actions = {
                    // Units selector
                    var unitsOpen by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitsOpen,
                        onExpandedChange = { unitsOpen = !unitsOpen }
                    ) {
                        OutlinedTextField(
                            value = if (units == Units.MM) "mm" else "in",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Units") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitsOpen) },
                            modifier = Modifier
                                .width(120.dp)
                                .menuAnchor() // <- scope extension; keep it last in the chain
                        )
                        ExposedDropdownMenu(
                            expanded = unitsOpen,
                            onDismissRequest = { unitsOpen = false }
                        ) {
                            DropdownMenuItem(text = { Text("Millimeters (mm)") },
                                onClick = { viewModel.setUnits(Units.MM); unitsOpen = false })
                            DropdownMenuItem(text = { Text("Inches (in)") },
                                onClick = { viewModel.setUnits(Units.IN); unitsOpen = false })
                        }
                    }

                    // Ref toggle
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ShaftDrawing(
                    spec = spec,
                    opts = opts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(8.dp)
                )
            }

            // Shaft
            item {
                CollapsibleSection(title = "Shaft") {
                    NumberField(
                        label = "Overall Length (${unitsLabel(units)})",
                        modelValue = viewModel.toUiUnits(spec.overallLengthMm),
                        onChange = { f -> viewModel.setOverallLength(f ?: 0f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bodies
            item {
                if (spec.bodies.isNotEmpty()) {
                    CollapsibleSection(title = "Bodies") {
                        spec.bodies.forEachIndexed { index, b ->
                            ComponentCard(
                                title = "Body ${index + 1}",
                                onRemove = { viewModel.removeBody(index) }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(b.startFromAftMm),
                                        onChange = { f -> viewModel.updateBody(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(b.lengthMm),
                                        onChange = { f -> viewModel.updateBody(index, lenUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Diameter (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(b.diaMm),
                                        onChange = { f -> viewModel.updateBody(index, diaUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tapers
            item {
                if (spec.tapers.isNotEmpty()) {
                    CollapsibleSection(title = "Tapers") {
                        spec.tapers.forEachIndexed { index, t ->
                            ComponentCard(
                                title = "Taper ${index + 1}",
                                onRemove = { viewModel.removeTaper(index) }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(t.startFromAftMm),
                                        onChange = { f -> viewModel.updateTaper(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(t.lengthMm),
                                        onChange = { f -> viewModel.updateTaper(index, lenUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        label = "Start Ø (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(t.startDiaMm),
                                        onChange = { f -> viewModel.updateTaper(index, startDiaUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "End Ø (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(t.endDiaMm),
                                        onChange = { f -> viewModel.updateTaper(index, endDiaUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Threads
            item {
                if (spec.threads.isNotEmpty()) {
                    CollapsibleSection(title = "Threads") {
                        spec.threads.forEachIndexed { index, th ->
                            ComponentCard(
                                title = "Thread ${index + 1}",
                                onRemove = { viewModel.removeThread(index) }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    var endOpen by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = endOpen,
                                        onExpandedChange = { endOpen = !endOpen }
                                    ) {
                                        OutlinedTextField(
                                            value = th.endLabel.ifBlank { "Select end" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("End") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endOpen) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .menuAnchor() // <- scope extension; last in chain
                                        )
                                        ExposedDropdownMenu(
                                            expanded = endOpen,
                                            onDismissRequest = { endOpen = false }
                                        ) {
                                            DropdownMenuItem(text = { Text("AFT") }, onClick = {
                                                viewModel.updateThread(index, endLabel = "AFT"); endOpen = false
                                            })
                                            DropdownMenuItem(text = { Text("FWD") }, onClick = {
                                                viewModel.updateThread(index, endLabel = "FWD"); endOpen = false
                                            })
                                        }
                                    }
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(th.startFromAftMm),
                                        onChange = { f -> viewModel.updateThread(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        label = "Major Ø (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(th.majorDiaMm),
                                        onChange = { f -> viewModel.updateThread(index, majorDiaUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Pitch (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(th.pitchMm),
                                        onChange = { f -> viewModel.updateThread(index, pitchUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(th.lengthMm),
                                        onChange = { f -> viewModel.updateThread(index, lenUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Liners
            item {
                if (spec.liners.isNotEmpty()) {
                    CollapsibleSection(title = "Liners") {
                        spec.liners.forEachIndexed { index, ln ->
                            ComponentCard(
                                title = "Liner ${index + 1}",
                                onRemove = { viewModel.removeLiner(index) }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(ln.startFromAftMm),
                                        onChange = { f -> viewModel.updateLiner(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(ln.lengthMm),
                                        onChange = { f -> viewModel.updateLiner(index, lenUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Outer Ø (${unitsLabel(units)})",
                                        modelValue = viewModel.toUiUnits(ln.odMm),
                                        onChange = { f -> viewModel.updateLiner(index, odUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Job / Notes
            item {
                CollapsibleSection(title = "Job / Notes", initiallyExpanded = false) {
                    TextFieldRow("Customer", customer, viewModel::setCustomer)
                    TextFieldRow("Vessel", vessel, viewModel::setVessel)
                    TextFieldRow("Job Number", jobNumber, viewModel::setJobNumber)
                    TextFieldRow("Port/Starboard", side, viewModel::setSide)
                    TextFieldRow("Date", date, viewModel::setDate)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { if (it.length <= 2000) viewModel.setNotes(it) },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 8
                    )
                }
            }

            // Add component row
            item {
                AddComponentRow(
                    onAddBody = viewModel::addBody,
                    onAddTaper = viewModel::addTaper,
                    onAddThread = viewModel::addThread,
                    onAddLiner = viewModel::addLiner
                )
            }
        }
    }
}

/* ==============================  Collapsible / Cards  ============================== */

@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

@Composable
private fun ComponentCard(
    title: String,
    onRemove: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
            content()
        }
    }
}

/* ==============================  Fields  ============================== */

@Composable
private fun NumberField(
    label: String,
    modelValue: Float,                    // current value from model in UI units
    onChange: (Float?) -> Unit,           // null when cleared
    modifier: Modifier = Modifier
) {
    // Keep editable text that can be empty; reset when modelValue changes
    val modelText = modelValue.toTrim()
    var text by remember(modelText) { mutableStateOf(modelText) }

    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            if (s.isBlank()) {
                text = ""
                onChange(null)
            } else if (s.isValidDecimal()) {
                text = s
                s.toFloatOrNull()?.let { onChange(it) }
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next
        ),
        modifier = modifier
    )
}

@Composable
private fun TextFieldRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/* ==============================  Add Component  ============================== */

@Composable
private fun AddComponentRow(
    onAddBody: () -> Unit,
    onAddTaper: () -> Unit,
    onAddThread: () -> Unit,
    onAddLiner: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ExtendedFloatingActionButton(
            onClick = { open = !open },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
            text = { Text("Add component") }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Body") }, onClick = { open = false; onAddBody() })
            DropdownMenuItem(text = { Text("Taper") }, onClick = { open = false; onAddTaper() })
            DropdownMenuItem(text = { Text("Thread") }, onClick = { open = false; onAddThread() })
            DropdownMenuItem(text = { Text("Liner") }, onClick = { open = false; onAddLiner() })
        }
    }
}

/* ==============================  Helpers  ============================== */

private fun unitsLabel(u: Units) = if (u == Units.MM) "mm" else "in"

private fun String.isValidDecimal(): Boolean {
    var dot = false
    for (c in this) {
        if (c.isDigit()) continue
        if (c == '.' && !dot) { dot = true; continue }
        return false
    }
    return true
}

private fun Float.toTrim(): String =
    if (this % 1f == 0f) this.toInt().toString() else this.toString()

private fun Float?.toUnits(u: Units): Float? =
    this?.let { if (u == Units.MM) it else it * 25.4f }

private fun defaultPdfName(spec: ShaftSpecMm, job: String): String {
    val j = if (job.isBlank()) "Job" else job
    return "Shaft_${j}_${spec.overallLengthMm.toInt()}mm.pdf"
}
