// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
    onNavigateHome: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onExportPdf: () -> Unit,
) {
    ShaftRoute(
        vm = vm,
        onNavigateHome = onNavigateHome,
        onNew = onNew,
        onOpen = onOpen,
        onSave = onSave,
        onExportPdf = onExportPdf,
        onOpenSettings = onOpenSettings,
        onOpenDeveloperOptions = onOpenDeveloperOptions,
    )
}
