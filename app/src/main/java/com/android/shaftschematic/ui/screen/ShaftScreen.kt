// file: com/android/shaftschematic/ui/shaft/ShaftScreen.kt
package com.android.shaftschematic.ui.shaft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem

/**
 * ShaftScreen
 *
 * Stable, non-experimental UI:
 *  - Custom top bar (title, unit toggle, grid toggle, PDF export)
 *  - Preview at top: grid underlay + injected renderer on top
 *  - Collapsible Project Info (Customer, Vessel, Job)
 *  - Overall Length input with dynamic unit label (mm/in)
 *  - Collapsible Notes
 *  - IME-safe FAB
 *  - Snackbar hosted **inside Scaffold** (provided by Route)
 */
@Composable
fun ShaftScreen(
    spec: ShaftSpec,
    unit: UnitSystem,
    customer: String,
    vessel: String,
    jobNumber: String,
    notes: String,
    showGrid: Boolean,
    onSetUnit: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,
    onAddComponent: () -> Unit,
    onExportPdf: () -> Unit,
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit = { s, u -> DefaultShaftRenderer(s, u) },
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = {
            AppBarStable(
                title = "Shaft Details",
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0) Preview card at the top
                PreviewCard(
                    showGrid = showGrid,
                    gridStepDp = 16.dp,
                    spec = spec,
                    unit = unit,
                    renderShaft = renderShaft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                )

                HorizontalDivider()

                // 1) Project Info (collapsible)
                ExpandableSection(title = "Project Info", initiallyExpanded = true) {
                    OutlinedTextField(
                        value = customer,
                        onValueChange = onSetCustomer,
                        label = { Text("Customer (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vessel,
                        onValueChange = onSetVessel,
                        label = { Text("Vessel (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jobNumber,
                        onValueChange = onSetJobNumber,
                        label = { Text("Job number (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2) Overall Length (label reflects current unit)
                var lengthText by remember(unit, spec.overallLengthMm) {
                    mutableStateOf(formatForDisplay(spec.overallLengthMm, unit))
                }
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { txt ->
                        lengthText = txt
                        onSetOverallLengthRaw(txt)
                    },
                    label = { Text("Shaft Overall Length (${unitAbbrev(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3) Notes (collapsible)
                ExpandableSection(title = "Notes", initiallyExpanded = false) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = onSetNotes,
                        label = { Text("Notes (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        singleLine = false,
                        maxLines = 5
                    )
                }
            }

            // IME-safe FAB
            FloatingActionButton(
                onClick = onAddComponent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add component")
            }
        }
    }
}

/* ─────────────────────────────
 *  Top App Bar (stable)
 * ───────────────────────────── */
@Composable
private fun AppBarStable(
    title: String,
    unit: UnitSystem,
    gridChecked: Boolean,
    onUnitSelected: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onExportPdf: () -> Unit,
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

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

/* ─────────────────────────────
 *  Unit segmented control
 * ───────────────────────────── */
@Composable
private fun UnitSegment(unit: UnitSystem, onUnitSelected: (UnitSystem) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(4.dp),
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
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { onUnitSelected(chipUnit) }
        )
    }
}

/* ─────────────────────────────
 *  Collapsible section
 * ───────────────────────────── */
@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/* ─────────────────────────────
 *  Preview area
 * ───────────────────────────── */
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
            modifier = Modifier
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
        }
    }
}

@Composable
private fun GridCanvas(step: Dp, color: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier) {
        val stepPx = step.toPx().coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        var x = 0f
        while (x <= w) {
            drawLine(color, Offset(x, 0f), Offset(x, h), 1f); x += stepPx
        }
        var y = 0f
        while (y <= h) {
            drawLine(color, Offset(0f, y), Offset(w, y), 1f); y += stepPx
        }
    }
}

/* ─────────────────────────────
 *  Helpers
 * ───────────────────────────── */
private fun unitAbbrev(unit: UnitSystem) = when (unit) {
    UnitSystem.MILLIMETERS -> "mm"
    UnitSystem.INCHES -> "in"
}

private fun formatForDisplay(mm: Float, unit: UnitSystem, maxDecimals: Int = 3): String {
    val v = if (unit == UnitSystem.MILLIMETERS) mm.toDouble() else (mm / com.android.shaftschematic.model.MM_PER_IN)
    val s = "%.${maxDecimals}f".format(v).trimEnd('0').trimEnd('.')
    return if (s.isEmpty()) "0" else s
}

/** Safe fallback if a renderer isn't injected yet. */
@Composable
private fun DefaultShaftRenderer(spec: ShaftSpec, unit: UnitSystem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Preview: Overall Length = ${formatForDisplay(spec.overallLengthMm, unit)} ${unitAbbrev(unit)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
