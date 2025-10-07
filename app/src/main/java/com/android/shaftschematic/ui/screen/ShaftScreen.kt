package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.ui.config.defaultBodyLenMm
import com.android.shaftschematic.ui.config.defaultLinerLenMm
import com.android.shaftschematic.ui.config.defaultTaperLenMm
import com.android.shaftschematic.ui.config.defaultThreadLenMm
import com.android.shaftschematic.ui.config.defaultThreadMajorDiaMm
import com.android.shaftschematic.ui.config.defaultThreadPitchMm
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.util.UnitSystem

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Header row (unit selector + grid toggle; unit selector disables when locked)
 * • Preview drawing (white square; optional grid; fixed-height band)
 * • Free-to-End badge overlay (top-start of preview; red on oversize)
 * • Overall length input (ghost “0”; commits on blur/Done; auto when not manual)
 * • Project fields (commit-on-blur / IME Done)
 * • Component cards (edit & remove) — rendering honors cross-type ID order
 * • Add-component FAB floating above IME & nav bar
 *
 * Contract / Invariants
 * • Canonical model units are millimeters (mm) — convert only at UI edge.
 * • No geometry-based resorting. When provided, UI renders strictly by componentOrder (IDs).
 * • IME safety: scroll area uses imePadding; FAB uses ime ∪ navigationBars insets.
 * • No file I/O or routing here.
 */
