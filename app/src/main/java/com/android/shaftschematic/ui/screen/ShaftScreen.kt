// file: com/android/shaftschematic/ui/screen/ShaftScreen.kt
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
import com.android.shaftschematic.model.*
import com.android.shaftschematic.ui.screen.ComponentType
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

/**
 * ShaftScreen
 *
 * Stable Compose UI for the Shaft Schematic app.
 * - Top bar: unit toggle, grid toggle, PDF export
 * - Preview: injected renderer (Compose drawing), optional DP grid underlay
 * - Overall Length (commit on blur/Done; no live mutation while typing)
 * - Project Info (collapsible): Job Number, Customer, Vessel, Notes
 * - Components:
 *   • FAB → chooser → inline insert with sensible defaults (no ephemeral dialog)
 *   • Unified editable list (newest-on-top) with commit-on-blur fields
 *
 * Data contract:
 * - All geometry in the ViewModel and models is **millimeters**.
 * - UI displays mm or inches depending on [unit]; conversions happen here.
 * - Threads show **TPI** (Threads Per Inch) regardless of unit for consistency.
 * - Tapers accept **S.E.T., L.E.T., Taper Rate, Length**. Taper Rate accepts:
 *     "1:12", "3/4", decimals, or bare "1" meaning **1:12** (legacy behavior).
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

    // Add callbacks (all mm)
    onAddBody:   (startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onAddTaper:  (startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onAddLiner:  (startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

    // Update callbacks (all mm; TPI converted to pitch mm before calling)
    onUpdateBody:   (index: Int, startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onUpdateTaper:  (index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) -> Unit,
    onUpdateThread: (index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onUpdateLiner:  (index: Int, startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

    // Other
    onExportPdf: () -> Unit,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    // Local UI state for add chooser
    var chooserOpen by remember { mutableStateOf(false) }

    // Scroll to top after inserting a new component (so the new card is visible)
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
        contentWindowInsets = WindowInsets.systemBars
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0) Preview
                PreviewCard(
                    showGrid = showGrid,
                    gridStepDp = 16.dp,
                    spec = spec,
                    unit = unit,
                    renderShaft = renderShaft,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                )

                HorizontalDivider()

                // 1) Overall Length (commit-on-blur)
                var lengthText by remember(unit, spec.overallLengthMm) {
                    mutableStateOf(formatDisplay(spec.overallLengthMm, unit))
                }
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it }, // keep local while typing
                    label = { Text("Shaft Overall Length (${abbr(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = { onSetOverallLengthRaw(lengthText) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f -> if (!f.isFocused) onSetOverallLengthRaw(lengthText) }
                )

                // 2) Project Info (collapsible) with Notes at bottom
                ExpandableSection(title = "Project Info", initiallyExpanded = true) {
                    CommitTextField(
                        "Job number (optional)",
                        jobNumber,
                        onSetJobNumber,
                        Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    CommitTextField(
                        "Customer (optional)",
                        customer,
                        onSetCustomer,
                        Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    CommitTextField(
                        "Vessel (optional)",
                        vessel,
                        onSetVessel,
                        Modifier.fillMaxWidth()
                    )
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

                // 3) Components — unified, newest-on-top
                Text("Components", style = MaterialTheme.typography.titleMedium)
                ComponentsUnifiedList(
                    spec = spec,
                    unit = unit,
                    onUpdateBody = onUpdateBody,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateThread = onUpdateThread,
                    onUpdateLiner = onUpdateLiner
                )
            }

            // FAB + chooser (inline insert)
            FloatingActionButton(
                onClick = { chooserOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) { Icon(Icons.Filled.Add, contentDescription = "Add component") }

            if (chooserOpen) {
                InlineAddChooserDialog(
                    onDismiss = { chooserOpen = false },
                    onSelect = { kind: ComponentType ->
                        chooserOpen = false
                        val d = computeAddDefaults(spec)
                        when (kind) {
                            ComponentType.BODY -> {
                                // 100 mm long with current dia
                                onAddBody(d.startMm, 100f, d.lastDiaMm)
                            }
                            ComponentType.LINER -> {
                                onAddLiner(d.startMm, 100f, d.lastDiaMm)
                            }
                            ComponentType.THREAD -> {
                                // default 10 TPI -> pitch mm
                                val pitchMm = 25.4f / 10f
                                onAddThread(d.startMm, 32f, d.lastDiaMm, pitchMm)
                            }
                            ComponentType.TAPER -> {
                                // default 1:12 over 100 mm; derive LET from SET + length/12
                                val set = d.lastDiaMm
                                val let = set + (100f / 12f)
                                onAddTaper(d.startMm, 100f, set, let)
                            }
                        }
                        scope.launch { scroll.animateScrollTo(0) } // new card visible
                    }
                )
            }
        } // Box
    } // Scaffold
}

/* ────────────────────────────────────────────────────────────────────────────
 * Top App Bar
 * ──────────────────────────────────────────────────────────────────────────── */

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
                Text("Grid"); Spacer(Modifier.width(4.dp))
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
private fun UnitChip(label: String, chipUnit: UnitSystem, current: UnitSystem, onUnitSelected: (UnitSystem) -> Unit) {
    val selected = chipUnit == current
    Surface(color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clickable { onUnitSelected(chipUnit) }
        )
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Collapsible section + commit-on-blur text field
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
            }
            if (expanded) { Spacer(Modifier.height(8.dp)); content() }
        }
    }
}

