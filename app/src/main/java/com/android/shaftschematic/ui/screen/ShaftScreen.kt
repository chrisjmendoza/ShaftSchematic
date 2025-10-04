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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.freeToEndMm
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

/**
 * ShaftScreen – UI Screen Layer
 * -----------------------------------
 * Presents the shaft editor surface and binds ViewModel state to user controls.
 *
 * Contract: docs/ShaftScreen.md (v0.3, 2025-10-04)
 *
 * Responsibilities:
 *  • Header row with unit selector and grid toggle.
 *  • Fixed preview area rendering via renderShaft(spec, unit).
 *  • Scrollable form area with overall length, project info, and unified component list.
 *  • ComponentCard handles its own top-right remove icon.
 *  • FloatingActionButton opens the Add-Chooser dialog.
 *
 * Invariants:
 *  • Model and ViewModel data are canonical millimeters (mm).
 *  • All unit conversions occur only at the UI edge.
 *  • Text fields commit on blur or IME Done (no live writes).
 *  • Renderer and layout layers are mm-only (no unit logic here).
 */

@Composable
fun ShaftScreen(
    // State
    spec: ShaftSpec,
    unit: UnitSystem,
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
    onSetOverallLengthRaw: (String) -> Unit,
    onSetOverallLengthMm: (Float) -> Unit,

    // Add
    onAddBody: (Float, Float, Float) -> Unit,
    onAddTaper: (Float, Float, Float, Float) -> Unit,
    onAddThread: (Float, Float, Float, Float) -> Unit,
    onAddLiner: (Float, Float, Float) -> Unit,

    // Update
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,

    // Remove
    onRemoveBody: (Int) -> Unit,
    onRemoveTaper: (Int) -> Unit,
    onRemoveThread: (Int) -> Unit,
    onRemoveLiner: (Int) -> Unit,

    // Other
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            HeaderRow(
                unit = unit,
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
                .padding(16.dp) // no .imePadding here
        ) {
            // — Fixed preview only —
            PreviewCard(
                showGrid = showGrid,
                gridStepDp = 16.dp,
                spec = spec,
                unit = unit,
                renderShaft = renderShaft,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
                    .aspectRatio(3f)  // wide, low profile
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // — Everything below is scrollable and IME-aware —
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding(), // <— apply IME padding ONLY to the scroll area
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Overall Length (moved into scrollable area)
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

                    // Project Info (moved into scrollable area)
                    ExpandableSection("Project Information", initiallyExpanded = false) {
                        CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                        CommitTextField("Customer", customer, onSetCustomer, Modifier.fillMaxWidth())
                        CommitTextField("Vessel", vessel, onSetVessel, Modifier.fillMaxWidth())
                        CommitTextField(
                            "Notes",
                            notes,
                            onSetNotes,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minHeight = 88.dp
                        )
                    }

                    Text("Components", style = MaterialTheme.typography.titleMedium)

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
    }

        if (chooserOpen) {
            val d = computeAddDefaults(spec)
            InlineAddChooserDialog(
                onDismiss = { chooserOpen = false },
                onAddBody = {
                    chooserOpen = false
                    onAddBody(d.startMm, 100f, d.lastDiaMm)
                    scope.launch { /* you can scroll to top of list if desired */ }
                },
                onAddLiner = {
                    chooserOpen = false
                    onAddLiner(d.startMm, 100f, d.lastDiaMm)
                },
                onAddThread = {
                    chooserOpen = false
                    onAddThread(d.startMm, 150f, d.lastDiaMm, 25.4f / 4f) // default 10 TPI
                },
                onAddTaper = {
                    chooserOpen = false
                    val set = d.lastDiaMm
                    val let = set + (100f / 12f) // ~1:12 over 100 mm
                    onAddTaper(d.startMm, 100f, set, let)
                }
            )
        }
    }


/* ───────────────────────── Header ───────────────────────── */

@Composable
private fun HeaderRow(
    unit: UnitSystem,
    gridChecked: Boolean,
    onUnitSelected: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit
) {
    // Keep this area visually slim: no tonal surface band, minimal height & padding.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp) // trimmed
            .heightIn(min = 44.dp),                       // slimmer strip
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        UnitSegment(unit = unit, onUnitSelected = onUnitSelected)
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grid")
            Spacer(Modifier.width(6.dp))
            Switch(checked = gridChecked, onCheckedChange = onToggleGrid)
        }
    }
}

