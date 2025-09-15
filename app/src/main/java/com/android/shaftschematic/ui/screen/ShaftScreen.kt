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
import androidx.compose.ui.platform.LocalFocusManager
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
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- VM state
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

    // Optional end-panel inputs you kept:
    val aftLet by viewModel.aftLet.collectAsStateWithLifecycle()
    val aftSet by viewModel.aftSet.collectAsStateWithLifecycle()
    val aftTaperRate by viewModel.aftTaperRate.collectAsStateWithLifecycle()
    val aftKeyway by viewModel.aftKeyway.collectAsStateWithLifecycle()
    val aftThreads by viewModel.aftThreads.collectAsStateWithLifecycle()
    val fwdLet by viewModel.fwdLet.collectAsStateWithLifecycle()
    val fwdSet by viewModel.fwdSet.collectAsStateWithLifecycle()
    val fwdTaperRate by viewModel.fwdTaperRate.collectAsStateWithLifecycle()
    val fwdKeyway by viewModel.fwdKeyway.collectAsStateWithLifecycle()
    val fwdThreads by viewModel.fwdThreads.collectAsStateWithLifecycle()

    // ---- Render options for preview
    val opts = remember(referenceEnd, showGrid, units) {
        RenderOptions(
            targetWidthInches = null,
            maxHeightInches = 2f,
            paddingPx = (16 * context.resources.displayMetrics.density).toInt(),
            referenceEnd = referenceEnd,
            lineWidthPx = 4f,
            dimLineWidthPx = 3f,
            textSizePx = 34f,
            showGrid = showGrid,

            // grid + legend
            gridUseInches = (units == Units.IN),
            gridDesiredMajors = 20,
            gridMinorsPerMajor = 4,
            gridMinorStrokePx = 1f,
            gridMajorStrokePx = 1.5f,
            gridMinorColor = 0x66888888.toInt(),
            gridMajorColor = 0x99888888.toInt(),
            showGridLegend = true
        )
    }

    // ---- Snackbar host for clamping/exceed messages
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- PDF exporter
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

    // ---- helpers
    fun coverageEndMm(spec: ShaftSpecMm): Float {
        var far = 0f
        spec.bodies.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        spec.tapers.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        spec.threads.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        spec.liners.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        spec.aftTaper?.let { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        spec.forwardTaper?.let { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
        return far
    }

    fun clampLenUi(startUi: Float, desiredLenUi: Float, overallUi: Float): Float =
        (overallUi - startUi).coerceAtLeast(0f).coerceAtMost(desiredLenUi)

    suspend fun showClampedSnack() {
        snackbarHostState.showSnackbar(
            message = "Length clamped to overall shaft length",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
    }

    // ---- Add-with-clamp helpers (start at previous end; clamp default length)
    fun addBodyClamped() {
        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
        val startUi = min(overallUi, viewModel.toUiUnits(coverageEndMm(spec)))
        val newIndex = spec.bodies.size // will be last after add
        viewModel.addBody()
        val defaultLenUi = if (units == Units.MM) 25f else 1f // small sane stub
        val lenUi = clampLenUi(startUi, defaultLenUi, overallUi)
        viewModel.updateBody(newIndex, startUi = startUi, lenUi = lenUi)
    }
    fun addTaperClamped() {
        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
        val startUi = min(overallUi, viewModel.toUiUnits(coverageEndMm(spec)))
        val newIndex = spec.tapers.size
        viewModel.addTaper()
        val defaultLenUi = if (units == Units.MM) 25f else 1f
        val lenUi = clampLenUi(startUi, defaultLenUi, overallUi)
        viewModel.updateTaper(newIndex, startUi = startUi, lenUi = lenUi)
    }
    fun addThreadClamped() {
        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
        val startUi = min(overallUi, viewModel.toUiUnits(coverageEndMm(spec)))
        val newIndex = spec.threads.size
        viewModel.addThread()
        val defaultLenUi = if (units == Units.MM) 12f else 0.5f
        val lenUi = clampLenUi(startUi, defaultLenUi, overallUi)
        viewModel.updateThread(newIndex, startUi = startUi, lenUi = lenUi, endLabel = "AFT")
    }
    fun addLinerClamped() {
        val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
        val startUi = min(overallUi, viewModel.toUiUnits(coverageEndMm(spec)))
        val newIndex = spec.liners.size
        viewModel.addLiner()
        val defaultLenUi = if (units == Units.MM) 25f else 1f
        val lenUi = clampLenUi(startUi, defaultLenUi, overallUi)
        viewModel.updateLiner(newIndex, startUi = startUi, lenUi = lenUi)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Drawing") },
                actions = {
                    // Units selector
                    var unitsOpen by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = unitsOpen, onExpandedChange = { unitsOpen = !unitsOpen }) {
                        OutlinedTextField(
                            value = if (units == Units.MM) "mm" else "in",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Units") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitsOpen) },
                            modifier = Modifier.width(120.dp).menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                            DropdownMenuItem(text = { Text("Millimeters (mm)") }, onClick = { viewModel.setUnits(Units.MM); unitsOpen = false })
                            DropdownMenuItem(text = { Text("Inches (in)") }, onClick = { viewModel.setUnits(Units.IN); unitsOpen = false })
                        }
                    }

                    // Ref toggle
                    var refMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { refMenu = true }) { Text("Ref: ${referenceEnd.name}") }
                    DropdownMenu(expanded = refMenu, onDismissRequest = { refMenu = false }) {
                        DropdownMenuItem(text = { Text("AFT") }, onClick = { viewModel.setReferenceEnd(ReferenceEnd.AFT); refMenu = false })
                        DropdownMenuItem(text = { Text("FWD") }, onClick = { viewModel.setReferenceEnd(ReferenceEnd.FWD); refMenu = false })
                    }

                    // Grid toggle
                    TextButton(onClick = { viewModel.setShowGrid(!showGrid) }) { Text(if (showGrid) "Grid On" else "Grid Off") }

                    // Export
                    TextButton(onClick = { savePdf.launch(defaultPdfName(spec, jobNumber)) }) { Text("Export PDF") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 72.dp),
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
                            .height(225.dp)
                            .padding(8.dp)
                    )
                }

                // Assistant chip
                item {
                    val coverageMm = remember(spec) { coverageEndMm(spec) }
                    val overallMm = spec.overallLengthMm
                    val deltaMm = coverageMm - overallMm
                    if (overallMm > 0f && kotlin.math.abs(deltaMm) >= 0.1f) {
                        val isShort = deltaMm < 0f
                        val label = if (isShort) {
                            val remainUi = viewModel.toUiUnits(-deltaMm)
                            "Components end ${(if (units == Units.MM) "%.1f mm" else "%.3f in").format(remainUi)} before overall"
                        } else {
                            val overUi = viewModel.toUiUnits(deltaMm)
                            "Components exceed overall by ${(if (units == Units.MM) "%.1f mm" else "%.3f in").format(overUi)}"
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    if (isShort) androidx.compose.material.icons.Icons.Default.Info
                                    else androidx.compose.material.icons.Icons.Default.Warning,
                                    contentDescription = null
                                )
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
                                val bodyErr = endExceedsOverall(startUi, lenUi, overallUi)

                                ComponentCard("Body ${index + 1}", onRemove = { viewModel.removeBody(index) }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        NumberField(
                                            label = "Start from AFT (${unitsLabel(units)})",
                                            modelValue = startUi,
                                            onChange = { f -> viewModel.updateBody(index, startUi = f) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        NumberField(
                                            label = "Length (${unitsLabel(units)})",
                                            modelValue = lenUi,
                                            onChange = { f ->
                                                val desired = f ?: 0f
                                                val clamped = clampLenUi(startUi, desired, overallUi)
                                                viewModel.updateBody(index, lenUi = clamped)
                                                if (clamped < desired) scope.launch { showClampedSnack() }
                                            },
                                            modifier = Modifier.weight(1f),
                                            isError = bodyErr,
                                            supportingText = if (bodyErr) "Start + length exceeds overall" else null
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
                                val taperErr = endExceedsOverall(startUi, lenUi, overallUi)

                                ComponentCard("Taper ${index + 1}", onRemove = { viewModel.removeTaper(index) }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        NumberField(
                                            label = "Start from AFT (${unitsLabel(units)})",
                                            modelValue = startUi,
                                            onChange = { f -> viewModel.updateTaper(index, startUi = f) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        NumberField(
                                            label = "Length (${unitsLabel(units)})",
                                            modelValue = lenUi,
                                            onChange = { f ->
                                                val desired = f ?: 0f
                                                val clamped = clampLenUi(startUi, desired, overallUi)
                                                viewModel.updateTaper(index, lenUi = clamped)
                                                if (clamped < desired) scope.launch { showClampedSnack() }
                                            },
                                            modifier = Modifier.weight(1f),
                                            isError = taperErr,
                                            supportingText = if (taperErr) "Start + length exceeds overall" else null
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
                if (spec.threads.isNotEmpty()) {
                    item {
                        CollapsibleSection(title = "Threads") {
                            val overallUi = viewModel.toUiUnits(spec.overallLengthMm)
                            spec.threads.forEachIndexed { index, th ->
                                val startUi = viewModel.toUiUnits(th.startFromAftMm)
                                val lenUi = viewModel.toUiUnits(th.lengthMm)
                                val threadErr = endExceedsOverall(startUi, lenUi, overallUi)

                                ComponentCard("Thread ${index + 1}", onRemove = { viewModel.removeThread(index) }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                                            modelValue = lenUi,
                                            onChange = { f ->
                                                val desired = f ?: 0f
                                                val clamped = clampLenUi(startUi, desired, overallUi)
                                                viewModel.updateThread(index, lenUi = clamped)
                                                if (clamped < desired) scope.launch { showClampedSnack() }
                                            },
                                            modifier = Modifier.weight(1f),
                                            isError = threadErr,
                                            supportingText = if (threadErr) "Start + length exceeds overall" else null
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
                                val linerErr = endExceedsOverall(startUi, lenUi, overallUi)

                                ComponentCard("Liner ${index + 1}", onRemove = { viewModel.removeLiner(index) }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        NumberField(
                                            label = "Start from AFT (${unitsLabel(units)})",
                                            modelValue = startUi,
                                            onChange = { f -> viewModel.updateLiner(index, startUi = f) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        NumberField(
                                            label = "Length (${unitsLabel(units)})",
                                            modelValue = lenUi,
                                            onChange = { f ->
                                                val desired = f ?: 0f
                                                val clamped = clampLenUi(startUi, desired, overallUi)
                                                viewModel.updateLiner(index, lenUi = clamped)
                                                if (clamped < desired) scope.launch { showClampedSnack() }
                                            },
                                            modifier = Modifier.weight(1f),
                                            isError = linerErr,
                                            supportingText = if (linerErr) "Start + length exceeds overall" else null
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

                // Optional end-panel inputs
                item {
                    CollapsibleSection(title = "Aft / Left End", initiallyExpanded = false) {
                        TextFieldRow("L.E.T.", aftLet, viewModel::setAftLet)
                        TextFieldRow("S.E.T.", aftSet, viewModel::setAftSet)
                        TextFieldRow("Taper Rate", aftTaperRate, viewModel::setAftTaperRate)
                        TextFieldRow("K.W. (W × D)", aftKeyway, viewModel::setAftKeyway)
                        TextFieldRow("Threads (Ø × Pitch × L)", aftThreads, viewModel::setAftThreads)
                    }
                }
                item {
                    CollapsibleSection(title = "Fwd / Right End", initiallyExpanded = false) {
                        TextFieldRow("L.E.T.", fwdLet, viewModel::setFwdLet)
                        TextFieldRow("S.E.T.", fwdSet, viewModel::setFwdSet)
                        TextFieldRow("Taper Rate", fwdTaperRate, viewModel::setFwdTaperRate)
                        TextFieldRow("K.W. (W × D)", fwdKeyway, viewModel::setFwdKeyway)
                        TextFieldRow("Threads (Ø × Pitch × L)", fwdThreads, viewModel::setFwdThreads)
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

            // Floating Add Component FAB (right edge)
            var menuOpen by remember { mutableStateOf(false) }
            ExtendedFloatingActionButton(
                onClick = { menuOpen = !menuOpen },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            ) {
                DropdownMenuItem(text = { Text("Body") }, onClick = { menuOpen = false; addBodyClamped() })
                DropdownMenuItem(text = { Text("Taper") }, onClick = { menuOpen = false; addTaperClamped() })
                DropdownMenuItem(text = { Text("Thread") }, onClick = { menuOpen = false; addThreadClamped() })
                DropdownMenuItem(text = { Text("Liner") }, onClick = { menuOpen = false; addLinerClamped() })
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
    modelValue: Float,                    // current value from model, in UI units
    onChange: (Float?) -> Unit,           // called on commit (blur/Next/Done); null when cleared
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val focusManager = LocalFocusManager.current

    val modelFormatted = remember(modelValue) { modelValue.toDisplayString() }
    var text by remember(modelFormatted) { mutableStateOf(modelFormatted) }
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
            if (s.isValidDecimal()) { text = s }
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
        value = value, onValueChange = onChange,
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
