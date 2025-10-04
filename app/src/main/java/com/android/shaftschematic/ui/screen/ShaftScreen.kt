package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Header row (Unit selector + Grid toggle; unit selector disables when locked)
 * • Preview drawing area (renderShaft(spec, unit))
 * • Overall length field (unit-aware; commits mm)
 * • Project fields (commit-on-blur / IME Done)
 * • Editable component cards including Remove buttons
 * • Add-component FAB floating above the keyboard/nav bar
 *
 * Invariants
 * • Canonical data & rendering units are millimeters (mm).
 * • All unit conversions happen only at the UI edge (format/parse).
 * • No file I/O here. Save/Load/PDF live in routes/host.
 */

@Composable
fun ShaftScreen(
    // State
    spec: ShaftSpec,
    unit: UnitSystem,
    unitLocked: Boolean,
    customer: String,
    vessel: String,
    jobNumber: String,
    notes: String,
    showGrid: Boolean,

    // Setters
    onSetUnit: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,   // legacy-friendly
    onSetOverallLengthMm: (Float) -> Unit,

    // Adds (all mm)
    onAddBody: (Float, Float, Float) -> Unit,
    onAddTaper: (Float, Float, Float, Float) -> Unit,
    onAddThread: (Float, Float, Float, Float) -> Unit,
    onAddLiner: (Float, Float, Float) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,

    // Removes
    onRemoveBody: (Int) -> Unit,
    onRemoveTaper: (Int) -> Unit,
    onRemoveThread: (Int) -> Unit,
    onRemoveLiner: (Int) -> Unit,

    // Other
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            HeaderRow(
                unit = unit,
                unitLocked = unitLocked,
                gridChecked = showGrid,
                onUnitSelected = onSetUnit,
                onToggleGrid = onToggleGrid
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { chooserOpen = true },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add component")
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
        ) {
            // Preview (fixed height)
            PreviewCard(
                showGrid = showGrid,
                gridStepDp = 16.dp,
                spec = spec,
                unit = unit,
                renderShaft = renderShaft,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 200.dp)
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Scrollable editor content; apply IME padding here (not on whole screen)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Length (unit-aware display; commit mm)
                var lengthText by remember(unit, spec.overallLengthMm) {
                    mutableStateOf(formatDisplay(spec.overallLengthMm, unit))
                }
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    label = { Text("Overall Length (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = { onSetOverallLengthRaw(lengthText) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f -> if (!f.isFocused) onSetOverallLengthRaw(lengthText) }
                )

                // Project Info
                ExpandableSection("Project Information (optional)", initiallyExpanded = false) {
                    CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                    CommitTextField("Customer", customer, onSetCustomer, Modifier.fillMaxWidth())
                    CommitTextField("Vessel",   vessel,   onSetVessel,   Modifier.fillMaxWidth())
                    CommitTextField(
                        label = "Notes",
                        initial = notes,
                        onCommit = onSetNotes,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minHeight = 88.dp
                    )
                }

                Text("Components", style = MaterialTheme.typography.titleMedium)

                // Editable component cards (commit-on-blur; remove buttons)
                ComponentsUnifiedList(
                    spec = spec,
                    unit = unit,
                    onUpdateBody = onUpdateBody,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateThread = onUpdateThread,
                    onUpdateLiner = onUpdateLiner,
                    onRemoveBody = onRemoveBody,
                    onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread,
                    onRemoveLiner = onRemoveLiner
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (chooserOpen) {
        val d = computeAddDefaults(spec)
        val defaultLenMm = if (unit == UnitSystem.INCHES) 16f * 25.4f else 100f
        val defaultThreadLenMm = 32f
        val defaultPitchMm = 25.4f / 4f // 4 TPI

        InlineAddChooserDialog(
            onDismiss = { chooserOpen = false },
            onAddBody = {
                chooserOpen = false
                onAddBody(d.startMm, defaultLenMm, d.lastDiaMm)
                scope.launch { scroll.animateScrollTo(0) }
            },
            onAddLiner = {
                chooserOpen = false
                onAddLiner(d.startMm, defaultLenMm, d.lastDiaMm)
                scope.launch { scroll.animateScrollTo(0) }
            },
            onAddThread = {
                chooserOpen = false
                onAddThread(d.startMm, defaultThreadLenMm, d.lastDiaMm, defaultPitchMm)
                scope.launch { scroll.animateScrollTo(0) }
            },
            onAddTaper = {
                chooserOpen = false
                val len = defaultLenMm
                val setDiaMm = d.lastDiaMm
                val letDiaMm = setDiaMm + (len / 12f)
                onAddTaper(d.startMm, len, setDiaMm, letDiaMm)
                scope.launch { scroll.animateScrollTo(0) }
            }
        )
    }
}

/* ───────────────── Header (Unit + Grid) ───────────────── */

@Composable
private fun HeaderRow(
    unit: UnitSystem,
    unitLocked: Boolean,
    gridChecked: Boolean,
    onUnitSelected: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        UnitSegment(unit = unit, enabled = !unitLocked, onUnitSelected = onUnitSelected)
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grid")
            Spacer(Modifier.width(6.dp))
            Switch(checked = gridChecked, onCheckedChange = onToggleGrid)
        }
    }
}

@Composable
private fun UnitSegment(
    unit: UnitSystem,
    enabled: Boolean,
    onUnitSelected: (UnitSystem) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.height(36.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            UnitChip("mm", UnitSystem.MILLIMETERS, unit, enabled, onUnitSelected)
            Spacer(Modifier.width(4.dp))
            UnitChip("in", UnitSystem.INCHES, unit, enabled, onUnitSelected)
        }
    }
}