@Composable
fun ShaftScreen(
    // Ordering (from VM via Route)
    componentOrder: List<ComponentKey> = emptyList(),
    onMoveComponentUp: (String) -> Unit = {},      // reserved for future Move UI
    onMoveComponentDown: (String) -> Unit = {},    // reserved for future Move UI

    // State
    spec: ShaftSpec,
    unit: UnitSystem,
    overallIsManual: Boolean,
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
    onSetOverallLengthRaw: (String) -> Unit,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetOverallIsManual: (Boolean) -> Unit,

    // Adds (all mm)
    onAddBody: (Float, Float, Float) -> Unit,
    onAddTaper: (Float, Float, Float, Float) -> Unit,
    onAddThread: (Float, Float, Float, Float) -> Unit, // order: start, length, pitch, majorDia
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
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // Auto-sync overall when not manual
    LaunchedEffect(overallIsManual, spec.bodies, spec.tapers, spec.threads, spec.liners) {
        if (!overallIsManual) {
            val end = lastOccupiedEndMm(spec)
            if (end != spec.overallLengthMm) onSetOverallLengthMm(end)
        }
    }

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
            ) { Icon(Icons.Filled.Add, contentDescription = "Add component") }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
        ) {
            // Preview
            PreviewCard(
                showGrid = showGrid,
                gridStepDp = 16.dp,
                spec = spec,
                unit = unit,
                renderShaft = renderShaft,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
                    .aspectRatio(3.0f)
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Scrollable editor content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Length (auto vs manual — always show a value)
                var hasLenFocus by remember { mutableStateOf(false) }

// keep local text in sync with unit/mode unless the user is typing
                val displayMm = if (overallIsManual) spec.overallLengthMm else lastOccupiedEndMm(spec)
                var lengthText by remember(unit, displayMm, overallIsManual) {
                    mutableStateOf(formatDisplay(displayMm, unit))
                }

                val freeSignedMm = spec.overallLengthMm - lastOccupiedEndMm(spec)
                val isOversized = freeSignedMm < 0f

                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { input ->
                        lengthText = input
                        if (overallIsManual) {
                            toMmOrNull(input, unit)?.let { mm ->
                                onSetOverallLengthMm(mm)
                            }
                        }
                    },
                    label = { Text("Overall Length (${abbr(unit)})") },
                    singleLine = true,
                    enabled = overallIsManual, // auto mode is read-only
                    isError = isOversized,
                    supportingText = {
                        val mode = if (overallIsManual) "Manual" else "Auto"
                        val hint = if (isOversized)
                            "Oversized by ${formatDisplay(-freeSignedMm, unit)} ${abbr(unit)}"
                        else "$mode • ${formatDisplay(displayMm, unit)} ${abbr(unit)}"
                        Text(hint)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val t = lengthText.trim()
                        // empty on Done ⇒ switch back to Auto
                        if (t.isEmpty()) {
                            onSetOverallIsManual(false)
                            onSetOverallLengthMm(lastOccupiedEndMm(spec))
                        } else {
                            toMmOrNull(t, unit)?.let { mm ->
                                onSetOverallLengthMm(mm)
                                onSetOverallIsManual(true)
                                onSetOverallLengthRaw(t) // keep user’s display text
                            }
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f ->
                            hasLenFocus = f.isFocused
                            if (!f.isFocused) {
                                val t = lengthText.trim()
                                if (t.isEmpty()) {
                                    onSetOverallIsManual(false)
                                    onSetOverallLengthMm(lastOccupiedEndMm(spec))
                                } else {
                                    toMmOrNull(t, unit)?.let { mm ->
                                        onSetOverallLengthMm(mm)
                                        onSetOverallIsManual(true)
                                        onSetOverallLengthRaw(t)
                                    }
                                }
                            }
                        }
                )


                // Project info (optional)
                ExpandableSection("Project Information (optional)", initiallyExpanded = false) {
                    CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                    CommitTextField("Customer", customer, onSetCustomer, Modifier.fillMaxWidth())
                    CommitTextField("Vessel", vessel, onSetVessel, Modifier.fillMaxWidth())
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

                // >>> PASS THE ORDER DOWN (fixes unresolved reference)
                ComponentsUnifiedList(
                    spec = spec,
                    unit = unit,
                    componentOrder = componentOrder,      // NEW
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

        // Centralized defaults (mm)
        val bodyLenMm     = defaultBodyLenMm(unit)
        val linerLenMm    = defaultLinerLenMm(unit)
        val taperLenMm    = defaultTaperLenMm(unit)
        val threadLenMm   = defaultThreadLenMm(unit)
        val threadMajMm   = defaultThreadMajorDiaMm(unit)
        val threadPitchMm = defaultThreadPitchMm()
        val taperRatio    = AddDefaultsConfig.TAPER_RATIO

        InlineAddChooserDialog(
            onDismiss = { chooserOpen = false },
            onAddBody = { chooserOpen = false; onAddBody(d.startMm, bodyLenMm, d.lastDiaMm) },
            onAddLiner = { chooserOpen = false; onAddLiner(d.startMm, linerLenMm, d.lastDiaMm) },
            onAddThread = {
                chooserOpen = false
                // IMPORTANT: order = start, length, pitch, majorDia
                onAddThread(d.startMm, threadLenMm, threadPitchMm, threadMajMm)
            },
            onAddTaper = {
                chooserOpen = false
                val len = taperLenMm
                val setDiaMm = d.lastDiaMm
                val letDiaMm = setDiaMm + (len * taperRatio) // ~1:12
                onAddTaper(d.startMm, len, setDiaMm, letDiaMm)
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
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.height(36.dp).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        shape = MaterialTheme.shapes.large
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
    gridStepDp: Dp, // kept for signature stability; unused now
    spec: ShaftSpec,
    unit: UnitSystem,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // IMPORTANT: No local Canvas grid here. Let renderShaft handle grid so it follows pan/zoom.
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            renderShaft(spec, unit) // ensure this calls ShaftDrawing(spec, unit, showGrid)
            FreeToEndBadge(
                spec = spec,
                unit = unit,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            )
        }
    }
}

/* ───────────────── Components (editable + remove) ───────────────── */

private data class RowRef(
    val kind: ComponentKind,
    val index: Int,
    val start: Float,
    val id: String
)

/**
 * ComponentsUnifiedList
 * - Renders strictly by ViewModel-provided cross-type order when available.
 * - No geometry re-sorting.
 * - Legacy fallback preserves stable type grouping if order is empty.
 */
@Composable
private fun ComponentsUnifiedList(
    spec: ShaftSpec,
    unit: UnitSystem,
    componentOrder: List<ComponentKey>,  // <<< NEW PARAM
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onRemoveBody: (Int) -> Unit,
    onRemoveTaper: (Int) -> Unit,
    onRemoveThread: (Int) -> Unit,
    onRemoveLiner: (Int) -> Unit
) {
    // Fast ID → index maps
    val bodyIdx   = remember(spec.bodies)  { spec.bodies.withIndex().associate { it.value.id to it.index } }
    val taperIdx  = remember(spec.tapers)  { spec.tapers.withIndex().associate { it.value.id to it.index } }
    val threadIdx = remember(spec.threads) { spec.threads.withIndex().associate { it.value.id to it.index } }
    val linerIdx  = remember(spec.liners)  { spec.liners.withIndex().associate { it.value.id to it.index } }

    // Build rows according to the provided cross-type order
    val rows: List<RowRef> =
        if (componentOrder.isNotEmpty()) {
            buildList {
                componentOrder.forEach { key ->
                    when (key.kind) {
                        ComponentKind.BODY   -> bodyIdx[key.id]?.let   { i -> add(RowRef(ComponentKind.BODY,   i, spec.bodies[i].startFromAftMm,  key.id)) }
                        ComponentKind.TAPER  -> taperIdx[key.id]?.let  { i -> add(RowRef(ComponentKind.TAPER,  i, spec.tapers[i].startFromAftMm,  key.id)) }
                        ComponentKind.THREAD -> threadIdx[key.id]?.let { i -> add(RowRef(ComponentKind.THREAD, i, spec.threads[i].startFromAftMm, key.id)) }
                        ComponentKind.LINER  -> linerIdx[key.id]?.let  { i -> add(RowRef(ComponentKind.LINER,  i, spec.liners[i].startFromAftMm,  key.id)) }
                    }
                }
            }
        } else {
            // Legacy fallback (no geometry resorting)
            buildList {
                spec.bodies.forEachIndexed  { i, b  -> add(RowRef(ComponentKind.BODY,   i, b.startFromAftMm,  b.id)) }
                spec.tapers.forEachIndexed  { i, t  -> add(RowRef(ComponentKind.TAPER,  i, t.startFromAftMm,  t.id)) }
                spec.threads.forEachIndexed { i, th -> add(RowRef(ComponentKind.THREAD, i, th.startFromAftMm, th.id)) }
                spec.liners.forEachIndexed  { i, ln -> add(RowRef(ComponentKind.LINER,  i, ln.startFromAftMm, ln.id)) }
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            when (row.kind) {
                ComponentKind.BODY -> {
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
                ComponentKind.TAPER -> {
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
                ComponentKind.THREAD -> {
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
                ComponentKind.LINER -> {
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
        shape = MaterialTheme.shapes.extraLarge
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
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
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
        shape = MaterialTheme.shapes.extraLarge,
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ───────────────── Helpers: units, parsing, badge math, defaults ───────────────── */

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

/** Latest occupied end position along the shaft (mm) from all components. */
private fun lastOccupiedEndMm(spec: ShaftSpec): Float {
    var end = 0f
    spec.bodies.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    return end
}

/* ───────────────── Free-to-End badge ───────────────── */

@Composable
private fun FreeToEndBadge(
    spec: ShaftSpec,
    unit: UnitSystem,
    modifier: Modifier = Modifier
) {
    val endMm = lastOccupiedEndMm(spec)
    val freeSignedMm = spec.overallLengthMm - endMm
    val isOversized = freeSignedMm < 0f

    val bg = if (isOversized) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val fg = if (isOversized) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        tonalElevation = if (isOversized) 3.dp else 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = "Free to end: ${formatDisplay(freeSignedMm, unit)} ${abbr(unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
@Suppress("unused")
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/** Defaults for new components (mm). */
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
