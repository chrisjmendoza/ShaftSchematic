// file: app/src/main/java/com/android/shaftschematic/ui/shaft/ShaftRoute.kt
package com.android.shaftschematic.ui.shaft

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import kotlinx.coroutines.launch

/**
 * ShaftRoute
 *
 * Purpose
 * Bind [ShaftViewModel] state to [ShaftScreen] and host the Snackbar state.
 *
 * Contract
 * - No I/O or PDF. Pure binding layer.
 * - Model stays mm; UI converts only for display/input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftRoute(vm: ShaftViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val spec       by vm.spec.collectAsState()
    val unit       by vm.unit.collectAsState()
    val unitLocked by vm.unitLocked.collectAsState()
    val showGrid   by vm.showGrid.collectAsState()
    val customer   by vm.customer.collectAsState()
    val vessel     by vm.vessel.collectAsState()
    val jobNumber  by vm.jobNumber.collectAsState()
    val notes      by vm.notes.collectAsState()
    val overallIsManual by vm.overallIsManual.collectAsState()
    val order            by vm.componentOrder.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    fun showSnack(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    Box {
        ShaftScreen(
            spec = spec,
            unit = unit,
            unitLocked = unitLocked,
            overallIsManual = overallIsManual,
            customer = customer,
            vessel = vessel,
            jobNumber = jobNumber,
            notes = notes,
            showGrid = showGrid,

            // Cross-type order: ensures list renders newest-first by stable IDs
            componentOrder = order,

            // Unit & grid
            onSetUnit = vm::setUnit,
            onToggleGrid = vm::setShowGrid,

            // Project fields
            onSetCustomer = vm::setCustomer,
            onSetVessel = vm::setVessel,
            onSetJobNumber = vm::setJobNumber,
            onSetNotes = vm::setNotes,
            onSetOverallLengthRaw = vm::setOverallLength,
            onSetOverallLengthMm = vm::onSetOverallLengthMm,
            onSetOverallIsManual  = vm::setOverallIsManual,

            // Adds
            onAddBody   = { s, l, d        -> vm.addBodyAt(s, l, d) },
            onAddTaper  = { s, l, sd, ed   -> vm.addTaperAt(s, l, sd, ed) },
            onAddThread = { startMm, lengthMm, majorDiaMm, pitchMm ->
                vm.addThreadAt(startMm, lengthMm, majorDiaMm, pitchMm)
            },
            onAddLiner  = { s, l, od       -> vm.addLinerAt(s, l, od) },

            // Updates
            onUpdateBody   = { i, s, l, d        -> vm.updateBody(i, s, l, d) },
            onUpdateTaper  = { i, s, l, sd, ed   -> vm.updateTaper(i, s, l, sd, ed) },
            onUpdateThread = { i, s, l, maj, p   -> vm.updateThread(i, s, l, maj, p) },
            onUpdateLiner  = { i, s, l, od       -> vm.updateLiner(i, s, l, od) },

            // Removes
            onRemoveBody   = vm::removeBody,
            onRemoveTaper  = vm::removeTaper,
            onRemoveThread = vm::removeThread,
            onRemoveLiner  = vm::removeLiner,

            // Preview renderer injection (mm-only)
            renderShaft = { s, u ->
                ShaftDrawing(
                    spec = s,
                    unit = u,
                    showGrid = showGrid   // pass actual state (was hard-coded before)
                )
            },

            snackbarHostState = snackbarHostState,

            // Actions (no unresolved refs)
            onExportPdf = {
                // Placeholder; replace with real export when available
                showSnack("PDF export not wired yet.")
            },
            onOpenSettings = {
                showSettings = true
            }
        )

        // Minimal settings sheet (non-invasive, can be replaced with a screen later)
        if (showSettings) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState
            ) {
                ListItem(
                    headlineContent = { Text("Show grid in preview") },
                    trailingContent = {
                        Switch(
                            checked = showGrid,
                            onCheckedChange = { vm.setShowGrid(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Theme (app-wide)") },
                    supportingContent = { Text("Managed in main Settings; coming soon.") }
                )
                TextButton(
                    onClick = { showSettings = false },
                    modifier = Modifier
                        .padding(16.dp)
                ) { Text("Close") }
            }
        }
    }
}
