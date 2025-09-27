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
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

/**
 * ShaftScreen (self-contained version)
 *
 * Insets policy:
 * • The scrolling content gets .imePadding() so fields are never hidden.
 * • Only the FAB gets ime + nav padding to float above the keyboard.
 * • We DO NOT add full-screen ime padding – no “white slab”.
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

    // Setters (commit-on-blur)
    onSetUnit: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,

    // Add callbacks (mm)
    onAddBody:   (startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onAddTaper:  (startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onAddLiner:  (startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

    // Update callbacks (mm) – left untouched
    onUpdateBody:   (index: Int, startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onUpdateTaper:  (index: Int, startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float) -> Unit,
    onUpdateThread: (index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onUpdateLiner:  (index: Int, startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

    // Other
    onExportPdf: () -> Unit,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var chooserOpen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppBar(
                unit = unit,
                gridChecked = showGrid,
                onUnitSelected = onSetUnit,
                onToggleGrid = onToggleGrid,
                onExportPdf = onExportPdf
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },

        // Top + horizontal only; bottom/IME handled by the Column/FAB.
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),

        floatingActionButton = {
            FloatingActionButton(
                onClick = { chooserOpen = true },
                modifier = Modifier
                    // Keep FAB above IME and nav bars (this is the ONLY place we add IME padding globally).
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add component")
            }
        }
    ) { innerPadding ->

        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scroll)
                    .imePadding(), // <- fields scroll above keyboard; no white slab
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PREVIEW
                PreviewCard(
                    showGrid = showGrid,
                    gridStepDp = 16.dp,
                    spec = spec,
                    unit = unit,
                    renderShaft = renderShaft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)   // was heightIn(min = 220.dp)
                )


                HorizontalDivider()

                // OVERALL LENGTH (commit on blur / Done)
                var lengthText by remember(unit, spec.overallLengthMm) {
                    mutableStateOf(formatDisplay(spec.overallLengthMm, unit))
                }
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it }, // keep local text while typing
                    label = { Text("Shaft Overall Length (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = { onSetOverallLengthRaw(lengthText) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f -> if (!f.isFocused) onSetOverallLengthRaw(lengthText) }
                )

                // PROJECT INFO (collapsible)
                ExpandableSection(title = "Project Info", initiallyExpanded = true) {
                    CommitTextField("Job number (optional)", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    CommitTextField("Customer (optional)",   customer,  onSetCustomer,  Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    CommitTextField("Vessel (optional)",     vessel,    onSetVessel,    Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    CommitTextField(
                        label = "Notes (optional)",
                        initial = notes,
                        onCommit = onSetNotes,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 5,
                        minHeight = 96.dp
                    )
                }

                // COMPONENTS LIST (newest-on-top)
                Text("Components", style = MaterialTheme.typography.titleMedium)
                ComponentsUnifiedList(
                    spec = spec,
                    unit = unit,
                    onUpdateBody = onUpdateBody,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateThread = onUpdateThread,
                    onUpdateLiner = onUpdateLiner
                )

                Spacer(Modifier.height(8.dp))
            }
        }

        // ADD DIALOG (no external enums; calls your add lambdas directly)
        if (chooserOpen) {
            val d = computeAddDefaults(spec)
            InlineAddChooserDialog(
                onDismiss = { chooserOpen = false },
                onAddBody = {
                    chooserOpen = false
                    onAddBody(d.startMm, 100f, d.lastDiaMm)
                    scope.launch { scroll.animateScrollTo(0) }
                },
                onAddLiner = {
                    chooserOpen = false
                    onAddLiner(d.startMm, 100f, d.lastDiaMm)
                    scope.launch { scroll.animateScrollTo(0) }
                },
                onAddThread = {
                    chooserOpen = false
                    onAddThread(d.startMm, 32f, d.lastDiaMm, 25.4f / 10f) // default 10 TPI
                    scope.launch { scroll.animateScrollTo(0) }
                },
                onAddTaper = {
                    chooserOpen = false
                    val set = d.lastDiaMm
                    val let = set + (100f / 12f) // ~1:12 over 100 mm
                    onAddTaper(d.startMm, 100f, set, let)
                    scope.launch { scroll.animateScrollTo(0) }
                }
            )
        }
    }
}

/* ───────────────────────── App Bar ───────────────────────── */

