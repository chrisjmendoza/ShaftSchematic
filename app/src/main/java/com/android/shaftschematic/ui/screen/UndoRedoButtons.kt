package com.android.shaftschematic.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * The Undo / Redo pair over the document's ONE history (`ShaftViewModel.canUndo`/`undoEdit`),
 * as the sheet tabs and their detail pages carry it in their top bars. Every tab's edits —
 * runout −/+ and drags, readings, pits, wear areas, Ø readings, worn sections, undercuts — are
 * steps in that same history, so each tab reaches them where they were made rather than only
 * from the Schematic tab's History menu (emulator QA: two round trips to undo two "−" taps).
 *
 * Tags are `<tagPrefix>_undo` / `<tagPrefix>_redo`.
 */
@Composable
internal fun UndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    tagPrefix: String,
) {
    IconButton(
        onClick = onUndo,
        enabled = canUndo,
        modifier = Modifier.testTag("${tagPrefix}_undo"),
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
    }
    IconButton(
        onClick = onRedo,
        enabled = canRedo,
        modifier = Modifier.testTag("${tagPrefix}_redo"),
    ) {
        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
    }
}

/** [UndoRedoButtons] bound to [vm]'s history. */
@Composable
internal fun UndoRedoButtons(vm: ShaftViewModel, tagPrefix: String) {
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    UndoRedoButtons(canUndo, canRedo, vm::undoEdit, vm::redoEdit, tagPrefix)
}