/** Text field that **does not** push updates while typing; commits on blur/Done. */
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
        onValueChange = { text = it }, // keep local
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Preview area (DP grid underlay; renderer injected)
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun PreviewCard(
    showGrid: Boolean,
    gridStepDp: Dp,
    spec: ShaftSpec,
    unit: UnitSystem,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
            if (showGrid) GridCanvas(step = gridStepDp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            renderShaft(spec, unit)
        }
    }
}
@Composable
private fun GridCanvas(step: Dp, color: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val stepPx = step.toPx().coerceAtLeast(1f)
        val w = size.width; val h = size.height
        val majorStroke = 1.8f
        val minorStroke = 1.2f
        val majorColor = color.copy(alpha = 0.60f)
        val minorColor = color.copy(alpha = 0.35f)

        // vertical lines
        var x = 0f; var i = 0
        while (x <= w + 0.5f) {
            val isMajor = i % 5 == 0
            val stroke = if (isMajor) majorStroke else minorStroke
            val c = if (isMajor) majorColor else minorColor
            drawLine(c, Offset(x, 0f), Offset(x, h), stroke)
            x += stepPx; i++
        }
        // horizontal lines
        var y = 0f; i = 0
        while (y <= h + 0.5f) {
            val isMajor = i % 5 == 0
            val stroke = if (isMajor) majorStroke else minorStroke
            val c = if (isMajor) majorColor else minorColor
            drawLine(c, Offset(0f, y), Offset(w, y), stroke)
            y += stepPx; i++
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 * Components unified list (newest-on-top) + commit-on-blur editors
 * ──────────────────────────────────────────────────────────────────────────── */

private enum class RowKind { LINER, BODY, THREAD, TAPER }
private data class RowRef(val kind: RowKind, val index: Int, val start: Float)

/** Unified, newest-on-top list with simple fields per component type. */
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
                        CommitText("Taper Rate (1:12, 3/4, 1)", rateFromDia(t.startDiaMm, t.endDiaMm, t.lengthMm, unit)) { rateText ->
                            val (outSet, outLet) = resolveTaperDiametersFromRate(
                                setDiaInput = t.startDiaMm,
                                letDiaInput = t.endDiaMm,
                                lengthMm = t.lengthMm,
                                unit = unit,
                                rateText = rateText
                            )
                            onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, outSet, outLet)
                        }
                    }
                }
            }
        }
    }
}

