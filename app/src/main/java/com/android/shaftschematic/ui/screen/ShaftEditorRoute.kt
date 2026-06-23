// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * ShaftEditorRoute
 *
 * Top-level container for the three editor document views:
 * Schematic (shaft editor), Runout Sheet, and Wear Document.
 *
 * ## Navigation model
 * Navigation is handled by [EditorSidebarOverlay], which is a modal overlay drawer.
 * The sidebar never displaces content — it slides in from the left and overlays the
 * content with a scrim. Content always occupies the full screen width.
 *
 * This avoids the "crushed content" problem that occurs with a persistent side rail
 * on phones, especially smaller devices.
 *
 * ## "Built" definition
 * Runout and Wear tabs are enabled once the spec has ≥1 component and a non-zero OAL.
 * If the shaft loses "built" status (all components deleted) the active tab reverts to
 * Schematic automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftEditorRoute(
    vm: ShaftViewModel,
    onNavigateHome: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    /** Export the main shaft schematic PDF (goes to existing preview/SAF flow). */
    onExportPdf: () -> Unit,
    /** Export the runout measurement sheet PDF (goes to SAF). */
    onExportRunout: () -> Unit = {},
    /** Export the shaft wear/inspection document PDF (goes to SAF). */
    onExportWear: () -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(EditorTab.SCHEMATIC) }
    var sidebarOpen by rememberSaveable { mutableStateOf(false) }

    val spec by vm.spec.collectAsState()
    val isBuilt = remember(spec.bodies, spec.tapers, spec.threads, spec.liners, spec.overallLengthMm) {
        (spec.bodies.isNotEmpty() || spec.tapers.isNotEmpty() ||
            spec.threads.isNotEmpty() || spec.liners.isNotEmpty()) &&
            spec.overallLengthMm > 0f
    }

    // Snap back to Schematic when shaft is no longer built
    if (!isBuilt && activeTab != EditorTab.SCHEMATIC) {
        activeTab = EditorTab.SCHEMATIC
    }

    // Full-size Box so the sidebar can overlay the content
    Box(Modifier.fillMaxSize()) {

        // ── Active document (always full-width) ─────────────────────────────
        when (activeTab) {
            EditorTab.SCHEMATIC -> ShaftRoute(
                vm = vm,
                onNavigateHome = onNavigateHome,
                onNew = onNew,
                onOpen = onOpen,
                onSave = onSave,
                onSaveAs = onSaveAs,
                onExportPdf = onExportPdf,
                onOpenSettings = onOpenSettings,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
                onOpenSidebar = { sidebarOpen = true },
            )

            EditorTab.RUNOUT -> RunoutRoute(
                vm = vm,
                onExportRunout = onExportRunout,
                onOpenSidebar = { sidebarOpen = true },
            )

            EditorTab.WEAR -> WearRoute(
                vm = vm,
                onExportWear = onExportWear,
                onOpenSidebar = { sidebarOpen = true },
            )
        }

        // ── Overlay sidebar (handle tab when closed, modal panel when open) ──
        EditorSidebarOverlay(
            open = sidebarOpen,
            selectedTab = activeTab,
            runoutEnabled = isBuilt,
            onOpen = { sidebarOpen = true },
            onClose = { sidebarOpen = false },
            onTabSelected = { tab ->
                if (isBuilt || tab == EditorTab.SCHEMATIC) activeTab = tab
            },
            onHome = onNavigateHome,
            onSettings = onOpenSettings,
        )
    }
}
