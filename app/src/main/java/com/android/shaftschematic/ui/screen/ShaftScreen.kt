package com.android.shaftschematic.ui.screen

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.coverageEndMm
import com.android.shaftschematic.model.freeToEndMm
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch
import java.io.File

/**
 * File: ShaftScreen.kt
 * Layer: UI → Screens
 * Purpose: Present the Shaft editor surface: preview, unit toggle, grid toggle,
 * overall length, project info, and component editors.
 *
 * Responsibilities
 * • Bind ViewModel state (spec/unit/grid/customer/etc.) to UI controls.
 * • Enforce unit boundaries at the **UI edge** (mm↔in conversion) before writing to VM.
 * • Render the preview via ShaftDrawing; compute derived labels (e.g., Free to end).
 *
 * Invariants
 * • Model stays in millimeters at all times; only display strings convert units.
 * • Text fields commit on blur/IME Done; while editing, they hold local text state.
 *
 * Notes
 * • Keep business logic out of the screen; push derived calculations into model helpers
 * (see: ShaftSpecExtensions.freeToEndMm()).
 * • Avoid heavyweight recomposition by scoping state and using remember where appropriate.
 */

private fun shaftsDir(context: Context): File =
    File(context.filesDir, "shafts").apply { mkdirs() }

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
    onSetOverallLengthMm: (Float) -> Unit,

    // Add callbacks (mm)
    onAddBody: (startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onAddTaper: (startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onAddLiner: (startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

    // Update callbacks (mm) – left untouched
    onUpdateBody: (index: Int, startMm: Float, lengthMm: Float, diaMm: Float) -> Unit,
    onUpdateTaper: (index: Int, startMm: Float, lengthMm: Float, setDiaMm: Float, letDiaMm: Float) -> Unit,
    onUpdateThread: (index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) -> Unit,
    onUpdateLiner: (index: Int, startMm: Float, lengthMm: Float, odMm: Float) -> Unit,

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

        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
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

                OverallLengthField(
                    specOverallMm = spec.overallLengthMm,
                    unit = unit,
                    onCommitMm = { mm -> onSetOverallLengthMm(mm) }, // <-- pass mm straight through
                    modifier = Modifier.fillMaxWidth()
                )

                // PROJECT INFO (collapsible)
                ExpandableSection(
                    title = "Project Information (optional)",
                    initiallyExpanded = false
                ) {
                    CommitTextField(
                        label = "Job Number",
                        value = jobNumber,                 // VM value
                        onCommit = { onSetJobNumber(it) }, // VM setter
                        modifier = Modifier.fillMaxWidth()
                    )
                    CommitTextField(
                        label = "Customer",
                        value = customer,                 // VM value
                        onCommit = { onSetCustomer(it) }, // same setter as before (keeps PDF naming flow)
                        modifier = Modifier.fillMaxWidth()
                    )
                    CommitTextField(
                        label = "Vessel",
                        value = vessel,
                        onCommit = { onSetVessel(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    CommitTextField(
                        label = "Notes",
                        value = notes,
                        onCommit = { onSetNotes(it) },
                        singleLine = false,                // multiline notes
                        minHeight = 88.dp,                 // a comfortable starting height
                        modifier = Modifier.fillMaxWidth()
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
            val d = computeAddDefaults(spec) // d.startMm & d.lastDiaMm are mm
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
                    val defaultLenMm = if (unit == UnitSystem.INCHES) 16f * 25.4f else 100f
                    val setDiaMm = d.lastDiaMm
                    val letDiaMm = setDiaMm + (defaultLenMm / 12f)
                    onAddTaper(d.startMm, defaultLenMm, setDiaMm, letDiaMm)
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
            Text(
                "Shaft Details",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
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
        Row(
            Modifier
                .height(36.dp)
                .padding(4.dp), verticalAlignment = Alignment.CenterVertically
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp)); content()
            }
        }
    }
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
        // ⬇️ Everything that needs .align(...) must live inside this Box
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

            // Shaft preview
            renderShaft(spec, unit)

            // Debug HUD
//            DebugHud(
//                spec = spec,
//                unit = unit,
//                modifier = Modifier
//                    .align(Alignment.TopEnd)
//                    .padding(6.dp)
//            )

            // ✅ Free-to-end badge now inside the Box
            val freeMm = spec.freeToEndMm()
            val freeTxt = formatMmForDisplay(freeMm, unit)
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
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
        val w = size.width;
        val h = size.height
        val majorStroke = 2.0f
        val minorStroke = 1.2f
        val majorColor = color.copy(alpha = 0.70f)
        val minorColor = color.copy(alpha = 0.35f)

        var x = 0f;
        var i = 0
        while (x <= w + 0.5f) {
            val isMajor = (i % 5 == 0)
            drawLine(
                if (isMajor) majorColor else minorColor, Offset(x, 0f), Offset(x, h),
                if (isMajor) majorStroke else minorStroke
            )
            x += stepPx; i++
        }
        var y = 0f; i = 0
        while (y <= h + 0.5f) {
            val isMajor = (i % 5 == 0)
            drawLine(
                if (isMajor) majorColor else minorColor, Offset(0f, y), Offset(w, y),
                if (isMajor) majorStroke else minorStroke
            )
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
        spec.liners.forEachIndexed { i, ln -> add(RowRef(RowKind.LINER, i, ln.startFromAftMm)) }
        spec.bodies.forEachIndexed { i, b -> add(RowRef(RowKind.BODY, i, b.startFromAftMm)) }
        spec.threads.forEachIndexed { i, th -> add(RowRef(RowKind.THREAD, i, th.startFromAftMm)) }
        spec.tapers.forEachIndexed { i, t -> add(RowRef(RowKind.TAPER, i, t.startFromAftMm)) }
    }.sortedByDescending { it.start }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            when (row.kind) {

                RowKind.LINER -> {
                    val ln = spec.liners[row.index]
                    ComponentCard("Liner #${row.index + 1}") {
                        CommitNum("Start (${abbr(unit)})", disp(ln.startFromAftMm, unit)) { s ->
                            val startMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateLiner(row.index, startMm, ln.lengthMm, ln.odMm)
                        }
                        CommitNum("Length (${abbr(unit)})", disp(ln.lengthMm, unit)) { s ->
                            val lenMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateLiner(row.index, ln.startFromAftMm, lenMm, ln.odMm)
                        }
                        CommitNum("Outer Ø (${abbr(unit)})", disp(ln.odMm, unit)) { s ->
                            val odMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateLiner(row.index, ln.startFromAftMm, ln.lengthMm, odMm)
                        }
                    }
                }

                RowKind.BODY -> {
                    val b = spec.bodies[row.index]
                    ComponentCard("Body #${row.index + 1}") {
                        CommitNum("Start (${abbr(unit)})", disp(b.startFromAftMm, unit)) { s ->
                            val startMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateBody(row.index, startMm, b.lengthMm, b.diaMm)
                        }
                        CommitNum("Length (${abbr(unit)})", disp(b.lengthMm, unit)) { s ->
                            val lenMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateBody(row.index, b.startFromAftMm, lenMm, b.diaMm)
                        }
                        CommitNum("Ø (${abbr(unit)})", disp(b.diaMm, unit)) { s ->
                            val diaMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateBody(row.index, b.startFromAftMm, b.lengthMm, diaMm)
                        }
                    }
                }

                RowKind.THREAD -> {
                    val th = spec.threads[row.index]
                    val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
                    ComponentCard("Thread #${row.index + 1}") {
                        CommitNum("Start (${abbr(unit)})", disp(th.startFromAftMm, unit)) { s ->
                            val startMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateThread(
                                row.index,
                                startMm,
                                th.lengthMm,
                                th.majorDiaMm,
                                th.pitchMm
                            )
                        }
                        CommitNum("Length (${abbr(unit)})", disp(th.lengthMm, unit)) { s ->
                            val lenMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateThread(
                                row.index,
                                th.startFromAftMm,
                                lenMm,
                                th.majorDiaMm,
                                th.pitchMm
                            )
                        }
                        CommitNum("Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit)) { s ->
                            val majMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateThread(
                                row.index,
                                th.startFromAftMm,
                                th.lengthMm,
                                majMm,
                                th.pitchMm
                            )
                        }
                        if (unit == UnitSystem.INCHES) {
                            CommitNum("TPI", tpiDisplay) { s ->
                                val tpi = parseFractionOrDecimal(s) ?: 0f
                                val pitchMm = if (tpi > 0f) 25.4f / tpi else th.pitchMm
                                onUpdateThread(
                                    row.index,
                                    th.startFromAftMm,
                                    th.lengthMm,
                                    th.majorDiaMm,
                                    pitchMm
                                )
                            }
                        } else {
                            CommitNum("Pitch (mm)", disp(th.pitchMm, UnitSystem.MILLIMETERS)) { s ->
                                val pitchMm = toMmOrNull(s, UnitSystem.MILLIMETERS) ?: 0f
                                onUpdateThread(
                                    row.index,
                                    th.startFromAftMm,
                                    th.lengthMm,
                                    th.majorDiaMm,
                                    pitchMm
                                )
                            }
                        }
                    }
                }

                RowKind.TAPER -> {
                    val t = spec.tapers[row.index]
                    ComponentCard("Taper #${row.index + 1}") {
                        CommitNum("Start (${abbr(unit)})", disp(t.startFromAftMm, unit)) { s ->
                            val startMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateTaper(row.index, startMm, t.lengthMm, t.startDiaMm, t.endDiaMm)
                        }
                        CommitNum("Length (${abbr(unit)})", disp(t.lengthMm, unit)) { s ->
                            val lenMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateTaper(
                                row.index,
                                t.startFromAftMm,
                                lenMm,
                                t.startDiaMm,
                                t.endDiaMm
                            )
                        }
                        CommitNum("S.E.T. Ø (${abbr(unit)})", disp(t.startDiaMm, unit)) { s ->
                            val sdMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, sdMm, t.endDiaMm)
                        }
                        CommitNum("L.E.T. Ø (${abbr(unit)})", disp(t.endDiaMm, unit)) { s ->
                            val edMm = toMmOrNull(s, unit) ?: 0f
                            onUpdateTaper(
                                row.index,
                                t.startFromAftMm,
                                t.lengthMm,
                                t.startDiaMm,
                                edMm
                            )
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun DebugHud(spec: ShaftSpec, unit: UnitSystem, modifier: Modifier = Modifier) {
    val endMm = spec.coverageEndMm()
    val freeMm = spec.freeToEndMm()
    val freeTxt = formatMmForDisplay(freeMm, unit)
    val tpLenMm = spec.tapers.lastOrNull()?.lengthMm ?: 0f
    val bdLenMm = spec.bodies.lastOrNull()?.lengthMm ?: 0f

    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Text("overall=${"%.1f".format(spec.overallLengthMm)} mm")
            Text("coverageEnd=${"%.1f".format(endMm)} mm")
            Text("free=${"%.1f".format(freeMm)} mm  ($freeTxt ${abbr(unit)})")
            Text("last taper len=${"%.1f".format(tpLenMm)} mm")
            Text("last body len =${"%.1f".format(bdLenMm)} mm")
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

/* Number/text inputs that keep local text and commit on blur/Done */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommitNum(
    label: String,
    initialDisplay: String,
    onCommit: (String) -> Unit
) {
    // Hard-value echo; start with whatever the VM has (including "0")
    var text by rememberSaveable(initialDisplay) { mutableStateOf(initialDisplay) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,                  // no placeholder/ghost
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { onCommit(text.ifBlank { "0" }) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                if (!f.isFocused) onCommit(text.ifBlank { "0" }) // commit-on-blur
            }
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
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddBody) { Text("Body") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddLiner) { Text("Liner") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddThread) { Text("Thread") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAddTaper) { Text("Taper") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ───────────── Helpers: defaults, parsing, units, etc. ───────────── */


// mm → display string (does the ONE conversion; formatting only)
private fun formatMmForDisplay(mm: Float, unit: UnitSystem): String {
    val v = if (unit == UnitSystem.INCHES) mm / 25.4f else mm
    // fmtTrim drops decimals for whole numbers (you already use it elsewhere)
    return v.fmtTrim(if (unit == UnitSystem.INCHES) 3 else 1)
}

// helpers at top of file (if not already present)
private fun parseNum(text: String): Float =
    parseFractionOrDecimal(text) ?: text.replace(",", "").trim().toFloatOrNull() ?: 0f

private fun asMm(valueInUnit: Float, unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) valueInUnit * 25.4f else valueInUnit

@Composable
private fun SaveLoadRow(
    vm: ShaftViewModel,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSave by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { showSave = true }) { Text("Save") }
        OutlinedButton(onClick = { showLoad = true }) { Text("Load") }
    }

    if (showSave) SaveDialog(
        onDismiss = { showSave = false },
        onConfirm = { name ->
            val dir = shaftsDir(ctx)
            scope.launch { vm.saveToFile(dir, name.trim()) }
            showSave = false
        }
    )

    if (showLoad) LoadDialog(
        dirProvider = { shaftsDir(ctx) },
        onDismiss = { showLoad = false },
        onLoad = { name ->
            val dir = shaftsDir(ctx)
            scope.launch { vm.loadFromFile(dir, name) }
            showLoad = false
        },
        onDelete = { name ->
            val f = File(shaftsDir(ctx), "$name.json")
            if (f.exists()) f.delete()
        }
    )
}

@Composable
private fun SaveDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save shaft as…") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LoadDialog(
    dirProvider: () -> File,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val (items, refresh) = remember {
        mutableStateOf<List<String>>(emptyList()) to mutableStateOf(false)
    }
    val dir = dirProvider()
    LaunchedEffect(dir, refresh.value) {
        val list =
            dir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }
                ?.sorted()

        @Suppress("NAME_SHADOWING")
        val itemsList = list ?: emptyList()
        items.value = itemsList   // ignored by compiler; see below
    }
    // Workaround to update remembered list cleanly
    val names = remember(refresh.value) {
        dir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open saved shaft") },
        text = {
            if (names.isEmpty()) {
                Text("No saved shafts yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(names) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onLoad(name) }) { Text("Load") }
                                TextButton(onClick = {
                                    onDelete(name)
                                    // trigger refresh
                                    refresh.value = !refresh.value
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun parseLinearToMm(text: String, unit: UnitSystem): Float {
    val v = text.replace(",", "").trim().toFloatOrNull() ?: 0f
    return if (unit == UnitSystem.INCHES) v * 25.4f else v
}

private fun fromMmLinear(mm: Float, unit: UnitSystem): String {
    val v = if (unit == UnitSystem.INCHES) mm / 25.4f else mm
    return formatDisplay(v, unit) // uses your integer/no-decimal rule
}

private fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

private fun formatDisplay(valueMm: Float, unit: UnitSystem, d: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) valueMm else valueMm / 25.4f
    return "%.${d}f".format(v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

private fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String = formatDisplay(mm, unit, d)

/** Parse user text (supports fractions) in the CURRENT unit and return millimeters. */
private fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val trimmed = text.replace(",", "").trim()
    // Try fractional formats like "6 1/2" or "1/4"
    val vInUnit = parseFractionOrDecimal(trimmed)
        ?: trimmed.toFloatOrNull()
        ?: return null

    return if (unit == UnitSystem.INCHES) vInUnit * 25.4f else vInUnit
}


/** Convert mm → current unit and format according to unit rules. */
private fun displayFromMm(mm: Float, unit: UnitSystem): String {
    val v = if (unit == UnitSystem.INCHES) mm / 25.4f else mm
    return formatDisplay(v, unit)
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

// Threads: TPI ↔ pitch mm
private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommitTextField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = Dp.Unspecified,
) {
    val scope = rememberCoroutineScope()
    val bringer = remember { BringIntoViewRequester() }
    // Local-echo: seed from VM value, but do not overwrite while typing
    var text by rememberSaveable(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .bringIntoViewRequester(bringer)
            .onFocusEvent { fe ->
                if (fe.isFocused) scope.launch { bringer.bringIntoView() }
                if (!fe.isFocused) onCommit(text) // commit-on-blur
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) })
    )
}

/** Overall Length: local echo + commit **mm** on blur/Done; never flips UI text to mm */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverallLengthField(
    specOverallMm: Float,
    unit: UnitSystem,
    onCommitMm: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable(unit) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val bringer = remember { BringIntoViewRequester() }

    // Seed only if blank, and only with the current unit's formatted value.
    LaunchedEffect(specOverallMm, unit) {
        if (text.isBlank()) {
            val valueInUnit =
                if (unit == UnitSystem.INCHES) specOverallMm / 25.4f else specOverallMm
            if (valueInUnit != 0f) text = formatDisplay(valueInUnit, unit)
        }
    }

    fun commit() {
        val v = text.replace(",", "").trim().toFloatOrNull() ?: 0f
        val mm = if (unit == UnitSystem.INCHES) v * 25.4f else v
        onCommitMm(mm)   // commit in mm; DO NOT mutate `text`
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Shaft Overall Length (${abbr(unit)})") },
        placeholder = { Text("0") }, // placeholder is fine here only
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringer)
            .onFocusEvent { fe ->
                if (fe.isFocused) scope.launch { bringer.bringIntoView() }
                if (!fe.isFocused) commit() // commit on blur
            }
    )
}