@Composable
private fun AppBar(
    unit: UnitSystem,
    gridChecked: Boolean,
    onUnitSelected: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onExportPdf: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shaft Details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            UnitSegment(unit = unit, onUnitSelected = onUnitSelected)
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grid")
                Spacer(Modifier.width(4.dp))
                Switch(checked = gridChecked, onCheckedChange = onToggleGrid)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onExportPdf) { Text("PDF") }
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
        Row(Modifier.height(36.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
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

/* ───── Collapsible section + commit-on-blur text fields ───── */

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
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (expanded) { Spacer(Modifier.height(8.dp)); content() }
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
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ─────────────── Preview card (DP grid + injected renderer) ─────────────── */

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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            if (showGrid) {
                GridCanvas(
                    step = gridStepDp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            }
            renderShaft(spec, unit)

            val lastEndMm = remember(spec) {
                listOfNotNull(
                    spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
                    spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
                    spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
                    spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
                ).maxOrNull() ?: 0f
            }
            val freeMm = (spec.overallLengthMm - lastEndMm).coerceAtLeast(0f)
            val freeTxt = formatDisplay(
                if (unit == UnitSystem.MILLIMETERS) freeMm else freeMm / 25.4f,
                unit
            )
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
            ) {
                Text(
                    text = "Free to end: $freeTxt ${abbr(unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun GridCanvas(step: Dp, color: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val stepPx = step.toPx().coerceAtLeast(1f)
        val w = size.width; val h = size.height
        val majorStroke = 2.0f
        val minorStroke = 1.2f
        val majorColor = color.copy(alpha = 0.70f)
        val minorColor = color.copy(alpha = 0.35f)

        var x = 0f; var i = 0
        while (x <= w + 0.5f) {
            val isMajor = (i % 5 == 0)
            drawLine(if (isMajor) majorColor else minorColor, Offset(x, 0f), Offset(x, h),
                if (isMajor) majorStroke else minorStroke)
            x += stepPx; i++
        }
        var y = 0f; i = 0
        while (y <= h + 0.5f) {
            val isMajor = (i % 5 == 0)
            drawLine(if (isMajor) majorColor else minorColor, Offset(0f, y), Offset(w, y),
                if (isMajor) majorStroke else minorStroke)
            y += stepPx; i++
        }
    }
}

/* ───────────── Components unified list + simple editors ───────────── */

private enum class RowKind { LINER, BODY, THREAD, TAPER }
private data class RowRef(val kind: RowKind, val index: Int, val start: Float)

@Composable
private fun ComponentsUnifiedList(
    spec: ShaftSpec,
    unit: UnitSystem,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit
) {
    val rows = buildList {
        spec.liners.forEachIndexed { i, ln -> add(RowRef(RowKind.LINER,  i, ln.startFromAftMm)) }
        spec.bodies.forEachIndexed { i, b  -> add(RowRef(RowKind.BODY,   i, b.startFromAftMm)) }
        spec.threads.forEachIndexed { i, th -> add(RowRef(RowKind.THREAD, i, th.startFromAftMm)) }
        spec.tapers.forEachIndexed  { i, t  -> add(RowRef(RowKind.TAPER,  i, t.startFromAftMm)) }
    }.sortedByDescending { it.start }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            when (row.kind) {
                RowKind.LINER -> {
                    val ln = spec.liners[row.index]
                    ComponentCard("Liner #${row.index + 1}") {
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
                RowKind.BODY -> {
                    val b = spec.bodies[row.index]
                    ComponentCard("Body #${row.index + 1}") {
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
                RowKind.THREAD -> {
                    val th = spec.threads[row.index]
                    val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
                    ComponentCard("Thread #${row.index + 1}") {
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
                RowKind.TAPER -> {
                    val t = spec.tapers[row.index]
                    ComponentCard("Taper #${row.index + 1}") {
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
            }
        }
    }
}

/* Small card wrapper for component rows */
@Composable
private fun ComponentCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/* Number/text inputs that keep local text and commit on blur/Done */
@Composable private fun CommitNum(label: String, initialDisplay: String, onCommit: (String) -> Unit) {
    var text by remember(initialDisplay) { mutableStateOf(initialDisplay) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}
@Composable private fun CommitText(label: String, initial: String, onCommit: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ───────────── Add chooser dialog (no external enums) ───────────── */

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
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddBody)   { Text("Body") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddLiner)  { Text("Liner") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddThread) { Text("Thread") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddTaper)  { Text("Taper") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ───────────── Helpers: defaults, parsing, units, etc. ───────────── */

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

/** Defaults for new components (in mm). */
private data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
private fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    val lastEnd = listOfNotNull(
        spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
    ).maxOrNull() ?: 0f
    val lastDia = spec.bodies.lastOrNull()?.diaMm
        ?: spec.tapers.lastOrNull()?.endDiaMm
        ?: 25f
    return AddDefaults(startMm = lastEnd, lastDiaMm = lastDia)
}

// Threads: TPI ↔ pitch mm
private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f
