package com.android.shaftschematic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.pdf.ShaftPdfComposer
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.HintStyle
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()
    val hintStyle by viewModel.hintStyle.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }

    var fwdRatio by remember { mutableStateOf(spec.forwardTaper.ratio.toString()) }
    var aftRatio by remember { mutableStateOf(spec.aftTaper.ratio.toString()) }
    LaunchedEffect(spec.forwardTaper.ratio) { fwdRatio = spec.forwardTaper.ratio.toString() }
    LaunchedEffect(spec.aftTaper.ratio) { aftRatio = spec.aftTaper.ratio.toString() }

    val onExportClick: () -> Unit = {
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    ShaftPdfComposer.export(ctx, spec, fileName = "shaft_drawing.pdf")
                }
                snackbarHostState.showSnackbar("Exported PDF: ${file.absolutePath}")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Export failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Schematic") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onExportClick) {
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
                NumberField(
                    label = "Overall length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.overallLengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 4) },
                    saveKey = "basic-overall",
                    onUserChange = { viewModel.setOverallLength(it) }
                )
            }
            item {
                NumberField(
                    label = "Shaft diameter (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.shaftDiameterMm,
                    format = { viewModel.formatInCurrentUnit(it, 4) },
                    saveKey = "basic-diameter",
                    onUserChange = { viewModel.setShaftDiameter(it) }
                )
            }
            item {
                NumberField(
                    label = "Shoulder length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.shoulderLengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 4) },
                    saveKey = "basic-shoulder",
                    onUserChange = { viewModel.setShoulderLength(it) }
                )
            }
            item {
                NumberField(
                    label = "Chamfer (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.chamferMm,
                    format = { viewModel.formatInCurrentUnit(it, 4) },
                    saveKey = "basic-chamfer",
                    onUserChange = { viewModel.setChamfer(it) }
                )
            }

            // Coverage nudge (based on settings)
            item {
                when (hintStyle) {
                    HintStyle.CHIP -> {
                        val chipHint = remember(spec, unit) { viewModel.coverageChipHint() }
                        if (chipHint != null) {
                            AssistChip(
                                onClick = { /* optional action */ },
                                label = { Text(chipHint) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    HintStyle.TEXT -> {
                        val longHint = remember(spec, unit) { viewModel.coverageHint() }
                        if (longHint != null) {
                            Text(
                                text = longHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    HintStyle.OFF -> Unit
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
                NumberField(
                    label = "Position from forward (${unit.displayName})",
                    unit = unit,
                    mmValue = seg.positionFromForwardMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "seg-$idx-pos",
                    onUserChange = { viewModel.setBodySegment(idx, position = it) }
                )
                NumberField(
                    label = "Length (${unit.displayName})",
                    unit = unit,
                    mmValue = seg.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "seg-$idx-len",
                    onUserChange = { viewModel.setBodySegment(idx, length = it) }
                )
                NumberField(
                    label = "Diameter (${unit.displayName})",
                    unit = unit,
                    mmValue = seg.diameterMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "seg-$idx-dia",
                    onUserChange = { viewModel.setBodySegment(idx, diameter = it) }
                )
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
                NumberField(
                    label = "Start position from forward (${unit.displayName})",
                    unit = unit,
                    mmValue = k.positionFromForwardMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "keyway-$idx-pos",
                    onUserChange = { viewModel.setKeyway(idx, position = it) }
                )
                NumberField(
                    label = "Width (${unit.displayName})",
                    unit = unit,
                    mmValue = k.widthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "keyway-$idx-width",
                    onUserChange = { viewModel.setKeyway(idx, width = it) }
                )
                NumberField(
                    label = "Depth (${unit.displayName})",
                    unit = unit,
                    mmValue = k.depthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "keyway-$idx-depth",
                    onUserChange = { viewModel.setKeyway(idx, depth = it) }
                )
                NumberField(
                    label = "Length (${unit.displayName})",
                    unit = unit,
                    mmValue = k.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "keyway-$idx-len",
                    onUserChange = { viewModel.setKeyway(idx, length = it) }
                )
                Spacer(Modifier.height(6.dp))
            }

            item { ThemedDivider() }

            /* ---------------- Tapers (ratio-aware) ---------------- */
            item { SectionTitle("Forward Taper (ratio like 1:10)") }
            item {
                NumberField(
                    label = "Large end Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardTaper.largeEndMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdTaper-large",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setForwardTaper(large = it) }
                )
            }
            item {
                NumberField(
                    label = "Small end Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardTaper.smallEndMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdTaper-small",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setForwardTaper(small = it) }
                )
            }
            item {
                NumberField(
                    label = "Taper length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardTaper.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdTaper-length",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setForwardTaper(length = it) }
                )
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
                NumberField(
                    label = "Large end Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftTaper.largeEndMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftTaper-large",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setAftTaper(large = it) }
                )
            }
            item {
                NumberField(
                    label = "Small end Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftTaper.smallEndMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftTaper-small",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setAftTaper(small = it) }
                )
            }
            item {
                NumberField(
                    label = "Taper length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftTaper.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftTaper-length",
                    commitOnFocusLoss = true,
                    onUserChange = { viewModel.setAftTaper(length = it) }
                )
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
                NumberField(
                    label = "Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardThreads.diameterMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdThreads-dia",
                    onUserChange = { viewModel.setForwardThreads(diameter = it) }
                )
            }
            item {
                NumberField(
                    label = "Pitch (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardThreads.pitchMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdThreads-pitch",
                    onUserChange = { viewModel.setForwardThreads(pitch = it) }
                )
            }
            item {
                NumberField(
                    label = "Length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.forwardThreads.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "fwdThreads-len",
                    onUserChange = { viewModel.setForwardThreads(length = it) }
                )
            }

            item { SectionTitle("Aft Threads (0 for none)") }
            item {
                NumberField(
                    label = "Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftThreads.diameterMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftThreads-dia",
                    onUserChange = { viewModel.setAftThreads(diameter = it) }
                )
            }
            item {
                NumberField(
                    label = "Pitch (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftThreads.pitchMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftThreads-pitch",
                    onUserChange = { viewModel.setAftThreads(pitch = it) }
                )
            }
            item {
                NumberField(
                    label = "Length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.aftThreads.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "aftThreads-len",
                    onUserChange = { viewModel.setAftThreads(length = it) }
                )
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
                NumberField(
                    label = "Position from forward (${unit.displayName})",
                    unit = unit,
                    mmValue = liner.positionFromForwardMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "liner-$idx-pos",
                    onUserChange = { viewModel.setLiner(idx, position = it) }
                )
                NumberField(
                    label = "Length (${unit.displayName})",
                    unit = unit,
                    mmValue = liner.lengthMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "liner-$idx-len",
                    onUserChange = { viewModel.setLiner(idx, length = it) }
                )
                NumberField(
                    label = "Outer Ø (${unit.displayName})",
                    unit = unit,
                    mmValue = liner.diameterMm,
                    format = { viewModel.formatInCurrentUnit(it, 3) },
                    saveKey = "liner-$idx-od",
                    onUserChange = { viewModel.setLiner(idx, diameter = it) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            current = hintStyle,
            onSelect = { viewModel.setHintStyle(it) },
            onDismiss = { showSettings = false }
        )
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

/** Cursor-safe numeric text field with optional commit-on-blur. */
@Composable
private fun NumberField(
    label: String,
    unit: UnitSystem,
    mmValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    onUserChange: (String) -> Unit,
    saveKey: String? = null,
    commitOnFocusLoss: Boolean = false
) {
    val rememberKey = saveKey ?: "$label|${unit.name}"
    var tf by rememberSaveable(rememberKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(format(mmValue)))
    }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(unit, mmValue, hasFocus) {
        if (!hasFocus) {
            val t = format(mmValue)
            tf = tf.copy(text = t, selection = TextRange(t.length))
        }
    }

    OutlinedTextField(
        value = tf,
        onValueChange = { v ->
            tf = v
            if (!commitOnFocusLoss) onUserChange(v.text)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = if (commitOnFocusLoss) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                if (commitOnFocusLoss) onUserChange(tf.text)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                hasFocus = f.isFocused
                if (!f.isFocused && commitOnFocusLoss) {
                    onUserChange(tf.text)
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

@Composable
private fun SettingsDialog(
    current: HintStyle,
    onSelect: (HintStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coverage hint style", style = MaterialTheme.typography.titleMedium)
                HintStyleOption("Chip (short)", current == HintStyle.CHIP) { onSelect(HintStyle.CHIP) }
                HintStyleOption("Text (detailed)", current == HintStyle.TEXT) { onSelect(HintStyle.TEXT) }
                HintStyleOption("Off", current == HintStyle.OFF) { onSelect(HintStyle.OFF) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun HintStyleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = onClick)
    }
}
