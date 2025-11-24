// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.shaftschematic.ui.screen.ShaftRoute
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * ShaftEditorRoute
 *
 * Purpose
 * Wraps the editor body with a top app bar for navigation and actions.
 *
 * Contract
 * - This route owns the app bar only.
 * - Save/Open/PDF actions delegate to navigation; no I/O here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftEditorRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onExportPdf: () -> Unit
) {
    val canUndoDeletes by vm.canUndoDeletes.collectAsState()
    val canRedoDeletes by vm.canRedoDeletes.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Editor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    // Undo / Redo for delete history (multi-step)
                    IconButton(
                        onClick = { vm.undoLastDelete() },
                        enabled = canUndoDeletes
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo delete")
                    }
                    IconButton(
                        onClick = { vm.redoLastDelete() },
                        enabled = canRedoDeletes
                    ) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo delete")
                    }

                    // Existing actions
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Save, contentDescription = "Save JSON")
                    }
                    IconButton(onClick = onExportPdf) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            ShaftRoute(vm = vm)
        }
    }
}