@Composable
private fun UnitChip(
    label: String,
    chipUnit: UnitSystem,
    current: UnitSystem,
    enabled: Boolean,
    onUnitSelected: (UnitSystem) -> Unit
) {
    val selected = chipUnit == current
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .let { if (enabled) it.clickable { onUnitSelected(chipUnit) } else it }
        )
    }
}

/* ───────────────── Preview ───────────────── */

@Composable
private fun PreviewCard(
    showGrid: Boolean,
    gridStepDp: Dp,
    spec: ShaftSpec,
    unit: UnitSystem,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            if (showGrid) {
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                Canvas(Modifier.fillMaxSize()) {
                    val stepPx = gridStepDp.toPx()
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                        x += stepPx
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                        y += stepPx
                    }
                }
            }
            renderShaft(spec, unit)
        }
    }
}

/* ───────────────── Components (editable + remove) ───────────────── */

private enum class RowKind { BODY, TAPER, THREAD, LINER }
private data class RowRef(val kind: RowKind, val index: Int, val start: Float)

@Composable
private fun ComponentsUnifiedList(
    spec: ShaftSpec,
    unit: UnitSystem,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onRemoveBody: (Int) -> Unit,
    onRemoveTaper: (Int) -> Unit,
    onRemoveThread: (Int) -> Unit,
    onRemoveLiner: (Int) -> Unit,
) {
    // Display by start position so the list reads like the drawing left→right.
    val rows = buildList {
        spec.bodies.forEachIndexed  { i, b  -> add(RowRef(RowKind.BODY,   i, b.startFromAftMm)) }
        spec.tapers.forEachIndexed  { i, t  -> add(RowRef(RowKind.TAPER,  i, t.startFromAftMm)) }
        spec.threads.forEachIndexed { i, th -> add(RowRef(RowKind.THREAD, i, th.startFromAftMm)) }
        spec.liners.forEachIndexed  { i, ln -> add(RowRef(RowKind.LINER,  i, ln.startFromAftMm)) }
    }.sortedBy { it.start } // ascending along the shaft

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            when (row.kind) {
                RowKind.BODY -> {
                    val b = spec.bodies[row.index]
                    ComponentCard("Body #${row.index + 1}", onRemove = { onRemoveBody(row.index) }) {
                        CommitNum("Start (${abbr(unit)})", disp(b.startFromAftMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateBody(row.index, it, b.lengthMm, b.diaMm) }
                        }
                        CommitNum("Length (${abbr(unit)})", disp(b.lengthMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateBody(row.index, b.startFromAftMm, it, b.diaMm) }
                        }
                        CommitNum("Ø (${abbr(unit)})", disp(b.diaMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateBody(row.index, b.startFromAftMm, b.lengthMm, it) }
                        }
                    }
                }
                RowKind.TAPER -> {
                    val t = spec.tapers[row.index]
                    ComponentCard("Taper #${row.index + 1}", onRemove = { onRemoveTaper(row.index) }) {
                        CommitNum("Start (${abbr(unit)})", disp(t.startFromAftMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateTaper(row.index, it, t.lengthMm, t.startDiaMm, t.endDiaMm) }
                        }
                        CommitNum("Length (${abbr(unit)})", disp(t.lengthMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateTaper(row.index, t.startFromAftMm, it, t.startDiaMm, t.endDiaMm) }
                        }
                        CommitNum("S.E.T. Ø (${abbr(unit)})", disp(t.startDiaMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, it, t.endDiaMm) }
                        }
                        CommitNum("L.E.T. Ø (${abbr(unit)})", disp(t.endDiaMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, t.startDiaMm, it) }
                        }
                    }
                }
                RowKind.THREAD -> {
                    val th = spec.threads[row.index]
                    val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
                    ComponentCard("Thread #${row.index + 1}", onRemove = { onRemoveThread(row.index) }) {
                        CommitNum("Start (${abbr(unit)})", disp(th.startFromAftMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateThread(row.index, it, th.lengthMm, th.majorDiaMm, th.pitchMm) }
                        }
                        CommitNum("Length (${abbr(unit)})", disp(th.lengthMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateThread(row.index, th.startFromAftMm, it, th.majorDiaMm, th.pitchMm) }
                        }
                        CommitNum("Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateThread(row.index, th.startFromAftMm, th.lengthMm, it, th.pitchMm) }
                        }
                        CommitNum("TPI", tpiDisplay) { s ->
                            parseFractionOrDecimal(s)?.takeIf { it > 0f }?.let { tpi ->
                                onUpdateThread(row.index, th.startFromAftMm, th.lengthMm, th.majorDiaMm, tpiToPitchMm(tpi))
                            }
                        }
                    }
                }
                RowKind.LINER -> {
                    val ln = spec.liners[row.index]
                    ComponentCard("Liner #${row.index + 1}", onRemove = { onRemoveLiner(row.index) }) {
                        CommitNum("Start (${abbr(unit)})", disp(ln.startFromAftMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateLiner(row.index, it, ln.lengthMm, ln.odMm) }
                        }
                        CommitNum("Length (${abbr(unit)})", disp(ln.lengthMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateLiner(row.index, ln.startFromAftMm, it, ln.odMm) }
                        }
                        CommitNum("Outer Ø (${abbr(unit)})", disp(ln.odMm, unit)) { s ->
                            toMmOrNull(s, unit)?.let { onUpdateLiner(row.index, ln.startFromAftMm, ln.lengthMm, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentCard(
    title: String,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                content()
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/* ───────────────── Sections & Fields ───────────────── */

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun CommitTextField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = Dp.Unspecified
) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

@Composable
private fun CommitNum(
    label: String,
    initialDisplay: String,
    onCommit: (String) -> Unit
) {
    var text by remember(initialDisplay) { mutableStateOf(initialDisplay) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ───────────────── Add chooser ───────────────── */

@Composable
private fun InlineAddChooserDialog(
    onDismiss: () -> Unit,
    onAddBody: () -> Unit,
    onAddLiner: () -> Unit,
    onAddThread: () -> Unit,
    onAddTaper: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Component") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddBody,  modifier = Modifier.fillMaxWidth()) { Text("Body") }
                Button(onClick = onAddLiner, modifier = Modifier.fillMaxWidth()) { Text("Liner") }
                Button(onClick = onAddThread,modifier = Modifier.fillMaxWidth()) { Text("Thread") }
                Button(onClick = onAddTaper, modifier = Modifier.fillMaxWidth()) { Text("Taper") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ───────────────── Helpers: units, parsing, defaults ───────────────── */

private fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

private fun formatDisplay(valueMm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) valueMm else valueMm / 25.4f
    return "%.${d}f".format(v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
private fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String = formatDisplay(mm, unit, d)

private fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val t = text.trim(); if (t.isEmpty()) return null
    val num = parseFractionOrDecimal(t) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) num else num * 25.4f
}

private fun Float.fmtTrim(d: Int) = "%.${d}f".format(this).trimEnd('0').trimEnd('.')

/** Accepts "12", "3/4", "1.5", or "1:12". */
private fun parseFractionOrDecimal(input: String): Float? {
    val t = input.trim(); if (t.isEmpty()) return null
    val colon = t.indexOf(':')
    if (colon >= 0) {
        val a = t.substring(0, colon).trim().toFloatOrNull() ?: return null
        val b = t.substring(colon + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    val slash = t.indexOf('/')
    if (slash >= 0) {
        val a = t.substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = t.substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    return t.toFloatOrNull()
}

private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
@Suppress("unused")
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/** Compute reasonable defaults for new components (mm). */
private data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
private fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    var end = 0f
    spec.bodies.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }

    var dia = 50f
    spec.bodies.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.diaMm }
    spec.liners.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.odMm }
    spec.threads.firstOrNull { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.majorDiaMm }
    spec.tapers.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.endDiaMm }
    if (dia == 50f && spec.bodies.isNotEmpty()) dia = spec.bodies.first().diaMm

    return AddDefaults(startMm = end, lastDiaMm = dia)
}