@Composable
private fun UnitSegment(unit: UnitSystem, onUnitSelected: (UnitSystem) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.height(36.dp).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnitChip("mm", UnitSystem.MILLIMETERS, unit, onUnitSelected)
            Spacer(Modifier.width(4.dp))
            UnitChip("in", UnitSystem.INCHES, unit, onUnitSelected)
        }
    }
}

@Composable
private fun UnitChip(
    label: String,
    chipUnit: UnitSystem,
    current: UnitSystem,
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
                .clickable { onUnitSelected(chipUnit) }
        )
    }
}

/* ─────────────── Preview + Free-to-End badge ─────────────── */

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
                // Read composition locals (MaterialTheme) OUTSIDE the Canvas
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                val step = gridStepDp

                Canvas(Modifier.fillMaxSize()) {
                    val stepPx = step.toPx()
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

            val freeMm  = spec.freeToEndMm()
            val freeTxt = formatDisplay(freeMm, unit)

            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)   // <-- was TopStart
                    .padding(top = 6.dp)          // a bit of breathing room
            ) {
                Text(
                    text = "Free to end: $freeTxt ${abbr(unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/* ─────────────── Unified Components List (no grouping) ─────────────── */

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
    val rows = buildList {
        spec.bodies.forEachIndexed  { i, b  -> add(RowRef(RowKind.BODY,   i, b.startFromAftMm)) }
        spec.tapers.forEachIndexed  { i, t  -> add(RowRef(RowKind.TAPER,  i, t.startFromAftMm)) }
        spec.threads.forEachIndexed { i, th -> add(RowRef(RowKind.THREAD, i, th.startFromAftMm)) }
        spec.liners.forEachIndexed  { i, ln -> add(RowRef(RowKind.LINER,  i, ln.startFromAftMm)) }
    }.sortedByDescending { it.start }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            when (row.kind) {
                RowKind.BODY -> {
                    val b = spec.bodies[row.index]
                    ComponentCard(
                        title = "Body #${row.index + 1}",
                        onRemove = { onRemoveBody(row.index) }
                    ) {
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
                    ComponentCard(
                        title = "Taper #${row.index + 1}",
                        onRemove = { onRemoveTaper(row.index) }
                    ) {
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
                    ComponentCard(
                        title = "Thread #${row.index + 1}",
                        onRemove = { onRemoveThread(row.index) }
                    ) {
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
                                onUpdateThread(
                                    row.index,
                                    th.startFromAftMm,
                                    th.lengthMm,
                                    th.majorDiaMm,
                                    tpiToPitchMm(tpi)
                                )
                            }
                        }
                    }
                }
                RowKind.LINER -> {
                    val ln = spec.liners[row.index]
                    ComponentCard(
                        title = "Liner #${row.index + 1}",
                        onRemove = { onRemoveLiner(row.index) }
                    ) {
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

/* Small card wrapper for component rows */
/* Small card wrapper for component rows */
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
            // Main content column
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                content()
            }

            // Top-right remove icon (if provided)
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


/* ───────────── Collapsible section + commit-on-blur text fields ───────────── */

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

/* ───────────── Add chooser dialog (kept LOCAL to this file) ───────────── */

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

/* ───────────── Helpers: units, parsing, defaults ───────────── */

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

/** Accepts "12", "3/4", "1.5" or "1:12". */
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

/** Defaults for new components (in mm). */
private data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
private fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    val lastEnd = listOfNotNull(
        spec.bodies.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        spec.tapers.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        spec.liners.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
    ).maxOrNull() ?: 0f
    val lastDia = spec.bodies.lastOrNull()?.diaMm
        ?: spec.tapers.lastOrNull()?.endDiaMm
        ?: 25f
    return AddDefaults(startMm = lastEnd, lastDiaMm = lastDia)
}