/* Small card wrapper rows use */
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/* Number/text fields that keep local text and commit on blur/Done */
@Composable private fun CommitNum(label: String, initialDisplay: String, onCommit: (String) -> Unit) {
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

@Composable private fun CommitText(label: String, initial: String, onCommit: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Add flow (chooser) — inline insert to keep new card visible & editable
 * ──────────────────────────────────────────────────────────────────────────── */
@Composable
private fun InlineAddChooserDialog(
    onDismiss: () -> Unit,
    onSelect: (ComponentType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Component") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSelect(ComponentType.BODY)   }, modifier = Modifier.fillMaxWidth()) { Text("Body") }
                Button(onClick = { onSelect(ComponentType.LINER)  }, modifier = Modifier.fillMaxWidth()) { Text("Liner") }
                Button(onClick = { onSelect(ComponentType.THREAD) }, modifier = Modifier.fillMaxWidth()) { Text("Thread") }
                Button(onClick = { onSelect(ComponentType.TAPER)  }, modifier = Modifier.fillMaxWidth()) { Text("Taper") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 * Helpers: defaults, conversions, parsing, taper rate math
 * ──────────────────────────────────────────────────────────────────────────── */

private fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

private fun formatDisplay(mm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) mm else mm / 25.4f
    return "%.${d}f".format(v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

private fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String = formatDisplay(mm, unit, d)

private fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val num = parseFractionOrDecimal(t) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) num else num * 25.4f
}

private fun Float.fmtTrim(d: Int) = "%.${d}f".format(this).trimEnd('0').trimEnd('.')

/** Accepts "12", "3/4", "1.5" and returns Float, or null. Also supports ratios like "1:12". */
private fun parseFractionOrDecimal(input: String): Float? {
    val t = input.trim()
    if (t.isEmpty()) return null
    val colon = t.indexOf(':')
    if (colon >= 0) {
        // For numeric purposes a:b ≈ a / b (we return the ratio value); callers interpret.
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

// Compute sensible defaults for a brand-new component (all in mm)
private data class AddDefaults(val startMm: Float, val lastDiaMm: Float)

private fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    // place new piece right after the last end among all components
    val lastEnd = listOfNotNull(
        spec.bodies.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.tapers.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.liners.maxOfOrNull  { it.startFromAftMm + it.lengthMm },
        spec.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
    ).maxOrNull() ?: 0f

    // reuse most recent diameter we know; fallback 25 mm
    val lastDia = spec.bodies.lastOrNull()?.diaMm
        ?: spec.tapers.lastOrNull()?.endDiaMm
        ?: 25f

    return AddDefaults(startMm = lastEnd, lastDiaMm = lastDia)
}

/** Threads: convert TPI → pitch mm and back. */
private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/**
 * Produce a user-friendly rate string for an existing taper (computed from SET/LET).
 * Rate ~= ΔDiameter / Length, in **display units** (mm→mm, in→in).
 * When close to 1:12, show "1:12".
 */
private fun rateFromDia(setDiaMm: Float, letDiaMm: Float, lengthMm: Float, unit: UnitSystem): String {
    if (lengthMm <= 0f) return ""
    val deltaMm = letDiaMm - setDiaMm
    val ratio = if (unit == UnitSystem.MILLIMETERS) deltaMm / lengthMm else (deltaMm / 25.4f) / (lengthMm / 25.4f)
    val approx12 = 1f / 12f
    return if (kotlin.math.abs(ratio - approx12) < 0.005f) "1:12" else ratio.fmtTrim(3)
}

/**
 * Resolve taper diameters using optional S.E.T., L.E.T., and a rate text.
 * Rules:
 *  - If both SET and LET provided → return them (rate displayed elsewhere).
 *  - Else if rate present and one diameter present → compute the other with Δ = rate * length (in current unit).
 *  - If rate is a single number like "1", interpret as **1:12** (legacy).
 */
private fun resolveTaperDiametersFromRate(
    setDiaInput: Float,     // may be -1f if blank
    letDiaInput: Float,     // may be -1f if blank
    lengthMm: Float,
    unit: UnitSystem,
    rateText: String
): Pair<Float, Float> {
    val hasSet = setDiaInput > 0f
    val hasLet = letDiaInput > 0f
    if (hasSet && hasLet) return setDiaInput to letDiaInput

    // Parse rate as a ratio value R = ΔDia / Length (in current unit)
    // Accept "1:12", "3/4", "0.0833", or bare "1" → 1:12
    val r = when {
        rateText.trim().isEmpty() -> null
        rateText.trim() == "1" -> 1f / 12f
        else -> parseFractionOrDecimal(rateText)
    } ?: return (setDiaInput.takeIf { it > 0f } ?: 0f) to (letDiaInput.takeIf { it > 0f } ?: 0f)

    val lengthDisp = if (unit == UnitSystem.MILLIMETERS) lengthMm else lengthMm / 25.4f
    val deltaDisp = r * lengthDisp
    val deltaMm = if (unit == UnitSystem.MILLIMETERS) deltaDisp else deltaDisp * 25.4f

    return when {
        hasSet -> setDiaInput to (setDiaInput + deltaMm)
        hasLet -> (letDiaInput - deltaMm) to letDiaInput
        else   -> 0f to 0f
    }
}
