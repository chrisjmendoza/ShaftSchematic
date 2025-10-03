package com.android.shaftschematic.ui.shaft

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import kotlinx.coroutines.launch

/**
 * ShaftRoute
 *
 * Purpose:
 * Bind [ShaftViewModel] state to the editor screen and host Snackbar state.
 *
 * Contract:
 * • No file I/O or PDF logic here (those belong to outer NavHost/editor shell).
 * • All geometry in mm; unit toggle affects only formatting/parsing at UI edge.
 * • Snackbar host is available for undo/errors, but not for normal “added” events.
 */
@Composable
fun ShaftRoute(vm: ShaftViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect all VM state as Compose state
    val spec      by vm.spec.collectAsState()
    val unit      by vm.unit.collectAsState()
    val showGrid  by vm.showGrid.collectAsState()
    val customer  by vm.customer.collectAsState()
    val vessel    by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val notes     by vm.notes.collectAsState()

    fun showSnack(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    Box {
        ShaftScreen(
            spec = spec,
            unit = unit,
            customer = customer,
            vessel = vessel,
            jobNumber = jobNumber,
            notes = notes,
            showGrid = showGrid,

            // Unit & grid toggles
            onSetUnit = vm::setUnit,          // <- wires to UnitSelector
            onToggleGrid = vm::setShowGrid,   // <- wires to GridToggle

            // Top fields
            onSetCustomer = vm::setCustomer,
            onSetVessel = vm::setVessel,
            onSetJobNumber = vm::setJobNumber,
            onSetNotes = vm::setNotes,
            onSetOverallLengthRaw = vm::setOverallLength,
            onSetOverallLengthMm = vm::onSetOverallLengthMm,

            // Add
            onAddBody = { s, l, d -> vm.addBodyAt(s, l, d) },
            onAddTaper = { s, l, sd, ed -> vm.addTaperAt(s, l, sd, ed) },
            onAddThread = { s, l, maj, p -> vm.addThreadAt(s, l, maj, p) },
            onAddLiner = { s, l, od -> vm.addLinerAt(s, l, od) },

            // Update
            onUpdateBody   = { i, s, l, d -> vm.updateBody(i, s, l, d) },
            onUpdateTaper  = { i, s, l, sd, ed -> vm.updateTaper(i, s, l, sd, ed) },
            onUpdateThread = { i, s, l, maj, p -> vm.updateThread(i, s, l, maj, p) },
            onUpdateLiner  = { i, s, l, od -> vm.updateLiner(i, s, l, od) },

            // Remove
            onRemoveBody   = vm::removeBody,
            onRemoveTaper  = vm::removeTaper,
            onRemoveThread = vm::removeThread,
            onRemoveLiner  = vm::removeLiner,

            // Renderer (mm-only, UI picks unit for labels)
            renderShaft = { s, u ->
                ShaftDrawing(
                    spec = s,
                    unit = u,
                    showGrid = false
                )
            },

            snackbarHostState = snackbarHostState
        )
    }
}
