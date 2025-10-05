package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.freeToEndMm
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.util.UnitSystem

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Header row (unit selector + grid toggle; unit selector disables when locked)
 * • Preview drawing (transparent; optional grid; fixed-height band)
 * • Free-to-End badge overlay (top-right of preview; mm-only math; clamped ≥ 0)
 * • Overall length input (unit-aware; commits mm; commit-on-blur/IME Done)
 * • Project fields (commit-on-blur / IME Done)
 * • Component cards (edit & remove)
 * • Add-component FAB floating above IME & nav bar
 *
 * Invariants / Contract
 * • Canonical model units are millimeters (mm); conversions only at the UI edge.
 * • Commit-on-blur: text fields commit when focus leaves or IME action triggers.
 * • IME safety: scrolling editor area uses imePadding; FAB uses ime ∪ navigationBars.
 * • Newest-on-top list: UI must not resort components by geometry.
 * • No file I/O here; routes/hosts handle Save/Load/PDF.
 * • Stable Material3 APIs only (no experimental opt-ins).
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
    onSetOverallLengthRaw: (String) -> Unit, // accepts fractions/decimals per app rules
    onSetOverallLengthMm: (Float) -> Unit,   // reserved for callers needing raw mm

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
        // Let us manage bottom insets in content; keep top/horizontal from system bars.
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { chooserOpen = true },
                modifier = Modifier
                    // Contract: FAB stays above IME and nav bars
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
                .padding(16.dp)
        ) {
            // Preview uses a derived spec when overall is unset (model stays untouched)
            val previewSpec = if (spec.overallLengthMm > 0f) spec
            else spec.copy(overallLengthMm = DEFAULT_OVERALL_MM)

            // Preview (fixed-height band, transparent, optional grid)
            PreviewCard(
                showGrid = showGrid,
                gridStepDp = 16.dp,
                spec = previewSpec,               // ← use previewSpec
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
                    // Safe when IME is hidden (avoid nav bar overlap)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                    // Safe when IME is shown
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Overall Length (ghost default, clears on focus; preserves user values) ──
                var hasLenFocus by remember { mutableStateOf(false) }
                var lengthText by remember(unit, spec.overallLengthMm) {
                    // When model is unset (0), start empty so placeholder shows; otherwise show formatted value
                    mutableStateOf(if (spec.overallLengthMm > 0f) formatDisplay(spec.overallLengthMm, unit) else "")
                }

                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    label = { Text("Overall Length (${abbr(unit)})") },
                    // Ghost placeholder: fixed text per product request (“100 in”), hidden while focused
                    placeholder = {
                        if (!hasLenFocus && lengthText.isEmpty()) Text("100 in")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = {
                        // Commit typed value (if any) on IME action
                        if (lengthText.isNotBlank()) onSetOverallLengthRaw(lengthText)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f ->
                            hasLenFocus = f.isFocused
                            if (!f.isFocused) {
                                // Commit typed value (if any) on blur; do nothing if still empty (keeps ghost next time)
                                if (lengthText.isNotBlank()) onSetOverallLengthRaw(lengthText)
                            }
                        }
                )

                // Project info (optional)
                ExpandableSection("Project Information (optional)", initiallyExpanded = false) {
                    CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                    CommitTextField("Customer",   customer,  onSetCustomer,  Modifier.fillMaxWidth())
                    CommitTextField("Vessel",     vessel,    onSetVessel,    Modifier.fillMaxWidth())
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
            },
            onAddLiner = {
                chooserOpen = false
                onAddLiner(d.startMm, defaultLenMm, d.lastDiaMm)
            },
            onAddThread = {
                chooserOpen = false
                onAddThread(d.startMm, defaultThreadLenMm, d.lastDiaMm, defaultPitchMm)
            },
            onAddTaper = {
                chooserOpen = false
                val len = defaultLenMm
                val setDiaMm = d.lastDiaMm
                val letDiaMm = setDiaMm + (len / 12f)
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
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

            // Shaft drawing
            renderShaft(spec, unit)

            // Badge overlay (top-center), style restored to previous
            FreeToEndBadge(
                spec = spec,
                unit = unit,
                modifier = Modifier
                    .align(Alignment.TopStart)   // ← previous alignment
                    .padding(top = 6.dp)          // ← previous spacing
            )
        }
    }
}


/* ───────────────── Components (editable + remove) ───────────────── */

private enum class RowKind { BODY, TAPER, THREAD, LINER }
private data class RowRef(val kind: RowKind, val index: Int, val start: Float)

/**
 * ComponentsUnifiedList — LOCKED: Newest-on-top (no geometry re-sorting)
 *
 * Contract:
 * - Do NOT sort by geometry here.
 * - Preserve the ViewModel's per-type list order (newest-first).
 * - Combine types in a stable group order for predictability.
 */
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
    onRemoveLiner: (Int) -> Unit
) {
    // IMPORTANT: We do NOT resort. We trust VM lists to be newest-first already.
    val rows = buildList {
        spec.bodies.forEachIndexed  { i, b  -> add(RowRef(RowKind.BODY,   i, b.startFromAftMm)) }
        spec.tapers.forEachIndexed  { i, t  -> add(RowRef(RowKind.TAPER,  i, t.startFromAftMm)) }
        spec.threads.forEachIndexed { i, th -> add(RowRef(RowKind.THREAD, i, th.startFromAftMm)) }
        spec.liners.forEachIndexed  { i, ln -> add(RowRef(RowKind.LINER,  i, ln.startFromAftMm)) }
    }

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
                        CommitNum("Start (${abbr(unit)})",  disp(t.startFromAftMm, unit)) { s ->
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
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ───────────────── Helpers: units, parsing, badge math, defaults ───────────────── */

private const val DEFAULT_OVERALL_MM = 100f * 25.4f // 100 in, mm-only; UI does not persist this

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

/** Latest occupied end position along X (mm), considering all components. */
private fun lastOccupiedEndMm(spec: ShaftSpec): Float {
    var end = 0f
    spec.bodies.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    return end
}

/** Contract: free = overallLengthMm – lastOccupiedEndMm; clamp to ≥ 0; mm only. */
private fun freeToEndMm(spec: ShaftSpec): Float {
    if (spec.overallLengthMm <= 0f) return 0f
    val free = spec.overallLengthMm - lastOccupiedEndMm(spec)
    return if (free < 0f) 0f else free
}

/* ───────────────── Free-to-End badge ───────────────── */

@Composable
private fun FreeToEndBadge(
    spec: ShaftSpec,
    unit: UnitSystem,
    modifier: Modifier = Modifier
) {
    // mm-only from model layer (contract)
    val freeMm = spec.freeToEndMm()
    if (spec.overallLengthMm <= 0f) return

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Text(
            text = "Free to end: ${formatDisplay(freeMm, unit)} ${abbr(unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
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
