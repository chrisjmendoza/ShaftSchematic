package com.android.shaftschematic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.KeywaySpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.pdf.ShaftPdfComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var fwdRatio by remember { mutableStateOf(spec.forwardTaper.ratio.toString()) }
    var aftRatio by remember { mutableStateOf(spec.aftTaper.ratio.toString()) }
    LaunchedEffect(spec.forwardTaper.ratio) { fwdRatio = spec.forwardTaper.ratio.toString() }
    LaunchedEffect(spec.aftTaper.ratio) { aftRatio = spec.aftTaper.ratio.toString() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Shaft Schematic") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                // You can customize the filename if you want.
                                ShaftPdfComposer.export(ctx, spec, fileName = "shaft_drawing.pdf")
                            }
                            snackbarHostState.showSnackbar("Exported PDF: ${file.absolutePath}")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Export failed: ${e.message ?: "Unknown error"}")
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Units
            item {
                UnitDropdown(
                    selected = unit,
                    onSelect = { viewModel.setUnit(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Basics
            item { SectionTitle("Basics") }
            item {
                NumberField("Overall length (${unit.displayName})", unit, spec.overallLengthMm, { viewModel.formatInCurrentUnit(it, 4) }) {
                    viewModel.setOverallLength(it)
                }
            }
            item {
                NumberField("Shaft diameter (${unit.displayName})", unit, spec.shaftDiameterMm, { viewModel.formatInCurrentUnit(it, 4) }) {
                    viewModel.setShaftDiameter(it)
                }
            }
            item {
                NumberField("Shoulder length (${unit.displayName})", unit, spec.shoulderLengthMm, { viewModel.formatInCurrentUnit(it, 4) }) {
                    viewModel.setShoulderLength(it)
                }
            }
            item {
                NumberField("Chamfer (${unit.displayName})", unit, spec.chamferMm, { viewModel.formatInCurrentUnit(it, 4) }) {
                    viewModel.setChamfer(it)
                }
            }

            item { ThemedDivider() }

            /* ---------------- Body Segments (dynamic) ---------------- */
            item {
                SectionHeaderWithAdd(
                    title = "Body Segments (variable Ø)",
                    onAdd = { viewModel.addBodySegment() }
                )
            }
            items(
                count = spec.bodySegments.size,
                key = { idx -> "seg-$idx" }
            ) { idx ->
                val seg = spec.bodySegments[idx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubsectionTitle("Segment ${idx + 1}")
                    IconButton(onClick = { viewModel.removeBodySegment(idx) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove Segment ${idx + 1}")
                    }
                }
                NumberField("Position from forward (${unit.displayName})", unit, seg.positionFromForwardMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setBodySegment(idx, position = it)
                }
                NumberField("Length (${unit.displayName})", unit, seg.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setBodySegment(idx, length = it)
                }
                NumberField("Diameter (${unit.displayName})", unit, seg.diameterMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setBodySegment(idx, diameter = it)
                }
                Spacer(Modifier.height(6.dp))
            }

            item { ThemedDivider() }

            /* ---------------- Keyways (dynamic) ---------------- */
            item {
                SectionHeaderWithAdd(
                    title = "Keyways",
                    onAdd = { viewModel.addKeyway() }
                )
            }
            items(
                count = spec.keyways.size,
                key = { idx -> "keyway-$idx" }
            ) { idx ->
                val k = spec.keyways[idx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubsectionTitle("Keyway ${idx + 1}")
                    IconButton(onClick = { viewModel.removeKeyway(idx) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove Keyway ${idx + 1}")
                    }
                }
                NumberField("Start position from forward (${unit.displayName})", unit, k.positionFromForwardMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setKeyway(idx, position = it)
                }
                NumberField("Width (${unit.displayName})", unit, k.widthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setKeyway(idx, width = it)
                }
                NumberField("Depth (${unit.displayName})", unit, k.depthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setKeyway(idx, depth = it)
                }
                NumberField("Length (${unit.displayName})", unit, k.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setKeyway(idx, length = it)
                }
                Spacer(Modifier.height(6.dp))
            }

            item { ThemedDivider() }

            /* ---------------- Tapers (ratio-aware) ---------------- */
            item { SectionTitle("Forward Taper (ratio like 1:10)") }
            item {
                NumberField("Large end Ø (${unit.displayName})", unit, spec.forwardTaper.largeEndMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardTaper(large = it)
                }
            }
            item {
                NumberField("Small end Ø (${unit.displayName})", unit, spec.forwardTaper.smallEndMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardTaper(small = it)
                }
            }
            item {
                NumberField("Taper length (${unit.displayName})", unit, spec.forwardTaper.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardTaper(length = it)
                }
            }
            item {
                OutlinedTextField(
                    value = fwdRatio,
                    onValueChange = { s -> fwdRatio = s; viewModel.setForwardTaper(ratio = s) },
                    label = { Text("Taper ratio (e.g., 1:10)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { SectionTitle("Aft Taper (ratio like 1:12)") }
            item {
                NumberField("Large end Ø (${unit.displayName})", unit, spec.aftTaper.largeEndMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftTaper(large = it)
                }
            }
            item {
                NumberField("Small end Ø (${unit.displayName})", unit, spec.aftTaper.smallEndMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftTaper(small = it)
                }
            }
            item {
                NumberField("Taper length (${unit.displayName})", unit, spec.aftTaper.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftTaper(length = it)
                }
            }
            item {
                OutlinedTextField(
                    value = aftRatio,
                    onValueChange = { s -> aftRatio = s; viewModel.setAftTaper(ratio = s) },
                    label = { Text("Taper ratio (e.g., 1:12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { ThemedDivider() }

            /* ---------------- Threads ---------------- */
            item { SectionTitle("Forward Threads (0 for none)") }
            item {
                NumberField("Ø (${unit.displayName})", unit, spec.forwardThreads.diameterMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardThreads(diameter = it)
                }
            }
            item {
                NumberField("Pitch (${unit.displayName})", unit, spec.forwardThreads.pitchMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardThreads(pitch = it)
                }
            }
            item {
                NumberField("Length (${unit.displayName})", unit, spec.forwardThreads.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setForwardThreads(length = it)
                }
            }

            item { SectionTitle("Aft Threads (0 for none)") }
            item {
                NumberField("Ø (${unit.displayName})", unit, spec.aftThreads.diameterMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftThreads(diameter = it)
                }
            }
            item {
                NumberField("Pitch (${unit.displayName})", unit, spec.aftThreads.pitchMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftThreads(pitch = it)
                }
            }
            item {
                NumberField("Length (${unit.displayName})", unit, spec.aftThreads.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setAftThreads(length = it)
                }
            }

            item { ThemedDivider() }

            /* ---------------- Liners (dynamic) ---------------- */
            item {
                SectionHeaderWithAdd(
                    title = "Liners",
                    onAdd = { viewModel.addLiner() }
                )
            }
            items(
                count = spec.liners.size,
                key = { idx -> "liner-$idx" }
            ) { idx ->
                val liner = spec.liners[idx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubsectionTitle("Liner ${idx + 1}")
                    IconButton(onClick = { viewModel.removeLiner(idx) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove Liner ${idx + 1}")
                    }
                }
                NumberField("Position from forward (${unit.displayName})", unit, liner.positionFromForwardMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setLiner(idx, position = it)
                }
                NumberField("Length (${unit.displayName})", unit, liner.lengthMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setLiner(idx, length = it)
                }
                NumberField("Outer Ø (${unit.displayName})", unit, liner.diameterMm, { viewModel.formatInCurrentUnit(it, 3) }) {
                    viewModel.setLiner(idx, diameter = it)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/* ----------------------------- Helpers ----------------------------- */

@Composable
private fun ThemedDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun SectionHeaderWithAdd(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionTitle(title)
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add $title")
        }
    }
}

/** Cursor-safe numeric text field. */
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

    // Reformat on unit change only
    LaunchedEffect(unit) {
        val t = format(mmValue)
        tf = tf.copy(text = t, selection = TextRange(t.length))
    }

    OutlinedTextField(
        value = tf,
        onValueChange = { v ->
            tf = v
            onUserChange(v.text)
        },
        label = { Text(label) },
        singleLine = true,
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
private fun UnitDropdown(
    selected: UnitSystem,
    onSelect: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Units") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Kotlin 1.9+: entries
            UnitSystem.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
