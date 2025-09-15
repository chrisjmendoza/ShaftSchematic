package com.android.shaftschematic.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.MetaBlock
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.Units
import com.android.shaftschematic.util.ShaftPdfComposer
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ----- ViewModel state
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

    // ----- Render options for the preview canvas
    val opts = remember(referenceEnd, showGrid, units) {
        RenderOptions(
            targetWidthInches = null,
            maxHeightInches = 2f,
            paddingPx = (16 * context.resources.displayMetrics.density).toInt(),
            referenceEnd = referenceEnd,
            lineWidthPx = 3f,
            dimLineWidthPx = 1.5f,
            textSizePx = 34f,
            showGrid = showGrid,

            // grid + legend (unit-aware majors)
            gridUseInches = (units == Units.IN),
            gridDesiredMajors = 20,
            gridMinorsPerMajor = 4,
            gridMinorStrokePx = 0.5f,
            gridMajorStrokePx = 1f,
            gridMinorColor = 0x66888888.toInt(),
            gridMajorColor = 0x88888888.toInt(),
            showGridLegend = true
        )
    }

    // ----- PDF exporter
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
                    units = units,
                    middle = MetaBlock(customer, vessel, jobNumber, side, date)
                )
            }
        }
    }

    // ----- Bottom sheet visibility for the FAB
    var addMenuOpen by remember { mutableStateOf(false) }

    // ----- Small helper for coverage chip
    fun coverageEndMm(spec: ShaftSpecMm): Float {
        var far = 0f
        spec.bodies.forEach { far = max(far, it.startFromAftMm + it.lengthMm) }
        spec.tapers.forEach { far = max(far, it.startFromAftMm + it.lengthMm) }
        spec.threads.forEach { far = max(far, it.startFromAftMm + it.lengthMm) }
        spec.liners.forEach { far = max(far, it.startFromAftMm + it.lengthMm) }
        spec.aftTaper?.let { far = max(far, it.startFromAftMm + it.lengthMm) }
        spec.forwardTaper?.let { far = max(far, it.startFromAftMm + it.lengthMm) }
        return far
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
                            modifier = Modifier.width(120.dp).menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                            DropdownMenuItem(text = { Text("Millimeters (mm)") }, onClick = {
                                viewModel.setUnits(Units.MM); unitsOpen = false
                            })
                            DropdownMenuItem(text = { Text("Inches (in)") }, onClick = {
                                viewModel.setUnits(Units.IN); unitsOpen = false
                            })
                        }
                    }

                    // Reference end toggle
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

                    // Export PDF
                    TextButton(onClick = { savePdf.launch(defaultPdfName(spec, jobNumber)) }) {
                        Text("Export PDF")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addMenuOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add component")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->

        // Full-width content (no extra end padding)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Preview
            item {
                ShaftDrawing(
                    spec = spec,
                    opts = opts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)  // reduced height as requested earlier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Coverage hint (chip)
            item {
                val coverageMm = remember(spec) { coverageEndMm(spec) }
                val overallMm = spec.overallLengthMm
                val deltaMm = coverageMm - overallMm

                if (overallMm > 0f && abs(deltaMm) >= 0.1f) {
                    val isShort = deltaMm < 0f
                    val remainingUi = viewModel.toUiUnits(abs(deltaMm))
                    val formatted = if (units == Units.MM) "%.1f mm".format(remainingUi) else "%.3f in".format(remainingUi)

                    AssistChip(
                        onClick = {},
                        label = { Text(if (isShort) "Components end $formatted before overall" else "Components exceed overall by $formatted") },
                        leadingIcon = {
                            Icon(if (isShort) Icons.Filled.Info else Icons.Filled.Warning, contentDescription = null)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Shaft (overall length)
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
            if (spec.bodies.isNotEmpty()) {
                item {
                    CollapsibleSection(title = "Bodies") {
                        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
                        spec.bodies.forEachIndexed { index, b ->
                            val startUi = viewModel.toUiUnits(b.startFromAftMm)
                            val lenUi = viewModel.toUiUnits(b.lengthMm)
                            val err = endExceedsOverall(startUi, lenUi, overallUi)
                            ComponentCard(title = "Body ${index + 1}", onRemove = { viewModel.removeBody(index) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = startUi,
                                        onChange = { f -> viewModel.updateBody(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = lenUi,
                                        onChange = { f -> viewModel.updateBody(index, lenUi = f) },
                                        modifier = Modifier.weight(1f),
                                        isError = err,
                                        supportingText = if (err) "Start + length exceeds overall" else null
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
            if (spec.tapers.isNotEmpty()) {
                item {
                    CollapsibleSection(title = "Tapers") {
                        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
                        spec.tapers.forEachIndexed { index, t ->
                            val startUi = viewModel.toUiUnits(t.startFromAftMm)
                            val lenUi = viewModel.toUiUnits(t.lengthMm)
                            val err = endExceedsOverall(startUi, lenUi, overallUi)
                            ComponentCard(title = "Taper ${index + 1}", onRemove = { viewModel.removeTaper(index) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = startUi,
                                        onChange = { f -> viewModel.updateTaper(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = lenUi,
                                        onChange = { f -> viewModel.updateTaper(index, lenUi = f) },
                                        modifier = Modifier.weight(1f),
                                        isError = err,
                                        supportingText = if (err) "Start + length exceeds overall" else null
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (spec.threads.isNotEmpty()) {
                item {
                    CollapsibleSection(title = "Threads") {
                        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
                        spec.threads.forEachIndexed { index, th ->
                            val startUi = viewModel.toUiUnits(th.startFromAftMm)
                            val lenUi = viewModel.toUiUnits(th.lengthMm)
                            val err = endExceedsOverall(startUi, lenUi, overallUi)

                            ComponentCard(title = "Thread ${index + 1}", onRemove = { viewModel.removeThread(index) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var endOpen by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(expanded = endOpen, onExpandedChange = { endOpen = !endOpen }) {
                                        OutlinedTextField(
                                            value = th.endLabel.ifBlank { "Select end" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("End") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endOpen) },
                                            modifier = Modifier.weight(1f).menuAnchor()
                                        )
                                        ExposedDropdownMenu(expanded = endOpen, onDismissRequest = { endOpen = false }) {
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
                                        modelValue = startUi,
                                        onChange = { f -> viewModel.updateThread(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        modelValue = lenUi,
                                        onChange = { f -> viewModel.updateThread(index, lenUi = f) },
                                        modifier = Modifier.weight(1f),
                                        isError = err,
                                        supportingText = if (err) "Start + length exceeds overall" else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Liners
            if (spec.liners.isNotEmpty()) {
                item {
                    CollapsibleSection(title = "Liners") {
                        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
                        spec.liners.forEachIndexed { index, ln ->
                            val startUi = viewModel.toUiUnits(ln.startFromAftMm)
                            val lenUi = viewModel.toUiUnits(ln.lengthMm)
                            val err = endExceedsOverall(startUi, lenUi, overallUi)

                            ComponentCard(title = "Liner ${index + 1}", onRemove = { viewModel.removeLiner(index) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(
                                        label = "Start from AFT (${unitsLabel(units)})",
                                        modelValue = startUi,
                                        onChange = { f -> viewModel.updateLiner(index, startUi = f) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberField(
                                        label = "Length (${unitsLabel(units)})",
                                        modelValue = lenUi,
                                        onChange = { f -> viewModel.updateLiner(index, lenUi = f) },
                                        modifier = Modifier.weight(1f),
                                        isError = err,
                                        supportingText = if (err) "Start + length exceeds overall" else null
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 8
                    )
                }
            }
        }

        // Bottom sheet for adding components
        AddComponentSheet(
            open = addMenuOpen,
            onDismiss = { addMenuOpen = false },
            onAddBody = viewModel::addBody,
            onAddTaper = viewModel::addTaper,
            onAddThread = viewModel::addThread,
            onAddLiner = viewModel::addLiner
        )
    }
}

/* ==============================  Bottom Sheet  ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddComponentSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onAddBody: () -> Unit,
    onAddTaper: () -> Unit,
    onAddThread: () -> Unit,
    onAddLiner: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (open) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add component", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onDismiss(); onAddBody() }, modifier = Modifier.fillMaxWidth()) { Text("Body") }
                Button(onClick = { onDismiss(); onAddTaper() }, modifier = Modifier.fillMaxWidth()) { Text("Taper") }
                Button(onClick = { onDismiss(); onAddThread() }, modifier = Modifier.fillMaxWidth()) { Text("Thread") }
                Button(onClick = { onDismiss(); onAddLiner() }, modifier = Modifier.fillMaxWidth()) { Text("Liner") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ==============================  Collapsibles / Cards  ============================== */

@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
            content()
        }
    }
}

/* ==============================  Fields  ============================== */

@Composable
private fun NumberField(
    label: String,
    modelValue: Float,              // UI units
    onChange: (Float?) -> Unit,     // null when cleared
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val formattedModel = remember(modelValue) { modelValue.toDisplayString() }
    var text by remember(formattedModel) { mutableStateOf(formattedModel) }
    var wasFocused by remember { mutableStateOf(false) }

    fun commit() {
        val t = text.trim()
        if (t.isEmpty()) { onChange(null); return }
        onChange(t.toFloatOrNull())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            if (s.isBlank()) { text = ""; return@OutlinedTextField }
            if (s.isValidDecimal()) text = s
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = { if (supportingText != null) Text(supportingText) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { commit(); focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            onDone = { commit(); focusManager.clearFocus() }
        ),
        modifier = modifier.onFocusChanged { st ->
            if (wasFocused && !st.isFocused) commit()
            wasFocused = st.isFocused
        }
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

/* ==============================  Helpers  ============================== */

private fun unitsLabel(u: Units) = if (u == Units.MM) "mm" else "in"

private fun endExceedsOverall(startUi: Float, lenUi: Float, overallUi: Float): Boolean =
    startUi + lenUi > overallUi + 1e-6f

private fun String.isValidDecimal(): Boolean {
    var dot = false
    for (c in this) {
        if (c.isDigit()) continue
        if (c == '.' && !dot) { dot = true; continue }
        return false
    }
    return true
}

private fun Float.toDisplayString(): String {
    val df = DecimalFormat("#.###")
    df.isGroupingUsed = false
    return df.format(this)
}

private fun defaultPdfName(spec: ShaftSpecMm, job: String): String {
    val j = if (job.isBlank()) "Job" else job
    return "Shaft_${j}_${spec.overallLengthMm.toInt()}mm.pdf"
}
