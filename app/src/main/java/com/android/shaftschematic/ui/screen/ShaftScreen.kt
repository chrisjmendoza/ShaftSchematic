package com.android.shaftschematic.ui.screen

// ---------- Imports ----------
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.pdf.ShaftPdfComposer
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Preview composable (comment this import + call below if you don't have it available right now)
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    // ↓↓↓ Add this: default options for the preview (no grid for now)
    val density = LocalContext.current.resources.displayMetrics.density
    val opts = remember(unit) {
        RenderOptions(
            targetWidthInches = null,
            maxHeightInches = 2f,
            paddingPx = (16 * density).toInt(),
            referenceEnd = ReferenceEnd.AFT,   // pick AFT as a sensible default
            lineWidthPx = 3f,
            dimLineWidthPx = 1.5f,
            textSizePx = 34f,
            showGrid = false,
            gridUseInches = (unit == UnitSystem.INCHES),
            gridDesiredMajors = 20,
            gridMinorsPerMajor = 4,
            gridMinorStrokePx = 0.5f,
            gridMajorStrokePx = 1f,
            gridMinorColor = 0x66888888.toInt(),
            gridMajorColor = 0x88888888.toInt(),
            showGridLegend = true
        )
    }
    // ↑↑↑

    // --- Local helpers for chips (no VM dependencies) ---
    val coverageEndMm by remember(spec) { mutableStateOf(computeCoverageEndMm(spec)) }
    val nextStartLabel by remember(spec, unit) {
        mutableStateOf("Next start at ${viewModel.formatInCurrentUnit(coverageEndMm.toFloat(), 3)} ${unit.displayName}")
    }
    val coverageMsg by remember(spec, unit, coverageEndMm) {
        mutableStateOf(buildCoverageMsgOrNull(spec.overallLengthMm, coverageEndMm, unit) { mm, decimals ->
            viewModel.formatInCurrentUnit(mm.toFloat(), decimals)
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Schematic") },
                actions = {
                    // Units dropdown
                    var unitsOpen by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitsOpen,
                        onExpandedChange = { unitsOpen = !unitsOpen }
                    ) {
                        OutlinedTextField(
                            value = unit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Units") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitsOpen) },
                            modifier = Modifier
                                .widthIn(min = 140.dp)
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = unitsOpen,
                            onDismissRequest = { unitsOpen = false }
                        ) {
                            UnitSystem.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.displayName) },
                                    onClick = { viewModel.setUnit(opt); unitsOpen = false }
                                )
                            }
                        }
                    }

                    // Export PDF
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = withContext(Dispatchers.IO) {
                                        ShaftPdfComposer.export(ctx, spec, fileName = "shaft_drawing.pdf")
                                    }
                                    snackbar.showSnackbar("Exported: ${file.name}")
                                } catch (e: Exception) {
                                    snackbar.showSnackbar("Export failed: ${e.message ?: "Unknown error"}")
                                }
                            }
                        }
                    ) { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF") }

                    // Overflow (Clear all)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear all") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.clearAll() // make sure this exists in your ViewModel (sets default overall length)
                                    scope.launch { snackbar.showSnackbar("Cleared shaft spec") }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = { AddFab(viewModel) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            /* ─── Preview ─── */
            item {
                // If ShaftDrawing isn't available, replace with: Box(Modifier.fillMaxWidth().height(180.dp))
                ShaftDrawing(
                    spec = spec,           // ← add this line
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            /* ─── Basics ─── */
            item { SectionTitle("Basics") }
            item {
                NumberField(
                    label = "Overall length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.overallLengthMm.toDouble(),
                    format = { mm -> viewModel.formatInCurrentUnit(mm.toFloat(), 3) },
                    onUserChange = { s -> viewModel.setOverallLength(s) }
                )
            }

            // Chips (optional)
            item { AssistChip(onClick = { }, label = { Text(nextStartLabel) }) }
            if (coverageMsg != null) {
                item { AssistChip(onClick = { }, label = { Text(coverageMsg!!) }) }
            }

            item { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant) }

            /* ─── Bodies ─── */
            if (spec.bodies.isNotEmpty()) {
                item { SectionTitle("Bodies") }
                itemsIndexed(spec.bodies, key = { idx, _ -> "body-$idx" }) { idx, b ->
                    SubsectionTitle("Body ${idx + 1}")
                    NumberField("Start from AFT (${unit.displayName})", unit,
                        mmValue = b.startFromAftMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setBody(idx, start = s) }
                    )
                    NumberField("Length (${unit.displayName})", unit,
                        mmValue = b.lengthMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setBody(idx, length = s) }
                    )
                    NumberField("Diameter (${unit.displayName})", unit,
                        mmValue = b.diaMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setBody(idx, dia = s) }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { viewModel.removeBody(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove body ${idx + 1}")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            }

            /* ─── Tapers ─── */
            if (spec.tapers.isNotEmpty()) {
                item { SectionTitle("Tapers") }
                itemsIndexed(spec.tapers, key = { idx, _ -> "taper-$idx" }) { idx, t ->
                    SubsectionTitle("Taper ${idx + 1}")
                    NumberField("Start from AFT (${unit.displayName})", unit,
                        mmValue = t.startFromAftMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setTaper(idx, start = s) }
                    )
                    NumberField("Length (${unit.displayName})", unit,
                        mmValue = t.lengthMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setTaper(idx, length = s) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Start Ø (${unit.displayName})", unit,
                            mmValue = t.startDiaMm.toDouble(),
                            format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                            onUserChange = { s -> viewModel.setTaper(idx, startDia = s) },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField("End Ø (${unit.displayName})", unit,
                            mmValue = t.endDiaMm.toDouble(),
                            format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                            onUserChange = { s -> viewModel.setTaper(idx, endDia = s) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { viewModel.removeTaper(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove taper ${idx + 1}")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            }

            /* ─── Threads ─── */
            if (spec.threads.isNotEmpty()) {
                item { SectionTitle("Threads") }
                itemsIndexed(spec.threads, key = { idx, _ -> "thread-$idx" }) { idx, th ->
                    SubsectionTitle("Thread ${idx + 1}")
                    NumberField("Start from AFT (${unit.displayName})", unit,
                        mmValue = th.startFromAftMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setThread(idx, start = s) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Major Ø (${unit.displayName})", unit,
                            mmValue = th.majorDiaMm.toDouble(),
                            format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                            onUserChange = { s -> viewModel.setThread(idx, majorDia = s) },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField("Pitch (${unit.displayName})", unit,
                            mmValue = th.pitchMm.toDouble(),
                            format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                            onUserChange = { s -> viewModel.setThread(idx, pitch = s) },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField("Length (${unit.displayName})", unit,
                            mmValue = th.lengthMm.toDouble(),
                            format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                            onUserChange = { s -> viewModel.setThread(idx, length = s) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { viewModel.removeThread(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove thread ${idx + 1}")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            }

            /* ─── Liners ─── */
            if (spec.liners.isNotEmpty()) {
                item { SectionTitle("Liners") }
                itemsIndexed(spec.liners, key = { idx, _ -> "liner-$idx" }) { idx, ln ->
                    SubsectionTitle("Liner ${idx + 1}")
                    NumberField("Start from AFT (${unit.displayName})", unit,
                        mmValue = ln.startFromAftMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setLiner(idx, start = s) }
                    )
                    NumberField("Length (${unit.displayName})", unit,
                        mmValue = ln.lengthMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setLiner(idx, length = s) }
                    )
                    NumberField("Outer Ø (${unit.displayName})", unit,
                        mmValue = ln.odMm.toDouble(),
                        format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                        onUserChange = { s -> viewModel.setLiner(idx, od = s) }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { viewModel.removeLiner(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove liner ${idx + 1}")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/* ----------------------------- Helpers / widgets ----------------------------- */

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable private fun SubsectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** Cursor-safe numeric text field. ViewModel parses raw string in current unit. */
@Composable
private fun NumberField(
    label: String,
    unit: UnitSystem,
    mmValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    onUserChange: (String) -> Unit
) {
    var tf by rememberSaveable(label, unit, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(format(mmValue)))
    }

    // Keep in sync with external value + unit changes
    LaunchedEffect(mmValue, unit) {
        val t = format(mmValue)
        tf = tf.copy(text = t, selection = TextRange(t.length))
    }

    OutlinedTextField(
        value = tf,
        onValueChange = { v -> tf = v; onUserChange(v.text) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                if (!f.isFocused) {
                    val t = format(mmValue)
                    tf = tf.copy(text = t, selection = TextRange(t.length))
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFab(viewModel: ShaftViewModel) {
    var open by remember { mutableStateOf(false) }
    FloatingActionButton(onClick = { open = true }) {
        Icon(Icons.Filled.Add, contentDescription = "Add")
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add component", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { open = false; viewModel.addBodySegment() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Body") }
                Button(onClick = { open = false; viewModel.addTaper() },  modifier = Modifier.fillMaxWidth()) { Text("Taper") }
                Button(onClick = { open = false; viewModel.addThread() }, modifier = Modifier.fillMaxWidth()) { Text("Thread") }
                Button(onClick = { open = false; viewModel.addLiner() },  modifier = Modifier.fillMaxWidth()) { Text("Liner") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ----------------------------- Local chip helpers ----------------------------- */

/** Max end position among all components (in mm). */
private fun computeCoverageEndMm(spec: com.android.shaftschematic.data.ShaftSpecMm): Float {
    var far = 0f
    spec.bodies.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach { far = maxOf(far, it.startFromAftMm + it.lengthMm) }
    return far
}

/** Human message if components don't match overall length; null if close enough. */
private fun buildCoverageMsgOrNull(
    overallMm: Float,
    coverageEndMm: Float,
    unit: UnitSystem,
    format: (Double, Int) -> String
): String? {
    val deltaMm = coverageEndMm - overallMm
    val eps = 0.05f // 0.05 mm tolerance; tweak if you like
    if (kotlin.math.abs(deltaMm) <= eps) return null
    val mag = kotlin.math.abs(deltaMm.toDouble())
    val pretty = format(mag, if (unit == UnitSystem.MILLIMETERS) 1 else 3)
    return if (deltaMm < 0) "Components end $pretty ${unit.displayName} before overall"
    else "Components exceed overall by $pretty ${unit.displayName}"
}
